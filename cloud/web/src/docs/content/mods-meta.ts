// Presentation data for the "Supported mods" page (website docs) on top of mods.gen.json (docs/COMPAT.md): a category
// per mod and one of its items as the card icon. The icons are exported by `./gradlew exportIcons` (cloud/dev/icon-ids.ts
// lists these ids); mods without items of their own get a fitting vanilla item.
export type ModCategory = "energy" | "storage" | "farms" | "machines" | "magic" | "logistics" | "world" | "server" | "protection";

/** icon = a PixelIcon map name (components/PixelIcon.tsx). */
export const MOD_CATEGORIES: { id: ModCategory; icon: string; en: string; de: string }[] = [
  { id: "energy", icon: "bolt", en: "Energy", de: "Energie" },
  { id: "storage", icon: "box", en: "Storage", de: "Lager" },
  { id: "farms", icon: "leaf", en: "Farms & mobs", de: "Farmen & Mobs" },
  { id: "machines", icon: "gear", en: "Machines", de: "Maschinen" },
  { id: "magic", icon: "star", en: "Magic", de: "Magie" },
  { id: "logistics", icon: "network", en: "Logistics", de: "Logistik" },
  { id: "world", icon: "home", en: "Map & quests", de: "Karte & Quests" },
  { id: "server", icon: "cpu", en: "Server & tools", de: "Server & Tools" },
  { id: "protection", icon: "shield", en: "Protection", de: "Schutz" },
];

/** mod id (as in mods.gen.json) → category and card icon (an item id). */
export const MOD_META: Record<string, { cat: ModCategory; icon: string }> = {
  powah: { cat: "energy", icon: "powah:energy_cell_niotic" },
  enderio: { cat: "energy", icon: "enderio:sag_mill" },
  oritech: { cat: "energy", icon: "oritech:portable_energy_storage" },
  energizedpower: { cat: "energy", icon: "energizedpower:battery_box" },
  energymeter: { cat: "energy", icon: "energymeter:meter" },
  generatorgalore: { cat: "energy", icon: "generatorgalore:diamond_generator" },
  ae2: { cat: "storage", icon: "ae2:controller" },
  refinedstorage: { cat: "storage", icon: "refinedstorage:controller" },
  functionalstorage: { cat: "storage", icon: "functionalstorage:storage_controller" },
  sophisticatedstorage: { cat: "storage", icon: "sophisticatedstorage:controller" },
  occultism: { cat: "storage", icon: "occultism:storage_controller_base" },
  dimstorage: { cat: "storage", icon: "dimstorage:dimensional_chest" },
  advanced_ae: { cat: "storage", icon: "advanced_ae:quantum_unit" },
  merequester: { cat: "storage", icon: "merequester:requester" },
  productivebees: { cat: "farms", icon: "productivebees:advanced_oak_beehive" },
  hostilenetworks: { cat: "farms", icon: "hostilenetworks:sim_chamber" },
  apothic_spawners: { cat: "farms", icon: "minecraft:spawner" },
  gateways: { cat: "farms", icon: "gateways:gate_pearl" },
  easy_villagers: { cat: "farms", icon: "minecraft:emerald" },
  agritechevolved: { cat: "farms", icon: "agritechevolved:advanced_planter" },
  productivefarming: { cat: "farms", icon: "productivefarming:farm_controller" },
  mysticalagriculture: { cat: "farms", icon: "mysticalagriculture:inferium_essence" },
  quarryplus: { cat: "machines", icon: "quarryplus:quarry" },
  ironfurnaces: { cat: "machines", icon: "ironfurnaces:diamond_furnace" },
  cobblegengalore: { cat: "machines", icon: "cobblegengalore:block_gen_diamond" },
  productivemetalworks: { cat: "machines", icon: "productivemetalworks:black_foundry_controller" },
  railcraft: { cat: "machines", icon: "railcraft:solid_fueled_firebox" },
  witchery: { cat: "magic", icon: "witchery:cauldron" },
  forbidden_arcanus: { cat: "magic", icon: "forbidden_arcanus:hephaestus_forge" },
  evilcraft: { cat: "magic", icon: "evilcraft:blood_infuser" },
  arsmagicalegacy: { cat: "magic", icon: "arsmagicalegacy:altar_core" },
  sfm: { cat: "logistics", icon: "sfm:manager" },
  modularrouters: { cat: "logistics", icon: "modularrouters:modular_router" },
  integrateddynamics: { cat: "logistics", icon: "integrateddynamics:part_display_panel" },
  computercraft: { cat: "logistics", icon: "computercraft:wired_modem_full" },
  waystones: { cat: "world", icon: "waystones:sandstone_waystone" },
  ftbquests: { cat: "world", icon: "minecraft:book" },
  journeymap: { cat: "world", icon: "minecraft:map" },
  ftbteams: { cat: "world", icon: "minecraft:white_banner" },
  spark: { cat: "server", icon: "minecraft:clock" },
  simplebackups: { cat: "server", icon: "minecraft:barrel" },
  kubejs: { cat: "server", icon: "minecraft:writable_book" },
  guideme: { cat: "server", icon: "guideme:guide" },
  mffs: { cat: "protection", icon: "mffs:projector" },
  pylons: { cat: "protection", icon: "pylons:expulsion_pylon" },
};

/** The two generic cards that work with almost any mod. */
export const GENERIC_ICONS = { energy: "minecraft:redstone_block", tank: "minecraft:water_bucket" };

/** Every icon id this page needs (for the icon export). */
export function modIconIds(): string[] {
  return [...Object.values(MOD_META).map((m) => m.icon), ...Object.values(GENERIC_ICONS)];
}
