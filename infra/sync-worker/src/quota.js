/**
 * Quota: one small Durable Object per player session (`idFromName("s:" + session hash)`) or per IP (`"ip:" + address`),
 * counting what a ratelimits binding can't (its longest period is 60 s): pairing codes per 10 minutes and spaces per day
 * (spec §6.4). Sliding windows of timestamps; the alarm deletes the object a day after its last use.
 */
import { serveRpc } from "./http.js";

const DAY_MS = 86_400_000;

/** kind → at most `n` per `ms`. Exported so tests can lower them. */
export const QUOTAS = {
    /** Pairing codes opened per player session (spec §6.4: 6 per 10 minutes). */
    code: { n: 6, ms: 10 * 60_000 },
    /** Spaces created from one player session's codes per day. */
    space: { n: 10, ms: DAY_MS },
    /** Spaces created from one phone IPv6 /64 per day. */
    spaceIp: { n: 20, ms: DAY_MS },
    /**
     * Spaces created from one phone IPv4 address per day. Higher than the /64's: one IPv4 address is often a carrier's NAT
     * or a campus's egress, shared by many phones each making its first link, and every space also needs a code from a
     * signed-in player session (`space`, 10 a day), so this cap only has to stop one host fanning out over many sessions.
     */
    spaceIp4: { n: 100, ms: DAY_MS },
};

/** The per-IP space quota for a limitKey: IPv6 keys (a /64) contain ':'; IPv4 ones don't. */
export const spaceIpKind = (ip) => (ip.includes(":") ? "spaceIp" : "spaceIp4");

/** Pure: `{ ok, state, retryAfter }`, where `state` keeps only the timestamps still inside the window. */
export function takeQuota(state, kind, now) {
    const q = QUOTAS[kind];
    const kept = (state?.[kind] ?? []).filter((t) => now - t < q.ms);
    if (kept.length >= q.n) return { ok: false, state: { ...state, [kind]: kept }, retryAfter: Math.ceil((kept[0] + q.ms - now) / 1000) };
    return { ok: true, state: { ...state, [kind]: [...kept, now] }, retryAfter: 0 };
}

export class Quota {
    constructor(ctx, env, opts = {}) {
        this.ctx = ctx;
        this.env = env;
        this.now = opts.now ?? (() => Date.now());
    }

    fetch(request) {
        return serveRpc(request, this, ["take"]);
    }

    /** `{ ok, retryAfter }`; a refused take isn't counted. */
    async take({ kind }) {
        if (!QUOTAS[kind]) return { ok: false, retryAfter: 60 };
        const now = this.now();
        const r = takeQuota((await this.ctx.storage.get("q")) ?? {}, kind, now);
        await this.ctx.storage.put("q", r.state);
        await this.ctx.storage.setAlarm(now + DAY_MS);
        return { ok: r.ok, retryAfter: r.retryAfter };
    }

    async alarm() {
        await this.ctx.storage.deleteAll();
    }
}
