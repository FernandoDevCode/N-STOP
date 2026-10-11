import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import worker, { canonicalParams, limitKey } from "../src/index.js";

// A Map-backed stand-in for caches.default, and a fetch stub that records every upstream URL.
let store;
let upstreamCalls;
let realFetch;
beforeEach(() => {
    store = new Map();
    upstreamCalls = [];
    globalThis.caches = {
        default: {
            match: async (req) => store.get(req.url)?.clone(),
            put: async (req, res) => void store.set(req.url, res),
        },
    };
    realFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
        upstreamCalls.push(new URL(url));
        return new Response('{"similarartists":{"artist":[]}}', { status: 200 });
    };
});
afterEach(() => {
    globalThis.fetch = realFetch;
    delete globalThis.caches;
});

function ctx() {
    const pending = [];
    return { waitUntil: (p) => pending.push(p), done: () => Promise.all(pending) };
}

async function get(query, env = { LASTFM_API_KEY: "K" }, ip = "203.0.113.7") {
    const c = ctx();
    const res = await worker.fetch(
        new Request(`https://proxy.example/lastfm?${query}`, { headers: { "CF-Connecting-IP": ip } }),
        env,
        c,
    );
    await c.done();
    return res;
}

test("junk params and method case can't bust the cache: the variants hit one entry and one upstream call", async () => {
    const first = await get("method=artist.getSimilar&artist=Radiohead&autocorrect=1&limit=30");
    assert.equal(first.headers.get("X-Stash-Cache"), "MISS");
    for (const q of [
        "method=ARTIST.GETSIMILAR&artist=Radiohead&autocorrect=1&limit=30&x=1",
        "limit=30&artist=Radiohead&method=artist.getsimilar&autocorrect=1&cb=" + Math.random(),
        "method=artist.getsimilar&artist=Radiohead&autocorrect=1&limit=30&page=7&mbid=zzz&format=xml",
    ]) {
        const res = await get(q);
        assert.equal(res.headers.get("X-Stash-Cache"), "HIT", q);
    }
    assert.equal(upstreamCalls.length, 1);
});

test("upstream gets only the method's allowed params, method lowercased, plus the server key and json", async () => {
    await get("method=Track.getSimilar&artist=A&track=T&autocorrect=1&limit=30&foo=bar&page=2");
    const u = upstreamCalls[0];
    assert.deepEqual(
        [...u.searchParams].sort(),
        [["api_key", "K"], ["artist", "A"], ["autocorrect", "1"], ["format", "json"], ["limit", "30"],
            ["method", "track.getsimilar"], ["track", "T"]].sort(),
    );
});

test("forbidden params are still refused", async () => {
    const res = await get("method=track.getinfo&artist=A&track=T&username=bob");
    assert.equal(res.status, 400);
    assert.equal(upstreamCalls.length, 0);
});

test("canonicalParams clamps limit, drops non-numeric limit and non-1 autocorrect", () => {
    assert.equal(canonicalParams(new URLSearchParams("method=tag.gettoptracks&tag=x&limit=100000")).get("limit"), "100");
    assert.equal(canonicalParams(new URLSearchParams("method=tag.gettoptracks&tag=x&limit=0")).get("limit"), "1");
    assert.equal(canonicalParams(new URLSearchParams("method=tag.gettoptracks&tag=x&limit=abc")).has("limit"), false);
    assert.equal(canonicalParams(new URLSearchParams("method=artist.getinfo&artist=x&autocorrect=yes")).has("autocorrect"), false);
    // A param another method allows isn't carried over (tag lookups have no artist).
    assert.equal(canonicalParams(new URLSearchParams("method=tag.gettoptracks&tag=x&artist=y")).has("artist"), false);
});

test("a MISS over the per-network limit is 429 without an upstream call; HITs are never limited", async () => {
    const keys = [];
    let allow = true;
    const env = { LASTFM_API_KEY: "K", LOOKUP_RL: { limit: async ({ key }) => (keys.push(key), { success: allow }) } };
    assert.equal((await get("method=tag.gettoptracks&tag=a", env, "2001:db8:1:2::5")).status, 200);
    allow = false;
    const hit = await get("method=tag.gettoptracks&tag=a", env, "2001:db8:1:ffff::9");
    assert.equal(hit.headers.get("X-Stash-Cache"), "HIT");
    const miss = await get("method=tag.gettoptracks&tag=b", env, "2001:db8:1:ffff::9");
    assert.equal(miss.status, 429);
    assert.equal(upstreamCalls.length, 1);
    // Only MISSes were checked, both keyed on the same /48.
    assert.deepEqual(keys, ["2001:db8:1::/48", "2001:db8:1::/48"]);
});

test("limitKey keeps IPv4 whole and folds IPv6 to its /48", () => {
    assert.equal(limitKey("198.51.100.4"), "198.51.100.4");
    assert.equal(limitKey("2001:DB8:aa:bb:cc::1"), "2001:db8:aa::/48");
    assert.equal(limitKey("::1"), "0:0:0::/48");
});
