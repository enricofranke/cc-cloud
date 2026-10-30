// Screenshots of the v0.10 screens (scenes, schedules, production, API keys) on desktop 1280×800 and phone
// 390×844, dark and light, written to cloud/screenshots/ (desktop-v10-…, phone-v10-…). Run `pnpm build` first.
//   node dev/scenes-shots.ts            (uses the system Chrome; PW_CHANNEL=chromium to change)
//   SHOTS_OUT=/some/dir node dev/scenes-shots.ts   also copies the files there
import { chromium, type Page } from "playwright";
import { copyFileSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { createMockServer } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const out = resolve(here, "../screenshots");
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (/^(desktop|phone)-v10-/.test(f)) rmSync(resolve(out, f));
const staticDir = resolve(here, "../../src/main/resources/cccloud_web");
// port 0: the system picks a free port
const mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, approveDelayMs: 200, staticDir, log: () => {} });
const origin = `http://localhost:${mock.port}`;
const mf = mock.base.id;
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
const problems: string[] = [];
const saved: string[] = [];
const watch = (page: Page, name: string) => {
  page.on("pageerror", (e) => problems.push(`${name} pageerror: ${e.message}`));
  page.on("console", (m) => m.type() === "error" && !/status of 40[349]/.test(m.text()) && problems.push(`${name} console: ${m.text()}`));
};
const shot = async (page: Page, name: string) => {
  await page.waitForTimeout(250);
  // nothing may stick out sideways
  const over = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  if (over > 1) problems.push(`${name}: page is ${over}px wider than the viewport`);
  await page.screenshot({ path: `${out}/${name}.png` });
  saved.push(`${name}.png`);
  console.log("saved", `screenshots/${name}.png`);
};
const clearToasts = async (page: Page) => {
  for (const b of await page.locator(".toast-close").all()) await b.click({ timeout: 500 }).catch(() => {});
};
const acct = async (player: string) => (await (await fetch(`${origin}/dev/token`, { method: "POST", body: JSON.stringify({ account: player, name: `${player}'s PC` }) })).json()).token as string;

async function session(kind: "desktop" | "phone", theme: "dark" | "light", lang: "en" | "de" = "en") {
  const ctx = await browser.newContext(
    kind === "desktop" ? { viewport: { width: 1280, height: 800 } } : { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
  );
  const page = await ctx.newPage();
  watch(page, `${kind}-${theme}`);
  const token = await acct("Enrico");
  await page.addInitScript(
    ([a, th, l]) => {
      localStorage.setItem("cccloud.account.v1", JSON.stringify(a));
      localStorage.setItem("cccloud.theme", th as string);
      localStorage.setItem("cccloud.lang", l as string);
    },
    [{ token, player: "Enrico", deviceName: "Enrico's PC", linkedAt: Date.now() }, theme, lang] as const,
  );
  return { ctx, page };
}

try {
  for (const kind of ["desktop", "phone"] as const)
    for (const theme of ["dark", "light"] as const) {
      const sfx = theme === "light" ? "-light" : "";
      const { ctx, page: p } = await session(kind, theme);
      const name = (n: string) => `${kind}-v10-${n}${sfx}`;

      // dashboard: the Scenes card and the pinned Production card (end of the main page)
      await p.goto(`${origin}/#/m/${mf}`);
      await p.waitForSelector('.card[aria-label="Scenes"]');
      await p.waitForTimeout(400);
      await clearToasts(p);
      await p.locator('.card[aria-label="Scenes"]').scrollIntoViewIfNeeded();
      await p.evaluate(() => window.scrollBy(0, -80));
      await shot(p, name("1-dashboard-cards"));

      // scenes list, a test run in progress (Reactor safe waits 5 s between its steps)
      await p.goto(`${origin}/#/m/${mf}/scenes`);
      await p.waitForSelector(".scene-row");
      await p.locator('.scene-row:has-text("Reactor safe") .btn-run').click();
      await p.waitForSelector(".dialog");
      await p.click(".dialog .pxbtn");
      await p.waitForSelector(".scene-row.running .ss-run");
      // the run's own alerts would cover the list
      const toasts = (on: boolean) => p.evaluate((v) => ((document.querySelector(".toasts") as HTMLElement).style.visibility = v), on ? "visible" : "hidden");
      await toasts(false);
      await p.waitForTimeout(400);
      await p.evaluate(() => window.scrollTo(0, 0));
      await shot(p, name("2-scenes"));
      await p.waitForSelector(".scene-row.running", { state: "detached", timeout: 10_000 });
      await toasts(true);
      await clearToasts(p);

      // the scene editor
      await clearToasts(p);
      await p.locator('.scene-row:has-text("Night mode") button:has-text("Edit")').click();
      await p.waitForSelector(".scene-editor");
      await p.waitForTimeout(200);
      await shot(p, name("3-scene-editor"));
      if (kind === "desktop") {
        await p.locator(".sheet-body").evaluate((el) => el.scrollTo(0, 420));
        await shot(p, name("3b-scene-editor-steps"));
      }
      await p.keyboard.press("Escape");

      // the rules editor with a schedule trigger and a scene action ("Schedule…" on Good morning)
      await p.locator('.scene-row:has-text("Night mode") button:has-text("Schedule")').click();
      await p.waitForSelector(".rule-editor .day-chips");
      await p.locator('.rule-editor .day-chip:has-text("Sat")').click();
      await p.fill('.rule-editor input[aria-label="Time (24 h)"]', "22:30");
      await p.locator(".sheet-body").evaluate((el) => el.scrollTo(0, 0));
      await clearToasts(p);
      await p.waitForTimeout(200);
      await shot(p, name("4-rule-schedule"));
      await p.keyboard.press("Escape");
      await p.waitForSelector(".rule-row");
      await p.locator('.rule-row:has-text("Coolant check")').scrollIntoViewIfNeeded();
      await p.evaluate((dy) => window.scrollBy(0, dy), kind === "desktop" ? 120 : 60);
      await shot(p, name("4b-rules-next-run"));

      // production page and the item drawer
      await p.goto(`${origin}/#/m/${mf}/production`);
      await p.waitForSelector(".prod-card .chart-line");
      await p.waitForTimeout(300);
      await clearToasts(p);
      await shot(p, name("5-production"));
      // the bottom of the page: what is pinned on the Production card
      await p.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      await shot(p, name("5b-production-pins"));
      await p.evaluate(() => window.scrollTo(0, 0));
      await p.locator('.movers:has(.mover-rate.down) .mover:has-text("Iron Ingot")').first().click();
      await p.waitForSelector(".item-drawer .prod-stats");
      await p.waitForTimeout(300);
      await shot(p, name("6-item-drawer"));
      await p.locator(".sheet-body").evaluate((el) => el.scrollTo(0, el.scrollHeight));
      await shot(p, name("6b-item-drawer-jobs"));
      await p.keyboard.press("Escape");

      // API keys: the list, the form, the new key shown once
      await p.goto(`${origin}/#/m/${mf}/apikeys`);
      await p.waitForSelector(".key-row");
      await p.waitForTimeout(200);
      await shot(p, name("7-api-keys"));
      await p.click('.pxbtn:has-text("New key")');
      await p.waitForSelector(".key-form");
      await p.fill('.key-form input[maxlength="40"]', "Discord bot 2");
      await p.locator('.key-form .perm-row:has-text("run:scenes") input').check();
      await p.fill(".key-form textarea", "203.0.113.0/24");
      await shot(p, name("8-api-key-form"));
      await p.click('.key-form button[type="submit"]');
      await p.waitForSelector(".new-key");
      await p.waitForTimeout(200);
      await shot(p, name("9-api-key-created"));
      await ctx.close();
      // revoke the key again so every run sees the same list
      for (const k of [...mock.apiKeys.values()].filter((x) => x.name === "Discord bot 2")) mock.apiKeys.delete(k.id);
    }

  // German: scenes and production on the phone (dark)
  {
    const { ctx, page: p } = await session("phone", "dark", "de");
    await p.goto(`${origin}/#/m/${mf}/scenes`);
    await p.waitForSelector(".scene-row");
    await shot(p, "phone-v10-de-scenes");
    await p.goto(`${origin}/#/m/${mf}/production`);
    await p.waitForSelector(".prod-card .chart-line");
    await shot(p, "phone-v10-de-production");
    await ctx.close();
  }
} catch (e) {
  problems.push(`script: ${(e as Error).stack ?? e}`);
} finally {
  await browser.close();
  await mock.close();
}
if (process.env.SHOTS_OUT) {
  mkdirSync(process.env.SHOTS_OUT, { recursive: true });
  for (const f of saved) copyFileSync(resolve(out, f), resolve(process.env.SHOTS_OUT, f));
}
if (problems.length) {
  console.log(`\n${problems.length} problem(s):\n${problems.join("\n")}`);
  process.exitCode = 1;
}
