#!/usr/bin/env node
// Reads every card of the machine gallery over the dashboard WebSocket, like the web app, and writes the readout report.
//   node tools/e2e/gallery-report.mjs [--shots]     docs/ATM11_READOUT.md, raw data + screenshots in $GALLERY_OUT
// Report: per mod and machine whether the card appears, its widgets with their values, flags for all-zero, empty, missing
// and nonsense values, the claims and cross-dimension results (gallery-claims.mjs, gallery-dimensions.mjs) and the error
// lines of the server log.
import { readFileSync, writeFileSync, existsSync, statSync, mkdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { Rcon } from "./rcon.mjs";
import { Dash, ORIGIN } from "./gallery/dash.mjs";
import { loadState, OUT, SERVER_DIR, readTable, layout, noteOf, TABLE } from "./gallery.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const REPORT = process.env.GALLERY_REPORT ?? join(here, "../../docs/ATM11_READOUT.md");
const SHOTS = join(OUT, "screenshots");

// ---- judging a value ------------------------------------------------------------------------------------------------
/** A string that says "nothing": no digit other than 0, or a dash. Words like "Idle" are real values. */
const zeroText = (s) => /^[\s\-–—.,/|%]*$/.test(s) || (/^[^A-Za-z1-9]*0[^A-Za-z1-9]*(?:[A-Za-z%/ ·]{0,12}[^A-Za-z1-9]*0?[^A-Za-z1-9]*)?$/.test(s) && !/[1-9]/.test(s));
/** Things a player should never read: raw keys, Java object names, broken numbers. */
const NONSENSE = [
  [/\b(NaN|Infinity|null|undefined)\b/, "broken number or null"],
  [/^(block|item|gui|container|tooltip|message|jei|screen)\.[a-z0-9_]+\.[a-z0-9_.]+$/, "untranslated key"],
  [/@[0-9a-f]{6,}\b|\bjava\.|\bnet\.minecraft\.|ResourceKey\[|Optional\[|\bBlock\{/, "Java object text"],
  [/%[sd]|\{\}/, "unfilled format string"],
  [/^-\d/, "negative"],
];
function judge(w) {
  const v = w.value;
  const flags = [];
  if (v === undefined || v === null) flags.push("missing");
  else if (typeof v === "number") {
    if (!Number.isFinite(v)) flags.push("nonsense: not finite");
    else if (v === 0) flags.push("zero");
    else if (v < 0) flags.push("nonsense: negative");
    const max = w.opts?.max ?? w.max;
    if (typeof max === "number" && v > max * 1.001) flags.push("nonsense: above its maximum");
  } else if (typeof v === "boolean") {
    if (!v) flags.push("false");
  } else if (typeof v === "string") {
    if (zeroText(v) || /^(none|empty|not set|n\/a)$/i.test(v.trim())) flags.push(v.trim() ? "zero" : "empty");
    for (const [re, why] of NONSENSE) if (re.test(v)) flags.push(`nonsense: ${why}`);
  } else if (Array.isArray(v)) {
    if (!v.length) flags.push("empty");
  } else if (typeof v === "object") {
    if (!Object.keys(v).length) flags.push("empty");
  }
  return flags;
}
const short = (v) => {
  const s = typeof v === "string" ? v : JSON.stringify(v) ?? "–";
  return s.length > 48 ? `${s.slice(0, 45)}…` : s;
};
/** The card's verdict from its widgets. Widgets that are switches or buttons say nothing about the readout. */
function verdict(rec, section) {
  if (!rec.placed) return "NOT_PLACED";
  if (!section) return "NO_SECTION";
  if (section.blocked) return "BLOCKED";
  if (!section.online) return "OFFLINE";
  if (section.mode !== "direct") return "NO_CARD";
  const data = (section.widgets ?? []).filter((w) => !["button", "toggle", "select", "slider", "input"].includes(w.type));
  if (!data.length) return "NO_WIDGETS";
  const judged = data.map((w) => ({ w, flags: judge(w) }));
  if (judged.some((j) => j.flags.some((f) => f.startsWith("nonsense")))) return "NONSENSE";
  const real = judged.filter((j) => !j.flags.some((f) => ["missing", "zero", "empty"].includes(f)));
  if (!real.length) return "EMPTY";
  return real.some((j) => isReading(j.w)) ? "OK" : "IDLE";
}
/** A status word or a setting tells nothing about the readout: only a number, stored amount or content does. */
const SETTINGS = /^(status|state|empty|redstone|redstone_mode|mode|target|speed|limit|tier|delay|range|running|active|working|signal|full|output_full)$/;
const isReading = (w) => !SETTINGS.test(w.id) && typeof w.value !== "boolean";

// ---- reading -----------------------------------------------------------------------------------------------------------
const state = loadState();
if (!state.mainframe) throw new Error("no gallery yet: node tools/e2e/gallery.mjs build");
const rcon = await Rcon.connect();
state.tokens ??= {};
if (!state.tokens.owner) {
  const out = await rcon.cmd(`cccloud dev token ${state.mainframe.id} e2e_admin`);
  state.tokens.owner = out.match(/token (\S+)/)?.[1];
  writeFileSync(join(OUT, "state.json"), JSON.stringify(state, null, 2), { mode: 0o600 });
}
const dash = await Dash.open(state.tokens.owner, { mainframeId: state.mainframe.id, settle: 6000 });
/** Cloud Switches and the like are widgets of their group's card, `d_<id>`: read as a section of their own. */
const devicePart = (rec) => {
  const group = [...dash.sections.values()].find((x) => x.title === rec.group);
  const prefix = `d_${rec.uplinkId?.replaceAll("-", "").slice(0, 12)}`;
  const widgets = group?.widgets?.filter((w) => w.id.startsWith(prefix)) ?? [];
  return widgets.length ? { ...group, mode: "direct", widgets: widgets.map((w) => ({ ...w, type: "reading" })) } : null;
};
const table = new Map(layout(readTable()).cells.map((c) => [`${c.mod}/${c.block}`, c]));
const rows = [];
for (const [key, rec] of Object.entries(state.cells)) {
  const section = rec.group ? devicePart(rec) : rec.uplinkId ? dash.sections.get(rec.uplinkId) : null;
  const widgets = (section?.widgets ?? []).map((w) => ({ id: w.id, type: w.type, label: w.label ?? "", value: w.value, flags: judge(w) }));
  rows.push({ key, mod: rec.mod, block: rec.block, pos: rec.pos, note: rec.note, error: rec.error, kind: section?.template ?? "", title: section?.title ?? "",
    verdict: verdict(rec, section), mode: section?.mode ?? "", widgets });
}
const extra = [...dash.sections.values()].filter((s) => !Object.values(state.cells).some((r) => r.uplinkId === s.uplinkId));
writeFileSync(join(OUT, "readout.json"), JSON.stringify({ rows, extra, head: { mainframe: dash.head.mainframe, role: dash.head.role } }, null, 2));

// ---- screenshots (outside the repository) --------------------------------------------------------------------------------
if (process.argv.includes("--shots")) {
  mkdirSync(SHOTS, { recursive: true });
  const require = createRequire(join(here, "../../cloud/package.json"));
  const { chromium } = require("playwright");
  const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  await ctx.addInitScript((m) => localStorage.setItem("cccloud.mainframes.v1", JSON.stringify([m])),
    { mainframeId: state.mainframe.id, token: state.tokens.owner, name: "Gallery", deviceName: "e2e-gallery", pairedAt: Date.now(), canControl: true });
  const page = await ctx.newPage();
  await page.goto(`${ORIGIN}/#/m/${state.mainframe.id}`);
  await page.waitForSelector(".card", { timeout: 60_000 });
  const pages = dash.head.layout?.pages ?? [];
  for (const p of pages) {
    await page.locator(".tab", { hasText: p.title }).first().click();
    await page.waitForTimeout(1500);
    // cards below the fold are not drawn: one picture per screen, scrolling like a player does
    await page.mouse.move(800, 500);
    for (let i = 1, last = -1; i <= 60; i++) {
      await page.screenshot({ path: join(SHOTS, `${p.id}-${String(i).padStart(2, "0")}.png`) });
      const top = await page.evaluate(() => Math.max(...[document.scrollingElement, ...document.querySelectorAll("*")].map((e) => e?.scrollTop ?? 0)));
      if (top === last) break;
      last = top;
      await page.mouse.wheel(0, 900);
      await page.waitForTimeout(600);
    }
  }
  await browser.close();
  console.log(`screenshots: ${pages.length} pages in ${SHOTS}`);
}

// ---- log ---------------------------------------------------------------------------------------------------------------
const logFile = join(SERVER_DIR, "logs/latest.log");
const logLines = existsSync(logFile) ? readFileSync(logFile, "utf8").split("\n") : [];
const errors = logLines.filter((l) => /\/(WARN|ERROR|FATAL)\]/.test(l) && /cccloud|computercraft/i.test(l)).map((l) => l.replace(/^\[[^\]]*\]\s*/, "").slice(0, 220));
const errorCounts = new Map();
for (const l of errors) errorCounts.set(l, (errorCounts.get(l) ?? 0) + 1);

// ---- the report --------------------------------------------------------------------------------------------------------
const count = (v) => rows.filter((r) => r.verdict === v).length;
const verdicts = ["OK", "IDLE", "EMPTY", "NONSENSE", "NO_CARD", "NO_WIDGETS", "NO_SECTION", "OFFLINE", "BLOCKED", "NOT_PLACED"];
const esc = (s) => String(s).replaceAll("|", "\\|").replaceAll("\n", " ");
const md = [];
md.push("# ATM11 readout", "");
md.push(`Generated by \`tools/e2e/gallery-report.mjs\` from the machine gallery (\`tools/e2e/gallery/machines.tsv\`) on the ATM11 test server, read over the dashboard WebSocket. How to regenerate it: the header of \`tools/e2e/gallery.mjs\`.`, "");
md.push("## Summary", "", `Machines in the table: **${rows.length}**, placed: **${rows.length - count("NOT_PLACED")}**.`, "");
md.push("| Result | Machines | Meaning |", "|---|---:|---|");
const meaning = { OK: "card shown, at least one real value", IDLE: "card shown, only a status word (not formed, no plant), no reading", EMPTY: "card shown, every value zero or empty", NONSENSE: "card shown, a value a player should not read", NO_CARD: "uplink online, but no ready-made card",
  NO_WIDGETS: "card without data widgets", NO_SECTION: "the dashboard has no section for this uplink", OFFLINE: "section offline", BLOCKED: "blocked by the claim guard", NOT_PLACED: "the block could not be placed" };
for (const v of verdicts) if (count(v)) md.push(`| ${v} | ${count(v)} | ${meaning[v]} |`);
md.push("");
const bugs = rows.filter((r) => r.verdict !== "OK" && !r.note);
md.push("## Bug list", "", "Machines that are not OK and have no note explaining why. Explained ones stay in the tables below.", "");
if (!bugs.length) md.push("None.", "");
else {
  md.push("| Mod | Machine | Result | Detail |", "|---|---|---|---|");
  for (const r of bugs) {
    const detail = r.error ? r.error : r.widgets.filter((w) => w.flags.some((f) => f.startsWith("nonsense"))).map((w) => `${w.id}=${short(w.value)}`).join(", ") || r.kind;
    md.push(`| ${esc(r.mod)} | ${esc(r.block)} | ${r.verdict} | ${esc(detail)} |`);
  }
  md.push("");
}
for (const extraFile of ["claims.md", "dimensions.md"]) if (existsSync(join(OUT, extraFile))) md.push(readFileSync(join(OUT, extraFile), "utf8").trimEnd(), "");
if (existsSync(join(OUT, "claims-before.json"))) {
  const before = JSON.parse(readFileSync(join(OUT, "claims-before.json"), "utf8")).filter((r) => r.ok === false);
  md.push("### The same scenario on the jar without the leak fix", "", "Failed checks, with what the player's client received:", "");
  if (!before.length) md.push("None.", "");
  for (const r of before) md.push(`- **${r.group}: ${r.name}**: \`${r.evidence.slice(0, 400)}\``);
  md.push("");
}
md.push("## Server log", "");
if (!errorCounts.size) md.push("No WARN or ERROR line mentions CC: Cloud or CC: Tweaked.", "");
else {
  md.push("WARN and ERROR lines that mention CC: Cloud or CC: Tweaked (count, text):", "", "```");
  for (const [l, n] of [...errorCounts].sort((a, b) => b[1] - a[1]).slice(0, 40)) md.push(`${String(n).padStart(4)}  ${l}`);
  md.push("```", "");
}
const gallery = new Set(Object.values(state.cells).map((r) => r.uplinkId));
const others = [...dash.sections.values()].filter((x) => !gallery.has(x.uplinkId));
md.push("## Cards of the mainframe", "", "Sections without a machine in the table (server, claims, quests, waystones and the like).", "");
if (!others.length) md.push("None.", "");
else {
  md.push("| Card | Online | Widgets (id = value, ⚠ = zero or empty) |", "|---|---|---|");
  for (const x of others) {
    const list = (x.widgets ?? []).map((w) => `${w.id}=${short(w.value)}${judge(w).some((f) => f !== "false") ? " ⚠" : ""}`).join("; ");
    md.push(`| ${esc(x.title ?? x.uplinkId)} | ${x.online ? "yes" : "no"} | ${esc(list)} |`);
  }
  md.push("");
}
md.push("## Machines", "");
for (const mod of [...new Set(rows.map((r) => r.mod))]) {
  const own = rows.filter((r) => r.mod === mod);
  const ok = own.filter((r) => r.verdict === "OK").length;
  md.push(`### ${mod} (${ok}/${own.length} OK)`, "");
  md.push("| Machine | Card | Result | Widgets (id = value, ⚠ = zero or empty) |", "|---|---|---|---|");
  for (const r of own) {
    const list = r.widgets.map((w) => `${w.id}=${short(w.value)}${w.flags.some((f) => f !== "false") ? " ⚠" : ""}`).join("; ");
    md.push(`| ${esc(r.block.split(":")[1])} | ${esc(r.kind || r.mode || "–")} | ${r.verdict} | ${esc(list)}${r.note ? ` *(${esc(r.note)})*` : ""} |`);
  }
  md.push("");
}
writeFileSync(REPORT, md.join("\n"));
console.log(`machines ${rows.length}: ${verdicts.filter(count).map((v) => `${v} ${count(v)}`).join(", ")}`);
console.log(`report: ${REPORT}`);
dash.close();
rcon.close();
