// Where the tour card goes: next to what the step highlights, never on top of it or of anything else the step needs.
// Pure geometry (viewport pixels), so it is tested without a browser.

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** Beside the target, or docked to the top or bottom edge of the free area. */
export type Side = "above" | "below" | "right" | "left" | "top" | "bottom";

export interface PlaceInput {
  card: { w: number; h: number };
  /** where the card may go: below the sticky header, above a bottom tab bar, inside the side margins */
  view: Rect;
  /** the highlighted element; null keeps the card docked */
  target: Rect | null;
  /** more areas the step needs (a legend, an open panel) */
  avoid?: Rect[];
  /** the order to try the sides next to the target */
  sides?: Side[];
  /** the side of the last placement: it stays while it still works, so the card does not jump around */
  last?: Side;
  /** the page can scroll the target (false inside a sheet or a sticky header) */
  scrollable?: boolean;
  /** space between the card and the target */
  gap?: number;
  /** pixels below the target that should stay in view and free too, when the screen has room for them */
  room?: number;
}

export interface Placement {
  x: number;
  y: number;
  side: Side;
  /** scroll the page by this much (window.scrollBy) to make the placement true; 0 when it already is */
  scroll: number;
  /** false only when nothing fits: then the card covers as little as possible */
  clear: boolean;
}

export const hit = (a: Rect, b: Rect) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
const grow = (r: Rect, d: number): Rect => ({ x: r.x - d, y: r.y - d, w: r.w + 2 * d, h: r.h + 2 * d });
const down = (r: Rect, dy: number): Rect => ({ ...r, y: r.y + dy });
const overlap = (a: Rect, b: Rect) => Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x)) * Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
/** lo wins when the range is empty: the card keeps to the left and top */
const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(v, hi));

const SIDES: Side[] = ["above", "below", "right", "left"];

export function placeCard(o: PlaceInput): Placement {
  const { w, h } = o.card;
  const v = o.view;
  const gap = o.gap ?? 12;
  const right = v.x + v.w - w;
  const bottom = v.y + v.h - h;
  const t0 = o.target;
  if (!t0) {
    // nothing highlighted: a corner, the lower left one unless the step needs it
    const corners: [number, number][] = [
      [v.x, bottom],
      [right, bottom],
      [right, v.y],
      [v.x, v.y],
    ];
    const clearOf = ([x, y]: [number, number]) => !(o.avoid ?? []).some((a) => hit({ x, y, w, h }, a));
    const free = corners.find(clearOf);
    const [x, y] = free ?? corners[0];
    return { x, y: Math.max(v.y, y), side: y === v.y ? "top" : "bottom", scroll: 0, clear: !!free };
  }

  /** the card's rectangle on one side of the target `t` */
  const at = (side: Side, t: Rect): Rect => {
    // a small target gets the card centred on it, a wide one starts at its left edge
    const x = clamp(t.w >= w ? t.x : t.x + t.w / 2 - w / 2, v.x, right);
    const y = clamp(t.y + t.h / 2 - h / 2, v.y, bottom);
    // docked: to the side away from the target, so a phone's full-width card and a desktop card both keep clear
    const away = t.x + t.w / 2 < v.x + v.w / 2 ? right : v.x;
    switch (side) {
      case "below":
        return { x, y: t.y + t.h + gap, w, h };
      case "above":
        return { x, y: t.y - gap - h, w, h };
      case "right":
        return { x: t.x + t.w + gap, y, w, h };
      case "left":
        return { x: t.x - gap - w, y, w, h };
      case "top":
        return { x: away, y: v.y, w, h };
      case "bottom":
        return { x: away, y: Math.max(v.y, bottom), w, h };
    }
  };
  const inside = (r: Rect) => r.x >= v.x - 0.5 && r.x + r.w <= v.x + v.w + 0.5 && r.y >= v.y - 0.5 && r.y + r.h <= v.y + v.h + 0.5;
  const anchored = o.sides ?? SIDES;
  // the edge away from the target first
  const docks: Side[] = t0.y + t0.h / 2 < v.y + v.h / 2 ? ["bottom", "top"] : ["top", "bottom"];

  /** a placement that keeps `t` (the target, perhaps with room below it) in view and free */
  const attempt = (t: Rect): Placement | null => {
    /** what the card must not touch: the target with its gap and every other area, after scrolling by dy */
    const blocked = (dy: number) => [grow(down(t, dy), gap - 2), ...(o.avoid ?? []).map((a) => down(a, dy))];
    const fits = (r: Rect, dy: number) => inside(r) && !blocked(dy).some((b) => hit(r, b));
    const shown = t.y >= v.y && t.y + t.h <= v.y + v.h;
    const asIs = (sides: Side[]): Placement | null => {
      if (!shown && o.scrollable) return null;
      for (const side of sides) {
        const r = at(side, t);
        if (fits(r, 0)) return { x: r.x, y: r.y, side, scroll: 0, clear: true };
      }
      return null;
    };
    /** scrolls the target as little as needed to make room for the card on one of these sides */
    const scrolled = (sides: Side[]): Placement | null => {
      if (!o.scrollable) return null;
      for (const side of sides) {
        // the range of target tops that leave room for the card on this side
        let lo = v.y;
        let hi = v.y + v.h - t.h;
        if (side === "above" || side === "top") lo = v.y + h + gap;
        if (side === "below" || side === "bottom") hi = v.y + v.h - h - gap - t.h;
        if (lo > hi) continue;
        const dy = clamp(t.y, lo, hi) - t.y;
        const r = at(side, down(t, dy));
        if (fits(r, dy)) return { x: r.x, y: r.y, side, scroll: Math.round(-dy), clear: true };
      }
      return null;
    };
    // beside the target (the last side first, so the card stays put), then beside it after scrolling, then docked
    // to an edge, as the page is or with the target scrolled into the rest of the screen
    return asIs([...new Set([...(o.last ? [o.last] : []), ...anchored])]) ?? scrolled(anchored) ?? asIs(docks) ?? scrolled(docks);
  };
  // the room below the target (where results show up) when the screen has it, half of it, else the target alone
  for (const room of o.room ? [o.room, Math.round(o.room / 2), 0] : [0]) {
    const found = attempt({ ...t0, h: t0.h + room });
    if (found) return found;
  }

  // nothing fits: the edge that covers the least of what the step needs
  let best: Placement | null = null;
  let least = Infinity;
  for (const side of docks) {
    const r = at(side, t0);
    const covered = [grow(t0, gap - 2), ...(o.avoid ?? [])].reduce((s, b) => s + overlap(r, b), 0);
    if (covered < least) {
      least = covered;
      best = { x: r.x, y: r.y, side, scroll: 0, clear: covered === 0 };
    }
  }
  return best!;
}
