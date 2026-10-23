#!/usr/bin/env node
// Screenshots of the REAL dashboard served by the ATM11 test server (:8797), in headless Chrome via Playwright
// (same setup as cloud/dev/screenshots.ts). The seeded device token is put into localStorage like a paired browser.
//   node tools/e2e/screenshots.mjs            -> $SCRATCH/screenshots/*.png
import { createRequire } from "node:module";
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(here, "../../cloud/package.json"));
const { chromium } = require("playwright");

const SCRATCH = process.env.SCRATCH ?? join(tmpdir(), "cccloud-e2e");
const PORT = Number(process.env.WEB_PORT ?? 8797);
const origin = `http://127.0.0.1:${PORT}`;
const device = JSON.parse(readFileSync(join(SCRATCH, "e2e-device.json"), "utf8"));
const out = join(SCRATCH, "screenshots");
mkdirSync(out, { recursive: true });

const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
const problems = [];
const watch = (page, tag) => {
  page.on("pageerror", (e) => problems.push(`${tag} pageerror: ${e.message}`));
  page.on("console", (m) => m.type() === "error" && problems.push(`${tag} console: ${m.text()}`));
  page.on("response", (r) => r.status() >= 400 && !r.url().includes("/api/icon/") && problems.push(`${tag} HTTP ${r.status()} ${r.url()}`));
};
const shot = async (page, name, fullPage = false) => {
  await page.waitForTimeout(500);
  await page.screenshot({ path: join(out, `${name}.png`), fullPage });
  console.log("saved", join(out, `${name}.png`));
};
const openMainframe = async (page, tag) => {
  // Depending on the viewport and the number of paired mainframes the app shows the mainframe list first or opens the
  // dashboard directly. Wait for whichever comes first and click the list entry only when the list is shown.
  const entry = page.locator("text=Mainframe @").first();
  const card = page.locator(".card").first();
  await Promise.race([
    entry.waitFor({ state: "visible", timeout: 30_000 }),
    card.waitFor({ state: "visible", timeout: 30_000 }),
  ]).catch(async (e) => {
    await shot(page, `${tag}-00-ERROR-no-list-no-card`);
    writeFileSync(join(out, `${tag}-error.txt`), await page.evaluate(() => document.body.innerText));
    throw e;
  });
  if (await card.isVisible()) return;
  await shot(page, `${tag}-00-mainframe-list`);
  await entry.click();
};
const seed = (ctx) => ctx.addInitScript((m) => {
  localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([m]));
}, { mainframeId: device.mainframeId, token: device.token, name: "ATM11 e2e", deviceName: device.deviceName, pairedAt: Date.now(), canControl: true });

try {
  // pairing screen (fresh browser), wrong code
  const fresh = await browser.newContext({ viewport: { width: 375, height: 812 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  const p0 = await fresh.newPage();
  watch(p0, "pair");
  await p0.goto(origin);
  await p0.waitForSelector(".input-code", { timeout: 15_000 });
  await shot(p0, "phone-01-pair");
  await p0.fill(".input-code", "zzzzzzzz");
  await p0.fill(".input-name", "e2e phone");
  await p0.click('button[type="submit"]');
  await p0.waitForSelector(".form-error", { timeout: 10_000 }).catch(() => {});
  await shot(p0, "phone-02-pair-wrong-code");
  await fresh.close();

  // phone dashboard
  const phone = await browser.newContext({ viewport: { width: 375, height: 812 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  await seed(phone);
  const p = await phone.newPage();
  watch(p, "phone");
  await p.goto(origin);
  await openMainframe(p, "phone");
  await p.waitForSelector(".card", { timeout: 20_000 }).catch(async (e) => { await shot(p, "phone-03-ERROR-no-card"); writeFileSync(join(out, "phone-error.txt"), await p.evaluate(() => document.body.innerText)); throw e; });
  await p.waitForTimeout(2500);
  await shot(p, "phone-03-dashboard");
  await shot(p, "phone-04-dashboard-full", true);
  const inputs = await p.$$(".search-input");
  if (inputs.length) {
    await inputs[0].fill("iron");
    await p.waitForSelector(".item-row", { timeout: 10_000 }).catch(() => {});
    await p.waitForTimeout(800);
    await inputs[0].scrollIntoViewIfNeeded();
    await shot(p, "phone-05-search-iron");
    const row = await p.$(".item-row");
    if (row) {
      await row.click();
      await p.waitForTimeout(800);
      await shot(p, "phone-06-item-sheet");
    }
  }
  const text = await p.evaluate(() => document.body.innerText);
  writeFileSync(join(out, "phone-dashboard.txt"), text);

  // desktop dashboard
  const desk = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await seed(desk);
  const d = await desk.newPage();
  watch(d, "desktop");
  await d.goto(origin);
  await openMainframe(d, "desktop");
  await d.waitForSelector(".card", { timeout: 20_000 });
  await d.waitForTimeout(2500);
  await shot(d, "desktop-01-dashboard");
  await shot(d, "desktop-02-dashboard-full", true);
  const meSearch = d.locator(".card", { hasText: "ME Network" }).locator(".search-input");
  if (await meSearch.count()) {
    await meSearch.first().fill("dia");
    await d.waitForSelector(".card:has-text('ME Network') .item-row", { timeout: 10_000 }).catch(() => {});
    await d.waitForTimeout(800);
    await shot(d, "desktop-03-me-search");
  }
} finally {
  await browser.close();
}
writeFileSync(join(out, "problems.txt"), problems.join("\n"));
console.log(problems.length ? `browser problems:\n  ${problems.join("\n  ")}` : "no browser errors");
