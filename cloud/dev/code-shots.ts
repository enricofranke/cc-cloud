// Screenshots of Cloud Scripts (list, editor with completions and hover help, error marker, log, version diff) on
// desktop 1280×800 and phone 390×844, dark and light, against the mock. Run `pnpm build` first.
//   node dev/code-shots.ts                 writes cloud/screenshots/{desktop,phone}-cs-…png (system Chrome)
//   SHOTS_OUT=/some/dir node dev/code-shots.ts   writes there instead
//   SHOTS_PORT=4215                        fixed mock port (default: a free one)
import { chromium, type Page } from "playwright";
import { mkdirSync, readdirSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { createMockServer } from "./mock-server.ts";

const here = fileURLToPath(new URL(".", import.meta.url));
const out = resolve(process.env.SHOTS_OUT ?? resolve(here, "../screenshots"));
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (/^(desktop|phone)-cs-/.test(f)) rmSync(resolve(out, f));
const staticDir = resolve(here, "../../src/main/resources/cccloud_web");
const mock = await createMockServer({ port: Number(process.env.SHOTS_PORT ?? 0), host: "127.0.0.1", tickMs: 0, approveDelayMs: 200, staticDir, log: () => {} });
const origin = `http://localhost:${mock.port}`;
const mf = mock.base.id;
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
const problems: string[] = [];
const watch = (page: Page, name: string) => {
  page.on("pageerror", (e) => problems.push(`${name} pageerror: ${e.message}`));
  page.on("console", (m) => m.type() === "error" && !/status of 40[349]/.test(m.text()) && problems.push(`${name} console: ${m.text()}`));
};
const shot = async (page: Page, name: string) => {
  await page.waitForTimeout(250);
  const over = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  if (over > 1) problems.push(`${name}: page is ${over}px wider than the viewport`);
  await page.screenshot({ path: `${out}/${name}.png` });
  console.log("saved", `${out}/${name}.png`);
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

/** Opens a script from the list and waits for its editor. */
async function open(p: Page, name: string) {
  await p.goto(`${origin}/#/m/${mf}/scripts`);
  await p.click(`.code-name:has-text("${name}")`);
  await p.waitForSelector(".cx-host .cm-content");
  await p.waitForTimeout(300);
}
/** Moves the mouse onto `word` in the first editor line containing `line` (the editor lives in a shadow root). */
async function hoverWord(p: Page, line: string, word: string) {
  const box = await p.evaluate(
    ([line, word]) => {
      const root = document.querySelector(".cx-host")?.shadowRoot;
      const row = [...(root?.querySelectorAll(".cm-line") ?? [])].find((l) => l.textContent?.includes(line));
      if (!row) return null;
      const walker = document.createTreeWalker(row, NodeFilter.SHOW_TEXT);
      const at = row.textContent!.indexOf(line) + line.indexOf(word);
      let seen = 0;
      for (let n = walker.nextNode(); n; n = walker.nextNode()) {
        const len = n.textContent!.length;
        if (seen + len > at) {
          const r = document.createRange();
          r.setStart(n, at - seen);
          r.setEnd(n, Math.min(len, at - seen + word.length));
          const b = r.getBoundingClientRect();
          return { x: b.x + b.width / 2, y: b.y + b.height / 2 };
        }
        seen += len;
      }
      return null;
    },
    [line, word] as const,
  );
  if (!box) throw new Error(`no "${word}" in a line with "${line}"`);
  await p.mouse.move(box.x, box.y, { steps: 3 });
}
/** Puts the cursor at the end of a line (1-based) of the editor. */
async function lineEnd(p: Page, line: number) {
  await p.locator(".cx-host .cm-line").nth(line - 1).click();
  await p.keyboard.press("End");
}

try {
  for (const kind of ["desktop", "phone"] as const)
    for (const theme of ["dark", "light"] as const) {
      const sfx = theme === "light" ? "-light" : "";
      const { ctx, page: p } = await session(kind, theme);
      const name = (n: string) => `${kind}-cs-${n}${sfx}`;
      const phone = kind === "phone";
      const tab = (label: string) => p.click(`.code-phone-tabs button:has-text("${label}")`);

      // the list: a running autostart script, one by Lisa, one stopped by an error
      await p.goto(`${origin}/#/m/${mf}/scripts`);
      await p.waitForSelector(".code-row");
      // let the autostart script report some CPU
      await p.waitForTimeout(1500);
      await clearToasts(p);
      await shot(p, name("1-list"));

      // the reactor guard: completions on a new line, then the hover help of cloud.set
      await open(p, "Reactor guard");
      await lineEnd(p, 3);
      await p.keyboard.press("Enter");
      await p.keyboard.type("cloud.ge", { delay: 30 });
      await p.waitForSelector(".cx-host .cm-tooltip-autocomplete");
      await p.waitForTimeout(300);
      await shot(p, name("2-complete"));
      await p.keyboard.press("Escape");
      await p.keyboard.type("tValue(CARD, ", { delay: 20 });
      await p.waitForTimeout(300);
      await shot(p, name("3-signature"));
      // drop the edit again so the version list below stays clean
      await p.keyboard.press(process.platform === "darwin" ? "Meta+z" : "Control+z");
      for (let i = 0; i < 4; i++) await p.keyboard.press(process.platform === "darwin" ? "Meta+z" : "Control+z");
      await p.locator(".cx-host .cm-content").blur();
      if (!phone) {
        await hoverWord(p, "cloud.set(CARD", "set");
        await p.waitForSelector(".cx-host .cm-tooltip-hover");
        await p.waitForTimeout(300);
        await shot(p, name("4-hover"));
        await p.mouse.move(5, 5);
      }

      // the log of the running script
      if (phone) await tab("Log");
      await p.waitForSelector(".code-log-lines li.lv-info, .code-log-lines li.lv-system");
      await shot(p, name("5-log"));

      // versions: the older one against the current
      if (phone) await tab("Versions");
      else await p.click('.code-side-tabs button:has-text("Versions")');
      await p.waitForSelector(".code-version");
      await p.locator(".code-version").nth(1).click();
      await p.waitForSelector(".code-diff-lines");
      await shot(p, name("6-diff"));

      // the door timer stopped with a runtime error on line 6, plus a syntax error typed below
      await open(p, "Door timer");
      if (phone) await tab("Code");
      await shot(p, name("7-runtime-error"));
      await lineEnd(p, 2);
      await p.keyboard.press("Enter");
      await p.keyboard.type("if cloud.isOnline(GATE)", { delay: 20 });
      // the runtime marker only belongs to the saved text, so after the edit the check's marker is the only one
      await p.waitForSelector(".cx-host .cm-lintRange-error", { timeout: 5000 }).catch(() => problems.push(`${name("8")}: no error marker`));
      // the marker's message (the string is not an API name, so only the lint tooltip shows)
      if (!phone) await hoverWord(p, 'cloud.at("07:00"', "07:00");
      await p.waitForTimeout(600);
      await shot(p, name("8-syntax-error"));
      // leave without the discard question
      await p.keyboard.press(process.platform === "darwin" ? "Meta+z" : "Control+z");
      await ctx.close();
    }

  // German: list and editor hover on the desktop (dark), the editor on the phone
  {
    const { ctx, page: p } = await session("desktop", "dark", "de");
    await p.goto(`${origin}/#/m/${mf}/scripts`);
    await p.waitForSelector(".code-row");
    await shot(p, "desktop-cs-de-list");
    await open(p, "Reactor guard");
    await hoverWord(p, 'cloud.on("above"', "on");
    await p.waitForSelector(".cx-host .cm-tooltip-hover");
    await p.waitForTimeout(300);
    await shot(p, "desktop-cs-de-hover");
    await ctx.close();
  }
  {
    const { ctx, page: p } = await session("phone", "dark", "de");
    await p.goto(`${origin}/#/m/${mf}/scripts`);
    await p.waitForSelector(".code-row");
    await p.click(".code-head .pxbtn");
    await p.waitForSelector(".code-templates");
    await shot(p, "phone-cs-de-new");
    await ctx.close();
  }
} catch (e) {
  problems.push(`script: ${(e as Error).stack ?? e}`);
} finally {
  await browser.close();
  await mock.close();
}
if (problems.length) {
  console.log(`\n${problems.length} problem(s):\n${problems.join("\n")}`);
  process.exitCode = 1;
}
