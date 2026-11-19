# Cloud Scripts in AssemblyScript

Cloud Scripts can be WebAssembly modules. This folder has the SDK for writing them in
[AssemblyScript](https://www.assemblyscript.org/) (TypeScript syntax), an example, and the build of the modules the mod's
tests use.

## Write a script

```ts
import { cloud, Value } from "./sdk";
export { cccloud_handle } from "./sdk"; // needed: the mod calls your handlers through it

let lamp = "";

function onHeat(e: Value): void {
  const hot = e.get("value").asNumber() > 80;
  cloud.set("Reactor", lamp, hot);
}

export function main(): void {
  lamp = "alarm";
  cloud.log("watching " + cloud.getCard("Reactor").get("title").asString());
  cloud.on("change", Value.object().set("card", "Reactor").set("widget", "heat"), onHeat);
}
```

- `main()` runs when the script starts. Top-level code runs before it.
- Every `cloud.*` function of the Lua API is here with the same name and arguments (`cloud.store.delete` is
  `cloud.store.remove`, `delete` is a reserved word). Tables are `Value`s: `Value.object().set("confirm", true)`,
  `v.get("title").asString()`, `v.at(0)`, `v.length`.
- Actions (`cloud.set`, `cloud.press`, …) return a `Result` with `ok` and `error` (`pin_required`, `unknown_card`, …).
- Handlers are top-level functions (AssemblyScript has no closures): keep what they need in top-level variables.
- A wrong call (bad argument, unknown event) or an uncaught `throw` stops the script with the error and its line, like
  an uncaught error in Lua.
- Limits are the same as for Lua scripts (CPU per second and per stretch without waiting, timers, handlers, storage),
  plus a hard memory cap (16 MB) and at most 2 000 calls into the mod per second.

## Build it

```sh
npm install --save-dev assemblyscript@0.28.20
npx asc myscript.ts -o myscript.wasm -Oz --runtime incremental --noExportMemory
```

Copy `sdk/` next to your script (or point the import at it). Then in the dashboard: Cloud Scripts → New script →
WebAssembly (.wasm): pick `myscript.wasm` and, optionally, `myscript.ts` so the dashboard shows the source. To change
it, build again and use "Upload new version".

The module may only import what the SDK imports (`cccloud.call`, `cccloud.take`, and `env.abort`, `env.trace`,
`env.seed` from AssemblyScript's runtime). WASI is not available: build without it.

## In this repository

- `sdk/cloud.ts` is generated from the mod's API descriptor: `npm run gen` after a change of
  `src/main/resources/cccloud_code/api.json`.
- `npm ci && npm run build` rebuilds the committed modules from their sources: `tests/*.ts` →
  `src/test/resources/wasm/*.wasm` (unit tests) and `examples/porch-light.ts` →
  `src/main/resources/cccloud_gametest/wasm/porch-light.wasm` (GameTest).
