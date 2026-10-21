// Starts the dev mock server: `pnpm mock` (API + WebSocket on :8787, serves the
// last build from ../src/main/resources/cccloud_web).
//   --port 8787        listen port
//   --approve-ms 3000  auto-approve pairings after this delay (-1 = never; names containing "deny" are denied)
//   --lan-only         simulate webAllowInternet=false (non-LAN Host headers get 403)
//   --no-extras        only one mainframe, no offline uplink
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { parseArgs } from "node:util";
import { createMockServer } from "./mock-server.ts";

const { values } = parseArgs({
  options: {
    port: { type: "string" },
    "approve-ms": { type: "string", default: "3000" },
    "lan-only": { type: "boolean", default: false },
    "no-extras": { type: "boolean", default: false },
    "no-static": { type: "boolean", default: false },
  },
});
const here = fileURLToPath(new URL(".", import.meta.url));
const mock = await createMockServer({
  port: Number(values.port ?? process.env.PORT ?? 8787),
  extras: !values["no-extras"],
  approveDelayMs: Number(values["approve-ms"]),
  lanOnly: values["lan-only"],
  staticDir: values["no-static"] ? undefined : resolve(here, "../../src/main/resources/cccloud_web"),
});
console.log("[mock] PIN for PIN-protected widgets: 1234");
const stop = async () => {
  await mock.close();
  process.exit(0);
};
process.on("SIGINT", stop);
process.on("SIGTERM", stop);
