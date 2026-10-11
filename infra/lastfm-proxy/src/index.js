/**
 * Stash Last.fm Proxy — Cloudflare Worker
 *
 * Proxies the GENERIC, cross-user Last.fm read lookups that power Stash Mixes
 * (tag→tracks, artist→similar, etc.) through a single server-side API key with
 * an edge cache. This decouples Last.fm request volume from the number of app
 * installs: each unique query is fetched from Last.fm at most once per TTL and
 * served to every user from cache, so the shared key never gets rate-limited
 * (error 29) no matter how the user base grows.
 *
 * What this does NOT proxy (by design):
 *   - Auth + scrobbling (signed, bound to a user's session) — stay direct from
 *     the app on its primary key; the secret never reaches this Worker.
 *   - Per-user reads (user.getTop*, track.getInfo WITH username) — personal,
 *     low-volume; the Worker rejects any `user`/`username`/`sk`/`api_sig`.
 *
 * Personalization is unaffected: the Worker only serves the generic candidate
 * POOL. Seed selection, scoring and filtering all happen on-device per user.
 *
 * Endpoint:
 *   GET /lastfm?method=<m>&<lookup params...>
 *     → injects the server api_key + format=json, proxies to Last.fm, caches
 *       the body, returns JSON. 429 (with Retry-After) on Last.fm rate-limit.
 *
 * Secret: LASTFM_API_KEY  (set via `wrangler secret put LASTFM_API_KEY`).
 * Deploy: see ../README.md (this lives at infra/lastfm-proxy).
 */

const LASTFM_BASE = "https://ws.audioscrobbler.com/2.0/";

/** Cache TTL for generic lookups, in seconds. These charts move slowly. */
const CACHE_TTL_SECONDS = 14 * 24 * 60 * 60; // 14 days

/**
 * Read-only, generic (non-personalized) methods this proxy is allowed to
 * serve. Anything else is rejected — the Worker must not become an open
 * Last.fm proxy, and must never touch signed/per-user endpoints.
 */
export const ALLOWED_METHODS = new Set([
    "tag.gettoptracks",
    "tag.gettopartists",
    "artist.getinfo",
    "artist.getsimilar",
    "artist.gettoptracks",
    "artist.gettoptags",
    "track.getsimilar",
    "track.gettoptags",
    "track.getinfo",
]);

/** Params a client may NOT send — they'd make the request signed or per-user. */
export const FORBIDDEN_PARAMS = new Set(["api_key", "api_sig", "sk", "user", "username"]);

/**
 * The lookup params each method forwards upstream, which are exactly the ones the app sends
 * (LastFmApiClient). Everything else is dropped, not echoed. Copying every client param upstream made
 * the proxy cache-bustable: `&x=<random>` (or a different case of `method`) was a fresh cache key and a
 * fresh Last.fm call on the server key every time, so one loop could push the key into error 29 for every
 * install. The upstream URL and the cache key are both rebuilt from this list.
 */
export const METHOD_PARAMS = {
    "tag.gettoptracks": ["tag", "limit"],
    "tag.gettopartists": ["tag", "limit"],
    "artist.getinfo": ["artist", "autocorrect"],
    "artist.getsimilar": ["artist", "autocorrect", "limit"],
    "artist.gettoptracks": ["artist", "autocorrect", "limit"],
    "artist.gettoptags": ["artist", "autocorrect"],
    "track.getsimilar": ["artist", "track", "autocorrect", "limit"],
    "track.gettoptags": ["artist", "track", "autocorrect"],
    "track.getinfo": ["artist", "track", "autocorrect"],
};

/** The largest page the app asks for (RadioStationGenerator.NEIGHBOR_MAX); bigger `limit`s are clamped to it. */
export const MAX_LIMIT = 100;

export default {
    async fetch(request, env, ctx) {
        if (request.method !== "GET") {
            return json({ error: "method_not_allowed" }, 405);
        }
        const url = new URL(request.url);
        if (url.pathname !== "/lastfm") {
            return json({ error: "not_found" }, 404);
        }
        return handleLastFmRead(request, url, env, ctx);
    },
};

async function handleLastFmRead(request, url, env, ctx) {
    const apiKey = env.LASTFM_API_KEY;
    if (!apiKey) {
        return json({ error: "server_misconfigured", message: "LASTFM_API_KEY not set" }, 500);
    }

    const params = url.searchParams;
    const method = (params.get("method") || "").toLowerCase();

    if (!ALLOWED_METHODS.has(method)) {
        return json({ error: "method_not_allowed", message: `method '${method}' not proxied` }, 400);
    }
    for (const forbidden of FORBIDDEN_PARAMS) {
        if (params.has(forbidden)) {
            return json({ error: "forbidden_param", message: `'${forbidden}' is not accepted` }, 400);
        }
    }

    // Canonical query: the lowercased method plus only that method's allowed
    // lookup args (limit clamped), sorted. It is both the cache key and the
    // upstream query, so the cache is keyed by the logical query alone (and is
    // naturally shared across every caller), and nothing a client adds can
    // turn a cached lookup into a fresh upstream call.
    const query = canonicalParams(params);
    const cacheKey = buildCacheKey(query);
    const cache = caches.default;

    const cached = await cache.match(cacheKey);
    if (cached) {
        const hit = new Response(cached.body, cached);
        hit.headers.set("X-Stash-Cache", "HIT");
        return hit;
    }

    // Only a MISS spends the server key, so only a MISS is rate-limited, per
    // client network. The canonical key bounds variants per lookup; this bounds
    // how many distinct lookups one network can push upstream. Optional binding,
    // so local dev and tests without it still work.
    if (env.LOOKUP_RL) {
        const { success } = await env.LOOKUP_RL.limit({ key: limitKey(request.headers.get("CF-Connecting-IP") || "?") });
        if (!success) return rateLimited(60);
    }

    // Build the upstream Last.fm request from the canonical query, then inject
    // the server key + json format.
    const upstream = new URL(LASTFM_BASE);
    for (const [k, v] of query) upstream.searchParams.set(k, v);
    upstream.searchParams.set("api_key", apiKey);
    upstream.searchParams.set("format", "json");

    let lfmResponse;
    try {
        lfmResponse = await fetch(upstream.toString(), {
            headers: { "User-Agent": "Stash-LastFm-Proxy/1.0" },
        });
    } catch (e) {
        console.error("Last.fm upstream request failed", e);
        return json({ error: "upstream_unreachable", message: "Upstream service unreachable" }, 502);
    }

    // Hard HTTP rate limit.
    if (lfmResponse.status === 429) {
        return rateLimited();
    }
    if (!lfmResponse.ok) {
        return json({ error: "upstream_error", status: lfmResponse.status }, 502);
    }

    const body = await lfmResponse.text();

    // Last.fm returns HTTP 200 with { "error": N } on soft failures. error 29 is
    // the rate limit — surface as 429 and DO NOT cache. Other errors (e.g. 6
    // "not found") are passed through but also not cached (they're often
    // transient or query-specific noise).
    const errorCode = extractErrorCode(body);
    if (errorCode === 29) {
        return rateLimited();
    }
    if (errorCode !== null) {
        return new Response(body, {
            status: 200,
            headers: { "content-type": "application/json", "X-Stash-Cache": "BYPASS" },
        });
    }

    const response = new Response(body, {
        status: 200,
        headers: {
            "content-type": "application/json",
            "cache-control": `public, max-age=${CACHE_TTL_SECONDS}`,
            "X-Stash-Cache": "MISS",
        },
    });
    // Store a cacheable clone (the X-Stash-Cache header on the stored copy is
    // overwritten to HIT when served from cache above).
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
}

/**
 * The canonical lookup for an allowed method: `method` lowercased, then only
 * [METHOD_PARAMS] for it, with `limit` clamped to 1..[MAX_LIMIT] (dropped if it
 * isn't a number) and `autocorrect` kept only as "1". Unknown params are
 * dropped rather than refused, so an older app that adds one still works; they
 * just can't reach Last.fm or vary the cache key. Pure + exported for tests.
 */
export function canonicalParams(params) {
    const method = (params.get("method") || "").toLowerCase();
    const out = new URLSearchParams({ method });
    for (const name of METHOD_PARAMS[method] || []) {
        const v = params.get(name);
        if (v === null) continue;
        if (name === "limit") {
            const n = parseInt(v, 10);
            if (Number.isFinite(n)) out.set(name, String(Math.min(MAX_LIMIT, Math.max(1, n))));
        } else if (name === "autocorrect") {
            if (v === "1") out.set(name, "1");
        } else {
            out.set(name, v);
        }
    }
    return out;
}

/**
 * Rate-limit key for a client: an IPv4 address as is, an IPv6 one by its /48
 * (as web/worker/http.js does). One subscriber is often given a whole /48 or
 * /56, so a full-address or /64 key would let one host rotate through
 * thousands of buckets.
 */
export function limitKey(addr) {
    if (!addr.includes(":") || addr.includes(".")) return addr; // IPv4, or IPv4-mapped IPv6
    const [head, tail] = addr.toLowerCase().split("::");
    const left = head ? head.split(":") : [];
    const right = tail ? tail.split(":") : [];
    const groups = tail === undefined ? left : [...left, ...Array(Math.max(0, 8 - left.length - right.length)).fill("0"), ...right];
    return `${groups.slice(0, 3).map((g) => (parseInt(g || "0", 16) || 0).toString(16)).join(":")}::/48`;
}

/**
 * Builds the edge-cache key as a normalized GET Request. Sorts the client
 * params and drops anything we inject upstream, so logically-identical queries
 * collapse to one cache entry regardless of param order.
 */
function buildCacheKey(params) {
    // Host is arbitrary but must be stable — it only namespaces the cache.
    return new Request(
        `https://stash-lastfm-cache.internal/lastfm?${normalizeQuery(params)}`,
        { method: "GET" },
    );
}

/**
 * Sorts the query params and drops `api_key`/`format`, so logically-identical
 * lookups collapse to one cache entry regardless of order or which key served
 * them. Pure + exported for unit tests.
 */
export function normalizeQuery(params) {
    const entries = [];
    for (const [k, v] of params) {
        if (k === "api_key" || k === "format") continue;
        entries.push([k, v]);
    }
    entries.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
    return entries
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join("&");
}

/** Returns the numeric Last.fm error code in a 200 body, or null if none. */
export function extractErrorCode(body) {
    try {
        const parsed = JSON.parse(body);
        if (typeof parsed.error === "number") return parsed.error;
        if (typeof parsed.error === "string") return parseInt(parsed.error, 10);
    } catch {
        // Non-JSON body — treat as no structured error.
    }
    return null;
}

function rateLimited(retryAfter = 300) {
    return new Response(
        JSON.stringify({ error: 29, message: "Rate Limit Exceeded (upstream)" }),
        {
            status: 429,
            headers: {
                "content-type": "application/json",
                "retry-after": String(retryAfter),
                "X-Stash-Cache": "BYPASS",
            },
        },
    );
}

function json(obj, status) {
    return new Response(JSON.stringify(obj), {
        status,
        headers: { "content-type": "application/json" },
    });
}
