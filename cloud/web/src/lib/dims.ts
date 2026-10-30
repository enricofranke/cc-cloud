// Wave 4: names and icons of the dimensions on the network map. The mod sends dimension ids
// ("aether_ii:aether_holy_isles"); the known ones of All the Mods 11 get their in-game names,
// everything else a name from its id and an icon guessed from its words.

export type DimIcon = "overworld" | "flame" | "ender" | "cloud" | "cave" | "pick" | "moon" | "planet" | "star" | "box" | "globe";

const KNOWN: Record<string, { name: string; icon: DimIcon }> = {
  "minecraft:overworld": { name: "Overworld", icon: "overworld" },
  "minecraft:the_nether": { name: "Nether", icon: "flame" },
  "minecraft:the_end": { name: "End", icon: "ender" },
  "aether_ii:aether_holy_isles": { name: "Aether", icon: "cloud" },
  "aether:the_aether": { name: "Aether", icon: "cloud" },
  "undergarden:undergarden": { name: "Undergarden", icon: "cave" },
  "allthemodium:mining": { name: "Mining Dimension", icon: "pick" },
  "allthemodium:the_other": { name: "The Other", icon: "flame" },
  "allthemodium:the_beyond": { name: "The Beyond", icon: "ender" },
  "stellaris:moon": { name: "Moon", icon: "moon" },
  "stellaris:earth_orbit": { name: "Earth Orbit", icon: "planet" },
  "witchery:dream_world": { name: "Dream World", icon: "star" },
  "witchery:nightmare_world": { name: "Nightmare World", icon: "star" },
  "witchery:mirror_world": { name: "Mirror World", icon: "star" },
  "mahoutsukai:reality_marble": { name: "Reality Marble", icon: "star" },
  "neovitae:dungeon": { name: "Dungeon", icon: "cave" },
  "thaumaturge:outer_lands": { name: "Outer Lands", icon: "ender" },
  "ae2:spatial_storage": { name: "Spatial Storage", icon: "box" },
  "twilightforest:twilight_forest": { name: "Twilight Forest", icon: "overworld" },
};

/** Order of the dimension tabs: the vanilla three first, then the others by name. */
export const DIM_ORDER = ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"];

export function dimLabel(dim: string): string {
  const known = KNOWN[dim];
  if (known) return known.name;
  return dim.replace(/^[a-z0-9_.-]+:/, "").replace(/[_/]/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Pixel icon of a dimension (PixelIcon name): known ids, else guessed from the words in the id. */
export function dimIcon(dim: string): DimIcon {
  const known = KNOWN[dim];
  if (known) return known.icon;
  const id = dim.toLowerCase();
  if (/nether|hell|inferno|lava|fire|other/.test(id)) return "flame";
  if (/(^|[:_])end($|_)|void|beyond|abyss|null/.test(id)) return "ender";
  if (/aether|sky|cloud|heaven|isle/.test(id)) return "cloud";
  if (/under|cave|deep|dungeon|crypt|mine$|mines/.test(id)) return "cave";
  if (/mining|quarry/.test(id)) return "pick";
  if (/moon|luna/.test(id)) return "moon";
  if (/mars|venus|mercury|planet|orbit|space|asteroid|glacio/.test(id)) return "planet";
  if (/dream|nightmare|mirror|spirit|astral|marble/.test(id)) return "star";
  if (/spatial|storage|compact|pocket/.test(id)) return "box";
  if (/overworld|forest|twilight|garden/.test(id)) return "overworld";
  return "globe";
}

/** Vanilla height range of a dimension the server did not describe (older mods send no `dims`). */
export function dimRange(dim: string, info?: { minY: number; maxY: number }): { minY: number; maxY: number } {
  if (info) return info;
  if (dim === "minecraft:the_nether" || dim === "minecraft:the_end") return { minY: 0, maxY: 255 };
  return { minY: -64, maxY: 319 };
}

/** Dimensions with a roof (the surface map would show bedrock): Nether, Undergarden, … */
export function dimHasCeiling(dim: string, info?: { ceiling?: boolean }): boolean {
  return info ? info.ceiling === true : dim === "minecraft:the_nether";
}
