// Crafting job rows: what the status and the reason of a job mean in plain words.

export interface MissingItem {
  name: string;
  displayName: string;
  have: number;
  need: number;
}

export type JobStatus = "queued" | "running" | "waiting" | "stuck" | "done" | "failed" | "cancelled";

const STATUS: Record<JobStatus, string> = { queued: "Queued", running: "Running", waiting: "Waiting for CPU", stuck: "Stuck", done: "Done", failed: "Failed", cancelled: "Cancelled" };

/** Why a stuck job does not move (BridgeCore / the adapters); the same words as the alert. */
const REASON: Record<string, string> = {
  not_enough_items: "Not enough items",
  no_cpu: "No free crafting CPU",
  no_machine: "No machine found for a step",
  machine_rejected: "The machine does not accept the items",
  machine_locked: "The machine is locked",
  suspended: "Suspended in the crafting status",
  offline: "The storage network is offline",
  planning: "Still planning",
  no_progress: "No progress",
};

export const jobStatusText = (j: { status: JobStatus; error?: string }) => (j.status === "waiting" && j.error === "not_enough_items" ? "Waiting for items" : STATUS[j.status]);

/** A line under a job: why it is stuck, or the reason it failed. Waiting needs none, its status says it. */
export function jobReason(j: { status: JobStatus; error?: string }): string | null {
  if (j.status === "stuck") return REASON[j.error ?? ""] ?? REASON.no_progress;
  return j.status === "failed" && j.error ? (REASON[j.error] ?? j.error) : null;
}

export const jobCancellable = (j: { status: JobStatus }) => j.status === "running" || j.status === "queued" || j.status === "stuck" || j.status === "waiting";

export function parseMissing(v: unknown): MissingItem[] {
  if (!Array.isArray(v)) return [];
  return v.slice(0, 16).flatMap((r) => {
    const o = r as Record<string, unknown>;
    if (typeof o?.name !== "string") return [];
    return [{ name: o.name, displayName: typeof o.displayName === "string" ? o.displayName : o.name, have: typeof o.have === "number" ? o.have : 0, need: typeof o.need === "number" ? o.need : 0 }];
  });
}

/** The missing entries of a craft plan as the same rows. */
export const planMissing = (entries: { name: string; displayName: string; stored: number; missing: number }[]): MissingItem[] =>
  entries.filter((e) => e.missing > 0).map((e) => ({ name: e.name, displayName: e.displayName, have: e.stored, need: e.stored + e.missing }));
