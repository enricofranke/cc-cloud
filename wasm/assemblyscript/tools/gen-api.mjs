// Generates sdk/cloud.ts (typed wrappers of every cloud.* function) from the mod's API descriptor, the same file the
// Lua binding and the dashboard editor are generated from. Run: npm run gen
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const spec = JSON.parse(readFileSync(join(root, "../../src/main/resources/cccloud_code/api.json"), "utf8"));

// AssemblyScript names for descriptor names that are reserved words.
const RENAMED = { delete: "remove" };

function resolve(type) {
  const t = spec.types[type];
  return t && t.kind === "alias" ? t.of : type;
}

function param(fn, p, generics, nullable) {
  const type = resolve(p.type);
  if (p.variadic) {
    generics.push("T");
    return { decl: `message: T`, value: `Value.from(message)` };
  }
  switch (type) {
    case "string":
      return p.optional ? { decl: `${p.name}: string | null = null`, value: `Value.from(${p.name})` } : { decl: `${p.name}: string`, value: `Value.string(${p.name})` };
    case "number":
      // NaN = left out (it becomes null in JSON)
      return { decl: `${p.name}: f64${p.optional ? " = NaN" : ""}`, value: `Value.number(${p.name})` };
    case "integer":
      return { decl: `${p.name}: i64`, value: `Value.number(<f64>${p.name})` };
    case "boolean":
      return { decl: `${p.name}: bool`, value: `Value.bool(${p.name})` };
    case "function":
      return fn.name === "on"
        ? { decl: `${p.name}: EventHandler`, value: `eventHandler(${p.name})` }
        : { decl: `${p.name}: TimerHandler`, value: `timerHandler(${p.name})` };
    case "any": {
      const g = "T" + (generics.length ? generics.length + 1 : "");
      generics.push(g);
      return { decl: `${p.name}: ${g}`, value: `Value.from(${p.name})` };
    }
    default: // table and named table types
      if (nullable && !p.optional) return { decl: `${p.name}: Value | null`, value: `Value.from(${p.name})` };
      return p.optional ? { decl: `${p.name}: Value | null = null`, value: `Value.from(${p.name})` } : { decl: `${p.name}: Value`, value: p.name };
  }
}

function returns(fn) {
  const r = fn.returns.map((x) => x.type);
  if (r.length === 0) return { type: "void", wrap: (call) => `${call};` };
  if (r.length === 2 && r[0] === "boolean") return { type: "Result", wrap: (call) => `return result(${call});` };
  if (r.length === 1 && r[0] === "boolean") return { type: "bool", wrap: (call) => `return first(${call}).asBool();` };
  if (r.length === 1 && r[0] === "integer") return { type: "i64", wrap: (call) => `return <i64>first(${call}).asNumber();` };
  if (r.length === 1 && r[0] === "number") return { type: "f64", wrap: (call) => `return first(${call}).asNumber();` };
  return { type: "Value", wrap: (call) => `return first(${call});` };
}

function doc(fn, indent) {
  const lines = [fn.doc?.en ?? ""];
  for (const p of fn.params) if (p.doc?.en) lines.push(`@param ${p.variadic ? "message" : p.name} ${p.doc.en}`);
  return `${indent}/**\n${lines.map((l) => `${indent} * ${l}`).join("\n")}\n${indent} */\n`;
}

function render(fn, indent) {
  const path = fn.name.split(".");
  const local = RENAMED[path[path.length - 1]] ?? path[path.length - 1];
  const generics = [];
  // an optional parameter before a required one takes no default (pass null): cloud.on("change", null, handler)
  const params = fn.params.map((p, i) => param(fn, { ...p, optional: p.optional && fn.params.slice(i + 1).every((q) => q.optional || q.variadic) }, generics,
    p.optional));
  const ret = returns(fn);
  const g = generics.length ? `<${generics.join(", ")}>` : "";
  const call = `invoke("cloud.${fn.name}", [${params.map((p) => p.value).join(", ")}])`;
  return `${doc(fn, indent)}${indent}export function ${local}${g}(${params.map((p) => p.decl).join(", ")}): ${ret.type} {\n${indent}  ${ret.wrap(call)}\n${indent}}\n`;
}

let top = "";
const groups = {};
for (const fn of spec.functions) {
  const path = fn.name.split(".");
  if (path.length === 1) top += render(fn, "  ") + "\n";
  else (groups[path[0]] ??= []).push(fn);
}
// descriptor fields (cloud.script) are functions without arguments here
for (const field of spec.fields ?? []) {
  top += `  /**\n   * ${field.doc?.en ?? field.name}\n   */\n  export function ${field.name}(): Value {\n    return first(invoke("cloud.${field.name}", []));\n  }\n\n`;
}
let nested = "";
for (const [name, fns] of Object.entries(groups)) {
  nested += `  export namespace ${name} {\n${fns.map((fn) => render(fn, "    ")).join("\n")}  }\n`;
}

const out = `// Generated from src/main/resources/cccloud_code/api.json by tools/gen-api.mjs (npm run gen). Do not edit.
// API version ${spec.version}.
import { Value } from "./value";
import { EventHandler, Result, TimerHandler, eventHandler, first, invoke, result, timerHandler } from "./host";

export namespace cloud {
${top}${nested}}
`;
writeFileSync(join(root, "sdk/cloud.ts"), out);
console.log(`sdk/cloud.ts: ${spec.functions.length} functions (API version ${spec.version})`);
