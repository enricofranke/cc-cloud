// Visual QA: every screen at 375 / 768 / 1440 px in dark and light mode, one contact sheet per
// screen, plus an axe-core check (contrast, names, labels, focusable elements) per screen.
//   pnpm build && node dev/qa.ts [outDir] [screen…]      (default outDir: <tmp>/cccloud-qa)
import { chromium, type Browser, type Page } from "playwright";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { join, resolve } from "node:path";
import { createMockServer, REACTOR } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const out = resolve(process.argv[2] ?? join(tmpdir(), "cccloud-qa"));
const only = new Set(process.argv.slice(3));
mkdirSync(out, { recursive: true });
const staticDir = resolve(here, "../../src/main/resources/cccloud_web");
const axeSource = readFileSync(createRequire(import.meta.url).resolve("axe-core/axe.min.js"), "utf8");

const mock = await createMockServer({ port: 0, host: "127.0.0.1", extras: true, tickMs: 700, voxelRate: 400, staticDir, log: () => {} });
const origin = `http://localhost:${mock.port}`;
const base = mock.base.id;
const newBase = [...mock.mainframes.values()].find((m) => m.name.startsWith("New Base"))!.id;
const acct = async (player: string) => (await (await fetch(`${origin}/dev/token`, { method: "POST", body: JSON.stringify({ account: player, name: `${player}'s PC` }) })).json()).token as string;

const WIDTHS = [375, 768, 1440] as const;
const THEMES = ["dark", "light"] as const;

async function pin(page: Page) {
  if (!(await page.locator(".pin-dialog").count())) return;
  for (const d of "1234") await page.click(`.keypad .key:text-is("${d}")`);
  await page.click(".key-ok");
}
const settings = async (p: Page) => {
  await p.click('button[aria-label="Settings"]');
  await p.waitForSelector(".settings-links");
};

type Screen = { name: string; go: (p: Page, w: number) => Promise<unknown> };
const dash = async (p: Page) => {
  await p.goto(`${origin}/#/m/${base}`);
  await p.waitForSelector(".card", { timeout: 40_000 });
  await p.waitForTimeout(500);
};
const net = (mode: "Topology" | "Map" | "3D") => async (p: Page, w: number) => {
  await dash(p);
  await p.locator(".tab-network:visible").first().click();
  await p.waitForSelector(".net-toolbar");
  await p.click(`.seg-ctl button:has-text("${mode}")`);
  await p.waitForTimeout(mode === "3D" ? 3500 : 800);
};
const SCREENS: Screen[] = [
  { name: "pair", go: async (p) => (await p.goto(`${origin}/#/pair`), await p.waitForSelector(".input-code")) },
  { name: "list", go: async (p) => (await p.goto(`${origin}/#/`), await p.waitForSelector(".mf-card"), await p.waitForTimeout(400)) },
  { name: "dashboard", go: dash },
  { name: "empty-mainframe", go: async (p) => (await p.goto(`${origin}/#/m/${newBase}`), await p.waitForSelector(".setup, .card-empty, .empty", { timeout: 20_000 })) },
  { name: "edit-mode", go: async (p) => (await dash(p), await p.click('[aria-label="Edit layout"]'), await p.waitForSelector(".card-editing"), await p.waitForTimeout(400)) },
  { name: "storage-browse", go: async (p) => (await dash(p), await p.locator(".sb-browse").first().click(), await p.waitForSelector(".sb-tile"), await p.waitForTimeout(400)) },
  { name: "store", go: async (p) => (await dash(p), await settings(p), await p.click('.settings-links button:has-text("Script Store")'), await p.waitForSelector(".store-card")) },
  { name: "access", go: async (p) => (await p.goto(`${origin}/#/m/${base}/access`), await p.waitForSelector(".acc-table")) },
  { name: "devices", go: async (p) => (await p.goto(`${origin}/#/devices`), await p.waitForSelector(".dev-row")) },
  { name: "shares", go: async (p) => (await p.goto(`${origin}/#/m/${base}/shares`), await p.waitForTimeout(800)) },
  { name: "network-topology", go: net("Topology") },
  { name: "network-map", go: net("Map") },
  { name: "network-3d", go: net("3D") },
  { name: "rules", go: async (p) => (await p.goto(`${origin}/#/m/${base}/rules`), await p.waitForSelector(".rule-row")) },
  { name: "rule-editor", go: async (p) => (await p.goto(`${origin}/#/m/${base}/rules`), await p.waitForSelector(".rule-row"), await p.locator('.rule-row button:has-text("Edit")').first().click(), await p.waitForSelector(".rule-editor")) },
  { name: "notifications", go: async (p) => (await p.goto(`${origin}/#/m/${base}/push`), await p.waitForSelector(".push-row")) },
  {
    name: "terminal",
    go: async (p) => {
      await dash(p);
      await p.locator('button[aria-label^="Terminal of"]').first().click();
      await p.waitForSelector(".pin-dialog, .term-row");
      await pin(p);
      await p.waitForSelector(".term-row");
      await p.click(".term-screen");
      await p.keyboard.type("ls");
      await p.keyboard.press("Enter");
      await p.waitForTimeout(400);
    },
  },
  {
    name: "editor",
    go: async (p) => {
      await dash(p);
      await p.locator(".card-scripts").first().click();
      await p.waitForSelector(".open-editor");
      await p.click(".open-editor");
      await p.waitForSelector(".ed-tree");
      await p.waitForTimeout(400);
      if (await p.locator(".ed-file:not(.dir) .ed-name").count()) await p.locator(".ed-file:not(.dir) .ed-name").first().click();
      await p.waitForTimeout(500);
    },
  },
  { name: "history", go: async (p) => (await dash(p), await p.click('button[aria-label="History of Core temperature"]'), await p.waitForSelector(".hist-line"), await p.waitForTimeout(300)) },
  { name: "docs", go: async (p) => (await p.goto(`${origin}/docs/start`), await p.waitForSelector(".docs-main, main"), await p.waitForTimeout(600)) },
  { name: "docs-automation", go: async (p) => (await p.goto(`${origin}/docs/automation`), await p.waitForSelector("main"), await p.waitForTimeout(600)) },
];

type Finding = { screen: string; variant: string; rule: string; impact: string; count: number; sample: string; nodes?: { target: string; text: string }[] };
const findings: Finding[] = [];

async function runScreen(browser: Browser, s: Screen) {
  const tiles: { label: string; png: Buffer }[] = [];
  for (const theme of THEMES)
    for (const w of WIDTHS) {
      const ctx = await browser.newContext({ viewport: { width: w, height: w === 375 ? 812 : 900 }, deviceScaleFactor: 1, isMobile: w === 375, hasTouch: w < 1100, bypassCSP: true });
      const token = await acct("Enrico");
      await ctx.addInitScript(
        ([t, th]) => {
          localStorage.setItem("cccloud.account.v1", JSON.stringify({ token: t, player: "Enrico", deviceName: "QA", linkedAt: Date.now() }));
          localStorage.setItem("cccloud.theme", th);
        },
        [token, theme] as const,
      );
      const p = await ctx.newPage();
      const variant = `${w}-${theme}`;
      try {
        await s.go(p, w);
        for (const b of await p.locator(".toast-close").all()) await b.click({ timeout: 500 }).catch(() => {});
        await p.waitForTimeout(200);
        tiles.push({ label: variant, png: await p.screenshot() });
        // axe: WCAG 2 A/AA rules on what is on screen
        await p.evaluate(axeSource);
        const res = (await p.evaluate(`axe.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa"] }, resultTypes: ["violations"] })`)) as {
          violations: { id: string; impact: string; nodes: { target: string[]; any?: { data?: { fgColor?: string; bgColor?: string; contrastRatio?: number; expectedContrastRatio?: string } }[] }[] }[];
        };
        for (const v of res.violations)
          findings.push({
            screen: s.name,
            variant,
            rule: v.id,
            impact: v.impact,
            count: v.nodes.length,
            sample: v.nodes.slice(0, 3).map((n) => n.target.join(" ")).join(" | "),
            nodes: v.nodes.map((n) => {
              const d = n.any?.[0]?.data;
              return { target: n.target.join(" "), text: d?.fgColor ? `${d.fgColor} on ${d.bgColor} = ${d.contrastRatio} (need ${d.expectedContrastRatio})` : "" };
            }),
          });
      } catch (e) {
        findings.push({ screen: s.name, variant, rule: "script-error", impact: "n/a", count: 1, sample: String((e as Error).message).split("\n")[0] });
        tiles.push({ label: `${variant} (error)`, png: await p.screenshot() });
      }
      await ctx.close();
    }
  // contact sheet: dark row, light row
  const sheet = await browser.newPage({ viewport: { width: 2400, height: 1200 } });
  const cell = (t: { label: string; png: Buffer }) => `<figure><img src="data:image/png;base64,${t.png.toString("base64")}"><figcaption>${t.label}</figcaption></figure>`;
  await sheet.setContent(
    `<style>body{margin:0;background:#777;font:14px sans-serif}div{display:flex;gap:10px;padding:10px;align-items:flex-start}figure{margin:0}img{display:block;height:560px;border:1px solid #000}figcaption{color:#fff}</style><div>${tiles.slice(0, 3).map(cell).join("")}</div><div>${tiles.slice(3).map(cell).join("")}</div>`,
  );
  await sheet.waitForTimeout(200);
  await sheet.screenshot({ path: join(out, `${s.name}.png`), fullPage: true });
  await sheet.close();
  console.log("sheet", s.name);
}

const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome", args: ["--enable-unsafe-swiftshader"] });
try {
  for (const s of SCREENS) if (!only.size || only.has(s.name)) await runScreen(browser, s);
} finally {
  await browser.close();
  await mock.close();
}
const summary = new Map<string, { screens: Set<string>; count: number; impact: string; sample: string }>();
for (const f of findings) {
  const k = f.rule;
  const e = summary.get(k) ?? { screens: new Set<string>(), count: 0, impact: f.impact, sample: f.sample };
  e.screens.add(`${f.screen}@${f.variant}`);
  e.count += f.count;
  summary.set(k, e);
}
writeFileSync(join(out, "axe.json"), JSON.stringify(findings, null, 1));
for (const [rule, e] of summary) console.log(`${rule} (${e.impact}): ${e.count} nodes on ${e.screens.size} views – e.g. ${e.sample}`);
console.log(`\ncontact sheets and axe.json in ${out}`);
void REACTOR;
