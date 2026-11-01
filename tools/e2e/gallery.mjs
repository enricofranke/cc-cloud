#!/usr/bin/env node
// The machine gallery: one of every machine CC: Cloud has a card for, in rows by mod, each with a Cloud Uplink, fed with
// power, items and fluids, all linked to one mainframe. The machines come from the table tools/e2e/gallery/machines.tsv:
// one line per machine, so a new card is a new line (see the head of that file).
//
//   tools/e2e/gallery-server.sh start        the test server with its own world "gallery" and the dev commands
//   node tools/e2e/gallery.mjs catalog       asks the running server which blocks show a card, adds the new ones to the table
//                                            (catalog-merge: only the second half, from the last scan)
//   node tools/e2e/gallery.mjs build [mod..] clears and builds the gallery (or only these mods' rows, same positions)
//   node tools/e2e/gallery.mjs feed [mod..]  fills the machines again
//   node tools/e2e/gallery.mjs layout        dashboard pages by mod (12 at most)
//   node tools/e2e/gallery-report.mjs        reads every card, writes docs/ATM11_READOUT.md
//
// Idempotent: every run clears the cells it builds; the mainframe keeps its id. State and tokens are kept in $GALLERY_OUT
// (default <server>/gallery-out), never in the repository. Area: x >= 0, z >= 0, y -61..-50 on the superflat floor.
import { Rcon } from "./rcon.mjs";
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { homedir } from "node:os";
import { fileURLToPath } from "node:url";
import { BUILDERS } from "./gallery/builders.mjs";

const here = dirname(fileURLToPath(import.meta.url));
export const SERVER_DIR = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
export const OUT = process.env.GALLERY_OUT ?? join(SERVER_DIR, "gallery-out");
export const TABLE = join(here, "gallery", "machines.tsv");
export const STATE_FILE = join(OUT, "state.json");
const CATALOG = join(OUT, "catalog.json");
mkdirSync(OUT, { recursive: true });

export const OWNER = { uuid: "", name: "e2e_admin" }; // a scripted player (dev commands): the analyzer can stand in for it
export const Y = -60, FLOOR = Y - 1;
const STEP_X = 6, STEP_Z = 7, PER_ROW = 24;
const RELAY_STEP = 170, RELAY_Y = -50;
const MAINFRAME = [-8, Y, -8];
const CELL = "powah:energy_cell_nitro{energy_stored_main_energy:2000000000L}";
export const OW = "minecraft:overworld";

// ---------------------------------------------------------------------------------------------------------- the table
/** Reads machines.tsv: [{ mod, block, tokens: Map }]. Lines: mod, block, then tokens (see the file's head). */
export function readTable(file = TABLE) {
  const rows = [];
  for (const line of readFileSync(file, "utf8").split("\n")) {
    const text = line.trim();
    if (!text || text.startsWith("#")) continue;
    const [mod, block, ...rest] = text.split(/\s+/);
    rows.push({ mod, block, tokens: rest });
  }
  return rows;
}
const token = (row, key) => row.tokens.find((t) => t === key || t.startsWith(`${key}=`))?.slice(key.length + 1);
const has = (row, key) => row.tokens.some((t) => t === key || t.startsWith(`${key}=`));
export const noteOf = (row) => row.tokens.filter((t) => t.startsWith("note=")).map((t) => t.slice(5).replaceAll("_", " ")).join(" ");

/** Positions: rows by mod in table order, at most PER_ROW machines each. Pure (same table = same places). */
export function layout(rows) {
  const cells = [];
  let z = 0;
  for (const mod of [...new Set(rows.map((r) => r.mod))]) {
    const own = rows.filter((r) => r.mod === mod);
    for (let i = 0; i < own.length; i += PER_ROW) {
      own.slice(i, i + PER_ROW).forEach((row, n) => cells.push({ ...row, x: n * STEP_X, y: Y, z, label: i === 0 && n === 0 }));
      z += STEP_Z;
    }
  }
  return { cells, width: PER_ROW * STEP_X, depth: z };
}

// -------------------------------------------------------------------------------------------------------------- rcon
let rcon;
const log = [];
const failures = [];
const BAD = /^(Too many|Unknown|Incorrect|Could not (?!set the block)|Expected|Invalid|No (block|entity)|Unable|That position|Not a|Malformed|Can't)|<--\[HERE\]|not loaded|failed/m;
async function cmd(c, { allowFail = false, dim = null } = {}) {
  const full = dim ? `execute in ${dim} run ${c}` : c;
  if (Buffer.byteLength(full) > 1400) throw new Error(`command too long for RCON: ${full.slice(0, 120)}`);
  const out = await rcon.cmd(full);
  log.push(`> ${full}\n${out}`);
  if (!allowFail && BAD.test(out)) failures.push({ c: full, out });
  return out;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const uuidInts = (u) => {
  const hex = u.replace(/-/g, "");
  return [0, 8, 16, 24].map((i) => parseInt(hex.slice(i, i + 8), 16) | 0);
};
export const linkNbt = (mf) => `link:{pos:{dimension:"${mf.dim ?? OW}",pos:[I;${mf.pos.join(",")}]},id:[I;${uuidInts(mf.id).join(",")}]},net_v:1`;
const signText = (lines, color = "black") =>
  `{front_text:{messages:[${[0, 1, 2, 3].map((i) => JSON.stringify(lines[i] ?? "")).join(",")}],color:"${color}",has_glowing_text:1b},is_waxed:1b}`;
/** A block name split over the sign's lines (15 characters a line). */
function signLines(name) {
  const words = name.split("_");
  const lines = [""];
  for (const w of words) {
    const last = lines[lines.length - 1];
    if (last && (last + "_" + w).length > 15) lines.push(w.slice(0, 15));
    else lines[lines.length - 1] = last ? `${last}_${w}` : w.slice(0, 15);
  }
  return lines.slice(0, 4);
}

// ------------------------------------------------------------------------------------------------------------- state
export const loadState = () => (existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, "utf8")) : { cells: {} });
const saveState = (s) => writeFileSync(STATE_FILE, JSON.stringify(s, null, 2));

/** Rectangles of at most 200 chunks per forceload call. */
async function forceload(x1, z1, x2, z2, dim) {
  for (let x = x1; x <= x2; x += 160) for (let z = z1; z <= z2; z += 160) {
    await cmd(`forceload add ${x} ${z} ${Math.min(x2, x + 159)} ${Math.min(z2, z + 159)}`, { allowFail: true, dim });
  }
}

/** Fill split so that one command stays below /fill's limit. */
async function fill(x1, y1, z1, x2, y2, z2, block) {
  const per = (x2 - x1 + 1) * (y2 - y1 + 1);
  const step = Math.max(1, Math.floor(32000 / per));
  for (let z = z1; z <= z2; z += step) await cmd(`fill ${x1} ${y1} ${z} ${x2} ${y2} ${Math.min(z2, z + step - 1)} ${block}`, { allowFail: true });
}

async function ensureMainframe(state) {
  await forceload(MAINFRAME[0] - 16, MAINFRAME[2] - 16, MAINFRAME[0] + 16, MAINFRAME[2] + 16);
  OWNER.uuid = (await cmd(`cccloud dev uuid ${OWNER.name}`)).match(/uuid (\S+)/)[1];
  const [x, y, z] = MAINFRAME;
  if (state.mainframe?.id && /passed/i.test(await cmd(`execute if block ${x} ${y} ${z} cccloud:cloud_mainframe`, { allowFail: true }))) return;
  await cmd(`setblock ${x} ${y} ${z} cccloud:cloud_mainframe[half=lower,facing=south]`);
  await cmd(`setblock ${x} ${y + 1} ${z} cccloud:cloud_mainframe[half=upper,facing=south]`);
  await sleep(1000);
  const id = (await cmd(`data get block ${x} ${y} ${z} mainframe_id`)).match(/"([0-9a-f-]{36})"/)?.[1];
  if (!id) throw new Error("no mainframe id");
  await cmd(`data merge block ${x} ${y} ${z} {upgrades:{Items:[{Slot:0b,id:"cccloud:upgrade_capacity_unobtainium",count:1},{Slot:1b,id:"cccloud:upgrade_bandwidth_vibranium",count:1},{Slot:2b,id:"cccloud:upgrade_memory_unobtainium",count:1},{Slot:3b,id:"cccloud:upgrade_efficiency_allthemodium",count:1},{Slot:4b,id:"cccloud:quantum_core",count:1}]}}`);
  await cmd(`cccloud dev own ${x} ${y} ${z} ${OWNER.name}`); // access data and upgrade effects
  await cmd(`setblock ${x + 1} ${y} ${z} ${CELL}`);
  await cmd(`setblock ${x - 1} ${y} ${z} ${CELL}`);
  state.mainframe = { id, pos: MAINFRAME, dim: OW };
  saveState(state);
}

async function relays(state, width, depth) {
  // the mainframe only reaches 96 blocks, the first relay of the grid is further away
  await cmd(`setblock 40 ${RELAY_Y - 1} 40 ${CELL}`, { allowFail: true });
  await cmd(`setblock 40 ${RELAY_Y} 40 cccloud:advanced_cloud_relay`, { allowFail: true });
  for (let x = RELAY_STEP / 2; x < width + RELAY_STEP / 2; x += RELAY_STEP) {
    for (let z = RELAY_STEP / 2; z < depth + RELAY_STEP / 2; z += RELAY_STEP) {
      await cmd(`setblock ${x} ${RELAY_Y - 1} ${z} ${CELL}`, { allowFail: true });
      await cmd(`setblock ${x} ${RELAY_Y} ${z} cccloud:advanced_cloud_relay`, { allowFail: true });
    }
  }
}

function cellId(c) { return `${c.mod}/${c.block}`; }

async function placeCell(c, state) {
  const { x, y, z } = c;
  const size = Number(token(c, "size") ?? 1);
  const blockState = token(c, "state") ?? "";
  const nbt = token(c, "nbt") ?? "";
  const face = token(c, "face") ?? "up";
  const rec = { mod: c.mod, block: c.block, pos: [x, y, z], note: noteOf(c), placed: false, group: nbt.match(/group:"([^"]+)"/)?.[1] };
  const place = BUILDERS[token(c, "build")];
  if (has(c, "build") && !place) throw new Error(`unknown builder ${token(c, "build")} (${c.block})`);
  if (!has(c, "nopower")) await cmd(`setblock ${x} ${FLOOR} ${z} ${CELL}`);
  const data = nbt.replaceAll("§", " ").replace("LINK", `${linkNbt(state.mainframe)},linked_by:"${OWNER.uuid}"`);
  const out = await cmd(`setblock ${x} ${y} ${z} ${c.block}${blockState}${data}`, { allowFail: true });
  rec.placed = /Changed the block/.test(out) || /passed/i.test(await cmd(`execute if block ${x} ${y} ${z} ${c.block}`, { allowFail: true }));
  if (!rec.placed) rec.error = out.slice(0, 200);
  const ctx = { cmd, x, y, z, Y, FLOOR, cell: CELL, sleep, mainframe: state.mainframe, rec };
  if (place) await place(ctx, c);
  // the uplink: on top of the machine by default (facing = towards the machine)
  const [ux, uy, uz, facing] = {
    up: [x, y + size, z, "down"], north: [x, y, z - size, "south"], south: [x, y, z + size, "north"],
    west: [x - size, y, z, "east"], east: [x + size, y, z, "west"],
  }[face];
  const uplink = has(c, "self") ? [x, y, z] : c.uplink ?? rec.uplink ?? (has(c, "probe") ? [x, y, z + 1, "down"] : [ux, uy, uz, facing]);
  // probe: models that draw beyond their block get a Cloud Probe on the floor next to them (an uplink would sit inside the model)
  if (has(c, "probe")) await cmd(`setblock ${uplink[0]} ${uplink[1]} ${uplink[2]} cccloud:cloud_probe[facing=${uplink[3]}]{${linkNbt(state.mainframe)},linked_by:"${OWNER.uuid}",remote_target:[I;${x},${y},${z}],remote_side:"up",remote_origin:[I;${uplink.slice(0, 3).join(",")}],remote_dim:"${OW}",remote_by:"${OWNER.uuid}"}`, { allowFail: true });
  else if (!has(c, "self")) await cmd(`setblock ${uplink[0]} ${uplink[1]} ${uplink[2]} cccloud:cloud_uplink[facing=${uplink[3]}]{${linkNbt(state.mainframe)},linked_by:"${OWNER.uuid}"}`, { allowFail: true });
  rec.uplink = uplink.slice(0, 3);
  await cmd(`setblock ${x} ${y} ${z + 2} minecraft:oak_sign[rotation=8]${signText(signLines(c.block.split(":")[1]))}`, { allowFail: true });
  if (c.label) await cmd(`setblock ${x - 3} ${y} ${z} minecraft:oak_sign[rotation=4]${signText(["", c.mod.replaceAll("_", " "), "", ""], "blue")}`, { allowFail: true });
  state.cells[cellId(c)] = rec;
}

async function feedCell(c) {
  const { x, y, z } = c;
  for (const t of c.tokens) {
    const [k, ...v] = t.split("=");
    const value = v.join("=");
    if (k === "fe") await cmd(`cccloud dev fe ${x} ${y} ${z} ${value || 1000000}`, { allowFail: true });
    else if (k === "item") {
      const [id, n] = value.split("*");
      await cmd(`cccloud dev item ${x} ${y} ${z} ${id} ${n ?? 16}`, { allowFail: true });
    } else if (k === "fluid") {
      const [id, n] = value.split("*");
      await cmd(`cccloud dev fluid ${x} ${y} ${z} ${id} ${n ?? 4000}`, { allowFail: true });
    }
  }
}

async function readIds(state, cells) {
  for (const c of cells) {
    const rec = state.cells[cellId(c)];
    if (!rec?.uplink) continue;
    let m = null;
    for (const key of ["uplink_id", "section_id", "device_id"]) m ??= (await cmd(`data get block ${rec.uplink.join(" ")} ${key}`, { allowFail: true })).match(/"([0-9a-f-]{36})"/);
    rec.uplinkId = m?.[1] ?? null;
    if (!m) rec.error = rec.error ?? "no uplink id";
  }
}

// ------------------------------------------------------------------------------------------------------------ phases
async function build(mods) {
  const state = loadState();
  const table = readTable();
  const { cells, width, depth } = layout(table);
  const chosen = mods.length ? cells.filter((c) => mods.includes(c.mod)) : cells;
  if (!chosen.length) throw new Error(`no such mod in the table: ${mods.join(" ")}`);
  for (const c of ["gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule spawn_patrols false",
    "gamerule spawn_phantoms false", "gamerule spawn_wandering_traders false", "gamerule send_command_feedback true", "time set noon", "weather clear"]) {
    await cmd(c, { allowFail: true });
  }
  await ensureMainframe(state);
  await forceload(-8, -8, width + RELAY_STEP, depth + RELAY_STEP); // the outer relays stand beyond the rows
  // clear what the chosen cells cover (the whole width of their rows), then the floor and signs again
  const zs = [...new Set(chosen.map((c) => c.z))];
  console.log(`clearing ${zs.length} rows ...`);
  if (!mods.length) await fill(-4, FLOOR, -3, width + 4, RELAY_Y - 2, depth, "minecraft:air");
  else for (const z of zs) await fill(-4, FLOOR, z - 2, width + 4, RELAY_Y - 2, z + 4, "minecraft:air");
  await fill(-4, FLOOR, -3, width + 4, FLOOR, depth, "minecraft:grass_block keep");
  await cmd("kill @e[type=item]", { allowFail: true });
  await relays(state, width, depth);
  console.log(`placing ${chosen.length} machines ...`);
  let n = 0;
  for (const c of chosen) {
    await placeCell(c, state);
    if (++n % 50 === 0) { console.log(`  ${n}/${chosen.length}`); saveState(state); }
  }
  // drop state of machines that left the table
  const wanted = new Set(cells.map(cellId));
  for (const key of Object.keys(state.cells)) if (!wanted.has(key)) delete state.cells[key];
  await sleep(3000); // block entities tick and uplinks register
  await feed(chosen);
  await readIds(state, chosen);
  saveState(state);
  const missing = chosen.filter((c) => !state.cells[cellId(c)]?.placed);
  console.log(`built ${chosen.length - missing.length}/${chosen.length} machines; mainframe ${state.mainframe.id}`);
  for (const c of missing) console.log(`  NOT PLACED ${c.block}: ${state.cells[cellId(c)]?.error ?? ""}`);
}

async function feed(cells) {
  console.log(`feeding ${cells.length} machines ...`);
  for (const c of cells) await feedCell(c);
}

/** One dashboard page per mod (keeps the dashboard usable with hundreds of cards). */
/** One dashboard page per group of mods: a layout holds 12 pages at most, the gallery has more mods than that. */
async function pageLayout() {
  const { Dash } = await import("./gallery/dash.mjs");
  const state = loadState();
  const dash = await Dash.open(state.tokens?.owner, { mainframeId: state.mainframe.id });
  const byMod = new Map();
  for (const rec of Object.values(state.cells)) {
    if (!rec.uplinkId) continue;
    byMod.set(rec.mod, [...(byMod.get(rec.mod) ?? []), rec]);
  }
  const mods = [...byMod.keys()].sort();
  const total = [...byMod.values()].reduce((n, l) => n + l.length, 0);
  const perPage = Math.ceil(total / 12);
  const groups = [[]];
  let inGroup = 0;
  for (const mod of mods) {
    if (inGroup >= perPage && groups.length < 12) { groups.push([]); inGroup = 0; }
    groups.at(-1).push(mod);
    inGroup += byMod.get(mod).length;
  }
  const cards = {}, pages = [];
  groups.forEach((group, i) => {
    const id = `gallery-${i + 1}`;
    pages.push({ id, title: group.length > 1 ? `${group[0]} .. ${group.at(-1)}`.slice(0, 40) : group[0].slice(0, 40) });
    let order = 0;
    for (const mod of group) for (const rec of byMod.get(mod)) cards[rec.uplinkId] = { page: id, order: order++ };
  });
  const res = await dash.request({ t: "layout_set", layout: { v: 1, pages, cards, widgets: {} } }, "layout_result");
  console.log(JSON.stringify(res).slice(0, 300));
  dash.close();
}

/** Asks the server mod by mod. A block that hangs the server: tools/e2e/gallery/catalog-skip.txt (see e2e-catalog-progress.txt). */
async function scanCatalog() {
  await cmd("forceload add -48 -48", { allowFail: true });
  await cmd("setblock -40 -51 -40 minecraft:stone", { allowFail: true });
  const skip = readFileSync(join(here, "gallery", "catalog-skip.txt"), "utf8").split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#"));
  const names = JSON.parse((await cmd("cccloud dev namespaces")).match(/json (.*)/s)[1]);
  const blocks = existsSync(CATALOG) && process.env.CATALOG_RESUME ? JSON.parse(readFileSync(CATALOG, "utf8")).blocks : [];
  const done = new Set(blocks.map((b) => b.block.split(":")[0]));
  for (const ns of Object.keys(names)) {
    if (skip.includes(`${ns}:*`) || done.has(ns)) continue;
    const own = skip.filter((s) => s.startsWith(`${ns}:`)).map((s) => s.split(":")[1]);
    const out = await cmd(`cccloud dev catalog -40 -50 -40 ${ns} ${own.join(",") || "-"}`);
    const json = JSON.parse(out.match(/json (.*)/s)?.[1] ?? '{"blocks":[]}');
    blocks.push(...json.blocks);
    console.log(`  ${ns}: ${json.blocks.length} of ${names[ns]}`);
    writeFileSync(CATALOG, JSON.stringify({ blocks }));
    await cmd("forceload query -40 -40", { allowFail: true }); // lets the server breathe between mods
  }
}

/** What a new line starts with, by card: enough to make the card show something real. Edit the line for more. */
const KIND_FEED = {
  ironfurnaces_furnace: ["item=minecraft:raw_iron*32", "item=minecraft:coal*16"],
  generatorgalore_generator: ["item=minecraft:coal*32"],
  justdirethings_generator: ["item=minecraft:coal*32"],
  charginggadgets_station: ["item=minecraft:coal*32"],
  cookingforblockheads_oven: ["item=minecraft:coal*16", "item=minecraft:potato*8"],
  productivetrees_machine: ["item=minecraft:oak_log*32"],
  agritechevolved_planter: ["item=minecraft:wheat_seeds*4", "item=minecraft:dirt*1"],
  agritechevolved_advanced_planter: ["item=minecraft:wheat_seeds*4", "item=minecraft:dirt*1"],
  sellingbin_bin: ["item=minecraft:wheat*16"],
  farmingforblockheads_shipping_bin: ["item=minecraft:wheat*16"],
  railcraft_boiler: ["item=minecraft:coal*16", "fluid=minecraft:water*8000"],
};

/** Adds blocks the server has cards for and the table does not list yet (commented out when only a generic card fits). */
function catalog() {
  const data = JSON.parse(readFileSync(CATALOG, "utf8"));
  const text = readFileSync(TABLE, "utf8");
  const known = new Set(text.split("\n").map((l) => l.replace(/^#\s*/, "").trim().split(/\s+/)[1]).filter(Boolean));
  const fresh = data.blocks.filter((b) => !known.has(b.block));
  const lines = fresh.map((b) => {
    const mod = b.block.split(":")[0];
    const generic = b.kind === "energy" || b.kind === "tank";
    const tokens = [b.fe ? "fe" : "", ...(KIND_FEED[b.kind] ?? []), b.fluid && !b.fe ? "fluid=minecraft:water*4000" : "",
      b.sides.includes("up") ? "" : `face=${b.sides[0]}`, `kind=${b.kind}`].filter(Boolean);
    return `${generic ? "# " : ""}${mod.padEnd(24)} ${b.block.padEnd(56)} ${tokens.join(" ")}`;
  });
  if (lines.length) writeFileSync(TABLE, text.trimEnd() + "\n" + lines.join("\n") + "\n");
  console.log(`${data.blocks.length} blocks with a card, ${fresh.length} new lines in ${TABLE}`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const [phase = "build", ...args] = process.argv.slice(2);
  if (phase === "catalog-merge") {
    catalog();
  } else {
    rcon = await Rcon.connect();
    try {
      if (phase === "build") await build(args);
      else if (phase === "catalog") {
        await scanCatalog();
        catalog();
      } else if (phase === "feed") {
        const cells = layout(readTable()).cells;
        await feed(args.length ? cells.filter((c) => args.includes(c.mod)) : cells);
      } else if (phase === "ids") {
        const state = loadState();
        await readIds(state, layout(readTable()).cells);
        saveState(state);
      } else if (phase === "layout") await pageLayout();
      else throw new Error(`unknown phase ${phase}`);
    } finally {
      writeFileSync(join(OUT, "rcon.log"), log.join("\n"));
      rcon.close();
    }
    if (failures.length) {
      console.log(`${failures.length} commands failed, first ones:`);
      for (const f of failures.slice(0, 15)) console.log(`  ${f.c.slice(0, 140)}\n    -> ${f.out.slice(0, 200)}`);
    }
  }
}
