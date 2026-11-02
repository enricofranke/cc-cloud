// The Cloud Scripts API reference of the docs, generated from the mod's descriptor (cccloud_code/api.json): the Lua
// binding, the editor's completions and hover help and these pages all read the same file, so they cannot disagree.
// Plain functions returning Markdown; en.ts and de.ts use them, and tools/gen_handbook.ts runs them under node.
import API_JSON from "../../../../../src/main/resources/cccloud_code/api.json" with { type: "json" };
import { FENCE } from "./code.ts";
import type { Lang } from "../../lib/lang.ts";

type Loc = { en: string; de: string };
interface Param {
  name: string;
  type: string;
  optional?: boolean;
  variadic?: boolean;
  doc?: Loc;
}
interface Fn {
  name: string;
  group: string;
  kind: string;
  params: Param[];
  returns: { type: string; doc?: Loc }[];
  doc: Loc;
  example?: string;
  perm?: string;
}
interface Descriptor {
  name: string;
  doc: Loc;
  groups: { id: string; title: Loc }[];
  types: Record<string, { kind: string; of?: string; doc?: Loc; fields?: { name: string; type: string; doc?: Loc }[] }>;
  functions: Fn[];
  fields: { name: string; type: string; group: string; doc: Loc }[];
  events: { name: string; filter: string[]; doc: Loc }[];
  lua: { globals: string[]; libraries: Record<string, string[]>; removed: string[] };
}
const API = API_JSON as unknown as Descriptor;

const T = {
  en: {
    needs: "needs",
    waits: "waits for the server (one tick)",
    pauses: "pauses the script",
    returns: "Returns",
    nothing: "nothing",
    optional: "optional",
    event: "Event",
    filter: "Filter",
    what: "What happens",
    field: "Field",
    type: "Type",
    meaning: "Meaning",
    types: "Types",
    typesIntro: "Tables the functions return or take. A `?` after a type means it can be `nil`.",
    events: "Events",
    eventsIntro: "For `cloud.on(event, filter, handler)` and `cloud.wait(event, filter, timeout)`. The handler gets an Event table.",
    alias_of: "a string:",
    globals: "Global functions",
    libraries: "Libraries",
    removed: "Not available",
  },
  de: {
    needs: "braucht",
    waits: "wartet auf den Server (einen Tick)",
    pauses: "pausiert das Skript",
    returns: "Gibt zurück",
    nothing: "nichts",
    optional: "optional",
    event: "Ereignis",
    filter: "Filter",
    what: "Was passiert",
    field: "Feld",
    type: "Typ",
    meaning: "Bedeutung",
    types: "Typen",
    typesIntro: "Tabellen, die die Funktionen zurückgeben oder nehmen. Ein `?` nach einem Typ heißt: kann `nil` sein.",
    events: "Ereignisse",
    eventsIntro: "Für `cloud.on(event, filter, handler)` und `cloud.wait(event, filter, timeout)`. Der Handler bekommt eine Event-Tabelle.",
    alias_of: "ein Text:",
    globals: "Globale Funktionen",
    libraries: "Bibliotheken",
    removed: "Nicht verfügbar",
  },
} satisfies Record<Lang, Record<string, string>>;

const loc = (l: Loc | undefined, lang: Lang) => (l ? l[lang] || l.en : "");
const params = (f: Fn) => f.params.map((p) => (p.variadic ? "..." : `${p.name}${p.optional ? "?" : ""}`)).join(", ");
const anchor = (f: Fn) => `\`${API.name}.${f.name}(${params(f)})\``;

function fnBlock(f: Fn, lang: Lang): string {
  const t = T[lang];
  const ret = f.returns.length ? ` → \`${f.returns.map((r) => r.type).join(", ")}\`` : "";
  const tags = [f.perm ? `${t.needs} \`${f.perm}\`` : "", f.kind === "await" ? t.waits : f.kind === "yield" ? t.pauses : ""].filter(Boolean);
  // Aliases (cancel, alert) say what they alias in their own doc text.
  const out = [`**${anchor(f)}**${ret}${tags.length ? ` · ${tags.join(" · ")}` : ""}`, "", loc(f.doc, lang)];
  const described = f.params.filter((p) => p.doc || p.optional);
  const retDocs = f.returns.filter((r) => r.doc);
  if (described.length || retDocs.length) {
    out.push("");
    for (const p of described) out.push(`- \`${p.variadic ? "..." : p.name}\` (${p.type}${p.optional ? `, ${t.optional}` : ""})${p.doc ? ` – ${loc(p.doc, lang)}` : ""}`);
    if (retDocs.length) out.push(`- ${t.returns}: ${f.returns.map((r) => `\`${r.type}\`${r.doc ? ` – ${loc(r.doc, lang)}` : ""}`).join("; ")}`);
  }
  if (f.example) out.push("", `${FENCE}lua`, f.example, FENCE);
  return out.join("\n");
}

/** The functions by group, then the events and types (### headings with ids that are the same in both languages). */
export function apiReference(lang: Lang): string {
  const t = T[lang];
  const parts: string[] = [loc(API.doc, lang)];
  for (const g of API.groups) {
    parts.push(`### ${loc(g.title, lang)} {#api-${g.id}}`);
    for (const f of API.functions.filter((x) => x.group === g.id)) parts.push(fnBlock(f, lang));
    for (const x of API.fields.filter((y) => y.group === g.id)) parts.push(`**\`${API.name}.${x.name}\`** → \`${x.type}\`\n\n${loc(x.doc, lang)}`);
  }
  parts.push(`### ${t.events} {#api-events}`, t.eventsIntro, [`| ${t.event} | ${t.filter} | ${t.what} |`, "|---|---|---|", ...API.events.map((e) => `| \`${e.name}\` | ${e.filter.map((x) => `\`${x}\``).join(", ")} | ${loc(e.doc, lang)} |`)].join("\n"));
  parts.push(`### ${t.types} {#api-types}`, t.typesIntro);
  for (const [name, ty] of Object.entries(API.types)) {
    if (ty.kind === "alias") parts.push(`**${name}** – ${t.alias_of} \`${ty.of}\`. ${loc(ty.doc, lang)}`);
    else parts.push(`**${name}**\n\n${[`| ${t.field} | ${t.type} | ${t.meaning} |`, "|---|---|---|", ...(ty.fields ?? []).map((x) => `| \`${x.name}\` | \`${x.type}\` | ${loc(x.doc, lang)} |`)].join("\n")}`);
  }
  return parts.join("\n\n");
}

/** What the sandbox keeps and removes, from the descriptor's lua section. */
export function sandboxLists(lang: Lang): string {
  const t = T[lang];
  const libs = Object.entries(API.lua.libraries).map(([lib, names]) => `- \`${lib}\`: ${names.map((n) => `\`${n}\``).join(", ")}`);
  return [`**${t.globals}:** ${API.lua.globals.map((g) => `\`${g}\``).join(", ")}`, `**${t.libraries}:**`, libs.join("\n"), `**${t.removed}:** ${API.lua.removed.map((g) => `\`${g}\``).join(", ")}`].join("\n\n");
}

/** A short first script for the getting-started part. */
export const FIRST_SCRIPT = `-- every 10 seconds: the reactor's heat to the log
cloud.every(10, function()
  print("heat", cloud.getValue("Reactor", "heat"))
end)

-- above 80: switch it off and tell everyone
cloud.on("above", {card = "Reactor", widget = "heat", value = 80}, function(e)
  local ok, err = cloud.set("Reactor", "active", false)
  if ok then
    cloud.notify("Reactor off at " .. e.value, "crit")
  else
    cloud.warn("could not switch it off: " .. err)
  end
end)`;
