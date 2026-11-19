// Builds the WebAssembly modules the mod's tests use from their AssemblyScript sources, with the pinned compiler
// (package-lock.json): tests/*.ts -> src/test/resources/wasm/*.wasm (unit tests) and examples/porch-light.ts ->
// src/main/resources/cccloud_gametest/wasm/porch-light.wasm (GameTest). Run from this folder: npm ci && npm run build
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const asc = join(root, "node_modules", ".bin", "asc");
// Size-optimised, incremental garbage collector (scripts run for days), memory not exported (the host reads it anyway).
export const FLAGS = ["-Oz", "--runtime", "incremental", "--noExportMemory"];

function build(source, out) {
  mkdirSync(join(root, dirname(out)), { recursive: true });
  // relative paths only: nothing of the machine that built it ends up in the module
  execFileSync(asc, [source, "-o", out, ...FLAGS], { cwd: root, stdio: "inherit" });
  const bytes = readFileSync(join(root, out));
  const text = bytes.toString("latin1");
  for (const bad of ["/Users/", "/home/", "/private/", "/tmp/", "C:\\"]) {
    if (text.includes(bad)) throw new Error(`${out} contains a local path (${bad})`);
  }
  console.log(`${out}: ${bytes.length} bytes`);
}

for (const file of readdirSync(join(root, "tests")).filter((f) => f.endsWith(".ts")).sort()) {
  build(`tests/${file}`, `../../src/test/resources/wasm/${file.replace(/\.ts$/, ".wasm")}`);
}
build("examples/porch-light.ts", "../../src/main/resources/cccloud_gametest/wasm/porch-light.wasm");
