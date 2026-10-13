/**
 * The open window: until OPEN_UNTIL (a var in wrangler.jsonc, a UTC time such as "2026-10-15T01:00:00Z"),
 * anyone can sign in to the web player, not only the people on the access list.
 *
 * While it's open, POST /access sends a code to any email, and the first sign-in of an email that isn't on the
 * list records it as an open-window sign-up: open:<h> = { at }, with metadata { at }. That key sits beside the
 * access list, never in it, so nothing done to the sign-ups (keeping or removing them on the admin page) can touch
 * a supporter's access:<h>, and a sign-in never overwrites one. hasAccess (session.js) lets either key in. Sign-ups
 * keep working after the window closes, until the admin page keeps them (they join the access list as approved)
 * or removes them. Before 2026-10-13 ~02:00 UTC a sign-up was written as access:<h> tagged { source: "open" } in
 * its metadata; those still count, and the admin page handles them the same way.
 *
 * Codes to emails on neither list are what make the window a way to send mail to anyone, so they're capped
 * (openSendAllowed): a few a minute per Cloudflare location, and a daily total (OPEN_DAILY_CODES). The check runs
 * after the response, so the page is the same whether or not a code went out, and whether or not the email is
 * on the list. A cap only ever holds back codes to strangers: supporters still get theirs.
 *
 * The pages say so too: home and the sign-in pages carry an "open" version of their early-access wording
 * (data-if="open" / data-if="not-open"), with the end time in data-fill="open-until". After OPEN_UNTIL passes,
 * or with it unset or not a plain UTC time, everything is exactly as before, with no deploy needed to close it.
 */
import { clock } from "./crypto.js";
import { KEYS } from "./keys.js";

/** The legacy tag on an access:<h> written as an open-window sign-up (see above). */
export const OPEN_SOURCE = "open";
/** Codes a day to emails on neither list, unless OPEN_DAILY_CODES says otherwise. */
export const OPEN_DAILY_CODES = 1500;
/** The longest a window can be set to run, so a typo in the year can't open it for decades. */
const MAX_WINDOW_MS = 31 * 24 * 3600 * 1000;
/** Only a plain UTC time: 2026-10-15T01:00Z or 2026-10-15T01:00:00Z (Date.parse alone accepts far more). */
const UTC_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?Z$/;
const DAY_S = 24 * 3600;

/** OPEN_UNTIL as epoch milliseconds, or null when it's unset, not a plain UTC time, or absurdly far away. */
export function openUntilMs(env, nowMs = clock.now()) {
    const raw = typeof env.OPEN_UNTIL === "string" ? env.OPEN_UNTIL.trim() : "";
    if (!UTC_TIME.test(raw)) return null;
    const t = Date.parse(raw);
    return Number.isFinite(t) && t - nowMs <= MAX_WINDOW_MS ? t : null;
}

/** Whether the window is open right now. */
export function isOpen(env, nowMs = clock.now()) {
    const until = openUntilMs(env, nowMs);
    return until !== null && nowMs < until;
}

/** "Thu 15 Oct, 01:00 UTC": when the window closes, for the pages. */
export function openUntilText(env) {
    const raw = typeof env.OPEN_UNTIL === "string" ? env.OPEN_UNTIL.trim() : "";
    const until = UTC_TIME.test(raw) ? Date.parse(raw) : NaN;
    if (!Number.isFinite(until)) return "";
    const d = new Date(until);
    const day = d.toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" });
    const time = d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hourCycle: "h23", timeZone: "UTC" });
    return `${day}, ${time} UTC`;
}

/** The page flags and text for the open wording: merged into every filled page. */
export function openFills(env) {
    const open = isOpen(env);
    return { flags: { open, "not-open": !open }, text: open ? { "open-until": openUntilText(env) } : {} };
}

/** Records [h] as an open-window sign-up. Writes only open:<h>, so it can never overwrite a supporter's entry. */
export async function grantOpenAccess(env, h) {
    const at = new Date(clock.now()).toISOString();
    await env.ACCESS_KV.put(KEYS.open(h), JSON.stringify({ at }), { metadata: { at } });
}

/**
 * Whether one more code may go to an email on neither list. Called after the response, only while the window is
 * open, only for such emails: a few a minute per Cloudflare location (OPEN_SEND_RL), and OPEN_DAILY_CODES a UTC day.
 * The daily count is a KV counter, which takes one write a second; if a write can't land the code is held back, so
 * a burst can't slip past the count.
 */
export async function openSendAllowed(env, wait = (ms) => new Promise((r) => setTimeout(r, ms))) {
    if (env.OPEN_SEND_RL && !(await env.OPEN_SEND_RL.limit({ key: "open" })).success) {
        console.warn("open window: code held back, per-minute cap");
        return false;
    }
    const parsed = Number.parseInt(env.OPEN_DAILY_CODES, 10);
    const cap = parsed > 0 ? parsed : OPEN_DAILY_CODES;
    const key = KEYS.openSends(new Date(clock.now()).toISOString().slice(0, 10));
    for (let attempt = 0; attempt < 2; attempt++) {
        const n = Number((await env.ACCESS_KV.get(key, "json"))?.n) || 0;
        if (n >= cap) {
            console.warn(`open window: code held back, the day's ${cap} codes are used`);
            return false;
        }
        try {
            await env.ACCESS_KV.put(key, JSON.stringify({ n: n + 1 }), { expirationTtl: 2 * DAY_S });
            return true;
        } catch (err) {
            if (attempt === 0) await wait(1100); // KV takes one write a second per key
            else console.warn("open window: code held back, count not written:", err && err.message);
        }
    }
    return false;
}

/**
 * Every open-window sign-up, as { h, legacy }: the open:<h> keys, plus the older access:<h> entries tagged "open"
 * in their metadata. From key listings alone (each 1,000 keys a call), no reads.
 */
export async function openSignups(kv) {
    const out = [];
    for (const [prefix, legacy] of [[KEYS.openPrefix, false], [KEYS.accessPrefix, true]]) {
        let cursor;
        for (;;) {
            const res = await kv.list({ prefix, cursor });
            for (const { name, metadata } of res.keys) {
                if (!legacy || metadata?.source === OPEN_SOURCE) out.push({ h: name.slice(prefix.length), legacy });
            }
            if (res.list_complete || !res.cursor) break;
            cursor = res.cursor;
        }
    }
    return out;
}
