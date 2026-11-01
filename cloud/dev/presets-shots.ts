// Screenshots of the dashboard presets on the mock's bases (desktop 1440 and phone 390), written to
// cloud/screenshots/presets/. Run `pnpm build` first.
//   node dev/presets-shots.ts
import { chromium, type Page } from "playwright";
import { mkdirSync, readdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { createMockServer } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const out = process.env.SHOTS_DIR ?? resolve(here, "../screenshots/presets");
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (f.endsWith(".png")) rmSync(resolve(out, f));
const staticDir = resolve(here, "../../src/main/resources/cccloud_web");
const mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, approveDelayMs: 200, extras: true, staticDir, log: () => {} });
const origin = `http://localhost:${mock.port}`;
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
const problems: string[] = [];
const watch = (page: Page) => {
  page.on("pageerror", (e) => problems.push(`pageerror: ${e.message}`));
  page.on("console", (m) => m.type() === "error" && !/status of 40[349]/.test(m.text()) && problems.push(`console: ${m.text()}`));
};
const DESKTOP = { width: 1440, height: 900 };
const PHONE = { width: 390, height: 844 };
const shot = async (page: Page, name: string, fullPage = true) => {
  await page.screenshot({ path: `${out}/${name}.png`, fullPage });
  console.log("saved", name);
};
const clearToasts = async (page: Page) => {
  for (const b of await page.locator(".toast-close").all()) await b.click({ timeout: 500 }).catch(() => {});
};
const mainframe = (name: string) => [...mock.mainframes.values()].find((m) => m.name.startsWith(name))!;
const main = [...mock.mainframes.values()][0];
const show = mainframe("Mod Showcase");
const open = async (page: Page, id: string) => {
  await page.goto(`${origin}/#/m/${id}`);
  await page.reload();
  await page.waitForSelector(".card", { timeout: 15_000 });
  await page.waitForTimeout(400);
  await clearToasts(page);
};
const reset = (m: typeof main) => {
  (m as { layout: unknown }).layout = null;
};

const context = await browser.newContext({ viewport: DESKTOP, locale: "en-US" });
const page = await context.newPage();
watch(page);
await page.goto(origin);
await page.fill(".input-code", mock.code().replace("-", "").toLowerCase());
await page.fill(".input-name", "Presets");
await page.click('button[type="submit"]');
await page.waitForSelector(".card", { timeout: 15_000 });
await page.waitForTimeout(600);
await clearToasts(page);

// the showcase is a second mainframe of this browser
const showToken = await (await fetch(`${origin}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Showcase", mainframeId: show.id }) })).json();
await page.evaluate((t) => {
  const list = JSON.parse(localStorage.getItem("cccloud.mainframes.v1") ?? "[]");
  localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([...list, { mainframeId: t.mainframeId, token: t.token, name: t.name, deviceName: "Showcase", pairedAt: Date.now() }]));
}, showToken);

const openTab = async () => {
  await page.click('button[aria-label="Edit layout"]');
  await page.waitForSelector(".ed-gallery");
  await page.click('.nd-tab:has-text("Presets"), .nd-tab:has-text("Vorlagen")');
};

// the first run
await page.waitForSelector(".pr-start");
await shot(page, "first-run-desktop", false);

// every official preset: the sheet, then the page on desktop and phone
const presets = ["base-overview", "power-grid", "me-command-centre", "reactor-control", "farm-production", "doors-redstone", "alerts-status", "phone-quick-panel"];
const titles: Record<string, string> = { "base-overview": "Base overview", "power-grid": "Power grid", "me-command-centre": "ME/RS command centre", "reactor-control": "Reactor control", "farm-production": "Farm and production", "doors-redstone": "Doors and redstone", "alerts-status": "Alerts and status", "phone-quick-panel": "Phone quick panel" };
async function apply(m: typeof main, id: string, tag: string) {
  reset(m);
  await open(page, m.id);
  await openTab();
  await page.click(`.pr-row:has-text("${titles[id]}")`);
  await page.waitForSelector(".sheet");
  await page.waitForTimeout(300);
  if (await page.locator(".sheet .pxbtn-gold").isDisabled()) {
    console.log("skip (nothing fits)", tag, id);
    await page.keyboard.press("Escape");
    await page.click('button:has-text("Cancel")').catch(() => {});
    return;
  }
  await shot(page, `${tag}-${id}-sheet`, false);
  await page.click(".sheet .pxbtn-gold");
  await page.waitForTimeout(300);
  await clearToasts(page);
  await page.click('.ed-tools ~ * .pxbtn-gold, button:has-text("Save")');
  await page.waitForTimeout(700);
  await clearToasts(page);
  await page.evaluate(() => scrollTo(0, 0));
  await shot(page, `${tag}-${id}-desktop`);
  await page.setViewportSize(PHONE);
  await page.waitForTimeout(400);
  await shot(page, `${tag}-${id}-phone`);
  await page.setViewportSize(DESKTOP);
  await page.waitForTimeout(200);
}
if (!process.env.SKIP_PAGES) {
  for (const id of presets) await apply(main, id, "main");
  for (const id of ["power-grid", "reactor-control", "me-command-centre", "farm-production", "alerts-status", "base-overview"]) await apply(show, id, "showcase");
}

// the picker, in both languages
reset(main);
await open(page, main.id);
await openTab();
await page.waitForTimeout(200);
await shot(page, "picker-official", false);
await page.click('.seg-ctl button:has-text("Community")');
await page.waitForSelector(".pr-list");
await shot(page, "picker-community", false);
await page.click('.pr-row:has-text("Night watch")');
await page.waitForSelector(".sheet");
await page.waitForTimeout(300);
await shot(page, "sheet-store", false);
await page.keyboard.press("Escape");
await page.waitForTimeout(100);

// share the page, then import the code again
await page.click('button:has-text("Share this page as preset")');
await page.waitForSelector(".pr-code");
await page.waitForTimeout(300);
await shot(page, "share-sheet", false);
const code = await page.inputValue(".sheet .pr-code");
await page.keyboard.press("Escape");
await page.click('.seg-ctl button:has-text("Code")');
await page.fill("textarea.pr-code", "ccp1.broken.00000000");
await page.click('button:has-text("Check code")');
await page.waitForSelector(".pr-note-warn");
await shot(page, "import-error", false);
await page.fill("textarea.pr-code", code);
await page.click('button:has-text("Check code")');
await page.waitForSelector(".sheet");
await page.waitForTimeout(300);
await shot(page, "sheet-code-unsigned", false);
await page.keyboard.press("Escape");

// behaviour the screenshots cannot show
const check = (ok: boolean, what: string) => {
  console.log(ok ? "ok  " : "FAIL", what);
  if (!ok) problems.push(what);
};
const layoutOf = () => (main as { layout: { pages?: { id: string; title: string }[]; cards?: object } | null }).layout;

// the first run: one click, undo, dismissal per mainframe
reset(main);
await open(page, main.id);
await page.click(".pr-start .pxbtn-gold");
await page.waitForFunction(() => !document.querySelector(".pr-start"));
await page.waitForTimeout(300);
check(layoutOf()?.pages?.[0]?.title === "Base overview", "the first run puts the preset's page in front");
check((await page.locator(".tab.on").first().innerText()).includes("Base overview"), "the dashboard lands on the preset");
await page.click('.toast button:has-text("Undo")');
await page.waitForSelector(".pr-start");
check(!layoutOf()?.pages?.length && !Object.keys(layoutOf()?.cards ?? {}).length, "Undo takes the first run back");
await page.click('.pr-start button:has-text("Not now")');
await page.waitForFunction(() => !document.querySelector(".pr-start"));
await page.reload();
await page.waitForSelector(".card");
await page.waitForTimeout(800);
check((await page.locator(".pr-start").count()) === 0, "dismissing is remembered after a reload");
reset(show);
await open(page, show.id);
await page.waitForSelector(".pr-start");
check((await page.locator(".pr-start").count()) === 1, "dismissing one mainframe leaves the others alone");
await page.evaluate((id) => localStorage.removeItem(`cccloud.presets.dismissed.${id}`), main.id);

// the editor: a new page, Undo, the page limit
reset(main);
await open(page, main.id);
await openTab();
const tabs = await page.locator(".tab").count();
await page.click('.pr-row:has-text("Power grid")');
await page.waitForSelector(".sheet");
await page.click(".sheet .pxbtn-gold");
await page.waitForTimeout(400);
check((await page.locator(".tab").count()) > tabs, "a preset lands on a new page");
await clearToasts(page);
await page.click('button[aria-label="Undo (Ctrl+Z)"]');
await page.waitForTimeout(300);
check((await page.locator(".tab").count()) === tabs, "Undo in the editor removes the page again");
await page.click('button:has-text("Cancel")');
await page.waitForTimeout(200);
(main as { layout: unknown }).layout = { v: 2, pages: Array.from({ length: 12 }, (_, i) => ({ id: `p${i}`, title: `Page ${i + 1}` })), cards: {}, widgets: {} };
await open(page, main.id).catch(() => {});
await openTab();
await page.click('.pr-row:has-text("Reactor control")');
await page.waitForSelector(".sheet");
await page.waitForTimeout(300);
check((await page.locator(".sheet .pr-note-warn").innerText()).includes("free"), "the page limit is explained");
check(await page.locator(".sheet .pxbtn-gold").isDisabled(), "the page limit blocks Apply");
await shot(page, "page-limit", false);
await page.keyboard.press("Escape");
await page.click('button:has-text("Cancel")');
await page.waitForTimeout(200);

// phone: the first run and the picker
reset(main);
await page.setViewportSize(PHONE);
await open(page, main.id);
await page.waitForSelector(".pr-start");
await shot(page, "first-run-phone", false);
await page.click('.pr-start button:has-text("More presets")');
await page.waitForSelector(".pr-list");
await page.waitForTimeout(300);
await shot(page, "picker-phone", false);
await page.click(`.pr-row:has-text("${titles["reactor-control"]}")`);
await page.waitForSelector(".pr-preview");
await page.waitForTimeout(300);
await shot(page, "sheet-phone", false);

await browser.close();
await mock.close();
if (problems.length) {
  console.log("problems:", [...new Set(problems)].join("\n"));
  process.exitCode = 1;
}
