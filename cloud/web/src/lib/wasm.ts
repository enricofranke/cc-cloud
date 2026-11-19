// WebAssembly Cloud Scripts: the stored form the mod expects (code.wasm.WasmSource on the server) – a small JSON object
// with the compiled module in base64 and, optionally, the text it was compiled from, so the dashboard can show what
// runs. The module is the truth; the server checks it (imports, memory, size) when it is saved and before every start.

export const WASM_FORMAT = "cccloud-wasm";

export interface WasmStored {
  /** bytes of the module */
  moduleBytes: number;
  source: string | null;
  sourceLanguage: string | null;
}

/** A .wasm file starts with \0asm. */
export function isWasmModule(bytes: Uint8Array): boolean {
  return bytes.length >= 8 && bytes[0] === 0 && bytes[1] === 0x61 && bytes[2] === 0x73 && bytes[3] === 0x6d;
}

function base64(bytes: Uint8Array): string {
  let bin = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) bin += String.fromCharCode(...bytes.subarray(i, i + chunk));
  return btoa(bin);
}

/** The stored form of a module and its (optional) source. */
export function encodeWasm(module: Uint8Array, source: string | null, sourceLanguage: string | null): string {
  const o: Record<string, string> = { format: WASM_FORMAT, wasm: base64(module) };
  if (source != null) o.source = source;
  if (sourceLanguage) o.sourceLanguage = sourceLanguage;
  return JSON.stringify(o);
}

/** What a stored WebAssembly script holds; null if it is not one. */
export function decodeWasm(stored: string): WasmStored | null {
  let o: unknown;
  try {
    o = JSON.parse(stored);
  } catch {
    return null;
  }
  if (!o || typeof o !== "object") return null;
  const r = o as Record<string, unknown>;
  if (r.format !== WASM_FORMAT || typeof r.wasm !== "string") return null;
  const pad = r.wasm.endsWith("==") ? 2 : r.wasm.endsWith("=") ? 1 : 0;
  return {
    moduleBytes: Math.max(0, Math.floor((r.wasm.length * 3) / 4) - pad),
    source: typeof r.source === "string" ? r.source : null,
    sourceLanguage: typeof r.sourceLanguage === "string" ? r.sourceLanguage.slice(0, 32) : null,
  };
}

/** The source language from a file name: script.ts → assemblyscript. */
export function languageOfFile(name: string): string | null {
  const n = name.toLowerCase();
  if (n.endsWith(".ts") || n.endsWith(".as")) return "assemblyscript";
  if (n.endsWith(".rs")) return "rust";
  return null;
}

/** What the editor shows for a stored WebAssembly script: its source, or a note that none was stored. */
export function wasmView(stored: string, noSource: string): string {
  const w = decodeWasm(stored);
  if (!w) return stored;
  return w.source ?? noSource;
}
