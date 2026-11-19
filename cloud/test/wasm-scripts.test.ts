// WebAssembly Cloud Scripts on the dashboard side: the stored form (module in base64 + optional source) the mod's
// code.wasm.WasmSource reads, the module check before an upload, and the summary's language.
import { describe, expect, it } from "vitest";
import { decodeWasm, encodeWasm, isWasmModule, languageOfFile, wasmView, WASM_FORMAT } from "../web/src/lib/wasm.ts";
import { parseLimits, parseSummary } from "../web/src/lib/code.ts";

const MODULE = new Uint8Array([0, 0x61, 0x73, 0x6d, 1, 0, 0, 0, 1, 4, 1, 0x60, 0, 0]);

describe("stored form", () => {
  it("round-trips module and source like the server reads it", () => {
    const stored = encodeWasm(MODULE, 'export function main(): void {\n  cloud.log("hi");\n}\n', "assemblyscript");
    const o = JSON.parse(stored);
    expect(o.format).toBe(WASM_FORMAT);
    expect(Uint8Array.from(atob(o.wasm), (c) => c.charCodeAt(0))).toEqual(MODULE);
    const back = decodeWasm(stored)!;
    expect(back.moduleBytes).toBe(MODULE.length);
    expect(back.source).toContain('cloud.log("hi")');
    expect(back.sourceLanguage).toBe("assemblyscript");
  });

  it("works without source and for large modules (chunked base64)", () => {
    const big = new Uint8Array(300_000);
    big.set(MODULE);
    for (let i = 8; i < big.length; i++) big[i] = i & 0xff;
    const stored = encodeWasm(big, null, null);
    const back = decodeWasm(stored)!;
    expect(back.moduleBytes).toBe(big.length);
    expect(back.source).toBeNull();
    expect(Uint8Array.from(atob(JSON.parse(stored).wasm), (c) => c.charCodeAt(0))).toEqual(big);
    expect(wasmView(stored, "// none")).toBe("// none");
  });

  it("is not confused by other text", () => {
    expect(decodeWasm("print('lua')")).toBeNull();
    expect(decodeWasm('{"format":"other","wasm":"AA=="}')).toBeNull();
    expect(decodeWasm('{"format":"cccloud-wasm"}')).toBeNull();
    expect(decodeWasm("[]")).toBeNull();
    expect(wasmView("local x = 1", "// none")).toBe("local x = 1");
  });

  it("checks the magic bytes and names the source language", () => {
    expect(isWasmModule(MODULE)).toBe(true);
    expect(isWasmModule(new TextEncoder().encode("print('x')"))).toBe(false);
    expect(isWasmModule(new Uint8Array([0, 0x61, 0x73]))).toBe(false);
    expect(languageOfFile("porch-light.ts")).toBe("assemblyscript");
    expect(languageOfFile("main.RS")).toBe("rust");
    expect(languageOfFile("notes.txt")).toBeNull();
  });
});

describe("protocol", () => {
  it("reads the language of a script and the wasm limits", () => {
    expect(parseSummary({ id: "0a1b2c3d", name: "x", language: "wasm" })!.language).toBe("wasm");
    expect(parseSummary({ id: "0a1b2c3d", name: "x", language: "brainfuck" })!.language).toBe("lua");
    expect(parseSummary({ id: "0a1b2c3d", name: "x" })!.language).toBe("lua");
    const l = parseLimits({ maxWasmBytes: 65536, maxWasmSource: 1024 });
    expect(l.maxWasmBytes).toBe(65536);
    expect(l.maxWasmSource).toBe(1024);
    expect(parseLimits({}).maxWasmBytes).toBe(128 * 1024);
  });
});
