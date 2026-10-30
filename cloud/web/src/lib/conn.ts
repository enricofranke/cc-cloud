// One live connection per paired mainframe: GET /ws/client, then the first
// message authenticates ({"t":"auth","token"}), so the token never ends up in URLs/logs.
// Framework agnostic: state is exposed as signals so the UI and the tests can
// both observe it. Handles reconnect with backoff, ping/pong, optimistic
// values with rollback, and the offline/disabled/unauthorized states.
import { presetHas } from "./permissions.ts";
import { normalizeRules, type Rule, type RulesDoc } from "./rules.ts";
import { normalizeScenes, type Scene, type ScenesDoc } from "./scenes.ts";
import { applyTermFrame, type TermEvent, type TermState } from "./terminal.ts";
import { signal, batch } from "@preact/signals";
import { WIDGET_TYPES, type ClaimsOverlay, type RedstoneChannel, type RedstoneInfo, type WaystonesOverlay, type PowerInfo, type NetworkInfo, type NetNode, type NetLink, type ActionValue, type Alert, type ItemRow, type MainframeInfo, type Row, type Scalar, type Uplink, type Widget, type WidgetValue } from "./types.ts";
import { ApiError, fetchMainframes } from "./api.ts";
import { normalizeLayout, type LayoutDoc } from "./layout.ts";

/** Outcome of an action as reported by action_result (plus extras like attemptsLeft/retryAfter). */
/** Storage card v2 (docs/SPEC.md "Storage card v2 – protocol"). */
export interface StorageRow {
  name: string;
  displayName: string;
  count: number;
  craftable: boolean;
  favorite?: boolean;
}
export interface JobInfo {
  jobId: string;
  name: string;
  displayName: string;
  count: number;
  status: "queued" | "running" | "waiting" | "done" | "failed" | "cancelled";
  progress: number;
  error?: string;
  notify?: boolean;
  source?: "dashboard" | "lua" | "autostock" | "rule";
}
export type StockState = "idle" | "crafting" | "waiting_cpu" | "missing" | "paused";
export interface ItemDoc extends StorageRow {
  mod: string;
  watch: { min: number; target: number; autoCraft: boolean; state: StockState; failures: number } | null;
  jobs: JobInfo[];
  history: number[];
}
export interface CpuInfo {
  cpus: { id: string; name: string; coProcessors: number; storage: number; busy: boolean; job: { name: string; displayName: string; count: number; progress: number; elapsedMs: number } | null }[];
  tasks: { id: string; name: string; displayName: string; count: number; progress: number; own: boolean }[];
}
export interface CraftPlan {
  name: string;
  count: number;
  missing: boolean;
  bytes: number | null;
  entries: { name: string; displayName: string; stored: number; toCraft: number; missing: number }[];
  cpu: { available: number; enough: boolean } | null;
  timeMs: number | null;
}

export interface ActionOutcome {
  ok: boolean;
  error?: string;
  attemptsLeft?: number;
  retryAfter?: number;
  /** Set by successful craft actions. */
  jobId?: string;
  /** Optional: the widget's value after the handler ran (confirms an optimistic change). */
  value?: unknown;
}

/** Value equality for widget values (scalars, arrays, plain objects). */
export function sameValue(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (typeof a !== "object" || typeof b !== "object" || !a || !b) return false;
  try {
    return JSON.stringify(a) === JSON.stringify(b);
  } catch {
    return false;
  }
}

export interface QueryResult {
  rows: ItemRow[];
  truncated: boolean;
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
  /** v0.6: account tokens name the mainframe in the auth message */
  mainframeId?: string;
  /** v0.6: a read-only share-link session instead of a token */
  share?: { id: string; secret: string };
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
  value: ActionValue;
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
  unconfirmed: "No answer from the computer yet – is the program running?",
  unloaded: "Its chunk is not loaded – nobody is nearby. Keep it loaded (e.g. FTB Chunks force-load) to control it from here.",
  disconnected: "Not connected to the server.",
  read_only: "This device is read-only.",
  no_power: "The mainframe has no power.",
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
  readonly pending = signal<Record<string, ActionValue>>({});
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
  /** Shared dashboard layout of this mainframe (null = defaults). */
  readonly layout = signal<LayoutDoc | null>(null);
  /** v0.6 role on this mainframe (null = server without roles: canControl decides) */
  readonly role = signal<"viewer" | "operator" | "admin" | "owner" | "share" | null>(null);
  /** fine-grained permissions (docs/SPEC.md "Fine-grained permissions"); null = older mod (role presets apply) */
  readonly perms = signal<Set<string> | null>(null);
  /** Script Store (null = disabled or not allowed) */
  readonly store = signal<{ url: string; key?: string } | null>(null);
  /** v0.6: share-link sessions – what is shared and by whom */
  readonly shareInfo = signal<{ label: string; sharedBy: string; expires: number | null; hideNumbers: boolean } | null>(null);
  /** last error code sent by the server before closing (e.g. share_expired, no_access) */
  readonly lastError = signal<string | null>(null);
  /** v0.6 linked player account, if any */
  readonly account = signal<{ player: string } | null>(null);
  /** v0.4 network graph (null = server without network map) */
  readonly network = signal<NetworkInfo | null>(null);
  /** v0.7 wireless redstone channels (null = none / older mod) */
  readonly redstone = signal<RedstoneInfo | null>(null);
  /** v0.5 E open browser terminals by uplink id */
  readonly terminals = signal<Record<string, TermState>>({});
  /** why a terminal was closed by the server (forbidden, unknown_computer, disabled, unsupported) */
  readonly termClosed = signal<Record<string, string>>({});
  private termReqs = new Map<string, { resolve: () => void; reject: (e: Error & { attemptsLeft?: number; retryAfter?: number }) => void; timer: ReturnType<typeof setTimeout> }>();
  /** v0.5 C rules document (null = none / older mod / share session) */
  readonly rules = signal<RulesDoc | null>(null);
  private ruleReqs = new Map<string, { resolve: (r: { id?: string }) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  /** v0.10 scenes document (null = none / older mod / share session) */
  readonly scenes = signal<ScenesDoc | null>(null);
  private sceneReqs = new Map<string, { resolve: (r: { id?: string }) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  /** v0.10 production statistics: production_result */
  private prodReqs = new Map<string, { resolve: (d: any) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();

  private ws?: WebSocket;
  private stopped = true;
  private attempts = 0;
  private everOpened = false;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private silenceTimer?: ReturnType<typeof setTimeout>;
  private authFailed = false;
  private netReqs = new Map<string, { resolve: () => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  private layoutReqs = new Map<string, { resolve: (server?: LayoutDoc) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  private queries = new Map<string, { resolve: (r: QueryResult) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  /** storage card v2: storage_result / craft_plan */
  private storageReqs = new Map<string, { resolve: (d: any) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  private pendingMeta = new Map<string, Pending>();
  private requests = new Map<string, { key: string; label: string; timer: ReturnType<typeof setTimeout>; onResult?: ActionExtra["onResult"] }>();
  private readonly o: Required<Omit<ConnOptions, "onAlert" | "onNotice" | "WebSocketImpl" | "mainframeId" | "share">> & ConnOptions;

  constructor(opts: ConnOptions) {
    this.o = {
      minBackoffMs: 1000,
      maxBackoffMs: 15_000,
      resultTimeoutMs: 5000,
      confirmTimeoutMs: 8000,
      silenceTimeoutMs: 50_000,
      ...opts,
    };
  }

  get token() {
    return this.o.token;
  }

  get baseUrl() {
    return this.o.baseUrl;
  }

  /** Admin features (layout editing, unlink, access, shares). Servers without roles: canControl. */
  /** Share-link sessions never send anything but pong. */
  get isShare() {
    return !!this.o.share || this.role.value === "share";
  }

  /**
   * Does this session have a permission? Uses the server's set (snapshot/role push),
   * else the role presets; servers without roles: canControl = everything, else viewer.
   * Every client-side check goes through this (docs/SPEC.md "Fine-grained permissions").
   */
  can(perm: string): boolean {
    if (this.isShare) return false;
    const p = this.perms.value;
    if (p) return p.has(perm);
    const r = this.role.value;
    return presetHas(r ?? (this.canControl.value ? "admin" : "viewer"), perm);
  }
  /** The error a refused write gets: not_allowed, or read_only for sessions without act.buttons. */
  deniedCode(perm: string): "not_allowed" | "read_only" | null {
    return this.can(perm) ? null : this.can("act.buttons") ? "not_allowed" : "read_only";
  }

  get actionable() {
    const i = this.info.value;
    return this.status.value === "open" && !!i?.online && !!i?.enabled && this.canControl.value && !this.isShare;
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
      if (msg.t === "error" && typeof msg.error === "string") this.lastError.value = msg.error.slice(0, 32);
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
      ws.send(
        JSON.stringify(
          this.o.share
            ? { t: "auth_share", id: this.o.share.id, secret: this.o.share.secret }
            : { t: "auth", token: this.o.token, ...(this.o.mainframeId ? { mainframeId: this.o.mainframeId } : {}) },
        ),
      );
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
    // a share-link session is read-only by construction: nothing but pong ever leaves the browser
    if (this.isShare && (msg as { t?: string }).t !== "pong") return false;
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
          this.layout.value = msg.layout && typeof msg.layout === "object" ? normalizeLayout(msg.layout) : null;
          this.network.value = normalizeNetwork(msg.network);
          this.redstone.value = normalizeRedstone(msg.redstone);
          this.rules.value = normalizeRules(msg.rules);
          this.scenes.value = normalizeScenes(msg.scenes);
          this.role.value = ROLES.includes(msg.role) ? msg.role : null;
          this.perms.value = parsePerms(msg.perms);
          this.store.value = parseStore(msg.store);
          if (msg.share && typeof msg.share === "object")
            this.shareInfo.value = {
              label: typeof msg.share.label === "string" ? msg.share.label.slice(0, 48) : "Shared view",
              sharedBy: typeof msg.share.sharedBy === "string" ? msg.share.sharedBy.slice(0, 32) : "?",
              expires: typeof msg.share.expires === "number" ? msg.share.expires : null,
              hideNumbers: msg.share.hideNumbers === true,
            };
          this.account.value = msg.account && typeof msg.account.player === "string" ? { player: msg.account.player.slice(0, 32) } : null;
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
          // a delta that carries online/enabled also carries the current reason (or none)
          reason: "online" in msg || "reason" in msg ? (typeof msg.reason === "string" ? msg.reason.slice(0, 32) : undefined) : i.reason,
          power: "power" in msg ? normalizePower(msg.power) : i.power,
          removedUntil: "removedUntil" in msg ? until(msg.removedUntil) : i.removedUntil,
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
        const value = toValue(msg.value);
        let changed = false;
        const list = this.uplinks.value.map((u) => {
          if (u.uplinkId !== msg.uplinkId) return u;
          return {
            ...u,
            widgets: u.widgets.map((w) => {
              if (w.id !== msg.id) return w;
              changed = true;
              if (w.type === "chart") {
                const history = Array.isArray(msg.value)
                  ? numbers(msg.value, chartCap(w))
                  : typeof msg.value === "number" && Number.isFinite(msg.value)
                    ? [...(w.history ?? []), msg.value].slice(-chartCap(w))
                    : w.history ?? [];
                return { ...w, history, value: history.at(-1) ?? null };
              }
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
          jobId: msg.jobId != null ? String(msg.jobId) : undefined,
          ...("value" in msg ? { value: msg.value } : {}),
        });
        this.queryFailed(String(msg.reqId ?? ""), typeof msg.error === "string" ? msg.error : "rejected");
        break;
      case "role":
        if (ROLES.includes(msg.role)) {
          this.role.value = msg.role;
          this.canControl.value = typeof msg.canControl === "boolean" ? msg.canControl : ["operator", "admin", "owner"].includes(msg.role);
        }
        if (Array.isArray(msg.perms)) this.perms.value = parsePerms(msg.perms);
        break;
      case "network":
        this.network.value = normalizeNetwork(msg.network);
        break;
      case "redstone":
        this.redstone.value = normalizeRedstone(msg);
        break;
      case "rules":
        this.rules.value = normalizeRules(msg);
        break;
      case "scenes":
        this.scenes.value = normalizeScenes(msg);
        break;
      case "scene_result": {
        const r = this.sceneReqs.get(String(msg.reqId ?? ""));
        if (!r) return;
        this.sceneReqs.delete(String(msg.reqId));
        clearTimeout(r.timer);
        if (msg.ok === true) r.resolve(typeof msg.id === "string" ? { id: msg.id } : {});
        else {
          const e = new Error(typeof msg.error === "string" ? msg.error : "rejected") as Error & { detail?: string };
          if (typeof msg.detail === "string") e.detail = msg.detail.slice(0, 200);
          r.reject(e);
        }
        break;
      }
      case "production_result": {
        const r = this.prodReqs.get(String(msg.reqId ?? ""));
        if (!r) return;
        this.prodReqs.delete(String(msg.reqId));
        clearTimeout(r.timer);
        if (msg.ok === true) r.resolve(msg.data && typeof msg.data === "object" ? msg.data : {});
        else r.reject(new Error(typeof msg.error === "string" ? msg.error.slice(0, 32) : "failed"));
        break;
      }
      case "term": {
        const id = String(msg.uplinkId ?? "");
        if (!id || !(id in this.terminals.value)) return;
        const next = applyTermFrame(this.terminals.value[id] ?? null, msg);
        if (next) this.terminals.value = { ...this.terminals.value, [id]: next };
        break;
      }
      case "term_closed": {
        const id = String(msg.uplinkId ?? "");
        const { [id]: _gone, ...rest } = this.terminals.value;
        this.terminals.value = rest;
        this.termClosed.value = { ...this.termClosed.value, [id]: typeof msg.reason === "string" ? msg.reason.slice(0, 32) : "closed" };
        break;
      }
      case "term_result": {
        const r = this.termReqs.get(String(msg.reqId ?? ""));
        if (!r) return;
        this.termReqs.delete(String(msg.reqId));
        clearTimeout(r.timer);
        if (msg.ok === true) r.resolve();
        else {
          const e = new Error(typeof msg.error === "string" ? msg.error : "rejected") as Error & { attemptsLeft?: number; retryAfter?: number };
          if (typeof msg.attemptsLeft === "number") e.attemptsLeft = msg.attemptsLeft;
          if (typeof msg.retryAfter === "number") e.retryAfter = msg.retryAfter;
          r.reject(e);
        }
        break;
      }
      case "rule_result": {
        const r = this.ruleReqs.get(String(msg.reqId ?? ""));
        if (!r) return;
        this.ruleReqs.delete(String(msg.reqId));
        clearTimeout(r.timer);
        if (msg.ok === true) r.resolve(typeof msg.id === "string" ? { id: msg.id } : {});
        else {
          const e = new Error(typeof msg.error === "string" ? msg.error : "rejected") as Error & { detail?: string };
          if (typeof msg.detail === "string") e.detail = msg.detail.slice(0, 200);
          r.reject(e);
        }
        break;
      }
      case "rs_result":
      case "net_result": {
        const r = this.netReqs.get(String(msg.reqId ?? ""));
        if (!r) return;
        this.netReqs.delete(String(msg.reqId));
        clearTimeout(r.timer);
        if (msg.ok === true) r.resolve();
        else r.reject(new Error(typeof msg.error === "string" ? msg.error : "rejected"));
        break;
      }
      case "layout":
        this.layout.value = msg.layout && typeof msg.layout === "object" ? normalizeLayout(msg.layout) : null;
        break;
      case "storage_result":
      case "craft_plan": {
        const r = this.storageReqs.get(String(msg.reqId ?? ""));
        if (!r) return;
        this.storageReqs.delete(String(msg.reqId));
        clearTimeout(r.timer);
        if (msg.ok === true) r.resolve(msg.t === "craft_plan" ? msg.plan : (msg.data ?? {}));
        else r.reject(new Error(typeof msg.error === "string" ? msg.error : "failed"));
        return;
      }
      case "layout_result": {
        const l = this.layoutReqs.get(String(msg.reqId ?? ""));
        if (!l) return;
        this.layoutReqs.delete(String(msg.reqId));
        clearTimeout(l.timer);
        if (msg.ok === true) l.resolve(msg.layout && typeof msg.layout === "object" ? normalizeLayout(msg.layout) : undefined);
        else l.reject(new Error(typeof msg.error === "string" ? msg.error : "rejected"));
        break;
      }
      case "query_result": {
        const q = this.queries.get(String(msg.reqId ?? ""));
        if (!q) return;
        this.queries.delete(String(msg.reqId));
        clearTimeout(q.timer);
        if (typeof msg.error === "string") {
          q.reject(new Error(msg.error));
          return;
        }
        const rows: ItemRow[] = (Array.isArray(msg.rows) ? msg.rows : [])
          .filter((r: any) => r && typeof r.name === "string")
          .slice(0, 50)
          .map((r: any) => ({
            name: r.name,
            displayName: typeof r.displayName === "string" && r.displayName ? r.displayName : r.name,
            count: typeof r.count === "number" ? r.count : 0,
            craftable: r.craftable === true,
          }));
        q.resolve({ rows, truncated: msg.truncated === true });
        break;
      }
    }
  }

  // ---------------------------------------------------------------- actions
  /**
   * Sends an action. With `optimistic`, the value is shown immediately and
   * rolled back if the server rejects it or the computer does not confirm.
   */
  action(uplinkId: string, widget: Widget, value: ActionValue, optimistic = false, extra: ActionExtra = {}): string | undefined {
    const key = keyOf(uplinkId, widget.id);
    // picking the value that is already set (e.g. the current select option): nothing to do
    if (optimistic && !extra.pin && !(key in this.pending.value) && sameValue(this.widgetValue(key), value)) return undefined;
    const reqId = newReqId();
    const msg = { t: "action", reqId, uplinkId, id: widget.id, value, ...(extra.pin ? { pin: extra.pin } : {}) };
    if (!this.actionable || !this.send(msg)) {
      const why = !this.canControl.value
        ? "read_only"
        : this.status.value !== "open"
          ? "disconnected"
          : this.info.value?.online === false
            ? this.info.value.reason === "no_power" || this.info.value.power?.ok === false
              ? "no_power"
              : "offline"
            : "disabled";
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
      if (sameValue(this.widgetValue(req.key), p.value) || ("value" in outcome && sameValue(outcome.value, p.value))) return this.rollback(req.key);
      clearTimeout(p.timer);
      p.timer = setTimeout(() => {
        if (this.pendingMeta.get(req.key)?.reqId !== reqId) return;
        this.rollback(req.key);
        this.notice("info", `${req.label}: no answer`, REJECT_TEXT.unconfirmed);
      }, this.o.confirmTimeoutMs);
    }
  }

  /** v0.4: unlinks a device or relay from the network ({"t":"net_unlink"} → net_result). */
  unlinkNode(nodeId: string, timeoutMs = 8000): Promise<void> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      const no = this.deniedCode("network.link");
      if (no) return reject(new Error(no));
      if (!this.send({ t: "net_unlink", reqId, nodeId })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.netReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.netReqs.set(reqId, { resolve, reject, timer });
    });
  }

  /**
   * v0.7: overrides a wireless redstone channel ({"t":"rs_override"} → rs_result), `level` null clears it,
   * `seconds` null = until reset. Needs act.redstone.
   */
  rsOverride(channel: string, level: number | null, seconds: number | null, timeoutMs = 8000): Promise<void> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      const no = this.deniedCode("act.redstone");
      if (no) return reject(new Error(no));
      if (!this.send({ t: "rs_override", reqId, channel, level, seconds })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.netReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.netReqs.set(reqId, { resolve, reject, timer });
    });
  }

  /** v0.5 E: opens (or refreshes) the terminal of the computer behind a card. Frames arrive in `terminals`. */
  termOpen(uplinkId: string, pin?: string, timeoutMs = 8000): Promise<void> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      const no = this.deniedCode("terminal");
      if (no) return reject(new Error(no));
      if (!this.send({ t: "term_open", reqId, uplinkId, ...(pin ? { pin } : {}) })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.termReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.termReqs.set(reqId, {
        resolve: () => {
          const { [uplinkId]: _c, ...rest } = this.termClosed.value;
          this.termClosed.value = rest;
          if (!(uplinkId in this.terminals.value)) this.terminals.value = { ...this.terminals.value, [uplinkId]: applyTermFrame(null, { on: false })! };
          resolve();
        },
        reject,
        timer,
      });
    });
  }
  /**
   * Sends keyboard/mouse/power events. Fast typing is batched (one message per 50 ms, ≤ 32 events each),
   * so it stays under the server's 20 messages per second.
   */
  termInput(uplinkId: string, events: TermEvent[]) {
    const q = this.termQueue.get(uplinkId) ?? [];
    q.push(...events);
    this.termQueue.set(uplinkId, q);
    if (this.termTimer) return;
    const flush = () => {
      this.termTimer = undefined;
      for (const [id, list] of this.termQueue) {
        const batch = list.splice(0, 32);
        if (batch.length) this.send({ t: "term_input", uplinkId: id, events: batch });
        if (!list.length) this.termQueue.delete(id);
      }
      if (this.termQueue.size) this.termTimer = setTimeout(flush, 50);
    };
    // the first keystroke goes out at once, the rest of a burst is collected
    flush();
    this.termTimer = setTimeout(flush, 50);
  }
  private termQueue = new Map<string, TermEvent[]>();
  private termTimer: ReturnType<typeof setTimeout> | undefined;
  termClose(uplinkId: string) {
    this.send({ t: "term_close", uplinkId });
    const { [uplinkId]: _gone, ...rest } = this.terminals.value;
    this.terminals.value = rest;
  }

  /** v0.5 C: rule_set / rule_delete / rule_enable / rule_run → rule_result (rejects with the error code, `detail` attached). */
  ruleOp(msg: { t: "rule_set"; rule: Rule } | { t: "rule_delete"; id: string } | { t: "rule_enable"; id: string; enabled: boolean } | { t: "rule_run"; id: string }, timeoutMs = 8000): Promise<{ id?: string }> {
    const reqId = newReqId();
    const perm = msg.t === "rule_set" || msg.t === "rule_delete" ? "rules.edit" : "rules.toggle";
    return new Promise((resolve, reject) => {
      const no = this.deniedCode(perm);
      if (no) return reject(new Error(no));
      if (!this.send({ ...msg, reqId })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.ruleReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.ruleReqs.set(reqId, { resolve, reject, timer });
    });
  }

  /** v0.10: scene_set / scene_delete → scene_result (rejects with the error code, `detail` attached: "step 2: act.redstone"). */
  sceneOp(msg: { t: "scene_set"; scene: Scene } | { t: "scene_delete"; id: string }, timeoutMs = 8000): Promise<{ id?: string }> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      const no = this.deniedCode("rules.edit");
      if (no) return reject(new Error(no));
      if (!this.send({ ...msg, reqId })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.sceneReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.sceneReqs.set(reqId, { resolve, reject, timer });
    });
  }

  /** v0.10 production statistics: {"t":"production","op",…} → production_result.data (rejects with the error code). */
  production<T = any>(op: "overview" | "item" | "pin" | "unpin", params: Record<string, unknown> = {}, timeoutMs = 8000): Promise<T> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      if (this.isShare) return reject(new Error("forbidden"));
      if (this.status.value !== "open" || !this.send({ t: "production", reqId, op, ...params })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.prodReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.prodReqs.set(reqId, { resolve, reject, timer });
    });
  }

  /** Saves the shared layout: {"t":"layout_set"} → layout_result. Rejects with the server error code. */
  saveLayout(layout: LayoutDoc, timeoutMs = 8000): Promise<void> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      const no = this.deniedCode("layout.edit");
      if (no) return reject(new Error(no));
      if (!this.send({ t: "layout_set", reqId, layout })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.layoutReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.layoutReqs.set(reqId, {
        resolve: (server) => {
          this.layout.value = server ?? layout;
          resolve();
        },
        reject,
        timer,
      });
    });
  }

  /** Storage card v2: {"t":"storage","op",…} → storage_result.data (rejects with the error code). */
  storage<T = any>(uplinkId: string, op: string, params: Record<string, unknown> = {}, id?: string, timeoutMs = 6000): Promise<T> {
    return this.storageCall({ t: "storage", uplinkId, ...(id ? { id } : {}), op, ...params }, timeoutMs);
  }
  /** Craft preview: {"t":"craft_preview"} → craft_plan.plan. */
  craftPreview(uplinkId: string, name: string, count: number, id?: string): Promise<CraftPlan> {
    return this.storageCall({ t: "craft_preview", uplinkId, ...(id ? { id } : {}), name, count }, 8000);
  }
  private storageCall<T>(msg: Record<string, unknown>, timeoutMs: number): Promise<T> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      if (this.status.value !== "open" || !this.send({ ...msg, reqId })) return reject(new Error("disconnected"));
      const timer = setTimeout(() => {
        this.storageReqs.delete(reqId);
        reject(new Error("timeout"));
      }, timeoutMs);
      this.storageReqs.set(reqId, { resolve, reject, timer });
    });
  }

  /** item_search: {"t":"query"} → query_result. Rejects with the server error code (or "timeout"/"disconnected"). */
  query(uplinkId: string, id: string, q: string, timeoutMs = 5000): Promise<QueryResult> {
    const reqId = newReqId();
    return new Promise((resolve, reject) => {
      if (this.status.value !== "open" || !this.info.value?.online || !this.send({ t: "query", reqId, uplinkId, id, q: q.slice(0, 64) })) {
        reject(new Error(this.status.value !== "open" ? "disconnected" : "offline"));
        return;
      }
      const timer = setTimeout(() => this.queryFailed(reqId, "timeout"), timeoutMs);
      this.queries.set(reqId, { resolve, reject, timer });
    });
  }

  private queryFailed(reqId: string, error: string) {
    const q = this.queries.get(reqId);
    if (!q) return;
    clearTimeout(q.timer);
    this.queries.delete(reqId);
    q.reject(new Error(error));
  }

  private widgetValue(key: string): WidgetValue | undefined {
    const [uplinkId, id] = key.split("/");
    return this.uplinks.value.find((u) => u.uplinkId === uplinkId)?.widgets.find((w) => w.id === id)?.value;
  }

  /** The server reported a value for this widget: it wins over the optimistic one. */
  private settle(key: string) {
    const p = this.pendingMeta.get(key);
    if (!p) return;
    // only settle once the action was accepted or the server already shows our value
    if (this.requests.has(p.reqId) && !sameValue(this.widgetValue(key), p.value)) return;
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
    for (const [id] of this.queries) this.queryFailed(id, "disconnected");
    for (const [id, r] of this.storageReqs) {
      clearTimeout(r.timer);
      r.reject(new Error("disconnected"));
      this.storageReqs.delete(id);
    }
    for (const [id, r] of this.termReqs) {
      clearTimeout(r.timer);
      r.reject(new Error("disconnected"));
      this.termReqs.delete(id);
    }
    for (const [id, r] of this.ruleReqs) {
      clearTimeout(r.timer);
      r.reject(new Error("disconnected"));
      this.ruleReqs.delete(id);
    }
    for (const reqs of [this.sceneReqs, this.prodReqs] as Map<string, { reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>[])
      for (const [id, r] of reqs) {
        clearTimeout(r.timer);
        r.reject(new Error("disconnected"));
        reqs.delete(id);
      }
    for (const [id, r] of this.netReqs) {
      clearTimeout(r.timer);
      r.reject(new Error("disconnected"));
      this.netReqs.delete(id);
    }
    for (const [id, l] of this.layoutReqs) {
      clearTimeout(l.timer);
      l.reject(new Error("disconnected"));
      this.layoutReqs.delete(id);
    }
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
/** 4429: share link full (v0.6) – also stops retrying, the viewer explains it. */
const AUTH_CLOSE_CODES = new Set([4001, 4003, 4401, 4403, 4404, 4429]);

function isAuthError(msg: any) {
  if (msg.t === "auth_failed" || msg.t === "unauthorized") return true;
  if (msg.t !== "error" && msg.t !== "auth_error") return false;
  if (msg.t === "auth_error") return true;
  const why = String(msg.error ?? msg.reason ?? msg.code ?? "").toLowerCase();
  return /auth|token|revoked|forbidden|share_|no_access|mainframe_required/.test(why);
}

/** Scalars pass through; lists of plain objects (watchlist/jobs) are kept with scalar fields only. */
function toValue(v: unknown): WidgetValue {
  if (Array.isArray(v)) {
    return v
      .slice(0, 500)
      .filter((r) => r && typeof r === "object" && !Array.isArray(r))
      .map((r) => {
        const out: Row = {};
        for (const [k, x] of Object.entries(r as object)) if (x === null || ["string", "number", "boolean"].includes(typeof x)) out[k] = x as Row[string];
        return out;
      });
  }
  return toScalar(v);
}

function toScalar(v: unknown): Scalar {
  return typeof v === "number" || typeof v === "boolean" || typeof v === "string" ? v : null;
}

function normalizeInfo(m: any): MainframeInfo {
  return {
    id: String(m.id ?? ""),
    name: String(m.name ?? "Mainframe"),
    online: m.online !== false,
    enabled: m.enabled === true,
    reason: typeof m.reason === "string" ? m.reason.slice(0, 32) : undefined,
    power: normalizePower(m.power),
    removedUntil: until(m.removedUntil),
  };
}

/** A positive ms timestamp, else undefined (0 = not removed). */
function until(v: unknown): number | undefined {
  return typeof v === "number" && Number.isFinite(v) && v > 0 ? v : undefined;
}

export function normalizePower(p: any): PowerInfo | undefined {
  if (!p || typeof p !== "object") return undefined;
  const n = (v: unknown) => (typeof v === "number" && Number.isFinite(v) && v >= 0 ? v : 0);
  const capacity = n(p.capacity);
  const out: PowerInfo = { stored: Math.min(n(p.stored), capacity || Infinity), capacity, usage: n(p.usage), ok: p.ok !== false };
  if (Array.isArray(p.breakdown))
    out.breakdown = p.breakdown
      .filter((b: any) => b && typeof b.key === "string")
      .slice(0, 16)
      // efficiency is the saved FE (negative), so the items still sum to the usage
      .map((b: any) => ({
        key: b.key.slice(0, 32),
        fe: b.key === "efficiency" && typeof b.fe === "number" && Number.isFinite(b.fe) ? Math.min(0, b.fe) : n(b.fe),
        ...(typeof b.count === "number" ? { count: n(b.count) } : {}),
        ...(typeof b.fiber === "number" && b.fiber > 0 ? { fiber: n(b.fiber) } : {}),
      }));
  return out;
}

const ROLES = ["viewer", "operator", "admin", "owner", "share"];
const NODE_TYPES = ["mainframe", "relay", "advanced_relay", "ender_relay", "uplink", "advanced_uplink", "bridge", "device", "me_bridge", "rs_bridge"];
const NODE_STATUS = ["online", "offline", "connecting", "no_power", "no_route", "unloaded", "redstone", "cloud_disabled", "legacy_out_of_range", "over_capacity"];
const DEVICE_TYPES = ["uplink", "advanced_uplink", "bridge", "device", "me_bridge", "rs_bridge"];

/** Validates the untrusted network graph (v0.4 map). */
const FALLBACK_NAME: Record<string, string> = {
  mainframe: "Mainframe",
  relay: "Cloud Relay",
  advanced_relay: "Advanced Relay",
  ender_relay: "Ender Relay",
  uplink: "Uplink",
  advanced_uplink: "Advanced Uplink",
  bridge: "Bridge",
  device: "Unloaded device",
};

export function normalizeNetwork(raw: any): NetworkInfo | null {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.nodes)) return null;
  const num = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : d);
  const nodes: NetNode[] = [];
  const ids = new Set<string>();
  for (const n of raw.nodes.slice(0, 4096)) {
    if (!n || typeof n.id !== "string" || ids.has(n.id) || !NODE_TYPES.includes(n.type)) continue;
    ids.add(n.id);
    const pos = n.pos && typeof n.pos === "object" ? { x: num(n.pos.x), y: num(n.pos.y), z: num(n.pos.z) } : undefined;
    const flags = Array.isArray(n.flags) ? n.flags.filter((f: unknown): f is string => typeof f === "string").slice(0, 8) : undefined;
    const legacy = flags?.includes("legacy_out_of_range") || n.status === "legacy_out_of_range";
    const device = DEVICE_TYPES.includes(n.type);
    nodes.push({
      id: n.id.slice(0, 64),
      type: n.type,
      name: typeof n.name === "string" && n.name.trim() ? n.name.slice(0, 48) : FALLBACK_NAME[n.type] ?? n.type,
      ...(pos ? { pos } : {}),
      dim: typeof n.dim === "string" ? n.dim.slice(0, 64) : "minecraft:overworld",
      // a connected device kept by the legacy range grace is shown as its own state
      status: legacy && n.status !== "no_route" ? "legacy_out_of_range" : NODE_STATUS.includes(n.status) ? n.status : "offline",
      ...(n.power ? { power: normalizePower(n.power) } : {}),
      range: Math.max(0, num(n.range)),
      ...(typeof n.deviceRange === "number" ? { deviceRange: Math.max(0, num(n.deviceRange)) } : {}),
      ...(typeof n.uplinkId === "string" ? { uplinkId: n.uplinkId.slice(0, 64) } : device ? { uplinkId: n.id.slice(0, 64) } : {}),
      ...(typeof n.lastSeen === "number" ? { lastSeen: n.lastSeen } : {}),
      ...(typeof n.parent === "string" ? { parent: n.parent.slice(0, 64) } : {}),
      ...(typeof n.hops === "number" && n.hops >= 0 ? { hops: Math.min(16, Math.round(n.hops)) } : {}),
      ...(flags?.length ? { flags } : {}),
      ...(n.via === "cable" || n.via === "fiber" ? { via: n.via } : {}),
      ...(n.capacity && typeof n.capacity === "object"
        ? {
            capacity: {
              devices: Math.max(0, num(n.capacity.devices)),
              used: Math.max(0, num(n.capacity.used)),
              limit: typeof n.capacity.limit === "number" && n.capacity.limit >= 0 ? n.capacity.limit : null,
              over: Math.max(0, num(n.capacity.over)),
              wired: Math.max(0, num(n.capacity.wired)),
              fiber: Math.max(0, num(n.capacity.fiber)),
            },
          }
        : {}),
    });
  }
  // a parent that is not in the graph is dropped
  for (const n of nodes) if (n.parent && !ids.has(n.parent)) delete n.parent;
  const links: NetLink[] = (Array.isArray(raw.links) ? raw.links : [])
    .filter((l: any) => l && ids.has(l.a) && ids.has(l.b) && l.a !== l.b)
    .slice(0, 16384)
    .map((l: any) => ({
      a: l.a,
      b: l.b,
      signal: Math.max(0, Math.min(1, num(l.signal))),
      dist: Math.max(0, num(l.dist)),
      kind: l.kind === "ender" || l.kind === "wired" ? l.kind : "radio",
      ...(l.kind === "wired" ? { medium: l.medium === "fiber" ? "fiber" : "cc" } : {}),
      ...(l.legacy === true ? { legacy: true } : {}),
      ...(l.weak === true ? { weak: true } : {}),
    }));
  const routes: Record<string, string[]> = {};
  if (raw.routes && typeof raw.routes === "object")
    for (const [k, v] of Object.entries(raw.routes as Record<string, unknown>)) {
      if (!ids.has(k) || !Array.isArray(v)) continue;
      const path = v.filter((x): x is string => typeof x === "string" && ids.has(x)).slice(0, 9);
      if (path.length) routes[k] = path;
    }
  return { nodes, links, routes };
}

const fin = (v: unknown) => typeof v === "number" && Number.isFinite(v);
const lvl = (v: unknown) => (fin(v) ? Math.max(0, Math.min(15, Math.round(v as number))) : 0);
const CHANNEL = /^[A-Za-z0-9_.-]{1,32}$/;

export function normalizeRedstone(raw: any): RedstoneInfo | null {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.channels)) return null;
  const channels: RedstoneChannel[] = raw.channels
    .filter((c: any) => c && typeof c.name === "string" && CHANNEL.test(c.name))
    .slice(0, 64)
    .map((c: any) => ({
      name: c.name,
      level: lvl(c.level),
      signal: lvl(c.signal),
      transmitters: fin(c.transmitters) ? Math.max(0, c.transmitters) : 0,
      receivers: fin(c.receivers) ? Math.max(0, c.receivers) : 0,
      ...(c.override && typeof c.override === "object" ? { override: { level: lvl(c.override.level), until: fin(c.override.until) ? c.override.until : null } } : {}),
    }))
    .sort((a: RedstoneChannel, b: RedstoneChannel) => a.name.localeCompare(b.name));
  return { channels, limit: fin(raw.limit) ? raw.limit : 64 };
}

function normalizeClaims(raw: any): ClaimsOverlay | undefined {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.chunks)) return undefined;
  return {
    team: typeof raw.team === "string" ? raw.team.slice(0, 48) : "",
    // the mod sends "#rrggbb"
    color: typeof raw.color === "string" && /^#[0-9a-f]{6}$/i.test(raw.color) ? parseInt(raw.color.slice(1), 16) : fin(raw.color) ? raw.color & 0xffffff : 0x5ec8e5,
    chunks: raw.chunks
      .filter((c: any) => c && typeof c.dim === "string" && Number.isInteger(c.x) && Number.isInteger(c.z))
      .slice(0, 4096)
      .map((c: any) => ({ dim: c.dim.slice(0, 64), x: c.x, z: c.z, forced: c.forced === true })),
    truncated: raw.truncated === true,
  };
}
function normalizeWaystones(raw: any): WaystonesOverlay | undefined {
  if (!raw || typeof raw !== "object" || !Array.isArray(raw.items)) return undefined;
  return {
    items: raw.items
      .filter((w: any) => w && typeof w.dim === "string" && fin(w.x) && fin(w.z))
      .slice(0, 512)
      .map((w: any) => ({
        name: typeof w.name === "string" && w.name.trim() ? w.name.slice(0, 48) : "Waystone",
        dim: w.dim.slice(0, 64),
        x: w.x,
        y: fin(w.y) ? w.y : 64,
        z: w.z,
        kind: typeof w.kind === "string" ? w.kind.slice(0, 32) : "waystone",
        ...(w.global === true ? { global: true } : {}),
      })),
    truncated: raw.truncated === true,
  };
}

function normalizeUplink(u: any): Uplink | null {
  if (!u || typeof u.uplinkId !== "string") return null;
  const widgets = Array.isArray(u.widgets) ? u.widgets : [];
  return {
    uplinkId: u.uplinkId,
    title: typeof u.title === "string" ? u.title : "",
    online: u.online !== false,
    mode: u.mode === "auto" || u.mode === "lua" || u.mode === "direct" ? u.mode : undefined,
    ...(u.loaded === false ? { loaded: false, online: false } : {}),
    ...(u.loaded === false && typeof u.lastSeen === "number" && Number.isFinite(u.lastSeen) ? { lastSeen: u.lastSeen } : {}),
    widgets: widgets
      .filter((w: any) => w && typeof w.id === "string" && WIDGET_TYPES.includes(w.type))
      .map((w: any) => {
        const out: Widget = {
          id: w.id,
          type: w.type,
          label: typeof w.label === "string" ? w.label : undefined,
          opts: w.opts && typeof w.opts === "object" && !Array.isArray(w.opts) ? w.opts : {},
          value: w.value === undefined ? undefined : toValue(w.value),
        };
        if (w.type === "chart") {
          // history either as `history` or as an array value
          const raw = Array.isArray(w.history) ? w.history : Array.isArray(w.value) ? w.value : [];
          out.history = numbers(raw, chartCap(out));
          if (Array.isArray(w.value)) out.value = out.history.at(-1) ?? null;
        }
        return out;
      }),
    pages: Array.isArray(u.pages)
      ? u.pages.filter((p: any) => p && typeof p.id === "string").slice(0, 12).map((p: any) => ({ id: p.id, title: str(p.title), icon: str(p.icon) }))
      : undefined,
    ...(normalizeClaims(u.claims) ? { claims: normalizeClaims(u.claims) } : {}),
    ...(normalizeWaystones(u.waystones) ? { waystones: normalizeWaystones(u.waystones) } : {}),
    groups: Array.isArray(u.groups)
      ? u.groups
          .filter((g: any) => g && typeof g.id === "string")
          .slice(0, 32)
          .map((g: any) => ({ id: g.id, title: str(g.title), page: str(g.page), cols: typeof g.cols === "number" ? g.cols : undefined }))
      : undefined,
  };
}

const str = (v: unknown) => (typeof v === "string" ? v : undefined);
const chartCap = (w: Widget) => Math.min(300, Math.max(2, typeof w.opts?.points === "number" ? w.opts.points : 60));
function numbers(raw: unknown[], cap: number) {
  return raw.filter((x): x is number => typeof x === "number" && Number.isFinite(x)).slice(-cap);
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

// ------------------------------------------------------------- permissions
export { PERMISSIONS, presetHas } from "./permissions.ts";
function parsePerms(raw: unknown): Set<string> | null {
  if (!Array.isArray(raw)) return null;
  return new Set(raw.filter((p): p is string => typeof p === "string" && p.length <= 40).slice(0, 64));
}
export function parseStore(raw: unknown): { url: string; key?: string } | null {
  const url = (raw as { url?: unknown } | null)?.url;
  const key = (raw as { key?: unknown } | null)?.key;
  if (typeof url !== "string" || !/^https?:\/\/[^\s"'<>]+\/$/.test(url) || url.length > 256) return null;
  // https only, except a local dev store on the same origin
  if (!url.startsWith("https://") && !(typeof location !== "undefined" && url.startsWith(location.origin)) && !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(url)) return null;
  // the store's Ed25519 public key (base64 of 32 bytes), for the browser-side signature check
  return { url, ...(typeof key === "string" && /^[A-Za-z0-9+/]{43}=$/.test(key) ? { key } : {}) };
}
