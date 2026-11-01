// The simulated base of the live demo: dev/mock-core.ts in a Web Worker. Everything the mod's web server would do runs
// here, in the visitor's browser and in memory. No network.
import "./node/globals.ts";
import { PLAYERS } from "../../dev/access-sim.ts";
import { createMockCore, type MockRequest, type MockResponse, type MockSocket } from "../../dev/mock-core.ts";
import { MAINFRAMES, PLAYER, TOKEN, type FromWorker, type ToWorker } from "./session.ts";

PLAYERS[0].player = PLAYER;

const post = (m: FromWorker, transfer: Transferable[] = []) => (self as unknown as Worker).postMessage(m, transfer);

const core = createMockCore({
  extras: true,
  scriptStore: false,
  // a pairing needs a player in the game: nothing approves one here
  approveDelayMs: -1,
  unlimited: true,
  pinLockMs: 20_000,
  mainframeId: (name) => MAINFRAMES.find((m) => m.name === name)?.id,
  log: () => {},
});

// the showcase keeps the mainframes worth seeing; the others stand for an empty or switched-off base
for (const m of [...core.mainframes.values()]) if (!MAINFRAMES.some((x) => x.id === m.id)) core.mainframes.delete(m.id);
core.newToken(undefined, "This browser", true, { uuid: PLAYERS[0].uuid }, TOKEN);
core.start();

// ---- one simulated connection per FakeWebSocket on the page
class Socket implements MockSocket {
  readyState = 1;
  private listeners: Record<string, ((...a: any[]) => void)[]> = {};
  readonly id: number;
  constructor(id: number) {
    this.id = id;
  }
  send(data: string) {
    post({ t: "ws_message", id: this.id, data });
  }
  close(code = 1000, reason = "") {
    if (this.readyState !== 1) return;
    this.readyState = 3;
    post({ t: "ws_close", id: this.id, code, reason });
    this.emit("close");
  }
  terminate() {
    this.close(1006);
  }
  on(event: string, listener: (...a: any[]) => void) {
    (this.listeners[event] ??= []).push(listener);
    return this;
  }
  emit(event: string, ...args: unknown[]) {
    this.listeners[event]?.forEach((l) => l(...args));
  }
}
const sockets = new Map<number, Socket>();

// ---- one simulated HTTP request
async function http(m: Extract<ToWorker, { t: "http" }>) {
  const req: MockRequest = {
    method: m.method,
    url: m.url,
    headers: m.headers,
    socket: { remoteAddress: "127.0.0.1" },
    async *[Symbol.asyncIterator]() {
      if (m.body?.length) yield Buffer.from(m.body);
    },
  };
  const answer = { status: 200, headers: {} as Record<string, string>, parts: [] as Uint8Array[] };
  const encoder = new TextEncoder();
  let settle = () => {};
  const finished = new Promise<void>((r) => (settle = r));
  const res: MockResponse = {
    headersSent: false,
    writeHead(status, headers = {}) {
      answer.status = status;
      answer.headers = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k, String(v)]));
      this.headersSent = true;
    },
    end(body) {
      if (body !== undefined) answer.parts.push(typeof body === "string" ? encoder.encode(body) : body);
      settle();
    },
  };
  try {
    await core.handle(req, res);
    await finished;
  } catch {
    answer.status = 500;
    answer.parts = [encoder.encode('{"error":"internal"}')];
  }
  const merged = Buffer.concat(answer.parts);
  // a fresh copy: the simulation caches its map tiles, and a transferred buffer would be gone
  const body = new Uint8Array(merged);
  post({ t: "http", id: m.id, status: answer.status, headers: answer.headers, body: answer.parts.length ? body : null }, [body.buffer]);
}

self.onmessage = (ev: MessageEvent<ToWorker>) => {
  const m = ev.data;
  switch (m.t) {
    case "pause":
      if (m.paused) core.stop();
      else core.start();
      break;
    case "http":
      void http(m);
      break;
    case "ws_open": {
      const s = new Socket(m.id);
      sockets.set(m.id, s);
      s.on("close", () => sockets.delete(m.id));
      core.handleSocket(s);
      post({ t: "ws_open", id: m.id });
      break;
    }
    case "ws_send":
      sockets.get(m.id)?.emit("message", m.data);
      break;
    case "ws_close":
      sockets.get(m.id)?.close(m.code ?? 1000, m.reason ?? "");
      break;
  }
};
