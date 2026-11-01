// What the page and the worker agree on up front, so the page can render before the worker has started.

/** The visitor's "browser": an account token of the base's owner. */
export const TOKEN = "demo-session";
export const PLAYER = "Alex";

/** Fixed ids make links like /demo/#/m/<id>/rules survive a reload. */
export const BASE_ID = "de300000-0000-4000-8000-000000000001";
export const MAINFRAMES = [
  { id: BASE_ID, name: "Mainframe @ 12 64 -30" },
  { id: "de300000-0000-4000-8000-000000000002", name: "Deep Base @ 0 -61 0" },
  { id: "de300000-0000-4000-8000-000000000003", name: "Mod Showcase @ 500 64 500" },
];

// ---- messages between the page (bridge.ts) and the worker (worker.ts)
export type ToWorker =
  | { t: "pause"; paused: boolean }
  | { t: "http"; id: number; method: string; url: string; headers: Record<string, string>; body?: Uint8Array }
  | { t: "ws_open"; id: number }
  | { t: "ws_send"; id: number; data: string }
  | { t: "ws_close"; id: number; code?: number; reason?: string };

export type FromWorker =
  | { t: "http"; id: number; status: number; headers: Record<string, string>; body: Uint8Array | null }
  | { t: "ws_open"; id: number }
  | { t: "ws_message"; id: number; data: string }
  | { t: "ws_close"; id: number; code: number; reason: string };
