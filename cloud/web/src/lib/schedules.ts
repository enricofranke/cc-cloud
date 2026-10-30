// v0.10 schedules (docs/SPEC.md "Schedules (mod side)"): time-based rule triggers – `interval` (real time),
// `schedule` (weekdays at HH:MM in the server's time zone) and `time` (in-game time of day) – plus the
// plain-language texts both languages need ("Mon–Fri at 07:30", "next run in 2 h").
import type { Lang } from "./lang.ts";

export const DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;
export type Day = (typeof DAYS)[number];

export type TimeTrigger = { type: "interval"; minutes: number } | { type: "schedule"; days: Day[]; at: string } | { type: "time"; time: number };
export const TIME_TRIGGERS = ["interval", "schedule", "time"] as const;
/** "Schedules" are rules whose trigger is one of these (they count against maxSchedulesPerMainframe). */
export const isTimeTrigger = (w: { type: string }): w is TimeTrigger => (TIME_TRIGGERS as readonly string[]).includes(w.type);

/** The rules document's `schedules` object. */
export interface SchedulesInfo {
  count: number;
  limit: number;
  /** minIntervalMinutes */
  minInterval: number;
  /** IANA zone the `schedule` triggers use ("" = unknown) */
  timezone: string;
}

/** In-game presets (ticks): dawn, noon, dusk, midnight. */
export const TIME_PRESETS = [
  { id: "dawn", time: 23000 },
  { id: "noon", time: 6000 },
  { id: "dusk", time: 12000 },
  { id: "midnight", time: 18000 },
] as const;
export type TimePreset = (typeof TIME_PRESETS)[number]["id"];
export const presetOf = (time: number): TimePreset | null => TIME_PRESETS.find((p) => p.time === time)?.id ?? null;

const DAY_SHORT: Record<Lang, Record<Day, string>> = {
  en: { mon: "Mon", tue: "Tue", wed: "Wed", thu: "Thu", fri: "Fri", sat: "Sat", sun: "Sun" },
  de: { mon: "Mo", tue: "Di", wed: "Mi", thu: "Do", fri: "Fr", sat: "Sa", sun: "So" },
};
export const dayShort = (d: Day, lang: Lang) => DAY_SHORT[lang][d];

const TXT = {
  en: {
    everyDay: "every day",
    weekdays: "Mon–Fri",
    weekend: "weekends",
    minute: "every minute",
    minutes: "every {n} minutes",
    hours: "every {n} h",
    at: "{days} at {at}",
    ingame: "at {clock} in-game",
    preset: "at {preset} (in-game)",
    dawn: "dawn",
    noon: "noon",
    dusk: "dusk",
    midnight: "midnight",
    now: "now",
    in: "in {t}",
    ago: "{t} ago",
    next: "next run {t}",
    overdue: "due now",
  },
  de: {
    everyDay: "täglich",
    weekdays: "Mo–Fr",
    weekend: "am Wochenende",
    minute: "jede Minute",
    minutes: "alle {n} Minuten",
    hours: "alle {n} Std.",
    at: "{days} um {at}",
    ingame: "um {clock} Spielzeit",
    preset: "bei {preset} (Spielzeit)",
    dawn: "Sonnenaufgang",
    noon: "Mittag",
    dusk: "Sonnenuntergang",
    midnight: "Mitternacht",
    now: "jetzt",
    in: "in {t}",
    ago: "vor {t}",
    next: "nächster Lauf {t}",
    overdue: "jetzt fällig",
  },
};
const fill = (s: string, v: Record<string, string | number>) => s.replace(/\{(\w+)\}/g, (_, k) => String(v[k] ?? ""));

export const presetName = (p: TimePreset, lang: Lang) => TXT[lang][p];

/** "every day", "Mon–Fri", "weekends", "Mon–Wed, Fri" (runs of 3+ days are collapsed). */
export function daysText(days: readonly Day[], lang: Lang): string {
  const set = new Set(days);
  const idx = DAYS.filter((d) => set.has(d)).map((d) => DAYS.indexOf(d));
  if (idx.length === 7) return TXT[lang].everyDay;
  if (idx.join() === "0,1,2,3,4") return TXT[lang].weekdays;
  if (idx.join() === "5,6") return TXT[lang].weekend;
  const parts: string[] = [];
  for (let i = 0; i < idx.length; ) {
    let j = i;
    while (j + 1 < idx.length && idx[j + 1] === idx[j] + 1) j++;
    const a = DAY_SHORT[lang][DAYS[idx[i]]];
    const b = DAY_SHORT[lang][DAYS[idx[j]]];
    if (j - i >= 2) parts.push(`${a}–${b}`);
    else for (let k = i; k <= j; k++) parts.push(DAY_SHORT[lang][DAYS[idx[k]]]);
    i = j + 1;
  }
  return parts.join(", ");
}

/** In-game ticks (0 = 06:00) → "HH:MM". */
export function ticksToClock(t: number) {
  const mins = Math.round(((((t + 6000) % 24000) + 24000) % 24000) * 0.06);
  return `${String(Math.floor(mins / 60) % 24).padStart(2, "0")}:${String(mins % 60).padStart(2, "0")}`;
}
/** "HH:MM" → in-game ticks, null for a bad time. */
export function clockToTicks(hhmm: string) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm.trim());
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) return null;
  const mins = Number(m[1]) * 60 + Number(m[2]);
  return Math.round((((mins / 0.06 - 6000) % 24000) + 24000) % 24000);
}

/** `at` of a schedule trigger: "HH:MM", 24 h. */
export const validAt = (at: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(at);
/** Days of a schedule trigger: 1–7, known, unique. */
export const validDays = (days: readonly string[]) => days.length >= 1 && days.length <= 7 && new Set(days).size === days.length && days.every((d) => (DAYS as readonly string[]).includes(d));

/** A time trigger in plain words: "every 10 minutes", "Mon–Fri at 07:30", "at dusk (in-game)". */
export function describeWhen(t: TimeTrigger, lang: Lang): string {
  const x = TXT[lang];
  switch (t.type) {
    case "interval":
      return t.minutes === 1 ? x.minute : t.minutes % 60 === 0 ? fill(x.hours, { n: t.minutes / 60 }) : fill(x.minutes, { n: t.minutes });
    case "schedule":
      return fill(x.at, { days: daysText(t.days, lang), at: t.at });
    case "time": {
      const p = presetOf(t.time);
      return p ? fill(x.preset, { preset: x[p] }) : fill(x.ingame, { clock: ticksToClock(t.time) });
    }
  }
}

/** A duration in words: "45 s", "12 min", "2 h", "3 days" (rounded, ≥ 1 unit). */
export function durationText(ms: number, lang: Lang): string {
  const s = Math.max(0, Math.round(ms / 1000));
  if (s < 60) return lang === "de" ? `${s} s` : `${s} s`;
  const m = Math.round(s / 60);
  if (m < 60) return lang === "de" ? `${m} Min.` : `${m} min`;
  const h = Math.round(m / 60);
  if (h < 48) return lang === "de" ? `${h} Std.` : `${h} h`;
  const d = Math.round(h / 24);
  return lang === "de" ? `${d} Tagen` : `${d} days`;
}
/** "in 2 h" / "in 2 Std."; "now" when it is (about) due. */
export function inText(ts: number, lang: Lang, now = Date.now()) {
  return ts - now < 30_000 ? TXT[lang].now : fill(TXT[lang].in, { t: durationText(ts - now, lang) });
}
/** "2 h ago" / "vor 2 Std."; "just now" style for the last half minute. */
export function agoText(ts: number, lang: Lang, now = Date.now()) {
  if (now - ts < 10_000) return lang === "de" ? "gerade eben" : "just now";
  const d = durationText(now - ts, lang);
  // German dative: "vor 3 Tagen" (durationText already says "Tagen")
  return fill(TXT[lang].ago, { t: d });
}
/** "next run in 2 h" for a rule's nextRun; "" without one. */
export function nextRunText(nextRun: number | null | undefined, lang: Lang, now = Date.now()) {
  if (typeof nextRun !== "number") return "";
  if (nextRun <= now) return TXT[lang].overdue;
  return fill(TXT[lang].next, { t: inText(nextRun, lang, now) });
}

// ------------------------------------------------------------ next occurrence
/** Offset of a time zone at a moment (ms, local − UTC); 0 for an unknown zone. */
export function zoneOffset(ts: number, timeZone: string): number {
  try {
    const parts = new Intl.DateTimeFormat("en-US", { timeZone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" }).formatToParts(new Date(ts));
    const v = (k: string) => Number(parts.find((p) => p.type === k)?.value ?? 0);
    return Date.UTC(v("year"), v("month") - 1, v("day"), v("hour"), v("minute"), v("second")) - Math.floor(ts / 1000) * 1000;
  } catch {
    return 0;
  }
}
/** Wall-clock time in a zone → epoch ms (DST gaps move forward like java.time). */
function zoned(y: number, mo: number, d: number, h: number, mi: number, tz: string) {
  const guess = Date.UTC(y, mo, d, h, mi);
  const first = guess - zoneOffset(guess, tz);
  return guess - zoneOffset(first, tz);
}

/**
 * The next occurrence of a `schedule` trigger after `from` (ms), computed in `timeZone`
 * (empty or unknown = UTC). null for an invalid trigger. Used for the editor's preview and by the mock.
 */
export function nextScheduleRun(days: readonly Day[], at: string, timeZone: string, from = Date.now()): number | null {
  if (!validDays(days) || !validAt(at)) return null;
  const tz = timeZone || "UTC";
  const [h, mi] = at.split(":").map(Number);
  const local = new Date(from + zoneOffset(from, tz));
  for (let i = 0; i <= 7; i++) {
    const day = new Date(Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + i));
    const dow = DAYS[(day.getUTCDay() + 6) % 7];
    if (!days.includes(dow)) continue;
    const ts = zoned(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(), h, mi, tz);
    if (ts > from) return ts;
  }
  return null;
}
