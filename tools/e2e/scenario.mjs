#!/usr/bin/env node
// Builds the CC: Cloud test scenario in the running ATM11 test server over RCON and checks it.
//   node tools/e2e/scenario.mjs build    place mainframe, computers, uplinks, an RS and an AE2 network + bridges, items
//   node tools/e2e/scenario.mjs status   print block states, network contents and the Lua probe results
// Area: x 96..127, z 96..111 at y -60 (flat world), force-loaded. Writes $SCRATCH/scenario.json for the dashboard test.
import { Rcon } from "./rcon.mjs";
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { homedir, tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const SERVER_DIR = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
const SCRATCH = process.env.SCRATCH ?? join(tmpdir(), "cccloud-e2e");
mkdirSync(SCRATCH, { recursive: true });
const STATE_FILE = join(SCRATCH, "scenario.json");

// The fake owner: no real player joins, so the mainframe's owner is a fixed UUID (see seed-device.mjs).
export const E2E_OWNER = { uuid: "e2e00000-0000-4000-8000-00000000c10d", name: "e2e_admin" };

const Y = -60;
// Creative FE sources in ATM11, in order of preference. They must push FE into adjacent blocks.
const POWER_CANDIDATES = ["powah:energy_cell_creative", "energizedpower:creative_battery_box", "titanium:creative_fe_generator"];
let power = null;
const P = {
  mainframe: [100, Y, 100], power: [99, Y, 100],
  autoComputer: [104, Y, 100], autoUplink: [104, Y + 1, 100],
  rsController: [110, Y, 100], rsStorage: [111, Y, 100], rsBridge: [112, Y, 100], rsInterface: [113, Y, 100], rsHopper: [113, Y + 1, 100],
  rsComputer: [112, Y, 101], rsUplink: [112, Y + 1, 101],
  aeCell: [120, Y, 100], aeDrive: [121, Y, 100], aeBridge: [122, Y, 100], aeInterface: [123, Y, 100],
  aeDriveHopper: [121, Y + 1, 100], aeHopper: [123, Y + 1, 100], aeComputer: [122, Y, 101],
};
const ids = { auto: 9001, rs: 9002, ae: 9003 };
export const EXPECTED = {
  rs: { "minecraft:iron_ingot": 64, "minecraft:gold_ingot": 32, "minecraft:cobblestone": 64 },
  me: { "minecraft:diamond": 48, "minecraft:redstone": 64, "minecraft:iron_ingot": 10 },
};

const xyz = (p) => p.join(" ");
const uuidInts = (u) => {
  const hex = u.replace(/-/g, "");
  return [0, 8, 16, 24].map((i) => (parseInt(hex.slice(i, i + 8), 16) | 0));
};
const linkNbt = (mainframeId) => `{dimension:"minecraft:overworld",pos:[I;${P.mainframe.join(",")}]}`;
const link = (mainframeId) => `link:{pos:${linkNbt(mainframeId)},id:[I;${uuidInts(mainframeId).join(",")}]}`;

const log = [];
let rcon;
async function cmd(c, { allowFail = false } = {}) {
  const out = await rcon.cmd(c);
  log.push({ c, out });
  const bad = /^(Unknown|Incorrect|Could not|Expected|Invalid|No (block|entity)|Unable|That position|Not a)|<--\[HERE\]/m.test(out);
  if (bad && !allowFail) throw new Error(`command failed: ${c}\n  -> ${out}`);
  return out;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function blockIs(pos, state) {
  return /passed/i.test(await cmd(`execute if block ${xyz(pos)} ${state}`, { allowFail: true }));
}
async function dataGet(pos, path = "") {
  return cmd(`data get block ${xyz(pos)}${path ? " " + path : ""}`, { allowFail: true });
}

function writeLua(id, kind) {
  const dir = join(SERVER_DIR, "world/computercraft/computer", String(id));
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "startup.lua"), readFileSync(join(here, "lua/probe.lua"), "utf8").replace("__KIND__", kind));
}

async function build() {
  await cmd("gamerule sendCommandFeedback true", { allowFail: true });
  await cmd(`forceload add 96 96 127 111`, { allowFail: true });
  await cmd(`fill 96 ${Y} 96 127 ${Y + 6} 111 minecraft:air`, { allowFail: true }); // "No blocks were filled" on a fresh area

  // Mainframe (two halves) + owner
  await cmd(`setblock ${xyz(P.mainframe)} cccloud:cloud_mainframe[half=lower,facing=south]`);
  await cmd(`setblock ${P.mainframe[0]} ${Y + 1} ${P.mainframe[2]} cccloud:cloud_mainframe[half=upper,facing=south]`);
  await sleep(500);
  if (!(await blockIs(P.mainframe, "cccloud:cloud_mainframe[half=lower]"))) throw new Error("mainframe lower half missing after setblock");
  const idOut = await dataGet(P.mainframe, "mainframe_id");
  const mainframeId = idOut.match(/"([0-9a-f-]{36})"/)?.[1];
  if (!mainframeId) throw new Error(`no mainframe_id: ${idOut}`);
  await cmd(`data merge block ${xyz(P.mainframe)} {owner:"${E2E_OWNER.uuid}",owner_name:"${E2E_OWNER.name}"}`);

  // Power (v0.3+: the mainframe needs FE). Try the creative FE sources that ship with ATM11, next to the lower half.
  power = null;
  for (const block of POWER_CANDIDATES) {
    const out = await cmd(`setblock ${xyz(P.power)} ${block}`, { allowFail: true });
    if (/Changed the block/.test(out)) { power = block; break; }
  }
  console.log(`power source: ${power ?? "none placed"}`);

  // Lua probes (written before the computers boot)
  writeLua(ids.rs, "rs_cloud_bridge");
  writeLua(ids.ae, "me_cloud_bridge");

  // Computer with an uplink and no program -> automatic Computer card
  await cmd(`setblock ${xyz(P.autoComputer)} computercraft:computer_advanced[facing=south]{ComputerId:${ids.auto},On:1b,Label:"E2E Auto"}`);
  await cmd(`setblock ${xyz(P.autoUplink)} cccloud:cloud_uplink[facing=down]{${link(mainframeId)}}`);

  // Refined Storage: creative controller + 1k storage block + RS Cloud Bridge + interface fed by a hopper
  await cmd(`setblock ${xyz(P.rsController)} refinedstorage:creative_controller`);
  await cmd(`setblock ${xyz(P.rsStorage)} refinedstorage:1k_storage_block`);
  await cmd(`setblock ${xyz(P.rsBridge)} cccloud:rs_cloud_bridge[facing=south]{${link(mainframeId)}}`);
  await cmd(`setblock ${xyz(P.rsInterface)} refinedstorage:interface`);
  await cmd(`setblock ${xyz(P.rsComputer)} computercraft:computer_advanced[facing=south]{ComputerId:${ids.rs},On:1b,Label:"E2E RS Lua"}`);
  await cmd(`setblock ${xyz(P.rsUplink)} cccloud:cloud_uplink[facing=down]{${link(mainframeId)}}`);

  // AE2: creative energy cell + drive (cell via hopper) + ME Cloud Bridge + interface fed by a hopper (ad-hoc grid)
  await cmd(`setblock ${xyz(P.aeCell)} ae2:creative_energy_cell`);
  await cmd(`setblock ${xyz(P.aeDrive)} ae2:drive`);
  await cmd(`setblock ${xyz(P.aeBridge)} cccloud:me_cloud_bridge[facing=south]{${link(mainframeId)}}`);
  await cmd(`setblock ${xyz(P.aeInterface)} ae2:interface`);
  await cmd(`setblock ${xyz(P.aeComputer)} computercraft:computer_advanced[facing=south]{ComputerId:${ids.ae},On:1b,Label:"E2E ME Lua"}`);
  await cmd(`setblock ${xyz(P.aeDriveHopper)} minecraft:hopper[facing=down]`);
  await cmd(`item replace block ${xyz(P.aeDriveHopper)} container.0 with ae2:item_storage_cell_1k 1`);
  await sleep(2000); // the cell must be in the drive before items arrive

  await cmd(`setblock ${xyz(P.rsHopper)} minecraft:hopper[facing=down]`);
  await cmd(`setblock ${xyz(P.aeHopper)} minecraft:hopper[facing=down]`);
  let slot = 0;
  for (const [item, n] of Object.entries(EXPECTED.rs)) await cmd(`item replace block ${xyz(P.rsHopper)} container.${slot++} with ${item} ${n}`);
  slot = 0;
  for (const [item, n] of Object.entries(EXPECTED.me)) await cmd(`item replace block ${xyz(P.aeHopper)} container.${slot++} with ${item} ${n}`);

  const state = { mainframeId, power, positions: P, computerIds: ids, owner: E2E_OWNER, expected: EXPECTED, builtAt: new Date().toISOString() };
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
  console.log(`built; mainframe ${mainframeId}`);

  // Hoppers move 2.5 items/s: wait until both are empty (max 3 min).
  for (let i = 0; i < 90; i++) {
    const rsLeft = await dataGet(P.rsHopper, "Items");
    const aeLeft = await dataGet(P.aeHopper, "Items");
    const empty = (o) => /\[\]/.test(o) || /has the following block data: \[\]$/.test(o.trim());
    if (empty(rsLeft) && empty(aeLeft)) { console.log(`hoppers empty after ~${i * 2}s`); break; }
    if (i === 89) console.log(`hoppers NOT empty after 180s:\n  RS: ${rsLeft}\n  AE: ${aeLeft}`);
    await sleep(2000);
  }
}

async function status() {
  const state = JSON.parse(readFileSync(STATE_FILE, "utf8"));
  const out = { mainframeId: state.mainframeId, blocks: {}, data: {}, lua: {} };
  for (const [name, pos, block] of [
    ["mainframe", P.mainframe, "cccloud:cloud_mainframe"], ["autoUplink", P.autoUplink, "cccloud:cloud_uplink"],
    ["rsUplink", P.rsUplink, "cccloud:cloud_uplink"], ["rsBridge", P.rsBridge, "cccloud:rs_cloud_bridge"], ["meBridge", P.aeBridge, "cccloud:me_cloud_bridge"],
  ]) {
    let s = "?";
    for (const st of ["online", "connecting", "offline"]) if (await blockIs(pos, `${block}[status=${st}]`)) { s = st; break; }
    if (s === "?" && !(await blockIs(pos, block))) s = "MISSING";
    out.blocks[name] = s;
  }
  out.data.rsHopper = await dataGet(P.rsHopper, "Items");
  out.data.aeHopper = await dataGet(P.aeHopper, "Items");
  out.data.aeDriveHopper = await dataGet(P.aeDriveHopper, "Items");
  out.data.mainframe = await dataGet(P.mainframe);
  out.data.power = await dataGet(P.power);
  out.data.rsBridge = await dataGet(P.rsBridge);
  out.data.meBridge = await dataGet(P.aeBridge);
  out.data.autoUplink = await dataGet(P.autoUplink);
  for (const [k, id] of Object.entries(ids)) {
    const f = join(SERVER_DIR, "world/computercraft/computer", String(id), "e2e_result.json");
    out.lua[k] = existsSync(f) ? (() => { try { return JSON.parse(readFileSync(f, "utf8")); } catch { return readFileSync(f, "utf8"); } })() : null;
  }
  writeFileSync(join(SCRATCH, "scenario-status.json"), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out.blocks));
  for (const k of ["rs", "ae"]) {
    const l = out.lua[k];
    if (!l || typeof l !== "object") { console.log(`lua ${k}: no result`); continue; }
    console.log(`lua ${k}: connected=${JSON.stringify(l.connected)} storage=${JSON.stringify(l.storage)} energy=${JSON.stringify(l.energy)}/${JSON.stringify(l.maxEnergy)} usage=${JSON.stringify(l.energyUsage)}`);
    console.log(`  items=${JSON.stringify(l.items)}`);
    console.log(`  findIron=${JSON.stringify(l.findIron)} isCraftable(iron_block)=${JSON.stringify(l.isCraftableIronBlock)} craft=${JSON.stringify(l.craftIronBlock)} craftUnknown=${JSON.stringify(l.craftUnknown)} jobs=${JSON.stringify(l.jobs)}`);
    console.log(`  setup=${JSON.stringify(l.setup)} online=${JSON.stringify(l.online)}`);
  }
  console.log(`details: ${join(SCRATCH, "scenario-status.json")}`);
}

rcon = await Rcon.connect();
try {
  const phase = process.argv[2] ?? "build";
  if (phase === "build") { await build(); await sleep(8000); await status(); }
  else if (phase === "status") await status();
  else throw new Error(`unknown phase ${phase}`);
} finally {
  writeFileSync(join(SCRATCH, "scenario-rcon.log"), log.map((l) => `> ${l.c}\n${l.out}`).join("\n"));
  rcon.close();
}
