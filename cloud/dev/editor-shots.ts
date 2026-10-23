// Screenshots + frame sequences of the WYSIWYG layout editor (desktop 1440 and
// phone 375), written to cloud/screenshots/editor/. Run `pnpm build` first.
//   node dev/editor-shots.ts
import { chromium, type Page } from "playwright";
import { mkdirSync, readdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { createMockServer, REACTOR, STORAGE, ME } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const out = resolve(here, "../screenshots/editor");
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (f.endsWith(".png")) rmSync(resolve(out, f));
const staticDir = resolve(here, "../../src/main/resources/cccloud_web");
const mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, approveDelayMs: 200, staticDir, log: () => {} });
const origin = `http://localhost:${mock.port}`;
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
const problems: string[] = [];
const watch = (page: Page) => {
  page.on("pageerror", (e) => problems.push(`pageerror: ${e.message}`));
  page.on("console", (m) => m.type() === "error" && !/status of 40[349]/.test(m.text()) && problems.push(`console: ${m.text()}`));
};
const shot = async (page: Page, name: string) => {
  await page.screenshot({ path: `${out}/${name}.png` });
  console.log("saved", `screenshots/editor/${name}.png`);
};
const clearToasts = async (page: Page) => {
  for (const b of await page.locator(".toast-close").all()) await b.click({ timeout: 500 }).catch(() => {});
};
async function pair(page: Page) {
  await page.goto(origin);
  await page.fill(".input-code", mock.code().replace("-", "").toLowerCase());
  await page.fill(".input-name", "Editor");
  await page.click('button[type="submit"]');
  await page.waitForSelector(".card", { timeout: 15_000 });
  await page.waitForTimeout(500);
  await clearToasts(page);
}
const center = async (page: Page, sel: string) => {
  const b = (await page.locator(sel).first().boundingBox())!;
  return { x: b.x + b.width / 2, y: b.y + b.height / 2, b };
};
/** Mouse drag in small steps, saving a frame every `every` steps. */
async function mouseDrag(page: Page, from: { x: number; y: number }, to: { x: number; y: number }, name: string, steps = 30, every = 10) {
  await page.mouse.move(from.x, from.y);
  await page.mouse.down();
  for (let i = 1; i <= steps; i++) {
    await page.mouse.move(from.x + ((to.x - from.x) * i) / steps, from.y + ((to.y - from.y) * i) / steps);
    await page.waitForTimeout(16);
    if (i % every === 0) {
      if (process.env.DEBUG_DRAG) console.log(name, i, await page.evaluate(() => { const g = document.querySelector(".drag-ghost") as HTMLElement | null; return g ? g.style.transform : "no ghost"; }), await page.evaluate(() => scrollY));
      await shot(page, `${name}-${String(i).padStart(2, "0")}`);
    }
  }
  await page.waitForTimeout(200);
  await page.mouse.up();
  await page.waitForTimeout(60);
  await shot(page, `${name}-drop`);
  await page.waitForTimeout(600);
  await shot(page, `${name}-settled`);
}

try {
  // ============================================================ desktop 1440
  const desk = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const d = await desk.newPage();
  watch(d);
  await pair(d);
  await shot(d, "d00-view");
  await d.click('[aria-label="Edit layout"]');
  await d.waitForSelector(".editor .pgrid");
  await d.waitForTimeout(300);
  await shot(d, "d01-edit");
  // hover shows the widget tools and the resize corner
  await d.hover(`[data-w="${REACTOR}/temp"]`, { force: true });
  await d.waitForTimeout(200);
  await shot(d, "d02-hover");
  // drag a widget into another card (frames)
  const from = await center(d, `[data-w="${REACTOR}/coolant"]`);
  const dst = (await d.locator(`[data-card="${STORAGE}"] [data-wgrid]`).boundingBox())!;
  await mouseDrag(d, from, { x: dst.x + dst.width * 0.78, y: dst.y + 50 }, "d03-widget-to-card");
  // drag a card (frames): Storage to the far left
  const head = await center(d, `[data-card="${STORAGE}"] .card-title`);
  const me = await center(d, `[data-card="${ME}"] .card-title`);
  await mouseDrag(d, head, { x: me.b.x + 30, y: me.y }, "d04-card-move");
  // resize a widget from the corner with the live size badge
  await d.waitForTimeout(400);
  const corner = await center(d, `[data-w="${REACTOR}/temp"] .rz-rb`);
  await d.hover(`[data-w="${REACTOR}/temp"]`, { force: true });
  await d.mouse.move(corner.x, corner.y);
  await d.mouse.down();
  await d.mouse.move(corner.x + 60, corner.y + 10, { steps: 6 });
  await d.waitForTimeout(150);
  await shot(d, "d05-resize-widget");
  await d.mouse.move(corner.x + 140, corner.y + 30, { steps: 8 });
  await d.waitForTimeout(150);
  await shot(d, "d05-resize-widget-wide");
  await d.mouse.up();
  await d.waitForTimeout(300);
  // below the readable minimum: it stops, the badge turns red, then it bounces
  {
    const c2 = await center(d, `[data-w="${REACTOR}/power"] .rz-r`);
    await d.hover(`[data-w="${REACTOR}/power"]`, { force: true });
    await d.mouse.move(c2.x, c2.y);
    await d.mouse.down();
    await d.mouse.move(c2.x - 400, c2.y, { steps: 10 });
    await d.waitForTimeout(150);
    await shot(d, "d05-resize-min");
    await d.mouse.up();
  }
  await d.waitForTimeout(400);
  // drag a widget into empty space: a new card appears
  await d.locator('.card:has-text("Computer #7")').scrollIntoViewIfNeeded();
  await d.evaluate(() => window.scrollBy(0, 120));
  await d.waitForTimeout(300);
  const sv = await center(d, '.card:has-text("Computer #7") .wcell:has-text("Shut down")');
  const grid = (await d.locator(".pgrid").boundingBox())!;
  await mouseDrag(d, sv, { x: grid.x + grid.width * 0.55, y: sv.y - 40 }, "d06-new-card", 30, 15);
  // widget settings panel with live preview: gauge as a bar, red accent
  await d.click(`[data-w="${REACTOR}/temp"]`);
  await d.waitForSelector(".ed-side .ed-preview");
  await d.click('.ed-side .seg-ctl button:has-text("Bar")');
  await d.click(".ed-side .sw-red");
  await d.waitForTimeout(300);
  await shot(d, "d07-widget-settings");
  // multi-select with Shift-click
  await d.click(`[data-w="${STORAGE}/energy"]`);
  await d.click(`[data-w="${STORAGE}/items"]`, { modifiers: ["Shift"] });
  await d.waitForTimeout(200);
  await shot(d, "d08-multi-select");
  // card settings: compact style, icon
  await d.click(`[data-card="${STORAGE}"] [aria-label^="Settings for"]`);
  await d.click('.ed-side .seg-ctl button:has-text("Compact")');
  await d.waitForTimeout(250);
  await shot(d, "d09-card-settings");
  // Add gallery: hidden items and every widget type with its Lua line
  await d.click(`[data-card="${ME}"] [aria-label^="Hide"]`);
  await d.click(".ed-add");
  await d.waitForSelector(".ed-gallery");
  await d.waitForTimeout(300);
  await shot(d, "d10-add-hidden");
  await d.click('.ed-gallery .nd-tab:has-text("All widget types")');
  await d.waitForTimeout(300);
  await shot(d, "d11-add-types");
  // keyboard: focus a widget, Space, arrows, Shift+arrows, Space
  await d.keyboard.press("Escape");
  await d.focus(`[data-w="${REACTOR}/power"]`);
  await d.keyboard.press("Space");
  await d.keyboard.press("ArrowRight");
  await d.keyboard.press("Shift+ArrowLeft");
  await d.waitForTimeout(250);
  await shot(d, "d12-keyboard-move");
  await d.keyboard.press("Space");
  await d.keyboard.press("?");
  await d.waitForTimeout(200);
  await shot(d, "d13-shortcuts");
  // undo everything step by step, then save
  for (let i = 0; i < 3; i++) await d.keyboard.press("Control+z");
  await d.click(".ed-actions .pxbtn-gold");
  await d.waitForSelector(".editor", { state: "detached" });
  await d.waitForTimeout(400);
  await clearToasts(d);
  await shot(d, "d14-saved-view");
  await desk.close();

  // ============================================================== phone 375
  const phone = await browser.newContext({ viewport: { width: 375, height: 812 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  const p = await phone.newPage();
  watch(p);
  await pair(p);
  await p.click('[aria-label="Edit layout"]');
  await p.waitForSelector(".editor .pgrid");
  await p.waitForTimeout(300);
  await shot(p, "p01-edit");
  // long-press + drag a widget with touch (CDP touch events)
  const cdp = await phone.newCDPSession(p);
  const t = await center(p, `[data-w="${REACTOR}/coolant"]`);
  // to the free spot right of the Capacity gauge in the same card
  const cap = await center(p, `[data-w="${STORAGE}/fill"]`);
  const target = { x: cap.b.x + cap.b.width * 1.55, y: cap.y };
  const touch = (type: string, x: number, y: number) =>
    cdp.send("Input.dispatchTouchEvent", { type, touchPoints: type === "touchEnd" ? [] : [{ x, y, id: 1 }] } as never);
  await touch("touchStart", t.x, t.y);
  await p.waitForTimeout(420); // long-press picks it up
  await shot(p, "p02-long-press");
  for (let i = 1; i <= 12; i++) {
    await touch("touchMove", t.x + ((target.x - t.x) * i) / 12, t.y + ((target.y - t.y) * i) / 12);
    await p.waitForTimeout(20);
  }
  await shot(p, "p03-touch-drag");
  await touch("touchEnd", target.x, target.y);
  await p.waitForTimeout(700);
  await shot(p, "p04-touch-dropped");
  // tap selects (tools appear), then settings as a sheet
  await p.locator(`[data-w="${REACTOR}/temp"]`).tap();
  await p.waitForTimeout(200);
  await shot(p, "p05-selected");
  await p.click(`[data-w="${REACTOR}/temp"] [aria-label^="Settings for"]`);
  await p.waitForSelector(".sheet .ed-preview");
  await shot(p, "p06-settings-sheet");
  await p.keyboard.press("Escape");
  await phone.close();
} finally {
  await browser.close();
  await mock.close();
}
console.log(problems.length ? `\nPage problems:\n${problems.join("\n")}` : "\nNo page errors.");
