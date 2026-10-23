#!/usr/bin/env node
// Talks to the real dashboard of the running ATM11 test server like the web app does (HTTP + WebSocket) and checks
// the scenario built by scenario.mjs, using the device seeded by seed-device.mjs.
//   node tools/e2e/dashboard.mjs
// Checks: security headers, deny-by-default routes, pairing (wrong code 403, seeded code -> pending), WS auth,
// snapshot (mainframe + RS and ME storage sections with the expected counts, auto Computer card, Lua card),
// item search on both networks, watchlist add/remove, craft (not_craftable / unknown_item), redstone action.
import { createRequire } from "node:module";
import { Rcon } from "./rcon.mjs";
import { readFileSync, writeFileSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { homedir, tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(here, "../../cloud/package.json"));
const WebSocket = require("ws");

const SERVER_DIR = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
const SCRATCH = process.env.SCRATCH ?? join(tmpdir(), "cccloud-e2e");
const PORT = Number(process.env.WEB_PORT ?? 8797);
const ORIGIN = `http://127.0.0.1:${PORT}`;
const device = JSON.parse(readFileSync(join(SCRATCH, "e2e-device.json"), "utf8"));
const scenario = JSON.parse(readFileSync(join(SCRATCH, "scenario.json"), "utf8"));
const LOG = join(SERVER_DIR, "logs/latest.log");
const logOffset = statSync(LOG).size;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const results = [];
function check(name, ok, detail = "") {
  results.push({ name, ok: !!ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

// ---- HTTP -----------------------------------------------------------------------------------------------------------
const index = await fetch(`${ORIGIN}/`);
check("GET / serves the app", index.status === 200 && /<html/i.test(await index.text()), `status ${index.status}`);
for (const h of ["content-security-policy", "x-content-type-options", "x-frame-options", "referrer-policy"]) {
  check(`header ${h}`, index.headers.get(h), index.headers.get(h) ?? "missing");
}
const noTok = await fetch(`${ORIGIN}/api/mainframes`);
check("GET /api/mainframes without token is rejected", noTok.status === 401 || noTok.status === 403, `status ${noTok.status}`);
const mf = await fetch(`${ORIGIN}/api/mainframes`, { headers: { Authorization: `Bearer ${device.token}` } });
const mfBody = await mf.text();
check("GET /api/mainframes with the seeded token", mf.status === 200 && mfBody.includes(device.mainframeId), `status ${mf.status} ${mfBody.slice(0, 200)}`);
const icon = await fetch(`${ORIGIN}/api/icon/minecraft/iron_ingot.png`);
check("GET /api/icon/minecraft/iron_ingot.png (dedicated server: 200 or 404 fallback)", [200, 404].includes(icon.status), `status ${icon.status} ${icon.headers.get("content-type")}`);
const icon2 = await fetch(`${ORIGIN}/api/icon/cccloud/cloud_mainframe.png`);
check("GET /api/icon/cccloud/cloud_mainframe.png (mod-jar texture)", icon2.status === 200, `status ${icon2.status} ${icon2.headers.get("content-type")}`);

const wrong = await fetch(`${ORIGIN}/api/pair`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ code: "ZZZZ-ZZZZ", name: "e2e wrong" }) });
check("POST /api/pair with a wrong code -> 403", wrong.status === 403, `status ${wrong.status}`);
const pair = await fetch(`${ORIGIN}/api/pair`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ code: device.pairingCode, name: "e2e pairing" }) });
const pairBody = await pair.json().catch(() => ({}));
if (pair.status === 403) console.log("SKIP  POST /api/pair with the seeded code — code already used by an earlier run (single use); run seed-device.mjs on the stopped server for a new one");
else check("POST /api/pair with the seeded code -> pending (202)", (pair.status === 200 || pair.status === 202) && pairBody.status === "pending" && pairBody.pairingId, `${pair.status} ${JSON.stringify(pairBody)}`);
if (pairBody.pairingId) {
  const st = await fetch(`${ORIGIN}/api/pair/${pairBody.pairingId}`);
  check("GET /api/pair/<id> stays pending (nobody can approve)", st.status === 200, `${st.status} ${(await st.text()).slice(0, 200)}`);
}

// ---- WebSocket ------------------------------------------------------------------------------------------------------
const ws = new WebSocket(`ws://127.0.0.1:${PORT}/ws/client`, { headers: { Origin: ORIGIN } });
const inbox = [];
const waiters = [];
ws.on("message", (data) => {
  const msg = JSON.parse(String(data));
  inbox.push(msg);
  for (const w of [...waiters]) if (w.pred(msg)) { waiters.splice(waiters.indexOf(w), 1); w.resolve(msg); }
  if (msg.t === "ping") ws.send(JSON.stringify({ t: "pong" }));
});
const closed = new Promise((r) => ws.on("close", (code, reason) => r({ code, reason: String(reason) })));
function next(pred, ms = 10_000, label = "message") {
  const found = inbox.find(pred);
  if (found) { inbox.splice(inbox.indexOf(found), 1); return Promise.resolve(found); }
  return new Promise((resolve, reject) => {
    const w = { pred, resolve: (m) => { clearTimeout(t); inbox.splice(inbox.indexOf(m), 1); resolve(m); } };
    const t = setTimeout(() => { waiters.splice(waiters.indexOf(w), 1); reject(new Error(`timeout waiting for ${label}`)); }, ms);
    waiters.push(w);
  });
}
await new Promise((resolve, reject) => { ws.once("open", resolve); ws.once("error", reject); });
ws.send(JSON.stringify({ t: "auth", token: device.token }));
let snapshot;
try {
  snapshot = await next((m) => m.t === "snapshot" || m.t === "error", 10_000, "snapshot");
} catch (e) {
  check("WS auth -> snapshot", false, `${e.message}; close=${JSON.stringify(await Promise.race([closed, sleep(500)]))}`);
  finish();
}
check("WS auth -> snapshot", snapshot.t === "snapshot", snapshot.t === "error" ? JSON.stringify(snapshot) : "");
// Let late uplink messages (bridges publish once a second) arrive, then fold them into the snapshot.
await sleep(3000);
const uplinks = new Map((snapshot.uplinks ?? []).map((u) => [u.uplinkId, u]));
for (const m of inbox.splice(0)) {
  if (m.t === "uplink") uplinks.set(m.uplinkId, m.uplink ?? m);
  if (m.t === "value" && uplinks.has(m.uplinkId)) {
    const w = uplinks.get(m.uplinkId).widgets?.find((x) => x.id === m.id);
    if (w) w.value = m.value;
  }
}
writeFileSync(join(SCRATCH, "dashboard-snapshot.json"), JSON.stringify({ snapshot, uplinks: [...uplinks.values()] }, null, 2));
check("mainframe online + enabled", snapshot.mainframe?.online && snapshot.mainframe?.enabled, JSON.stringify(snapshot.mainframe));

const all = [...uplinks.values()];
const rs = all.find((u) => u.bridge === "rs");
const me = all.find((u) => u.bridge === "me");
const auto = all.find((u) => u.mode === "auto");
const lua = all.find((u) => u.mode === "lua");
console.log(`sections: ${all.map((u) => `${u.title} [${u.mode ?? "?"}${u.bridge ? "/" + u.bridge : ""}${u.online ? "" : ", offline"}]`).join(", ")}`);
const wv = (u, id) => u?.widgets?.find((w) => w.id === id)?.value;
const sum = (o) => Object.values(o).reduce((a, b) => a + b, 0);
for (const [name, sec, expected] of [["RS", rs, scenario.expected.rs], ["ME", me, scenario.expected.me]]) {
  check(`${name} storage section present (source=bridge)`, sec && sec.source === "bridge" && sec.template === "storage", sec ? `${sec.title} online=${sec.online}` : "missing");
  if (!sec) continue;
  const items = wv(sec, "items"), types = wv(sec, "types");
  check(`${name} items = ${sum(expected)}`, String(items).replace(/[^0-9]/g, "") === String(sum(expected)), `items=${JSON.stringify(items)} types=${JSON.stringify(types)} storage=${JSON.stringify(wv(sec, "storage"))} energy=${JSON.stringify(wv(sec, "energy"))} usage=${JSON.stringify(wv(sec, "usage"))}`);
  check(`${name} types = ${Object.keys(expected).length}`, String(types).replace(/[^0-9]/g, "") === String(Object.keys(expected).length), `types=${JSON.stringify(types)}`);
}
check("auto Computer card present", auto && auto.widgets?.some((w) => w.type === "redstone_sides"), auto ? `${auto.title}: ${auto.widgets.map((w) => w.id).join(",")}` : "missing");
check("Lua card present (E2E Lua rs_cloud_bridge)", lua && /E2E Lua/.test(lua.title ?? ""), lua ? `${lua.title}: ${lua.widgets.map((w) => `${w.id}=${JSON.stringify(w.value)}`).join(", ")}` : "missing");

let req = 0;
// re-runs: start with empty watchlists
async function resetWatch(sec) {
  for (const e of (sec?.widgets?.find((w) => w.id === "watch")?.value ?? [])) await action(sec, "watch", { op: "remove", name: e.name });
}
async function query(sec, q) {
  const reqId = `q${++req}`;
  ws.send(JSON.stringify({ t: "query", reqId, uplinkId: sec.uplinkId, id: "search", q }));
  return next((m) => m.t === "query_result" && m.reqId === reqId, 10_000, `query_result ${q}`);
}
async function action(sec, id, value) {
  const reqId = `a${++req}`;
  ws.send(JSON.stringify({ t: "action", reqId, uplinkId: sec.uplinkId, id, value }));
  return next((m) => m.t === "action_result" && m.reqId === reqId, 15_000, `action_result ${id}`);
}

await resetWatch(rs);
await resetWatch(me);
for (const [name, sec, expected] of [["RS", rs, scenario.expected.rs], ["ME", me, scenario.expected.me]]) {
  if (!sec) continue;
  for (const [item, count] of Object.entries(expected)) {
    const q = item.split(":")[1].split("_")[0];
    const r = await query(sec, q).catch((e) => ({ error: e.message, rows: [] }));
    const row = r.rows?.find((x) => x.name === item);
    check(`${name} search "${q}" finds ${item} x${count}`, row && row.count === count, `rows=${JSON.stringify(r.rows?.slice(0, 5))}${r.error ? " error=" + r.error : ""}`);
    await sleep(250);
  }
  const all2 = await query(sec, "@minecraft");
  check(`${name} search "@minecraft" lists all ${Object.keys(expected).length} types`, all2.rows?.length === Object.keys(expected).length, `rows=${all2.rows?.map((x) => `${x.name}:${x.count}`).join(",")} ${all2.error ?? ""}`);

  // watchlist: add (below min -> warn alert), duplicate, remove
  const [watchItem, watchCount] = Object.entries(expected)[0];
  const add = await action(sec, "watch", { op: "add", name: watchItem, min: watchCount + 100 });
  check(`${name} watchlist add ${watchItem} min ${watchCount + 100}`, add.ok, JSON.stringify(add));
  const wl = await next((m) => m.t === "value" && m.uplinkId === sec.uplinkId && m.id === "watch" && Array.isArray(m.value) && m.value.some((e) => e.name === watchItem), 5000, "watch value").catch((e) => ({ error: e.message }));
  check(`${name} watchlist value delta shows the entry`, Array.isArray(wl.value) && wl.value.some((e) => e.name === watchItem && e.count === watchCount), JSON.stringify(wl.value ?? wl));
  const alert = await next((m) => m.t === "alert" && m.uplinkId === sec.uplinkId, 4000, "alert").catch((e) => ({ error: e.message }));
  check(`${name} alert when an item that is already below its minimum is added (BridgeCore.watchOp swallows the crossing)`, alert.level === "warn", JSON.stringify(alert));
  const dup = await action(sec, "watch", { op: "add", name: watchItem, min: 1 });
  check(`${name} watchlist duplicate -> already_watched`, !dup.ok && dup.error === "already_watched", JSON.stringify(dup));
  const bogus = await action(sec, "watch", { op: "add", name: "minecraft:not_an_item", min: 1 });
  check(`${name} watchlist unknown item -> unknown_item`, !bogus.ok && bogus.error === "unknown_item", JSON.stringify(bogus));

  // crafting: no patterns in the network
  const craft = await action(sec, "search", { op: "craft", name: "minecraft:iron_block", count: 1 });
  check(`${name} craft iron_block (no pattern) -> not_craftable`, !craft.ok && craft.error === "not_craftable", JSON.stringify(craft));
  const tooMany = await action(sec, "search", { op: "craft", name: "minecraft:iron_block", count: 100000 });
  check(`${name} craft 100000 -> too_many`, !tooMany.ok && tooMany.error === "too_many", JSON.stringify(tooMany));
  await sleep(300);
}
// keep one watch entry on RS for the screenshots; remove the ME one again
if (me) {
  const rm = await action(me, "watch", { op: "remove", name: Object.keys(scenario.expected.me)[0] });
  check("ME watchlist remove", rm.ok, JSON.stringify(rm));
}

// A real crossing: watch diamonds (48 stored) with min 10, then pull the 1k cell out of the ME drive with a hopper
// (count -> 0), expect a warn alert, then put the cell back.
if (me) {
  const rcon = await Rcon.connect();
  const [dx, dy, dz] = scenario.positions.aeDrive;
  try {
    const add = await action(me, "watch", { op: "add", name: "minecraft:diamond", min: 10 });
    check("ME watchlist add diamond min 10 (not below)", add.ok, JSON.stringify(add));
    await sleep(2500);
    inbox.splice(0).filter((m) => m.t === "alert").forEach((m) => console.log("  (early alert)", JSON.stringify(m)));
    await rcon.cmd(`setblock ${dx} ${dy - 1} ${dz} minecraft:hopper[facing=down]`);
    const alert = await next((m) => m.t === "alert" && m.uplinkId === me.uplinkId, 8000, "crossing alert").catch((e) => ({ error: e.message }));
    check("ME watchlist: diamonds drop below min (cell pulled) -> warn alert", alert.level === "warn" && /Diamond/.test(alert.text ?? ""), JSON.stringify(alert));
    const items = await next((m) => m.t === "value" && m.uplinkId === me.uplinkId && m.id === "items", 5000, "items value").catch((e) => ({ error: e.message }));
    check("ME items value updates to 0 without the cell", String(items.value).replace(/[^0-9]/g, "") === "0", JSON.stringify(items.value ?? items));
  } finally {
    // put the cell back: bottom hopper -> top hopper (which feeds the drive), then remove the bottom hopper
    await rcon.cmd(`item replace block ${dx} ${dy + 1} ${dz} container.0 from block ${dx} ${dy - 1} ${dz} container.0`);
    await rcon.cmd(`setblock ${dx} ${dy - 1} ${dz} minecraft:grass_block`);
    rcon.close();
  }
  const back = await next((m) => m.t === "value" && m.uplinkId === me.uplinkId && m.id === "items" && String(m.value).replace(/[^0-9]/g, "") !== "0", 8000, "items back").catch((e) => ({ error: e.message }));
  check("ME items back after reinserting the cell", String(back.value ?? "").replace(/[^0-9]/g, "") === "122", JSON.stringify(back.value ?? back));
  await action(me, "watch", { op: "remove", name: "minecraft:diamond" });
}

if (auto) {
  const rsOut = await action(auto, "redstone", { op: "set_output", side: "back", value: 15 });
  check("auto card: redstone set_output back=15", rsOut.ok, JSON.stringify(rsOut));
  const bad = await action(auto, "redstone", { op: "set_output", side: "north", value: 15 });
  check("auto card: unknown side -> invalid_value", !bad.ok && bad.error === "invalid_value", JSON.stringify(bad));
}

finish();

function finish() {
  const text = readFileSync(LOG).subarray(logOffset).toString("utf8");
  const relevant = text.split("\n").filter((l) => /cccloud|computercraft|refinedstorage|appeng|ae2/i.test(l) && /\/(WARN|ERROR)\]|\[cccloud\]/.test(l));
  console.log("\n== server log lines during the dashboard test ==");
  for (const l of relevant.slice(0, 40)) console.log(l.slice(0, 400));
  const failed = results.filter((r) => !r.ok);
  writeFileSync(join(SCRATCH, "dashboard-results.json"), JSON.stringify(results, null, 2));
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  try { ws.close(); } catch {}
  process.exit(failed.length ? 1 : 0);
}
