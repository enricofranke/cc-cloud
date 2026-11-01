// The page's side of the simulation: stands in for fetch and WebSocket, so the dashboard runs unchanged while everything
// it says to "the server" goes to the worker (worker.ts). Same-origin static files still go through the real fetch.
import type { FromWorker, ToWorker } from "./session.ts";

let worker: Worker;
const post = (m: ToWorker, transfer: Transferable[] = []) => worker.postMessage(m, transfer);

let seq = 0;
const pending = new Map<number, (m: Extract<FromWorker, { t: "http" }>) => void>();
const sockets = new Map<number, FakeWebSocket>();

function onMessage(ev: MessageEvent<FromWorker>) {
  const m = ev.data;
  if (m.t === "http") {
    pending.get(m.id)?.(m);
    pending.delete(m.id);
    return;
  }
  const s = sockets.get(m.id);
  if (!s) return;
  if (m.t === "ws_open") s.opened();
  else if (m.t === "ws_message") s.message(m.data);
  else s.closed(m.code, m.reason);
}

/** A WebSocket whose other end is the worker. */
export class FakeWebSocket extends EventTarget {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;
  readonly CONNECTING = 0;
  readonly OPEN = 1;
  readonly CLOSING = 2;
  readonly CLOSED = 3;
  readyState = 0;
  readonly url: string;
  readonly id = ++seq;
  onopen: ((ev: Event) => void) | null = null;
  onmessage: ((ev: MessageEvent) => void) | null = null;
  onclose: ((ev: CloseEvent) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;
  constructor(url: string | URL) {
    super();
    this.url = String(url);
    sockets.set(this.id, this);
    post({ t: "ws_open", id: this.id });
  }
  send(data: string) {
    if (this.readyState === 1) post({ t: "ws_send", id: this.id, data });
  }
  close(code?: number, reason?: string) {
    if (this.readyState >= 2) return;
    this.readyState = 2;
    post({ t: "ws_close", id: this.id, code, reason });
  }
  opened() {
    if (this.readyState !== 0) return;
    this.readyState = 1;
    const ev = new Event("open");
    this.onopen?.(ev);
    this.dispatchEvent(ev);
  }
  message(data: string) {
    const ev = new MessageEvent("message", { data });
    this.onmessage?.(ev);
    this.dispatchEvent(ev);
  }
  closed(code: number, reason: string) {
    this.readyState = 3;
    sockets.delete(this.id);
    const ev = new CloseEvent("close", { code, reason, wasClean: code !== 1006 });
    this.onclose?.(ev);
    this.dispatchEvent(ev);
  }
}

/** The paths the mod's web server answers. Anything else is a static file of the site. */
const SIMULATED = /^\/(api|dev)\//;

async function bodyBytes(init: RequestInit | undefined, input: RequestInfo | URL) {
  const b = init?.body ?? (input instanceof Request ? await input.clone().arrayBuffer() : undefined);
  if (b === undefined || b === null) return undefined;
  if (typeof b === "string") return new TextEncoder().encode(b);
  if (b instanceof Uint8Array) return new Uint8Array(b);
  if (b instanceof ArrayBuffer) return new Uint8Array(b);
  return new Uint8Array(await new Response(b).arrayBuffer());
}

export function installBridge() {
  const realFetch = window.fetch.bind(window);
  worker = new Worker(new URL("./worker.ts", import.meta.url), { type: "module", name: "cc-cloud-simulated-base" });
  worker.onmessage = onMessage;

  window.fetch = async (input, init) => {
    const raw = input instanceof Request ? input.url : String(input);
    const url = new URL(raw, location.href);
    if (url.origin !== location.origin || !SIMULATED.test(url.pathname)) return realFetch(input, init);
    const headers = Object.fromEntries(new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined)));
    const body = await bodyBytes(init, input);
    const id = ++seq;
    const answer = await new Promise<Extract<FromWorker, { t: "http" }>>((resolve) => {
      pending.set(id, resolve);
      post({ t: "http", id, method: (init?.method ?? (input instanceof Request ? input.method : "GET")).toUpperCase(), url: url.pathname + url.search, headers, body }, body ? [body.buffer as ArrayBuffer] : []);
    });
    const empty = answer.status === 204 || answer.status === 304;
    return new Response(empty ? null : (answer.body as BodyInit | null), { status: answer.status, headers: answer.headers });
  };
  window.WebSocket = FakeWebSocket as unknown as typeof WebSocket;

  // a hidden tab stands still: no simulation, no battery
  const visibility = () => post({ t: "pause", paused: document.hidden });
  document.addEventListener("visibilitychange", visibility);
  if (document.hidden) visibility();
}
