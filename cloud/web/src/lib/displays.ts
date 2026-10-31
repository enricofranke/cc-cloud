// Cloud Displays on the dashboard (screen #/m/<id>/displays): the list row, what a display can show and the
// preview cards. The preview uses the section data the dashboard already has; nothing is sent for it.
import { resolveLayout, type LayoutDoc, type ResolvedCard, type ResolvedPage } from "./layout.ts";
import type { Uplink } from "./types.ts";

export type DisplayStatus = "ok" | "no_section" | "no_rights" | "missing" | "too_large" | "restricted" | "offline" | "unloaded";
const STATUSES: readonly string[] = ["ok", "no_section", "no_rights", "missing", "too_large", "restricted", "offline", "unloaded"];

export interface DisplayRow {
  id: string;
  w: number;
  h: number;
  status: DisplayStatus;
  /** "" nothing, a section id, c:<card> or p:<page> */
  target: string;
  interactive: boolean;
  allowRestricted: boolean;
  /** only with view.positions */
  dim?: string;
  x?: number;
  y?: number;
  z?: number;
}
export interface DisplayChange {
  target?: string;
  interactive?: boolean;
  allowRestricted?: boolean;
}

/** Sanitises the server's list. */
export function normalizeDisplays(raw: unknown): DisplayRow[] {
  if (!Array.isArray(raw)) return [];
  const out: DisplayRow[] = [];
  for (const r of raw as Record<string, any>[]) {
    if (!r || typeof r.id !== "string") continue;
    const size = (v: unknown) => (typeof v === "number" && v >= 1 ? Math.round(v) : 1);
    out.push({
      id: r.id,
      w: size(r.w),
      h: size(r.h),
      status: STATUSES.includes(r.status) ? r.status : "missing",
      target: typeof r.target === "string" ? r.target : "",
      interactive: r.interactive === true,
      allowRestricted: r.allowRestricted === true,
      ...(typeof r.dim === "string" ? { dim: r.dim } : {}),
      ...(typeof r.x === "number" && typeof r.y === "number" && typeof r.z === "number" ? { x: r.x, y: r.y, z: r.z } : {}),
    });
  }
  return out;
}

export interface TargetOption {
  value: string;
  title: string;
  kind: "section" | "card" | "page";
  restricted: boolean;
}

/**
 * What a display can show: original sections, custom cards and pages. Restricted sections are offered only once the
 * display allows them; the session only knows them with view.positions anyway.
 */
export function targetOptions(uplinks: Uplink[], layout: LayoutDoc | null, allowRestricted: boolean): TargetOption[] {
  const out: TargetOption[] = uplinks.filter((u) => !u.restricted || allowRestricted).map((u) => ({ value: u.uplinkId, title: u.title, kind: "section", restricted: !!u.restricted }));
  for (const p of resolveLayout(uplinks, layout, { includeHidden: true })) {
    for (const c of p.cards) if (c.custom) out.push({ value: c.key, title: c.title, kind: "card", restricted: c.widgets.some((w) => w.uplink.restricted) });
    out.push({ value: `p:${p.id}`, title: p.title, kind: "page", restricted: p.cards.some((c) => c.widgets.some((w) => w.uplink.restricted)) });
  }
  return out;
}

/** Without the display's permission, restricted widgets stay off a composed card (as DisplayCompose does). */
function allowed(card: ResolvedCard, allowRestricted: boolean): ResolvedCard {
  return allowRestricted ? card : { ...card, widgets: card.widgets.filter((w) => !w.uplink.restricted) };
}

/**
 * The cards of a preview. A section shows as published: every widget with its own label, regardless of the layout,
 * like the display does. A custom card or page goes through the layout. Null: nothing the dashboard could show.
 */
export function previewCards(target: string, uplinks: Uplink[], pages: ResolvedPage[], allowRestricted: boolean): ResolvedCard[] | null {
  if (!target) return null;
  if (target.startsWith("c:")) {
    for (const p of pages) for (const c of p.cards) if (c.key === target) return [allowed(c, allowRestricted)];
    return null;
  }
  if (target.startsWith("p:")) {
    const page = pages.find((p) => p.id === target.slice(2));
    return page ? page.cards.filter((c) => !c.hidden).map((c) => allowed(c, allowRestricted)).filter((c) => c.widgets.length > 0) : null;
  }
  const u = uplinks.find((x) => x.uplinkId === target);
  if (!u || (u.restricted && !allowRestricted)) return null;
  const cards = resolveLayout([u], null).flatMap((p) => p.cards);
  if (!cards.length) return null;
  return [{ ...cards[0], title: u.title, widgets: cards.flatMap((c) => c.widgets) }];
}

/**
 * The frame of a preview: the display's aspect ratio inside a 340×240 box, with the card laid out on a canvas at
 * least 320 px wide (the card's smallest readable width) and scaled down to the frame.
 */
export function previewFrame(w: number, h: number) {
  const ratio = w / h;
  const frameW = Math.min(340, 240 * ratio);
  const frameH = frameW / ratio;
  const canvasW = Math.max(320, w * 160);
  const scale = Math.min(1, frameW / canvasW);
  return { frameW: Math.round(frameW), frameH: Math.round(frameH), canvasW, canvasH: Math.round(frameH / scale), scale };
}
