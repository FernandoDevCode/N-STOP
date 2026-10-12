/**
 * One-time sign-in codes, each tied to the browser that asked for it. code:<hash>:<browser> holds up to LIVE_CODES
 * live codes that one browser (its stash_signin id, worker/session.js) asked for, each as an HMAC keyed with
 * SESSION_SECRET over the email hash, that browser's id and the code (so even someone reading KV can't work a code
 * out, and a code only ever matches in the browser it was sent for), and its expiry (10 minutes), plus that
 * browser's wrong tries so far. Asking again in the same browser adds a code instead of replacing the one already
 * on its way. Any live code signs that browser in, and signing in clears them all. The fifth wrong try clears them
 * all too.
 *
 * Why per browser: with one record per email (before 2026-10), anyone who knew a supporter's email could send 5
 * wrong codes and clear the supporter's live codes. Now wrong tries from another browser count against that
 * browser's own record (or none), and asking from another browser writes only that browser's record, so neither can
 * touch this one. What every browser still shares is the email's sends (SENDS_PER_HOUR an hour, however many
 * browsers ask): someone asking for your email can use up that hour's code emails, but not cancel one you have.
 *
 * Guessing: each try has at most a 3-in-100-million chance (up to 3 live codes out of 100,000,000). A record allows
 * 5 wrong tries, and each new record costs one of the email's 5 code emails an hour, so a guesser gets about 25
 * tries an hour, every one of them emailing a code to the address's owner.
 *
 * Timing: a wrong code is answered without waiting on KV (the try is counted afterwards, in ctx.waitUntil). The
 * read itself can still be a little slower when a code record exists than when none does (web/README.md).
 *
 * KV isn't transactional and can lag up to a minute between locations, so tries landing at once can count as one,
 * and the 5-try count isn't exact. The tries are also rate-limited per email and browser (VERIFY_EMAIL_RL, 5 a
 * minute) and per address (VERIFY_IP_RL), but those limits are per Cloudflare location, so tries spread over many
 * locations at once can slip past the count a little.
 */
import { clock, eightDigitCode, hmacHex, sameHex } from "./crypto.js";
import { KEYS } from "./keys.js";

export const CODE_TTL_S = 10 * 60;
export const MAX_TRIES = 5;
export const LIVE_CODES = 3;
export const SENDS_PER_HOUR = 5;
const HOUR_S = 3600;
/** KV's shortest expiry. */
const MIN_TTL_S = 60;

const codeMac = (secret, h, browser, code) => hmacHex(secret, `code:${h}:${browser}:${code}`);
const nowS = () => Math.floor(clock.now() / 1000);

/** KV put options that expire at [exp] (unix seconds), never sooner than KV allows. */
const expiresAt = (exp) => (exp - nowS() >= MIN_TTL_S ? { expiration: exp } : { expirationTtl: MIN_TTL_S });

const liveCodes = (record, now) => (Array.isArray(record?.codes) ? record.codes.filter((c) => Number.isInteger(c?.exp) && c.exp > now && typeof c.c === "string") : []);

/** "1234 5678", "1234-5678" and " 12345678 " all mean 12345678. Anything else isn't a code. */
export function cleanCode(input) {
    const digits = String(input ?? "").replace(/[\s-]/g, "");
    return /^\d{8}$/.test(digits) ? digits : null;
}

/**
 * A fresh code for [h], asked for by [browser], added to the ones that browser still has live (the oldest dropped
 * past LIVE_CODES), or null when this email has had SENDS_PER_HOUR codes this hour, from any browser. That hourly
 * count is a KV counter, so it's approximate under concurrency and across locations. CODE_SEND_RL (one a minute,
 * checked before this) is tighter but per Cloudflare location too: together they keep one address from being
 * flooded, not to an exact number. Other browsers' codes for the same email are never touched.
 */
export async function issueCode(env, secret, h, browser) {
    const kv = env.ACCESS_KV;
    const now = nowS();
    const sends = await kv.get(KEYS.sends(h), "json");
    const window = sends && sends.until > now ? sends : { n: 0, until: now + HOUR_S };
    if (window.n >= SENDS_PER_HOUR) return null;
    const key = KEYS.code(h, browser);
    const record = await kv.get(key, "json");
    const live = liveCodes(record, now);
    const n = live.length && Number.isInteger(record.n) ? record.n : 0;
    const code = eightDigitCode();
    const codes = [...live, { c: await codeMac(secret, h, browser, code), exp: now + CODE_TTL_S }].slice(-LIVE_CODES);
    await kv.put(key, JSON.stringify({ codes, n }), { expirationTtl: CODE_TTL_S });
    await kv.put(KEYS.sends(h), JSON.stringify({ n: window.n + 1, until: window.until }), expiresAt(window.until));
    return code;
}

/**
 * True for any live code [browser] asked for [h], within its tries; all of them are cleared then. A wrong or missing
 * code is false at once: the bookkeeping (counting the try, clearing expired or used-up codes) goes to [later].
 */
export async function checkCode(env, secret, h, browser, code, later) {
    const kv = env.ACCESS_KV;
    const key = KEYS.code(h, browser);
    const [record, mac] = await Promise.all([kv.get(key, "json"), codeMac(secret, h, browser, code ?? "")]);
    const live = liveCodes(record, nowS());
    if (!record) return false;
    if (!live.length || !(record.n < MAX_TRIES)) {
        later(kv.delete(key));
        return false;
    }
    if (code && live.some((c) => sameHex(mac, c.c))) {
        await kv.delete(key);
        return true;
    }
    const n = record.n + 1;
    const lastExp = Math.max(...live.map((c) => c.exp));
    later(n >= MAX_TRIES ? kv.delete(key) : kv.put(key, JSON.stringify({ codes: live, n }), expiresAt(lastExp)));
    return false;
}

/**
 * Deletes every browser's codes for [h] (the admin page, removing an email). KV's list() can lag a little, so a code
 * asked for in the last minute may survive it; verify checks the access list after the code anyway.
 */
export async function clearCodes(env, h) {
    const kv = env.ACCESS_KV;
    let cursor;
    do {
        const page = await kv.list({ prefix: KEYS.codePrefix(h), cursor });
        await Promise.all(page.keys.map((k) => kv.delete(k.name)));
        cursor = page.list_complete ? undefined : page.cursor;
    } while (cursor);
}
