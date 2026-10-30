// Build step for the 3D network view: converts the mod's block models (JSON +
// PNG textures in src/main/resources/assets/cccloud) into compact, pre-rotated
// geometry the dashboard can render with three.js – same rules as
// preview/build_viewer.py (vanilla element/face/uv/rotation handling, faces
// with partially transparent texels are translucent, emissive elements).
//
//   node dev/build-models.ts            -> web/src/network/models.gen.json
import { readFileSync, readdirSync, writeFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { inflateSync } from "node:zlib";

const HERE = fileURLToPath(new URL(".", import.meta.url));
export const ASSETS = join(HERE, "../../src/main/resources/assets/cccloud");
const OUT = join(HERE, "../web/src/network/models.gen.json");

type Vec = [number, number, number];
interface Face {
  uv?: number[];
  texture: string;
  rotation?: number;
}
interface Element {
  from: Vec;
  to: Vec;
  rotation?: { origin: Vec; axis: "x" | "y" | "z"; angle: number };
  faces?: Record<string, Face>;
  shade?: boolean;
  light_emission?: number;
}
interface ModelJson {
  parent?: string;
  textures?: Record<string, string>;
  elements?: Element[];
}

export interface Part {
  tex: string;
  emit: boolean;
  trans: boolean;
  pos: number[];
  uv: number[];
  shade: number[];
  idx: number[];
}
export interface GenModels {
  textures: Record<string, string>;
  /** Geometry per node type (first status); `status` maps other statuses to texture swaps. */
  models: Record<string, { parts: Part[]; tall: number; status: Record<string, Record<string, string>> }>;
}

// ------------------------------------------------------------------ PNG decode
/** Minimal PNG decoder (8-bit RGB/RGBA/palette, non-interlaced) – enough for the mod's textures. */
export function decodePng(buf: Buffer): { w: number; h: number; rgba: Uint8Array } {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error("not a png");
  let off = 8;
  let w = 0;
  let h = 0;
  let type = 0;
  let depth = 0;
  let palette: Buffer | null = null;
  let trns: Buffer | null = null;
  const idat: Buffer[] = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const t = buf.toString("ascii", off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (t === "IHDR") {
      w = data.readUInt32BE(0);
      h = data.readUInt32BE(4);
      depth = data[8];
      type = data[9];
      if (data[12] !== 0) throw new Error("interlaced png");
    } else if (t === "PLTE") palette = data;
    else if (t === "tRNS") trns = data;
    else if (t === "IDAT") idat.push(data);
    else if (t === "IEND") break;
    off += 12 + len;
  }
  if (depth !== 8) throw new Error(`unsupported bit depth ${depth}`);
  const bpp = type === 6 ? 4 : type === 2 ? 3 : type === 3 ? 1 : type === 4 ? 2 : type === 0 ? 1 : 0;
  if (!bpp) throw new Error(`unsupported color type ${type}`);
  const raw = inflateSync(Buffer.concat(idat));
  const stride = w * bpp;
  const out = new Uint8Array(w * h * 4);
  const prev = new Uint8Array(stride);
  const cur = new Uint8Array(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? cur[x - bpp] : 0;
      const b = prev[x];
      const c = x >= bpp ? prev[x - bpp] : 0;
      const v = raw[y * (stride + 1) + 1 + x];
      let r: number;
      if (f === 0) r = v;
      else if (f === 1) r = v + a;
      else if (f === 2) r = v + b;
      else if (f === 3) r = v + ((a + b) >> 1);
      else {
        const p = a + b - c;
        const pa = Math.abs(p - a);
        const pb = Math.abs(p - b);
        const pc = Math.abs(p - c);
        r = v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
      }
      cur[x] = r & 255;
    }
    for (let x = 0; x < w; x++) {
      const o = (y * w + x) * 4;
      if (type === 6) out.set(cur.subarray(x * 4, x * 4 + 4), o);
      else if (type === 2) out.set([cur[x * 3], cur[x * 3 + 1], cur[x * 3 + 2], 255], o);
      else if (type === 0) out.set([cur[x], cur[x], cur[x], 255], o);
      else if (type === 4) out.set([cur[x * 2], cur[x * 2], cur[x * 2], cur[x * 2 + 1]], o);
      else {
        const i = cur[x];
        out.set([palette![i * 3], palette![i * 3 + 1], palette![i * 3 + 2], trns && i < trns.length ? trns[i] : 255], o);
      }
    }
    prev.set(cur);
  }
  return { w, h, rgba: out };
}

// --------------------------------------------------------------- model logic
const CORNERS: Record<string, number[][]> = {
  north: [[1, 1, 0], [0, 1, 0], [0, 0, 0], [1, 0, 0]],
  south: [[0, 1, 1], [1, 1, 1], [1, 0, 1], [0, 0, 1]],
  west: [[0, 1, 0], [0, 1, 1], [0, 0, 1], [0, 0, 0]],
  east: [[1, 1, 1], [1, 1, 0], [1, 0, 0], [1, 0, 1]],
  up: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]],
  down: [[0, 0, 1], [1, 0, 1], [1, 0, 0], [0, 0, 0]],
};

function rotate(v: Vec, axis: "x" | "y" | "z", deg: number, o: Vec): Vec {
  const r = (deg * Math.PI) / 180;
  const c = Math.cos(r);
  const s = Math.sin(r);
  const x = v[0] - o[0];
  const y = v[1] - o[1];
  const z = v[2] - o[2];
  let p: Vec;
  if (axis === "x") p = [x, y * c - z * s, y * s + z * c];
  else if (axis === "y") p = [x * c + z * s, y, -x * s + z * c];
  else p = [x * c - y * s, x * s + y * c, z];
  return [p[0] + o[0], p[1] + o[1], p[2] + o[2]];
}

const shadeOf = (n: Vec) => Math.min(n[0] * n[0] * 0.6 + n[1] * n[1] * (n[1] > 0 ? 1 : 0.5) + n[2] * n[2] * 0.8, 1);

function loadModels() {
  const models: Record<string, ModelJson> = {};
  const dir = join(ASSETS, "models", "block");
  for (const f of readdirSync(dir)) if (f.endsWith(".json")) models[`cccloud:block/${f.slice(0, -5)}`] = JSON.parse(readFileSync(join(dir, f), "utf8"));
  return models;
}

function resolve(models: Record<string, ModelJson>, id: string) {
  const chain: ModelJson[] = [];
  let cur: string | undefined = id;
  for (let n = 0; cur && models[cur] && n < 10; n++) {
    chain.push(models[cur]);
    cur = models[cur].parent;
  }
  if (!chain.length) throw new Error(`unknown model ${id}`);
  const textures: Record<string, string> = {};
  for (let i = chain.length - 1; i >= 0; i--) Object.assign(textures, chain[i].textures ?? {});
  const elements = chain.find((m) => m.elements)?.elements ?? [];
  return { textures, elements };
}

const resolveRef = (tx: Record<string, string>, r: string) => {
  for (let n = 0; r?.startsWith("#") && n < 10; n++) r = tx[r.slice(1)];
  return r;
};

/** Builds parts for one model, rotated by the blockstate's x/y (vanilla: x first, then y, clockwise). */
export function buildModel(
  models: Record<string, ModelJson>,
  id: string,
  rx: number,
  ry: number,
  isTranslucent: (tex: string, uv: number[]) => boolean,
  dy = 0,
): Part[] {
  const { textures, elements } = resolve(models, id);
  const buckets = new Map<string, Part>();
  const C: Vec = [8, 8, 8];
  for (const el of elements) {
    const emit = (el.light_emission ?? 0) > 0;
    for (const [dir, face] of Object.entries(el.faces ?? {})) {
      const tex = resolveRef(textures, face.texture);
      if (!tex || tex.startsWith("#")) continue;
      let pts = CORNERS[dir].map((c) => [c[0] ? el.to[0] : el.from[0], c[1] ? el.to[1] : el.from[1], c[2] ? el.to[2] : el.from[2]] as Vec);
      if (el.rotation) pts = pts.map((p) => rotate(p, el.rotation!.axis, el.rotation!.angle, el.rotation!.origin));
      // blockstate rotation around the block centre (negative = clockwise, like the viewer)
      if (rx) pts = pts.map((p) => rotate(p, "x", -rx, C));
      if (ry) pts = pts.map((p) => rotate(p, "y", -ry, C));
      const uv = face.uv ?? defaultUv(dir, el);
      let uvs = [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]];
      const rot = (face.rotation ?? 0) / 90;
      if (rot) uvs = uvs.slice(rot).concat(uvs.slice(0, rot));
      const e1 = [pts[3][0] - pts[0][0], pts[3][1] - pts[0][1], pts[3][2] - pts[0][2]];
      const e2 = [pts[1][0] - pts[0][0], pts[1][1] - pts[0][1], pts[1][2] - pts[0][2]];
      const n: Vec = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
      const len = Math.hypot(...n) || 1;
      const nn: Vec = [n[0] / len, n[1] / len, n[2] / len];
      const trans = isTranslucent(tex, uv);
      const key = `${tex}|${emit}|${trans}`;
      let b = buckets.get(key);
      if (!b) buckets.set(key, (b = { tex, emit, trans, pos: [], uv: [], shade: [], idx: [] }));
      const s = el.shade === false || emit ? 1 : shadeOf(nn);
      const base = b.pos.length / 3;
      for (let i = 0; i < 4; i++) {
        b.pos.push(r2(pts[i][0]), r2(pts[i][1] + dy), r2(pts[i][2]));
        b.uv.push(r4(uvs[i][0] / 16), r4(uvs[i][1] / 16));
      }
      b.shade.push(r2(s));
      b.idx.push(base, base + 2, base + 1, base, base + 3, base + 2);
    }
  }
  return [...buckets.values()];
}
const r2 = (n: number) => Math.round(n * 100) / 100;
const r4 = (n: number) => Math.round(n * 10000) / 10000;
function defaultUv(dir: string, el: Element): number[] {
  const [x0, y0, z0] = el.from;
  const [x1, y1, z1] = el.to;
  if (dir === "up" || dir === "down") return [x0, z0, x1, z1];
  if (dir === "north" || dir === "south") return [x0, 16 - y1, x1, 16 - y0];
  return [z0, 16 - y1, z1, 16 - y0];
}

/** Picks the blockstate model + rotation for the given properties. */
function variant(block: string, props: Record<string, string>) {
  const bs = JSON.parse(readFileSync(join(ASSETS, "blockstates", `${block}.json`), "utf8"));
  const out: { model: string; x: number; y: number }[] = [];
  const matches = (when: Record<string, string> | undefined) => !when || Object.entries(when).every(([k, v]) => !(k in props) || String(v).split("|").includes(props[k]));
  if (bs.variants) {
    for (const [k, v] of Object.entries(bs.variants as Record<string, any>)) {
      const kv = Object.fromEntries(k.split(",").filter(Boolean).map((s) => s.split("=")));
      if (matches(kv)) {
        const a = Array.isArray(v) ? v[0] : v;
        out.push({ model: a.model, x: a.x ?? 0, y: a.y ?? 0 });
        break;
      }
    }
  } else
    for (const part of bs.multipart ?? []) {
      const when = part.when;
      const ok = when?.OR ? when.OR.some((w: any) => matches(w)) : matches(when);
      if (!ok) continue;
      const a = Array.isArray(part.apply) ? part.apply[0] : part.apply;
      out.push({ model: a.model, x: a.x ?? 0, y: a.y ?? 0 });
    }
  return out;
}

/** What the 3D view draws per node type (block id, properties). */
export const KINDS: Record<string, { block: string; props: Record<string, string>; statuses: string[]; extra?: string[] }> = {
  mainframe: { block: "cloud_mainframe", props: { facing: "north" }, statuses: ["online", "connecting", "offline"] },
  relay: { block: "cloud_relay", props: {}, statuses: ["online", "connecting", "offline", "no_power"], extra: ["cccloud:block/cloud_relay_ring"] },
  advanced_relay: { block: "advanced_cloud_relay", props: {}, statuses: ["online", "connecting", "offline", "no_power"], extra: ["cccloud:block/advanced_cloud_relay_ring"] },
  ender_relay: { block: "ender_relay", props: {}, statuses: ["online", "connecting", "offline", "no_power"], extra: ["cccloud:block/ender_relay_core"] },
  uplink: { block: "cloud_uplink", props: { facing: "down", direct: "false" }, statuses: ["online", "connecting", "offline"] },
  advanced_uplink: { block: "advanced_cloud_uplink", props: { facing: "down", direct: "false" }, statuses: ["online", "connecting", "offline"] },
  bridge: { block: "me_cloud_bridge", props: {}, statuses: ["online", "connecting", "offline"] },
  // Cloud Redstone devices: plates lying on the ground (facing down), the lamp lit (no status of its own)
  cloud_switch: { block: "cloud_switch", props: { facing: "down", powered: "true" }, statuses: ["online", "connecting", "offline"] },
  cloud_button: { block: "cloud_button", props: { facing: "down", powered: "false" }, statuses: ["online", "connecting", "offline"] },
  cloud_sensor: { block: "cloud_sensor", props: { facing: "down", powered: "true" }, statuses: ["online", "connecting", "offline"] },
  cloud_lamp: { block: "cloud_lamp", props: { level: "15" }, statuses: ["online"] },
};

export function generate(): GenModels {
  const models = loadModels();
  const texData = new Map<string, { w: number; h: number; rgba: Uint8Array }>();
  const texFile = (id: string) => join(ASSETS, "textures", `${id.replace(/^cccloud:/, "")}.png`);
  const pixels = (id: string) => {
    if (!texData.has(id)) texData.set(id, decodePng(readFileSync(texFile(id))));
    return texData.get(id)!;
  };
  const isTranslucent = (id: string, uv: number[]) => {
    const t = pixels(id);
    const x0 = Math.floor((Math.min(uv[0], uv[2]) / 16) * t.w);
    const x1 = Math.ceil((Math.max(uv[0], uv[2]) / 16) * t.w);
    const y0 = Math.floor((Math.min(uv[1], uv[3]) / 16) * t.h);
    const y1 = Math.ceil((Math.max(uv[1], uv[3]) / 16) * t.h);
    for (let y = y0; y < Math.max(y1, y0 + 1); y++)
      for (let x = x0; x < Math.max(x1, x0 + 1); x++) {
        const a = t.rgba[(Math.min(t.h - 1, y) * t.w + Math.min(t.w - 1, x)) * 4 + 3];
        if (a > 0 && a < 255) return true;
      }
    return false;
  };

  const out: GenModels = { textures: {}, models: {} };
  const addTex = (t: string) => {
    if (!out.textures[t]) out.textures[t] = `data:image/png;base64,${readFileSync(texFile(t)).toString("base64")}`;
  };
  for (const [kind, def] of Object.entries(KINDS)) {
    const build = (status: string) => {
      const parts: Part[] = [];
      if (kind === "mainframe") {
        // two blocks: lower + upper
        for (const [half, dy] of [["lower", 0], ["upper", 16]] as const)
          for (const v of variant(def.block, { ...def.props, half, status })) parts.push(...buildModel(models, v.model, v.x, v.y, isTranslucent, dy));
      } else {
        for (const v of variant(def.block, { ...def.props, status })) parts.push(...buildModel(models, v.model, v.x, v.y, isTranslucent));
        for (const e of def.extra ?? []) if (models[e]) parts.push(...buildModel(models, e, 0, 0, isTranslucent));
      }
      return parts;
    };
    const [first, ...rest] = def.statuses;
    const parts = build(first);
    parts.forEach((p) => addTex(p.tex));
    const status: Record<string, Record<string, string>> = { [first]: {} };
    for (const st of rest) {
      const other = build(st);
      if (other.length !== parts.length) throw new Error(`${kind}:${st} differs in geometry`);
      const swap: Record<string, string> = {};
      other.forEach((p, i) => {
        if (p.pos.length !== parts[i].pos.length) throw new Error(`${kind}:${st} differs in geometry`);
        if (p.tex !== parts[i].tex) {
          swap[parts[i].tex] = p.tex;
          addTex(p.tex);
        }
      });
      status[st] = swap;
    }
    out.models[kind] = { parts, tall: kind === "mainframe" ? 2 : 1, status };
  }
  return out;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  if (!existsSync(ASSETS)) throw new Error(`assets not found: ${ASSETS}`);
  const g = generate();
  const json = JSON.stringify(g);
  writeFileSync(OUT, json);
  const parts = Object.values(g.models).reduce((n, m) => n + m.parts.length, 0);
  console.log(`models.gen.json: ${Math.round(json.length / 1024)} KiB, ${Object.keys(g.models).length} models, ${parts} parts, ${Object.keys(g.textures).length} textures`);
}
