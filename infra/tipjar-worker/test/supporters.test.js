import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker from "../src/index.js";
import { legacyEntries, rebuildSupporters, SUPPORTERS_LIMIT } from "../src/supporters.js";
import { donation, env, fakeKV, kofiPost } from "./fake-kv.js";

const BASE = "https://stash-tipjar.example.workers.dev";
// The list as the pre-2026-10 webhook (and the hand seeding before it) left it: newest first, no keys, and the
// three seeded entries share one timestamp.
const OLD_LIST = {
    supporters: [
        { name: "Nickydicky8", amountUsd: 5, message: "Thanks so much!", timestamp: "2026-05-07T14:30:00Z" },
        { name: "Cedric", amountUsd: 10, message: "Just downloaded", timestamp: "2025-01-01T00:00:00Z" },
        { name: "Slowcab", amountUsd: 5, message: "Amazing work!", timestamp: "2025-01-01T00:00:00Z" },
        { name: "RucaNebas", amountUsd: 5, message: "Awesome application!", timestamp: "2025-01-01T00:00:00Z" },
    ],
};

const listOf = (e) => JSON.parse(e.STASH_KV.map.get("supporters").value).supporters;
const namesOf = (e) => listOf(e).map((s) => s.name);
const supporterKeys = (e) => [...e.STASH_KV.map.keys()].filter((k) => k.startsWith("supporter:")).sort();
const getList = async (e) => (await worker.fetch(new Request(`${BASE}/`), e)).text();
const cron = (e) => worker.scheduled({ cron: "17 4 * * *", scheduledTime: Date.now() }, e);
const second = (n, over = {}) => donation({ kofi_transaction_id: `tx-${n}`, message_id: `m${n}`, from_name: `Donor ${n}`, timestamp: `2026-10-04T12:00:${String(n).padStart(2, "0")}Z`, ...over });

test("each supporter is a key of their own, with the entry in its metadata; GET / is unchanged", async () => {
    const e = env();
    await worker.fetch(kofiPost(donation()), e);
    const key = "supporter:2026-10-04T12:00:00.000Z:tx-1";
    const entry = { name: "Ann", amountUsd: 5, message: "Love the app", timestamp: "2026-10-04T12:00:00Z" };
    assert.deepEqual(JSON.parse(e.STASH_KV.map.get(key).value), entry);
    assert.deepEqual(e.STASH_KV.map.get(key).opts.metadata, entry);
    assert.equal(e.STASH_KV.map.get(key).opts.expirationTtl, undefined, "kept for good");
    assert.deepEqual(listOf(e), [{ ...entry, key }]);
    assert.equal(await getList(e), '{"supporters":[{"name":"Ann","amountUsd":5,"message":"Love the app"}]}');
});

/** Holds every read of [key] until [n] of them have read it, so overlapping requests all see the same old value. */
function readTogether(kv, key, n) {
    const get = kv.get.bind(kv);
    let reads = 0;
    let release;
    const together = new Promise((resolve) => (release = resolve));
    kv.get = async (k, type) => {
        if (k !== key) return get(k, type);
        const value = await get(k, type);
        if (++reads >= n) release();
        await together;
        return value;
    };
}

test("two overlapping webhooks for different donations both stay listed", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: JSON.stringify(OLD_LIST) }) });
    // Both read the list before either writes it. Before 2026-10 the second write then dropped the first donor.
    readTogether(e.STASH_KV, "supporters", 2);
    const results = await Promise.all([worker.fetch(kofiPost(second(1)), e), worker.fetch(kofiPost(second(2)), e)]);
    assert.deepEqual(results.map((r) => r.status), [200, 200]);
    assert.deepEqual(namesOf(e).slice(0, 2).sort(), ["Donor 1", "Donor 2"]);
    assert.equal(listOf(e).length, 6);
});

test("a rebuild that can't see the other donor yet loses nobody: the next rebuild (the daily cron) lists them", async () => {
    const kv = fakeKV();
    const e = env({ STASH_KV: kv });
    await worker.fetch(kofiPost(second(1)), e);
    // KV's list() can lag a minute behind a write: the second webhook's rebuild doesn't see donor 1's key, and its
    // list (read before donor 1's rebuild landed, say) wins.
    const list = kv.list.bind(kv);
    kv.list = async (opts) => {
        const page = await list(opts);
        return { ...page, keys: page.keys.filter((k) => !k.name.endsWith(":tx-1")) };
    };
    await kv.put("supporters", JSON.stringify({ supporters: [] }));
    await worker.fetch(kofiPost(second(2)), e);
    assert.deepEqual(namesOf(e), ["Donor 2"], "donor 1 is missing from this rebuild");
    assert.ok(kv.map.has("supporter:2026-10-04T12:00:01.000Z:tx-1"), "but their own key is still there");
    kv.list = list;
    await cron(e);
    assert.deepEqual(namesOf(e), ["Donor 2", "Donor 1"]);
});

/** Makes [kv]'s list() leave out every key [hide] matches (KV's list() lagging behind writes). Returns the undo. */
function lagList(kv, hide) {
    const list = kv.list.bind(kv);
    kv.list = async (opts) => {
        const page = await list(opts);
        return { ...page, keys: page.keys.filter((k) => !hide(k.name)) };
    };
    return () => (kv.list = list);
}

test("a rebuild whose list() lags behind keeps the supporters already listed (right after the old list's move, say)", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: JSON.stringify(OLD_LIST) }) });
    await cron(e); // the move: four fresh legacy keys
    const undo = lagList(e.STASH_KV, (name) => name.includes(":legacy-"));
    await worker.fetch(kofiPost(donation()), e);
    assert.deepEqual(namesOf(e), ["Ann", ...OLD_LIST.supporters.map((s) => s.name)]);
    undo();
});

test("a supporter whose key is deleted by hand leaves the list at the next rebuild", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: JSON.stringify(OLD_LIST) }) });
    await cron(e);
    await e.STASH_KV.delete("supporter:2025-01-01T00:00:00.000Z:legacy-0001"); // Slowcab
    await cron(e);
    assert.deepEqual(namesOf(e), ["Nickydicky8", "Cedric", "RucaNebas"]);
});

test("a Ko-fi retry lists the supporter even when list() doesn't show their key yet", async () => {
    const kv = fakeKV();
    const put = kv.put.bind(kv);
    let failing = true;
    kv.put = async (key, value, opts) => {
        if (failing && key === "supporters") throw new Error("KV PUT failed: 429 Too Many Requests");
        return put(key, value, opts);
    };
    const e = env({ STASH_KV: kv });
    assert.equal((await worker.fetch(kofiPost(donation()), e)).status, 500);
    failing = false;
    lagList(kv, (name) => name.endsWith(":tx-1"));
    assert.equal((await worker.fetch(kofiPost(donation()), e)).status, 200);
    assert.deepEqual(namesOf(e), ["Ann"]);
    assert.equal(kv.puts.filter((k) => k.startsWith("supporter:")).length, 1, "the retry didn't write the key again");
});

test("a Ko-fi retry writes the same key again, even after the dedupe entry is gone: nobody is listed twice", async () => {
    const e = env();
    await worker.fetch(kofiPost(donation()), e);
    e.STASH_KV.map.delete("kofitxn:tx-1"); // expired after 60 days, or the first try failed before marking it
    await worker.fetch(kofiPost(donation()), e);
    assert.deepEqual(supporterKeys(e), ["supporter:2026-10-04T12:00:00.000Z:tx-1"]);
    assert.equal(listOf(e).length, 1);
});

test("the list from before 2026-10 moves to keys of its own on the first rebuild, and GET / serves the same bytes", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: JSON.stringify(OLD_LIST) }) });
    const before = await getList(e);
    await cron(e);
    assert.equal(await getList(e), before);
    assert.deepEqual(supporterKeys(e), [
        "supporter:2025-01-01T00:00:00.000Z:legacy-0000",
        "supporter:2025-01-01T00:00:00.000Z:legacy-0001",
        "supporter:2025-01-01T00:00:00.000Z:legacy-0002",
        "supporter:2026-05-07T14:30:00.000Z:legacy-0003",
    ]);
    assert.deepEqual(JSON.parse(e.STASH_KV.map.get("supporter:2025-01-01T00:00:00.000Z:legacy-0002").value), OLD_LIST.supporters[1]);
    assert.ok(listOf(e).every((s) => typeof s.key === "string"), "every entry now names its key");
    // The stored entries are the old ones exactly, plus their key.
    assert.deepEqual(listOf(e).map(({ key, ...entry }) => entry), OLD_LIST.supporters);
});

test("moving the old list is idempotent: a second rebuild from a stale copy writes the same keys, nobody twice", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: JSON.stringify(OLD_LIST) }) });
    await cron(e);
    const keys = supporterKeys(e);
    const moved = e.STASH_KV.map.get("supporters").value;
    // Another location's read (or a webhook at the same moment) still sees the old list.
    await e.STASH_KV.put("supporters", JSON.stringify(OLD_LIST));
    await worker.fetch(kofiPost(donation()), e);
    assert.deepEqual(supporterKeys(e), [...keys, "supporter:2026-10-04T12:00:00.000Z:tx-1"].sort());
    assert.deepEqual(namesOf(e), ["Ann", ...OLD_LIST.supporters.map((s) => s.name)]);
    assert.deepEqual(listOf(e).slice(1), JSON.parse(moved).supporters);
});

test("an old-format entry written after the move (an old Worker finishing during a deploy) gets a key too", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: JSON.stringify(OLD_LIST) }) });
    await cron(e);
    const list = JSON.parse(e.STASH_KV.map.get("supporters").value);
    list.supporters.unshift({ name: "Straggler", amountUsd: 3, message: "", timestamp: "2026-10-05T08:00:00Z" });
    await e.STASH_KV.put("supporters", JSON.stringify(list));
    await cron(e);
    assert.deepEqual(namesOf(e), ["Straggler", ...OLD_LIST.supporters.map((s) => s.name)]);
    assert.equal(supporterKeys(e).length, 5);
    assert.ok(e.STASH_KV.map.has("supporter:2026-10-05T08:00:00.000Z:legacy-0000"));
});

test("legacyEntries keeps the old order: times never go back, and a missing time takes the one before it", () => {
    const keys = legacyEntries([
        { name: "C", timestamp: "2026-01-01T00:00:00Z" },
        { name: "B", timestamp: "not a time" },
        { name: "A", timestamp: "2026-03-01T00:00:00Z" }, // out of order: older than its place says
        { name: "keyed", key: "supporter:x:y" }, // already moved: skipped
        { name: "Z" },
    ]).map((x) => x.key);
    assert.deepEqual(keys, [
        "supporter:1970-01-01T00:00:00.000Z:legacy-0000",
        "supporter:2026-03-01T00:00:00.000Z:legacy-0001",
        "supporter:2026-03-01T00:00:00.000Z:legacy-0002",
        "supporter:2026-03-01T00:00:00.000Z:legacy-0003",
    ]);
    assert.deepEqual([...keys].sort().reverse(), [...keys].reverse(), "sorting the keys gives back the list's own order");
});

test("the daily cron writes nothing when the list hasn't changed", async () => {
    const e = env();
    await worker.fetch(kofiPost(donation()), e);
    const writes = e.STASH_KV.puts.length;
    await cron(e);
    await cron(e);
    assert.equal(e.STASH_KV.puts.length, writes);
});

test(`the list holds the newest ${SUPPORTERS_LIMIT}, and nothing past it is deleted`, async () => {
    const initial = {};
    for (let i = 0; i < SUPPORTERS_LIMIT + 3; i++) {
        const at = new Date(Date.UTC(2026, 0, 1) + i * 60_000).toISOString();
        initial[`supporter:${at}:tx-${i}`] = JSON.stringify({ name: `S${i}`, amountUsd: 1, message: "", timestamp: at });
    }
    const e = env({ STASH_KV: fakeKV(initial) });
    await cron(e);
    const names = namesOf(e);
    assert.equal(names.length, SUPPORTERS_LIMIT);
    assert.equal(names[0], `S${SUPPORTERS_LIMIT + 2}`);
    assert.equal(names.at(-1), "S3");
    assert.equal(supporterKeys(e).length, SUPPORTERS_LIMIT + 3);
});

test("an entry too big for KV metadata, or a key added by hand without any, is read from its value; junk is left out", async () => {
    const e = env();
    const big = donation({ from_name: "€".repeat(40), message: "€".repeat(300) }); // 3 bytes each in UTF-8
    await worker.fetch(kofiPost(big), e);
    const key = "supporter:2026-10-04T12:00:00.000Z:tx-1";
    assert.equal(e.STASH_KV.map.get(key).opts.metadata, undefined);
    await e.STASH_KV.put("supporter:2026-10-05T00:00:00.000Z:manual-1", JSON.stringify({ name: "By hand", amountUsd: 20, message: "" }));
    await e.STASH_KV.put("supporter:2026-10-06T00:00:00.000Z:manual-2", "{not json");
    await cron(e);
    assert.deepEqual(namesOf(e), ["By hand", "€".repeat(40)]);
    assert.equal(listOf(e)[1].message, "€".repeat(280));
});

test("if the rebuild fails, the supporter is still stored, Ko-fi gets a 500, and the retry lists them", async () => {
    const kv = fakeKV({ supporters: JSON.stringify(OLD_LIST) });
    const put = kv.put.bind(kv);
    let failing = true;
    kv.put = async (key, value, opts) => {
        if (failing && key === "supporters") throw new Error("KV PUT failed: 429 Too Many Requests");
        return put(key, value, opts);
    };
    const e = env({ STASH_KV: kv });
    assert.equal((await worker.fetch(kofiPost(donation()), e)).status, 500);
    assert.ok(kv.map.has("supporter:2026-10-04T12:00:00.000Z:tx-1"));
    assert.equal(kv.map.get("kofitxn:tx-1").value, "listed", "not done: the retry has to rebuild");
    assert.equal(e.ACCESS_KV.map.size > 0, true, "early access went ahead");
    failing = false;
    assert.equal((await worker.fetch(kofiPost(donation()), e)).status, 200);
    assert.deepEqual(namesOf(e), ["Ann", ...OLD_LIST.supporters.map((s) => s.name)]);
    assert.equal(kv.map.get("kofitxn:tx-1").value, "done");
});

test("a list that isn't JSON is left alone for a person to look at; the supporter is still stored", async () => {
    const e = env({ STASH_KV: fakeKV({ supporters: "{broken" }) });
    assert.equal((await worker.fetch(kofiPost(donation()), e)).status, 500);
    assert.equal(e.STASH_KV.map.get("supporters").value, "{broken");
    assert.ok(e.STASH_KV.map.has("supporter:2026-10-04T12:00:00.000Z:tx-1"));
    await assert.rejects(rebuildSupporters(e));
});

test("wrangler.toml runs the rebuild daily, and the hand-seeding script is gone", () => {
    const toml = readFileSync(new URL("../wrangler.toml", import.meta.url), "utf8");
    assert.match(toml, /^\[triggers\]\s*\ncrons = \["\d+ \d+ \* \* \*"\]$/m);
    assert.throws(() => readFileSync(new URL("../seed-historical-supporters.ps1", import.meta.url)), /ENOENT/);
});
