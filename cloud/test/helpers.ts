import { WebSocket } from "ws";

/** A WebSocket wrapper that buffers incoming JSON messages and lets tests await them. */
export class TestSocket {
  readonly ws: WebSocket;
  readonly messages: any[] = [];
  closeCode?: number;
  private waiters: { pred: (m: any) => boolean; resolve: (m: any) => void }[] = [];
  private closeWaiters: ((code: number) => void)[] = [];

  constructor(url: string, headers?: Record<string, string>) {
    this.ws = new WebSocket(url, { headers });
    this.ws.on("message", (raw) => {
      const msg = JSON.parse(raw.toString());
      this.messages.push(msg);
      this.waiters = this.waiters.filter((w) => {
        if (w.pred(msg)) {
          w.resolve(msg);
          return false;
        }
        return true;
      });
    });
    this.ws.on("close", (code) => {
      this.closeCode = code;
      this.closeWaiters.forEach((r) => r(code));
    });
  }

  opened() {
    return new Promise<void>((resolve, reject) => {
      if (this.ws.readyState === WebSocket.OPEN) return resolve();
      this.ws.once("open", () => resolve());
      this.ws.once("error", reject);
      this.ws.once("unexpected-response", (_req, res) => reject(new Error(`HTTP ${res.statusCode}`)));
    });
  }

  send(msg: unknown) {
    this.ws.send(typeof msg === "string" ? msg : JSON.stringify(msg));
  }

  /** Resolves with the first message (already received or future) matching pred. */
  next(pred: ((m: any) => boolean) | string, timeoutMs = 2000): Promise<any> {
    const p = typeof pred === "string" ? (m: any) => m.t === pred : pred;
    const found = this.messages.find(p as any);
    if (found) {
      this.messages.splice(this.messages.indexOf(found), 1);
      return Promise.resolve(found);
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`timeout waiting for message (${String(pred)})`)), timeoutMs);
      this.waiters.push({
        pred: p as any,
        resolve: (m) => {
          clearTimeout(timer);
          this.messages.splice(this.messages.indexOf(m), 1);
          resolve(m);
        },
      });
    });
  }

  closed(timeoutMs = 2000) {
    if (this.closeCode !== undefined) return Promise.resolve(this.closeCode);
    return new Promise<number>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("timeout waiting for close")), timeoutMs);
      this.closeWaiters.push((c) => {
        clearTimeout(timer);
        resolve(c);
      });
    });
  }

  close() {
    this.ws.close();
  }
}

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
