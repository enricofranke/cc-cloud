// DEV-ONLY: sample compat cards mirroring the Java builders in src/main/java/ai/ezfox/cccloud/compat (for screenshots).
//
// Every entry is one card the mod can build: the widget ids, types, labels and opts are written exactly as
// CardWidgets / WidgetFactory serialize them for the browser (Widget.toJson), with plausible sample values.
// Device cards are uplink sections in direct mode; mainframe cards are restricted sections (source "mainframe").

export interface SampleWidget {
  id: string;
  type: string;
  label?: string;
  opts?: Record<string, unknown>;
  value?: unknown;
  history?: number[];
}
export interface SampleCard {
  key: string;
  mod: string;
  title: string;
  template: string;
  restricted?: boolean;
  widgets: SampleWidget[];
  extra?: Record<string, unknown>;
}

type Opts = Record<string, unknown>;

// ---- widget helpers (same defaults and option order as CardWidgets + WidgetFactory) ---------------------------------

/** WidgetFactory writes the type-specific keys first, then the common layout keys. */
const KEY_ORDER = [
  "pin",
  "history",
  "confirm",
  "min",
  "max",
  "step",
  "unit",
  "warn",
  "crit",
  "points",
  "interval",
  "style",
  "onColor",
  "offColor",
  "options",
  "maxLength",
  "placeholder",
  "page",
  "group",
  "span",
  "order",
  "color",
  "icon",
];

function sortOpts(raw: Opts): Opts {
  const out: Opts = {};
  for (const key of KEY_ORDER) if (raw[key] !== undefined && raw[key] !== null) out[key] = raw[key];
  return out;
}

function widget(type: string, id: string, label: string, opts: Opts, value?: unknown): SampleWidget {
  const w: SampleWidget = { id, type, label, opts: sortOpts(opts) };
  if (value !== undefined) w.value = value;
  return w;
}

/** CardWidgets.gauge: min/max/unit (unit skipped when null). */
function gauge(id: string, label: string, min: number, max: number, unit: string | null, extra: Opts, value: number): SampleWidget {
  return widget("gauge", id, label, { min, max, unit, ...extra }, value);
}

/** CardWidgets.percent: 0–100 % gauge. */
function percent(id: string, label: string, extra: Opts, value: number): SampleWidget {
  return gauge(id, label, 0, 100, "%", extra, value);
}

function progress(id: string, label: string, min: number, max: number, unit: string | null, extra: Opts, value: number): SampleWidget {
  return widget("progress", id, label, { min, max, unit, ...extra }, value);
}

/** CardWidgets.label: the caption is the widget label, the value is the text (starts as "–"). */
function label(id: string, caption: string, value: string, extra: Opts = {}): SampleWidget {
  return widget("label", id, caption, extra, value);
}

function indicator(id: string, label: string, onColor: string, offColor: string, value: boolean): SampleWidget {
  return widget("indicator", id, label, { onColor, offColor }, value);
}

/** CardWidgets.history: 60-minute chart (300 points, one every 12 s); value = the newest point. */
function chart(id: string, label: string, min: number | null, max: number | null, unit: string | null, points: number[]): SampleWidget {
  const w = widget("chart", id, label, { min: min ?? undefined, max: max ?? undefined, unit, points: 300, interval: 12 }, points[points.length - 1]);
  w.history = points;
  return w;
}

function toggle(id: string, label: string, extra: Opts, value: boolean): SampleWidget {
  return widget("toggle", id, label, extra, value);
}

function button(id: string, label: string, extra: Opts): SampleWidget {
  return widget("button", id, label, extra);
}

/** CardWidgets.select: options arrive as an array in the browser; value = the current option. */
function select(id: string, label: string, options: string[], extra: Opts, value: string): SampleWidget {
  return widget("select", id, label, { ...extra, options }, value);
}

function number(id: string, label: string, min: number, max: number, step: number, extra: Opts, value: number): SampleWidget {
  return widget("number", id, label, { min, max, step, ...extra }, value);
}

/** CardWidgets.itemSearch: storage search without crafting (max 1). */
function itemSearch(id: string, label: string): SampleWidget {
  return widget("item_search", id, label, { max: 1 });
}

/** Deterministic chart samples: a slow wave plus a little pseudo-noise, clamped and rounded to 0.1. */
function series(count: number, base: number, amplitude: number, period: number, seed: number, min = -Infinity, max = Infinity): number[] {
  const out: number[] = [];
  let s = seed;
  for (let i = 0; i < count; i++) {
    s = (s * 1103515245 + 12345) % 2147483648;
    const noise = (s / 2147483648 - 0.5) * amplitude * 0.3;
    const v = base + amplitude * Math.sin((i / period) * Math.PI * 2 + seed) + noise;
    out.push(Math.round(Math.min(max, Math.max(min, v)) * 10) / 10);
  }
  return out;
}

// ---- shared card parts -------------------------------------------------------------------------------------------------

/** EnergyCard.widgets(): stored gauge, energy label, net flow (or input/output), 60-minute chart. */
function energyBase(stored: number, energy: string, rates: { flow: string } | { input: string; output: string; outputLabel: string }, seed: number): SampleWidget[] {
  const w: SampleWidget[] = [percent("stored", "Stored", { color: "green", icon: "bolt" }, stored), label("energy", "Energy", energy, { span: 2 })];
  if ("flow" in rates) w.push(label("flow", "Net flow", rates.flow));
  else w.push(label("input", "Input", rates.input), label("output", rates.outputLabel, rates.output));
  w.push(chart("history", "Stored (60 min)", 0, 100, "%", series(60, stored, 6, 40, seed, 0, 100)));
  return w;
}

const POWAH_REDSTONE = ["Ignore redstone", "Active with signal", "Active without signal"];
const ENDERIO_REDSTONE = ["Always active", "Active with signal", "Active without signal", "Never active"];

// ---- claims / waystones overlays ---------------------------------------------------------------------------------------

/** FTB Chunks claim overlay (chunk coordinates; a base around blocks x 0..200, z -100..100). */
const CLAIM_CHUNKS: { dim: string; x: number; z: number; forced?: boolean }[] = (() => {
  const cells: [number, number, boolean][] = [
    [0, -2, false],
    [0, -1, false],
    [0, 0, false],
    [1, -3, false],
    [1, -2, false],
    [1, -1, true],
    [1, 0, true],
    [1, 1, false],
    [2, -2, false],
    [2, -1, true],
    [2, 0, false],
    [2, 1, false],
    [3, -1, false],
    [3, 0, false],
    [4, 0, false],
    [5, 0, false],
    [6, 0, false],
    [7, -1, false],
    [7, 0, false],
    [8, 2, false],
    [9, 2, false],
    [12, 5, false],
  ];
  return cells.map(([x, z, forced]) => (forced ? { dim: "minecraft:overworld", x, z, forced: true } : { dim: "minecraft:overworld", x, z }));
})();

const WAYSTONES: { name: string; dim: string; x: number; y: number; z: number; kind: string; global?: boolean }[] = [
  { name: "Base", dim: "minecraft:overworld", x: 64, y: 71, z: -8, kind: "waystone", global: true },
  { name: "Desert Temple", dim: "minecraft:overworld", x: -284, y: 68, z: 212, kind: "sandy_waystone" },
  { name: "Farm District", dim: "minecraft:overworld", x: 151, y: 66, z: 87, kind: "mossy_waystone" },
  { name: "Spawn", dim: "minecraft:overworld", x: -12, y: 70, z: 4, kind: "sharestone", global: true },
  { name: "Nether Hub", dim: "minecraft:the_nether", x: 18, y: 72, z: -12, kind: "waystone", global: true },
  { name: "Quartz Mines", dim: "minecraft:the_nether", x: -37, y: 41, z: 26, kind: "waystone" },
];

function waystoneRow(i: number): string {
  const it = WAYSTONES[i];
  if (!it) return "–";
  return `${it.name}${it.global ? " (global)" : ""} – ${it.dim.slice(it.dim.indexOf(":") + 1)} ${it.x}, ${it.y}, ${it.z}`;
}

// ---- the cards ---------------------------------------------------------------------------------------------------------

export const COMPAT_CARDS: SampleCard[] = [
  // Generic cards
  {
    key: "energy",
    mod: "generic",
    title: "Battery Box",
    template: "energy",
    widgets: energyBase(62.4, "3.12 M / 5.00 M FE", { flow: "+1.2 k FE/t" }, 1),
  },
  {
    key: "tank",
    mod: "generic",
    title: "Dark Tank",
    template: "tank",
    widgets: [percent("fill", "Filled", { color: "cyan" }, 37.5), label("fluid", "Fluid", "Water"), label("amount", "Amount", "6.00 k / 16.0 k mB")],
  },
  {
    key: "storage",
    mod: "functionalstorage",
    title: "Storage Controller",
    template: "functionalstorage_storage",
    widgets: [
      percent("storage", "Storage used", { warn: 85, crit: 95 }, 71.3),
      label("items", "Total items", "184 k / 258 k"),
      label("types", "Item types", "142"),
      label("slots", "Slots used", "151 / 256"),
      itemSearch("search", "Search storage"),
    ],
  },

  // Powah
  {
    key: "powah_cell",
    mod: "powah",
    title: "Energy Cell Hardened",
    template: "powah_cell",
    widgets: [
      ...energyBase(84.2, "4.21 M / 5.00 M FE", { flow: "-320 FE/t" }, 2),
      label("tier", "Tier", "Hardened"),
      select("redstone", "Redstone mode", POWAH_REDSTONE, { confirm: true }, "Ignore redstone"),
    ],
  },
  {
    key: "powah_generator",
    mod: "powah",
    title: "Thermo Generator Niotic",
    template: "powah_generator",
    widgets: [
      ...energyBase(48, "240 k / 500 k FE", { flow: "+180 FE/t" }, 3),
      indicator("running", "Generating", "green", "gray", true),
      label("generation", "Generation", "180 FE/t"),
      select("redstone", "Redstone mode", POWAH_REDSTONE, { confirm: true }, "Ignore redstone"),
    ],
  },
  {
    key: "powah_reactor",
    mod: "powah",
    title: "Powah Reactor (Nitro)",
    template: "powah_reactor",
    widgets: [
      ...energyBase(91.6, "458 M / 500 M FE", { input: "–", output: "12.4 k FE/t", outputLabel: "Generation" }, 4),
      indicator("running", "Running", "green", "gray", true),
      indicator("built", "Multiblock complete", "green", "red", true),
      gauge("temperature", "Temperature", 0, 1000, "°C", { warn: 750, crit: 900, icon: "flame" }, 684),
      progress("fuel", "Fuel", 0, 1000, "mB", {}, 732),
      progress("carbon", "Carbon", 0, 100, "%", {}, 58),
      progress("redstone_fuel", "Redstone", 0, 100, "%", {}, 41),
      label("consumption", "Fuel use", "0.12 mB/t"),
      toggle("gen_mode", "Generate only on demand", { confirm: true }, false),
      select("redstone", "Redstone mode", POWAH_REDSTONE, { confirm: true }, "Ignore redstone"),
    ],
  },

  // Ender IO
  {
    key: "enderio_machine",
    mod: "enderio",
    title: "Stirling Generator",
    template: "enderio_machine",
    widgets: [
      ...energyBase(33.3, "33.3 k / 100 k FE", { flow: "+40 FE/t" }, 5),
      indicator("active", "Active", "green", "gray", true),
      label("generation", "Generation", "40 FE/t"),
      select("redstone", "Redstone mode", ENDERIO_REDSTONE, { confirm: true }, "Always active"),
    ],
  },
  {
    key: "enderio_solar",
    mod: "enderio",
    title: "Energetic Photovoltaic Module",
    template: "enderio_solar",
    widgets: [...energyBase(100, "64.0 k / 64.0 k FE", { flow: "0 FE/t" }, 6), label("generation", "Generation (this module)", "16 FE/t")],
  },

  // Oritech
  {
    key: "oritech_storage",
    mod: "oritech",
    title: "Large Storage Block",
    template: "oritech_storage",
    widgets: energyBase(56.8, "28.4 M / 50.0 M FE", { input: "2.40 k FE/t", output: "1.85 k FE/t", outputLabel: "Output" }, 7),
  },
  {
    key: "oritech_generator",
    mod: "oritech",
    title: "Basic Generator Block",
    template: "oritech_generator",
    widgets: [...energyBase(22.5, "11.3 k / 50.0 k FE", { flow: "+64 FE/t" }, 8), indicator("running", "Generating", "green", "gray", true), label("generation", "Generation", "64 FE/t")],
  },

  // Energized Power
  {
    key: "energizedpower_machine",
    mod: "energizedpower",
    title: "Coal Engine",
    template: "energizedpower_machine",
    widgets: [
      ...energyBase(78.1, "15.6 k / 20.0 k FE", { flow: "+60 FE/t" }, 9),
      indicator("running", "Working", "green", "gray", true),
      select("redstone", "Redstone mode", ["Ignore redstone", "Active with signal", "Active without signal"], { confirm: true }, "Ignore redstone"),
    ],
  },

  // Generator Galore
  {
    key: "generatorgalore_generator",
    mod: "generatorgalore",
    title: "Iron Generator",
    template: "generatorgalore_generator",
    widgets: [
      ...energyBase(45.2, "22.6 k / 50.0 k FE", { flow: "+80 FE/t" }, 10),
      indicator("running", "Generating", "green", "gray", true),
      label("generation", "Generation", "80 FE/t"),
      progress("burn", "Fuel burn", 0, 100, "%", {}, 63.5),
    ],
  },

  // Energy Meter
  {
    key: "energymeter",
    mod: "energymeter",
    title: "Energy Meter",
    template: "energymeter",
    widgets: [
      label("flow", "Flow", "2.56 k FE/t", { icon: "bolt" }),
      label("status", "Status", "Connected"),
      chart("history", "Flow (60 min)", null, null, "FE/t", series(60, 2400, 600, 30, 11, 0)),
      label("total", "Transferred", "1.84 G FE"),
      label("limit", "Transfer limit", "Unlimited"),
      select("mode", "Transfer mode", ["Split", "Transfer", "Consume"], { confirm: true }, "Transfer"),
    ],
  },

  // Hostile Neural Networks
  {
    key: "hostilenetworks_chamber",
    mod: "hostilenetworks",
    title: "Simulation Chamber",
    template: "hostilenetworks_chamber",
    widgets: [
      label("status", "Status", "Running", { span: 2 }),
      indicator("running", "Simulating", "green", "gray", true),
      indicator("output_full", "Output full", "red", "gray", false),
      label("model", "Data model", "Blaze"),
      label("data", "Data", "142 / 254 (68 % accuracy)"),
      progress("progress", "Cycle", 0, 100, "%", {}, 47),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 88.4),
    ],
  },

  // Productive Bees
  {
    key: "productivebees_hive",
    mod: "productivebees",
    title: "Advanced Oak Beehive",
    template: "productivebees_hive",
    widgets: [
      progress("honey", "Honey level", 0, 5, null, { icon: "drop" }, 3),
      label("bees", "Bees inside", "2 / 3"),
      percent("output", "Output", { warn: 80, crit: 95 }, 42.7),
      indicator("output_full", "Output full", "red", "gray", false),
    ],
  },
  {
    key: "productivebees_centrifuge",
    mod: "productivebees",
    title: "Powered Centrifuge",
    template: "productivebees_centrifuge",
    widgets: [
      indicator("running", "Running", "green", "gray", true),
      percent("output", "Output", { warn: 80, crit: 95 }, 86.1),
      indicator("output_full", "Output full", "red", "gray", false),
      label("fluid", "Honey tank", "3250 / 10000 mB"),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 64.9),
    ],
  },

  // MFFS (pin: mffsRequirePin defaults to true)
  {
    key: "mffs_projector",
    mod: "mffs",
    title: "Force Field Projector",
    template: "mffs_projector",
    widgets: [
      toggle("field", "Force field", { confirm: true, pin: true, icon: "shield", color: "cyan" }, true),
      label("status", "Status", "Projecting", { span: 2 }),
      percent("fortron", "Fortron", { color: "cyan" }, 73.5),
      label("fortron_text", "Fortron stored", "36.8 k / 50.0 k mB"),
      label("blocks", "Field blocks", "1842"),
      indicator("redstone_held", "Held on by redstone", "yellow", "gray", false),
    ],
  },

  // ME Requester
  {
    key: "merequester",
    mod: "merequester",
    title: "ME Requester",
    template: "merequester",
    widgets: [
      toggle("req0_on", "Iron Ingot", { order: 0 }, true),
      number("req0_amount", "Keep in stock", 1, 2147483647, 1, { order: 1 }, 1024),
      label("req0_status", "Status", "Stocked", { order: 2 }),
      toggle("req1_on", "Basic Processor", { order: 3 }, true),
      number("req1_amount", "Keep in stock", 1, 2147483647, 1, { order: 4 }, 256),
      label("req1_status", "Status", "Crafting", { order: 5 }),
      toggle("req2_on", "Fluix Crystal", { order: 6 }, false),
      number("req2_amount", "Keep in stock", 1, 2147483647, 1, { order: 7 }, 512),
      label("req2_status", "Status", "Disabled", { order: 8 }),
    ],
  },

  // Advanced AE
  {
    key: "advancedae_quantum",
    mod: "advanced_ae",
    title: "Quantum Computer",
    template: "advancedae_quantum",
    widgets: [
      label("status", "Status", "Online – Quantum Core", { span: 2 }),
      label("storage", "Storage", "268 M bytes free"),
      label("threads", "Co-processors", "64"),
      label("active", "Jobs running", "3"),
      label("job1", "Job 1", "64× Iron Ingot – 42 %, 1 min 3 s", { span: 2 }),
      label("job2", "Job 2", "16× Engineering Processor – 75 %, 2 min 18 s", { span: 2 }),
      label("job3", "Job 3", "1.00 k× Fluix Crystal – 8 %, 12 s", { span: 2 }),
      label("job4", "Job 4", "–", { span: 2 }),
      label("job5", "Job 5", "–", { span: 2 }),
      label("job6", "Job 6", "–", { span: 2 }),
      button("cancel_all", "Cancel all jobs", { confirm: true, color: "red" }),
    ],
  },

  // QuarryPlus (kind quarryplus_adv_quarry / "Chunk Destroyer" has the same widgets)
  {
    key: "quarryplus_quarry",
    mod: "quarryplus",
    title: "Quarry",
    template: "quarryplus_quarry",
    widgets: [
      label("status", "Status", "Mining", { span: 2 }),
      indicator("working", "Working", "green", "gray", true),
      indicator("output_full", "Output blocked", "red", "gray", false),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 67.2),
      label("area", "Area", "62 × 62 down to Y -59"),
      progress("depth", "Depth", 0, 100, "%", {}, 38),
      label("buffer", "Buffered items", "412"),
    ],
  },

  // Apothic Spawners
  {
    key: "apothicspawners_spawner",
    mod: "apothic_spawners",
    title: "Spawner",
    template: "apothicspawners_spawner",
    widgets: [
      label("mob", "Mob", "Blaze", { span: 2 }),
      indicator("active", "Spawning", "green", "gray", true),
      indicator("redstone_control", "Needs redstone", "yellow", "gray", false),
      progress("next", "Next spawn", 0, 100, "%", {}, 64),
      label("delay", "Spawn delay", "100–300 ticks"),
      label("count", "Mobs per spawn", "6"),
      label("nearby", "Max nearby mobs", "16"),
      label("range", "Player range", "32 blocks"),
      label("upgrades", "Upgrades", "Ignore light, Silent, Echoing 2", { span: 2 }),
    ],
  },

  // Gateways (mainframe card)
  {
    key: "gateways",
    mod: "gateways",
    title: "Gateways",
    template: "gateways",
    restricted: true,
    widgets: [
      indicator("active", "Gate open", "red", "gray", true),
      label("count", "Open gates", "2"),
      label("gate1", "Gate 1", "Emerald Grove: wave 3 / 5, 7 enemies, 2 lives – 38 m", { span: 2 }),
      label("gate2", "Gate 2", "Blaze Gate: wave 1 / 3 starting, 3 lives – 112 m", { span: 2 }),
      label("gate3", "Gate 3", "–", { span: 2 }),
      label("gate4", "Gate 4", "–", { span: 2 }),
      label("gate5", "Gate 5", "–", { span: 2 }),
      label("gate6", "Gate 6", "–", { span: 2 }),
    ],
  },

  // Easy Villagers (breeder / farmer have no "cycle" widget)
  {
    key: "easyvillagers_iron_farm",
    mod: "easy_villagers",
    title: "Iron Farm",
    template: "easyvillagers_iron_farm",
    widgets: [
      label("status", "Status", "Waiting for a golem", { span: 2 }),
      label("villagers", "Villagers", "1"),
      progress("cycle", "Golem cycle", 0, 100, "%", {}, 58),
      percent("output", "Output slots used", { warn: 80, crit: 100 }, 22.2),
      indicator("output_full", "Output full", "red", "gray", false),
    ],
  },

  // Iron Furnaces
  {
    key: "ironfurnaces_furnace",
    mod: "ironfurnaces",
    title: "Diamond Furnace",
    template: "ironfurnaces_furnace",
    widgets: [
      label("status", "Status", "Smelting Raw Iron", { span: 2 }),
      progress("progress", "Smelting", 0, 100, "%", {}, 72),
      label("fuel", "Fuel left", "4 min 12 s"),
      label("input", "Input", "38× Raw Iron"),
      label("output", "Output", "26× Iron Ingot"),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 0),
      select("redstone", "Redstone mode", ["Ignore redstone", "Run without signal", "Run with signal"], { confirm: true }, "Ignore redstone"),
    ],
  },

  // Cobblegen Galore
  {
    key: "cobblegengalore_generator",
    mod: "cobblegengalore",
    title: "Diamond Cobblegen",
    template: "cobblegengalore_generator",
    widgets: [
      label("producing", "Producing", "Cobblestone", { span: 2 }),
      label("speed", "Tier speed", "×16 every 20 ticks"),
      label("buffer", "Buffer", "48 / 64"),
      indicator("full", "Buffer full", "red", "gray", false),
    ],
  },

  // Productive Metalworks
  {
    key: "productivemetalworks_foundry",
    mod: "productivemetalworks",
    title: "Foundry",
    template: "productivemetalworks_foundry",
    widgets: [
      label("status", "Status", "Formed (Energy coil)", { span: 2 }),
      label("fuel", "Fuel", "None"),
      percent("power", "Power", { color: "yellow", icon: "bolt" }, 81.6),
      label("melting", "Items melting", "12"),
      label("molten", "Molten", "Molten Iron 1.44 k mB, Molten Gold 432 mB", { span: 2 }),
    ],
  },

  // Productive Farming
  {
    key: "productivefarming_farm",
    mod: "productivefarming",
    title: "Farm",
    template: "productivefarming_farm",
    widgets: [
      label("status", "Status", "Farming", { span: 2 }),
      label("water", "Water", "7.50 k / 8.00 k mB"),
      percent("output", "Output slots used", { warn: 80, crit: 100 }, 44.4),
      indicator("output_full", "Output full", "red", "gray", false),
    ],
  },

  // Agritech: Evolved (the basic planter has no energy gauge)
  {
    key: "agritechevolved_advanced_planter",
    mod: "agritechevolved",
    title: "Advanced Planter",
    template: "agritechevolved_advanced_planter",
    widgets: [
      label("status", "Status", "Growing", { span: 2 }),
      label("plant", "Plant", "Wheat Seeds"),
      label("soil", "Soil", "Farmland"),
      progress("growth", "Growth", 0, 100, "%", {}, 64),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 92.3),
      indicator("output_full", "Output full", "red", "gray", false),
    ],
  },

  // Mystical Agriculture
  {
    key: "mysticalagriculture_machine",
    mod: "mysticalagriculture",
    title: "Inferium Essence Furnace",
    template: "mysticalagriculture_machine",
    widgets: [
      label("status", "Status", "Working", { span: 2 }),
      progress("progress", "Progress", 0, 100, "%", {}, 55),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 76.4),
      label("fuel", "Fuel left", "12.8 k FE"),
      label("tier", "Upgrade", "Prudentium"),
    ],
  },

  // Railcraft
  {
    key: "railcraft_boiler",
    mod: "railcraft",
    title: "Steam Boiler",
    template: "railcraft_boiler",
    widgets: [
      label("status", "Status", "Making steam – 412 °C", { span: 2 }),
      gauge("temperature", "Temperature", 0, 100, "%", { warn: 90, color: "yellow" }, 82.4),
      percent("water", "Water", { color: "cyan", warn: 30, crit: 20 }, 68.5),
      percent("steam", "Steam", { color: "gray" }, 54.2),
      indicator("burning", "Burning", "yellow", "gray", true),
    ],
  },

  // Super Factory Manager
  {
    key: "sfm_manager",
    mod: "sfm",
    title: "Factory Manager",
    template: "sfm_manager",
    widgets: [
      label("state", "State", "Running", { span: 2 }),
      label("program", "Program", "Ore processing"),
      label("tick_time", "Tick time", "0.42 ms"),
      label("errors", "Errors", "0"),
      label("warnings", "Warnings", "1"),
      label("last_error", "First error", "–", { span: 2 }),
      label("pulses", "Queued redstone pulses", "0"),
    ],
  },

  // Modular Routers
  {
    key: "modularrouters_router",
    mod: "modularrouters",
    title: "Modular Router",
    template: "modularrouters_router",
    widgets: [
      label("status", "Status", "Running", { span: 2 }),
      label("modules", "Modules", "3"),
      label("speed", "Speed", "every 4 ticks, 16 items, eco"),
      label("buffer", "Buffer", "12× Cobblestone"),
      select("redstone", "Redstone mode", ["Always", "Redstone low", "Redstone high", "Never", "Redstone pulse"], { confirm: true }, "Always"),
    ],
  },

  // Integrated Dynamics
  {
    key: "integrateddynamics_panels",
    mod: "integrateddynamics",
    title: "Display Panels",
    template: "integrateddynamics_panels",
    widgets: [
      label("panel_north", "Panel (north)", "18432 (Integer)", { span: 2 }),
      label("panel_east", "Panel (east)", "true (Boolean)", { span: 2 }),
      label("panel_up", "Panel (up)", "Iron Ingot (Item)", { span: 2 }),
    ],
  },

  // Waystones (mainframe card with map overlay)
  {
    key: "waystones",
    mod: "waystones",
    title: "Waystones",
    template: "waystones",
    restricted: true,
    widgets: [label("count", "Waystones", String(WAYSTONES.length), { icon: "home" }), ...[1, 2, 3, 4, 5, 6, 7, 8].map((i) => label(`waystone${i}`, `Waystone ${i}`, waystoneRow(i - 1), { span: 2 }))],
    extra: { waystones: { items: WAYSTONES, truncated: false } },
  },

  // FTB Quests (mainframe card)
  {
    key: "quests",
    mod: "ftbquests",
    title: "Quests",
    template: "quests",
    restricted: true,
    widgets: [
      label("team", "Team", "Foxworks", { icon: "star" }),
      progress("progress", "Progress", 0, 100, "%", {}, 37),
      label("chapters", "Chapters done", "4 / 23"),
      label("quests", "Quests done", "312 / 845"),
      label("recent1", "Last completed", "Into the Nether (Getting Started) – 12 min 4 s ago", { span: 2 }),
      label("recent2", "Completed before", "Molecular Assembler (Applied Energistics) – 48 min 31 s ago", { span: 2 }),
      label("recent3", "Completed before", "Basic Generator (Powah) – 1 h 52 min ago", { span: 2 }),
      label("recent4", "Completed before", "Iron Age (Getting Started) – 3 h 7 min ago", { span: 2 }),
      label("recent5", "Completed before", "First Steps (Getting Started) – 1 d 2 h ago", { span: 2 }),
    ],
  },

  // Witchery
  {
    key: "witchery_altar",
    mod: "witchery",
    title: "Altar",
    template: "witchery_altar",
    widgets: [
      percent("power", "Altar power", { color: "green", warn: 25, crit: 10 }, 78.4),
      label("power_text", "Power", "3.92 k / 5.00 k"),
      label("range", "Range", "16 blocks"),
      label("augments", "Augments", "×1.50 rate, +2 boost", { span: 2 }),
    ],
  },

  // Forbidden & Arcanus
  {
    key: "forbiddenarcanus_forge",
    mod: "forbidden_arcanus",
    title: "Hephaestus Forge",
    template: "forbiddenarcanus_forge",
    widgets: [
      label("status", "Status", "Tier 3, ritual running", { span: 2 }),
      label("item", "Main item", "Mundabitur Dust", { span: 2 }),
      percent("essence_aureal", "Aureal", {}, 62.5),
      percent("essence_ectoplasm", "Ectoplasm", {}, 35),
      percent("essence_blood", "Blood", {}, 80),
      percent("essence_experience", "Experience", {}, 12.4),
      label("essence_aureal_text", "Aureal stored", "125 / 200"),
      label("essence_ectoplasm_text", "Ectoplasm stored", "70 / 200"),
      label("essence_blood_text", "Blood stored", "16.0 k / 20.0 k"),
      label("essence_experience_text", "Experience stored", "2.48 k / 20.0 k"),
    ],
  },

  // Pylons
  {
    key: "pylons_pylon",
    mod: "pylons",
    title: "Expulsion Pylon",
    template: "pylons_pylon",
    widgets: [toggle("working", "Pylon on", { confirm: true, icon: "shield" }, true), label("owner", "Owner", "Enrico"), label("range", "Range", "3 × 3 chunks")],
  },

  // Ars Magica Legacy (one gauge + label per etherium type of the block)
  {
    key: "arsmagica_etherium",
    mod: "arsmagicalegacy",
    title: "Obelisk",
    template: "arsmagica_etherium",
    widgets: [percent("etherium_neutral", "Neutral etherium", { color: "cyan" }, 46.9), label("etherium_neutral_text", "Neutral stored", "2.35 k / 5.00 k")],
  },

  // ---- wave 4 ----
  // Peripheral card (any CC peripheral without a card of its own; values picked in the card's settings)
  {
    key: "peripheral",
    mod: "generic",
    title: "Chest",
    template: "peripheral",
    widgets: [
      label("type", "Peripheral", "minecraft:chest, inventory"),
      label("v1", "Size", "27", { span: 2 }),
      label("v2", "List", "14 entries", { span: 2 }),
      chart("c1", "Size", null, null, null, series(60, 27, 0, 40, 3)),
    ],
    extra: {
      cardSettings: {
        kind: "peripheral",
        type: "minecraft:chest",
        types: ["minecraft:chest", "inventory"],
        getters: [
          { name: "list", caption: "List", value: "text" },
          { name: "size", caption: "Size", value: "number" },
        ],
        setters: [],
        unknown: 0,
        maxValues: 6,
        maxCharts: 2,
        settings: { show: ["size", "list"], charts: ["size"], pin: true },
      },
    },
  },

  // Just Dire Things
  {
    key: "justdirethings_generator",
    mod: "justdirethings",
    title: "Simple Coal Generator",
    template: "justdirethings_generator",
    widgets: [
      label("status", "Status", "Generating", { span: 2 }),
      label("output", "Output", "120 FE/t", { span: 2 }),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 64.2),
      progress("fuel", "Burn left", 0, 100, "%", {}, 58),
      label("extra", "Fuel", "12× Coal"),
      select("redstone", "Redstone mode", ["Always on", "On without signal", "On with signal", "One step per pulse"], { confirm: true }, "Always on"),
    ],
  },
  {
    key: "justdirethings_machine",
    mod: "justdirethings",
    title: "Advanced Block Breaker",
    template: "justdirethings_machine",
    widgets: [
      label("status", "Status", "Active", { span: 2 }),
      label("speed", "Speed", "Every 20 ticks", { span: 2 }),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 81.0),
      select("redstone", "Redstone mode", ["Always on", "On without signal", "On with signal", "One step per pulse"], { confirm: true }, "On with signal"),
    ],
  },

  // Productive Trees
  {
    key: "productivetrees_machine",
    mod: "productivetrees",
    title: "Sawmill",
    template: "productivetrees_machine",
    widgets: [
      label("status", "Status", "Working"),
      progress("progress", "Progress", 0, 100, "%", {}, 45),
      label("input", "Input", "23× Oak Log", { span: 2 }),
      label("output", "Output", "36× Oak Planks", { span: 2 }),
      label("extra", "By-products", "9× Sawdust, 4× Oak Bark"),
    ],
  },

  // Ranged Pumps
  {
    key: "rangedpumps_pump",
    mod: "rangedpumps",
    title: "Pump",
    template: "rangedpumps_pump",
    widgets: [
      label("status", "Status", "Pumping"),
      percent("tank", "Tank", { color: "cyan", icon: "drop" }, 31.3),
      percent("energy", "Energy", { color: "green", icon: "bolt" }, 88.0),
      label("fluid", "Fluid", "10.0 k / 32.0 k mB Lava"),
      label("range", "Scan range", "17 of 64"),
    ],
  },

  // Charging Gadgets
  {
    key: "charginggadgets_station",
    mod: "charginggadgets",
    title: "Charging Station",
    template: "charginggadgets_station",
    widgets: [
      label("status", "Status", "Charging"),
      percent("energy", "Station energy", { color: "green", icon: "bolt" }, 42.0),
      percent("charge", "Item charge", { color: "cyan", icon: "bolt" }, 73.5),
      label("item", "Charging", "1× Building Gadget", { span: 2 }),
      label("fuel", "Fuel", "48× Coal", { span: 2 }),
      progress("burn", "Burn left", 0, 100, "%", {}, 66),
    ],
  },

  // Selling Bin
  {
    key: "sellingbin_bin",
    mod: "selling_bin",
    title: "Selling Bin",
    template: "sellingbin_bin",
    widgets: [
      label("selling", "To sell", "32× Baked Potato", { span: 2 }),
      label("worth", "Worth", "96", { span: 2 }),
      label("credit", "Credit", "40", { span: 2 }),
      label("payout", "Paid out", "7× Emerald", { span: 2 }),
      toggle("auto", "Auto-sell", { icon: "star" }, true),
      button("sell", "Sell now", { confirm: true, color: "green" }),
    ],
  },

  // Cooking for Blockheads
  {
    key: "cookingforblockheads_oven",
    mod: "cookingforblockheads",
    title: "Oven",
    template: "cookingforblockheads_oven",
    widgets: [
      label("status", "Status", "Cooking 4 items"),
      progress("cooking", "Cooking", 0, 100, "%", {}, 62),
      progress("burn", "Fuel burning", 0, 100, "%", {}, 35),
      label("input", "Input", "12× Raw Beef, 6× Potato"),
      label("fuel", "Fuel", "21× Charcoal", { span: 2 }),
      label("output", "Output", "18× Steak", { span: 2 }),
    ],
  },

  // Farming for Blockheads
  {
    key: "farmingforblockheads_shipping_bin",
    mod: "farmingforblockheads",
    title: "Shipping Bin",
    template: "farmingforblockheads_shipping_bin",
    widgets: [
      percent("shipment", "Next shipment", { color: "yellow", icon: "box" }, 68.8),
      label("value", "Value", "88 / 128"),
      label("goods", "Goods", "64× Wheat, 24× Carrot"),
      label("payout", "Paid out", "3× Emerald"),
    ],
  },

  // Training Dummy (mainframe card: a DPS meter)
  {
    key: "dummies",
    mod: "trainingdummy",
    title: "Training dummies",
    template: "dummies",
    restricted: true,
    widgets: [
      indicator("hit", "Being hit", "red", "gray", true),
      label("best", "Best DPS", "48.6 DPS"),
      { ...widget("chart", "dps", "DPS", { points: 120, interval: 1 }, 31.2), history: series(120, 26, 14, 30, 21, 0) },
      label("dummy1", "Dummy 1", "Dummy: 31.2 DPS, last 9.5, total 412 (Enrico) – 6 m"),
    ],
  },

  // FTB Essentials (mainframe card)
  {
    key: "warps",
    mod: "ftbessentials",
    title: "Warps",
    template: "warps",
    restricted: true,
    widgets: [
      label("count", "Warps", "4"),
      label("warp1", "Warp 1", "spawn – Overworld -12, 70, 4"),
      label("warp2", "Warp 2", "shop – Overworld 151, 66, 87"),
      label("warp3", "Warp 3", "nether_hub – Nether 18, 72, -12"),
      label("warp4", "Warp 4", "mining – Mining Dimension 0, 253, 0"),
    ],
  },

  // Server (mainframe card, with the spark extras appended when spark is installed)
  {
    key: "server",
    mod: "minecraft",
    title: "Server",
    template: "server",
    restricted: true,
    widgets: [
      gauge("tps", "TPS", 0, 20, null, { color: "green", icon: "cpu" }, 19.8),
      gauge("mspt", "MSPT", 0, 100, "ms", { warn: 40, crit: 50 }, 23.4),
      chart("mspt_history", "MSPT (60 min)", null, null, "ms", series(60, 24, 6, 25, 13, 5)),
      label("players", "Players online", "4 / 20"),
      label("uptime", "Uptime", "1 d 6 h"),
      progress("memory", "Memory", 0, 16384, "MB", {}, 9218),
      label("dimensions", "Tick time per dimension", "overworld 18.2 ms · the_nether 3.1 ms · the_end 0.4 ms", { span: 2 }),
      label("spark_tps", "TPS 10s / 1m / 5m / 15m (spark)", "19.9 / 19.8 / 19.7 / 19.8", { span: 2 }),
      label("spark_mspt", "MSPT last minute (spark)", "23.4 ms avg, 61.2 ms max"),
      percent("spark_cpu", "Process CPU (spark)", { warn: 80, crit: 95 }, 34.6),
    ],
  },

  // FTB Chunks (mainframe card with map overlay)
  {
    key: "claims",
    mod: "ftbchunks",
    title: "Team claims",
    template: "claims",
    restricted: true,
    widgets: [
      label("team", "Team", "Foxworks", { icon: "shield" }),
      label("claimed", "Claimed chunks", `${CLAIM_CHUNKS.length} / 250`),
      label("forced", "Force-loaded chunks", `${CLAIM_CHUNKS.filter((c) => c.forced).length} / 25`),
      label("dimensions", "Claims per dimension", `overworld ${CLAIM_CHUNKS.length}`, { span: 2 }),
    ],
    extra: { claims: { team: "Foxworks", color: "#e8792b", chunks: CLAIM_CHUNKS, truncated: false } },
  },

  // Simple Backups (mainframe card)
  {
    key: "backups",
    mod: "simplebackups",
    title: "Backups",
    template: "backups",
    restricted: true,
    widgets: [
      label("last", "Last backup", "2026-10-23 14:00 (42 min 10 s ago)", { span: 2, icon: "shield" }),
      label("size", "Backups on disk", "3.84 GB"),
      indicator("running", "Backup running", "yellow", "gray", false),
      indicator("paused", "Automatic backups paused", "yellow", "gray", false),
      button("backup_now", "Backup now", { confirm: true, color: "yellow" }),
    ],
  },
];
