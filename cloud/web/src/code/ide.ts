// Cloud Scripts: the IDE's logic that needs no browser – editor markers, the log view, line diffs and names. Kept
// apart from lib/code.ts, which the connection (and so the main chunk) needs; this only loads with the Scripts screen.
import { MAX_NAME, errorLine, errorText, type Diagnostic, type LogLine, type ScriptError, type ScriptSummary } from "../lib/code.ts";

// ------------------------------------------------------------------ markers

export interface Marker {
  from: number;
  to: number;
  severity: "error" | "warning";
  message: string;
  /** compile check (while typing) or the last runtime error of the saved version */
  source: "check" | "runtime";
}

/** Offsets of the start of each line (1-based line n starts at starts[n - 1]). */
function lineStarts(text: string): number[] {
  const starts = [0];
  for (let i = 0; i < text.length; i++) if (text.charCodeAt(i) === 10) starts.push(i + 1);
  return starts;
}
function span(text: string, starts: number[], line: number, column: number | null): { from: number; to: number } {
  const n = Math.min(Math.max(1, line), starts.length);
  const start = starts[n - 1];
  const end = n < starts.length ? starts[n] - 1 : text.length;
  if (column !== null) {
    const from = Math.min(start + column - 1, end);
    // the word under the column, else one character
    let to = from;
    while (to < end && /[\w.]/.test(text[to])) to++;
    return { from, to: to > from ? to : Math.min(from + 1, end) };
  }
  // the whole line without its indentation; an empty line gets a zero-width marker at its start
  let from = start;
  while (from < end && /\s/.test(text[from])) from++;
  return from < end ? { from, to: end } : { from: start, to: start };
}

/**
 * Editor markers for check diagnostics and the runtime error. The runtime error only counts while the text is the
 * saved one (`runtimeApplies`): after an edit the line numbers may point elsewhere.
 */
export function markersFor(text: string, diags: Diagnostic[], runtime: ScriptError | null, runtimeApplies: boolean): Marker[] {
  const starts = lineStarts(text);
  const out: Marker[] = diags.map((d) => ({ ...span(text, starts, d.line, d.column), severity: d.severity, message: errorText(d.message), source: "check" as const }));
  if (runtime && runtimeApplies) {
    const line = runtime.line ?? errorLine(runtime.message);
    if (line) out.push({ ...span(text, starts, line, null), severity: "error", message: errorText(runtime.message), source: "runtime" });
  }
  return out;
}

// ------------------------------------------------------------------ log

/** "Clear view" hides what was there; the server keeps its lines. */
export function visibleLog(lines: LogLine[], clearedAt: number): LogLine[] {
  return clearedAt ? lines.filter((l) => l.ts > clearedAt) : lines;
}
export function logTime(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

// ------------------------------------------------------------------ diff

export type DiffLine = { kind: "same" | "add" | "del"; text: string; a: number | null; b: number | null };
export type DiffRow = DiffLine | { kind: "skip"; count: number };

const splitLines = (s: string) => (s === "" ? [] : s.replace(/\r\n?/g, "\n").replace(/\n$/, "").split("\n"));

/**
 * Line diff of `a` (old) against `b` (new). Common head and tail are cut first, the middle is an LCS table; a middle
 * too big for that (both sides over ~2000 lines) shows as removed then added, which is still correct.
 */
export function diffLines(a: string, b: string): DiffLine[] {
  const A = splitLines(a);
  const B = splitLines(b);
  let head = 0;
  while (head < A.length && head < B.length && A[head] === B[head]) head++;
  let tail = 0;
  while (tail < A.length - head && tail < B.length - head && A[A.length - 1 - tail] === B[B.length - 1 - tail]) tail++;
  const out: DiffLine[] = [];
  for (let i = 0; i < head; i++) out.push({ kind: "same", text: A[i], a: i + 1, b: i + 1 });
  const am = A.slice(head, A.length - tail);
  const bm = B.slice(head, B.length - tail);
  const n = am.length;
  const m = bm.length;
  if (n * m > 4_000_000) {
    am.forEach((t, i) => out.push({ kind: "del", text: t, a: head + i + 1, b: null }));
    bm.forEach((t, j) => out.push({ kind: "add", text: t, a: null, b: head + j + 1 }));
  } else {
    // lcs[i][j] = LCS length of am[i..] and bm[j..]
    const w = m + 1;
    const lcs = new Uint32Array((n + 1) * w);
    for (let i = n - 1; i >= 0; i--)
      for (let j = m - 1; j >= 0; j--) lcs[i * w + j] = am[i] === bm[j] ? lcs[(i + 1) * w + j + 1] + 1 : Math.max(lcs[(i + 1) * w + j], lcs[i * w + j + 1]);
    let i = 0;
    let j = 0;
    while (i < n || j < m) {
      if (i < n && j < m && am[i] === bm[j]) {
        out.push({ kind: "same", text: am[i], a: head + i + 1, b: head + j + 1 });
        i++;
        j++;
      } else if (j < m && (i >= n || lcs[i * w + j + 1] >= lcs[(i + 1) * w + j])) {
        out.push({ kind: "add", text: bm[j], a: null, b: head + j + 1 });
        j++;
      } else {
        out.push({ kind: "del", text: am[i], a: head + i + 1, b: null });
        i++;
      }
    }
  }
  for (let k = tail; k > 0; k--) out.push({ kind: "same", text: A[A.length - k], a: A.length - k + 1, b: B.length - k + 1 });
  return out;
}
export function diffStats(d: DiffLine[]) {
  let add = 0;
  let del = 0;
  for (const l of d) if (l.kind === "add") add++;
    else if (l.kind === "del") del++;
  return { add, del };
}
/** Unchanged runs longer than 2 × context fold into one "skip" row. */
export function foldDiff(d: DiffLine[], context = 3): DiffRow[] {
  const out: DiffRow[] = [];
  let i = 0;
  while (i < d.length) {
    if (d[i].kind !== "same") {
      out.push(d[i++]);
      continue;
    }
    let j = i;
    while (j < d.length && d[j].kind === "same") j++;
    const run = j - i;
    const keepHead = i === 0 ? 0 : context;
    const keepTail = j === d.length ? 0 : context;
    if (run > keepHead + keepTail + 1) {
      for (let k = i; k < i + keepHead; k++) out.push(d[k]);
      out.push({ kind: "skip", count: run - keepHead - keepTail });
      for (let k = j - keepTail; k < j; k++) out.push(d[k]);
    } else for (let k = i; k < j; k++) out.push(d[k]);
    i = j;
  }
  return out;
}

// ------------------------------------------------------------------ misc

/** UTF-8 size of a source, as the server counts it against maxSource. */
export function byteSize(s: string): number {
  let n = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c < 0x80) n += 1;
    else if (c < 0x800) n += 2;
    else if (c >= 0xd800 && c < 0xdc00) {
      n += 4;
      i++;
    } else n += 3;
  }
  return n;
}
/** A trimmed name, or null when it is empty, too long or has control characters. */
export function cleanName(name: string): string | null {
  const t = name.trim();
  return t && t.length <= MAX_NAME && !/[\u0000-\u001f\u007f]/.test(t) ? t : null;
}
/** A free name: "Night mode", "Night mode 2", … */
export function uniqueName(base: string, taken: string[]): string {
  const lower = new Set(taken.map((x) => x.toLowerCase()));
  const b = base.trim().slice(0, MAX_NAME - 3) || "Script";
  if (!lower.has(b.toLowerCase())) return b;
  for (let n = 2; n < 100; n++) if (!lower.has(`${b} ${n}`.toLowerCase())) return `${b} ${n}`;
  return b;
}
/** CPU use against the limit, 0..1 (more when over: the script then pauses). */
export const cpuShare = (cpu: ScriptSummary["cpu"]) => (cpu.limit > 0 ? cpu.avg / cpu.limit : 0);
