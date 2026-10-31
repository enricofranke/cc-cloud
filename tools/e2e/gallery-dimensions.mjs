#!/usr/bin/env node
// Cross-dimension rows: machines in the Nether and the End, linked to the gallery's mainframe in the overworld through
// Ender Relays, with a plain relay behind them. Checks the cards, the network map and the Cloud Analyzer in each dimension.
//   node tools/e2e/gallery-dimensions.mjs      (after gallery.mjs build) writes $GALLERY_OUT/dimensions.md
// Also a claim in the Nether: a device of the gallery's owner in a chunk Bob's party claimed there.
import { World, Checks, CELL, Y, OW, NETHER, END, OUT, sleep, linkNbt } from "./gallery/world.mjs";
import { Dash } from "./gallery/dash.mjs";
import { loadState } from "./gallery.mjs";
import { writeFileSync } from "node:fs";
import { join } from "node:path";

const state = loadState();
if (!state.mainframe) throw new Error("no gallery yet: node tools/e2e/gallery.mjs build");
const MF = state.mainframe;
const w = await World.open();
const c = new Checks("Cross-dimension rows", "The gallery's mainframe stands in the overworld; machines in the Nether and the End reach it through Ender Relays (`tools/e2e/gallery-dimensions.mjs`).");
const owner = "e2e_admin";
const ownerId = await w.uuid(owner);

/** The machines of a far row: stored FE, a burning generator and a furnace with work to do. */
const ROW = [
  ["powah:energy_cell_nitro", "{energy_stored_main_energy:777000000L}"],
  ["powah:energy_cell_hardened", "{energy_stored_main_energy:1000000L}"],
  ["ironfurnaces:iron_furnace", ""],
  ["generatorgalore:copper_generator", ""],
];
const U = {};
async function outpost(dim, key, ox, oy, oz) {
  await w.cmd(`forceload add ${ox - 16} ${oz - 16} ${ox + 40} ${oz + 16}`, { dim });
  await w.cmd(`fill ${ox - 8} ${oy - 1} ${oz - 8} ${ox + 32} ${oy + 6} ${oz + 8} minecraft:air`, { dim });
  await w.cmd(`fill ${ox - 8} ${oy - 1} ${oz - 8} ${ox + 32} ${oy - 1} ${oz + 8} minecraft:smooth_stone`, { dim });
  // Ender Relay of the mainframe's group on a charged cell, a Cloud Relay as the last hop for the machines further away
  await w.cmd(`setblock ${ox} ${oy} ${oz} ${CELL()}`, { dim });
  await w.cmd(`setblock ${ox} ${oy + 1} ${oz} cccloud:ender_relay{ender_group:"${MF.id}"}`, { dim });
  await w.cmd(`setblock ${ox + 16} ${oy} ${oz} ${CELL()}`, { dim });
  await w.cmd(`setblock ${ox + 16} ${oy + 1} ${oz} cccloud:cloud_relay`, { dim });
  for (let i = 0; i < ROW.length; i++) {
    const x = ox + 4 + i * 6, z = oz + 4;
    const [block, nbt] = ROW[i];
    await w.cmd(`setblock ${x} ${oy - 1} ${z} ${CELL()}`, { dim });
    await w.cmd(`setblock ${x} ${oy} ${z} ${block}${nbt}`, { dim });
    await w.cmd(`setblock ${x} ${oy + 1} ${z} cccloud:cloud_uplink[facing=down]{${linkNbt(MF, ownerId)}}`, { dim });
  }
  await sleep(1000);
  await w.cmd(`cccloud dev item ${ox + 16} ${oy} ${oz + 4} minecraft:raw_iron 32`, { dim });
  await w.cmd(`cccloud dev item ${ox + 16} ${oy} ${oz + 4} minecraft:coal 16`, { dim });
  await w.cmd(`cccloud dev item ${ox + 22} ${oy} ${oz + 4} minecraft:coal 16`, { dim });
  await sleep(1000);
  U[key] = [];
  for (let i = 0; i < ROW.length; i++) {
    const out = await w.cmd(`data get block ${ox + 4 + i * 6} ${oy + 1} ${oz + 4} uplink_id`, { dim });
    U[key].push(out.match(/"([0-9a-f-]{36})"/)?.[1]);
  }
}
// the gallery's end of the Ender group, next to the mainframe
const [mx, my, mz] = MF.pos;
await w.cmd(`setblock ${mx + 3} ${my} ${mz} ${CELL()}`);
await w.cmd(`setblock ${mx + 3} ${my + 1} ${mz} cccloud:ender_relay{ender_group:"${MF.id}"}`);
await outpost(NETHER, "nether", 0, 70, 0);
await outpost(END, "end", 100, 70, 0);
await sleep(8000);

const token = state.tokens?.owner;
const dash = await Dash.open(token, { mainframeId: MF.id, settle: 4000 });
const hasValue = (s) => (s?.widgets ?? []).some((x) => typeof x.value === "string" ? /[1-9]/.test(x.value) : x.value);
const widgets = (s) => (s?.widgets ?? []).map((x) => `${x.id}=${JSON.stringify(x.value)}`).join(" ").slice(0, 220);
for (const [key, dim] of [["nether", NETHER], ["end", END]]) {
  ROW.forEach(([block], i) => {
    const s = dash.sections.get(U[key][i]);
    c.check("Cards", `${key}: ${block}`, s?.online && hasValue(s), s ? `${s.template} online=${s.online} ${widgets(s)}` : "no section");
  });
}
const net = dash.inbox.filter((m) => m.t === "network").pop()?.network ?? dash.head.network;
const nodes = net?.nodes ?? [];
const byDim = {};
for (const n of nodes) byDim[n.dim] = (byDim[n.dim] ?? 0) + 1;
c.check("Network map", "nodes in all three dimensions", byDim[OW] && byDim[NETHER] && byDim[END], JSON.stringify(byDim));
const far = nodes.filter((n) => U.nether.includes(n.id) || U.end.includes(n.id));
c.check("Network map", "far devices are connected with a route", far.length === 2 * ROW.length && far.every((n) => n.status === "online" && n.hops > 0), JSON.stringify(far.map((n) => `${n.dim.split(":")[1]} ${n.status} hops=${n.hops}`)));
writeFileSync(join(OUT, "network-dimensions.json"), JSON.stringify(net, null, 2));

const list = (a, key) => (a[key] ?? []).map((n) => n.id);
const aNether = await w.json(`cccloud dev analyzer ${owner} 10 71 0`, { dim: NETHER });
const aEnd = await w.json(`cccloud dev analyzer ${owner} 110 71 0`, { dim: END });
const aOver = await w.json(`cccloud dev analyzer ${owner} ${mx} ${my + 1} ${mz + 3}`, { dim: OW });
writeFileSync(join(OUT, "analyzer-dimensions.json"), JSON.stringify({ aNether, aEnd, aOver }, null, 2));
c.check("Cloud Analyzer", "in the Nether: the row, the relays, no overworld nodes in place", U.nether.every((id) => list(aNether, "devices").includes(id)), `${list(aNether, "devices").length} devices, ${list(aNether, "nodes").length} nodes, keys ${Object.keys(aNether)}`);
c.check("Cloud Analyzer", "in the End: the row", U.end.every((id) => list(aEnd, "devices").includes(id)), `${list(aEnd, "devices").length} devices`);
c.check("Cloud Analyzer", "in the Nether: no device of the End is sent", !U.end.some((id) => list(aNether, "devices").includes(id)), "");
c.info("Cloud Analyzer", "what the HUD says about other dimensions", JSON.stringify({ elsewhere: aNether.elsewhere, gates: aNether.gates }).slice(0, 280));
c.check("Cloud Analyzer", "in the overworld: gates to the other dimensions are shown", (aOver.gates ?? []).length > 0 || (aOver.elsewhere ?? []).length > 0, JSON.stringify({ gates: aOver.gates?.length, elsewhere: aOver.elsewhere }).slice(0, 240));

// a claim in the Nether: hidden and blocked there, nothing leaks through the other dimensions' payloads
await w.cmd("cccloud dev party bob Blue");
await w.cmd(`cccloud dev claim bob 4 70 4`, { dim: NETHER });
await sleep(2500);
const blocked = dash.sections.get(U.nether[0]);
c.check("Claim in the Nether", "the machine in Bob's Nether claim is blocked, no values", blocked?.blocked && !(blocked.widgets ?? []).length, JSON.stringify({ blocked: blocked?.blocked, widgets: blocked?.widgets?.length }));
c.check("Claim in the Nether", "a machine in the unclaimed next chunk keeps working", hasValue(dash.sections.get(U.nether[3])), widgets(dash.sections.get(U.nether[3])));
const aBlocked = await w.json(`cccloud dev analyzer ${owner} 10 71 0`, { dim: NETHER });
c.check("Claim in the Nether", "the owner's analyzer in the Nether hides it", !list(aBlocked, "devices").includes(U.nether[0]), `${list(aBlocked, "devices").length} devices`);
const aFrom = await w.json(`cccloud dev analyzer ${owner} 110 71 0`, { dim: END });
c.check("Claim in the Nether", "the analyzer in the End does not carry it either", !list(aFrom, "devices").includes(U.nether[0]) && !(JSON.stringify(aFrom).includes(U.nether[0])), "");
await w.cmd(`cccloud dev unclaim bob 4 70 4`, { dim: NETHER });

dash.close();
writeFileSync(join(OUT, "dimensions.json"), JSON.stringify(c.rows, null, 2));
const failed = c.write(join(OUT, "dimensions.md"));
await w.cleanup();
console.log(`${failed} failed`);
process.exit(failed ? 1 : 0);
