// DEV-ONLY fake AE2 network behind an ME Cloud Bridge in direct mode
// (docs/SPEC.md "v0.2 – Cloud Bridges"). Produces the Storage section with
// gauges, labels, item_search, watchlist and jobs widgets.

export interface Item {
  name: string;
  displayName: string;
  count: number;
  craftable: boolean;
}
export interface Job {
  jobId: string;
  name: string;
  displayName: string;
  count: number;
  status: "queued" | "running" | "done" | "failed" | "cancelled";
  progress: number;
  /** progress per tick */
  speed: number;
  notify?: boolean;
  source?: "dashboard" | "lua" | "autostock" | "rule";
  error?: string;
  started?: number;
}
export interface Watch {
  name: string;
  min: number;
  below: boolean;
  target?: number;
  autoCraft?: boolean;
  state?: "idle" | "crafting" | "waiting_cpu" | "missing" | "paused";
  failures?: number;
  cooldownUntil?: number;
}

export const MAX_CRAFT = 4096;
export const MAX_ROWS = 50;
const CPUS = 2;

/** Small seeded PRNG so the fake network looks the same on every run. */
function rng(seed: number) {
  return () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 2 ** 32;
  };
}

const title = (s: string) => s.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

export function buildItems(): Map<string, Item> {
  const rand = rng(42);
  const items = new Map<string, Item>();
  const add = (name: string, displayName: string, count: number, craftable = false) => items.set(name, { name, displayName, count, craftable });

  // metals: vanilla iron/gold/copper plus Railcraft's (all items the icon export can draw, docs/BUILD.md)
  const metals: [string, string, string[]][] = [
    ["minecraft", "iron", ["ingot", "nugget", "block", "raw", "ore"]],
    ["minecraft", "gold", ["ingot", "nugget", "block", "raw", "ore"]],
    ["minecraft", "copper", ["ingot", "nugget", "block", "raw", "ore"]],
    ["railcraft", "iron", ["plate", "gear"]],
    ["railcraft", "gold", ["plate", "gear"]],
    ["railcraft", "copper", ["plate", "gear"]],
    ...["tin", "lead", "silver", "nickel", "zinc", "steel", "bronze", "brass", "invar"].map((m) => ["railcraft", m, ["ingot", "nugget", "block", "plate", "gear"]] as [string, string, string[]]),
  ];
  for (const [mod, metal, forms] of metals)
    for (const form of forms) {
      const name = form === "raw" ? `${mod}:raw_${metal}` : form === "block" ? `${mod}:${metal}_block` : `${mod}:${metal}_${form}`;
      const display = form === "raw" ? `Raw ${title(metal)}` : form === "block" ? `Block of ${title(metal)}` : `${title(metal)} ${title(form)}`;
      const scale = form === "ingot" ? 60_000 : form === "nugget" ? 9_000 : form === "block" ? 4_000 : form === "ore" ? 900 : 2_500;
      add(name, display, Math.round(rand() ** 2 * scale), ["plate", "gear"].includes(form) && rand() > 0.35);
    }

  const misc: [string, string, number, boolean?][] = [
    ["minecraft:cobblestone", "Cobblestone", 1_834_210],
    ["minecraft:stone", "Stone", 412_388, true],
    ["minecraft:dirt", "Dirt", 96_512],
    ["minecraft:redstone", "Redstone Dust", 284_002],
    ["minecraft:diamond", "Diamond", 3_412],
    ["minecraft:emerald", "Emerald", 812],
    ["minecraft:netherite_ingot", "Netherite Ingot", 37, true],
    ["minecraft:glass", "Glass", 18_220, true],
    ["minecraft:oak_log", "Oak Log", 12_004],
    ["minecraft:oak_planks", "Oak Planks", 7_340, true],
    ["minecraft:stick", "Stick", 2_210, true],
    ["minecraft:torch", "Torch", 1_920, true],
    ["minecraft:ender_pearl", "Ender Pearl", 266],
    ["minecraft:blaze_rod", "Blaze Rod", 448],
    ["minecraft:quartz", "Nether Quartz", 22_310],
    ["ae2:certus_quartz_crystal", "Certus Quartz Crystal", 9_412],
    ["ae2:fluix_crystal", "Fluix Crystal", 1_204, true],
    ["ae2:fluix_dust", "Fluix Dust", 310, true],
    ["ae2:silicon", "Silicon", 4_022],
    ["ae2:printed_silicon", "Printed Silicon", 288, true],
    ["ae2:logic_processor", "Logic Processor", 96, true],
    ["ae2:calculation_processor", "Calculation Processor", 64, true],
    ["ae2:engineering_processor", "Engineering Processor", 51, true],
    ["ae2:fluix_glass_cable", "Fluix Glass Cable", 812, true],
    ["ae2:cell_component_64k", "64k ME Storage Component", 4, true],
    ["ae2:singularity", "Singularity", 0, true],
    ["enderio:basic_capacitor", "Basic Capacitor", 140, true],
    ["enderio:double_layer_capacitor", "Double-Layer Capacitor", 32, true],
    ["enderio:octadic_capacitor", "Octadic Capacitor", 0, true],
    ["powah:dielectric_paste", "Dielectric Paste", 2_048],
    ["railcraft:bushing_gear", "Bushing Gear", 6_120, true],
    ["modularrouters:blank_module", "Blank Module", 2_430],
    ["integrateddynamics:variable", "Variable Card", 412, true],
    ["sfm:cable", "Inventory Cable", 980, true],
    ["computercraft:computer_advanced", "Advanced Computer", 6, true],
    ["computercraft:wired_modem", "Wired Modem", 18, true],
    ["computercraft:cable", "Networking Cable", 340, true],
    ["cccloud:cloud_core", "Cloud Core", 3, true],
    ["mysticalagriculture:inferium_essence", "Inferium Essence", 1_203_441],
    ["mysticalagriculture:prudentium_essence", "Prudentium Essence", 48_220, true],
  ];
  for (const [n, d, c, cr] of misc) add(n, d, c, cr === true);

  const bulk = ["granite", "diorite", "andesite", "cobbled_deepslate", "tuff", "calcite", "netherrack", "end_stone", "basalt", "blackstone", "sand", "red_sand", "gravel",
    "clay_ball", "flint", "bone", "bone_meal", "string", "leather", "feather", "gunpowder", "slime_ball", "glowstone_dust", "coal", "charcoal", "lapis_lazuli",
    "amethyst_shard", "prismarine_crystals", "obsidian", "paper", "book", "ender_eye", "ghast_tear", "magma_cream", "honeycomb", "ink_sac", "rotten_flesh", "spider_eye"];
  for (const b of bulk) add(`minecraft:${b}`, title(b), Math.round(rand() ** 1.5 * 60_000), ["paper", "book", "ender_eye", "bone_meal", "magma_cream"].includes(b));
  for (const d of ["coal", "charcoal", "obsidian", "saltpeter", "sulfur", "ender"]) add(`railcraft:${d}_dust`, `${title(d)} Dust`, Math.round(rand() * 4_000), d !== "saltpeter" && d !== "sulfur");

  const food = ["wheat", "carrot", "potato", "beetroot", "melon_slice", "pumpkin", "sugar_cane", "apple", "bread", "cooked_beef", "egg", "bamboo", "kelp", "cactus", "cocoa_beans"];
  for (const f of food) add(`minecraft:${f}`, title(f), Math.round(rand() * 40_000));
  const woods = ["spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"];
  for (const w of woods) {
    add(`minecraft:${w}_log`, `${title(w)} Log`, Math.round(rand() * 9_000));
    add(`minecraft:${w}_planks`, `${title(w)} Planks`, Math.round(rand() * 6_000), true);
  }
  const colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"];
  for (const c of colors) {
    add(`minecraft:${c}_wool`, `${title(c)} Wool`, Math.round(rand() * 900));
    add(`minecraft:${c}_dye`, `${title(c)} Dye`, Math.round(rand() * 600), rand() > 0.5);
    add(`minecraft:${c}_concrete`, `${title(c)} Concrete`, Math.round(rand() * 5_000), true);
  }
  // drop empty, non-craftable entries (AE2 does not list them)
  for (const [k, v] of items) if (v.count === 0 && !v.craftable) items.delete(k);
  return items;
}

export function search(items: Map<string, Item>, q: string) {
  const needle = q.trim().toLowerCase();
  const all = [...items.values()].filter((i) => !needle || i.name.toLowerCase().includes(needle) || i.displayName.toLowerCase().includes(needle));
  // exact-ish matches first, then by count
  all.sort((a, b) => {
    const pa = a.displayName.toLowerCase().startsWith(needle) ? 0 : 1;
    const pb = b.displayName.toLowerCase().startsWith(needle) ? 0 : 1;
    return pa - pb || b.count - a.count || a.displayName.localeCompare(b.displayName);
  });
  return { rows: all.slice(0, MAX_ROWS).map(({ name, displayName, count, craftable }) => ({ name, displayName, count, craftable })), truncated: all.length > MAX_ROWS };
}

export type CraftResult = { ok: true; jobId: string } | { ok: false; error: "not_craftable" | "no_cpu" | "missing_resources" | "too_many" | "unknown_item" };

export class FakeAe2 {
  readonly items = buildItems();
  readonly jobs: Job[] = [];
  readonly watch: Watch[] = [];
  readonly favorites = new Set<string>(["minecraft:diamond", "ae2:fluix_crystal"]);
  readonly fluids = [
    { name: "minecraft:water", displayName: "Water", amount: 1_200_000 },
    { name: "minecraft:lava", displayName: "Lava", amount: 384_000 },
    { name: "mekanism:hydrogen", displayName: "Hydrogen", amount: 96_500 },
    { name: "industrialforegoing:latex", displayName: "Latex", amount: 12_000 },
  ];
  energy = 0.82; // fraction
  private seq = 1000;
  private rand = rng(7);

  constructor() {
    this.watch.push(
      { name: "minecraft:iron_ingot", min: 0, below: false },
      { name: "ae2:fluix_crystal", min: 2_000, below: true },
      { name: "minecraft:diamond", min: 1_000, below: false },
      { name: "minecraft:ender_pearl", min: 512, below: true },
      { name: "minecraft:redstone", min: 10_000, below: false },
    );
    // keep iron just above its minimum so the sim crosses it soon
    const iron = this.items.get("minecraft:iron_ingot")!;
    iron.count = 52_340;
    this.watch[0].min = 50_000;
    this.jobs.push(
      { jobId: "1001", name: "ae2:logic_processor", displayName: "Logic Processor", count: 64, status: "running", progress: 0.45, speed: 0.03 },
      { jobId: "1000", name: "minecraft:glass", displayName: "Glass", count: 512, status: "done", progress: 1, speed: 0 },
    );
  }

  get stats() {
    let total = 0;
    for (const i of this.items.values()) total += i.count;
    return { total, types: [...this.items.values()].filter((i) => i.count > 0).length, capacity: 8_388_608 };
  }

  watchRows() {
    return this.watch.map((w) => {
      const it = this.items.get(w.name);
      return {
        name: w.name,
        displayName: it?.displayName ?? w.name,
        count: it?.count ?? 0,
        min: w.min,
        ...(w.target !== undefined ? { target: w.target, autoCraft: !!w.autoCraft, state: w.state ?? "idle" } : {}),
      };
    });
  }

  private jobRow(j: Job) {
    return {
      jobId: j.jobId,
      name: j.name,
      displayName: j.displayName,
      count: j.count,
      status: j.status,
      progress: Math.round(j.progress * 100) / 100,
      notify: !!j.notify,
      source: j.source ?? "dashboard",
      ...(j.error ? { error: j.error } : {}),
    };
  }
  jobRows() {
    return this.jobs.slice(0, 8).map((j) => this.jobRow(j));
  }

  // ------------------------------------------------- Storage card v2 (docs/SPEC.md)
  private row(it: Item) {
    return { name: it.name, displayName: it.displayName, count: it.count, craftable: it.craftable, favorite: this.favorites.has(it.name) };
  }
  browse(p: { sort?: string; desc?: boolean; filter?: string; q?: string; offset?: number; limit?: number }) {
    const q = String(p.q ?? "").toLowerCase().trim();
    let list = [...this.items.values()].filter((i) => i.count > 0 || i.craftable);
    if (p.filter === "craftable") list = list.filter((i) => i.craftable);
    if (p.filter === "favorites") list = list.filter((i) => this.favorites.has(i.name));
    if (p.filter !== "craftable") list = list.filter((i) => i.count > 0 || this.favorites.has(i.name));
    if (q) list = list.filter((i) => `${i.name} ${i.displayName}`.toLowerCase().includes(q));
    const mod = (n: string) => n.split(":")[0];
    const dir = p.desc === false ? 1 : -1;
    const sorts: Record<string, (a: Item, b: Item) => number> = {
      count: (a, b) => dir * (a.count - b.count) || a.displayName.localeCompare(b.displayName),
      name: (a, b) => -dir * a.displayName.localeCompare(b.displayName),
      mod: (a, b) => -dir * mod(a.name).localeCompare(mod(b.name)) || a.displayName.localeCompare(b.displayName),
    };
    list.sort((a, b) => Number(this.favorites.has(b.name)) - Number(this.favorites.has(a.name)) || (sorts[p.sort ?? "count"] ?? sorts.count)(a, b));
    const offset = Math.max(0, Math.floor(p.offset ?? 0));
    const limit = Math.max(1, Math.min(200, Math.floor(p.limit ?? 100)));
    return { rows: list.slice(offset, offset + limit).map((i) => this.row(i)), total: list.length, offset };
  }
  craftables(p: { q?: string; offset?: number; limit?: number }) {
    return this.browse({ ...p, filter: "craftable", sort: "name", desc: false });
  }
  fluidRows(p: { offset?: number; limit?: number }) {
    const offset = Math.max(0, Math.floor(p.offset ?? 0));
    return { rows: this.fluids.slice(offset, offset + Math.min(200, p.limit ?? 100)), total: this.fluids.length };
  }
  itemDoc(name: string) {
    const it = this.items.get(name);
    if (!it) return null;
    const w = this.watch.find((x) => x.name === name);
    // a deterministic 24 h history ending at the current count (48 points)
    const r = rng(name.length * 31 + name.charCodeAt(0));
    const history: number[] = [];
    let v = it.count;
    for (let i = 0; i < 48; i++) {
      history.unshift(Math.max(0, Math.round(v)));
      v = v * (1 + (r() - 0.48) * 0.06) + (r() - 0.5) * it.count * 0.02;
    }
    history[history.length - 1] = it.count;
    return {
      ...this.row(it),
      mod: name.split(":")[0],
      watch: w ? { min: w.min, target: w.target ?? w.min, autoCraft: !!w.autoCraft, state: w.state ?? "idle", failures: w.failures ?? 0 } : null,
      jobs: this.jobs.filter((j) => j.name === name).slice(0, 5).map((j) => this.jobRow(j)),
      history,
    };
  }
  cpus() {
    const running = this.jobs.filter((j) => j.status === "running");
    return {
      cpus: Array.from({ length: CPUS }, (_, i) => {
        const j = running[i];
        return {
          id: `cpu-${i + 1}`,
          name: i === 0 ? "Main CPU" : `CPU ${i + 1}`,
          coProcessors: i === 0 ? 4 : 1,
          storage: i === 0 ? 65_536 : 16_384,
          busy: !!j,
          job: j ? { name: j.name, displayName: j.displayName, count: j.count, progress: Math.round(j.progress * 100) / 100, elapsedMs: Date.now() - (j.started ?? Date.now()) } : null,
        };
      }),
      tasks: this.jobs.filter((j) => j.status === "running" || j.status === "queued").map((j) => ({ id: j.jobId, name: j.name, displayName: j.displayName, count: j.count, progress: Math.round(j.progress * 100) / 100, own: true })),
    };
  }
  cancel(jobId: string): { ok: boolean; error?: string } {
    const j = this.jobs.find((x) => x.jobId === jobId.replace(/^cpu-\d+:/, ""));
    if (!j || (j.status !== "running" && j.status !== "queued")) return { ok: false, error: "unknown_item" };
    j.status = "cancelled";
    return { ok: true };
  }
  favorite(name: string, on: boolean): { ok: boolean; error?: string } {
    if (!this.items.has(name)) return { ok: false, error: "unknown_item" };
    if (on && this.favorites.size >= 64 && !this.favorites.has(name)) return { ok: false, error: "invalid" };
    if (on) this.favorites.add(name);
    else this.favorites.delete(name);
    return { ok: true };
  }
  autostock(p: { name?: string; min?: number; target?: number; autoCraft?: boolean }): { ok: boolean; error?: string } {
    const name = String(p.name ?? "");
    if (!this.items.has(name)) return { ok: false, error: "unknown_item" };
    const min = Math.floor(Number(p.min));
    const target = Math.floor(Number(p.target));
    if (!Number.isFinite(min) || !Number.isFinite(target) || min < 0 || target < min || target > 2 ** 31 - 1) return { ok: false, error: "invalid" };
    let w = this.watch.find((x) => x.name === name);
    if (!w) {
      if (this.watch.length >= 32) return { ok: false, error: "invalid" };
      w = { name, min, below: false };
      this.watch.push(w);
    }
    w.min = min;
    w.target = target;
    w.autoCraft = !!p.autoCraft;
    w.failures = 0;
    w.state = "idle";
    w.cooldownUntil = 0;
    return { ok: true };
  }
  /** Craft plan: two ingredients per item, made up but stable. */
  preview(name: string, count: number) {
    const it = this.items.get(name);
    if (!it) return { ok: false as const, error: "unknown_item" };
    if (!it.craftable) return { ok: false as const, error: "not_craftable" };
    if (!Number.isInteger(count) || count < 1 || count > MAX_CRAFT) return { ok: false as const, error: "invalid" };
    const pool = [...this.items.values()].filter((i) => i.name !== name);
    const h = [...name].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);
    // stable picks, moved on to the next item that is in stock (or craftable) for this amount
    const pick = (start: number, need: number, not?: Item) => {
      for (let i = 0; i < pool.length; i++) {
        const g = pool[(start + i) % pool.length];
        if (g !== not && (g.craftable || g.count >= need)) return g;
      }
      return pool[start % pool.length];
    };
    const first = pick(h, count * 2);
    const ings = [first, pick(h >>> 5, count, first)];
    const missingAll = name === "ae2:singularity" || name === "enderio:octadic_capacitor";
    const entries = ings.map((g, i) => {
      const need = count * (i === 0 ? 2 : 1);
      const stored = Math.min(g.count, need);
      const toCraft = g.craftable ? need - stored : 0;
      const missing = missingAll ? need : g.craftable ? 0 : need - stored;
      return { name: g.name, displayName: g.displayName, stored, toCraft, missing };
    });
    entries.unshift({ name, displayName: it.displayName, stored: 0, toCraft: count, missing: 0 });
    const busy = this.jobs.filter((j) => j.status === "running").length;
    return {
      ok: true as const,
      plan: {
        name,
        count,
        missing: entries.some((e) => e.missing > 0),
        bytes: count * 8 + 1024,
        entries,
        cpu: { available: CPUS - busy, enough: CPUS - busy > 0 },
        timeMs: null,
      },
    };
  }

  craft(name: string, count: number, notify = false, source: Job["source"] = "dashboard"): CraftResult {
    const it = this.items.get(name);
    if (!it) return { ok: false, error: "unknown_item" };
    if (!it.craftable) return { ok: false, error: "not_craftable" };
    if (!Number.isInteger(count) || count < 1 || count > MAX_CRAFT) return { ok: false, error: "too_many" };
    if (name === "ae2:singularity" || name === "enderio:octadic_capacitor") return { ok: false, error: "missing_resources" };
    if (this.jobs.filter((j) => j.status === "running" || j.status === "queued").length >= CPUS + 1) return { ok: false, error: "no_cpu" };
    const running = this.jobs.filter((j) => j.status === "running").length;
    const job: Job = {
      jobId: String(++this.seq),
      name,
      displayName: it.displayName,
      count,
      status: running >= CPUS ? "queued" : "running",
      progress: 0,
      speed: Math.min(0.25, 6 / Math.max(6, count)),
      notify,
      source,
      started: Date.now(),
    };
    this.jobs.unshift(job);
    if (this.jobs.length > 12) this.jobs.length = 12;
    return { ok: true, jobId: job.jobId };
  }

  /** Advances jobs and consumption. Returns events for alerts. */
  tick() {
    const events: { level: "info" | "warn"; text: string }[] = [];
    for (const j of this.jobs) {
      if (j.status !== "running") continue;
      j.progress = Math.min(1, j.progress + j.speed);
      if (j.progress >= 1) {
        j.status = "done";
        const it = this.items.get(j.name);
        if (it) it.count += j.count;
        if (j.notify || j.source !== "autostock") events.push({ level: "info", text: `Crafted ${j.count.toLocaleString("en-US")}× ${j.displayName}` });
      }
    }
    // start queued jobs on free CPUs
    let free = CPUS - this.jobs.filter((j) => j.status === "running").length;
    for (const j of [...this.jobs].reverse()) if (free > 0 && j.status === "queued") (j.status = "running"), free--;

    // the base keeps eating iron and redstone
    const iron = this.items.get("minecraft:iron_ingot")!;
    iron.count = Math.max(0, iron.count - Math.round(200 + this.rand() * 300));
    const red = this.items.get("minecraft:redstone")!;
    red.count = Math.max(0, red.count - Math.round(this.rand() * 900));
    this.items.get("minecraft:cobblestone")!.count += Math.round(this.rand() * 2_000);
    this.energy = Math.min(1, Math.max(0.2, this.energy + (this.rand() - 0.5) * 0.02));

    // auto-stock (level maintainer)
    const now = Date.now();
    for (const w of this.watch) {
      if (!w.autoCraft || w.state === "paused") continue;
      const it = this.items.get(w.name);
      if (!it?.craftable) continue;
      const active = this.jobs.some((j) => j.name === w.name && (j.status === "running" || j.status === "queued"));
      if (active) {
        w.state = "crafting";
        continue;
      }
      if (it.count >= w.min) {
        w.state = "idle";
        continue;
      }
      if ((w.cooldownUntil ?? 0) > now) continue;
      const r = this.craft(w.name, Math.min((w.target ?? w.min) - it.count, 64), false, "autostock");
      w.cooldownUntil = now + 30_000;
      if (r.ok) w.state = "crafting";
      else if (r.error === "no_cpu") w.state = "waiting_cpu";
      else {
        w.failures = (w.failures ?? 0) + 1;
        w.state = w.failures >= 3 ? "paused" : "missing";
      }
    }
    for (const w of this.watch) {
      const count = this.items.get(w.name)?.count ?? 0;
      const below = count < w.min;
      if (below && !w.below) events.push({ level: "warn", text: `${this.items.get(w.name)?.displayName ?? w.name} below minimum: ${count.toLocaleString("en-US")} / ${w.min.toLocaleString("en-US")}` });
      w.below = below;
    }
    return events;
  }

  watchOp(op: { op: string; name?: string; min?: number }): { ok: boolean; error?: string } {
    const name = String(op.name ?? "");
    const i = this.watch.findIndex((w) => w.name === name);
    if (op.op === "add") {
      if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(name)) return { ok: false, error: "invalid_value" };
      if (i >= 0) return { ok: false, error: "already_watched" };
      if (this.watch.length >= 32) return { ok: false, error: "too_many" };
      const min = Math.max(0, Math.floor(op.min ?? 0));
      this.watch.push({ name, min, below: (this.items.get(name)?.count ?? 0) < min });
      return { ok: true };
    }
    if (i < 0) return { ok: false, error: "unknown_item" };
    if (op.op === "remove") {
      this.watch.splice(i, 1);
      return { ok: true };
    }
    if (op.op === "set_min") {
      this.watch[i].min = Math.max(0, Math.floor(op.min ?? 0));
      return { ok: true };
    }
    return { ok: false, error: "invalid_value" };
  }
}
