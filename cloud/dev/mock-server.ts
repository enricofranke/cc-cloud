// DEV-ONLY: the simulation of dev/mock-core.ts as a real server: Node HTTP + WebSocket on a port, like the mod's
// embedded web server. `pnpm mock`, the tests and the screenshot tools use this. Not shipped.
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { generateKeyPairSync, sign } from "node:crypto";
import { WebSocketServer } from "ws";
import { MAX_LAYOUT_BYTES, SECURITY_HEADERS } from "./protocol.ts";
import { serveStatic } from "./static.ts";
import { mockIcon } from "./icons.ts";
import { createMockCore, type MockOptions as CoreOptions } from "./mock-core.ts";

export { REACTOR, STORAGE, TURBINE, ME, CONTROL, COMPUTER, DEEP_COMPUTER, DEEP_RS, GREENHOUSE, CANVAS, CONSOLE, CONSOLE_STYLE } from "./mock-core.ts";

export interface MockOptions extends CoreOptions {
  port?: number;
  host?: string;
  staticDir?: string;
  maxConnsPerIp?: number;
  maxConns?: number;
}

/** The Script Store build served at /store/: CCCLOUD_STORE_DIST (e.g. ../cc-cloud-scripts/dist after its build-index), else the test snapshot. */
const STORE_DIST = process.env.CCCLOUD_STORE_DIST || fileURLToPath(new URL("../test/fixtures/store/", import.meta.url));

/** A fresh dev signing key for the Script Store (the mod verifies index.json.sig with [store] scriptStoreKey). */
function devStoreKey() {
  const { publicKey, privateKey } = generateKeyPairSync("ed25519");
  const spki = publicKey.export({ type: "spki", format: "der" });
  return { key: Buffer.from(spki.subarray(spki.length - 32)).toString("base64"), sign: (b: Uint8Array) => sign(null, b, privateKey) };
}

export async function createMockServer(opts: MockOptions = {}) {
  const log = opts.log ?? ((m: string) => console.log(`[mock] ${m}`));
  const maxConnsPerIp = opts.maxConnsPerIp ?? 8;
  const maxConns = opts.maxConns ?? 64;
  const connsByIp = new Map<string, number>();
  let connCount = 0;
  let port = 0;

  const core = createMockCore(
    { ...opts, log },
    {
      icon: mockIcon,
      store: {
        ...devStoreKey(),
        file: (rel) => {
          try {
            return readFileSync(join(STORE_DIST, rel));
          } catch {
            return undefined;
          }
        },
        url: () => `http://localhost:${port}/store/`,
      },
      fallback: opts.staticDir ? (pathname, req, res) => serveStatic(opts.staticDir!, pathname, req as never, res as never) : undefined,
    },
  );

  const server = createServer((req, res) =>
    core.handle(req, res).catch((e) => {
      log(`http error ${(e as Error).message}`);
      if (!res.headersSent) {
        res.writeHead(500, { ...SECURITY_HEADERS, "content-type": "application/json", "cache-control": "no-store" });
        res.end(JSON.stringify({ error: "internal" }));
      }
    }),
  );
  // 64 KB frames so a layout_set fits; every other message is still limited to 8 KB (dev/mock-core.ts)
  const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_LAYOUT_BYTES + 1024 });
  server.on("upgrade", (req, socket, head) => {
    socket.on("error", () => {});
    const url = new URL(req.url ?? "/", "http://mock");
    const deny = (status: string) => socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\n\r\n`);
    if (url.pathname !== "/ws/client") return void deny("404 Not Found");
    if (core.lanBlocked(req)) return void deny("403 Forbidden");
    // Cross-site WebSocket hijacking protection: Origin must match Host.
    const origin = req.headers.origin;
    if (origin) {
      let originHost = "";
      try {
        originHost = new URL(origin).host;
      } catch {}
      const allowed = [req.headers.host, req.headers["x-forwarded-host"]].filter(Boolean);
      if (!allowed.includes(originHost)) return void deny("403 Forbidden");
    }
    const ip = req.socket.remoteAddress ?? "?";
    if (connCount >= maxConns || (connsByIp.get(ip) ?? 0) >= maxConnsPerIp) return void deny("429 Too Many Requests");
    connCount++;
    connsByIp.set(ip, (connsByIp.get(ip) ?? 0) + 1);
    wss.handleUpgrade(req, socket, head, (ws) => {
      core.handleSocket(ws);
      ws.on("close", () => {
        connCount--;
        const n = (connsByIp.get(ip) ?? 1) - 1;
        if (n <= 0) connsByIp.delete(ip);
        else connsByIp.set(ip, n);
      });
    });
  });

  core.start();
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(opts.port ?? 8787, opts.host ?? "0.0.0.0", resolve);
  });
  port = (server.address() as AddressInfo).port;
  for (const m of core.mainframes.values()) log(`${m.name}: pairing code ${core.code(m)}`);
  log(`listening on http://localhost:${port}`);

  return {
    ...core,
    port,
    /** The fake AE2 network (undefined with ae2:false). */
    get ae2() {
      return core.ae2;
    },
    async close() {
      core.close();
      wss.clients.forEach((c) => c.terminate());
      await new Promise<void>((r) => server.close(() => r()));
    },
  };
}

export type MockServer = Awaited<ReturnType<typeof createMockServer>>;
