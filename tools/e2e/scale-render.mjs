#!/usr/bin/env node
// Dashboard render cost on a huge base (docs/SPEC.md "Scaling"). Start the scale benchmark with a hold phase:
//   ./gradlew runGameTestServer -PscaleTest -PscaleTestHold=180 --max-workers=3
// and, once run/scaletest-session.json exists (the log says "SCALE TEST holding"), run:
//   node tools/e2e/scale-render.mjs [run/scaletest-session.json] [--runs 3] [--watch 15]
// Opens the big mainframe's dashboard in headless Chromium (the device token goes into localStorage like a paired
// browser), measures the time to the first card and to all cards of the first page, then watches the live updates for
// --watch seconds: long tasks (main thread blocked > 50 ms), frames, JS heap and DOM size. Prints one JSON object.
import { createRequire } from "node:module";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(here, "../../cloud/package.json"));
const { chromium } = require("playwright");

const argv = process.argv.slice(2);
const opt = (name, def) => { const i = argv.indexOf(`--${name}`); return i >= 0 ? argv[i + 1] : def; };
const positional = argv.filter((a, i) => !a.startsWith("--") && !(i > 0 && argv[i - 1].startsWith("--")));
const sessionFile = positional[0] ?? join(here, "../../run/scaletest-session.json");
if (!existsSync(sessionFile)) {
  console.error(`No session file at ${sessionFile}: start the scale test with -PscaleTestHold=180 first.`);
  process.exit(2);
}
const session = JSON.parse(readFileSync(sessionFile, "utf8"));
const RUNS = Number(opt("runs", 3));
const WATCH_S = Number(opt("watch", 15));
const WIDTH = Number(opt("width", 1440));

const browser = await chromium.launch({ headless: true });
const results = [];
for (let run = 0; run < RUNS; run++) {
  const context = await browser.newContext({ viewport: { width: WIDTH, height: 900 } });
  await context.addInitScript((s) => {
    localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([
      { mainframeId: s.mainframeId, token: s.token, name: s.name, deviceName: "scale-render", pairedAt: Date.now() },
    ]));
    // long tasks from the very start (the first render is the interesting one)
    window.__long = [];
    new PerformanceObserver((list) => { for (const e of list.getEntries()) window.__long.push([e.startTime, e.duration]); })
      .observe({ type: "longtask", buffered: true });
    window.__ws = { frames: 0, chars: 0, firstSnapshotAt: 0 };
    const Orig = window.WebSocket;
    window.WebSocket = class extends Orig {
      constructor(...a) {
        super(...a);
        this.addEventListener("message", (e) => {
          window.__ws.frames++;
          window.__ws.chars += typeof e.data === "string" ? e.data.length : 0;
          if (!window.__ws.firstSnapshotAt && typeof e.data === "string" && e.data.startsWith('{"t":"snapshot"')) window.__ws.firstSnapshotAt = performance.now();
        });
      }
    };
  }, session);
  const page = await context.newPage();
  const url = `${session.url}#/m/${encodeURIComponent(session.mainframeId)}`;
  const t0 = Date.now();
  await page.goto(url, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("[data-card]", { timeout: 60_000 });
  const firstCardMs = Date.now() - t0;
  // all cards of the first page: the count stops growing for 500 ms
  let last = -1, stableSince = Date.now(), cards = 0;
  while (Date.now() - stableSince < 500 && Date.now() - t0 < 60_000) {
    cards = await page.evaluate(() => document.querySelectorAll("[data-card]").length);
    if (cards !== last) { last = cards; stableSince = Date.now(); }
    await page.waitForTimeout(50);
  }
  const allCardsMs = stableSince - t0;
  const load = await page.evaluate(() => ({
    snapshotAtMs: Math.round(window.__ws.firstSnapshotAt),
    longTasks: window.__long.length,
    longTaskMs: Math.round(window.__long.reduce((a, [, d]) => a + d, 0)),
    maxLongTaskMs: Math.round(Math.max(0, ...window.__long.map(([, d]) => d))),
  }));
  // live updates
  await page.evaluate(() => {
    window.__long = [];
    window.__frames = 0;
    window.__ws.frames = 0;
    window.__ws.chars = 0;
    const tick = () => { window.__frames++; requestAnimationFrame(tick); };
    requestAnimationFrame(tick);
  });
  await page.waitForTimeout(WATCH_S * 1000);
  const live = await page.evaluate((s) => ({
    fps: Math.round(window.__frames / s),
    longTasksPerSecond: Math.round(window.__long.length / s * 100) / 100,
    busyPercent: Math.round(window.__long.reduce((a, [, d]) => a + d, 0) / (s * 10)),
    maxLongTaskMs: Math.round(Math.max(0, ...window.__long.map(([, d]) => d))),
    wsFramesPerSecond: Math.round(window.__ws.frames / s),
    wsKiBPerSecond: Math.round(window.__ws.chars / 1024 / s * 10) / 10,
    heapMiB: performance.memory ? Math.round(performance.memory.usedJSHeapSize / 1048576) : null,
    domNodes: document.getElementsByTagName("*").length,
  }), WATCH_S);
  results.push({ run, cards, firstCardMs, allCardsMs, ...load, live });
  await context.close();
}
await browser.close();
const median = (k, f = (r) => r[k]) => { const v = results.map(f).sort((a, b) => a - b); return v[Math.floor(v.length / 2)]; };
console.log(JSON.stringify({
  url: session.url,
  runs: results,
  median: {
    firstCardMs: median("firstCardMs"),
    allCardsMs: median("allCardsMs"),
    snapshotAtMs: median("snapshotAtMs"),
    loadLongTaskMs: median("longTaskMs"),
    liveBusyPercent: median(null, (r) => r.live.busyPercent),
    liveFps: median(null, (r) => r.live.fps),
  },
}, null, 2));
