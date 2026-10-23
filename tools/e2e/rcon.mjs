#!/usr/bin/env node
// Minimal Source RCON client (Minecraft). Library + CLI.
//   node tools/e2e/rcon.mjs "list" "time query daytime"
// Password: $RCON_PASSWORD or the file $SERVER_DIR/.e2e-rcon-password; port: $RCON_PORT (default 25576).
import net from "node:net";
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export function rconDefaults() {
  const serverDir = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
  const password = process.env.RCON_PASSWORD ?? readFileSync(join(serverDir, ".e2e-rcon-password"), "utf8").trim();
  return { host: "127.0.0.1", port: Number(process.env.RCON_PORT ?? 25576), password };
}

export class Rcon {
  #socket;
  #buffer = Buffer.alloc(0);
  #waiters = new Map();
  #nextId = 1;
  #queue = Promise.resolve();

  static async connect(opts = rconDefaults()) {
    const r = new Rcon();
    await new Promise((resolve, reject) => {
      r.#socket = net.createConnection({ host: opts.host, port: opts.port }, resolve);
      r.#socket.once("error", reject);
    });
    r.#socket.on("data", (d) => r.#onData(d));
    r.#socket.on("error", (e) => { for (const w of r.#waiters.values()) w.reject(e); r.#waiters.clear(); });
    const id = r.#send(3, opts.password);
    const res = await r.#wait(id, true);
    if (res.id === -1) throw new Error("RCON authentication failed");
    return r;
  }

  #send(type, body) {
    const id = this.#nextId++;
    const payload = Buffer.from(body, "utf8");
    const pkt = Buffer.alloc(14 + payload.length);
    pkt.writeInt32LE(10 + payload.length, 0);
    pkt.writeInt32LE(id, 4);
    pkt.writeInt32LE(type, 8);
    payload.copy(pkt, 12);
    this.#socket.write(pkt);
    return id;
  }

  #wait(id, auth = false) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.#waiters.delete(auth ? "auth" : id); reject(new Error("RCON timeout")); }, 120_000);
      this.#waiters.set(auth ? "auth" : id, { resolve: (v) => { clearTimeout(timer); resolve(v); }, reject, parts: [] });
    });
  }

  #onData(chunk) {
    this.#buffer = Buffer.concat([this.#buffer, chunk]);
    while (this.#buffer.length >= 4) {
      const len = this.#buffer.readInt32LE(0);
      if (this.#buffer.length < 4 + len) return;
      const id = this.#buffer.readInt32LE(4);
      const type = this.#buffer.readInt32LE(8);
      const raw = this.#buffer.subarray(12, 4 + len - 2);
      this.#buffer = this.#buffer.subarray(4 + len);
      if (type === 2 && this.#waiters.has("auth")) {
        const w = this.#waiters.get("auth");
        this.#waiters.delete("auth");
        w.resolve({ id, body: raw.toString("utf8") });
        continue;
      }
      const w = this.#waiters.get(id);
      if (!w) continue;
      w.parts.push(raw);
      clearTimeout(w.idle);
      // The server splits answers into 4096-byte packets: a shorter one is the last. A full one may be followed by
      // more, so wait briefly before resolving.
      if (raw.length < 4096) w.finish();
      else w.idle = setTimeout(() => w.finish(), 400);
    }
  }

  /** Runs one command and returns its full output text. Commands run one at a time. */
  async cmd(command) {
    const run = async () => {
      const id = this.#send(2, command);
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { this.#waiters.delete(id); reject(new Error(`RCON timeout: ${command}`)); }, 300_000);
        const w = {
          parts: [], reject,
          finish: () => { clearTimeout(timer); clearTimeout(w.idle); this.#waiters.delete(id); resolve(Buffer.concat(w.parts).toString("utf8")); },
        };
        this.#waiters.set(id, w);
      });
    };
    const p = this.#queue.then(run, run);
    this.#queue = p.catch(() => {});
    return p;
  }

  close() {
    this.#socket.end();
    this.#socket.destroy();
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const rcon = await Rcon.connect();
  try {
    for (const c of process.argv.slice(2)) {
      const out = await rcon.cmd(c);
      console.log(`> ${c}\n${out}`);
    }
  } finally {
    rcon.close();
  }
}
