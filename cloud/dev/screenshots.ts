// Drives the built dashboard against the mock server in headless Chrome and
// writes screenshots to cloud/screenshots/. Run `pnpm build` first.
//   node dev/screenshots.ts            (uses the system Chrome; PW_CHANNEL=chromium to change)
import { chromium, type Page } from "playwright";
import { mkdirSync, readdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { createMockServer, COMPUTER, REACTOR } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const out = resolve(here, "../screenshots");
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (f.endsWith(".png")) rmSync(resolve(out, f));
const staticDir = resolve(here, "../../src/main/resources/cccloud_web");

const mock = await createMockServer({ port: 0, host: "127.0.0.1", extras: true, tickMs: 700, approveDelayMs: 2500, staticDir, log: () => {} });
const origin = `http://localhost:${mock.port}`;
const control = (body: object, mainframeId?: string) =>
  fetch(`${origin}/dev/control`, { method: "POST", body: JSON.stringify({ ...body, mainframeId }) });
const outpost = [...mock.mainframes.values()].find((m) => m.name.startsWith("Outpost"))!;

const browser = await chromium.launch({
  channel: process.env.PW_CHANNEL ?? "chrome",
  // lets us open the dashboard under a public-looking hostname (HTTP warning, LAN-only 403)
  // swiftshader: WebGL for the 3D view in headless Chrome
  args: ["--host-resolver-rules=MAP mc.example.net 127.0.0.1", "--enable-unsafe-swiftshader"],
});
const problems: string[] = [];
const watch = (page: Page) => {
  page.on("pageerror", (e) => problems.push(`pageerror: ${e.message}`));
  page.on("console", (m) => m.type() === "error" && problems.push(`console: ${m.text()}`));
};
const shot = async (page: Page, name: string, fullPage = false) => {
  await page.waitForTimeout(350);
  await page.screenshot({ path: `${out}/${name}.png`, fullPage });
  console.log("saved", `screenshots/${name}.png`);
};
const phoneCtx = () => browser.newContext({ viewport: { width: 375, height: 812 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });

async function enterCode(page: Page, code: string, device: string) {
  await page.fill(".input-code", code.replace("-", "").toLowerCase());
  await page.fill(".input-name", device);
  await page.click('button[type="submit"]');
}
async function pairVia(page: Page, code: string, device: string) {
  await enterCode(page, code, device);
  await page.waitForSelector(".card", { timeout: 15_000 });
}
async function pairViaEmpty(page: Page, code: string, device: string) {
  await enterCode(page, code, device);
  await page.waitForSelector(".setup", { timeout: 15_000 });
}
async function clearToasts(page: Page) {
  for (const b of await page.locator(".toast-close").all()) await b.click({ timeout: 1000 }).catch(() => {});
}
async function pinEnter(page: Page, digits: string) {
  for (const d of digits) await page.click(`.keypad .key:text-is("${d}")`);
  await page.click(".key-ok");
}

try {
  // ================================================================ phone
  const phone = await phoneCtx();
  const p = await phone.newPage();
  watch(p);
  await p.goto(origin);
  await p.waitForSelector(".input-code");
  await shot(p, "phone-01-pair");

  await p.fill(".input-code", "ZZZZZZZZ");
  await p.click('button[type="submit"]');
  await p.waitForSelector(".form-error");
  await shot(p, "phone-02-pair-wrong-code");

  // two-step pairing: waiting for approval in-game, auto-approved by the mock after 2.5 s
  await enterCode(p, mock.code(), "Enricos iPhone");
  await p.waitForSelector(".waiting");
  await p.waitForTimeout(600);
  await shot(p, "phone-03-waiting-for-approval");
  await p.waitForSelector(".card", { timeout: 15_000 });
  await p.waitForTimeout(700);
  await shot(p, "phone-04-dashboard");
  await shot(p, "phone-05-dashboard-full", true);

  // ---- v0.4 network: power popover, topology, map
  await p.click("button.pwr");
  await p.waitForSelector(".power-pop");
  await p.waitForTimeout(1200); // let the breakdown arrive with the next power tick
  await shot(p, "phone-n0-power-breakdown");
  await p.keyboard.press("Escape");
  await p.click(".tabbar .tab-network");
  await p.waitForSelector(".topo svg .tnode");
  await p.waitForTimeout(500);
  await clearToasts(p);
  await shot(p, "phone-n1-topology");
  await p.click('.tnode[aria-label^="East Relay"]');
  await p.waitForSelector(".sheet .ndetail");
  await clearToasts(p);
  await shot(p, "phone-n2-relay-detail");
  await p.keyboard.press("Escape");
  await p.click('.seg-ctl button:has-text("Map")');
  await p.waitForSelector(".netmap svg .mnode");
  await p.waitForTimeout(400);
  await clearToasts(p);
  await shot(p, "phone-n3-map");
  await p.locator('.mnode[aria-label^="Quarry"]').dispatchEvent("click");
  await p.waitForSelector(".sheet .ndetail");
  await clearToasts(p);
  await shot(p, "phone-n4-no-route-detail");
  await p.keyboard.press("Escape");
  await p.click('.dim-tab:has-text("Nether")');
  await p.waitForTimeout(400);
  await clearToasts(p);
  await shot(p, "phone-n5-map-nether");
  // ---- v0.7 3D view
  await p.click('.dim-tab:has-text("Overworld")');
  await p.click('.seg-ctl button:has-text("3D")');
  await p.waitForSelector(".n3d canvas");
  await p.waitForTimeout(2500); // chunk, textures and terrain
  await clearToasts(p);
  await p.evaluate(() => document.querySelector(".net-canvas")!.scrollIntoView({ block: "end" }));
  await shot(p, "phone-n6-3d");
  await p.fill(".fly-input", "computer");
  await p.keyboard.press("Enter");
  await p.waitForSelector(".sheet .ndetail");
  await p.click('.nd-tab:has-text("Route")');
  await p.waitForTimeout(900);
  await clearToasts(p);
  await shot(p, "phone-n7-3d-detail-route");
  await p.click('.nd-tab:has-text("Card")');
  await p.waitForTimeout(300);
  await shot(p, "phone-n8-3d-detail-card");
  await p.keyboard.press("Escape");
  await p.click('.seg-ctl button:has-text("Topology")');
  await p.click('.tabbar .tab:has-text("Overview")');
  await p.waitForSelector(".search-input");

  // ---- v0.3 auto Computer card (redstone sides)
  await p.locator(".card:has(.rs)").scrollIntoViewIfNeeded();
  await p.evaluate(() => {
    const el = document.querySelector(".card:has(.rs)")!;
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 70);
  });
  await p.waitForTimeout(300);
  await shot(p, "phone-c1-computer-card");
  await p.click(`.card[data-card="${COMPUTER}"] .card-scripts`);
  await p.waitForSelector(".scripts-comp b");
  await p.click('.scripts .nd-tab:has-text("Examples")');
  await p.waitForSelector(".ex-card");
  await shot(p, "phone-x1-scripts-examples");
  await p.click('.ex-card:has-text("cloud-test.lua") button:has-text("Use this")');
  await p.waitForTimeout(200);
  await shot(p, "phone-x2-scripts-upload");
  await p.keyboard.press("Escape");
  // shut it down: "Turn on" becomes the only active power button
  await p.click('.card:has(.rs) .pxbtn:has-text("Shut down")');
  await p.waitForSelector(".dialog");
  await p.click(".dialog .pxbtn-red");
  await p.waitForSelector('.card:has(.rs) .indicator.off');
  await p.waitForTimeout(300);
  await shot(p, "phone-c1b-computer-off");
  await p.click('.card:has(.rs) .pxbtn:has-text("Turn on")');
  await p.waitForSelector('.card:has(.rs) .indicator.on');
  await clearToasts(p);
  await p.click('.rs-side:has-text("front")');
  await clearToasts(p);
  await p.click(".rs-adv-toggle");
  await p.waitForTimeout(500);
  await p.evaluate(() => {
    const el = document.querySelector(".card:has(.rs) .rs")!;
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 70);
  });
  await shot(p, "phone-c2-redstone-advanced");
  await p.evaluate(() => window.scrollTo(0, 0));

  // ---- pages (bottom tab bar) with the Lua v2 widgets
  await p.click('.tabbar .tab:has-text("Power")');
  await p.waitForSelector(".chart-svg");
  await p.waitForTimeout(800);
  await shot(p, "phone-p1-power-page");
  await p.click('.seg-btn:has-text("High")');
  await p.selectOption(".select", "boost");
  await p.waitForTimeout(400);
  await shot(p, "phone-p2-power-page-full", true);
  await p.click('.tabbar .tab:has-text("Farm")');
  await p.waitForSelector(".indicator");
  await shot(p, "phone-p3-farm-page");
  await p.click('.tabbar .tab:has-text("Overview")');
  await p.waitForSelector(".search-input");

  // ---- v0.2 storage card: search, craft sheet, watchlist, jobs
  await p.click(".toast-close").catch(() => {});
  await p.fill(".search-input", "iron");
  await p.waitForSelector(".item-row");
  await p.waitForTimeout(400);
  await p.evaluate(() => {
    const el = document.querySelector(".search-bar")!;
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 120);
  });
  await shot(p, "phone-s1-search-results");
  await p.evaluate(() => window.scrollBy(0, 420)); // the search bar sticks under the header
  await shot(p, "phone-s2-search-sticky");
  await p.fill(".search-input", "processor");
  await p.waitForSelector('.item-row:has-text("Logic Processor")');
  await p.click('.item-row:has-text("Logic Processor")');
  await p.waitForSelector(".amounts");
  await shot(p, "phone-s3-craft-sheet");
  await p.click('.chip:has-text("Custom")');
  await p.fill(".input-amount", "250");
  await p.click('.pxbtn-gold:has-text("Craft")');
  await p.waitForSelector(".result-ok");
  await shot(p, "phone-s4-craft-started");
  await p.keyboard.press("Escape");
  await p.fill(".search-input", "singularity");
  await p.waitForSelector('.item-row:has-text("Singularity")');
  await p.click('.item-row:has-text("Singularity")');
  await p.click('.chip:has-text("16")');
  await p.click('.pxbtn-gold:has-text("Craft")');
  await p.waitForSelector(".result-err");
  await shot(p, "phone-s5-craft-error");
  await p.keyboard.press("Escape");
  await p.fill(".search-input", "");
  await p.evaluate(() => {
    const el = document.querySelector(".watch-list")!;
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 140);
  });
  await p.waitForTimeout(1500); // let jobs progress a bit
  await shot(p, "phone-s6-watchlist-jobs");
  await p.click(".watch-row.tone-low");
  await p.waitForSelector(".stepper");
  await shot(p, "phone-s7-watch-edit");
  await p.keyboard.press("Escape");
  await p.click('.sec-head .btn:has-text("Add")');
  await p.fill('.sheet .input[type="search"]', "diamond");
  await p.waitForSelector('.sheet .item-row:has-text("Diamond")');
  await shot(p, "phone-s8-watch-add");
  await p.keyboard.press("Escape");
  await p.evaluate(() => window.scrollTo(0, 0));

  // SCRAM is PIN-protected (mock PIN 1234): keypad, wrong PIN, then right PIN
  await p.click(".pxbtn-red");
  await p.waitForSelector(".pin-dialog");
  await shot(p, "phone-06-pin-keypad");
  await pinEnter(p, "0000");
  await p.waitForSelector(".pin-msg.err");
  await shot(p, "phone-07-pin-wrong");
  await pinEnter(p, "1234");
  await p.waitForSelector(".toast-warn");
  await shot(p, "phone-08-after-scram-toast");

  // toggle back on (optimistic) + crit alert + alert log
  await clearToasts(p);
  await p.click('[role="switch"]');
  await control({ alert: { uplinkId: REACTOR, text: "Core temperature critical: 1104 K", level: "crit" } });
  await p.waitForSelector(".toast-crit");
  await shot(p, "phone-09-crit-alert");
  await p.click('[aria-label^="Alerts"]');
  await p.waitForSelector(".alert-log");
  await shot(p, "phone-10-alert-log");
  await p.keyboard.press("Escape");

  // slider (sends on release/change) via keyboard
  await p.focus(".range");
  await p.keyboard.press("ArrowRight");
  await p.waitForTimeout(300);

  // dashboard disabled / mainframe offline
  await p.evaluate(() => window.scrollTo(0, 0));
  await p.click(".toast-close").catch(() => {});
  await p.click(".toast-close").catch(() => {});
  await control({ enabled: false });
  await shot(p, "phone-11-disabled");
  await control({ enabled: true, online: false });
  await shot(p, "phone-12-mainframe-offline");
  await control({ online: true });
  // v0.3: out of FE -> "Mainframe has no power"
  await control({ power: { stored: 0, input: 0, tick: 5 } });
  await p.waitForSelector(".banner-no_power");
  await shot(p, "phone-21-no-power");
  await control({ power: { input: 2000, tick: 1 } });
  await p.waitForSelector(".banner-no_power", { state: "detached" });
  await control({ power: { input: 120, stored: 152000 } });

  // PIN lockout after 5 wrong PINs
  await p.click(".pxbtn-red");
  await p.waitForSelector(".pin-dialog");
  for (let i = 0; i < 5; i++) {
    await pinEnter(p, "9999");
    await p.waitForSelector(i < 4 ? ".pin-msg.err" : ".pin-msg.locked");
  }
  await shot(p, "phone-13-pin-locked");
  await p.click(".pin-cancel");
  await p.evaluate(() => window.scrollTo(0, 0));
  await shot(p, "phone-14-pin-lock-banner");

  // ---- layout editor on the phone (WYSIWYG; frame sequences in dev/editor-shots.ts)
  await p.evaluate(() => window.scrollTo(0, 0));
  await p.click('[aria-label="Edit layout"]');
  await p.waitForSelector(".editor .pgrid");
  await clearToasts(p);
  await shot(p, "phone-e1-edit-mode");
  await p.click('[aria-label^="Settings for Fission Reactor"]');
  await p.waitForSelector(".sheet .swatches");
  await p.fill(".sheet .input", "Reactor Core");
  await p.click(".sheet .swatch.sw-red");
  await p.click('.sheet .icon-cell[aria-label="flame"]');
  await shot(p, "phone-e2-card-settings");
  await p.keyboard.press("Escape");
  await p.click(".tabbar .tab-add");
  await p.waitForSelector(".sheet .icon-grid");
  await p.fill(".sheet .input", "Base");
  await p.click('.sheet .icon-cell[aria-label="home"]');
  await shot(p, "phone-e3-new-page");
  await p.click(".sheet .pxbtn-gold");
  await p.click('.tabbar .tab:has-text("Overview")');
  await p.waitForTimeout(300);
  await shot(p, "phone-e4-edit-overview");
  await p.click(".ed-actions .pxbtn-gold");
  await p.waitForSelector(".editor", { state: "detached" });
  await p.waitForTimeout(400);
  await clearToasts(p);
  await shot(p, "phone-e5-saved-layout");

  // second mainframe -> list
  await p.goto(`${origin}/#/pair`);
  await pairVia(p, mock.code(outpost), "Enricos iPhone");
  await p.goto(`${origin}/#/`);
  await p.waitForSelector(".mf-card");
  await p.waitForTimeout(500);
  await shot(p, "phone-15-mainframe-list");

  // ---------------------------------------------------------------- other phones
  // denied pairing
  const dctx = await phoneCtx();
  const dp = await dctx.newPage();
  watch(dp);
  await dp.goto(origin);
  await enterCode(dp, mock.code(), "Please deny me");
  await dp.waitForSelector(".pair-result", { timeout: 15_000 });
  await shot(dp, "phone-16-pair-denied");
  await dctx.close();

  // read-only device
  const ro = await phoneCtx();
  const rp = await ro.newPage();
  watch(rp);
  const tok = await (await fetch(`${origin}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Living room TV", canControl: false }) })).json();
  await rp.addInitScript((t) => {
    localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([{ mainframeId: t.mainframeId, token: t.token, name: t.name, deviceName: "Living room TV", pairedAt: Date.now() }]));
  }, tok);
  await rp.goto(`${origin}/#/m/${tok.mainframeId}`);
  await rp.waitForSelector(".card");
  await rp.waitForTimeout(500);
  await shot(rp, "phone-17-read-only");
  await ro.close();

  // plain HTTP on a public host -> warning banner
  const pub = await phoneCtx();
  const pp = await pub.newPage();
  watch(pp);
  await pp.goto(`http://mc.example.net:${mock.port}/`);
  await pp.waitForSelector(".banner-insecure");
  await shot(pp, "phone-18-insecure-http-warning");
  await pub.close();

  // v0.3 empty state: a freshly paired mainframe without uplinks, opened over plain HTTP
  // from a non-localhost address, so "Copy" has to use the execCommand fallback
  const newBase = [...mock.mainframes.values()].find((m) => m.name.startsWith("New Base"))!;
  const ectx = await phoneCtx();
  const ep = await ectx.newPage();
  watch(ep);
  await ep.goto(`http://mc.example.net:${mock.port}/`);
  await ep.waitForSelector(".input-code");
  await pairViaEmpty(ep, mock.code(newBase), "Enricos iPhone");
  await ep.click(".banner-insecure .icon-btn").catch(() => {});
  await ep.waitForTimeout(400);
  await shot(ep, "phone-g1-setup-guide");
  await ep.click(".setup-more summary");
  await ep.click(".copy-btn");
  await ep.waitForSelector(".copy-btn.copied");
  await ep.evaluate(() => document.querySelector(".setup-more")!.scrollIntoView({ block: "start" }));
  await ep.evaluate(() => window.scrollBy(0, -70));
  await shot(ep, "phone-g2-build-your-own-copied");
  const desk0 = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const dp0 = await desk0.newPage();
  watch(dp0);
  await dp0.goto(origin);
  await enterCode(dp0, mock.code(newBase), "Desktop");
  await dp0.waitForSelector(".setup", { timeout: 15_000 });
  await shot(dp0, "desktop-g1-setup-guide");
  await desk0.close();
  await ectx.close();

  // auto card sent as plain per-side widgets (current Java format) -> same compact grid
  {
    const m2 = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, ae2: false, controlRoom: false, computerFormat: "widgets", staticDir, log: () => {} });
    const o2 = `http://localhost:${m2.port}`;
    const tok2 = await (await fetch(`${o2}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Phone" }) })).json();
    const c2 = await phoneCtx();
    const p2 = await c2.newPage();
    watch(p2);
    await p2.addInitScript((t) => {
      localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([{ mainframeId: t.mainframeId, token: t.token, name: t.name, deviceName: "Phone", pairedAt: Date.now() }]));
    }, tok2);
    await p2.goto(`${o2}/#/m/${tok2.mainframeId}`);
    await p2.waitForSelector(".card:has(.rs)");
    await p2.evaluate(() => {
      const el = document.querySelector(".card:has(.rs)")!;
      window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 70);
    });
    await shot(p2, "phone-c3-computer-card-per-side-widgets");
    await c2.close();
    await m2.close();
  }

  // LAN-only server (webAllowInternet=false): the page came from cache/LAN, the API answers 403
  const lanMock = await createMockServer({ port: 0, host: "127.0.0.1", lanOnly: true, tickMs: 0, staticDir, log: () => {} });
  const lctx = await phoneCtx();
  const lp = await lctx.newPage();
  watch(lp);
  await lp.goto(`http://localhost:${lanMock.port}/`); // load the app from the LAN address…
  await lp.waitForSelector(".input-code");
  // …then make its API calls look like they come from the internet
  await lp.route("**/api/**", async (route) => {
    const res = await route.fetch({ headers: { ...route.request().headers(), host: "mc.example.net" } });
    await route.fulfill({ response: res });
  });
  await enterCode(lp, lanMock.code(), "Phone on 4G");
  await lp.waitForSelector(".form-error");
  await shot(lp, "phone-19-lan-only");
  await lctx.close();
  await lanMock.close();

  // ================================================================ docs (/docs, lazy chunk)
  {
    const dd = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "de-DE" });
    const dp = await dd.newPage();
    watch(dp);
    await dp.goto(`${origin}/docs/start#recipes`);
    await dp.waitForSelector(".recipe");
    await dp.waitForTimeout(400);
    await shot(dp, "docs-d1-start-recipes-de");
    await dp.click('.lang-toggle button:has-text("EN")');
    await dp.click('.docs-nav-page:has-text("Lua reference")');
    await dp.waitForSelector("#widgets");
    await dp.click(".docs-article .copy-btn");
    await dp.waitForSelector(".copy-btn.copied");
    await shot(dp, "docs-d2-lua-reference-en");
    await dp.fill(".docs-side .docs-search-input", "reverse proxy");
    await dp.waitForSelector(".docs-side .docs-hit");
    await shot(dp, "docs-d3-search");
    await dd.close();

    const pd = await browser.newContext({ viewport: { width: 375, height: 812 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: "de-DE" });
    const pp2 = await pd.newPage();
    watch(pp2);
    await pp2.goto(`${origin}/`);
    await pp2.waitForSelector(".help-btn");
    await pp2.click(".help-btn");
    await pp2.waitForSelector(".docs-article");
    await pp2.click(".docs-menu-btn");
    await pp2.waitForSelector(".docs-sheet");
    await pp2.fill(".docs-sheet .docs-search-input", "No such program");
    await pp2.waitForSelector(".docs-sheet .docs-hit");
    await shot(pp2, "docs-p1-menu-search-de");
    await pp2.click(".docs-sheet .docs-hit");
    await pp2.waitForSelector(".docs-sheet", { state: "detached" });
    await pp2.waitForTimeout(400);
    await shot(pp2, "docs-p2-faq-no-such-program-de");
    await pp2.goto(`${origin}/docs/cc-basics`);
    await pp2.waitForSelector(".callout-warn");
    await shot(pp2, "docs-p3-cc-basics-de");
    await pp2.goto(`${origin}/docs/start#recipes`);
    await pp2.waitForSelector(".recipe");
    await pp2.waitForTimeout(300);
    await shot(pp2, "docs-p4-recipes-de");
    await pd.close();
  }

  // ================================================================ desktop
  const desk = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const d = await desk.newPage();
  watch(d);
  await d.goto(origin);
  await pairVia(d, mock.code(), "Desktop");
  await d.waitForTimeout(800);
  await shot(d, "desktop-01-dashboard");
  // ---- script import: examples → upload & run (PIN, basic computer asks to restart) → files
  await d.click(`.card[data-card="${COMPUTER}"] .card-scripts`);
  await d.waitForSelector(".scripts .dropzone");
  await d.waitForSelector(".scripts-comp b");
  await shot(d, "desktop-x1-scripts-upload");
  await d.click('.scripts .nd-tab:has-text("Examples")');
  await d.waitForSelector(".ex-card");
  await shot(d, "desktop-x2-scripts-examples");
  await d.click('.ex-card:has-text("control-room.lua") button:has-text("Use this")');
  await d.click('.scripts button[type="submit"]');
  await d.waitForSelector(".pin-dialog");
  await pinEnter(d, "1234");
  await d.waitForSelector('.dialog:has-text("Restart the computer?")');
  await shot(d, "desktop-x3-scripts-restart-ask");
  await d.click('.dialog button:has-text("Restart & run")');
  await d.waitForSelector(".scripts .form-ok");
  await clearToasts(d);
  await shot(d, "desktop-x4-scripts-uploaded");
  await d.click('.scripts .nd-tab:has-text("Files")');
  await d.waitForSelector(".file-row");
  await shot(d, "desktop-x5-scripts-files");
  await d.keyboard.press("Escape");
  await d.click('.toptabs .tab-network');
  await d.waitForSelector(".topo svg .tnode");
  await d.waitForTimeout(500);
  await d.click('.tnode[aria-label^="North Relay"]');
  await d.waitForSelector(".net-panel .ndetail");
  await clearToasts(d);
  await shot(d, "desktop-n1-topology");
  await d.click('.seg-ctl button:has-text("Map")');
  await d.waitForSelector(".netmap svg .mnode");
  await d.locator('.mnode[aria-label^="Tree Farm"]').dispatchEvent("click");
  await d.waitForTimeout(400);
  await clearToasts(d);
  await shot(d, "desktop-n2-map");
  // range circles explained: a selected relay labels its node + device range, "?" explains planning
  await d.locator('.mnode[aria-label^="West Relay"]').dispatchEvent("click");
  await d.click(".map-help-btn");
  await d.waitForSelector(".map-help-pop");
  await d.waitForTimeout(300);
  await clearToasts(d);
  await shot(d, "desktop-n2b-map-ranges");
  await d.click(".map-help-btn");
  // ---- v0.7 3D: overview, then a relay highlights what it carries
  await d.click('.seg-ctl button:has-text("3D")');
  await d.waitForSelector(".n3d canvas");
  await d.waitForTimeout(2500);
  await d.click(".net-panel-close").catch(() => {});
  await clearToasts(d);
  await shot(d, "desktop-n4-3d");
  await d.fill(".fly-input", "west");
  await d.keyboard.press("Enter");
  await d.waitForSelector(".net-panel .ndetail");
  await d.click('.nd-tab:has-text("Route")');
  await d.waitForTimeout(1200);
  await clearToasts(d);
  await shot(d, "desktop-n5-3d-relay-route");
  await d.fill(".fly-input", "mainframe");
  await d.keyboard.press("Enter");
  await d.waitForTimeout(1200);
  await d.click('.nd-tab:has-text("Power")');
  await clearToasts(d);
  await shot(d, "desktop-n6-3d-mainframe");
  await d.click('.seg-ctl button:has-text("Topology")');
  await d.click('.tnode[aria-label^="Computer #7"]');
  await d.waitForSelector(".net-panel .ndetail");
  await clearToasts(d);
  await shot(d, "desktop-n7-topology-cable");
  // without WebGL the 3D tab falls back to the 2D map
  const noGl = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome", args: ["--disable-3d-apis"] });
  const ng = await (await noGl.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  await ng.goto(origin);
  await pairVia(ng, mock.code(), "Old laptop");
  await ng.click(".toptabs .tab-network");
  await ng.click('.seg-ctl button:has-text("3D")');
  await ng.waitForSelector(".n3d-fallback");
  await ng.waitForTimeout(500);
  await clearToasts(ng);
  await shot(ng, "desktop-n8-3d-no-webgl");
  await noGl.close();
  await d.click("button.pwr");
  await d.waitForSelector(".power-pop");
  await shot(d, "desktop-n3-power-breakdown");
  await d.keyboard.press("Escape");
  await d.click('.toptabs .tab:has-text("Overview")');
  await d.click('.toptabs .tab:has-text("Power")');
  await d.waitForSelector(".chart-svg");
  await d.waitForTimeout(600);
  await shot(d, "desktop-01b-power-page");
  // real-usage bugs: long button labels in a 3-button row, caption-less label, select
  await d.click('.toptabs .tab:has-text("Farm")');
  await d.waitForSelector('.card:has-text("Kontrollraum")');
  await d.waitForTimeout(400);
  const cut = await d.evaluate(() =>
    [...document.querySelectorAll(".wcell.t-button .pxbtn span")].filter((el) => el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1).map((el) => el.textContent),
  );
  if (cut.length) problems.push(`truncated button labels: ${cut.join(", ")}`);
  const perRow = await d.evaluate(() => {
    const card = [...document.querySelectorAll(".card")].find((x) => x.textContent?.includes("Notaus"))!;
    const tops = [...card.querySelectorAll(".wcell.t-button")].map((el) => Math.round(el.getBoundingClientRect().top));
    return tops.filter((t) => t === tops[0]).length;
  });
  if (perRow < 3) problems.push(`only ${perRow} buttons per row at 1440 px`);
  await clearToasts(d);
  await shot(d, "desktop-01c-kontrollraum");
  await d.click('.toptabs .tab:has-text("Overview")');
  // edit mode (WYSIWYG): drag a card, resize one, widget settings, save
  await d.click('[aria-label="Edit layout"]');
  await d.waitForSelector(".editor .pgrid");
  await d.waitForTimeout(300);
  await clearToasts(d);
  await shot(d, "desktop-e1-edit-mode");
  {
    const head = (await d.locator(".pgrid > .card").nth(2).locator(".card-title").boundingBox())!;
    const first = (await d.locator(".pgrid > .card").nth(0).boundingBox())!;
    await d.mouse.move(head.x + 10, head.y + 8);
    await d.mouse.down();
    await d.mouse.move(first.x + 40, first.y + 30, { steps: 20 });
    await d.waitForTimeout(150);
    await d.mouse.up();
    await d.waitForTimeout(700);
    const corner = (await d.locator(".pgrid > .card").nth(1).locator(":scope > .rz-rb").boundingBox())!;
    await d.mouse.move(corner.x + 9, corner.y + 9);
    await d.mouse.down();
    await d.mouse.move(corner.x + 200, corner.y + 9, { steps: 10 });
    await d.mouse.up();
    await d.waitForTimeout(500);
  }
  await shot(d, "desktop-e2-dragged-resized");
  await d.click('.wcell:has-text("Status")');
  await d.waitForSelector(".ed-side .ed-preview");
  await shot(d, "desktop-e3-widget-settings");
  await d.click(".ed-actions .pxbtn-gold");
  await d.waitForSelector(".editor", { state: "detached" });
  await d.waitForTimeout(500);
  await clearToasts(d);
  await shot(d, "desktop-e4-saved-layout");
  await d.click(".pxbtn-yellow"); // Vent steam (no PIN)
  await d.click('[aria-label^="Back"]');
  await d.waitForSelector(".mf-card");
  await d.click('[aria-label^="Theme"]'); // dark -> light
  await shot(d, "desktop-02-list-light");
  await d.click(".mf-card");
  await d.waitForSelector(".card");
  await shot(d, "desktop-03-dashboard-light");
  await d.click('[aria-label^="Back"]');
  await d.waitForSelector(".mf-card");
  await d.click('[aria-label^="Theme"]'); // light -> system
  await d.click('[aria-label^="Theme"]'); // system -> dark

  // the approving player lost access -> device removed, back to pairing
  await d.click(".mf-card");
  await d.waitForSelector(".card");
  await control({ revokePlayer: true });
  await d.waitForSelector(".input-code");
  await shot(d, "desktop-04-access-revoked");

  // ================================================================ v0.6 accounts, roles, shares
  {
    const acct = async (player: string) => (await (await fetch(`${origin}/dev/token`, { method: "POST", body: JSON.stringify({ account: player, name: `${player}'s PC` }) })).json()).token as string;
    const seed = (token: string, player: string) => ({ token, player, deviceName: `${player}'s PC`, linkedAt: Date.now() });
    const ectx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const ep = await ectx.newPage();
    watch(ep);
    await ep.addInitScript((a) => localStorage.setItem("cccloud.account.v1", JSON.stringify(a)), seed(await acct("Enrico"), "Enrico"));
    await acct("Enrico"); // a second browser of the same player for the devices list
    await ep.goto(`${origin}/`);
    await ep.waitForSelector(".mf-card");
    await ep.waitForTimeout(600);
    await shot(ep, "desktop-a1-account-mainframes");
    await ep.click(".mf-card");
    await ep.waitForSelector(".card");
    await ep.click(".switcher");
    await ep.waitForSelector(".switch-menu");
    await shot(ep, "desktop-a2-switcher-menu");
    await ep.keyboard.press("Escape");
    await ep.click(".switcher");
    await ep.click('.sm-item:has-text("Access")');
    await ep.waitForSelector(".acc-table");
    await shot(ep, "desktop-a3-access");
    await ep.goto(`${origin}/#/devices`);
    await ep.waitForSelector(".dev-row");
    await shot(ep, "desktop-a4-devices");
    // share link: create
    await ep.goto(`${origin}/#/m/${mock.base.id}/shares`);
    await ep.waitForSelector(".acc-head");
    await ep.click('button:has-text("New link")');
    await ep.waitForSelector(".share-scope");
    await ep.fill('.sheet .input[maxlength="48"]', "Reactor for the stream");
    await ep.click('.scope-card:has-text("Reactor") input');
    await ep.click('.scope-card:has-text("ME Network") input');
    await ep.click('.sheet input[type="checkbox"] >> nth=-1');
    await shot(ep, "desktop-a5-new-share");
    await ep.click('.sheet .pxbtn-gold:has-text("Create link")');
    await ep.waitForSelector(".sheet .codeblock");
    const shareUrl = (await ep.locator(".sheet .codeblock pre").innerText()).trim();
    await shot(ep, "desktop-a6-share-created");
    await ep.keyboard.press("Escape");
    await ep.waitForSelector(".dev-row");
    await shot(ep, "desktop-a7-share-list");
    await ectx.close();

    // the share viewer: secret scrubbed from the URL, read-only, "Shared by"
    for (const [w, h, name] of [[1440, 900, "desktop-s1-share-view"], [375, 812, "phone-s9-share-view"]] as const) {
      const sctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: w < 500 ? 2 : 1 });
      const sp = await sctx.newPage();
      watch(sp);
      await sp.goto(shareUrl.replace(/^https?:\/\/[^/]+/, origin));
      await sp.waitForSelector(".banner-share");
      await sp.waitForSelector(".card");
      await sp.waitForTimeout(500);
      if (sp.url().includes("#")) throw new Error("share secret still in the URL");
      if (await sp.locator(".pxbtn, .switch, .range, .search-input").count()) throw new Error("share view shows controls");
      await shot(sp, name);
      await sctx.close();
    }

    // a viewer (team ally) at 375 px: controls disabled with the reason
    const vctx = await phoneCtx();
    const vp = await vctx.newPage();
    watch(vp);
    const samTok = await acct("Sam");
    await vp.addInitScript((a) => localStorage.setItem("cccloud.account.v1", JSON.stringify(a)), seed(samTok, "Sam"));
    await vp.goto(`${origin}/#/m/${mock.base.id}`);
    await vp.waitForSelector(".banner-readonly");
    await vp.waitForTimeout(400);
    await shot(vp, "phone-a1-viewer-role");
    // keyboard: focus ring on the page tabs
    await vp.keyboard.press("Tab");
    await vp.keyboard.press("Tab");
    await vctx.close();

    const kctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const kp = await kctx.newPage();
    watch(kp);
    await kp.addInitScript((a) => localStorage.setItem("cccloud.account.v1", JSON.stringify(a)), seed(await acct("Enrico"), "Enrico"));
    await kp.goto(`${origin}/#/m/${mock.base.id}`);
    await kp.waitForSelector(".toptabs .tab");
    await kp.focus(".toptabs .tab");
    await kp.keyboard.press("ArrowRight");
    await kp.waitForTimeout(200);
    await shot(kp, "desktop-k1-keyboard-focus");
    await kctx.close();
  }

  // server gone -> unreachable banner (phone, outpost still paired there)
  // ---- "Pair in browser" chat link: code prefilled, fragment cleared, only the name left to confirm
  for (const [ctx, name] of [[await phoneCtx(), "phone-p1-pair-link"], [await browser.newContext({ viewport: { width: 1440, height: 900 } }), "desktop-p1-pair-link"]] as const) {
    const lp = await ctx.newPage();
    watch(lp);
    await lp.goto(`${origin}/pair#code=${mock.code()}`);
    await lp.waitForSelector(".pair-from-link");
    if (new URL(lp.url()).hash.includes("code")) problems.push("pair link: code still in the URL");
    await shot(lp, name);
    await ctx.close();
  }
  await mock.close();
  await p.goto(`${origin}/#/m/${outpost.id}`).catch(() => {});
  await p.waitForTimeout(2500);
  await shot(p, "phone-20-server-unreachable");
} finally {
  await browser.close();
  await mock.close().catch(() => {});
}

const relevant = problems.filter((m) => !/ERR_CONNECTION_REFUSED|WebSocket connection|status of 40[349]|status of 429/.test(m));
console.log(relevant.length ? `\nPage problems:\n${relevant.join("\n")}` : "\nNo page errors or CSP violations.");
