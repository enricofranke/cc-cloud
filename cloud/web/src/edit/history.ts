// Undo/redo for the layout editor. Snapshots are immutable page arrays;
// rapid edits of the same field (typing a label) coalesce into one step.

export interface History<T> {
  /** the state the editor started with (dirty = we are somewhere else) */
  initial: T;
  stack: T[];
  index: number;
  /** coalescing key + time of the last push */
  lastKey?: string;
  lastAt?: number;
}

export const MAX_HISTORY = 100;

export const createHistory = <T>(initial: T): History<T> => ({ initial, stack: [initial], index: 0 });
export const current = <T>(h: History<T>) => h.stack[h.index];
export const canUndo = (h: History<unknown>) => h.index > 0;
export const canRedo = (h: History<unknown>) => h.index < h.stack.length - 1;

/** Pushes a new state (dropping the redo branch). Same `key` within `windowMs` replaces the top instead. */
export function push<T>(h: History<T>, next: T, key?: string, now = Date.now(), windowMs = 800): History<T> {
  if (next === current(h)) return h;
  if (key && h.lastKey === key && h.lastAt !== undefined && now - h.lastAt < windowMs && h.index > 0) {
    const stack = [...h.stack.slice(0, h.index), next];
    return { ...h, stack, index: stack.length - 1, lastKey: key, lastAt: now };
  }
  let stack = [...h.stack.slice(0, h.index + 1), next];
  if (stack.length > MAX_HISTORY) stack = stack.slice(stack.length - MAX_HISTORY);
  return { ...h, stack, index: stack.length - 1, lastKey: key, lastAt: now };
}

export const undo = <T>(h: History<T>): History<T> => (canUndo(h) ? { ...h, index: h.index - 1, lastKey: undefined } : h);
export const redo = <T>(h: History<T>): History<T> => (canRedo(h) ? { ...h, index: h.index + 1, lastKey: undefined } : h);
/** Dirty = differs from the state the editor started with. */
export const isDirty = <T>(h: History<T>) => current(h) !== h.initial;
