// Factory Manager program view: the on-demand detail document, the line diff of the confirm step and file checks.

export interface ProgramDetail {
  state: string;
  disk: boolean;
  undo: boolean;
  name?: string;
  program?: string;
  truncated?: boolean;
  errors?: string[];
  warnings?: string[];
}

/** Longest program the server accepts, in characters (ClientMessages.MAX_PROGRAM). */
export const MAX_PROGRAM = 32 * 1024;
export const PROGRAM_FILES = ".sfml,.sfm,.txt";

export function lineCount(text: string): number {
  const trimmed = text.replace(/\n+$/, "");
  return trimmed ? trimmed.split("\n").length : 0;
}

export interface ProgramDiff {
  oldLines: number;
  newLines: number;
  /** Lines both versions start with. */
  same: number;
  removed: string[];
  added: string[];
}

/** The changed middle of two programs: everything between the common first and last lines. */
export function programDiff(before: string, after: string): ProgramDiff {
  const a = before.replace(/\r\n/g, "\n").replace(/\n+$/, "").split("\n");
  const b = after.replace(/\r\n/g, "\n").replace(/\n+$/, "").split("\n");
  if (a.length === 1 && a[0] === "") a.length = 0;
  if (b.length === 1 && b[0] === "") b.length = 0;
  let start = 0;
  while (start < a.length && start < b.length && a[start] === b[start]) start++;
  let tail = 0;
  while (tail < a.length - start && tail < b.length - start && a[a.length - 1 - tail] === b[b.length - 1 - tail]) tail++;
  return { oldLines: a.length, newLines: b.length, same: start, removed: a.slice(start, a.length - tail), added: b.slice(start, b.length - tail) };
}

/** Why a file cannot be loaded into the editor, or null. */
export function programFileError(name: string, size: number): string | null {
  if (!/\.(sfml|sfm|txt)$/i.test(name)) return "Pick a .sfml, .sfm or .txt file.";
  return size > MAX_PROGRAM ? `That file is too large (at most ${MAX_PROGRAM / 1024} KB).` : null;
}

export const PROGRAM_ERRORS: Record<string, string> = {
  no_disk: "There is no disk in the manager.",
  empty: "The program is empty.",
  nothing_to_undo: "There is nothing to undo any more.",
  compile_failed: "The new program has errors, so the running one was kept.",
  too_large: "The program is too long for the dashboard.",
  rate_limited: "Too many requests – wait a moment.",
  remote_no_pin: "This mainframe has no PIN, so programs cannot be written from the internet.",
  claim_blocked: "This manager is in a chunk claimed by another team.",
  not_allowed: "You may not change this program.",
  read_only: "You may not change this program.",
  uplink_offline: "The card is offline.",
  unloaded: "The manager's chunk is not loaded.",
  offline: "The mainframe is offline.",
  timeout: "No answer from the server.",
  disconnected: "Not connected.",
};
