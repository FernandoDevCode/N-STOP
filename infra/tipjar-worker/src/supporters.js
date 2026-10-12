/**
 * The supporters list the Stash app reads (GET /, src/index.js), stored so that two donations arriving at once
 * can't lose either donor.
 *
 * Each supporter is a key of its own in STASH_KV, supporter:<time>:<id>, written once and kept for good. The
 * time is the donation's, as an ISO string, so the keys sort oldest to newest; the id is Ko-fi's transaction id,
 * so a Ko-fi retry writes the same key again. The value is the list entry, { name, amountUsd, message, timestamp },
 * and so is the key's metadata when it fits KV's 1,024 bytes, so a rebuild reads the whole list from list() pages
 * instead of one get per supporter.
 *
 * `supporters`, the JSON the app gets, is only ever rebuilt from those keys: after each donation, and daily (the
 * cron in wrangler.toml). Before 2026-10 the webhook read that list, added one donor and wrote it back, so two
 * webhooks at once kept only one of the two donors, and the other was gone for good (the retry dedupe had already
 * marked them listed). Now a rebuild that misses a donor (KV's list() can lag up to a minute behind a write, and
 * two rebuilds at once can finish in either order) loses nothing: the donor's own key is still there, and the
 * next rebuild, a day later at the latest, lists them.
 *
 * Entries in `supporters` carry the key they came from (`key`, which GET / leaves out). One without a key is from
 * the list as it was before 2026-10, or from an old Worker still finishing a request during a deploy: the first
 * rebuild that sees it gives it a key of its own (legacyEntries), so the old list moves over by itself.
 */
import { toUsdCents } from "./access.js";

// 2026-07-19: 20 was destructively truncating — donors pushed past the
// cap were deleted from KV forever, which is why older donations vanished
// from the app's ticker. 500 entries is still a trivially small KV value.
// (Since 2026-10 nothing past the cap is deleted either: it just isn't listed.)
export const SUPPORTERS_LIMIT = 500;
export const LIST_KEY = "supporters";
export const SUPPORTER_PREFIX = "supporter:";
/** KV refuses metadata over 1,024 bytes of JSON; an entry bigger than this is read from its value instead. */
const METADATA_MAX_BYTES = 1000;

const enc = new TextEncoder();

/** A Ko-fi time as "2026-10-04T12:00:00.000Z", or null. Every key's time has this one shape, so the keys sort by time. */
function isoTime(value) {
    const t = typeof value === "string" ? Date.parse(value) : Number.NaN;
    return Number.isNaN(t) ? null : new Date(t).toISOString();
}

/**
 * The list entry for one Ko-fi payment, exactly as before 2026-10. A donor who ticked Ko-fi's "make this private"
 * (`is_public: false`) is listed as "Anonymous" with no message: the list is public (every Stash install's Home
 * ticker, and `Access-Control-Allow-Origin: *`), so their name and message are never stored at all.
 */
export function supporterEntry(payload, now = new Date()) {
    const isPublic = payload.is_public !== false;
    // Ko-fi's `amount` is in the donation's own currency: a ¥1000 tip used to show as "$1000". Converted like the
    // goal is (src/access.js); a currency with no known rate shows 0 rather than a wrong dollar figure.
    const cents = toUsdCents(payload.amount, payload.currency);
    return {
        name: (isPublic && payload.from_name ? payload.from_name : "Anonymous").slice(0, 40),
        amountUsd: cents === null ? 0 : Math.floor(cents / 100),
        message: (isPublic ? payload.message || "" : "").slice(0, 280),
        timestamp: payload.timestamp || now.toISOString(),
    };
}

/**
 * supporter:<time>:<id>, from the donation's time and dedupeId (src/access.js). A Ko-fi retry has the same time
 * and id, so it writes the same key. Without an id (no dedupe possible, as for the goal) the key is made unique.
 */
export function supporterKey(timestamp, id, now = new Date()) {
    return `${SUPPORTER_PREFIX}${isoTime(timestamp) ?? now.toISOString()}:${id || crypto.randomUUID()}`;
}

/**
 * The { key, entry } a donation was stored under, worked out again from its payload: for a Ko-fi retry, so its
 * rebuild needn't wait for list() to show the key. Null when the key can't be worked out again (no dedupe id, or no
 * readable time: the key then holds something random or the moment it arrived).
 */
export function storedSupporter(payload, id) {
    if (!id || !isoTime(payload.timestamp)) return null;
    return { key: supporterKey(payload.timestamp, id), entry: supporterEntry(payload) };
}

/** Writes one supporter's key, with the entry in its metadata too when it fits. */
async function putSupporter(kv, key, entry) {
    const json = JSON.stringify(entry);
    await kv.put(key, json, enc.encode(json).length <= METADATA_MAX_BYTES ? { metadata: entry } : {});
}

/**
 * Stores one Ko-fi Donation or Subscription under its own key and returns { key, entry }, for rebuildSupporters
 * (whose list() may not show the key yet). Storing the same donation twice writes the same key with the same value.
 */
export async function addSupporter(env, payload, id, now = new Date()) {
    const entry = supporterEntry(payload, now);
    const key = supporterKey(payload.timestamp, id, now);
    await putSupporter(env.STASH_KV, key, entry);
    return { key, entry };
}

/**
 * Keys for the entries of `supporters` that have none: the list as it was before 2026-10, newest first. The same
 * list always gets the same keys, so converting it twice (two webhooks at once, or a stale read) writes nothing
 * new. A key's time is its entry's own, raised where needed so that it never goes back from oldest to newest, and
 * its id is the entry's place among them, oldest first: sorting the keys gives back the list's own order, even for
 * entries with the same time (the hand-seeded ones have). An entry with no readable time takes the one before it.
 */
export function legacyEntries(supporters) {
    const old = supporters.filter((s) => s && typeof s === "object" && typeof s.key !== "string").reverse();
    let floor = new Date(0).toISOString();
    return old.map((s, i) => {
        const time = isoTime(s.timestamp);
        if (time && time > floor) floor = time;
        return {
            key: `${SUPPORTER_PREFIX}${floor}:legacy-${String(i).padStart(4, "0")}`,
            entry: { name: s.name, amountUsd: s.amountUsd, message: s.message, timestamp: s.timestamp },
        };
    });
}

/** A supporter key's value, or null when it's gone or isn't an entry (a key added by hand can be anything: it's logged and left out). */
async function readEntry(kv, key) {
    const text = await kv.get(key);
    if (text === null) return null;
    try {
        const entry = JSON.parse(text);
        if (entry && typeof entry === "object" && !Array.isArray(entry)) return entry;
    } catch {
        // not JSON: logged below
    }
    console.error(`${key} doesn't hold a supporter entry, so it isn't listed`);
    return null;
}

/** Every supporter:* key, oldest first, with its metadata (undefined when there's none). One list() per 1,000. */
async function listSupporterKeys(kv) {
    const keys = new Map();
    let cursor;
    do {
        const page = await kv.list({ prefix: SUPPORTER_PREFIX, cursor });
        for (const k of page.keys) keys.set(k.name, k.metadata ?? undefined);
        cursor = page.list_complete ? undefined : page.cursor;
    } while (cursor);
    return keys;
}

/**
 * Rebuilds `supporters` from the supporter:* keys: the newest SUPPORTERS_LIMIT, newest first. [fresh] holds
 * { key, entry } pairs written just now, which list() may not show yet. Entries still without a key are moved to
 * keys of their own first (legacyEntries). Writes `supporters` only when it changed. Returns how many are listed.
 *
 * Throws, leaving `supporters` as it is, when that holds JSON it can't read: it may list donors nothing else has.
 * Two rebuilds at once can also make the second write fail (KV takes one write a second per key); the caller
 * then lets Ko-fi retry, and the daily cron rebuilds regardless.
 */
export async function rebuildSupporters(env, fresh = []) {
    const kv = env.STASH_KV;
    const raw = await kv.get(LIST_KEY);
    const current = raw ? JSON.parse(raw) : { supporters: [] };
    const legacy = legacyEntries(Array.isArray(current?.supporters) ? current.supporters : []);
    for (const { key, entry } of legacy) await putSupporter(kv, key, entry);

    const known = new Map([...legacy, ...fresh].map(({ key, entry }) => [key, entry]));
    const stored = await listSupporterKeys(kv);
    // Supporters already on the list whose keys list() doesn't show (yet: it can lag a minute behind a write, as
    // it does right after the old list's move) are looked up one by one, so a lagging list() never drops them. A
    // key deleted by hand reads as null, and leaves the list. Normally there are none, so no extra reads.
    const listed = Array.isArray(current?.supporters) ? current.supporters : [];
    const unseen = listed.map((s) => s?.key).filter((key) => typeof key === "string" && key.startsWith(SUPPORTER_PREFIX) && !stored.has(key) && !known.has(key));
    const found = await Promise.all(unseen.map((key) => readEntry(kv, key)));
    unseen.forEach((key, i) => found[i] && known.set(key, found[i]));
    const newest = [...new Set([...stored.keys(), ...known.keys()])].sort().reverse().slice(0, SUPPORTERS_LIMIT);
    // Entries come from what was just written, else the key's metadata, else (too big for metadata, or a key
    // added by hand without it) its value.
    const entries = await Promise.all(
        newest.map(async (key) => {
            const meta = stored.get(key);
            return known.get(key) ?? (meta && typeof meta === "object" ? meta : await readEntry(kv, key));
        }),
    );
    const supporters = newest.flatMap((key, i) => (entries[i] ? [{ ...entries[i], key }] : []));
    const json = JSON.stringify({ supporters });
    if (json !== raw) await kv.put(LIST_KEY, json);
    return supporters.length;
}
