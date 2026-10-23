#!/usr/bin/env node
// Soak test: runs the ATM11 test server for hours under a realistic CC: Cloud load and looks for leaks.
//   node tools/e2e/soak.mjs [--hours 6] [--clients 20] [--baseline-min 10] [--skip-build | --jar path] [--fresh-scenario]
//   node tools/e2e/soak.mjs summary <out dir>       recompute the summary of an earlier run
//
// Load while it runs:
//   - N simulated dashboard clients (WebSocket + HTTP, each with its own device token and its own forwarded client
//     address, so the per-client limits apply as for real browsers): item searches every ~5 s, actions every ~10 s
//     (redstone on the auto Computer card, the Lua toggle), a map tile every ~4 s, a voxel section every ~3 s;
//   - a Lua computer (lua/soak.lua) that updates three widgets every game tick;
//   - a rule that fires every minute (sets the Lua toggle and sends an info alert).
// The first --baseline-min minutes run without clients (MSPT baseline of the pack itself).
// Every minute samples.csv gets: TPS/MSPT (/neoforge tps polled every 5 s: median/p95/max of the 100-tick means; spark
// answers RCON asynchronously, so its output cannot be read back), heap (jcmd GC.heap_info) and heap after GC
// (GC log), threads, open file descriptors, RSS/CPU, Lua ticks, rule runs and per-kind request stats of the clients.
// At the end summary.json + summary.md hold the trends (heap-after-GC slope, thread/FD growth, MSPT drift).
//
// Steps: build cccloud from HEAD in a throwaway worktree (unless --skip-build/--jar) -> copy it into the server's mods
// -> start -> build the scenario if needed + place the soak computer -> stop -> seed the client devices -> trust
// 127.0.0.1 as proxy (the config is restored afterwards) -> start with a GC log -> soak -> stop -> summary.
// Output: $SCRATCH/soak-<timestamp>/ (tokens are written there with mode 600, never into the repo).
import { createRequire } from "node:module";
import { execFile, spawnSync } from "node:child_process";
import { readFileSync, writeFileSync, appendFileSync, existsSync, mkdirSync, copyFileSync, readdirSync, rmSync, statSync, openSync, readSync, closeSync } from "node:fs";
import { join, dirname, basename } from "node:path";
import { homedir, tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { createHash, randomBytes } from "node:crypto";
import http from "node:http";
import { Rcon } from "./rcon.mjs";
import { readNbtFile, writeNbtFile, S, L, B, C } from "./nbt.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const REPO = join(here, "../..");
const require = createRequire(join(REPO, "cloud/package.json"));
const SERVER_DIR = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
const SCRATCH = process.env.SCRATCH ?? join(tmpdir(), "cccloud-e2e");
const PORT = Number(process.env.WEB_PORT ?? 8797);
const ORIGIN = `http://127.0.0.1:${PORT}`;
const JAVA_HOME_BIN = dirname(process.env.JAVA ?? "/opt/homebrew/opt/openjdk@25/bin/java");
const PID_FILE = join(SERVER_DIR, "e2e-server.pid");
const LOG = join(SERVER_DIR, "logs/latest.log");
const CFG = join(SERVER_DIR, "config/cccloud-server.toml");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const jitter = (base, spread) => base + (Math.random() * 2 - 1) * spread;
const now = () => Date.now();
const ts = () => new Date().toISOString();

// ---- options --------------------------------------------------------------------------------------------------------
const argv = process.argv.slice(2);
if (argv[0] === "summary") {
  const s = summarize(argv[1]);
  console.log(readFileSync(join(argv[1], "summary.md"), "utf8"));
  process.exit(s.verdict === "ok" ? 0 : 1);
}
const opt = (name, def) => { const i = argv.indexOf(`--${name}`); return i >= 0 ? argv[i + 1] : def; };
const flag = (name) => argv.includes(`--${name}`);
const HOURS = Number(opt("hours", 6));
const CLIENTS = Number(opt("clients", 20));
const BASELINE_MIN = Number(opt("baseline-min", 10));
const SAMPLE_MS = Number(opt("sample-sec", 60)) * 1000;
const OUT = opt("out", join(SCRATCH, `soak-${new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19)}`));
mkdirSync(OUT, { recursive: true });
const RUN_LOG = join(OUT, "soak.log");
function log(...a) {
  const line = `[${ts()}] ${a.join(" ")}`;
  console.log(line);
  appendFileSync(RUN_LOG, line + "\n");
}

const Y = -60;
const SOAK = { computer: [106, Y, 100], uplink: [106, Y + 1, 100], id: 9004 };

function sh(cmd, args, { env, timeout = 0, quiet = false } = {}) {
  const r = spawnSync(cmd, args, { env: { ...process.env, ...env }, encoding: "utf8", timeout, maxBuffer: 64 * 1024 * 1024 });
  if (!quiet) { if (r.stdout) appendFileSync(RUN_LOG, r.stdout); if (r.stderr) appendFileSync(RUN_LOG, r.stderr); }
  if (r.status !== 0) throw new Error(`${basename(cmd)} ${args.join(" ")} failed (${r.status ?? r.signal}): ${(r.stderr || r.stdout || "").slice(-2000)}`);
  return r.stdout;
}
function run(cmd, args, timeout = 30_000) {
  return new Promise((resolve) => execFile(cmd, args, { timeout, maxBuffer: 32 * 1024 * 1024 }, (err, stdout) => resolve(err ? null : stdout)));
}
const serverPid = () => { try { const p = Number(readFileSync(PID_FILE, "utf8")); process.kill(p, 0); return p; } catch { return null; } };

// ---- 1. build + install ---------------------------------------------------------------------------------------------
function buildJar() {
  if (opt("jar")) return opt("jar");
  if (flag("skip-build")) return null;
  const wt = join(SCRATCH, "soak-cccloud-head");
  if (existsSync(wt)) sh("git", ["-C", REPO, "worktree", "remove", "--force", wt]);
  sh("git", ["-C", REPO, "worktree", "add", "--detach", wt, "HEAD"]);
  const rev = sh("git", ["-C", wt, "rev-parse", "--short", "HEAD"], { quiet: true }).trim();
  log(`building cccloud ${rev} in a throwaway worktree (./gradlew build --max-workers=3)`);
  const r = spawnSync("./gradlew", ["build", "--max-workers=3"], { cwd: wt, encoding: "utf8", maxBuffer: 256 * 1024 * 1024 });
  writeFileSync(join(OUT, "build.log"), (r.stdout ?? "") + (r.stderr ?? ""));
  if (r.status !== 0) throw new Error(`build failed, see ${join(OUT, "build.log")}`);
  const libs = join(wt, "build/libs");
  const jar = readdirSync(libs).filter((f) => /^cccloud-.*\.jar$/.test(f) && !/sources|javadoc/.test(f))
    .map((f) => join(libs, f)).sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)[0];
  const keep = join(OUT, basename(jar));
  copyFileSync(jar, keep);
  sh("git", ["-C", REPO, "worktree", "remove", "--force", wt]);
  writeFileSync(join(OUT, "build-rev.txt"), rev + "\n");
  return keep;
}
function installJar(jar) {
  if (!jar) { log("using the cccloud jar already in the server's mods"); return; }
  for (const f of readdirSync(join(SERVER_DIR, "mods"))) if (/^cccloud-.*\.jar$/.test(f)) rmSync(join(SERVER_DIR, "mods", f));
  copyFileSync(jar, join(SERVER_DIR, "mods", basename(jar)));
  log(`installed ${basename(jar)}`);
}

// ---- 2. server control ----------------------------------------------------------------------------------------------
function startServer(env = {}) { sh(join(here, "start-server.sh"), [], { env }); }
function stopServer() { try { sh(join(here, "stop-server.sh"), []); } catch (e) { log(`stop: ${e.message}`); } }

const uuidInts = (u) => { const hex = u.replace(/-/g, ""); return [0, 8, 16, 24].map((i) => (parseInt(hex.slice(i, i + 8), 16) | 0)); };

async function prepareWorld() {
  const scenarioFile = join(SCRATCH, "scenario.json");
  if (flag("fresh-scenario") || !existsSync(scenarioFile)) {
    log("building the e2e scenario (scenario.mjs build)");
    sh("node", [join(here, "scenario.mjs"), "build"], { env: { SCRATCH, SERVER_DIR } });
  }
  const scenario = JSON.parse(readFileSync(scenarioFile, "utf8"));
  const mf = scenario.positions.mainframe;
  const dir = join(SERVER_DIR, "world/computercraft/computer", String(SOAK.id));
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "startup.lua"), readFileSync(join(here, "lua/soak.lua"), "utf8"));
  const link = `link:{pos:{dimension:"minecraft:overworld",pos:[I;${mf.join(",")}]},id:[I;${uuidInts(scenario.mainframeId).join(",")}]}`;
  const rcon = await Rcon.connect();
  try {
    for (const c of [
      `setblock ${SOAK.uplink.join(" ")} minecraft:air`,
      `setblock ${SOAK.computer.join(" ")} minecraft:air`,
      `setblock ${SOAK.computer.join(" ")} computercraft:computer_advanced[facing=south]{ComputerId:${SOAK.id},On:1b,Label:"Soak Lua"}`,
      `setblock ${SOAK.uplink.join(" ")} cccloud:cloud_uplink[facing=down]{${link}}`,
      // Bandwidth upgrade, top tier: 64 browsers per mainframe (the base allows 4).
      `data modify block ${mf.join(" ")} upgrades.Items set value [{Slot:1b,id:"cccloud:upgrade_bandwidth_unobtainium",count:1}]`,
    ]) {
      const out = await rcon.cmd(c);
      if (/Unknown|Incorrect|Could not|Invalid|<--\[HERE\]/.test(out) && !c.endsWith("minecraft:air")) throw new Error(`${c} -> ${out}`);
    }
    await sleep(5000);
  } finally { rcon.close(); }
  log(`soak computer ${SOAK.id} placed at ${SOAK.computer.join(" ")} (mainframe ${scenario.mainframeId})`);
  return scenario;
}

function seedDevices(scenario, n) {
  const file = join(SERVER_DIR, "world/data/cccloud/web.dat");
  mkdirSync(dirname(file), { recursive: true });
  const doc = existsSync(file) ? readNbtFile(file) : { name: "", root: C({ data: C({}), DataVersion: { type: "int", value: 4790 } }) };
  if (existsSync(file)) copyFileSync(file, `${file}.soak-backup`);
  const data = doc.root.value.data.value;
  const devices = data.devices?.value ?? { elementType: "compound", items: [] };
  devices.elementType = "compound";
  devices.items = devices.items.filter((d) => !/^soak-\d+$/.test(d.name?.value ?? ""));
  const tokens = [];
  const t = now();
  for (let i = 0; i < n; i++) {
    const token = randomBytes(32).toString("base64url");
    const name = `soak-${String(i + 1).padStart(2, "0")}`;
    devices.items.push({
      hash: S(createHash("sha256").update(token, "utf8").digest("hex")), mainframe: S(scenario.mainframeId), name: S(name),
      can_control: B(true), created: L(t), last_used: L(t), player: S(scenario.owner.uuid), player_name: S(scenario.owner.name),
    });
    tokens.push({ name, token });
  }
  data.devices = { type: "list", value: devices };
  data.acls = data.acls ?? C({});
  data.acls.value[scenario.mainframeId] = C({ owner: S(scenario.owner.uuid), trusted: { type: "list", value: { elementType: "string", items: [] } } });
  data.access = data.access ?? C({});
  data.access.value[scenario.mainframeId] = C({ owner: S(scenario.owner.uuid), owner_name: S(scenario.owner.name) });
  writeNbtFile(file, doc);
  writeFileSync(join(OUT, "devices.json"), JSON.stringify(tokens, null, 2), { mode: 0o600 });
  log(`seeded ${n} soak devices`);
  return tokens;
}

// All clients connect from 127.0.0.1; as a trusted proxy each client counts by its own X-Forwarded-For address.
let cfgBackup = null;
function trustLocalProxy() {
  cfgBackup = readFileSync(CFG, "utf8");
  writeFileSync(join(OUT, "cccloud-server.toml.orig"), cfgBackup);
  const next = cfgBackup.replace(/^(\s*webTrustedProxies\s*=\s*).*$/m, `$1["127.0.0.1"]`);
  if (next === cfgBackup && !/webTrustedProxies\s*=\s*\["127\.0\.0\.1"\]/.test(cfgBackup)) throw new Error("webTrustedProxies not found in the config");
  writeFileSync(CFG, next);
}
function restoreConfig() { if (cfgBackup != null) { writeFileSync(CFG, cfgBackup); cfgBackup = null; log("config restored"); } }

// ---- 3. clients -----------------------------------------------------------------------------------------------------
const KINDS = ["search", "action", "tile", "voxel", "api", "rule"];
const stats = { minute: newWindow(), total: newWindow(), connected: 0, reconnects: 0, msgsIn: 0, valuesIn: 0, alertsIn: 0, ruleRuns: 0, ruleLast: null };
function newWindow() { return Object.fromEntries(KINDS.map((k) => [k, { n: 0, err: 0, limited: 0, lat: [], status: {} }])); }
function record(kind, ms, status, ok) {
  for (const w of [stats.minute, stats.total]) {
    const s = w[kind];
    s.n++;
    s.status[status] = (s.status[status] ?? 0) + 1;
    if (status === 429 || status === "rate_limited") s.limited++;
    else if (!ok) s.err++;
    if (w === stats.minute) s.lat.push(ms);
    else if (s.lat.length < 200_000) s.lat.push(ms);
  }
}
const pct = (arr, p) => { if (!arr.length) return ""; const a = [...arr].sort((x, y) => x - y); return a[Math.min(a.length - 1, Math.floor(p * a.length))].toFixed(1); };

const SEARCH_TERMS = ["iron", "gold", "dia", "red", "cobble", "@minecraft", "ingot", "x"];
class Client {
  constructor(i, token, scenario) {
    this.i = i; this.token = token; this.scenario = scenario;
    this.ip = `10.77.0.${i + 1}`;
    this.agent = new http.Agent({ keepAlive: true, maxSockets: 1 });
    this.req = 0; this.pending = new Map(); this.timers = []; this.closed = false; this.sections = {};
  }
  get(path) {
    return new Promise((resolve) => {
      const t0 = performance.now();
      const r = http.get(`${ORIGIN}${path}`, { agent: this.agent, timeout: 30_000,
        headers: { Authorization: `Bearer ${this.token}`, "X-Forwarded-For": this.ip } }, (res) => {
        let size = 0;
        res.on("data", (d) => { size += d.length; });
        res.on("end", () => resolve({ status: res.statusCode, ms: performance.now() - t0, size }));
      });
      r.on("timeout", () => r.destroy(new Error("timeout")));
      r.on("error", (e) => resolve({ status: e.message === "timeout" ? "timeout" : "neterr", ms: performance.now() - t0, size: 0 }));
    });
  }
  async http(kind, path) {
    const r = await this.get(path);
    // 204 = no data for that tile, 403 = outside the allowed area or X-ray guard: expected answers, not errors.
    record(kind, r.ms, r.status, typeof r.status === "number" && r.status < 500 && r.status !== 401);
  }
  connect() {
    if (this.closed) return;
    const WebSocket = require("ws");
    const ws = new WebSocket(`ws://127.0.0.1:${PORT}/ws/client`, { headers: { Origin: ORIGIN, "X-Forwarded-For": this.ip } });
    this.ws = ws;
    ws.on("open", () => ws.send(JSON.stringify({ t: "auth", token: this.token })));
    ws.on("message", (data) => this.onMessage(JSON.parse(String(data))));
    ws.on("error", () => {});
    ws.on("close", () => {
      if (this.authed) stats.connected--;
      this.authed = false;
      for (const [, p] of this.pending) p.resolve({ ok: false, error: "closed" });
      this.pending.clear();
      if (!this.closed) { stats.reconnects++; setTimeout(() => this.connect(), 5000); }
    });
  }
  onMessage(m) {
    stats.msgsIn++;
    if (m.t === "ping") { this.ws.send(JSON.stringify({ t: "pong" })); return; }
    if (m.t === "snapshot") {
      if (!this.authed) stats.connected++;
      this.authed = true;
      for (const u of m.uplinks ?? []) this.learn(u);
      if (this.i === 0 && m.rules) this.onRules(m.rules);
      this.ready?.();
      return;
    }
    if (m.t === "uplink") { this.learn(m.uplink ?? m); return; }
    if (m.t === "value") { stats.valuesIn++; return; }
    if (m.t === "alert") { stats.alertsIn++; return; }
    if (m.t === "rules" && this.i === 0) { this.onRules(m); return; }
    if (m.reqId && this.pending.has(m.reqId)) { const p = this.pending.get(m.reqId); this.pending.delete(m.reqId); p.resolve(m); }
  }
  onRules(doc) {
    const r = (doc.rules ?? []).find((x) => x.name === "soak every minute");
    if (r) { stats.ruleRuns = r.runs ?? stats.ruleRuns; stats.ruleLast = r.lastResult ?? stats.ruleLast; this.ruleId = r.id; }
  }
  learn(u) {
    if (!u?.uplinkId) return;
    if (u.bridge === "rs") this.sections.rs = u.uplinkId;
    else if (u.bridge === "me") this.sections.me = u.uplinkId;
    else if (u.mode === "auto") this.sections.auto = u.uplinkId;
    else if (u.mode === "lua" && /Soak Lua/.test(u.title ?? "")) this.sections.soak = u.uplinkId;
  }
  send(msg, kind) {
    if (!this.authed) return Promise.resolve(null);
    const reqId = `c${this.i}r${++this.req}`;
    const t0 = performance.now();
    return new Promise((resolve) => {
      const timer = setTimeout(() => { this.pending.delete(reqId); resolve({ ok: false, error: "timeout" }); }, 30_000);
      this.pending.set(reqId, { resolve: (m) => { clearTimeout(timer); resolve(m); } });
      this.ws.send(JSON.stringify({ ...msg, reqId }));
    }).then((m) => {
      const err = m.t === "query_result" ? m.error : (m.ok ? null : m.error);
      record(kind, performance.now() - t0, err ?? "ok", !err || err === "rate_limited");
      return m;
    });
  }
  every(baseMs, spreadMs, fn) {
    const loop = async () => {
      if (this.closed) return;
      try { await fn(); } catch (e) { /* counted where it happened */ }
      this.timers.push(setTimeout(loop, jitter(baseMs, spreadMs)));
    };
    this.timers.push(setTimeout(loop, Math.random() * baseMs));
  }
  start() {
    if (!this.ws) this.connect();
    const s = this.sections;
    this.every(5000, 2000, async () => {
      const id = Math.random() < 0.5 ? s.rs : s.me;
      if (id) await this.send({ t: "query", uplinkId: id, id: "search", q: SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)] }, "search");
    });
    let flip = false;
    this.every(10_000, 3000, async () => {
      flip = !flip;
      if (flip && s.auto) await this.send({ t: "action", uplinkId: s.auto, id: "redstone", value: { op: "set_output", side: "back", value: Math.random() < 0.5 ? 0 : 15 } }, "action");
      else if (s.soak) {
        const value = Math.random() < 0.5 ? { id: "flag", value: Math.random() < 0.5 } : { id: "level", value: Math.floor(Math.random() * 101) };
        await this.send({ t: "action", uplinkId: s.soak, ...value }, "action");
      }
    });
    const mf = this.scenario.mainframeId;
    this.every(4000, 1000, async () => {
      const tx = Math.floor(Math.random() * 5) - 2, tz = Math.floor(Math.random() * 5) - 2;
      const layer = Math.random() < 0.25 ? `slice/${[-60, -58, -50][Math.floor(Math.random() * 3)]}` : "surface";
      await this.http("tile", `/api/mainframes/${mf}/map/minecraft:overworld/${layer}/${tx}/${tz}.png`);
    });
    this.every(3000, 1000, async () => {
      const cx = 5 + Math.floor(Math.random() * 4), cy = -4 + Math.floor(Math.random() * 2), cz = 5 + Math.floor(Math.random() * 4);
      await this.http("voxel", `/api/mainframes/${mf}/voxels/minecraft:overworld/${cx}/${cy}/${cz}`);
    });
    this.every(60_000, 10_000, () => this.http("api", "/api/mainframes"));
  }
  stop() {
    this.closed = true;
    for (const t of this.timers) clearTimeout(t);
    try { this.ws?.close(); } catch {}
    this.agent.destroy();
  }
}

async function setupRule(c) {
  const s = c.sections;
  if (!s.soak) throw new Error("the Soak Lua card is missing from the snapshot");
  // remove rules of earlier soak runs
  if (c.ruleId) log(`removed the earlier soak rule: ${JSON.stringify(await c.send({ t: "rule_delete", id: c.ruleId }, "rule"))}`);
  const res = await c.send({ t: "rule_set", rule: {
    name: "soak every minute", enabled: true, cooldown: 30,
    when: { type: "interval", minutes: 1 },
    then: [{ type: "widget", uplinkId: s.soak, widgetId: "flag", value: true }, { type: "alert", text: "soak rule tick", level: "info" }],
  } }, "rule");
  if (!res?.ok) throw new Error(`rule_set failed: ${JSON.stringify(res)}`);
  log(`rule created (${res.id})`);
}

// ---- 4. sampling ----------------------------------------------------------------------------------------------------
const CSV_COLS = ["time", "minute", "phase", "tps_1m", "mspt_med_1m", "mspt_p95_1m", "mspt_max_1m", "nf_mean_ms", "nf_tps", "ow_mean_ms",
  "heap_used_mb", "heap_committed_mb", "heap_after_gc_min_mb", "gc_pauses", "gc_pause_ms", "threads", "fds", "rss_mb", "cpu_pct",
  "lua_ticks_per_min", "lua_errors", "lua_actions", "rule_runs", "rule_last", "clients", "reconnects", "msgs_in", "values_in", "alerts_in",
  ...KINDS.flatMap((k) => [`${k}_n`, `${k}_err`, `${k}_429`, `${k}_p50`, `${k}_p95`]), "log_warn", "log_error"];

let gcOffset = 0;
function readGc(file) {
  if (!existsSync(file)) return [];
  const size = statSync(file).size;
  if (size <= gcOffset) return [];
  const fd = openSync(file, "r");
  const buf = Buffer.alloc(size - gcOffset);
  readSync(fd, buf, 0, buf.length, gcOffset);
  closeSync(fd);
  const text = buf.toString("utf8");
  const lastNl = text.lastIndexOf("\n");
  if (lastNl < 0) return [];
  gcOffset += Buffer.byteLength(text.slice(0, lastNl + 1));
  const events = [];
  for (const line of text.slice(0, lastNl).split("\n")) {
    // the JVM writes numbers in the server's locale ("0,902s", "7,759ms")
    const m = line.match(/\[([\d.,]+)s\].*GC\((\d+)\) (Pause .*?) (\d+)M->(\d+)M\((\d+)M\) ([\d.,]+)ms/);
    const num = (v) => Number(v.replace(",", "."));
    if (m) events.push({ uptime: num(m[1]), id: Number(m[2]), type: m[3].trim().replace(/[,"]/g, ""), before: Number(m[4]), after: Number(m[5]), total: Number(m[6]), ms: num(m[7]) });
  }
  return events;
}

let logOffset = 0;
const logLines = { warn: 0, error: 0, samples: [] };
function readServerLog() {
  if (!existsSync(LOG)) return { warn: 0, error: 0 };
  const size = statSync(LOG).size;
  if (size < logOffset) logOffset = 0;
  const fd = openSync(LOG, "r");
  const buf = Buffer.alloc(size - logOffset);
  readSync(fd, buf, 0, buf.length, logOffset);
  closeSync(fd);
  logOffset = size;
  let warn = 0, error = 0;
  for (const l of buf.toString("utf8").split("\n")) {
    if (!/cccloud/i.test(l)) continue;
    if (/\/WARN\]/.test(l)) warn++;
    else if (/\/ERROR\]/.test(l)) error++;
    else continue;
    const key = l.replace(/^\[[^\]]*\]\s*/, "").replace(/\d+/g, "#").slice(0, 200);
    if (!logLines.samples.some((s) => s.key === key) && logLines.samples.length < 40) logLines.samples.push({ key, line: l.slice(0, 400) });
  }
  logLines.warn += warn; logLines.error += error;
  return { warn, error };
}

const strip = (s) => (s ?? "").replace(/§./g, "");
// "Overall: 20,000 TPS (0,730 ms/tick)" (the server's locale may use decimal commas)
function parseNeoTps(out) {
  const num = (v) => Number(v.replace(",", "."));
  const t = strip(out);
  const all = t.match(/Overall:\s*([\d.,]+) TPS \(([\d.,]+) ms\/tick\)/);
  const ow = t.match(/Overworld:\s*([\d.,]+) TPS \(([\d.,]+) ms\/tick\)/);
  return all ? { tps: num(all[1]), ms: num(all[2]), ow: ow ? num(ow[2]) : null } : null;
}
async function sample(ctx, phase) {
  const pid = serverPid();
  const row = { time: ts(), minute: Math.round((now() - ctx.t0) / 60000), phase };
  if (!pid) return { ...row, dead: true };
  const tick = ctx.ticks.splice(0);
  if (tick.length) {
    const ms = tick.map((x) => x.ms).sort((a, b) => a - b);
    row.mspt_med_1m = ms[Math.floor(ms.length / 2)]; row.mspt_p95_1m = ms[Math.min(ms.length - 1, Math.floor(ms.length * 0.95))]; row.mspt_max_1m = ms.at(-1);
    row.nf_mean_ms = +(ms.reduce((a, b) => a + b, 0) / ms.length).toFixed(3);
    row.nf_tps = +(tick.reduce((a, x) => a + x.tps, 0) / tick.length).toFixed(2);
    row.tps_1m = Math.min(...tick.map((x) => x.tps));
    const ow = tick.map((x) => x.ow).filter((x) => x != null);
    row.ow_mean_ms = ow.length ? +(ow.reduce((a, b) => a + b, 0) / ow.length).toFixed(3) : null;
  }
  const heap = await run(join(JAVA_HOME_BIN, "jcmd"), [String(pid), "GC.heap_info"]);
  const hm = heap?.match(/total (?:reserved )?\d+K, committed (\d+)K, used (\d+)K/) ?? heap?.match(/total (\d+)K, used (\d+)K/);
  if (hm) { row.heap_committed_mb = Math.round(Number(hm[1]) / 1024); row.heap_used_mb = Math.round(Number(hm[2]) / 1024); }
  const threads = await run("ps", ["-M", "-p", String(pid)]);
  if (threads) row.threads = threads.trim().split("\n").length - 1;
  const fds = await run("lsof", ["-n", "-P", "-p", String(pid), "-Ff"], 60_000);
  if (fds) row.fds = fds.split("\n").filter((l) => /^f\d+/.test(l)).length;
  const ps = await run("ps", ["-o", "rss=,%cpu=", "-p", String(pid)]);
  if (ps) { const [rss, cpu] = ps.trim().split(/\s+/).map((v) => Number(v.replace(",", "."))); row.rss_mb = Math.round(rss / 1024); row.cpu_pct = cpu; }
  const gc = readGc(ctx.gcLog);
  for (const e of gc) appendFileSync(join(OUT, "gc-events.csv"), `${e.uptime},${e.id},"${e.type}",${e.before},${e.after},${e.total},${e.ms}\n`);
  if (gc.length) { row.heap_after_gc_min_mb = Math.min(...gc.map((e) => e.after)); row.gc_pauses = gc.length; row.gc_pause_ms = gc.reduce((a, e) => a + e.ms, 0).toFixed(1); }
  else { row.gc_pauses = 0; row.gc_pause_ms = 0; }
  try {
    const lua = JSON.parse(readFileSync(join(SERVER_DIR, "world/computercraft/computer", String(SOAK.id), "soak_status.json"), "utf8"));
    if (ctx.lastLua && lua.started === ctx.lastLua.started) row.lua_ticks_per_min = Math.round((lua.tick - ctx.lastLua.tick) / ((lua.time - ctx.lastLua.time) / 60000));
    row.lua_errors = lua.errors; row.lua_actions = lua.actions;
    ctx.lastLua = lua;
  } catch {}
  Object.assign(row, { rule_runs: stats.ruleRuns, rule_last: stats.ruleLast ?? "", clients: stats.connected, reconnects: stats.reconnects,
    msgs_in: stats.msgsIn, values_in: stats.valuesIn, alerts_in: stats.alertsIn });
  stats.msgsIn = 0; stats.valuesIn = 0; stats.alertsIn = 0;
  for (const k of KINDS) {
    const s = stats.minute[k];
    Object.assign(row, { [`${k}_n`]: s.n, [`${k}_err`]: s.err, [`${k}_429`]: s.limited, [`${k}_p50`]: pct(s.lat, 0.5), [`${k}_p95`]: pct(s.lat, 0.95) });
  }
  stats.minute = newWindow();
  const l = readServerLog();
  row.log_warn = l.warn; row.log_error = l.error;
  return row;
}

// ---- 5. summary -----------------------------------------------------------------------------------------------------
function parseCsv(file) {
  const lines = readFileSync(file, "utf8").trim().split("\n");
  const head = lines[0].split(",");
  return lines.slice(1).map((l) => Object.fromEntries(l.split(",").map((v, i) => [head[i], v === "" ? null : (isNaN(Number(v)) ? v : Number(v))])));
}
function regress(points) { // [[xHours, y]] -> slope per hour, r2
  const p = points.filter(([, y]) => y != null && Number.isFinite(y));
  if (p.length < 3) return { slope: null, r2: null, n: p.length };
  const n = p.length, mx = p.reduce((a, [x]) => a + x, 0) / n, my = p.reduce((a, [, y]) => a + y, 0) / n;
  let sxy = 0, sxx = 0, syy = 0;
  for (const [x, y] of p) { sxy += (x - mx) * (y - my); sxx += (x - mx) ** 2; syy += (y - my) ** 2; }
  const slope = sxx ? sxy / sxx : 0;
  return { slope: +slope.toFixed(3), r2: syy ? +((sxy * sxy) / (sxx * syy)).toFixed(3) : 1, n };
}
const avg = (a) => { const v = a.filter((x) => x != null && Number.isFinite(x)); return v.length ? +(v.reduce((x, y) => x + y, 0) / v.length).toFixed(2) : null; };
const max = (a) => { const v = a.filter((x) => x != null && Number.isFinite(x)); return v.length ? Math.max(...v) : null; };
const min = (a) => { const v = a.filter((x) => x != null && Number.isFinite(x)); return v.length ? Math.min(...v) : null; };

function summarize(dir) {
  const rows = parseCsv(join(dir, "samples.csv"));
  const load = rows.filter((r) => r.phase === "load");
  const base = rows.filter((r) => r.phase === "baseline");
  const meta = existsSync(join(dir, "meta.json")) ? JSON.parse(readFileSync(join(dir, "meta.json"), "utf8")) : {};
  const t0 = load[0]?.minute ?? 0;
  const hours = (r) => (r.minute - t0) / 60;
  const warm = load.filter((r) => r.minute - t0 >= 15); // JIT, caches and the tile/voxel caches settle first
  const lastHour = load.filter((r) => r.minute >= (load.at(-1)?.minute ?? 0) - 60);
  const firstHour = warm.filter((r) => r.minute - t0 < 75);

  // Heap after GC: the floor per 30-minute window (minimum after-GC value), regressed over time.
  const gcFile = join(dir, "gc-events.csv");
  const gc = existsSync(gcFile) ? readFileSync(gcFile, "utf8").trim().split("\n").filter(Boolean).map((l) => { const [u, , type, b, a, t, ms] = l.split(","); return { uptime: +u, type, after: +a, total: +t, ms: +ms }; }) : [];
  const loadStartUptime = meta.loadStartUptimeSec ?? 0;
  const gcLoad = gc.filter((e) => e.uptime >= loadStartUptime + 15 * 60);
  const windows = new Map();
  for (const e of gcLoad) { const w = Math.floor((e.uptime - loadStartUptime) / 1800); windows.set(w, Math.min(windows.get(w) ?? Infinity, e.after)); }
  const floors = [...windows.entries()].sort((a, b) => a[0] - b[0]).map(([w, v]) => [w * 0.5 + 0.25, v]);
  const heapTrend = regress(floors);
  const minuteFloors = regress(warm.map((r) => [hours(r), r.heap_after_gc_min_mb]));
  const threadTrend = regress(warm.map((r) => [hours(r), r.threads]));
  const fdTrend = regress(warm.map((r) => [hours(r), r.fds]));
  const msptTrend = regress(warm.map((r) => [hours(r), r.mspt_med_1m]));
  const rssTrend = regress(warm.map((r) => [hours(r), r.rss_mb]));
  const pauses = gcLoad.map((e) => e.ms).sort((a, b) => a - b);

  const findings = [];
  const floorRise = floors.length >= 2 ? floors.at(-1)[1] - floors[0][1] : 0;
  if (heapTrend.slope != null && heapTrend.slope > 64 && (heapTrend.r2 ?? 0) > 0.6 && floorRise > 256) findings.push(`heap after GC grows ${heapTrend.slope} MB/h (r² ${heapTrend.r2}, +${floorRise} MB)`);
  const thrFirst = max(firstHour.map((r) => r.threads)), thrLast = max(lastHour.map((r) => r.threads));
  if (thrFirst != null && thrLast != null && thrLast - thrFirst > 10 && (threadTrend.slope ?? 0) > 2) findings.push(`threads grow ${thrFirst} -> ${thrLast} (${threadTrend.slope}/h)`);
  const fdFirst = max(firstHour.map((r) => r.fds)), fdLast = max(lastHour.map((r) => r.fds));
  if (fdFirst != null && fdLast != null && fdLast - fdFirst > 20 && (fdTrend.slope ?? 0) > 3) findings.push(`open files grow ${fdFirst} -> ${fdLast} (${fdTrend.slope}/h)`);
  const msptFirst = avg(firstHour.map((r) => r.mspt_med_1m)), msptLast = avg(lastHour.map((r) => r.mspt_med_1m));
  if (msptFirst != null && msptLast != null && msptLast > msptFirst * 1.5 && msptLast - msptFirst > 2) findings.push(`MSPT drifts ${msptFirst} -> ${msptLast} ms`);
  const luaLow = warm.filter((r) => r.lua_ticks_per_min != null && r.lua_ticks_per_min < 1000).length;
  if (luaLow > warm.length * 0.1) findings.push(`the Lua computer missed ticks in ${luaLow} of ${warm.length} minutes`);
  const dead = rows.some((r) => r.phase === "dead");
  if (dead) findings.push("the server process died during the run");
  const totals = Object.fromEntries(KINDS.map((k) => [k, { n: load.reduce((a, r) => a + (r[`${k}_n`] ?? 0), 0), err: load.reduce((a, r) => a + (r[`${k}_err`] ?? 0), 0), limited: load.reduce((a, r) => a + (r[`${k}_429`] ?? 0), 0), p95_median: avg(load.map((r) => r[`${k}_p95`])) }]));
  for (const k of KINDS) if (totals[k].n && totals[k].err / totals[k].n > 0.01) findings.push(`${k} errors ${totals[k].err}/${totals[k].n}`);
  const ruleRuns = max(load.map((r) => r.rule_runs)) ?? 0;
  const expectedRuns = load.length ? load.at(-1).minute - load[0].minute - 1 : 0;
  if (load.length > 5 && ruleRuns < expectedRuns * 0.9) findings.push(`rule ran ${ruleRuns} times, expected about ${expectedRuns}`);

  const s = {
    verdict: findings.length ? "check" : "ok", findings, meta,
    duration: { baselineMin: base.length, loadMin: load.length },
    mspt: { baselineMed: avg(base.map((r) => r.mspt_med_1m)), loadMed: avg(load.map((r) => r.mspt_med_1m)), firstHourMed: msptFirst, lastHourMed: msptLast,
      loadP95: avg(load.map((r) => r.mspt_p95_1m)), loadMax: max(load.map((r) => r.mspt_max_1m)), trendPerHour: msptTrend,
      tpsMin1m: min(load.map((r) => r.tps_1m)), nfMeanBaseline: avg(base.map((r) => r.nf_mean_ms)), nfMeanLoad: avg(load.map((r) => r.nf_mean_ms)),
      owBaseline: avg(base.map((r) => r.ow_mean_ms)), owLoad: avg(load.map((r) => r.ow_mean_ms)) },
    heap: { afterGcFloorsPer30Min: floors, floorSlopeMbPerHour: heapTrend, minuteFloorSlope: minuteFloors, usedMax: max(load.map((r) => r.heap_used_mb)),
      committedMax: max(load.map((r) => r.heap_committed_mb)), gcPauses: pauses.length, pauseP50: pauses.length ? pauses[Math.floor(pauses.length / 2)] : null,
      pauseP99: pauses.length ? pauses[Math.floor(pauses.length * 0.99)] : null, pauseMax: pauses.at(-1) ?? null },
    threads: { first: min(warm.map((r) => r.threads)), firstHourMax: thrFirst, lastHourMax: thrLast, max: max(load.map((r) => r.threads)), trend: threadTrend },
    fds: { firstHourMax: fdFirst, lastHourMax: fdLast, max: max(load.map((r) => r.fds)), trend: fdTrend },
    rss: { first: min(warm.map((r) => r.rss_mb)), last: load.at(-1)?.rss_mb ?? null, trend: rssTrend },
    lua: { ticksPerMinMedian: avg(warm.map((r) => r.lua_ticks_per_min)), minutesBelow1000: luaLow, errors: max(load.map((r) => r.lua_errors)), actions: max(load.map((r) => r.lua_actions)) },
    rules: { runs: ruleRuns, last: load.at(-1)?.rule_last ?? null },
    clients: { min: min(warm.map((r) => r.clients)), reconnects: max(load.map((r) => r.reconnects)), totals, valuesPerMin: avg(load.map((r) => r.values_in)) },
    log: { warn: load.reduce((a, r) => a + (r.log_warn ?? 0), 0), error: load.reduce((a, r) => a + (r.log_error ?? 0), 0) },
  };
  writeFileSync(join(dir, "summary.json"), JSON.stringify(s, null, 2));
  const f = (v, u = "") => (v == null ? "-" : `${v}${u}`);
  const md = [
    `# Soak summary (${s.verdict === "ok" ? "no leak signs" : "CHECK"})`,
    "",
    `cccloud ${f(meta.rev)} (${f(meta.jar)}), ${f(meta.clients)} clients, ${s.duration.loadMin} min under load after ${s.duration.baselineMin} min baseline, started ${f(meta.started)}.`,
    "",
    ...(findings.length ? ["Findings:", ...findings.map((x) => `- ${x}`), ""] : ["Findings: none.", ""]),
    "| metric | value |", "|---|---|",
    `| MSPT median baseline / load | ${f(s.mspt.baselineMed)} / ${f(s.mspt.loadMed)} ms |`,
    `| MSPT median first / last hour | ${f(msptFirst)} / ${f(msptLast)} ms (trend ${f(msptTrend.slope)} ms/h) |`,
    `| MSPT p95 (avg) / max | ${f(s.mspt.loadP95)} / ${f(s.mspt.loadMax)} ms |`,
    `| mean tick baseline / load (all dims) | ${f(s.mspt.nfMeanBaseline)} / ${f(s.mspt.nfMeanLoad)} ms |`,
    `| overworld mean tick baseline / load | ${f(s.mspt.owBaseline)} / ${f(s.mspt.owLoad)} ms |`,
    `| lowest 1-min TPS | ${f(s.mspt.tpsMin1m)} |`,
    `| heap after GC, 30-min floors | ${floors.map(([h, v]) => `${h}h:${v}`).join(" ") || "-"} MB |`,
    `| heap-after-GC slope | ${f(heapTrend.slope)} MB/h (r² ${f(heapTrend.r2)}); per-minute floors ${f(minuteFloors.slope)} MB/h |`,
    `| heap used max / committed max | ${f(s.heap.usedMax)} / ${f(s.heap.committedMax)} MB |`,
    `| GC pauses (n, p50, p99, max) | ${s.heap.gcPauses}, ${f(s.heap.pauseP50)}, ${f(s.heap.pauseP99)}, ${f(s.heap.pauseMax)} ms |`,
    `| threads first-hour max / last-hour max | ${f(thrFirst)} / ${f(thrLast)} (trend ${f(threadTrend.slope)}/h) |`,
    `| open files first-hour max / last-hour max | ${f(fdFirst)} / ${f(fdLast)} (trend ${f(fdTrend.slope)}/h) |`,
    `| RSS first / last | ${f(s.rss.first)} / ${f(s.rss.last)} MB (trend ${f(rssTrend.slope)} MB/h) |`,
    `| Lua ticks/min (median), minutes < 1000, errors | ${f(s.lua.ticksPerMinMedian)}, ${luaLow}, ${f(s.lua.errors)} |`,
    `| rule runs / last result | ${ruleRuns} / ${f(s.rules.last)} |`,
    `| clients connected (min) / reconnects | ${f(s.clients.min)} / ${f(s.clients.reconnects)} |`,
    `| value messages per minute (all clients) | ${f(s.clients.valuesPerMin)} |`,
    ...KINDS.map((k) => `| ${k}: requests / errors / 429 / p95 | ${totals[k].n} / ${totals[k].err} / ${totals[k].limited} / ${f(totals[k].p95_median)} ms |`),
    `| cccloud log WARN / ERROR lines | ${s.log.warn} / ${s.log.error} |`,
    "",
    `Data: ${join(dir, "samples.csv")}, ${gcFile}.`,
  ].join("\n");
  writeFileSync(join(dir, "summary.md"), md + "\n");
  return s;
}

// ---- main -----------------------------------------------------------------------------------------------------------
async function main() {
  if (serverPid()) throw new Error("the test server is already running; stop it first (tools/e2e/stop-server.sh)");
  const meta = { started: ts(), hours: HOURS, clients: CLIENTS, baselineMin: BASELINE_MIN, out: OUT };
  const jar = buildJar();
  meta.jar = jar ? basename(jar) : readdirSync(join(SERVER_DIR, "mods")).find((f) => /^cccloud-.*\.jar$/.test(f));
  meta.rev = existsSync(join(OUT, "build-rev.txt")) ? readFileSync(join(OUT, "build-rev.txt"), "utf8").trim() : null;
  installJar(jar);

  log("starting the server to prepare the world");
  startServer();
  let scenario;
  try { scenario = await prepareWorld(); } finally { stopServer(); }
  const tokens = seedDevices(scenario, CLIENTS);
  trustLocalProxy();

  const gcLog = join(OUT, "gc.log");
  log("starting the server for the soak (GC log on)");
  startServer({ JAVA_TOOL_OPTIONS: `-Xlog:gc:file=${gcLog}:time,uptime:filecount=0` });
  const ctx = { t0: now(), gcLog, rcon: await Rcon.connect(), ticks: [] };
  // MSPT/TPS: /neoforge tps every 5 s (each answer is the mean of the last 100 ticks)
  const tpsTimer = setInterval(async () => {
    try { const r = parseNeoTps(await ctx.rcon.cmd("neoforge tps")); if (r) ctx.ticks.push(r); }
    catch { try { ctx.rcon.close(); } catch {} ctx.rcon = await Rcon.connect().catch(() => ctx.rcon); }
  }, 5000);
  writeFileSync(join(OUT, "samples.csv"), CSV_COLS.join(",") + "\n");
  writeFileSync(join(OUT, "gc-events.csv"), "");
  logOffset = existsSync(LOG) ? statSync(LOG).size : 0;
  const clients = tokens.map((t, i) => new Client(i, t.token, scenario));
  let stopping = false;
  const onSignal = () => { if (!stopping) { log("signal: ending the soak early"); stopping = true; } };
  process.on("SIGINT", onSignal); process.on("SIGTERM", onSignal);

  const writeRow = (row) => {
    appendFileSync(join(OUT, "samples.csv"), CSV_COLS.map((c) => (row[c] ?? "")).map(String).map((v) => v.replace(/,/g, ";")).join(",") + "\n");
  };
  const end = ctx.t0 + (BASELINE_MIN + HOURS * 60) * 60_000;
  let phase = "baseline";
  try {
    while (!stopping && now() < end) {
      const next = ctx.t0 + (Math.floor((now() - ctx.t0) / SAMPLE_MS) + 1) * SAMPLE_MS;
      while (!stopping && now() < next) await sleep(Math.min(1000, next - now()));
      if (phase === "baseline" && now() - ctx.t0 >= BASELINE_MIN * 60_000) {
        log(`baseline done; connecting ${CLIENTS} clients`);
        const first = clients[0];
        await new Promise((resolve, reject) => { first.ready = resolve; first.connect(); setTimeout(() => reject(new Error("client 0 got no snapshot")), 30_000); });
        await sleep(3000);
        log(`sections: ${JSON.stringify(first.sections)}`);
        await setupRule(first);
        stats.reconnects = 0;
        for (const c of clients) { c.start(); await sleep(200); }
        phase = "load";
        const up = readGc(gcLog); // drain baseline GC events into the CSV and note the JVM uptime at load start
        for (const e of up) appendFileSync(join(OUT, "gc-events.csv"), `${e.uptime},${e.id},"${e.type}",${e.before},${e.after},${e.total},${e.ms}\n`);
        const jcmdUp = await run(join(JAVA_HOME_BIN, "jcmd"), [String(serverPid()), "VM.uptime"]);
        meta.loadStartUptimeSec = Number(jcmdUp?.match(/([\d.]+) s/)?.[1] ?? 0);
        meta.loadStarted = ts();
        writeFileSync(join(OUT, "meta.json"), JSON.stringify(meta, null, 2));
        continue;
      }
      const row = await sample(ctx, phase);
      if (row.dead) { writeRow({ ...row, phase: "dead" }); log("server process is gone, ending"); break; }
      writeRow(row);
      if (row.minute % 15 === 0) log(`min ${row.minute} ${phase}: mspt med ${row.mspt_med_1m ?? "?"} heap ${row.heap_used_mb ?? "?"}/${row.heap_after_gc_min_mb ?? "-"} MB threads ${row.threads} fds ${row.fds} lua ${row.lua_ticks_per_min ?? "?"}/min rules ${row.rule_runs} clients ${row.clients}`);
    }
  } finally {
    clearInterval(tpsTimer);
    for (const c of clients) c.stop();
    try { ctx.rcon.close(); } catch {}
    meta.ended = ts();
    writeFileSync(join(OUT, "meta.json"), JSON.stringify(meta, null, 2));
    writeFileSync(join(OUT, "log-samples.txt"), logLines.samples.map((s) => s.line).join("\n") + "\n");
    log("stopping the server");
    stopServer();
    restoreConfig();
  }
  const s = summarize(OUT);
  console.log("\n" + readFileSync(join(OUT, "summary.md"), "utf8"));
  return s;
}

main().then((s) => process.exit(s.verdict === "ok" ? 0 : 1), (e) => {
  log(`FAILED: ${e.stack ?? e}`);
  try { if (serverPid()) stopServer(); } catch {}
  restoreConfig();
  process.exit(2);
});
