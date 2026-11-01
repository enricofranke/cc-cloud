// End-to-end run of the live demo in a real browser, on desktop and phone width. Serve a build first:
//   pnpm build:demo && pnpm preview:demo            then: pnpm test:demo
// DEMO_URL=<url of the demo> tests another server (for the site: the built website behind its _headers).
// DEMO_SHOTS=<dir> keeps the screenshots there (default: a folder in the system's temp dir). PW_CHANNEL as in dev/qa.ts.
import { mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { chromium, type Page } from "playwright";

const URL_ = process.env.DEMO_URL ?? "http://localhost:4174/demo/";
const origin = new URL(URL_).origin;
const SHOTS = process.env.DEMO_SHOTS ?? join(tmpdir(), "cccloud-demo-shots");
mkdirSync(SHOTS, { recursive: true });

const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome", args: ["--enable-unsafe-swiftshader"] });
let failures = 0;

function check(ok: unknown, what: string) {
  console.log(`  ${ok ? "ok  " : "FAIL"} ${what}`);
  if (!ok) failures++;
}

const VIEWS = [
  { name: "desktop", viewport: { width: 1280, height: 860 } },
  { name: "phone", viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 },
];

async function open(page: Page, url = `${URL_}?lang=en`) {
  await page.goto(url);
  await page.waitForSelector("#demo-boot", { state: "detached", timeout: 20_000 });
}

const noSideScroll = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);
const closeSheets = async (page: Page) => {
  for (let i = 0; i < 5 && (await page.locator(".backdrop").count()); i++) {
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
  }
};
const cardCount = (page: Page) => page.locator(".card:visible").count();
const reactor = (page: Page) => page.locator(".card", { hasText: "Fission Reactor" }).first();
const statusOf = (page: Page) => reactor(page).locator("text=/^(Running|Shut down|Overheating|CRITICAL)$/").first().textContent();

for (const view of VIEWS) {
  console.log(view.name);
  const { name, ...options } = view;
  const context = await browser.newContext({ ...options, locale: "en-US" });
  const page = await context.newPage();
  const problems: string[] = [];
  const outside: string[] = [];
  page.on("console", (m) => ["error", "warning"].includes(m.type()) && problems.push(`${m.type()}: ${m.text()}`));
  page.on("pageerror", (e) => problems.push(`exception: ${e.message}`));
  page.on("response", (r) => r.status() >= 400 && problems.push(`${r.status()} ${r.url()}`));
  page.on("request", (r) => {
    const url = r.url();
    if (!url.startsWith(`${origin}/`) && !url.startsWith("data:") && !url.startsWith("blob:")) outside.push(url);
  });
  const shot = (step: string) => page.screenshot({ path: join(SHOTS, `${name}-${step}.png`) });

  // ---- open
  const started = Date.now();
  await open(page);
  console.log(`  opened in ${Date.now() - started} ms`);
  check(await page.locator(".demo-bar-text").textContent().then((t) => t?.includes("Live demo: simulated base")), "banner");
  check(await page.locator('a.demo-get[href="/en/download/"]').count(), "download link in the banner");
  check(page.url().includes("#/m/"), "opens on the base, no pairing");
  check(await page.locator(".card").count().then((n) => n >= 5), "populated dashboard");
  check(await page.locator("text=/Connecting/i").count().then((n) => n === 0), "never says Connecting");
  check(await noSideScroll(page), "no horizontal scroll");
  await shot("01-open");

  // ---- tour
  await page.waitForSelector(".demo-tour");
  const steps = 5;
  for (let i = 0; i < steps; i++) {
    const title = await page.locator(".demo-tour h2").textContent();
    await page.waitForFunction(() => document.querySelector<HTMLElement>(".demo-ring")?.style.opacity === "1", null, { timeout: 5000 }).catch(() => {});
    await shot(`02-tour-${i + 1}`);
    await page.locator(".demo-tour .demo-tool", { hasText: "Show me" }).click();
    await page.waitForSelector(".demo-ok", { timeout: 15_000 }).catch(() => {});
    check(await page.locator(".demo-ok").count(), `tour: ${title}`);
    await page.waitForTimeout(i === 1 ? 2500 : 1200); // the craft step ends on the job list
    await shot(`02-tour-${i + 1}-done`);
    check(await noSideScroll(page), "  no horizontal scroll");
    await closeSheets(page);
    await page.locator(".demo-tour .demo-get, .demo-tour .demo-tool", { hasText: "Next" }).last().click();
  }
  await shot("02-tour-end");
  check(await page.locator('.demo-tour a[href="/en/download/"]').count(), "tour ends at the download");
  await page.locator(".demo-tour .demo-tool", { hasText: "Keep exploring" }).click();
  check(!(await page.locator(".demo-tour").count()), "tour closes");
  await page.locator(".tabbar .tab, .toptabs .tab").first().click();

  // ---- toggle: the tour's SCRAM left the reactor off
  await reactor(page).waitFor();
  check((await statusOf(page)) === "Shut down", "SCRAM shut the reactor down");
  await reactor(page).locator('[role="switch"]').first().click();
  check(await reactor(page).locator("text=/^Running$/").waitFor({ timeout: 5000 }).then(() => true, () => false), "toggle starts the reactor");

  // ---- search
  await page.locator(".search-clear").first().click().catch(() => {});
  await page.locator(".search-input").first().fill("diamond");
  await page.waitForSelector(".item-row");
  check(await page.locator(".item-row", { hasText: /diamond/i }).count(), "search finds diamonds");
  await shot("03-search");

  // ---- craft: a job appears, runs and finishes
  await page.locator(".search-input").first().fill("calculation processor");
  await page.locator(".item-row", { hasText: /calculation processor/i }).first().click();
  await page.locator(".backdrop .pxbtn", { hasText: "Craft…" }).click();
  await page.locator(".backdrop .pxbtn-wide:not([disabled]), .backdrop .btn-wide:not([disabled])").first().click({ timeout: 6000 });
  await page.waitForSelector(".toast", { timeout: 5000 });
  check(await page.locator(".toast", { hasText: /(Crafting|Ordered) .*Calculation Processor/ }).count(), "craft job started");
  await closeSheets(page);
  await page.locator(".search-clear").first().click();
  await page.locator(".store-quick .btn", { hasText: "CPUs" }).first().click();
  await page.waitForSelector(".backdrop");
  await page.waitForTimeout(800);
  check(await page.locator(".backdrop", { hasText: "Calculation Processor" }).count(), "job listed under CPUs");
  await shot("04-craft-job");
  await closeSheets(page);

  // ---- editor: a card is added and saved
  const cards = await cardCount(page);
  await page.click('button[aria-label="Edit layout"]');
  await page.waitForSelector(".ed-bar");
  await page.waitForTimeout(400);
  if (!(await page.locator(".ed-tool:visible").count())) await page.locator(".ed-bar button", { hasText: "Toolbox" }).click();
  await page.locator(".ed-tool:visible").first().click();
  await page.waitForTimeout(500);
  await shot("05-editor");
  await closeSheets(page);
  await page.locator(".ed-bar .pxbtn").first().click();
  await page.waitForTimeout(800);
  check((await cardCount(page)) > cards, "editor adds a card and saves it");

  // ---- reload starts over, the tour stays dismissed
  await open(page);
  await page.waitForTimeout(1000);
  check((await cardCount(page)) === cards, "reload resets the layout");
  check(await reactor(page).locator("text=/^Running$/").waitFor({ timeout: 5000 }).then(() => true, () => false), "reload resets the base");
  check(!(await page.locator(".demo-tour").count()), "the tour is remembered");
  await shot("06-after-reload");

  // ---- a hidden tab stops the simulation
  const energy = () => page.locator(".card", { hasText: "Storage" }).first().innerText();
  const hide = (hidden: boolean) =>
    page.evaluate((h) => {
      Object.defineProperty(document, "hidden", { value: h, configurable: true });
      document.dispatchEvent(new Event("visibilitychange"));
    }, hidden);
  await hide(true);
  await page.waitForTimeout(300);
  const before = await energy();
  await page.waitForTimeout(2500);
  check((await energy()) === before, "hidden tab pauses the simulation");
  await hide(false);
  await page.waitForTimeout(2500);
  check((await energy()) !== before, "and it resumes");

  // ---- reset
  await page.click(".demo-bar .demo-tool:nth-of-type(2)");
  await page.waitForLoadState();
  await page.waitForSelector("#demo-boot", { state: "detached", timeout: 20_000 });
  check(page.url().includes("#/m/"), "reset opens the base again");

  check(problems.length === 0, `no console errors or failed requests (${problems.length})`);
  for (const p of problems.slice(0, 8)) console.log(`       ${p}`);
  check(outside.length === 0, `no requests outside the site (${outside.length})`);
  for (const u of outside.slice(0, 8)) console.log(`       ${u}`);
  check((await context.cookies()).length === 0, "no cookies");
  await context.close();
}

// ---- language: ?lang= first, then the browser
for (const [query, locale, expected] of [
  ["?lang=de", "en-US", "Live-Demo"],
  ["?lang=en", "de-DE", "Live demo"],
  ["", "de-DE", "Live-Demo"],
  ["", "fr-FR", "Live demo"],
] as const) {
  const context = await browser.newContext({ locale, viewport: { width: 1000, height: 700 } });
  const page = await context.newPage();
  await open(page, `${URL_}${query}`);
  check(await page.locator(".demo-bar-text").textContent().then((t) => t?.startsWith(expected)), `language ${query || "(none)"} in ${locale}`);
  await context.close();
}

// ---- reduced motion
{
  const context = await browser.newContext({ reducedMotion: "reduce", viewport: { width: 1000, height: 700 } });
  const page = await context.newPage();
  await open(page);
  await page.waitForSelector(".demo-ring", { state: "attached" });
  check((await page.locator(".demo-ring").evaluate((el) => getComputedStyle(el).animationName)) === "none", "reduced motion stops the tour's pulse");
  await context.close();
}

await browser.close();
console.log(failures ? `${failures} check(s) failed` : "all checks passed");
console.log(`screenshots: ${SHOTS}`);
process.exit(failures ? 1 : 0);
