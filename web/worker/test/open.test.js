import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { handle } from "../index.js";
import { isOpen, openSignupKeys, openUntilText } from "../open.js";
import { forgetAllAccess, hasAccess } from "../session.js";
import { browser, env, fakeCtx, fakeKV, freezeClock, get, hashFor as hashEmail, post } from "./fakes.js";

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
    const asked = await send("/access", { email });
    const mail = e.EMAIL.sent.findLast((m) => m.to === email);
    if (!mail) return { asked, mailed: false, res: null };
    const code = /is (\d{8})\b/.exec(mail.text)[1];
    return { asked, mailed: true, res: await send("/access/verify", { email, code }) };
}

test("the window is open only before OPEN_UNTIL, and off when it's unset or not a time", () => {
    const clock = freezeClock(T0);
    try {
        assert.equal(isOpen({ OPEN_UNTIL: UNTIL }), true);
        assert.equal(isOpen({}), false);
        assert.equal(isOpen({ OPEN_UNTIL: "" }), false);
        assert.equal(isOpen({ OPEN_UNTIL: "next tuesday" }), false);
        clock.advance(48 * 3600 * 1000);
        assert.equal(isOpen({ OPEN_UNTIL: UNTIL }), false, "closes at OPEN_UNTIL exactly");
        assert.equal(openUntilText({ OPEN_UNTIL: UNTIL }), "Thu 15 Oct, 12:00 UTC");
    } finally {
        clock.restore();
    }
});

test("closed: someone not on the list gets no code and can't sign in, exactly as before", async () => {
    const clock = freezeClock(T0);
    try {
        const e = env({ OPEN_UNTIL: "2026-10-01T00:00:00Z" });
        const { asked, mailed } = await signIn(e, STRANGER);
        assert.equal(asked.status, 200);
        assert.equal(mailed, false);
        assert.equal(e.ACCESS_KV.map.size, 0, "nothing was written for them");
    } finally {
        clock.restore();
    }
});

test("open: anyone gets a code, signs in, and lands on the list tagged as an open-window sign-up", async () => {
    const clock = freezeClock(T0);
    try {
        const e = env({ OPEN_UNTIL: UNTIL });
        const { mailed, res } = await signIn(e, STRANGER);
        assert.equal(mailed, true);
        assert.equal(res.status, 303);
        assert.match(res.headers.getSetCookie().join("\n"), /stash_access=/);
        const h = await hashEmail(STRANGER);
        const entry = e.ACCESS_KV.map.get(`access:${h}`);
        assert.deepEqual(JSON.parse(entry.value), { source: "open", at: new Date(T0).toISOString() });
        assert.equal(entry.opts.metadata.source, "open");
        assert.deepEqual(await openSignupKeys(e.ACCESS_KV), [`access:${h}`]);
    } finally {
        clock.restore();
    }
});

test("open: someone already on the list signs in as before, and their entry isn't touched or re-tagged", async () => {
    const clock = freezeClock(T0);
    try {
        const h = await hashEmail(LISTED);
        const e = env({ OPEN_UNTIL: UNTIL, ACCESS_KV: fakeKV({ [`access:${h}`]: { source: "kofi" } }) });
        const { res } = await signIn(e, LISTED);
        assert.equal(res.status, 303);
        assert.deepEqual(JSON.parse(e.ACCESS_KV.map.get(`access:${h}`).value), { source: "kofi" });
        assert.deepEqual(await openSignupKeys(e.ACCESS_KV), []);
    } finally {
        clock.restore();
    }
});

test("an open-window sign-up keeps access after the window closes (until the admin page keeps or removes them)", async () => {
    const clock = freezeClock(T0);
    try {
        const e = env({ OPEN_UNTIL: UNTIL });
        await signIn(e, STRANGER);
        clock.advance(72 * 3600 * 1000);
        forgetAllAccess();
        assert.equal(isOpen(e), false);
        assert.equal(await hasAccess(e, await hashEmail(STRANGER)), true);
    } finally {
        clock.restore();
    }
});

test("a code asked for while open but entered after it closes doesn't let a stranger in", async () => {
    const clock = freezeClock(Date.parse(UNTIL) - 60 * 1000);
    try {
        const e = env({ OPEN_UNTIL: UNTIL });
        const from = browser();
        from.keep(await run(e, post("/access", { email: STRANGER })));
        const code = /is (\d{8})\b/.exec(e.EMAIL.sent.at(-1).text)[1];
        clock.advance(2 * 60 * 1000);
        const res = await run(e, post("/access/verify", { email: STRANGER, code }, { headers: { Cookie: from.cookie } }));
        assert.equal(res.status, 400);
        assert.equal(await e.ACCESS_KV.get(`access:${await hashEmail(STRANGER)}`), null);
    } finally {
        clock.restore();
    }
});

test("the pages follow the window: open wording with the end time while open, early-access wording otherwise", async () => {
    const clock = freezeClock(T0);
    try {
        const open = await (await handle(get("/"), env({ OPEN_UNTIL: UNTIL }))).text();
        assert.match(open, /OPEN UNTIL <span data-fill="open-until">Thu 15 Oct, 12:00 UTC<\/span>/);
        assert.doesNotMatch(open, /EARLY ACCESS/);

        const sent = await (await run(env({ OPEN_UNTIL: UNTIL }), post("/access", { email: STRANGER }))).text();
        assert.match(sent, /A code is on its way to <strong data-fill="email">new\.person@example\.com<\/strong>/);
        assert.doesNotMatch(sent, /has early access/);

        const closed = await (await handle(get("/"), env())).text();
        assert.match(closed, /EARLY ACCESS/);
        assert.doesNotMatch(closed, /OPEN UNTIL/);
    } finally {
        clock.restore();
    }
});
