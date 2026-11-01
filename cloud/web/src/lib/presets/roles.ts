// What a device is, for preset slots. Device cards carry a stable kind (`template`), but programs do not: a Lua uplink
// has no template at all and its widget ids are its own. So a role comes from the kind where there is one, and otherwise
// from what the device says about itself (its title, its widgets' labels and units).
import type { Uplink, Widget, WidgetType } from "../types.ts";
import type { Role } from "./format.ts";

/** Widgets that do something: a preset never relabels them (expand.ts). */
export const ACTION_TYPES: ReadonlySet<WidgetType> = new Set<WidgetType>(["button", "toggle", "slider", "select", "number", "text", "buttons", "redstone_sides", "item_search", "watchlist", "jobs", "canvas"]);

const KINDS: Record<string, Role[]> = {
  energy: ["energy"],
  energymeter: ["energy"],
  powah_cell: ["energy"],
  oritech_storage: ["energy"],
  refinedtypes_energizer: ["energy"],
  charginggadgets_station: ["machine"],
  powah_reactor: ["reactor", "generator", "energy"],
  enderio_solar: ["generator"],
  cobblegengalore_generator: ["machine"],
  railcraft_boiler: ["generator", "fluid"],
  thaumaturge_reservoir: ["fluid"],
  storage: ["storage"],
  merequester: ["storage"],
  advancedae_quantum: ["storage"],
  integratedtunnels_network: ["storage"],
  integratedcrafting_jobs: ["storage"],
  classicpipes_network: ["storage"],
  stepcrafter_monitor: ["storage"],
  appliedthaumaturge_essentia: ["storage"],
  trashcans_can: ["items"],
  sellingbin_bin: ["items"],
  farmingforblockheads_shipping_bin: ["items"],
  tank: ["fluid"],
  rangedpumps_pump: ["fluid"],
  redstone: ["redstone"],
  sensor: ["sensor"],
  justdirethings_sensor: ["sensor"],
  script: ["script"],
  peripheral: ["machine"],
  productivebees_hive: ["farm"],
  productivebees_centrifuge: ["farm", "machine"],
  productivefarming_farm: ["farm"],
  productivetrees_machine: ["farm", "machine"],
  agritechevolved_advanced_planter: ["farm"],
  easyvillagers_iron_farm: ["farm"],
  mysticalagriculture_machine: ["farm", "machine"],
  strainers_strainer: ["farm"],
  apothicspawners_spawner: ["farm"],
  hostilenetworks_chamber: ["farm", "machine"],
  quarryplus_quarry: ["machine"],
  sfm_manager: ["machine"],
  modularrouters_router: ["machine"],
};
const SUFFIXES: [RegExp, Role][] = [
  [/_reactor$/, "reactor"],
  [/_generator$/, "generator"],
  [/_(cell|battery|capacitor)$/, "energy"],
  [/_storage$/, "items"],
  [/_tank$/, "fluid"],
  [/_(farm|planter|hive)$/, "farm"],
  [/_(machine|furnace|oven|forge|foundry|chamber|toaster|charger|crucible|assembler|coke_oven|blast_furnace|altar|matrix)$/, "machine"],
];
/** Cards of the mainframe itself: not devices of the base. */
const SYSTEM = new Set(["scenes", "production"]);

/** Words in a device's title (and, for programs, its widgets' labels). */
const WORDS: [Role, string[]][] = [
  ["reactor", ["reactor", "reaktor", "fission", "fusion"]],
  ["generator", ["generator", "turbine", "turbin", "solar", "dynamo"]],
  ["farm", ["farm", "greenhouse", "gewächshaus", "tree", "crop", "harvest", "garden", "garten", "beehive", "apiary"]],
  ["door", ["door", "gate", "hatch", "airlock", "tür", "tuer", "tor ", "schleuse", "luke"]],
  ["items", ["storage", "chest", "barrel", "lager", "kiste"]],
  ["fluid", ["tank", "fluid", "coolant", "kühlmittel", "wasser", "water"]],
  ["machine", ["machine", "maschine", "furnace", "ofen", "crusher", "smelter", "assembler", "factory", "fabrik", "quarry"]],
  ["sensor", ["sensor", "detector"]],
];
const ENERGY_UNIT = /\b[kMGT]?(FE|RF)(\/t)?\b/;
const ENERGY_WORDS = /energy|energie|battery|batterie|capacitor|kondensator|induction|accumulator|akku|\bcharge\b|\bstrom\b/i;

const isValue = (w: Widget) => w.type === "gauge" || w.type === "progress" || w.type === "chart" || w.type === "label";
const warns = (w: Widget) => w.type === "gauge" && (typeof w.opts?.warn === "number" || typeof w.opts?.crit === "number");

const memo = new WeakMap<Uplink, ReadonlySet<Role>>();

/** The roles of a device. "any" is every device of the base; the mainframe's own Scenes and Production cards have none. */
export function rolesOf(u: Uplink): ReadonlySet<Role> {
  const known = memo.get(u);
  if (known) return known;
  const roles = new Set<Role>();
  const kind = u.template;
  if (!kind || !SYSTEM.has(kind)) {
    roles.add("any");
    const listed = kind ? KINDS[kind] : undefined;
    for (const r of listed ?? []) roles.add(r);
    if (kind && !listed) for (const [re, role] of SUFFIXES) if (re.test(kind)) roles.add(role);
    const program = !kind || kind === "script";
    const haystack = `${u.title ?? ""} ${program ? u.widgets.map((w) => w.label ?? "").join(" ") : ""} `.toLowerCase();
    if (program || !listed) for (const [role, words] of WORDS) if (words.some((word) => haystack.includes(word))) roles.add(role);
    const values = u.widgets.filter(isValue);
    if (values.some((w) => ENERGY_UNIT.test(String(w.opts?.unit ?? ""))) || (program && values.some((w) => ENERGY_WORDS.test(w.label ?? "")))) roles.add("energy");
    if (u.widgets.some((w) => w.id === "stored") && u.widgets.some((w) => w.id === "energy")) roles.add("energy");
    if (u.widgets.some((w) => w.type === "indicator" || warns(w))) roles.add("alarm");
    if (u.widgets.some((w) => ACTION_TYPES.has(w.type) && w.type !== "item_search" && w.type !== "watchlist" && w.type !== "jobs")) roles.add("control");
    if (u.mode === "auto") {
      roles.add("computer");
      roles.add("redstone");
    }
    if (program && u.mode !== "auto") roles.add("script");
    if (u.widgets.some((w) => w.type === "redstone_sides")) roles.add("redstone");
    if (u.restricted) roles.add("server");
  }
  memo.set(u, roles);
  return roles;
}
