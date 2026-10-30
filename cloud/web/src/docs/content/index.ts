import { de } from "./de.ts";
import GEN from "./recipes.gen.json" with { type: "json" };
import { en } from "./en.ts";
import { MOD_NAMES } from "./code.ts";
import type { IconName } from "../../lib/layout.ts";

import type { Lang } from "../../lib/lang.ts";
export type { Lang };

export interface DocPageMeta {
  id: string;
  icon: IconName;
  title: Record<Lang, string>;
}

export const PAGES: DocPageMeta[] = [
  { id: "start", icon: "home", title: { en: "Getting started", de: "Erste Schritte" } },
  { id: "cc-basics", icon: "cpu", title: { en: "CC basics", de: "CC-Grundlagen" } },
  { id: "dashboard", icon: "chart", title: { en: "The dashboard", de: "Das Dashboard" } },
  { id: "redstone", icon: "lamp", title: { en: "Redstone devices", de: "Redstone-Geräte" } },
  { id: "lua", icon: "gear", title: { en: "Lua reference", de: "Lua-Referenz" } },
  { id: "ui-library", icon: "star", title: { en: "cloud.ui library", de: "cloud.ui-Bibliothek" } },
  { id: "network", icon: "fan", title: { en: "Network, relays & range", de: "Netzwerk, Relays & Reichweite" } },
  { id: "upgrades", icon: "star", title: { en: "Upgrades, tablet & display", de: "Upgrades, Tablet & Display" } },
  { id: "automation", icon: "bolt", title: { en: "Rules, scenes & notifications", de: "Regeln, Szenen & Benachrichtigungen" } },
  { id: "recipes", icon: "pick", title: { en: "All recipes", de: "Alle Rezepte" } },
  { id: "mods", icon: "box", title: { en: "Supported mods", de: "Unterstützte Mods" } },
  { id: "bridges", icon: "box", title: { en: "Bridges (AE2 / RS)", de: "Bridges (AE2 / RS)" } },
  { id: "accounts", icon: "lock", title: { en: "Accounts, roles & sharing", de: "Konten, Rollen & Teilen" } },
  { id: "api", icon: "cpu", title: { en: "Public API", de: "Öffentliche API" } },
  { id: "security", icon: "shield", title: { en: "Security & hosting", de: "Sicherheit & Hosting" } },
  { id: "faq", icon: "warning", title: { en: "Troubleshooting", de: "Fehlerbehebung" } },
];

export const CONTENT: Record<Lang, Record<string, string>> = { de, en };

/** UI strings of the docs shell. */
export const UI: Record<Lang, Record<string, string>> = {
  en: {
    docs: "Docs",
    search: "Search the docs…",
    noResults: "Nothing found for",
    results: "Results",
    menu: "Menu",
    back: "Dashboard",
    onThisPage: "On this page",
    prev: "Previous",
    next: "Next",
    copyLink: "Copy link to this section",
    linkCopied: "Link copied",
    language: "Language",
    any: "any",
    shapeless: "Shapeless – any arrangement",
    requires: "Needs",
    without: "Only without",
    result: "Result",
    liveTitle: "Live recipes of this server.",
    liveAllDefault: "All as shipped with the mod.",
    liveChanged: "changed",
    liveAdded: "added",
    liveRemoved: "removed",
    liveFallback: "The mod's default recipes – this server's live recipes could not be loaded. Modpacks can change recipes; JEI/EMI and the in-game handbook show the live ones.",
    badgeChanged: "Changed by this server/modpack",
    badgeAdded: "Added by this server/modpack",
    badgeRemoved: "Not craftable on this server",
    inactive: "not active on this server",
    defaultRecipe: "Default recipe",
    madeWith: "Made with",
    addedHere: "Added by this server",
  },
  de: {
    docs: "Doku",
    search: "Doku durchsuchen…",
    noResults: "Nichts gefunden für",
    results: "Treffer",
    menu: "Menü",
    back: "Dashboard",
    onThisPage: "Auf dieser Seite",
    prev: "Zurück",
    next: "Weiter",
    copyLink: "Link zu diesem Abschnitt kopieren",
    linkCopied: "Link kopiert",
    language: "Sprache",
    any: "beliebig",
    shapeless: "Formlos – beliebige Anordnung",
    requires: "Braucht",
    without: "Nur ohne",
    result: "Ergebnis",
    liveTitle: "Live-Rezepte dieses Servers.",
    liveAllDefault: "Alle wie in der Mod.",
    liveChanged: "geändert",
    liveAdded: "hinzugefügt",
    liveRemoved: "entfernt",
    liveFallback: "Die Standard-Rezepte der Mod – die aktuellen Rezepte dieses Servers ließen sich nicht laden. Modpacks können Rezepte ändern; JEI/EMI und das Handbuch im Spiel zeigen die aktuellen.",
    badgeChanged: "Von diesem Server/Modpack geändert",
    badgeAdded: "Von diesem Server/Modpack hinzugefügt",
    badgeRemoved: "Auf diesem Server nicht herstellbar",
    inactive: "auf diesem Server nicht aktiv",
    defaultRecipe: "Standard-Rezept",
    madeWith: "Hergestellt mit",
    addedHere: "Von diesem Server hinzugefügt",
  },
};

/** Item names for the recipe grids. */
export const ITEMS: Record<string, Record<Lang, string>> = {
  "#c:nuggets/gold": { en: "Gold Nugget", de: "Goldklumpen" },
  "#c:ingots/gold": { en: "Gold Ingot", de: "Goldbarren" },
  "#c:ender_pearls": { en: "Ender Pearl", de: "Enderperle" },
  "#c:dusts/redstone": { en: "Redstone Dust", de: "Redstonestaub" },
  "minecraft:prismarine_crystals": { en: "Prismarine Crystals", de: "Prismarinkristalle" },
  "minecraft:stone": { en: "Stone", de: "Stein" },
  "minecraft:paper": { en: "Paper", de: "Papier" },
  "computercraft:computer_advanced": { en: "Advanced Computer", de: "Advanced Computer" },
  "computercraft:disk_drive": { en: "Disk Drive", de: "Disk Drive" },
  "computercraft:wired_modem": { en: "Wired Modem", de: "Wired Modem" },
  "ae2:interface": { en: "ME Interface", de: "ME Interface" },
  "refinedstorage:interface": { en: "RS Interface", de: "RS Interface" },
  "minecraft:ender_eye": { en: "Eye of Ender", de: "Enderauge" },
  "#c:storage_blocks/gold": { en: "Block of Gold", de: "Goldblock" },
  "#c:glass_panes": { en: "Glass Pane", de: "Glasscheibe" },
  "#c:ingots/allthemodium": { en: "Allthemodium Ingot", de: "Allthemodiumbarren" },
  "#c:ingots/vibranium": { en: "Vibranium Ingot", de: "Vibraniumbarren" },
  "#c:ingots/unobtainium": { en: "Unobtainium Ingot", de: "Unobtainiumbarren" },
  "allthetweaks:atm_star": { en: "ATM Star", de: "ATM-Stern" },
  "computercraft:monitor_advanced": { en: "Advanced Monitor", de: "Advanced Monitor" },
  "computercraft:pocket_computer_normal": { en: "Pocket Computer", de: "Pocket Computer" },
  "computercraft:pocket_computer_advanced": { en: "Advanced Pocket Computer", de: "Advanced Pocket Computer" },
  "computercraft:wireless_modem_advanced": { en: "Ender Modem", de: "Ender Modem" },
  "minecraft:chest": { en: "Chest", de: "Truhe" },
  "minecraft:diamond": { en: "Diamond", de: "Diamant" },
  "#c:gems/diamond": { en: "Diamond", de: "Diamant" },
  "minecraft:netherite_scrap": { en: "Netherite Scrap", de: "Netheritschrott" },
  "minecraft:ender_pearl": { en: "Ender Pearl", de: "Enderperle" },
  "minecraft:lodestone": { en: "Lodestone", de: "Leitstein" },
  "minecraft:nether_star": { en: "Nether Star", de: "Netherstern" },
  "minecraft:netherite_block": { en: "Block of Netherite", de: "Netheritblock" },
  "minecraft:netherite_ingot": { en: "Netherite Ingot", de: "Netheritbarren" },
  "minecraft:obsidian": { en: "Obsidian", de: "Obsidian" },
  "minecraft:redstone": { en: "Redstone Dust", de: "Redstonestaub" },
  "minecraft:redstone_block": { en: "Block of Redstone", de: "Redstoneblock" },
  "minecraft:string": { en: "String", de: "Faden" },
  "minecraft:book": { en: "Book", de: "Buch" },
  "minecraft:lever": { en: "Lever", de: "Hebel" },
  "minecraft:stone_button": { en: "Stone Button", de: "Steinknopf" },
  "minecraft:comparator": { en: "Redstone Comparator", de: "Redstone-Komparator" },
  "minecraft:redstone_lamp": { en: "Redstone Lamp", de: "Redstone-Lampe" },
  "minecraft:observer": { en: "Observer", de: "Beobachter" },
  "guideme:guide": { en: "CC: Cloud Handbook", de: "CC: Cloud-Handbuch" },
  // every CC: Cloud item, from the mod's lang files (build step dev/build-recipes.ts)
  ...GEN.names,
};
/** Mod names for recipe conditions ("requires …"). */
export const MODS: Record<string, string> = MOD_NAMES;

/** Tags have no icon of their own: show a typical member. */
const TAG_ICON: Record<string, string> = {
  "#c:nuggets/gold": "minecraft:gold_nugget",
  "#c:ingots/gold": "minecraft:gold_ingot",
  "#c:ender_pearls": "minecraft:ender_pearl",
  "#c:dusts/redstone": "minecraft:redstone",
  "#c:storage_blocks/gold": "minecraft:gold_block",
  "#c:glass_panes": "minecraft:glass_pane",
  "#c:ingots/allthemodium": "allthemodium:allthemodium_ingot",
  "#c:ingots/vibranium": "allthemodium:vibranium_ingot",
  "#c:ingots/unobtainium": "allthemodium:unobtainium_ingot",
  "#c:gems/diamond": "minecraft:diamond",
};
/** What the server knows on top (live recipes): a representative item per tag and English names of other mods' items. */
export interface ServerItems {
  icons?: Record<string, string>;
  names?: Record<string, string>;
}

/** The item whose icon an ingredient shows: a tag's typical member, the first of several alternatives ("a|b"). */
export function iconItem(id: string, server?: ServerItems) {
  const first = id.split("|")[0];
  return server?.icons?.[first] ?? TAG_ICON[first] ?? first.replace(/^#/, "");
}

export function itemName(id: string, lang: Lang, server?: ServerItems): string {
  if (id.includes("|")) {
    const parts = id.split("|");
    return parts
      .slice(0, 3)
      .map((p) => itemName(p, lang, server))
      .concat(parts.length > 3 ? ["…"] : [])
      .join(" / ");
  }
  const tag = id.startsWith("#");
  const rep = tag ? server?.icons?.[id] : undefined;
  const n = ITEMS[id]?.[lang] ?? (tag ? (rep ? ITEMS[rep]?.[lang] ?? server?.names?.[rep] : undefined) : server?.names?.[id]);
  if (n) return tag ? `${n} (${UI[lang].any})` : n;
  return id.replace(/^#?[a-z0-9_.-]+:/, "").replace(/[_/]/g, " ");
}

/** German for German browsers, otherwise English (shared with the v0.10 screens). */
export { detectLang } from "../../lib/lang.ts";
