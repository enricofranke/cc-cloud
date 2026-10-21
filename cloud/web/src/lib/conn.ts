// One live connection per paired mainframe: GET /ws/client, then the first
// message authenticates ({"t":"auth","token"}), so the token never ends up in URLs/logs.
// Framework agnostic: state is exposed as signals so the UI and the tests can
// both observe it. Handles reconnect with backoff, ping/pong, optimistic
// values with rollback, and the offline/disabled/unauthorized states.
import { signal, batch } from "@preact/signals";
import type { Alert, MainframeInfo, Scalar, Uplink, Widget } from "./types.ts";
import { ApiError, fetchMainframes } from "./api.ts";

/** Outcome of an action as reported by action_result (plus extras like attemptsLeft/retryAfter). */
export interface ActionOutcome {
  ok: boolean;
  error?: string;
  attemptsLeft?: number;
  retryAfter?: number;
}
export interface ActionExtra {
  pin?: string;
  /** Return true to say "handled" and suppress the default toast. */
  onResult?: (r: ActionOutcome) => boolean | void;
}

export type ConnStatus =
  | "connecting" // first attempt in progress
  | "open" // socket open, snapshot received
  | "reconnecting" // lost a working connection, retrying
  | "unreachable" // cannot reach the server at all (keeps retrying)
  | "unauthorized"; // token unknown or revoked (stops retrying)

export interface Notice {
  level: "info" | "warn" | "crit";
  title: string;
  text: string;
}

export interface ConnOptions {
  /** http(s) origin of the mod's web server, e.g. location.origin */
  baseUrl: string;
  token: string;
  WebSocketImpl?: typeof WebSocket;
  minBackoffMs?: number;
  maxBackoffMs?: number;
  /** How long to wait for the action_result. */
  resultTimeoutMs?: number;
  /** After ok:true, how long to wait for the value to change before rolling back. */
  confirmTimeoutMs?: number;
  /** Close and reconnect when the server has been silent this long (server pings every 20 s). */
  silenceTimeoutMs?: number;
  onAlert?: (a: Alert, conn: MainframeConn) => void;
  onNotice?: (n: Notice, conn: MainframeConn) => void;
}

interface Pending {
  reqId: string;
  value: Scalar;
  timer?: ReturnType<typeof setTimeout>;
}

export const REJECT_TEXT: Record<string, string> = {
  offline: "The mainframe is offline.",
  disabled: "The dashboard is switched off in-game.",
  rate_limited: "Too many actions – slow down a little.",
  uplink_offline: "This uplink is offline.",
  unknown_uplink: "This uplink no longer exists.",
  unknown_widget: "This control no longer exists.",
  not_interactive: "This widget is read-only.",
  invalid_value: "The value was not accepted.",
  invalid_message: "The server did not understand the request.",
  timeout: "No answer from the server.",
  unconfirmed: "The computer did not confirm the change.",
  disconnected: "Not connected to the server.",
  read_only: "This device is read-only.",
  pin_required: "This control needs the mainframe PIN.",
  pin_wrong: "Wrong PIN.",
  wrong_pin: "Wrong PIN.",
  pin_not_set: "The owner has not set a PIN yet (/cccloud pin set).",
  busy: "The server is busy – try again.",
  pin_locked: "Too many wrong PINs – this device is locked for a few minutes.",
  access_revoked: "Your access to this mainframe was revoked.",
  forbidden: "This device is read-only.",
};

export function rejectText(r: ActionOutcome) {
  if (r.error === "pin_wrong" || r.error === "wrong_pin") return r.attemptsLeft != null ? `Wrong PIN – ${r.attemptsLeft} attempt${r.attemptsLeft === 1 ? "" : "s"} left.` : "Wrong PIN.";
  if (r.error === "pin_locked") return `Too many wrong PINs. This device is locked${r.retryAfter ? ` for ${formatDuration(r.retryAfter)}` : " for a few minutes"}; the owner was notified in-game.`;
  return REJECT_TEXT[r.error ?? ""] ?? r.error ?? "Rejected.";
}

export function formatDuration(seconds: number) {
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s ? `${m}m ${s}s` : `${m} min`;
}

export const keyOf = (uplinkId: string, id: string) => `${uplinkId}/${id}`;
let reqSeq = 0;
const newReqId = () => `${Date.now().toString(36)}-${(++reqSeq).toString(36)}`;

export class MainframeConn {
  readonly status = signal<ConnStatus>("connecting");
  readonly info = signal<MainframeInfo | null>(null);
  readonly uplinks = signal<Uplink[]>([]);
  readonly alerts = signal<Alert[]>([]);
  readonly unread = signal(0);
  /** Optimistic values by `uplinkId/id`, shown until the server confirms or we roll back. */
  readonly pending = signal<Record<string, Scalar>>({});
  /** Widgets with an action in flight (e.g. a pressed button waiting for its result). */
  readonly busy = signal<Record<string, true>>({});
  /** Timestamp of the next reconnect attempt (for the UI countdown). */
  readonly nextRetryAt = signal<number | null>(null);
  /** false for read-only device tokens: controls are shown but disabled. */
  readonly canControl = signal(true);
  /** Set when the server answered 403 "internet access disabled" (webAllowInternet=false). */
  readonly lanOnly = signal(false);
  /** Set after too many wrong PINs: PIN-protected controls are locked until then (ms epoch). */
  readonly pinLockedUntil = signal(0);

  private ws?: WebSocket;
  private stopped = true;
  private attempts = 0;
  private everOpened = false;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private silenceTimer?: ReturnType<typeof setTimeout>;
  private authFailed = false;
  private pendingMeta = new Map<string, Pending>();
  private requests = new Map<string, { key: string; label: string; timer: ReturnType<typeof setTimeout>; onResult?: ActionExtra["onResult"] }>();
  private readonly o: Required<Omit<ConnOptions, "onAlert" | "onNotice" | "WebSocketImpl">> & ConnOptions;

  constructor(opts: ConnOptions) {
    this.o = {
      minBackoffMs: 1000,
      maxBackoffMs: 15_000,
      resultTimeoutMs: 5000,
      confirmTimeoutMs: 4000,
      silenceTimeoutMs: 50_000,
      ...opts,
    };
  }

  get token() {
    return this.o.token;
  }

  get actionable() {
    const i = this.info.value;
    return this.status.value === "open" && !!i?.online && !!i?.enabled && this.canControl.value;
  }

  start() {
    if (!this.stopped) return;
    this.stopped = false;
    this.connect();
  }

  stop() {
    this.stopped = true;
    clearTimeout(this.retryTimer);
    clearTimeout(this.silenceTimer);
    this.nextRetryAt.value = null;
    const ws = this.ws;
    this.ws = undefined;
    ws?.close(1000);
    this.dropAllPending();
  }

  /** Skip the backoff wait (e.g. app came back to the foreground). */
  retryNow() {
    if (this.stopped || this.status.value === "unauthorized" || this.status.value === "open") return;
    if (this.ws && this.ws.readyState === 0) return; // already connecting
    clearTimeout(this.retryTimer);
    this.connect();
  }

  private connect() {
    this.nextRetryAt.value = null;
    const Impl = this.o.WebSocketImpl ?? WebSocket;
    const url = this.o.baseUrl.replace(/^http/, "ws").replace(/\/$/, "") + `/ws/client`;
    let ws: WebSocket;
    try {
      ws = new Impl(url);
    } catch {
      this.scheduleRetry();
      return;
    }
    this.ws = ws;
    ws.onmessage = (ev) => {
      if (this.ws !== ws) return;
      this.armSilence();
      let msg: any;
      try {
        msg = JSON.parse(typeof ev.data === "string" ? ev.data : String(ev.data));
      } catch {
        return;
      }
      if (!msg || typeof msg !== "object") return;
      if (isAuthError(msg)) {
        this.authFailed = true;
        return;
      }
      if (msg.t === "snapshot" || msg.t === "auth_ok") authed = true;
      this.handle(msg);
    };
    let authed = false;
    ws.onopen = () => {
      if (this.ws !== ws) return;
      this.armSilence();
      ws.send(JSON.stringify({ t: "auth", token: this.o.token }));
    };
    ws.onclose = (ev) => {
      if (this.ws !== ws) return;
      this.ws = undefined;
      clearTimeout(this.silenceTimer);
      this.dropAllPending();
      // An auth error: explicit error message, a 4001/4003/4401/4403 close, or a
      // policy close (1008) before we were ever let in.
      if (this.authFailed || AUTH_CLOSE_CODES.has(ev.code) || (ev.code === 1008 && !authed)) {
        this.status.value = "unauthorized";
        return;
      }
      if (this.stopped) return;
      this.scheduleRetry();
    };
    ws.onerror = () => {};
  }

  private scheduleRetry() {
    this.attempts++;
    // never connected, or several failures in a row -> the server is probably unreachable
    this.status.value = !this.everOpened || this.attempts > 3 ? "unreachable" : "reconnecting";
    if (this.status.value === "unreachable" && this.attempts % 4 === 1) this.diagnose();
    const base = Math.min(this.o.maxBackoffMs, this.o.minBackoffMs * 2 ** Math.min(this.attempts - 1, 10));
    const delay = Math.round(base * (0.8 + Math.random() * 0.4));
    this.nextRetryAt.value = Date.now() + delay;
    this.retryTimer = setTimeout(() => this.connect(), delay);
  }

  /** The socket failed: ask the HTTP API why (LAN-only 403, revoked token, or really down). */
  private async diagnose() {
    try {
      await fetchMainframes(this.o.token, this.o.baseUrl);
      this.lanOnly.value = false;
    } catch (e) {
      if (!(e instanceof ApiError) || this.stopped) return;
      this.lanOnly.value = e.kind === "lan_only";
      if (e.kind === "unauthorized") {
        this.stopped = true;
        clearTimeout(this.retryTimer);
        this.nextRetryAt.value = null;
        this.status.value = "unauthorized";
      }
    }
  }

  private armSilence() {
    clearTimeout(this.silenceTimer);
    this.silenceTimer = setTimeout(() => this.ws?.close(4000, "silent"), this.o.silenceTimeoutMs);
  }

  private send(msg: object) {
    if (this.ws?.readyState !== 1) return false;
    this.ws.send(JSON.stringify(msg));
    return true;
  }

  // ---------------------------------------------------------------- inbound
  private handle(msg: any) {
    switch (msg.t) {
      case "auth_ok":
        if (typeof msg.canControl === "boolean") this.canControl.value = msg.canControl;
        break;
      case "ping":
        this.send({ t: "pong" });
        break;
      case "snapshot": {
        if (!msg.mainframe || !Array.isArray(msg.uplinks)) return;
        batch(() => {
          this.dropAllPending();
          this.info.value = normalizeInfo(msg.mainframe);
          this.uplinks.value = msg.uplinks.map(normalizeUplink).filter(Boolean) as Uplink[];
          if (Array.isArray(msg.alerts)) this.alerts.value = msg.alerts.map(normalizeAlert).filter(Boolean).slice(-50) as Alert[];
          const cc = msg.canControl ?? msg.device?.canControl ?? msg.mainframe?.canControl;
          if (typeof cc === "boolean") this.canControl.value = cc;
          this.status.value = "open";
          this.lanOnly.value = false;
          this.attempts = 0;
          this.everOpened = true;
        });
        break;
      }
      case "state": {
        const i = this.info.value;
        if (!i) return;
        this.info.value = {
          ...i,
          online: typeof msg.online === "boolean" ? msg.online : i.online,
          enabled: typeof msg.enabled === "boolean" ? msg.enabled : i.enabled,
          name: typeof msg.name === "string" ? msg.name : i.name,
        };
        if (!this.info.value.online || !this.info.value.enabled) this.dropAllPending();
        break;
      }
      case "uplink": {
        const u = normalizeUplink(msg);
        if (!u) return;
        const list = this.uplinks.value;
        const i = list.findIndex((x) => x.uplinkId === u.uplinkId);
        this.uplinks.value = i >= 0 ? list.map((x, j) => (j === i ? u : x)) : [...list, u];
        // a fresh snapshot of the uplink settles every pending value in it
        for (const w of u.widgets) this.settle(keyOf(u.uplinkId, w.id));
        break;
      }
      case "uplink_removed":
        this.uplinks.value = this.uplinks.value.filter((x) => x.uplinkId !== msg.uplinkId);
        break;
      case "value": {
        if (typeof msg.uplinkId !== "string" || typeof msg.id !== "string") return;
        const value = toScalar(msg.value);
        let changed = false;
        const list = this.uplinks.value.map((u) => {
          if (u.uplinkId !== msg.uplinkId) return u;
          return {
            ...u,
            widgets: u.widgets.map((w) => {
              if (w.id !== msg.id) return w;
              changed = true;
              return { ...w, value };
            }),
          };
        });
        if (changed) this.uplinks.value = list;
        this.settle(keyOf(msg.uplinkId, msg.id));
        break;
      }
      case "alert": {
        const a = normalizeAlert(msg);
        if (!a) return;
        if (!a.uplinkTitle && a.uplinkId) a.uplinkTitle = this.uplinks.value.find((u) => u.uplinkId === a.uplinkId)?.title;
        this.alerts.value = [...this.alerts.value.slice(-49), a];
        this.unread.value++;
        this.o.onAlert?.(a, this);
        break;
      }
      case "action_result":
        this.onResult(String(msg.reqId ?? ""), {
          ok: msg.ok === true,
          error: typeof msg.error === "string" ? msg.error : undefined,
          attemptsLeft: typeof msg.attemptsLeft === "number" ? msg.attemptsLeft : undefined,
          retryAfter: typeof msg.retryAfter === "number" ? msg.retryAfter : undefined,
        });
        break;
    }
  }

  // ---------------------------------------------------------------- actions
  /**
   * Sends an action. With `optimistic`, the value is shown immediately and
   * rolled back if the server rejects it or the computer does not confirm.
   */
  action(uplinkId: string, widget: Widget, value: Scalar, optimistic = false, extra: ActionExtra = {}): string | undefined {
    const key = keyOf(uplinkId, widget.id);
    const reqId = newReqId();
    const msg = { t: "action", reqId, uplinkId, id: widget.id, value, ...(extra.pin ? { pin: extra.pin } : {}) };
    if (!this.actionable || !this.send(msg)) {
      const why = !this.canControl.value ? "read_only" : this.status.value !== "open" ? "disconnected" : this.info.value?.online === false ? "offline" : "disabled";
      this.notice("warn", widget.label || widget.id, REJECT_TEXT[why]);
      return undefined;
    }
    const timer = setTimeout(() => this.onResult(reqId, { ok: false, error: "timeout" }), this.o.resultTimeoutMs);
    this.requests.set(reqId, { key, label: widget.label || widget.id, timer, onResult: extra.onResult });
    this.busy.value = { ...this.busy.value, [key]: true };
    if (optimistic) {
      const prev = this.pendingMeta.get(key);
      if (prev) clearTimeout(prev.timer);
      this.pendingMeta.set(key, { reqId, value });
      this.pending.value = { ...this.pending.value, [key]: value };
    }
    return reqId;
  }

  private onResult(reqId: string, outcome: ActionOutcome) {
    const { ok, error } = outcome;
    const req = this.requests.get(reqId);
    if (!req) return;
    if (error === "pin_locked") this.pinLockedUntil.value = Date.now() + (outcome.retryAfter ?? 300) * 1000;
    let handled = false;
    try {
      handled = req.onResult?.(outcome) === true;
    } catch {}
    clearTimeout(req.timer);
    this.requests.delete(reqId);
    if (![...this.requests.values()].some((r) => r.key === req.key)) {
      const { [req.key]: _, ...rest } = this.busy.value;
      this.busy.value = rest;
    }
    const p = this.pendingMeta.get(req.key);
    if (!ok) {
      if (p?.reqId === reqId) this.rollback(req.key);
      if (!handled) this.notice(error === "rate_limited" ? "info" : "warn", `${req.label}: not applied`, rejectText(outcome));
      return;
    }
    if (p?.reqId === reqId) {
      // accepted and queued in-game: now wait for the computer to report the new value
      const current = this.widgetValue(req.key);
      if (current === p.value) return this.settle(req.key);
      clearTimeout(p.timer);
      p.timer = setTimeout(() => {
        if (this.pendingMeta.get(req.key)?.reqId !== reqId) return;
        this.rollback(req.key);
        this.notice("warn", `${req.label}: reverted`, REJECT_TEXT.unconfirmed);
      }, this.o.confirmTimeoutMs);
    }
  }

  private widgetValue(key: string): Scalar | undefined {
    const [uplinkId, id] = key.split("/");
    return this.uplinks.value.find((u) => u.uplinkId === uplinkId)?.widgets.find((w) => w.id === id)?.value;
  }

  /** The server reported a value for this widget: it wins over the optimistic one. */
  private settle(key: string) {
    const p = this.pendingMeta.get(key);
    if (!p) return;
    // only settle once the action was accepted or the server already shows our value
    if (this.requests.has(p.reqId) && this.widgetValue(key) !== p.value) return;
    this.rollback(key);
  }

  private rollback(key: string) {
    const p = this.pendingMeta.get(key);
    if (p) clearTimeout(p.timer);
    this.pendingMeta.delete(key);
    if (key in this.pending.value) {
      const { [key]: _, ...rest } = this.pending.value;
      this.pending.value = rest;
    }
  }

  private dropAllPending() {
    for (const p of this.pendingMeta.values()) clearTimeout(p.timer);
    this.pendingMeta.clear();
    for (const r of this.requests.values()) {
      clearTimeout(r.timer);
      try {
        r.onResult?.({ ok: false, error: "disconnected" });
      } catch {}
    }
    this.requests.clear();
    if (Object.keys(this.pending.value).length) this.pending.value = {};
    if (Object.keys(this.busy.value).length) this.busy.value = {};
  }

  private notice(level: Notice["level"], title: string, text: string) {
    this.o.onNotice?.({ level, title, text }, this);
  }

  markRead() {
    this.unread.value = 0;
  }
}

// ------------------------------------------------------------- normalizers
/** 4401 revoked/unauthorized, 4403 forbidden (player lost access), 4404 mainframe removed; 4001/4003 legacy. */
const AUTH_CLOSE_CODES = new Set([4001, 4003, 4401, 4403, 4404]);

function isAuthError(msg: any) {
  if (msg.t === "auth_failed" || msg.t === "unauthorized") return true;
  if (msg.t !== "error" && msg.t !== "auth_error") return false;
  if (msg.t === "auth_error") return true;
  const why = String(msg.error ?? msg.reason ?? msg.code ?? "").toLowerCase();
  return /auth|token|revoked|forbidden/.test(why);
}

function toScalar(v: unknown): Scalar {
  return typeof v === "number" || typeof v === "boolean" || typeof v === "string" ? v : null;
}

function normalizeInfo(m: any): MainframeInfo {
  return { id: String(m.id ?? ""), name: String(m.name ?? "Mainframe"), online: m.online !== false, enabled: m.enabled === true };
}

function normalizeUplink(u: any): Uplink | null {
  if (!u || typeof u.uplinkId !== "string") return null;
  const widgets = Array.isArray(u.widgets) ? u.widgets : [];
  return {
    uplinkId: u.uplinkId,
    title: typeof u.title === "string" ? u.title : "",
    online: u.online !== false,
    widgets: widgets
      .filter((w: any) => w && typeof w.id === "string" && ["button", "toggle", "slider", "gauge", "label"].includes(w.type))
      .map((w: any) => ({
        id: w.id,
        type: w.type,
        label: typeof w.label === "string" ? w.label : undefined,
        opts: w.opts && typeof w.opts === "object" && !Array.isArray(w.opts) ? w.opts : {},
        value: w.value === undefined ? undefined : toScalar(w.value),
      })),
  };
}

function normalizeAlert(a: any): Alert | null {
  if (!a || typeof a.text !== "string") return null;
  return {
    uplinkId: typeof a.uplinkId === "string" ? a.uplinkId : undefined,
    uplinkTitle: typeof a.uplinkTitle === "string" ? a.uplinkTitle : undefined,
    text: a.text,
    level: a.level === "crit" || a.level === "warn" ? a.level : "info",
    ts: typeof a.ts === "number" ? a.ts : Date.now(),
  };
}
