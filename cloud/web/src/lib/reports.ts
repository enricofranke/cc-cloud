// Crash reports, usage stats and feedback (docs/PRIVACY.md): the owner's decision and the exact payloads, through
// /api/mainframes/<id>/reports and /feedback. Nothing leaves the server from here; the mod does that after a yes.
import type { Lang } from "./i18n.ts";

export type ReportChannel = "crash" | "stats";
export type ReportState = "ask" | "active" | "declined";
export type FeedbackCategory = "bug" | "idea" | "other";
export const REPORT_CHANNELS: ReportChannel[] = ["crash", "stats"];
export const FEEDBACK_CATEGORIES: FeedbackCategory[] = ["bug", "idea", "other"];
export const FEEDBACK_MAX = 1500;
export const CONTACT_MAX = 120;
/** A request body may be 4 KB; long texts in scripts with three-byte characters reach that first. */
export const FEEDBACK_MAX_BYTES = 3600;

export interface ReportsDoc {
  version: number;
  /** server owner on a browser that may write */
  canDecide: boolean;
  /** the one-time question is due */
  prompt: boolean;
  /** only for the server owner */
  channels: Record<ReportChannel, ReportState> | null;
  /** this browser may send feedback */
  feedback: boolean;
}

export interface ReportPreview {
  crash: { example: boolean; payload: unknown | null };
  stats: { payload: unknown | null };
}

export interface FeedbackInput {
  category: FeedbackCategory;
  text: string;
  contact?: string;
  technical?: boolean;
}

const state = (v: unknown): ReportState => (v === "active" || v === "declined" ? v : "ask");

export function parseReports(raw: any): ReportsDoc {
  if (!raw || typeof raw !== "object" || typeof raw.version !== "number") throw new Error("bad_reports");
  const c = raw.channels;
  return {
    version: raw.version,
    canDecide: raw.canDecide === true,
    prompt: raw.prompt === true,
    channels: c && typeof c === "object" ? { crash: state(c.crash), stats: state(c.stats) } : null,
    feedback: raw.feedback === true,
  };
}

export function parsePreview(raw: any): ReportPreview {
  if (!raw || typeof raw !== "object") throw new Error("bad_reports");
  return {
    crash: { example: raw.crash?.example !== false, payload: raw.crash?.payload ?? null },
    stats: { payload: raw.stats?.payload ?? null },
  };
}

/** What the sheet shows as the message that would go out. */
export const pretty = (payload: unknown) => JSON.stringify(payload, null, 2);

/** The reason the Send step is closed, or null. */
export function feedbackProblem(input: FeedbackInput): "empty" | "too_long" | null {
  if (!input.text.trim()) return "empty";
  if (input.text.length > FEEDBACK_MAX || (input.contact ?? "").length > CONTACT_MAX) return "too_long";
  if (new TextEncoder().encode(input.text + (input.contact ?? "")).length > FEEDBACK_MAX_BYTES) return "too_long";
  return null;
}

export const privacyUrl = (l: Lang) => `https://cccloud.dev/${l === "de" ? "de" : "en"}/legal/privacy/`;

async function call(base: string, token: string, path: string, init: RequestInit = {}) {
  let res: Response;
  try {
    res = await fetch(`${base}${path}`, {
      ...init,
      headers: { authorization: `Bearer ${token}`, ...(init.body ? { "content-type": "application/json" } : {}) },
      cache: "no-store",
    });
  } catch {
    throw new Error("unreachable");
  }
  let body: any = null;
  try {
    body = await res.json();
  } catch {}
  if (!res.ok) throw new Error(body?.error ?? `http_${res.status}`);
  return body;
}

const reportsPath = (mf: string) => `/api/mainframes/${encodeURIComponent(mf)}/reports`;
const post = (body: unknown): RequestInit => ({ method: "POST", body: JSON.stringify(body) });

export const getReports = async (base: string, token: string, mf: string) => parseReports(await call(base, token, reportsPath(mf)));
export const getPreview = async (base: string, token: string, mf: string) => parsePreview(await call(base, token, `${reportsPath(mf)}/preview`));
export const decideReport = async (base: string, token: string, mf: string, channel: ReportChannel, yes: boolean) =>
  parseReports(await call(base, token, reportsPath(mf), post({ channel, decision: yes ? "on" : "off" })));
export const dismissReports = async (base: string, token: string, mf: string) => parseReports(await call(base, token, reportsPath(mf), post({ dismiss: true })));

const feedbackPath = (mf: string) => `/api/mainframes/${encodeURIComponent(mf)}/feedback`;
const feedbackBody = ({ category, text, contact, technical }: FeedbackInput) => ({ category, text, ...(contact?.trim() ? { contact: contact.trim() } : {}), ...(technical ? { technical: true } : {}) });

/** The exact message, cleaned by the server. Sends nothing. */
export const previewFeedback = async (base: string, token: string, mf: string, input: FeedbackInput): Promise<unknown> =>
  (await call(base, token, feedbackPath(mf), post(feedbackBody(input)))).payload;

/** Sends what {@link previewFeedback} showed: only after the writer confirmed that very message. */
export const sendFeedback = async (base: string, token: string, mf: string, input: FeedbackInput): Promise<boolean> =>
  (await call(base, token, feedbackPath(mf), post({ ...feedbackBody(input), confirm: true }))).sent === true;
