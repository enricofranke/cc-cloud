// Shared helpers of the claims and cross-dimension scenarios: RCON with failure detection, cards, mainframes and checks.
import { Rcon } from "../rcon.mjs";
import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { inflateSync } from "node:zlib";

export const SERVER_DIR = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
export const OUT = process.env.GALLERY_OUT ?? join(SERVER_DIR, "gallery-out");
mkdirSync(OUT, { recursive: true });
export const Y = -60;
export const OW = "minecraft:overworld", NETHER = "minecraft:the_nether", END = "minecraft:the_end";
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const CELL = (fill = 2_000_000_000) => `powah:energy_cell_nitro{energy_stored_main_energy:${fill}L}`;
export const uuidInts = (u) => {
  const hex = u.replace(/-/g, "");
  return [0, 8, 16, 24].map((i) => parseInt(hex.slice(i, i + 8), 16) | 0);
};
export const linkNbt = (mf, by) =>
  `link:{pos:{dimension:"${mf.dim ?? OW}",pos:[I;${mf.pos.join(",")}]},id:[I;${uuidInts(mf.id).join(",")}]},net_v:1${by ? `,linked_by:"${by}"` : ""}`;

export class World {
  static async open() {
    const w = new World();
    w.rcon = await Rcon.connect();
    w.log = [];
    return w;
  }
  async cmd(c, { dim = null } = {}) {
    const full = dim ? `execute in ${dim} run ${c}` : c;
    const out = await this.rcon.cmd(full);
    this.log.push(`> ${full}\n${out}`);
    if (/^(Unknown|Incorrect|Expected|Invalid|Unable)|<--\[HERE\]|^failed/m.test(out)) console.warn(`  ! ${full.slice(0, 140)}\n    -> ${out.slice(0, 200)}`);
    return out;
  }
  /** A dev command's JSON answer. */
  async json(c, opts) {
    const out = await this.cmd(c, opts);
    const at = out.indexOf("json ");
    if (at < 0) throw new Error(`no JSON from ${c}: ${out.slice(0, 200)}`);
    return JSON.parse(out.slice(at + 5).trim());
  }
  async uuid(name) {
    return (await this.cmd(`cccloud dev uuid ${name}`)).match(/uuid (\S+)/)[1];
  }
  async token(mainframeId, name) {
    return (await this.cmd(`cccloud dev token ${mainframeId} ${name}`)).match(/token (\S+)/)?.[1];
  }
  /** Mainframe (with Quantum Core, so capacity never matters) on a charged cell, owned by a scripted player. */
  async mainframe(pos, owner, name, { dim = OW } = {}) {
    const [x, y, z] = pos;
    await this.cmd(`setblock ${x} ${y} ${z} cccloud:cloud_mainframe[half=lower,facing=south]`, { dim });
    await this.cmd(`setblock ${x} ${y + 1} ${z} cccloud:cloud_mainframe[half=upper,facing=south]`, { dim });
    await sleep(1500);
    const id = (await this.cmd(`data get block ${x} ${y} ${z} mainframe_id`, { dim })).match(/"([0-9a-f-]{36})"/)?.[1];
    if (!id) throw new Error(`no mainframe at ${pos}`);
    await this.cmd(`data merge block ${x} ${y} ${z} {name:"${name}",upgrades:{Items:[{Slot:4b,id:"cccloud:quantum_core",count:1}]}}`, { dim });
    await this.cmd(`cccloud dev own ${x} ${y} ${z} ${owner.name}`, { dim }); // access data and upgrade effects
    await this.cmd(`setblock ${x + 1} ${y} ${z} ${CELL()}`, { dim });
    return { id, pos, dim };
  }
  async cleanup() {
    writeFileSync(join(OUT, "scenario-rcon.log"), this.log.join("\n"));
    this.rcon.close();
  }
}

/** Collects checks and writes them as a markdown section (gallery-report.mjs includes it). */
export class Checks {
  rows = [];
  constructor(title, intro) { this.title = title; this.intro = intro; }
  check(group, name, ok, evidence = "") {
    this.rows.push({ group, name, ok: !!ok, evidence: String(evidence) });
    console.log(`${ok ? "PASS" : "FAIL"}  ${group}: ${name}${evidence ? `  — ${String(evidence).slice(0, 160)}` : ""}`);
  }
  info(group, name, evidence) {
    this.rows.push({ group, name, ok: null, evidence: String(evidence) });
    console.log(`INFO  ${group}: ${name}  — ${String(evidence).slice(0, 200)}`);
  }
  write(file) {
    const esc = (s) => s.replaceAll("|", "\\|").replaceAll("\n", " ");
    const fail = this.rows.filter((r) => r.ok === false).length;
    const pass = this.rows.filter((r) => r.ok === true).length;
    const md = [`## ${this.title}`, "", this.intro, "", `**${pass} passed, ${fail} failed.**`, ""];
    for (const group of [...new Set(this.rows.map((r) => r.group))]) {
      md.push(`### ${group}`, "", "| Check | Result | Evidence |", "|---|---|---|");
      for (const r of this.rows.filter((x) => x.group === group)) {
        md.push(`| ${esc(r.name)} | ${r.ok === null ? "info" : r.ok ? "pass" : "**FAIL**"} | ${esc(r.evidence.slice(0, 300))} |`);
      }
      md.push("");
    }
    writeFileSync(file, md.join("\n"));
    return fail;
  }
}

/** Minimal PNG reader (8 bit, colour types 2, 3, 6, no interlace): { width, height, alpha(x, y), rgb(x, y) }. */
export function readPng(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error("not a PNG");
  let at = 8, width = 0, height = 0, type = 0, plte = null, trns = null;
  const idat = [];
  while (at < buf.length) {
    const len = buf.readUInt32BE(at), kind = buf.toString("latin1", at + 4, at + 8), data = buf.subarray(at + 8, at + 8 + len);
    if (kind === "IHDR") { width = data.readUInt32BE(0); height = data.readUInt32BE(4); type = data[9]; if (data[8] !== 8 || data[12]) throw new Error("unsupported PNG"); }
    if (kind === "PLTE") plte = data;
    if (kind === "tRNS") trns = data;
    if (kind === "IDAT") idat.push(data);
    at += 12 + len;
  }
  const bpp = { 2: 3, 3: 1, 6: 4 }[type];
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * bpp, img = Buffer.alloc(stride * height);
  for (let y = 0, p = 0; y < height; y++) {
    const f = raw[p++];
    for (let x = 0; x < stride; x++, p++) {
      const a = x >= bpp ? img[y * stride + x - bpp] : 0, b = y ? img[(y - 1) * stride + x] : 0, c = x >= bpp && y ? img[(y - 1) * stride + x - bpp] : 0;
      let v = raw[p];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const q = a + b - c, pa = Math.abs(q - a), pb = Math.abs(q - b), pc = Math.abs(q - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      img[y * stride + x] = v & 255;
    }
  }
  const px = (x, y) => (y * width + x) * bpp;
  return {
    width, height,
    alpha: (x, y) => (type === 6 ? img[px(x, y) + 3] : type === 3 ? (trns && img[px(x, y)] < trns.length ? trns[img[px(x, y)]] : 255) : 255),
    rgb: (x, y) => (type === 3 ? [...plte.subarray(img[px(x, y)] * 3, img[px(x, y)] * 3 + 3)] : [img[px(x, y)], img[px(x, y) + 1], img[px(x, y) + 2]]),
  };
}
