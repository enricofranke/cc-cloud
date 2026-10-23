// Motion for the layout editor: FLIP reflow and spring physics, transform-only
// (no layout properties are animated, so dragging stays at 60 fps).

export const reducedMotion = () => typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches;

/** One step of a damped spring (semi-implicit Euler). Exported for tests. */
export function springStep(s: { x: number; v: number }, target: number, dt: number, k = 380, c = 30) {
  const a = -k * (s.x - target) - c * s.v;
  const v = s.v + a * dt;
  return { x: s.x + v * dt, v };
}

/** Simulates a spring until it settles; returns the positions per frame (for tests and the drop animation). */
export function springPath(from: number, to: number, fps = 60, k = 380, c = 30, maxFrames = 120): number[] {
  const out: number[] = [];
  let s = { x: from, v: 0 };
  for (let i = 0; i < maxFrames; i++) {
    s = springStep(s, to, 1 / fps, k, c);
    out.push(s.x);
    if (Math.abs(s.x - to) < 0.3 && Math.abs(s.v) < 4) break;
  }
  out[out.length - 1] = to;
  return out;
}

/**
 * Animates an element's transform with springs for x, y and scale. Resolves
 * when it settles. `apply` writes the transform (so the caller can add extras).
 */
export function springTo(
  el: HTMLElement,
  from: { x: number; y: number; s: number },
  to: { x: number; y: number; s: number },
  apply = (t: { x: number; y: number; s: number }) => (el.style.transform = `translate3d(${t.x}px, ${t.y}px, 0) scale(${t.s})`),
): Promise<void> {
  if (reducedMotion()) {
    apply(to);
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    let sx = { x: from.x, v: 0 };
    let sy = { x: from.y, v: 0 };
    let ss = { x: from.s, v: 0 };
    let last = performance.now();
    const frame = (now: number) => {
      const dt = Math.min(1 / 30, (now - last) / 1000);
      last = now;
      sx = springStep(sx, to.x, dt);
      sy = springStep(sy, to.y, dt);
      ss = springStep(ss, to.s, dt, 500, 34);
      apply({ x: sx.x, y: sy.x, s: ss.x });
      const done = Math.abs(sx.x - to.x) < 0.4 && Math.abs(sy.x - to.y) < 0.4 && Math.abs(sx.v) + Math.abs(sy.v) < 8 && Math.abs(ss.x - to.s) < 0.002;
      if (done) {
        apply(to);
        resolve();
      } else requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
  });
}

/**
 * FLIP: remember where keyed elements are, and after the DOM changed, make each
 * moved element start at its old place and glide to the new one.
 */
export class Flip {
  private rects = new Map<string, DOMRect>();
  private root: () => HTMLElement | null;
  private selector: string;
  constructor(root: () => HTMLElement | null, selector = "[data-card],[data-w]") {
    this.root = root;
    this.selector = selector;
  }
  private keyOf(el: Element) {
    return el.getAttribute("data-w") ?? `card:${el.getAttribute("data-card")}`;
  }
  /** Read phase: measure everything (call before the DOM changes). */
  snapshot() {
    const r = this.root();
    this.rects.clear();
    if (!r) return;
    for (const el of r.querySelectorAll(this.selector)) this.rects.set(this.keyOf(el), el.getBoundingClientRect());
  }
  /** After the DOM changed: invert + play. Reads all rects first, then writes (no layout thrash). */
  play(duration = 240) {
    const r = this.root();
    if (!r || reducedMotion()) return;
    const els = [...r.querySelectorAll<HTMLElement>(this.selector)];
    const moves: [HTMLElement, number, number][] = [];
    for (const el of els) {
      const before = this.rects.get(this.keyOf(el));
      if (!before) continue;
      const now = el.getBoundingClientRect();
      const dx = before.left - now.left;
      const dy = before.top - now.top;
      if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) moves.push([el, dx, dy]);
    }
    for (const [el, dx, dy] of moves) {
      el.style.transition = "none";
      el.style.transform = `translate(${dx}px, ${dy}px)`;
    }
    if (!moves.length) return;
    // one forced style flush for all of them
    void r.offsetWidth;
    for (const [el] of moves) {
      el.style.transition = `transform ${duration}ms cubic-bezier(0.2, 0.9, 0.25, 1)`;
      el.style.transform = "";
      const clear = () => {
        el.style.transition = "";
        el.removeEventListener("transitionend", clear);
      };
      el.addEventListener("transitionend", clear);
    }
  }
}
