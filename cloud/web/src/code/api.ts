// The Cloud Scripts descriptor (src/main/resources/cccloud_code/api.json) turned into what the editor shows:
// completions, hover docs and the signature of the call under the cursor, in English or German. Pure functions on
// plain text, so they run in tests and the docs generator; the CodeMirror glue is in CodeEditor.tsx.
import type { Lang } from "../lib/lang.ts";

export interface Loc {
  en: string;
  de: string;
}
export interface ApiParam {
  name: string;
  type: string;
  optional?: boolean;
  variadic?: boolean;
  doc?: Loc;
}
export interface ApiReturn {
  type: string;
  doc?: Loc;
}
export interface ApiFunction {
  name: string;
  group: string;
  /** sync = returns at once, await = waits for the server (a tick), yield = pauses the script */
  kind: "sync" | "await" | "yield";
  params: ApiParam[];
  returns: ApiReturn[];
  doc: Loc;
  example?: string;
  perm?: string;
  aliasOf?: string;
}
export interface ApiTypeField {
  name: string;
  type: string;
  doc?: Loc;
}
export type ApiType = { kind: "alias"; of: string; doc?: Loc } | { kind: "table"; fields: ApiTypeField[]; doc?: Loc };
export interface ApiDescriptor {
  name: string;
  version: number;
  doc: Loc;
  groups: { id: string; title: Loc }[];
  types: Record<string, ApiType>;
  functions: ApiFunction[];
  fields: (ApiTypeField & { group: string })[];
  events: { name: string; filter: string[]; doc: Loc }[];
  lua: { globals: string[]; libraries: Record<string, string[]>; removed: string[] };
}

export const loc = (l: Loc | undefined, lang: Lang) => (l ? l[lang] || l.en : "");

export const LUA_KEYWORDS = ["and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"];

/** "card, widget, options?" */
export function paramList(f: ApiFunction): string {
  return f.params.map((p) => (p.variadic ? "..." : `${p.name}${p.optional ? "?" : ""}`)).join(", ");
}
/** "cloud.press(card, widget, options?) → boolean, string?" */
export function signature(api: ApiDescriptor, f: ApiFunction): string {
  const ret = f.returns.length ? ` → ${f.returns.map((r) => r.type).join(", ")}` : "";
  return `${api.name}.${f.name}(${paramList(f)})${ret}`;
}

// ------------------------------------------------------------------ text helpers

interface Span {
  from: number;
  /** exclusive; the end of the text when it is not closed */
  to: number;
  kind: "string" | "comment";
  closed: boolean;
}

/** The strings and comments of a Lua source, in order. Long brackets ([[ ]], [==[ ]==]) and escapes are handled. */
export function luaSpans(text: string): Span[] {
  const spans: Span[] = [];
  const longLevel = (i: number) => {
    const m = /^\[(=*)\[/.exec(text.slice(i, i + 64));
    return m ? m[1].length : -1;
  };
  const longEnd = (from: number, level: number) => {
    const close = text.indexOf(`]${"=".repeat(level)}]`, from);
    return close < 0 ? { to: text.length, closed: false } : { to: close + level + 2, closed: true };
  };
  let i = 0;
  while (i < text.length) {
    const c = text[i];
    if (c === "-" && text[i + 1] === "-") {
      const level = text[i + 2] === "[" ? longLevel(i + 2) : -1;
      if (level >= 0) {
        const e = longEnd(i + 4 + level, level);
        spans.push({ from: i, to: e.to, kind: "comment", closed: e.closed });
        i = e.to;
      } else {
        const nl = text.indexOf("\n", i);
        const to = nl < 0 ? text.length : nl;
        spans.push({ from: i, to, kind: "comment", closed: nl >= 0 });
        i = to;
      }
      continue;
    }
    if (c === "[") {
      const level = longLevel(i);
      if (level >= 0) {
        const e = longEnd(i + 2 + level, level);
        spans.push({ from: i, to: e.to, kind: "string", closed: e.closed });
        i = e.to;
        continue;
      }
    }
    if (c === '"' || c === "'") {
      let j = i + 1;
      while (j < text.length && text[j] !== c && text[j] !== "\n") j += text[j] === "\\" ? 2 : 1;
      const closed = j < text.length && text[j] === c;
      spans.push({ from: i, to: Math.min(closed ? j + 1 : j, text.length), kind: "string", closed });
      i = j + 1;
      continue;
    }
    i++;
  }
  return spans;
}

/** The source with the contents of strings and comments blanked (same length, newlines and quotes kept). */
export function maskLua(text: string): string {
  const out = text.split("");
  for (const s of luaSpans(text)) {
    // a string keeps its delimiters, so "(" and "," inside it never count
    const from = s.kind === "string" ? s.from + 1 : s.from;
    const to = s.kind === "string" && s.closed ? s.to - 1 : s.to;
    for (let k = from; k < to; k++) if (out[k] !== "\n") out[k] = " ";
  }
  return out.join("");
}

/** Is the cursor at `pos` in code, in a string or in a comment? */
export function luaStateAt(text: string, pos: number): "code" | "string" | "comment" {
  for (const s of luaSpans(text.slice(0, pos))) {
    // only the text before the cursor was scanned: a span that reaches the cursor without closing holds it
    if (s.to === pos && (!s.closed || s.kind === "comment") && s.from < pos) return s.kind;
  }
  return "code";
}

/** The dotted name that ends at `pos` (cursor), e.g. "cloud.store.ge" → ["cloud","store","ge"]. */
function pathBefore(text: string, pos: number): { parts: string[]; from: number; colon: boolean } {
  let i = pos;
  while (i > 0 && /[\w.:]/.test(text[i - 1])) i--;
  // a leading digit is a number, not a name
  const raw = text.slice(i, pos);
  if (/^\d/.test(raw)) return { parts: [], from: pos, colon: false };
  const colonAt = raw.lastIndexOf(":");
  if (colonAt >= 0) {
    // s:upper → the string methods; only the part after the colon is completed
    const after = raw.slice(colonAt + 1);
    return { parts: ["string", after], from: pos - after.length, colon: true };
  }
  const parts = raw.split(".");
  return { parts, from: pos - parts[parts.length - 1].length, colon: false };
}

// ------------------------------------------------------------------ completions

export interface Completion {
  label: string;
  type: "function" | "variable" | "namespace" | "keyword" | "constant" | "property" | "event";
  detail?: string;
  info?: string;
  boost?: number;
}
export interface CompletionResult {
  /** where the replaced word starts */
  from: number;
  options: Completion[];
}

function fnCompletion(api: ApiDescriptor, f: ApiFunction, lang: Lang, label: string): Completion {
  return { label, type: "function", detail: `(${paramList(f)})`, info: `${signature(api, f)}\n\n${loc(f.doc, lang)}` };
}

/** What a namespace holds: "cloud", "cloud.store", "cloud.script", a library, or "" for the top level. */
export function membersOf(api: ApiDescriptor, ns: string, lang: Lang): Completion[] {
  const out: Completion[] = [];
  if (ns === "") {
    out.push({ label: api.name, type: "namespace", detail: "API", info: loc(api.doc, lang), boost: 2 });
    for (const g of api.lua.globals) out.push({ label: g, type: g === "_G" || g === "_VERSION" ? "constant" : "function", detail: "Lua" });
    for (const lib of Object.keys(api.lua.libraries)) out.push({ label: lib, type: "namespace", detail: "Lua" });
    for (const k of LUA_KEYWORDS) out.push({ label: k, type: "keyword", boost: -1 });
    return out;
  }
  if (ns === api.name) {
    const subs = new Set<string>();
    for (const f of api.functions) {
      const dot = f.name.indexOf(".");
      if (dot < 0) out.push(fnCompletion(api, f, lang, f.name));
      else subs.add(f.name.slice(0, dot));
    }
    for (const s of subs) out.push({ label: s, type: "namespace", detail: "table" });
    for (const f of api.fields) out.push({ label: f.name, type: "property", detail: f.type, info: loc(f.doc, lang) });
    return out;
  }
  if (ns.startsWith(`${api.name}.`)) {
    const rest = ns.slice(api.name.length + 1);
    const prefix = `${rest}.`;
    for (const f of api.functions) if (f.name.startsWith(prefix) && !f.name.slice(prefix.length).includes(".")) out.push(fnCompletion(api, f, lang, f.name.slice(prefix.length)));
    // a field's table type (cloud.script.name)
    const field = api.fields.find((x) => x.name === rest);
    const t = field ? api.types[field.type] : undefined;
    if (t?.kind === "table") for (const x of t.fields) out.push({ label: x.name, type: "property", detail: x.type, info: loc(x.doc, lang) });
    return out;
  }
  const lib = api.lua.libraries[ns];
  if (lib) for (const m of lib) out.push({ label: m, type: m === "pi" || m === "huge" || m === "charpattern" ? "constant" : "function", detail: `${ns}.${m}` });
  return out;
}

/** Completions for the cursor at `pos` in `text` (event names inside cloud.on("…"), nothing in comments). */
export function completionsAt(api: ApiDescriptor, lang: Lang, text: string, pos: number, explicit = false): CompletionResult | null {
  const state = luaStateAt(text, pos);
  if (state === "comment") return null;
  if (state === "string") {
    const ev = new RegExp(`\\b${api.name}\\.(on|wait)\\(\\s*["']([a-z]*)$`).exec(text.slice(Math.max(0, pos - 80), pos));
    if (!ev) return null;
    return { from: pos - ev[2].length, options: api.events.map((e) => ({ label: e.name, type: "event" as const, detail: e.filter.length ? `{${e.filter.join(", ")}}` : "", info: loc(e.doc, lang) })) };
  }
  const { parts, from, colon } = pathBefore(text, pos);
  if (!parts.length || (parts.length === 1 && !parts[0])) return explicit ? { from: pos, options: membersOf(api, "", lang) } : null;
  const ns = colon ? "string" : parts.slice(0, -1).join(".");
  const options = membersOf(api, ns, lang);
  return options.length ? { from, options } : null;
}

// ------------------------------------------------------------------ hover

export interface HoverDoc {
  /** what is hovered: cloud.press, string.format, … */
  name: string;
  signature: string;
  doc: string;
  params: { name: string; type: string; doc: string }[];
  returns: { type: string; doc: string }[];
  perm?: string;
  kind?: ApiFunction["kind"];
  example?: string;
  from: number;
  to: number;
}

/** The name under `pos` (dotted), with its range. */
export function wordAt(text: string, pos: number): { word: string; from: number; to: number } | null {
  let a = pos;
  let b = pos;
  while (a > 0 && /[\w.]/.test(text[a - 1])) a--;
  while (b < text.length && /\w/.test(text[b])) b++;
  if (a === b) return null;
  const word = text.slice(a, b).replace(/^\.+/, "");
  if (!word || /^\d/.test(word)) return null;
  // hovering "cloud" in "cloud.press" means cloud; hovering "press" means the whole path
  return { word, from: b - word.length, to: b };
}

export function functionByName(api: ApiDescriptor, name: string): ApiFunction | undefined {
  return api.functions.find((f) => f.name === name);
}

export function hoverAt(api: ApiDescriptor, lang: Lang, text: string, pos: number): HoverDoc | null {
  if (luaStateAt(text, pos) !== "code") return null;
  const w = wordAt(text, pos);
  if (!w) return null;
  const { word, from, to } = w;
  const prefix = `${api.name}.`;
  if (word === api.name) return { name: api.name, signature: api.name, doc: loc(api.doc, lang), params: [], returns: [], from, to };
  if (word.startsWith(prefix)) {
    const name = word.slice(prefix.length);
    const f = functionByName(api, name);
    if (f) {
      const target = f.aliasOf ? functionByName(api, f.aliasOf) : undefined;
      return {
        name: `${prefix}${f.name}`,
        signature: signature(api, f),
        doc: loc(f.doc, lang),
        params: f.params.map((p) => ({ name: p.variadic ? "..." : p.name, type: p.type + (p.optional ? "?" : ""), doc: loc(p.doc, lang) })),
        returns: f.returns.map((r) => ({ type: r.type, doc: loc(r.doc, lang) })),
        perm: f.perm ?? target?.perm,
        kind: f.kind,
        example: f.example ?? target?.example,
        from,
        to,
      };
    }
    const field = api.fields.find((x) => x.name === name);
    if (field) return { name: word, signature: `${word}: ${field.type}`, doc: loc(field.doc, lang), params: [], returns: [], from, to };
    if (api.functions.some((x) => x.name.startsWith(`${name}.`))) return { name: word, signature: word, doc: "", params: [], returns: [], from, to };
    return null;
  }
  const dot = word.indexOf(".");
  const lib = dot > 0 ? word.slice(0, dot) : null;
  if (lib && api.lua.libraries[lib]?.includes(word.slice(dot + 1))) return { name: word, signature: word, doc: lang === "de" ? "Lua-Standardbibliothek" : "Lua standard library", params: [], returns: [], from, to };
  if (!lib && (api.lua.globals.includes(word) || api.lua.libraries[word])) return { name: word, signature: word, doc: lang === "de" ? "Lua-Standardbibliothek" : "Lua standard library", params: [], returns: [], from, to };
  if (api.lua.removed.includes(word)) return { name: word, signature: word, doc: lang === "de" ? "Gibt es in Cloud Scripts nicht (Sandbox)." : "Not available in Cloud Scripts (sandbox).", params: [], returns: [], from, to };
  return null;
}

// ------------------------------------------------------------------ signature help

export interface SignatureHelp {
  fn: ApiFunction;
  label: string;
  /** the parameter the cursor is in (clamped to the last one for variadics) */
  param: number;
  /** where the call's "(" is */
  open: number;
}

/** The innermost open cloud.* call before `pos` and which argument the cursor is in. */
export function signatureAt(api: ApiDescriptor, text: string, pos: number): SignatureHelp | null {
  const start = Math.max(0, pos - 2000);
  const masked = maskLua(text.slice(0, pos)).slice(start);
  let depth = 0;
  let commas = 0;
  for (let i = masked.length - 1; i >= 0; i--) {
    const c = masked[i];
    if (c === ")" || c === "}" || c === "]") depth++;
    else if (c === "(" || c === "{" || c === "[") {
      if (depth > 0) {
        depth--;
        continue;
      }
      if (c !== "(") {
        // inside a table constructor: its commas are not arguments
        commas = 0;
        continue;
      }
      const head = /([\w.]+)\s*$/.exec(masked.slice(0, i));
      const prefix = `${api.name}.`;
      if (!head || !head[1].startsWith(prefix)) return null;
      const fn = functionByName(api, head[1].slice(prefix.length));
      if (!fn) return null;
      const last = fn.params.length - 1;
      const param = last < 0 ? 0 : Math.min(commas, fn.params[last].variadic ? last : Math.max(last, 0));
      return { fn, label: signature(api, fn), param: commas > last && !fn.params[last]?.variadic ? -1 : param, open: start + i };
    } else if (c === "," && depth === 0) commas++;
    // a new line ends the search unless the line before continues the arguments (a function body is not one)
    else if (depth === 0 && c === "\n" && !/[,({]\s*$/.test(masked.slice(Math.max(0, i - 200), i))) return null;
  }
  return null;
}

// ------------------------------------------------------------------ sandbox hints

export interface SandboxUse {
  from: number;
  to: number;
  name: string;
}
/** Uses of names the sandbox removes (io, require, os.execute, …), outside strings and comments. */
export function removedUses(api: ApiDescriptor, text: string): SandboxUse[] {
  const masked = maskLua(text);
  const out: SandboxUse[] = [];
  const re = /(?<![\w.:])([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)?)/g;
  for (let m = re.exec(masked); m && out.length < 50; m = re.exec(masked)) {
    const name = m[1];
    const top = name.split(".")[0];
    // `local io = …` or a field name in a table constructor is the script's own
    if (/\blocal\s+$/.test(masked.slice(Math.max(0, m.index - 12), m.index)) || /^\s*=(?!=)/.test(masked.slice(m.index + name.length))) continue;
    if (api.lua.removed.includes(name)) out.push({ from: m.index, to: m.index + name.length, name });
    else if (name.includes(".") && api.lua.removed.includes(top) && !api.lua.libraries[top]) out.push({ from: m.index, to: m.index + top.length, name: top });
  }
  return out;
}
