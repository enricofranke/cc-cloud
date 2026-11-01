// Browser check of the report prompt, the preview and the feedback sheet against the mock, one browser at a time.
//   pnpm build && node dev/reports-ui.ts [staticDir] [screenshotDir]      (CHROME_PATH: another browser binary)
import assert from "node:assert/strict";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium, type Page } from "playwright";
import { createMockServer, type MockServer } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const staticDir = resolve(process.argv[2] ?? resolve(here, "../../src/main/resources/cccloud_web"));
const shots = process.argv[3] ? resolve(process.argv[3]) : null;
if (shots) mkdirSync(shots, { recursive: true });

const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
let failed = 0;

async function withDashboard(opts: { canControl?: boolean; lang?: string; width?: number }, run: (page: Page, mock: MockServer) => Promise<void>) {
  const mock = await createMockServer({ port: 0, host: "127.0.0.1", staticDir, log: () => {} });
  const origin = `http://localhost:${mock.port}`;
  const issued = await (await fetch(`${origin}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Check", canControl: opts.canControl !== false }) })).json();
  const context = await browser.newContext({ viewport: { width: opts.width ?? 1280, height: 900 }, bypassCSP: true });
  await context.addInitScript(
    ([token, id, name, language]) => {
      localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([{ mainframeId: id, token, name, deviceName: "Check", pairedAt: Date.now() }]));
      localStorage.setItem("cccloud.lang", language);
    },
    [issued.token, mock.base.id, issued.name, opts.lang ?? "en"],
  );
  const page = await context.newPage();
  try {
    await page.goto(`${origin}/#/m/${mock.base.id}`);
    await page.waitForSelector(".dash-head", { timeout: 20_000 });
    await page.waitForFunction(() => document.querySelector(".pill-online"), undefined, { timeout: 20_000 });
    await run(page, mock);
  } finally {
    await context.close();
    await mock.close();
  }
}

async function check(name: string, opts: Parameters<typeof withDashboard>[0], run: Parameters<typeof withDashboard>[1]) {
  try {
    await withDashboard(opts, run);
    console.log(`ok    ${name}`);
  } catch (e) {
    failed++;
    console.log(`FAIL  ${name}\n      ${(e as Error).message}`);
  }
}

const prompt = (p: Page) => p.locator(".banner-reports");
const settings = async (p: Page) => {
  await p.click('button[aria-label="Settings"]');
  await p.waitForSelector(".settings-links");
};

await check("the owner gets the prompt with equal choices, nothing preselected", {}, async (p) => {
  await prompt(p).waitFor();
  const groups = prompt(p).locator(".rp-seg");
  assert.equal(await groups.count(), 2);
  for (let i = 0; i < 2; i++) {
    const [yes, no] = [groups.nth(i).locator("button").nth(0), groups.nth(i).locator("button").nth(1)];
    assert.equal(await yes.getAttribute("aria-checked"), "false");
    assert.equal(await no.getAttribute("aria-checked"), "false");
    const [a, b] = [await yes.boundingBox(), await no.boundingBox()];
    assert.ok(Math.abs(a!.width - b!.width) < 1 && a!.height === b!.height, "Yes and No have the same size");
    assert.equal(await yes.getAttribute("class"), await no.getAttribute("class"), "Yes and No have the same style");
  }
  const text = await prompt(p).innerText();
  assert.match(text, /operator of the server/);
  assert.match(text, /under 16/);
  assert.equal(await prompt(p).locator('a[href="https://cccloud.dev/en/legal/privacy/"]').count(), 1);
  if (shots) await p.screenshot({ path: `${shots}/prompt.png` });
});

await check("the preview shows the real payloads", {}, async (p) => {
  await prompt(p).locator("button", { hasText: "Show exactly" }).click();
  const sheet = p.locator(".sheet");
  await sheet.locator("pre").first().waitFor();
  assert.equal(await sheet.locator("pre").count(), 2);
  assert.match(await sheet.locator("pre").first().innerText(), /"fingerprint"/);
  assert.match(await sheet.innerText(), /no crash happened/);
  assert.match(await sheet.locator("pre").nth(1).innerText(), /"integrations"/);
  if (shots) await p.screenshot({ path: `${shots}/preview.png` });
});

await check("closing the prompt decides nothing and it stays away", {}, async (p, mock) => {
  await prompt(p).locator('button[aria-label="Not now"]').click();
  await prompt(p).waitFor({ state: "detached" });
  assert.deepEqual(mock.reports.channels, { crash: "ask", stats: "ask" });
  await p.reload();
  await p.waitForFunction(() => document.querySelector(".pill-online"));
  await p.waitForTimeout(400);
  assert.equal(await prompt(p).count(), 0);
});

await check("a decision per channel, and a yes can be taken back in the settings", {}, async (p, mock) => {
  await prompt(p).locator(".rp-seg").nth(0).locator("button", { hasText: "Yes" }).click();
  await prompt(p).locator('.rp-seg button[aria-checked="true"]').waitFor();
  await prompt(p).locator(".rp-seg").nth(1).locator("button", { hasText: "No" }).click();
  await prompt(p).waitFor({ state: "detached" });
  assert.deepEqual(mock.reports.channels, { crash: "active", stats: "declined" });
  await settings(p);
  await p.click('button:has-text("Crash reports & stats")');
  const sheet = p.locator(".sheet");
  await sheet.locator(".rp-seg").first().waitFor();
  await sheet.locator(".rp-seg").nth(0).locator("button", { hasText: "No" }).click();
  await sheet.locator('.rp-seg button[aria-checked="true"]:has-text("No")').nth(1).waitFor();
  assert.equal(mock.reports.channels.crash, "declined");
});

await check("a read-only browser sees neither prompt nor settings", { canControl: false }, async (p) => {
  await p.waitForTimeout(400);
  assert.equal(await prompt(p).count(), 0);
  await settings(p);
  assert.equal(await p.locator('button:has-text("Crash reports & stats")').count(), 0);
  assert.equal(await p.locator('button:has-text("Send feedback")').count(), 0);
});

await check("feedback shows the exact message and needs its own Send", {}, async (p, mock) => {
  await prompt(p).waitFor();
  await settings(p);
  await p.click('button:has-text("Send feedback")');
  const sheet = p.locator(".sheet");
  const next = sheet.locator("button", { hasText: "Show what would be sent" });
  assert.equal(await next.isDisabled(), true, "no empty message");
  assert.equal(await sheet.locator('input[type="checkbox"]').isChecked(), false, "technical info stays off");
  assert.match(await sheet.innerText(), /personal data/);
  if (shots) await p.screenshot({ path: `${shots}/feedback.png` });
  await sheet.locator("textarea").fill("Broke at /home/steve/world");
  await next.click();
  const message = sheet.locator("pre");
  await message.waitFor();
  assert.match(await message.innerText(), /<path>/);
  assert.doesNotMatch(await message.innerText(), /"env"/);
  assert.doesNotMatch(await message.innerText(), /steve/);
  assert.equal(mock.reports.feedback.length, 0, "nothing was sent by looking at it");
  if (shots) await p.screenshot({ path: `${shots}/feedback-review.png` });
  await sheet.locator("button", { hasText: "Edit" }).click();
  await sheet.locator('input[type="checkbox"]').check();
  await sheet.locator("input.input").fill("steve#1");
  await next.click();
  await message.waitFor();
  assert.match(await message.innerText(), /"env"/);
  assert.match(await message.innerText(), /steve#1/);
  await sheet.locator("button", { hasText: /^Send$/ }).click();
  await sheet.waitFor({ state: "detached" });
  assert.equal(mock.reports.feedback.length, 1);
});

await check("German texts and the German policy", { lang: "de" }, async (p) => {
  await prompt(p).waitFor();
  const text = await prompt(p).innerText();
  assert.match(text, /Betreiber des Servers/);
  assert.match(text, /unter 16/);
  assert.equal(await prompt(p).locator('a[href="https://cccloud.dev/de/legal/privacy/"]').count(), 1);
});

await check("fits a phone", { width: 375 }, async (p) => {
  await prompt(p).waitFor();
  const overflow = await p.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(overflow <= 0, `page scrolls sideways by ${overflow}px`);
  if (shots) await p.screenshot({ path: `${shots}/prompt-phone.png` });
});

await browser.close();
console.log(failed ? `${failed} failed` : "all passed");
process.exit(failed ? 1 : 0);
