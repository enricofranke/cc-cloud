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
  status: "queued" | "running" | "done" | "failed";
  progress: number;
  /** progress per tick */
  speed: number;
}
export interface Watch {
  name: string;
  min: number;
  below: boolean;
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

  const metals: [string, string][] = [
    ["minecraft", "iron"], ["minecraft", "gold"], ["minecraft", "copper"], ["alltheores", "tin"], ["alltheores", "lead"],
    ["alltheores", "silver"], ["alltheores", "nickel"], ["alltheores", "uranium"], ["alltheores", "osmium"], ["alltheores", "zinc"],
    ["alltheores", "aluminum"], ["alltheores", "platinum"], ["alltheores", "iridium"], ["allthemodium", "allthemodium"],
    ["allthemodium", "vibranium"], ["allthemodium", "unobtainium"], ["mekanism", "steel"], ["thermal", "bronze"],
    ["thermal", "electrum"], ["thermal", "invar"], ["thermal", "constantan"], ["thermal", "signalum"], ["thermal", "lumium"], ["thermal", "enderium"],
  ];
  const forms = ["ingot", "nugget", "block", "dust", "plate", "gear", "rod", "raw", "ore"];
  for (const [mod, metal] of metals)
    for (const form of forms) {
      const name = form === "raw" ? `${mod}:raw_${metal}` : form === "block" ? `${mod}:${metal}_block` : `${mod}:${metal}_${form}`;
      const display = form === "raw" ? `Raw ${title(metal)}` : form === "block" ? `Block of ${title(metal)}` : `${title(metal)} ${title(form)}`;
      const scale = form === "ingot" ? 60_000 : form === "nugget" ? 9_000 : form === "block" ? 4_000 : form === "ore" ? 900 : 2_500;
      const rare = ["allthemodium", "vibranium", "unobtainium", "iridium"].includes(metal) ? 0.02 : 1;
      add(name, display, Math.round(rand() ** 2 * scale * rare), ["plate", "gear", "rod"].includes(form) && rand() > 0.35);
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
    ["ae2:fluix_cable", "Fluix Glass Cable", 812, true],
    ["ae2:cell_component_64k", "64k ME Storage Component", 4, true],
    ["ae2:singularity", "Singularity", 0, true],
    ["mekanism:basic_control_circuit", "Basic Control Circuit", 140, true],
    ["mekanism:advanced_control_circuit", "Advanced Control Circuit", 32, true],
    ["mekanism:ultimate_control_circuit", "Ultimate Control Circuit", 0, true],
    ["mekanism:hdpe_pellet", "HDPE Pellet", 2_048],
    ["create:andesite_alloy", "Andesite Alloy", 6_120, true],
    ["create:brass_ingot", "Brass Ingot", 2_430],
    ["create:cogwheel", "Cogwheel", 412, true],
    ["create:shaft", "Shaft", 980, true],
    ["computercraft:computer_advanced", "Advanced Computer", 6, true],
    ["computercraft:wired_modem", "Wired Modem", 18, true],
    ["computercraft:cable", "Networking Cable", 340, true],
    ["cccloud:cloud_core", "Cloud Core", 3, true],
    ["mysticalagriculture:inferium_essence", "Inferium Essence", 1_203_441],
    ["mysticalagriculture:prudentium_essence", "Prudentium Essence", 48_220, true],
  ];
  for (const [n, d, c, cr] of misc) add(n, d, c, cr === true);

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
  energy = 0.82; // fraction
  private seq = 1000;
  private rand = rng(7);

  constructor() {
    this.watch.push(
      { name: "minecraft:iron_ingot", min: 0, below: false },
      { name: "ae2:fluix_crystal", min: 2_000, below: true },
      { name: "minecraft:diamond", min: 1_000, below: false },
      { name: "mekanism:basic_control_circuit", min: 256, below: true },
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
      return { name: w.name, displayName: it?.displayName ?? w.name, count: it?.count ?? 0, min: w.min };
    });
  }

  jobRows() {
    return this.jobs.slice(0, 8).map(({ jobId, name, displayName, count, status, progress }) => ({ jobId, name, displayName, count, status, progress: Math.round(progress * 100) / 100 }));
  }

  craft(name: string, count: number): CraftResult {
    const it = this.items.get(name);
    if (!it) return { ok: false, error: "unknown_item" };
    if (!it.craftable) return { ok: false, error: "not_craftable" };
    if (!Number.isInteger(count) || count < 1 || count > MAX_CRAFT) return { ok: false, error: "too_many" };
    if (name === "ae2:singularity" || name === "mekanism:ultimate_control_circuit") return { ok: false, error: "missing_resources" };
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
        events.push({ level: "info", text: `Crafted ${j.count.toLocaleString("en-US")}× ${j.displayName}` });
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
