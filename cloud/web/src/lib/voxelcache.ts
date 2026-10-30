// Voxel sections cached in the browser (IndexedDB), so reopening the 3D view
// shows the base at once; the server then only answers "same" for unchanged
// sections (docs/SPEC.md "Voxel view – batch API"). Keyed by
// `<mainframe>|<dim>|<cx>,<cy>,<cz>`. Everything here fails soft: without
// IndexedDB (private mode, blocked storage) the view simply loads from the
// server. Entries of a mainframe are dropped when it is removed from this
// browser or its access is refused (docs/SECURITY.md "Map, voxels and X-ray").
import type { PaletteEntry } from "../network/voxels.ts";

export interface CachedSection {
  /** `<mainframe>|<dim>|<cx>,<cy>,<cz>` */
  k: string;
  /** content hash from the server (16 hex digits) */
  h: string;
  /** exposed blocks (0 = nothing to draw) */
  n: number;
  /** the section's palette */
  p: PaletteEntry[];
  /** raw runs */
  r: Uint8Array;
  /** when it was stored (ms) */
  t: number;
}

const DB = "cccloud-voxels";
const STORE = "sections";
let opening: Promise<IDBDatabase | null> | null = null;

function open(): Promise<IDBDatabase | null> {
  if (opening) return opening;
  opening = new Promise((resolve) => {
    try {
      if (typeof indexedDB === "undefined") return resolve(null);
      const req = indexedDB.open(DB, 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains(STORE)) req.result.createObjectStore(STORE, { keyPath: "k" });
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
      req.onblocked = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
  return opening;
}

const range = (prefix: string) => IDBKeyRange.bound(`${prefix}|`, `${prefix}|￿`);
export const scopeOf = (mainframe: string, dim: string) => `${mainframe}|${dim}`;
export const keyOf = (scope: string, cx: number, cy: number, cz: number) => `${scope}|${cx},${cy},${cz}`;

/** Every cached section of a mainframe + dimension. */
export async function readScope(scope: string): Promise<CachedSection[]> {
  const db = await open();
  if (!db) return [];
  return new Promise((resolve) => {
    try {
      const req = db.transaction(STORE, "readonly").objectStore(STORE).getAll(range(scope));
      req.onsuccess = () => resolve((req.result as CachedSection[]).filter((e) => e && typeof e.h === "string" && typeof e.n === "number" && Array.isArray(e.p) && e.r instanceof Uint8Array));
      req.onerror = () => resolve([]);
    } catch {
      resolve([]);
    }
  });
}

/** Stores and deletes sections in one transaction. */
export async function writeSections(put: CachedSection[], del: string[] = []): Promise<void> {
  if (!put.length && !del.length) return;
  const db = await open();
  if (!db) return;
  await new Promise<void>((resolve) => {
    try {
      const tx = db.transaction(STORE, "readwrite");
      const store = tx.objectStore(STORE);
      for (const e of put) store.put(e);
      for (const k of del) store.delete(k);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
      tx.onabort = () => resolve();
    } catch {
      resolve();
    }
  });
}

/**
 * The cached blocks of a mainframe must go when this browser may no longer see positions there: the role lost
 * "See coordinates" (view.positions). Null = an older server that sends no permissions: nothing to decide.
 */
export const voxelsForbidden = (perms: ReadonlySet<string> | null) => perms !== null && !perms.has("view.positions");

/** Drops everything cached for a mainframe (all dimensions), or for one scope. */
export async function clearVoxelCache(prefix: string): Promise<void> {
  const db = await open();
  if (!db) return;
  await new Promise<void>((resolve) => {
    try {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).delete(range(prefix));
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
      tx.onabort = () => resolve();
    } catch {
      resolve();
    }
  });
}
