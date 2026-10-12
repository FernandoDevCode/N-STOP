import { test } from "node:test";
import assert from "node:assert/strict";
import { handle } from "../src/index.js";
import { DAILY_NEW, pruneDaily, untilTomorrow } from "../src/quota.js";
import { env } from "./fake-kv.js";

const BASE = "https://share.test";
const KEY = "k".repeat(43);
const doc = { v: 1, name: "Ambient", tracks: [{ t: "T", a: "A" }] };
const mix = (e, from) => handle(new Request(`${BASE}/v1/mixes`, {
    method: "POST", headers: { "content-type": "application/json", "CF-Connecting-IP": from }, body: JSON.stringify({ doc, editKey: KEY }),
}), e);
const track = (e, t, from = "203.0.113.9") => handle(new Request(`${BASE}/v1/tracks`, {
    method: "POST", headers: { "content-type": "application/json", "CF-Connecting-IP": from }, body: JSON.stringify({ t, a: "A" }),
}), e);

/** Runs [fn] with smaller daily budgets, restoring them after. */
async function withBudget(over, fn) {
    const saved = { ...DAILY_NEW };
    Object.assign(DAILY_NEW, over);
    try { await fn(); } finally { Object.assign(DAILY_NEW, saved); }
}

test("a network's new mixes stop at the daily budget with a Retry-After to tomorrow; /64 hops in one /56 share it", async () => {
    await withBudget({ mix: 2 }, async () => {
        const e = env();
        assert.equal((await mix(e, "2001:db8:aa:bb01::1")).status, 201);
        assert.equal((await mix(e, "2001:db8:aa:bb02::1")).status, 201);
        const over = await mix(e, "2001:db8:aa:bbff::9");
        assert.equal(over.status, 429);
        assert.deepEqual(await over.json(), { error: "rate_limited" });
        assert.ok(Number(over.headers.get("Retry-After")) > 0 && Number(over.headers.get("Retry-After")) <= 86_400);
        assert.equal(e.SHARE_KV.map.size, 2, "nothing stored past the budget");
        assert.equal((await mix(e, "2001:db8:aa:cc00::1")).status, 201, "another /56 has its own budget");
        assert.equal((await mix(e, "198.51.100.7")).status, 201);
    });
});

test("only new song links take from the budget: re-sharing a linked song still works past it", async () => {
    await withBudget({ track: 2 }, async () => {
        const e = env();
        assert.equal((await track(e, "One")).status, 201);
        assert.equal((await track(e, "Two")).status, 201);
        assert.equal((await track(e, "Three")).status, 429);
        assert.equal((await track(e, "One")).status, 200, "an existing link is answered, not refused");
        assert.equal((await track(e, "Three", "198.51.100.7")).status, 201);
    });
});

test("the cron prunes past days' counts and keeps today's", async () => {
    const e = env();
    await mix(e, "203.0.113.9");
    const db = e.COMMUNITY_DB.db;
    db.prepare("INSERT INTO share_quota (network, kind, day, n) VALUES ('old', 'mix', 1, 5)").run();
    await pruneDaily(e, Date.now());
    assert.deepEqual(db.prepare("SELECT kind, n FROM share_quota").all().map((r) => ({ ...r })), [{ kind: "mix", n: 1 }]);
});

test("untilTomorrow counts the seconds to the next UTC midnight", () => {
    assert.equal(untilTomorrow(Date.UTC(2026, 9, 11, 23, 59, 0)), "60");
    assert.equal(untilTomorrow(Date.UTC(2026, 9, 11, 0, 0, 0)), "86400");
});
