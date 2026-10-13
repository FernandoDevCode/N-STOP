import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { handle } from "../index.js";
import { isOpen, openSendAllowed, openSignups, openUntilText } from "../open.js";
import { forgetAllAccess, hasAccess } from "../session.js";
import { browser, env, fakeCtx, fakeKV, fakeRL, freezeClock, get, hashFor as hashEmail, post } from "./fakes.js";

const T0 = Date.UTC(2026, 9, 13, 12, 0, 0);
const UNTIL = "2026-10-15T12:00:00Z"; // 48 hours after T0
const STRANGER = "new.person@example.com";
const LISTED = "supporter@example.com";

beforeEach(() => forgetAllAccess());

async function run(e, req) {
    const ctx = fakeCtx();
    const res = await handle(req, e, ctx);
    await ctx.drain();
    return res;
}

/** One visitor's browser: asks for a code, then enters the code it was emailed. */
async function signIn(e, email, from = browser()) {
    const send = async (path, fields) => {
        const res = await run(e, post(path, fields, { headers: from.cookie ? { Cookie: from.cookie } : {} }));
        return from.keep(res);
    };
    const before = e.EMAIL.sent.length;
    const asked = await send("/access", { email });
    const mail = e.EMAIL.sent.slice(before).findLast((m) => m.to === email);
    if (!mail) return { asked, mailed: false, res: null };
    const code = /is (\d{8})\b/.exec(mail.text)[1];
    return { asked, mailed: true, res: await send("/access/verify", { email, code }) };
}

/** Runs [fn] with the Worker's clock at [ms], putting it back afterwards. */
async function at(ms, fn) {
    const clock = freezeClock(ms);
    try {
        return await fn(clock);
    } finally {
        clock.restore();
    }
}

const supporterEnv = async (over = {}) => {
    const h = await hashEmail(LISTED);
    return { h, e: env({ OPEN_UNTIL: UNTIL, ACCESS_KV: fakeKV({ [`access:${h}`]: { source: "kofi" } }), ...over }) };
};

test("the window is open only before OPEN_UNTIL, and only for a plain UTC time not absurdly far away", () =>
    at(T0, (clock) => {
        assert.equal(isOpen({ OPEN_UNTIL: UNTIL }), true);
        assert.equal(isOpen({ OPEN_UNTIL: "2026-10-15T12:00Z" }), true, "seconds are optional");
        assert.equal(isOpen({}), false);
        assert.equal(isOpen({ OPEN_UNTIL: "" }), false);
        assert.equal(isOpen({ OPEN_UNTIL: "next tuesday" }), false);
        assert.equal(isOpen({ OPEN_UNTIL: "2026-10-15" }), false, "a date alone is too loose");
        assert.equal(isOpen({ OPEN_UNTIL: "2026-10-15T12:00:00+02:00" }), false, "UTC only");
        assert.equal(isOpen({ OPEN_UNTIL: "2062-10-15T12:00:00Z" }), false, "a typo in the year can't open it for decades");
        assert.equal(openUntilText({ OPEN_UNTIL: UNTIL }), "Thu 15 Oct, 12:00 UTC");
        assert.equal(openUntilText({ OPEN_UNTIL: "2026-10-16T00:00:00Z" }), "Fri 16 Oct, 00:00 UTC", "midnight is 00:00, never 24:00");
        clock.advance(48 * 3600 * 1000);
        assert.equal(isOpen({ OPEN_UNTIL: UNTIL }), false, "closes at OPEN_UNTIL exactly");
    }));

test("closed: someone on neither list gets no code and can't sign in, exactly as before", () =>
    at(T0, async () => {
        const e = env({ OPEN_UNTIL: "2026-10-01T00:00:00Z" });
        const { asked, mailed } = await signIn(e, STRANGER);
        assert.equal(asked.status, 200);
        assert.equal(mailed, false);
        assert.equal(e.ACCESS_KV.map.size, 0, "nothing was written for them");
    }));

test("open: anyone gets a code and signs in, recorded as an open-window sign-up beside the access list", () =>
    at(T0, async () => {
        const e = env({ OPEN_UNTIL: UNTIL });
        const { mailed, res } = await signIn(e, STRANGER);
        assert.equal(mailed, true);
        assert.equal(res.status, 303);
        assert.match(res.headers.getSetCookie().join("\n"), /stash_access=/);
        const h = await hashEmail(STRANGER);
        assert.equal(await e.ACCESS_KV.get(`access:${h}`), null, "never written into the access list itself");
        const entry = e.ACCESS_KV.map.get(`open:${h}`);
        assert.deepEqual(JSON.parse(entry.value), { at: new Date(T0).toISOString() });
        assert.deepEqual(entry.opts.metadata, { at: new Date(T0).toISOString() });
        assert.deepEqual(await openSignups(e.ACCESS_KV), [{ h, legacy: false }]);
        assert.equal(e.ACCESS_KV.json(`opensends:2026-10-13`).n, 1, "the day's count of codes to strangers");
    }));

test("open: a supporter signs in as before, their entry untouched and no sign-up written for them", () =>
    at(T0, async () => {
        const { h, e } = await supporterEnv();
        const { res } = await signIn(e, LISTED);
        assert.equal(res.status, 303);
        assert.deepEqual(e.ACCESS_KV.json(`access:${h}`), { source: "kofi" });
        assert.equal(await e.ACCESS_KV.get(`open:${h}`), null);
        assert.equal(await e.ACCESS_KV.get("opensends:2026-10-13"), null, "supporters' codes never count against the cap");
    }));

test("open: the code-sent page and its cookie are the same for a supporter and a stranger (nothing leaks)", () =>
    at(T0, async () => {
        const { e } = await supporterEnv();
        const page = async (email) => {
            const res = await run(e, post("/access", { email }));
            const body = (await res.text()).replaceAll(email, "EMAIL").replace(/value="[0-9a-f]{32}"/g, 'value="ID"');
            const cookie = res.headers.get("set-cookie").replace(/=[0-9a-f]{32};/, "=ID;");
            return { status: res.status, body, cookie };
        };
        assert.deepEqual(await page(LISTED), await page(STRANGER));
    }));

test("open: past the day's cap, codes to strangers are held back silently, while supporters still get theirs", () =>
    at(T0, async () => {
        const { e } = await supporterEnv({ OPEN_DAILY_CODES: "2" });
        await e.ACCESS_KV.put("opensends:2026-10-13", JSON.stringify({ n: 2 }), { expirationTtl: 3600 });
        const stranger = await signIn(e, STRANGER);
        assert.equal(stranger.asked.status, 200, "the same page");
        assert.equal(stranger.mailed, false);
        const supporter = await signIn(e, LISTED);
        assert.equal(supporter.mailed, true);
    }));

test("open: past the per-minute cap, codes to strangers are held back too", () =>
    at(T0, async () => {
        const { e } = await supporterEnv({ OPEN_SEND_RL: fakeRL(0) });
        assert.equal((await signIn(e, STRANGER)).mailed, false);
        assert.equal((await signIn(e, LISTED)).mailed, true);
    }));

test("the day's count takes one write a second: a write that can't land is retried once, then the code is held back", () =>
    at(T0, async () => {
        const kv = fakeKV();
        const realPut = kv.put.bind(kv);
        let failures = 1;
        kv.put = async (...args) => {
            if (failures-- > 0) throw new Error("KV PUT failed: 429 Too Many Requests");
            return realPut(...args);
        };
        const noWait = async () => {};
        assert.equal(await openSendAllowed({ ACCESS_KV: kv }, noWait), true, "the retry lands");
        failures = 2;
        assert.equal(await openSendAllowed({ ACCESS_KV: kv }, noWait), false, "two misses: held back");
    }));

test("open: a wrong code writes nothing to either list", () =>
    at(T0, async () => {
        const e = env({ OPEN_UNTIL: UNTIL });
        const from = browser();
        from.keep(await run(e, post("/access", { email: STRANGER })));
        const real = /is (\d{8})\b/.exec(e.EMAIL.sent.at(-1).text)[1];
        const wrong = real === "12345678" ? "87654321" : "12345678";
        const res = await run(e, post("/access/verify", { email: STRANGER, code: wrong }, { headers: { Cookie: from.cookie } }));
        assert.equal(res.status, 400);
        const h = await hashEmail(STRANGER);
        assert.equal(await e.ACCESS_KV.get(`access:${h}`), null);
        assert.equal(await e.ACCESS_KV.get(`open:${h}`), null);
    }));

test("after the window closes, a sign-up keeps access and can still get a code to sign in again", () =>
    at(T0, async (clock) => {
        const e = env({ OPEN_UNTIL: UNTIL });
        await signIn(e, STRANGER);
        clock.advance(72 * 3600 * 1000);
        forgetAllAccess();
        assert.equal(isOpen(e), false);
        assert.equal(await hasAccess(e, await hashEmail(STRANGER)), true);
        const again = await signIn(e, STRANGER, browser());
        assert.equal(again.mailed, true);
        assert.equal(again.res.status, 303);
    }));

test("an older-format sign-up (access:<h> tagged open) still signs in and is still counted", () =>
    at(T0, async () => {
        const h = await hashEmail(STRANGER);
        const meta = { source: "open", at: "2026-10-13T01:00:00.000Z" };
        const e = env({ OPEN_UNTIL: UNTIL, ACCESS_KV: fakeKV({ [`access:${h}`]: { value: JSON.stringify(meta), metadata: meta } }) });
        assert.equal(await hasAccess(e, h), true);
        assert.deepEqual(await openSignups(e.ACCESS_KV), [{ h, legacy: true }]);
    }));

test("a code asked for while open but entered after it closes doesn't let a stranger in", () =>
    at(Date.parse(UNTIL) - 60 * 1000, async (clock) => {
        const e = env({ OPEN_UNTIL: UNTIL });
        const from = browser();
        from.keep(await run(e, post("/access", { email: STRANGER })));
        const code = /is (\d{8})\b/.exec(e.EMAIL.sent.at(-1).text)[1];
        clock.advance(2 * 60 * 1000);
        const res = await run(e, post("/access/verify", { email: STRANGER, code }, { headers: { Cookie: from.cookie } }));
        assert.equal(res.status, 400);
        assert.equal(await e.ACCESS_KV.get(`open:${await hashEmail(STRANGER)}`), null);
    }));

test("the pages follow the window: open wording with the end time while open, early-access wording otherwise", () =>
    at(T0, async () => {
        const open = await (await handle(get("/"), env({ OPEN_UNTIL: UNTIL }))).text();
        assert.match(open, /OPEN UNTIL <span data-fill="open-until">Thu 15 Oct, 12:00 UTC<\/span>/);
        assert.doesNotMatch(open, /EARLY ACCESS/);

        const sent = await (await run(env({ OPEN_UNTIL: UNTIL }), post("/access", { email: STRANGER }))).text();
        assert.match(sent, /A code is on its way to <strong data-fill="email">new\.person@example\.com<\/strong>/);
        assert.doesNotMatch(sent, /has early access/);

        const closed = await (await handle(get("/"), env())).text();
        assert.match(closed, /EARLY ACCESS/);
        assert.doesNotMatch(closed, /OPEN UNTIL/);
    }));
