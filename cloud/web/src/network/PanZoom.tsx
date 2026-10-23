// Pan/zoom container for SVG content in world coordinates.
// Mouse: drag to pan, wheel to zoom. Touch: one finger pans, two fingers pinch.
import { useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";

export interface Bounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}
export interface View {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** View that fits `b` into an element of size (ew × eh), keeping the aspect ratio. */
export function fitView(b: Bounds, ew: number, eh: number): View {
  const bw = Math.max(1, b.maxX - b.minX);
  const bh = Math.max(1, b.maxY - b.minY);
  const aspect = ew / Math.max(1, eh);
  let w = bw;
  let h = bw / aspect;
  if (h < bh) {
    h = bh;
    w = bh * aspect;
  }
  return { x: b.minX - (w - bw) / 2, y: b.minY - (h - bh) / 2, w, h };
}

/** Zooms the view by `factor` (< 1 = in) around the world point (px, py). */
export function zoomAt(v: View, factor: number, px: number, py: number, minW = 20, maxW = 20000): View {
  const w = Math.min(maxW, Math.max(minW, v.w * factor));
  const f = w / v.w;
  const h = v.h * f;
  return { x: px - (px - v.x) * f, y: py - (py - v.y) * f, w, h };
}

export function PanZoom(props: {
  bounds: Bounds;
  /** change to re-fit (e.g. switching dimension) */
  fitKey: string;
  minW?: number;
  maxW?: number;
  class?: string;
  label: string;
  children: (view: View, unitsPerPx: number) => ComponentChildren;
  /** suppresses clicks after a drag */
  onBackgroundClick?: () => void;
}) {
  const wrap = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 600, h: 400 });
  const [view, setView] = useState<View>(() => fitView(props.bounds, 600, 400));
  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const moved = useRef(0);
  const pinch = useRef<{ dist: number; view: View; mid: { x: number; y: number } } | null>(null);

  useLayoutEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const measure = () => {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) setSize({ w: r.width, h: r.height });
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // auto-fit on first layout, on size changes before any interaction, and when fitKey changes
  const interacted = useRef(false);
  useEffect(() => {
    interacted.current = false;
  }, [props.fitKey]);
  useEffect(() => {
    if (!interacted.current) setView(fitView(props.bounds, size.w, size.h));
    else setView((v) => ({ ...v, h: (v.w * size.h) / Math.max(1, size.w) }));
  }, [props.fitKey, size.w, size.h, props.bounds.minX, props.bounds.maxX, props.bounds.minY, props.bounds.maxY]);

  const upp = view.w / Math.max(1, size.w);
  const toWorld = (clientX: number, clientY: number) => {
    const r = wrap.current!.getBoundingClientRect();
    return { x: view.x + ((clientX - r.left) / r.width) * view.w, y: view.y + ((clientY - r.top) / r.height) * view.h };
  };

  const onDown = (e: PointerEvent) => {
    // no pointer capture yet: a plain tap must still reach the node under the finger
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    moved.current = 0;
    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()];
      pinch.current = { dist: Math.hypot(a.x - b.x, a.y - b.y), view, mid: toWorld((a.x + b.x) / 2, (a.y + b.y) / 2) };
    }
  };
  const onMove = (e: PointerEvent) => {
    const prev = pointers.current.get(e.pointerId);
    if (!prev) return;
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.current.size === 2 && pinch.current) {
      const [a, b] = [...pointers.current.values()];
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      const p = pinch.current;
      interacted.current = true;
      setView(zoomAt(p.view, p.dist / Math.max(1, d), p.mid.x, p.mid.y, props.minW, props.maxW));
      moved.current += 10;
      return;
    }
    const dx = e.clientX - prev.x;
    const dy = e.clientY - prev.y;
    moved.current += Math.abs(dx) + Math.abs(dy);
    if (moved.current < 4) return;
    try {
      if (!wrap.current?.hasPointerCapture(e.pointerId)) wrap.current?.setPointerCapture(e.pointerId);
    } catch {}
    interacted.current = true;
    setView((v) => ({ ...v, x: v.x - dx * (v.w / size.w), y: v.y - dy * (v.h / size.h) }));
  };
  const onUp = (e: PointerEvent) => {
    pointers.current.delete(e.pointerId);
    if (pointers.current.size < 2) pinch.current = null;
  };
  const onWheel = (e: WheelEvent) => {
    e.preventDefault();
    interacted.current = true;
    const p = toWorld(e.clientX, e.clientY);
    setView((v) => zoomAt(v, Math.pow(1.0015, e.deltaY), p.x, p.y, props.minW, props.maxW));
  };
  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  });

  const zoomBtn = (f: number) => {
    interacted.current = true;
    setView((v) => zoomAt(v, f, v.x + v.w / 2, v.y + v.h / 2, props.minW, props.maxW));
  };

  return (
    <div
      ref={wrap}
      class={`panzoom ${props.class ?? ""}`}
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={onUp}
      onPointerCancel={onUp}
      onClickCapture={(e) => {
        // a drag is not a click
        if (moved.current >= 6) {
          e.stopPropagation();
          e.preventDefault();
        }
      }}
    >
      <svg
        viewBox={`${view.x} ${view.y} ${view.w} ${view.h}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={props.label}
        onClick={(e) => {
          if (e.target === e.currentTarget && moved.current < 6) props.onBackgroundClick?.();
        }}
      >
        {props.children(view, upp)}
      </svg>
      <div class="pz-controls">
        <button type="button" class="pz-btn" aria-label="Zoom in" onClick={() => zoomBtn(0.7)}>
          +
        </button>
        <button type="button" class="pz-btn" aria-label="Zoom out" onClick={() => zoomBtn(1 / 0.7)}>
          −
        </button>
        <button
          type="button"
          class="pz-btn pz-fit"
          aria-label="Fit to screen"
          onClick={() => {
            interacted.current = false;
            setView(fitView(props.bounds, size.w, size.h));
          }}
        >
          ⤢
        </button>
      </div>
    </div>
  );
}
