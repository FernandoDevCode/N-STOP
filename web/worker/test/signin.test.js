import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import worker, { handle } from "../index.js";
import { hashFor as hashEmail } from "./fakes.js";
import { forgetAllAccess } from "../session.js";
import { assertSignedOutHome, browser, env, fakeCtx, fakeEmail, fakeKV, fakeRL, freezeClock, get, post } from "./fakes.js";

const EMAIL = "supporter@example.com";
const T0 = Date.UTC(2026, 9, 4, 12, 0, 0);

beforeEach(() => forgetAllAccess());

async function listedEnv(over = {}) {
    const h = await hashEmail(EMAIL);
    return { h, e: env({ ACCESS_KV: fakeKV({ [`access:${h}`]: { source: "kofi" } }), ...over }) };
}

/** Runs a request and then the work it left for after the response (ctx.waitUntil). */
async function run(e, req) {
    const ctx = fakeCtx();
    const res = await handle(req, e, ctx);
    await ctx.drain();
    return res;
}

// Each test env has one browser its requests come from, unless a test names another ({ from }). It sends its cookies
// along and keeps the ones each response sets, so asking for a code and then entering it is one visitor, as in life.
const browsers = new WeakMap();
function browserOf(e) {
    if (!browsers.has(e)) browsers.set(e, browser());
    return browsers.get(e);
}

async function send(e, path, fields, { from = browserOf(e), headers = {}, ...opts } = {}) {
    const res = await run(e, post(path, fields, { ...opts, headers: from.cookie ? { Cookie: from.cookie, ...headers } : headers }));
    return from.keep(res);
}

const askForCode = (e, email = EMAIL, opts) => send(e, "/access", { email }, opts);
const tryCode = (e, code, email = EMAIL, opts) => send(e, "/access/verify", { email, code }, opts);
const codeFrom = (mail) => /is (\d{8})\b/.exec(mail.text)[1];
const wrongFor = (...codes) => ["00000000", "11111111", "22222222", "33333333", "44444444"].find((c) => !codes.includes(c));
const signinIdOf = (b) => b.jar.get("stash_signin");
/** Where [from]'s codes for [h] are kept: code:<h>:<its stash_signin id>. */
const recordKey = (e, h, from = browserOf(e)) => `code:${h}:${signinIdOf(from)}`;
const SIGNIN_COOKIE = /^stash_signin=([0-9a-f]{32}); HttpOnly; Secure; SameSite=Lax; Path=\/access; Max-Age=1800$/;

test("the whole flow: email, an 8-digit code by email, code back from the same browser, cookie, the real home page", async () => {
    const { h, e } = await listedEnv();
    const res = await askForCode(e, "  Supporter@Example.com ");
    assert.equal(res.status, 200);
    assert.match(res.headers.get("set-cookie"), SIGNIN_COOKIE);
    const page = await res.text();
    assert.match(page, /id="sent"/);
    assert.match(page, /<strong data-fill="email">supporter@example\.com<\/strong>/);
    assert.match(page, /name="email" value="supporter@example\.com"/);

    assert.equal(e.EMAIL.sent.length, 1);
    const mail = e.EMAIL.sent[0];
    assert.equal(mail.to, EMAIL);
    assert.deepEqual(mail.from, { email: "access@stashfm.app", name: "Stash" });
    const code = codeFrom(mail);
    assert.match(code, /^\d{8}$/);
    assert.match(mail.text, /in the browser where you asked for it/);

    // Only an HMAC of the code is stored, never the code, and under this browser's id.
    const stored = e.ACCESS_KV.json(recordKey(e, h));
    assert.equal(stored.n, 0);
    assert.equal(stored.codes.length, 1);
    assert.ok(!JSON.stringify(stored).includes(code));
    assert.equal(e.ACCESS_KV.json(`code:${h}`), null, "nothing per email alone");

    const id = signinIdOf(browserOf(e));
    const verified = await tryCode(e, `${code.slice(0, 4)} ${code.slice(4)}`);
    assert.equal(verified.status, 303);
    assert.equal(verified.headers.get("location"), "/");
    const [session, cleared] = verified.headers.getSetCookie();
    assert.match(session, /^stash_access=[\w-]+\.[\w-]+; HttpOnly; Secure; SameSite=Lax; Path=\/; Max-Age=7776000$/);
    assert.equal(cleared, "stash_signin=; HttpOnly; Secure; SameSite=Lax; Path=/access; Max-Age=0", "signing in deletes the pending cookie");
    assert.equal(e.ACCESS_KV.json(`code:${h}:${id}`), null, "a used code is gone");

    const home = await handle(get("/", { Cookie: session.split(";")[0] }), e);
    assert.equal(home.status, 200);
    assert.match(await home.text(), /id="home"/);
    assert.equal(home.headers.get("cache-control"), "no-store");
    const replay = browser();
    replay.jar.set("stash_signin", id);
    assert.equal((await tryCode(e, code, EMAIL, { from: replay })).status, 400, "the same code can't be used twice");
});

test("through the whole Worker (security headers and all), signing in sets the session cookie and deletes the pending one", async () => {
    const { e } = await listedEnv();
    const b = browserOf(e);
    const ctx = fakeCtx();
    b.keep(await worker.fetch(post("/access", { email: EMAIL }), e, ctx));
    await ctx.drain();
    const res = await worker.fetch(post("/access/verify", { email: EMAIL, code: codeFrom(e.EMAIL.sent[0]) }, { headers: { Cookie: b.cookie } }), e, fakeCtx());
    assert.equal(res.status, 303);
    assert.ok(res.headers.get("content-security-policy"));
    const cookies = res.headers.getSetCookie();
    assert.equal(cookies.length, 2);
    assert.match(cookies[0], /^stash_access=[\w-]+\.[\w-]+;/);
    assert.match(cookies[1], /^stash_signin=; .*Path=\/access; Max-Age=0$/);
});

test("the code is in the email's body only, never its subject (lock screens, inbox previews)", async () => {
    const { e } = await listedEnv();
    await askForCode(e);
    const mail = e.EMAIL.sent[0];
    const code = codeFrom(mail);
    assert.equal(mail.subject, "Your Stash sign-in code");
    assert.ok(mail.text.includes(code) && mail.html.includes(code));
});

test("the 'code sent' page and its cookie are the same whether or not the email is on the list, and only listed emails get mail", async () => {
    const { e } = await listedEnv();
    const [a, b] = [browser(), browser()];
    const listed = await askForCode(e, EMAIL, { from: a });
    const other = await askForCode(e, "stranger@example.com", { from: b });
    assert.equal(listed.status, other.status);
    const strip = (html) => html.replaceAll("supporter@example.com", "X").replaceAll("stranger@example.com", "X");
    assert.equal(strip(await listed.text()), strip(await other.text()));
    const shape = (res) => res.headers.get("set-cookie").replace(/=[0-9a-f]{32};/, "=ID;");
    assert.equal(shape(listed), shape(other));
    assert.notEqual(signinIdOf(a), signinIdOf(b), "a random id per browser");
    assert.equal(e.EMAIL.sent.length, 1);
    assert.equal(e.EMAIL.sent[0].to, EMAIL);
    assert.equal([...e.ACCESS_KV.map.keys()].filter((k) => k.startsWith("code:")).length, 1);
});

test("the pending cookie: asking again in the same browser keeps its id (and restarts its time); a malformed one is replaced", async () => {
    const { e } = await listedEnv();
    const first = await askForCode(e);
    const id = SIGNIN_COOKIE.exec(first.headers.get("set-cookie"))[1];
    const again = await askForCode(e);
    assert.equal(SIGNIN_COOKIE.exec(again.headers.get("set-cookie"))[1], id);
    const odd = browser();
    odd.jar.set("stash_signin", "../../admin");
    const fixed = await askForCode(e, EMAIL, { from: odd });
    assert.notEqual(SIGNIN_COOKIE.exec(fixed.headers.get("set-cookie"))[1], "../../admin");
    assert.ok(![...e.ACCESS_KV.map.keys()].some((k) => k.includes("..")), "a cookie value never reaches a KV key unchecked");
});

const formSigninId = (html) => /name="signin" value="([0-9a-f]{32})"/.exec(html)?.[1];

test("every email form comes with a fresh random sign-in id: the home page, /access, and the pages that offer a new code", async () => {
    const { e } = await listedEnv();
    const ids = [];
    for (const path of ["/", "/", "/access"]) ids.push(formSigninId(await (await handle(get(path), e)).text()));
    ids.push(formSigninId(await (await tryCode(e, "12345678", EMAIL, { from: browser() })).text())); // other-browser
    await askForCode(e);
    ids.push(formSigninId(await (await tryCode(e, "12345678")).text())); // code-failed
    assert.ok(ids.every(Boolean), JSON.stringify(ids));
    assert.equal(new Set(ids).size, ids.length);
});

test("a double click with no pending cookie yet: both posts carry the form's id, so the one code sent works whichever answer the browser keeps", async () => {
    const { e } = await listedEnv({ CODE_SEND_RL: fakeRL(1) });
    const id = formSigninId(await (await handle(get("/"), e)).text());
    // Neither post has a cookie: the browser hasn't seen an answer yet when the second click goes.
    const [first, second] = [browser(), browser()];
    const a = await send(e, "/access", { email: EMAIL, signin: id }, { from: first });
    const b = await send(e, "/access", { email: EMAIL, signin: id }, { from: second });
    assert.equal(e.EMAIL.sent.length, 1, "one code (one a minute)");
    assert.equal(SIGNIN_COOKIE.exec(a.headers.get("set-cookie"))[1], id);
    assert.equal(SIGNIN_COOKIE.exec(b.headers.get("set-cookie"))[1], id);
    assert.equal((await tryCode(e, codeFrom(e.EMAIL.sent[0]), EMAIL, { from: second })).status, 303);
});

test("the form's id is only a fallback: a browser's own cookie wins, and a malformed form id is never used", async () => {
    const { e } = await listedEnv();
    await askForCode(e);
    const own = signinIdOf(browserOf(e));
    const res = await send(e, "/access", { email: EMAIL, signin: "f".repeat(32) });
    assert.equal(SIGNIN_COOKIE.exec(res.headers.get("set-cookie"))[1], own);
    const junk = await send(e, "/access", { email: EMAIL, signin: "../../admin" }, { from: browser() });
    assert.match(junk.headers.get("set-cookie"), SIGNIN_COOKIE);
    assert.ok(![...e.ACCESS_KV.map.keys()].some((k) => k.includes("..")));
});

test("the code and email work happen after the response (no timing difference to measure)", async () => {
    const { e } = await listedEnv();
    const ctx = fakeCtx();
    await handle(post("/access", { email: EMAIL }), e, ctx);
    assert.equal(e.EMAIL.sent.length, 0, "sent before the response");
    assert.equal(ctx.work.length, 1);
    await ctx.drain();
    assert.equal(e.EMAIL.sent.length, 1);
});

test("a wrong code is answered without waiting on KV: the try is counted after the response", async () => {
    const { h, e } = await listedEnv();
    await askForCode(e);
    const code = codeFrom(e.EMAIL.sent[0]);
    // Any write to the code record now hangs until released. A response that waited on it would never come.
    const release = e.ACCESS_KV.holdPuts((k) => k.startsWith("code:"));
    const ctx = fakeCtx();
    const res = await handle(post("/access/verify", { email: EMAIL, code: wrongFor(code) }, { headers: { Cookie: browserOf(e).cookie } }), e, ctx);
    assert.equal(res.status, 400);
    assert.equal(e.ACCESS_KV.json(recordKey(e, h)).n, 0, "not counted yet");
    release();
    await ctx.drain();
    assert.equal(e.ACCESS_KV.json(recordKey(e, h)).n, 1);
});

test("one failure page for every kind of bad code, listed or not", async () => {
    const { e } = await listedEnv();
    await askForCode(e, EMAIL);
    const code = codeFrom(e.EMAIL.sent[0]);
    const cases = [
        { email: EMAIL, code: wrongFor(code) }, // wrong code, listed
        { email: "stranger@example.com", code: wrongFor(code) }, // never had a code
        { email: EMAIL, code: code.slice(0, 7) }, // not eight digits
        { email: EMAIL, code: "123456" }, // an old six-digit code
        { email: "not-an-email", code },
    ];
    const pages = [];
    for (const { email, code: c } of cases) {
        const res = await tryCode(e, c, email);
        assert.equal(res.status, 400);
        assert.equal(res.headers.get("set-cookie"), null);
        pages.push((await res.text()).replace(/value="[^"]*"/g, 'value=""'));
    }
    assert.ok(pages.every((p) => p === pages[0] && /id="code-failed"/.test(p)));
});

test("five wrong tries use this browser's codes up: the right one fails after that", async () => {
    const { h, e } = await listedEnv();
    await askForCode(e);
    const code = codeFrom(e.EMAIL.sent[0]);
    const key = recordKey(e, h);
    for (let i = 1; i <= 4; i++) {
        await tryCode(e, wrongFor(code));
        assert.equal(e.ACCESS_KV.json(key).n, i);
    }
    await tryCode(e, wrongFor(code));
    assert.equal(e.ACCESS_KV.json(key), null);
    assert.equal((await tryCode(e, code)).status, 400);
});

test("a code expires after 10 minutes", async () => {
    const clock = freezeClock(T0);
    try {
        const { e } = await listedEnv();
        await askForCode(e);
        const code = codeFrom(e.EMAIL.sent[0]);
        clock.advance(10 * 60 * 1000 + 1000);
        assert.equal((await tryCode(e, code)).status, 400);
    } finally {
        clock.restore();
    }
});

test("a code still works at 9 minutes, after a wrong try, and the try keeps its original expiry", async () => {
    const clock = freezeClock(T0);
    try {
        const { h, e } = await listedEnv();
        await askForCode(e);
        const code = codeFrom(e.EMAIL.sent[0]);
        clock.advance(9 * 60 * 1000 + 30_000);
        await tryCode(e, wrongFor(code));
        assert.equal(e.ACCESS_KV.map.get(recordKey(e, h)).opts.expirationTtl, 60, "under a minute left: KV's shortest expiry");
        assert.equal((await tryCode(e, code)).status, 303);
    } finally {
        clock.restore();
    }
});

test("asking again in the same browser doesn't cancel a code still on its way: the last 3 all work, and signing in clears them all", async () => {
    const clock = freezeClock(T0);
    try {
        const { h, e } = await listedEnv();
        for (let i = 0; i < 4; i++) {
            await askForCode(e);
            clock.advance(61_000); // past the one-a-minute send limit
        }
        const [first, second, third, fourth] = e.EMAIL.sent.map(codeFrom);
        const key = recordKey(e, h);
        const id = signinIdOf(browserOf(e));
        assert.equal(e.ACCESS_KV.json(key).codes.length, 3);
        if (![second, third, fourth].includes(first)) assert.equal((await tryCode(e, first)).status, 400, "the oldest of 4 is dropped");
        assert.equal((await tryCode(e, third)).status, 303);
        assert.equal(e.ACCESS_KV.json(key), null);
        const same = browser();
        same.jar.set("stash_signin", id);
        if (third !== fourth) assert.equal((await tryCode(e, fourth, EMAIL, { from: same })).status, 400, "all cleared on success");
    } finally {
        clock.restore();
    }
});

test("a code entered in another browser fails with a page that says so; a new code asked for there works there", async () => {
    const { e } = await listedEnv();
    const phone = browser();
    await askForCode(e, EMAIL, { from: phone });
    const code = codeFrom(e.EMAIL.sent[0]);
    const laptop = browser();
    const res = await send(e, "/access/verify", { email: EMAIL, code, next: "/player" }, { from: laptop });
    assert.equal(res.status, 400);
    assert.equal(res.headers.get("set-cookie"), null);
    const page = await res.text();
    assert.match(page, /id="other-browser"/);
    assert.match(page, /<strong data-fill="email">supporter@example\.com<\/strong>/);
    assert.match(page, /name="email" value="supporter@example\.com"/);
    assert.match(page, /name="next" value="\/player"/, "the way back to /player is kept");
    // The page's "Send a new code" asks from this browser, and that code works here.
    await send(e, "/access", { email: EMAIL, next: "/player" }, { from: laptop });
    const signedIn = await send(e, "/access/verify", { email: EMAIL, code: codeFrom(e.EMAIL.sent[1]), next: "/player" }, { from: laptop });
    assert.equal(signedIn.status, 303);
    assert.equal(signedIn.headers.get("location"), "/player");
    // ...and the first browser's code still works in the first browser.
    assert.equal((await tryCode(e, code, EMAIL, { from: phone })).status, 303);
});

test("the 'other browser' page depends only on the browser: the same for any email, listed or not", async () => {
    const { e } = await listedEnv();
    const pages = [];
    for (const email of [EMAIL, "stranger@example.com"]) {
        const res = await tryCode(e, "12345678", email, { from: browser() });
        assert.equal(res.status, 400);
        pages.push((await res.text()).replaceAll(email, "X").replace(/name="signin" value="[0-9a-f]{32}"/, 'name="signin" value="ID"'));
    }
    assert.equal(pages[0], pages[1]);
    assert.match(pages[0], /id="other-browser"/);
});

test("a browser with its own pending cookie can't use another browser's code: the usual failure, and the code still works where it was asked for", async () => {
    const { e } = await listedEnv();
    const victim = browser();
    await askForCode(e, EMAIL, { from: victim });
    const code = codeFrom(e.EMAIL.sent[0]);
    const other = browser();
    await askForCode(e, "someone-else@example.com", { from: other }); // any ask gives it a cookie
    const res = await tryCode(e, code, EMAIL, { from: other });
    assert.equal(res.status, 400);
    assert.match(await res.text(), /id="code-failed"/);
    assert.equal((await tryCode(e, code, EMAIL, { from: victim })).status, 303);
});

test("wrong guesses and code requests from someone else's browser can't touch this browser's code", async () => {
    const clock = freezeClock(T0);
    try {
        const { h, e } = await listedEnv();
        const victim = browser();
        await askForCode(e, EMAIL, { from: victim });
        const code = codeFrom(e.EMAIL.sent[0]);
        const before = e.ACCESS_KV.map.get(recordKey(e, h, victim)).value;
        // An attacker who knows the email: asks for codes from their own browser, then guesses there, well past 5.
        const attacker = browser();
        for (let i = 0; i < 2; i++) {
            clock.advance(61_000);
            await askForCode(e, EMAIL, { from: attacker });
        }
        for (let i = 0; i < 12; i++) await tryCode(e, wrongFor(code), EMAIL, { from: attacker });
        // ...and from browsers with made-up ids.
        for (let i = 0; i < 6; i++) {
            const made = browser();
            made.jar.set("stash_signin", String(i).repeat(32));
            await tryCode(e, wrongFor(code), EMAIL, { from: made });
        }
        assert.equal(e.ACCESS_KV.map.get(recordKey(e, h, victim)).value, before, "the victim's record is untouched");
        assert.equal(e.ACCESS_KV.json(recordKey(e, h, attacker)), null, "the attacker used up only their own codes");
        assert.equal((await tryCode(e, code, EMAIL, { from: victim })).status, 303);
    } finally {
        clock.restore();
    }
});

test("the code's HMAC includes the browser: a copy of one browser's record under another's id doesn't let the code in", async () => {
    const { h, e } = await listedEnv();
    const victim = browser();
    await askForCode(e, EMAIL, { from: victim });
    const code = codeFrom(e.EMAIL.sent[0]);
    const thief = browser();
    thief.jar.set("stash_signin", "f".repeat(32));
    await e.ACCESS_KV.put(`code:${h}:${"f".repeat(32)}`, e.ACCESS_KV.map.get(recordKey(e, h, victim)).value, { expirationTtl: 600 });
    assert.equal((await tryCode(e, code, EMAIL, { from: thief })).status, 400);
});

test("one code email a minute per address: a second ask that minute sends nothing but looks the same", async () => {
    const { e } = await listedEnv({ CODE_SEND_RL: fakeRL(1) });
    const a = await askForCode(e);
    const b = await askForCode(e);
    assert.equal(e.EMAIL.sent.length, 1);
    assert.equal(await a.text(), await b.text());
});

test("at most 5 codes an hour per email: the 6th gets the same page and no email", async () => {
    const clock = freezeClock(T0);
    try {
        const { h, e } = await listedEnv();
        for (let i = 0; i < 6; i++) {
            const res = await askForCode(e, EMAIL);
            assert.equal(res.status, 200);
            assert.match(await res.text(), /id="sent"/);
            clock.advance(61_000);
        }
        assert.equal(e.EMAIL.sent.length, 5);
        assert.equal(e.ACCESS_KV.json(`sends:${h}`).n, 5);
        clock.advance(3600 * 1000);
        await askForCode(e, EMAIL);
        assert.equal(e.EMAIL.sent.length, 6, "a new hour, a new allowance");
    } finally {
        clock.restore();
    }
});

test("every browser shares the email's 5 code emails an hour, but using them up never voids a code already sent", async () => {
    const clock = freezeClock(T0);
    try {
        const { e } = await listedEnv();
        const victim = browser();
        await askForCode(e, EMAIL, { from: victim });
        const code = codeFrom(e.EMAIL.sent[0]);
        for (let i = 0; i < 5; i++) {
            clock.advance(61_000);
            await askForCode(e, EMAIL, { from: browser() });
        }
        assert.equal(e.EMAIL.sent.length, 5, "the hour's 5, across every browser");
        clock.advance(61_000);
        await askForCode(e, EMAIL, { from: victim });
        assert.equal(e.EMAIL.sent.length, 5, "none left this hour, for the victim either");
        assert.equal((await tryCode(e, code, EMAIL, { from: victim })).status, 303, "but their code still works");
    } finally {
        clock.restore();
    }
});

test("someone removed while their code is on the way can't sign in with it", async () => {
    const { h, e } = await listedEnv();
    await askForCode(e);
    const code = codeFrom(e.EMAIL.sent[0]);
    await e.ACCESS_KV.delete(`access:${h}`);
    assert.equal((await tryCode(e, code)).status, 400);
});

test("if the email doesn't send, the visitor still sees the usual page (and it's logged)", async () => {
    const { e } = await listedEnv({ EMAIL: fakeEmail({ failWith: "E_RATE_LIMIT_EXCEEDED" }) });
    const res = await askForCode(e, EMAIL);
    assert.equal(res.status, 200);
    assert.match(await res.text(), /id="sent"/);
});

test("an address with an apostrophe works", async () => {
    const h = await hashEmail("o'brien@example.com");
    const e = env({ ACCESS_KV: fakeKV({ [`access:${h}`]: { source: "manual" } }) });
    const res = await askForCode(e, "O'Brien@example.com");
    assert.equal(res.status, 200);
    assert.equal(e.EMAIL.sent[0].to, "o'brien@example.com");
    assert.equal((await tryCode(e, codeFrom(e.EMAIL.sent[0]), "o'brien@example.com")).status, 303);
});

test("forms must come from this site: no Origin, or another one, is refused; Sec-Fetch-Site same-origin is fine", async () => {
    const { e } = await listedEnv();
    for (const opts of [{ origin: null }, { origin: "https://evil.example" }, { origin: null, headers: { "Sec-Fetch-Site": "cross-site" } }]) {
        for (const path of ["/access", "/access/verify", "/request"]) {
            const res = await run(e, post(path, { email: EMAIL, code: "12345678" }, opts));
            assert.equal(res.status, 403, `${path} ${JSON.stringify(opts)}`);
            assert.match(await res.text(), /id="problem"/);
            assert.equal(res.headers.get("set-cookie"), null, "no pending cookie for a refused form");
        }
    }
    assert.equal(e.EMAIL.sent.length, 0);
    const ok = await askForCode(e, EMAIL, { origin: null, headers: { "Sec-Fetch-Site": "same-origin" } });
    assert.equal(ok.status, 200);
});

test("a bad email gets a friendly 400; a missing SESSION_SECRET turns sign-in off with a 503", async () => {
    const { e } = await listedEnv();
    const bad = await askForCode(e, "nope");
    assert.equal(bad.status, 400);
    assert.match(await bad.text(), /doesn.t look like an email/);
    const off = await askForCode(env({ SESSION_SECRET: "" }), EMAIL);
    assert.equal(off.status, 503);
});

test("without the gate's bindings or EMAIL_PEPPER (a Preview has none), sign-in and requests say they aren't working; the page still loads", async () => {
    for (const missing of ["ACCESS_KV", "EMAIL", "SEND_IP_RL", "VERIFY_EMAIL_RL", "CODE_SEND_RL", "EMAIL_PEPPER"]) {
        const e = env({ [missing]: undefined });
        for (const [path, fields] of [["/access", { email: EMAIL }], ["/access/verify", { email: EMAIL, code: "12345678" }], ["/request", { email: EMAIL }]]) {
            const res = await run(e, post(path, fields));
            assert.equal(res.status, 503, `${missing} ${path}`);
            assert.match(await res.text(), /isn.t working right now/);
        }
    }
    const preview = env({ ACCESS_KV: undefined, EMAIL: undefined, SESSION_SECRET: undefined });
    const front = await handle(get("/"), preview);
    assert.equal(front.status, 200);
    assertSignedOutHome(await front.text());
    const res = await worker.fetch(get("/admin"), preview, fakeCtx());
    assert.equal(res.status, 403);
});

test("a body that isn't a small form is refused", async () => {
    const { e } = await listedEnv();
    const big = await run(e, post("/access", { email: EMAIL, pad: "x".repeat(5000) }));
    assert.equal(big.status, 400);
    const json = new Request("https://stashfm.app/access", { method: "POST", headers: { Origin: "https://stashfm.app", "content-type": "application/json" }, body: "{}" });
    assert.equal((await run(e, json)).status, 400);
});

test("signed in, /access sends you home; GET on the POST-only routes goes home too", async () => {
    const { e } = await listedEnv();
    await askForCode(e);
    const ok = await tryCode(e, codeFrom(e.EMAIL.sent[0]));
    const cookie = ok.headers.getSetCookie()[0].split(";")[0];
    const access = await handle(get("/access", { Cookie: cookie }), e);
    assert.equal(access.status, 303);
    assert.equal(access.headers.get("location"), "/");
    for (const path of ["/access/verify", "/signout"]) assert.equal((await handle(get(path), e)).status, 303);
});
