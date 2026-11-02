// The canvas widget: a surface a Lua script draws on (docs/SPEC.md "Canvas widget"). Frames arrive as a widget value and
// are checked and drawn by lib/canvas.ts; pointer input goes back as an ordinary action, gated like a button.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { backingScale, canvasPoint, canvasSize, parseFrame, renderFrame, themePalette, type CanvasPaint } from "../lib/canvas.ts";
import { iconUrl } from "../lib/icons.ts";
import { ConfirmDialog } from "./Overlays.tsx";
import { pixelIconMask } from "./PixelIcon.tsx";
import { WIcon, usePinGate, type WidgetProps } from "./Widgets.tsx";

/** Item images, shared by all canvases. Bounded: a script naming endless items only gets plain tiles. */
const MAX_IMAGES = 256;
const images = new Map<string, HTMLImageElement | null>();
const waiting = new Set<() => void>();

function itemImage(name: string): HTMLImageElement | null {
  const known = images.get(name);
  if (known !== undefined) return known && known.complete && known.naturalWidth > 0 ? known : null;
  const url = iconUrl(name);
  if (!url || images.size >= MAX_IMAGES) return null;
  const image = new Image();
  images.set(name, image);
  image.onload = () => waiting.forEach((redraw) => redraw());
  image.onerror = () => images.set(name, null);
  image.src = url;
  return null;
}

/** Sends at most one drag position per this many ms: dragging shares the session's limit of 10 actions per second. */
const DRAG_MS = 150;

export function CanvasWidget({ w, uplink, conn, locked }: WidgetProps) {
  const size = canvasSize(w.opts);
  const host = useRef<HTMLDivElement>(null);
  const surface = useRef<HTMLCanvasElement>(null);
  const [fit, setFit] = useState({ width: 0, height: 0 });
  const [confirming, setConfirming] = useState<{ x: number; y: number } | null>(null);
  const frame = useMemo(() => parseFrame(w.value), [w.value]);
  const input = w.opts?.input === "click" || w.opts?.input === "drag" ? w.opts.input : undefined;
  const interactive = !!input && !locked;
  const gate = usePinGate(conn, uplink, w, false);
  const drag = useRef<{ last: number; pending: { x: number; y: number } | null; timer?: ReturnType<typeof setTimeout> } | null>(null);

  // as large as the widget's box allows, keeping the surface's proportions
  useEffect(() => {
    const el = host.current;
    if (!el) return;
    const measure = () => {
      const scale = Math.min(el.clientWidth / size.w, el.clientHeight / size.h);
      if (scale > 0) setFit({ width: Math.floor(size.w * scale), height: Math.floor(size.h * scale) });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [size.w, size.h]);

  useEffect(() => {
    const canvas = surface.current;
    if (!canvas || !fit.width) return;
    let scheduled = 0;
    const paint = () => {
      scheduled = 0;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      const k = backingScale(fit.width, size.w, size.h, window.devicePixelRatio || 1);
      if (canvas.width !== size.w * k || canvas.height !== size.h * k) {
        canvas.width = size.w * k;
        canvas.height = size.h * k;
      }
      ctx.setTransform(k, 0, 0, k, 0, 0);
      ctx.imageSmoothingEnabled = false;
      const css = getComputedStyle(canvas);
      const palette = themePalette((name) => css.getPropertyValue(name));
      ctx.clearRect(0, 0, size.w, size.h);
      const bg = w.opts?.bg;
      const commands = typeof bg === "string" ? [{ op: "clear" as const, color: bg }, ...frame] : frame;
      renderFrame(ctx as unknown as CanvasPaint, commands, size.w, size.h, { palette, iconMask: pixelIconMask, itemImage, font: css.fontFamily || "monospace" });
    };
    const redraw = () => {
      if (!scheduled) scheduled = requestAnimationFrame(paint);
    };
    redraw();
    waiting.add(redraw);
    // the theme's colours are read while painting: a theme change paints again
    const theme = new MutationObserver(redraw);
    theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    return () => {
      if (scheduled) cancelAnimationFrame(scheduled);
      waiting.delete(redraw);
      theme.disconnect();
    };
  }, [frame, fit.width, size.w, size.h, w.opts?.bg]);

  const point = (e: PointerEvent | MouseEvent) => {
    const box = (e.currentTarget as HTMLElement).getBoundingClientRect();
    return canvasPoint(e.clientX - box.left, e.clientY - box.top, box, size.w, size.h);
  };
  /** A drag event that does not fit the session's limit is simply lost: the next position follows right away. */
  const sendDrag = (kind: "down" | "drag" | "up", p: { x: number; y: number }) => {
    conn.action(uplink.uplinkId, w, { op: "canvas", kind, ...p }, false, { onResult: (r) => r.error === "rate_limited" });
  };
  const flushDrag = () => {
    const state = drag.current;
    if (!state?.pending) return;
    sendDrag("drag", state.pending);
    state.pending = null;
    state.last = Date.now();
  };
  const dragHandlers =
    interactive && input === "drag"
      ? {
          onPointerDown: (e: PointerEvent) => {
            if (e.button !== 0) return;
            (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
            drag.current = { last: Date.now(), pending: null };
            sendDrag("down", point(e));
          },
          onPointerMove: (e: PointerEvent) => {
            const state = drag.current;
            if (!state) return;
            state.pending = point(e);
            const wait = state.last + DRAG_MS - Date.now();
            if (wait <= 0) flushDrag();
            else if (state.timer === undefined) {
              state.timer = setTimeout(() => {
                state.timer = undefined;
                flushDrag();
              }, wait);
            }
          },
          onPointerUp: (e: PointerEvent) => {
            const state = drag.current;
            if (!state) return;
            clearTimeout(state.timer);
            drag.current = null;
            sendDrag("up", point(e));
          },
          onPointerCancel: (e: PointerEvent) => {
            const state = drag.current;
            if (!state) return;
            clearTimeout(state.timer);
            drag.current = null;
            sendDrag("up", point(e));
          },
        }
      : {};
  const click = (p: { x: number; y: number }) => gate.send({ op: "canvas", kind: "click", ...p });
  const clickHandlers =
    interactive && input === "click"
      ? {
          onClick: (e: MouseEvent) => {
            const p = point(e);
            if (w.opts?.confirm && !gate.needsPin) setConfirming(p);
            else click(p);
          },
        }
      : {};

  return (
    <div class="canvas-widget">
      {(w.label || w.opts?.wicon) && (
        <div class="w2-head">
          <span class="w2-label">
            <WIcon w={w} />
            {w.label}
          </span>
        </div>
      )}
      <div class="canvas-host" ref={host}>
        <canvas
          ref={surface}
          class={`canvas-surface ${interactive ? (input === "drag" ? "is-drag" : "is-click") : ""}`}
          style={fit.width ? { width: `${fit.width}px`, height: `${fit.height}px` } : undefined}
          role="img"
          aria-label={w.label ?? w.id}
          {...dragHandlers}
          {...clickHandlers}
        />
        {!frame.length && !w.opts?.bg && <span class="canvas-empty muted small">Waiting for the program</span>}
      </div>
      {gate.dialog}
      {confirming && (
        <ConfirmDialog
          title={w.label ?? w.id}
          text={`Send this click to ${uplink.title || "the program"}?`}
          confirmLabel={w.label ?? "Confirm"}
          color={w.opts?.color ?? "gold"}
          onCancel={() => setConfirming(null)}
          onConfirm={() => {
            click(confirming);
            setConfirming(null);
          }}
        />
      )}
    </div>
  );
}
