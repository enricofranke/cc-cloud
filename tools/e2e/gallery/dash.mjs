// A dashboard client for the gallery scripts: same WebSocket as the web app, with a token issued by the dev commands.
import { createRequire } from "node:module";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(here, "../../../cloud/package.json"));
const WebSocket = require("ws");

export const PORT = Number(process.env.WEB_PORT ?? 8797);
export const ORIGIN = `http://127.0.0.1:${PORT}`;

export class Dash {
  sections = new Map();
  inbox = [];
  waiters = [];
  head = null;
  req = 0;

  /** Opens a session and folds the first snapshot; `settle` ms later the sections are complete. */
  static async open(token, { mainframeId = null, settle = 3000 } = {}) {
    if (!token) throw new Error("no dashboard token (the report script makes it)");
    const d = new Dash();
    d.ws = new WebSocket(`ws://127.0.0.1:${PORT}/ws/client`, { headers: { Origin: ORIGIN }, maxPayload: 256 * 1024 * 1024 });
    d.ws.on("message", (data) => d.onMessage(JSON.parse(String(data))));
    await new Promise((resolve, reject) => { d.ws.once("open", resolve); d.ws.once("error", reject); });
    d.closed = new Promise((r) => d.ws.on("close", (code, reason) => r({ code, reason: String(reason) })));
    d.ws.send(JSON.stringify({ t: "auth", token, ...(mainframeId ? { mainframeId } : {}) }));
    const first = await Promise.race([d.next((m) => m.t === "snapshot" || m.t === "error", 20_000, "snapshot"), d.closed]);
    if (first?.t !== "snapshot") throw new Error(`no snapshot: ${JSON.stringify(first)}`);
    d.head = first;
    for (const u of first.uplinks ?? []) d.sections.set(u.uplinkId, u);
    await new Promise((r) => setTimeout(r, settle));
    return d;
  }

  onMessage(m) {
    if (m.t === "ping") this.ws.send(JSON.stringify({ t: "pong" }));
    if (m.t === "state") Object.assign(this.head?.mainframe ?? {}, m);
    if (m.t === "uplink") this.sections.set(m.uplinkId, m);
    if (m.t === "uplink_removed") this.sections.delete(m.uplinkId);
    if (m.t === "values") {
      for (const [uplinkId, values] of Object.entries(m.s ?? {})) {
        const widgets = this.sections.get(uplinkId)?.widgets;
        if (!widgets) continue;
        for (const [id, value] of Object.entries(values)) {
          const w = widgets.find((x) => x.id === id);
          if (w) w.value = value;
        }
      }
    }
    this.inbox.push(m);
    if (this.inbox.length > 20000) this.inbox.splice(0, 10000);
    for (const w of [...this.waiters]) {
      if (w.pred(m)) {
        this.waiters.splice(this.waiters.indexOf(w), 1);
        w.resolve(m);
      }
    }
  }

  next(pred, ms = 10_000, label = "message") {
    const found = this.inbox.find(pred);
    if (found) { this.inbox.splice(this.inbox.indexOf(found), 1); return Promise.resolve(found); }
    return new Promise((resolve, reject) => {
      const w = { pred, resolve: (m) => { clearTimeout(t); this.inbox.splice(this.inbox.indexOf(m), 1); resolve(m); } };
      const t = setTimeout(() => { this.waiters.splice(this.waiters.indexOf(w), 1); reject(new Error(`timeout waiting for ${label}`)); }, ms);
      this.waiters.push(w);
    });
  }

  /** Sends a message with a fresh reqId and waits for the answer of that type. */
  async request(msg, type, ms = 15_000) {
    const reqId = `r${++this.req}`;
    this.ws.send(JSON.stringify({ ...msg, reqId }));
    return this.next((m) => m.t === type && m.reqId === reqId, ms, type);
  }

  http(path, token, init = {}) {
    return fetch(`${ORIGIN}${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, ...(init.headers ?? {}) } });
  }

  close() {
    try { this.ws.close(); } catch {}
  }
}
