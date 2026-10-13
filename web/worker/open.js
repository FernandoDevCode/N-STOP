/**
 * The open window: until OPEN_UNTIL (a var in wrangler.jsonc, an ISO time such as "2026-10-15T00:00:00Z"),
 * anyone can sign in to the web player, not only the people on the access list.
 *
 * While it's open, POST /access sends a code to any email, and the first sign-in of an email that isn't on the
 * list puts it there as { source: "open", at }, with metadata { source: "open" } so the admin page can count and
 * act on everyone who came in this way without reading each entry. Those entries are ordinary access-list
 * entries: they keep working after the window closes, until the admin page keeps them (they become
 * "approved") or removes them. Everyone already on the list is untouched.
 *
 * The pages say so too: home and the sign-in pages carry an "open" version of their early-access wording
 * (data-if="open" / data-if="not-open"), with the end time in data-fill="open-until". After OPEN_UNTIL passes,
 * or with it unset or not a time, everything is exactly as before, with no deploy needed to close it.
 */
import { clock } from "./crypto.js";
import { KEYS } from "./keys.js";

export const OPEN_SOURCE = "open";

/** OPEN_UNTIL as epoch milliseconds, or null when it's unset or isn't a time. */
export function openUntilMs(env) {
    const raw = env.OPEN_UNTIL;
    if (typeof raw !== "string" || !raw.trim()) return null;
    const t = Date.parse(raw.trim());
    return Number.isFinite(t) ? t : null;
}

/** Whether the window is open right now. */
export function isOpen(env, nowMs = clock.now()) {
    const until = openUntilMs(env);
    return until !== null && nowMs < until;
}

/** "Thu 15 Oct, 00:00 UTC": when the window closes, for the pages. */
export function openUntilText(env) {
    const until = openUntilMs(env);
    if (until === null) return "";
    const d = new Date(until);
    const day = d.toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" });
    const time = d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "UTC" });
    return `${day}, ${time} UTC`;
}

/** The page flags and text for the open wording: merged into every filled page. */
export function openFills(env) {
    const open = isOpen(env);
    return { flags: { open, "not-open": !open }, text: open ? { "open-until": openUntilText(env) } : {} };
}

/** Puts [h] on the access list as an open-window sign-up. Never called for someone already on the list. */
export async function grantOpenAccess(env, h) {
    const at = new Date(clock.now()).toISOString();
    await env.ACCESS_KV.put(KEYS.access(h), JSON.stringify({ source: OPEN_SOURCE, at }), { metadata: { source: OPEN_SOURCE, at } });
}

/** Every access-list key an open-window sign-up made (still tagged "open"), from the keys' metadata alone. */
export async function openSignupKeys(kv) {
    const out = [];
    let cursor;
    for (;;) {
        const res = await kv.list({ prefix: "access:", cursor });
        for (const { name, metadata } of res.keys) if (metadata?.source === OPEN_SOURCE) out.push(name);
        if (res.list_complete || !res.cursor) break;
        cursor = res.cursor;
    }
    return out;
}
