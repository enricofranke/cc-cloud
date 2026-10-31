#!/usr/bin/env node
// Claims and protection on the real ATM11 server with real FTB Chunks and FTB Teams, scripted players (dev commands).
//   node tools/e2e/gallery-claims.mjs          builds the scene, runs every case, writes $GALLERY_OUT/claims.md and .json
// Players: alice (party Red, mainframe MA), bob (party Blue, mainframe MB, claims the zone), carol (party Green, ally of
// Blue, mainframe MC), dave (no party, viewer on MA). Zone: three chunks by three at x 2080..2127, z 2048..2095; everything
// else around it stays unclaimed. Machines are charged Powah cells, so a card that shows anything shows a number.
// Exit code 1 when a check fails.
import { World, Checks, CELL, Y, OW, OUT, sleep, linkNbt, readPng } from "./gallery/world.mjs";
import { Dash, ORIGIN } from "./gallery/dash.mjs";
import { writeFileSync } from "node:fs";
import { join } from "node:path";

const w = await World.open();
const c = new Checks("Claims and protection", "Real FTB Chunks and FTB Teams on the ATM11 server, scripted players, read over the same dashboard, tablet, display and analyzer code a player uses (`tools/e2e/gallery-claims.mjs`).");
const X = 2080, Z = 2048; // north-west corner of the claim
const CLAIM = [[X, Z], [X + 16, Z], [X + 32, Z], [X, Z + 16], [X + 16, Z + 16], [X + 32, Z + 16], [X, Z + 32], [X + 16, Z + 32], [X + 32, Z + 32]];
const inClaim = (x, z) => x >= X && x < X + 48 && z >= Z && z < Z + 48;

// ---- players and teams ---------------------------------------------------------------------------------------------
const P = {};
for (const name of ["alice", "bob", "carol", "dave"]) P[name] = { name, uuid: await w.uuid(name) };
await w.cmd("cccloud dev party alice Red");
await w.cmd("cccloud dev party bob Blue");
await w.cmd("cccloud dev party carol Green");
await w.cmd("cccloud dev ally bob carol false").catch(() => {}); // from the last run
await w.cmd("cccloud dev ally bob alice false").catch(() => {});

// ---- the scene (unclaimed for now: every device may read) -------------------------------------------------------------
for (const [x, z] of CLAIM) await w.cmd(`cccloud dev unclaim bob ${x} ${Y} ${z}`).catch(() => {});
await w.cmd(`forceload add ${X - 48} ${Z - 16} ${X + 64} ${Z + 64}`);
for (let z = Z - 16; z <= Z + 64; z += 30) await w.cmd(`fill ${X - 48} ${Y} ${z} ${X + 64} ${Y + 8} ${Math.min(Z + 64, z + 29)} minecraft:air`); // a fill takes 32768 blocks at most
await sleep(1000);
const MA = await w.mainframe([X - 24, Y, Z + 8], P.alice, "Alice MA");
const MC = await w.mainframe([X - 24, Y, Z + 28], P.carol, "Carol MC");
const MB = await w.mainframe([X + 20, Y, Z + 4], P.bob, "Bob MB");
await w.cmd(`cccloud dev role ${MA.id} dave viewer`);

/** Powah cell at pos with an uplink on top, linked to mf by `by`. Returns the uplink's id. */
async function cardOn(pos, mf, by, fill) {
  const [x, y, z] = pos;
  await w.cmd(`setblock ${x} ${y} ${z} ${CELL(fill)}`);
  await w.cmd(`setblock ${x} ${y + 1} ${z} cccloud:cloud_uplink[facing=down]{${linkNbt(mf, by.uuid)}}`);
  await sleep(300);
  return (await w.cmd(`data get block ${x} ${y + 1} ${z} uplink_id`)).match(/"([0-9a-f-]{36})"/)?.[1];
}
const POS = { alice: [X + 16, Y, Z + 12], bob: [X + 20, Y, Z + 12], carol: [X + 24, Y, Z + 12], aliceOut: [X - 20, Y, Z + 12], probeMachine: [X + 2, Y, Z + 20] };
const U = {
  alice: await cardOn(POS.alice, MA, P.alice, 111_000_000),
  bob: await cardOn(POS.bob, MB, P.bob, 222_000_000),
  carol: await cardOn(POS.carol, MC, P.carol, 333_000_000),
  aliceOut: await cardOn(POS.aliceOut, MA, P.alice, 444_000_000),
};
// Alice's Cloud Probe just outside the claim reads a cell inside it.
const [px, py, pz] = POS.probeMachine;
await w.cmd(`setblock ${px} ${py} ${pz} ${CELL(555_000_000)}`);
await w.cmd(`setblock ${X - 4} ${Y} ${pz} minecraft:stone`);
await w.cmd(`setblock ${X - 3} ${Y} ${pz} cccloud:cloud_probe[facing=west,direct=true]{${linkNbt(MA, P.alice.uuid)},remote_target:[I;${px},${py},${pz}],remote_side:"up",remote_origin:[I;${X - 3},${Y},${pz}],remote_dim:"minecraft:overworld",remote_by:"${P.alice.uuid}"}`);
await sleep(300);
U.probe = (await w.cmd(`data get block ${X - 3} ${Y} ${pz} uplink_id`)).match(/"([0-9a-f-]{36})"/)?.[1];
// Alice's Cloud Switch in the zone, an ME Cloud Bridge on a powered network in the zone, a computer with an uplink in the zone.
const sw = [X + 16, Y, Z + 20];
await w.cmd(`setblock ${sw[0]} ${Y - 1} ${sw[2]} minecraft:stone`);
await w.cmd(`setblock ${sw[0]} ${Y} ${sw[2]} cccloud:cloud_switch[facing=down,powered=false]{${linkNbt(MA, P.alice.uuid)},name:"Alice switch",group:"Zone"}`);
const bridge = [X + 20, Y, Z + 20];
await w.cmd(`setblock ${bridge[0] + 1} ${Y} ${bridge[2]} ae2:creative_energy_cell`);
await w.cmd(`setblock ${bridge[0]} ${Y} ${bridge[2]} cccloud:me_cloud_bridge[facing=south]{${linkNbt(MA, P.alice.uuid)}}`);
await sleep(300);
U.bridge = (await w.cmd(`data get block ${bridge[0]} ${Y} ${bridge[2]} section_id`)).match(/"([0-9a-f-]{36})"/)?.[1];
const comp = [X + 24, Y, Z + 20];
await w.cmd(`setblock ${comp[0]} ${Y} ${comp[2]} computercraft:computer_advanced[facing=south]{ComputerId:9101,On:1b,Label:"Alice zone"}`);
await w.cmd(`setblock ${comp[0]} ${Y + 1} ${comp[2]} cccloud:cloud_uplink[facing=down]{${linkNbt(MA, P.alice.uuid)}}`);
await sleep(300);
U.computer = (await w.cmd(`data get block ${comp[0]} ${Y + 1} ${comp[2]} uplink_id`)).match(/"([0-9a-f-]{36})"/)?.[1];
// Bob's secret room in the zone: blocks only a slice map or the voxel view could show.
await w.cmd(`fill ${X + 30} ${Y} ${Z + 30} ${X + 34} ${Y + 3} ${Z + 34} minecraft:gold_block outline`);
// Cloud Displays in unclaimed land: Alice's shows the card in the zone, Carol's the ally's card, and a display of Alice's free card.
async function display(pos, mf, section, by) {
  await w.cmd(`setblock ${pos.join(" ")} cccloud:cloud_display[facing=south]{mainframe:"${mf.id}",mainframe_name:"x",section:"${section}",configured_by:"${by.uuid}",interactive:0b,width:1,height:1,index_x:0,index_y:0}`);
}
const D = { blocked: [X - 16, Y + 1, Z + 4], ally: [X - 14, Y + 1, Z + 4], free: [X - 12, Y + 1, Z + 4] };
await display(D.blocked, MA, U.alice, P.alice);
await display(D.ally, MC, U.carol, P.carol);
await display(D.free, MA, U.aliceOut, P.alice);

const T = {
  alice: await w.token(MA.id, "alice"), bob: await w.token(MB.id, "bob"), carol: await w.token(MC.id, "carol"), dave: await w.token(MA.id, "dave"),
};
const allFine = Object.values(T).every(Boolean) && Object.values(U).every(Boolean);
c.check("Setup", "tokens and devices exist", allFine, JSON.stringify(Object.fromEntries(Object.entries(U).map(([k, v]) => [k, !!v]))));
writeFileSync(join(OUT, "claims-devices.json"), JSON.stringify({ U, POS, MA, MB, MC }, null, 2), { mode: 0o600 });
await sleep(6000);

const sessions = {
  alice: await Dash.open(T.alice, { mainframeId: MA.id }), bob: await Dash.open(T.bob, { mainframeId: MB.id }),
  carol: await Dash.open(T.carol, { mainframeId: MC.id }), dave: await Dash.open(T.dave, { mainframeId: MA.id }),
};
const hasValue = (s) => (s?.widgets ?? []).some((x) => typeof x.value === "string" ? /[1-9]/.test(x.value) : x.value);
const widgets = (s) => (s?.widgets ?? []).map((x) => `${x.id}=${JSON.stringify(x.value)}`).join(" ").slice(0, 200);
const blockedOk = (s) => s && s.blocked && !(s.widgets ?? []).length;
const section = (who, id) => sessions[who].sections.get(id);

// ---- before the claim ------------------------------------------------------------------------------------------------
const baseline = section("alice", U.alice);
c.check("Before the claim", "Alice's card in the unclaimed zone shows values", hasValue(baseline) && !baseline.blocked, widgets(baseline));
c.check("Before the claim", "Alice's probe reads the cell", hasValue(section("alice", U.probe)), widgets(section("alice", U.probe)));

// ---- claim -------------------------------------------------------------------------------------------------------------
for (const [x, z] of CLAIM) {
  const out = await w.cmd(`cccloud dev claim bob ${x} ${Y} ${z}`);
  if (!/SUCCESS|success|ok/i.test(out)) c.info("Claim", `claim ${x} ${z}`, out);
}
await sleep(2500);

// ---- other team's claim: blocked, no values ------------------------------------------------------------------------------
const G1 = "Machines in another team's claim";
for (const [name, id] of [["uplink on a machine", U.alice], ["Cloud Probe reading into the claim", U.probe], ["ME Cloud Bridge", U.bridge], ["computer + uplink", U.computer]]) {
  const s = section("alice", id);
  c.check(G1, `Alice: ${name} blocked, no widgets`, blockedOk(s), JSON.stringify({ title: s?.title, blocked: s?.blocked, widgets: s?.widgets?.length }));
}
const rsCards = [...sessions.alice.sections.values()].filter((s) => s.blockedWidgets || s.template === "redstone" || /redstone|zone/i.test(s.title ?? ""));
c.check(G1, "Alice: Cloud Switch in the claim blocked", rsCards.some((s) => s.blockedWidgets && Object.keys(s.blockedWidgets).length), JSON.stringify(rsCards.map((s) => ({ title: s.title, blockedWidgets: s.blockedWidgets, widgets: s.widgets?.map((x) => [x.id, x.value]) }))));
c.check(G1, "Alice: her card outside the claim keeps working", hasValue(section("alice", U.aliceOut)) && !section("alice", U.aliceOut).blocked, widgets(section("alice", U.aliceOut)));
const dv = section("dave", U.alice);
c.check(G1, "Dave (viewer on MA): blocked too, and no team name without view.positions", blockedOk(dv), JSON.stringify(dv?.blocked));
const act = await sessions.alice.request({ t: "action", uplinkId: U.alice, id: "output", value: true }, "action_result").catch((e) => ({ error: e.message }));
c.check(G1, "Alice: an action on the blocked card is refused", act.error === "claim_blocked" || act.ok === false, JSON.stringify(act));
const bq = await sessions.alice.request({ t: "query", uplinkId: U.bridge, id: "search", q: "iron" }, "query_result").catch((e) => ({ error: e.message }));
c.check(G1, "Alice: a search on the blocked bridge is refused (its widgets are gone, so unknown_widget)", !!bq.error && !bq.rows?.length, JSON.stringify(bq).slice(0, 200));
const term = await sessions.alice.request({ t: "term_open", uplinkId: U.computer, computer: 9101 }, "term_result").catch((e) => ({ error: e.message }));
c.check(G1, "Alice: the terminal of the blocked computer does not open", term.error != null, JSON.stringify(term).slice(0, 200));

// ---- own claim and allies ---------------------------------------------------------------------------------------------------
const G2 = "Own team and allies";
c.check(G2, "Bob: his card in his claim shows values", hasValue(section("bob", U.bob)) && !section("bob", U.bob).blocked, widgets(section("bob", U.bob)));
c.check(G2, "Carol before the alliance: blocked", blockedOk(section("carol", U.carol)), JSON.stringify(section("carol", U.carol)?.blocked));
await w.cmd("cccloud dev ally bob carol true");
await sleep(2500);
c.check(G2, "Carol as ally of Blue: card works, values back", hasValue(section("carol", U.carol)) && !section("carol", U.carol).blocked, widgets(section("carol", U.carol)));

// ---- Cloud Displays and tablets --------------------------------------------------------------------------------------------
const G3 = "Cloud Display and Cloud Tablet";
await sleep(2000);
const dBlocked = await w.json(`cccloud dev display ${D.blocked.join(" ")}`);
c.check(G3, "Display showing Alice's blocked card: claim shown, no values, no team name", dBlocked.blocked && !(dBlocked.widgets ?? []).length && !dBlocked.blocked?.team, JSON.stringify(dBlocked).slice(0, 240));
const dAlly = await w.json(`cccloud dev display ${D.ally.join(" ")}`);
c.check(G3, "Display showing the ally's card: values", hasValue(dAlly) && !dAlly.blocked, widgets(dAlly));
const dFree = await w.json(`cccloud dev display ${D.free.join(" ")}`);
c.check(G3, "Display showing the card outside the claim: values", hasValue(dFree), widgets(dFree));
async function tablet(mf, player, id) {
  await w.json(`cccloud dev tablet ${mf.id} ${player}`);
  await sleep(2500);
  const t = await w.json(`cccloud dev tablet ${mf.id} ${player}`);
  const parsed = t.messages.map((m) => JSON.parse(m));
  const all = parsed.flatMap((m) => (m.t === "snapshot" ? m.uplinks ?? [] : m.t === "uplink" ? [m] : []));
  return { role: t.role, s: all.find((u) => u.uplinkId === id), count: all.length, network: parsed.find((m) => m.t === "snapshot")?.network };
}
const tA = await tablet(MA, "alice", U.alice);
c.check(G3, "Tablet of Alice: blocked, no widgets", blockedOk(tA.s), JSON.stringify({ role: tA.role, sections: tA.count, blocked: tA.s?.blocked, widgets: tA.s?.widgets?.length }));
const tC = await tablet(MC, "carol", U.carol);
c.check(G3, "Tablet of Carol (ally): values", hasValue(tC.s) && !tC.s?.blocked, widgets(tC.s));

// ---- analyzer overlay --------------------------------------------------------------------------------------------------------
const G4 = "Cloud Analyzer overlay";
const stand = `${X + 8} ${Y + 1} ${Z + 16}`;
const positions = (a) => [...(a.nodes ?? []), ...(a.devices ?? [])].map((d) => ({ id: d.id, x: d.x ?? d.pos?.[0], z: d.z ?? d.pos?.[2], type: d.type }));
const insideIds = (a) => positions(a).filter((d) => inClaim(d.x, d.z));
const aA = await w.json(`cccloud dev analyzer alice ${stand}`);
writeFileSync(join(OUT, "analyzer-alice.json"), JSON.stringify(aA, null, 2));
c.check(G4, "Alice standing next to the zone: nothing of hers inside it is sent", positions(aA).length > 0 && !insideIds(aA).length, `${positions(aA).length} elements, inside: ${JSON.stringify(insideIds(aA))}`);
c.check(G4, "Alice: no probe line into the zone", !(aA.probes ?? []).some((l) => inClaim(l[0], l[2]) || inClaim(l[3], l[5])), JSON.stringify(aA.probes));
const aC = await w.json(`cccloud dev analyzer carol ${stand}`);
c.check(G4, "Carol (ally): her device in the zone is shown", insideIds(aC).some((d) => d.id === U.carol), JSON.stringify(insideIds(aC)));
const aB = await w.json(`cccloud dev analyzer bob ${stand}`);
c.check(G4, "Bob: his devices in his claim are shown", insideIds(aB).length >= 2, JSON.stringify(insideIds(aB)));
await w.cmd(`cccloud dev role ${MB.id} alice viewer`);
await sleep(1000);
const aShared = await w.json(`cccloud dev analyzer alice ${stand}`);
c.check(G4, "Alice as viewer of Bob's mainframe: Bob's devices in his claim stay hidden", !insideIds(aShared).length, JSON.stringify(insideIds(aShared)));
await w.cmd(`cccloud dev role ${MB.id} alice none`);

// ---- map overlays, voxels, positions --------------------------------------------------------------------------------------------
const G5 = "Map, voxel view, positions";
const nodes = async (who) => (await w.json(`cccloud dev mapnodes ${who}`)).nodes ?? [];
const mn = await nodes("alice");
writeFileSync(join(OUT, "mapnodes-alice.json"), JSON.stringify(mn, null, 2));
c.check(G5, "Map mod waypoints of Alice: nothing inside the zone", !mn.some((n) => inClaim(n.x, n.z) && n.dim === OW), JSON.stringify(mn.filter((n) => inClaim(n.x, n.z)).map((n) => ({ type: n.type, x: n.x, z: n.z, name: n.name }))));
const netA = sessions.alice.head.network, netLive = sessions.alice.inbox.filter((m) => m.t === "network").pop()?.network ?? netA;
const netNodes = netLive?.nodes ?? [];
const inNet = netNodes.filter((n) => n.pos && inClaim(n.pos.x, n.pos.z));
c.check(G5, "Dashboard network map of Alice: nothing inside the zone", !inNet.length, JSON.stringify(inNet.map((n) => ({ id: n.id.slice(0, 8), type: n.type, name: n.name, pos: n.pos }))));
const probeNode = netNodes.find((n) => n.id === U.probe);
c.check(G5, "Network map: the blocked probe's target is left out", probeNode && !probeNode.target, JSON.stringify(probeNode?.target ?? null));
const daveNet = sessions.dave.head.network?.nodes ?? [];
c.check(G5, "Viewer without view.positions gets no positions at all", !daveNet.some((n) => n.pos), `${daveNet.length} nodes, role ${sessions.dave.head.role}`);

const voxel = async (token, mf, cx, cy, cz) => {
  const res = await fetch(`${ORIGIN}/api/mainframes/${mf.id}/voxels/${OW}/${cx}/${cy}/${cz}`, { headers: { Authorization: `Bearer ${token}` } });
  return { status: res.status, body: (await res.text()).slice(0, 120) };
};
const chunk = (v) => v >> 4, secY = (Y + 2) >> 4;
const vIn = await voxel(T.alice, MA, chunk(X + 16), secY, chunk(Z + 12));
c.check(G5, "Voxels: Alice, section in the zone -> claimed", vIn.status === 403 && /claimed/.test(vIn.body), JSON.stringify(vIn));
const vOut = await voxel(T.alice, MA, chunk(X - 20), secY, chunk(Z + 12));
c.check(G5, "Voxels: Alice, section outside -> data", vOut.status === 200, `${vOut.status}`);
const vBob = await voxel(T.bob, MB, chunk(X + 20), secY, chunk(Z + 12));
c.check(G5, "Voxels: Bob, his claim -> data", vBob.status === 200, `${vBob.status}`);
const vCarol = await voxel(T.carol, MC, chunk(X + 24), secY, chunk(Z + 12));
c.check(G5, "Voxels: Carol (ally), the zone -> data", vCarol.status === 200, `${vCarol.status} ${vCarol.body.slice(0, 60)}`);
const batch = await fetch(`${ORIGIN}/api/mainframes/${MA.id}/voxels/${OW}`, { method: "POST", headers: { Authorization: `Bearer ${T.alice}`, "Content-Type": "application/json" },
  body: JSON.stringify({ sections: [[chunk(X + 16), secY, chunk(Z + 12)], [chunk(X - 20), secY, chunk(Z + 12)]] }) });
const batchBody = Buffer.from(await batch.arrayBuffer());
const batchText = batchBody.toString("latin1");
c.check(G5, "Voxel batch: Alice's section in the zone answers claimed, carries no blocks of it", batch.status === 200 && /"s":"claimed"/.test(batchText), `${batch.status} ${batchText.slice(0, 200).replace(/[^\x20-\x7e]/g, ".")}`);

const tile = async (token, mf, layer) => {
  const tx = Math.floor((X + 32) / 128), tz = Math.floor((Z + 32) / 128); // tiles are 8 chunks wide
  const res = await fetch(`${ORIGIN}/api/mainframes/${mf.id}/map/${OW}/${layer}/${tx}/${tz}.png`, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status !== 200) return { status: res.status, text: (await res.text()).slice(0, 100) };
  const png = readPng(Buffer.from(await res.arrayBuffer()));
  const px = (x, z) => { const m = png.width / 128; return { a: png.alpha(Math.floor(((x % 128) + 128) % 128 * m), Math.floor(((z % 128) + 128) % 128 * m)), rgb: png.rgb(Math.floor(((x % 128) + 128) % 128 * m), Math.floor(((z % 128) + 128) % 128 * m)) }; };
  return { status: 200, gold: px(X + 32, Z + 32), outside: px(X - 2, Z - 2) };
};
await sleep(1500);
const sliceY = Y + 1;
const sA = await tile(T.alice, MA, `slice/${sliceY}`);
c.check(G5, "Slice map: Alice's tile has the zone empty (Bob's gold room not drawn)", sA.status === 200 ? sA.gold.a === 0 : sA.status === 204, JSON.stringify(sA));
const sB = await tile(T.bob, MB, `slice/${sliceY}`);
c.check(G5, "Slice map: Bob sees his own room", sB.status === 200 && sB.gold.a > 0, JSON.stringify(sB));
const sC = await tile(T.carol, MC, `slice/${sliceY}`);
c.check(G5, "Slice map: Carol (ally) sees it", sC.status === 200 && sC.gold.a > 0, JSON.stringify(sC));
const sSurface = await tile(T.alice, MA, "surface");
c.info(G5, "Surface map of Alice over the zone (the top of the world, like any flying player sees; docs/SECURITY.md)", JSON.stringify(sSurface));

// ---- live changes ------------------------------------------------------------------------------------------------------------
const G6 = "Live changes";
await w.cmd("cccloud dev ally bob alice true");
await sleep(2500);
c.check(G6, "Bob allies Alice: her card works at once", hasValue(section("alice", U.alice)) && !section("alice", U.alice).blocked, widgets(section("alice", U.alice)));
await w.cmd("cccloud dev ally bob alice false");
await sleep(2500);
c.check(G6, "Alliance ended: blocked again", blockedOk(section("alice", U.alice)), JSON.stringify(section("alice", U.alice)?.blocked));
const [ux, , uz] = POS.alice;
await w.cmd(`cccloud dev unclaim bob ${ux} ${Y} ${uz}`);
await sleep(2500);
c.check(G6, "Chunk unclaimed: the card works again", hasValue(section("alice", U.alice)) && !section("alice", U.alice).blocked, widgets(section("alice", U.alice)));
await w.cmd(`cccloud dev claim bob ${ux} ${Y} ${uz}`);
await sleep(2500);
c.check(G6, "Claimed again: blocked at once", blockedOk(section("alice", U.alice)), JSON.stringify(section("alice", U.alice)?.blocked));

// ---- what stays after a block -------------------------------------------------------------------------------------------------
const G7 = "After a block";
const hist = await fetch(`${ORIGIN}/api/mainframes/${MA.id}/history/24h/json/w/${U.alice}/stored`, { headers: { Authorization: `Bearer ${T.alice}` } });
c.info(G7, "History of a blocked card (values from before the claim, recording stops)", `${hist.status} ${(await hist.text()).slice(0, 160)}`);
const list = await w.cmd("cccloud status");
c.info(G7, "cccloud status from RCON", list.slice(0, 120));

for (const s of Object.values(sessions)) s.close();
const NAME = process.env.CLAIMS_NAME ?? "claims"; // "claims-before": a run on a jar without a fix, the report quotes its failures
writeFileSync(join(OUT, `${NAME}.json`), JSON.stringify(c.rows, null, 2));
const failed = c.write(join(OUT, `${NAME}.md`));
await w.cleanup();
console.log(`${failed} failed`);
process.exit(failed ? 1 : 0);
