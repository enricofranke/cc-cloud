// WYSIWYG layout editor (lazy chunk): edit directly on the live cards.
// Cards sit on a 12-column page grid, widgets on a 12-column grid inside their
// card. Pointer drag (mouse, pen, touch long-press), resize from edges and the
// corner, keyboard drag & drop, multi-select, undo/redo, Add gallery and a
// settings panel. Motion is transform-only: the dragged item is a fixed ghost
// that follows the pointer, the rest reflows with FLIP, drops settle with a spring.
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import {
  COLS,
  MAX_LAYOUT_BYTES,
  MIN_CARD_W,
  minWidgetW,
  addPage,
  findWidget,
  layoutSize,
  moveCard,
  moveCardTo,
  moveWidgetsTo,
  newCardWithWidgets,
  resizeCard,
  resizeWidget,
  resolveLayout,
  snapshotLayout,
  updateCard,
  updateWidget,
  type Box,
  type ResolvedCard,
  type ResolvedPage,
  type ResolvedWidget,
} from "../lib/layout.ts";
import { bottom } from "../lib/grid.ts";
import { pushToast } from "../lib/ui.ts";
import { Card, placeVars, type CardEdit, type Edge } from "../components/Card.tsx";
import { Tabs } from "../components/PageTabs.tsx";
import { ConfirmDialog, Sheet } from "../components/Overlays.tsx";
import { canRedo, canUndo, createHistory, current, isDirty, push, redo, undo, type History } from "./history.ts";
import { Flip, springTo } from "./motion.ts";
import { PICKUP_HELP, describe, kbdStep, type Kind } from "./keyboard.ts";
import { AddGallery, CardSettings, ShortcutHelp, WidgetSettings } from "./Panels.tsx";

const SAVE_ERRORS: Record<string, string> = {
  read_only: "This device is read-only and cannot change the layout.",
  not_allowed: "Only admins can change the layout.",
  too_large: "The layout is too large (max 64 KB).",
  invalid_layout: "The server rejected the layout as invalid.",
  rate_limited: "Saving too often – try again in a moment.",
  disconnected: "Not connected – your changes are kept, try again when the connection is back.",
  timeout: "The server did not answer. Try again.",
};

/** Resolved grid tracks of a CSS grid element (exact, also when rows grew with their content). */
function tracks(el: HTMLElement) {
  const cs = getComputedStyle(el);
  const cols = cs.gridTemplateColumns.split(" ").map(parseFloat).filter((n) => !Number.isNaN(n));
  const rows = cs.gridTemplateRows.split(" ").map(parseFloat).filter((n) => !Number.isNaN(n));
  return { cols, rows, colGap: parseFloat(cs.columnGap) || 0, rowGap: parseFloat(cs.rowGap) || 0, rect: el.getBoundingClientRect(), baseRow: parseFloat(cs.gridAutoRows) || 24 };
}
/** Cell index under a coordinate along tracks (extrapolates past the last track). */
function trackIndex(pos: number, sizes: number[], gap: number, base: number) {
  let edge = 0;
  for (let i = 0; i < sizes.length; i++) {
    if (pos < edge + sizes[i] + gap / 2) return i;
    edge += sizes[i] + gap;
  }
  return sizes.length + Math.max(0, Math.floor((pos - edge) / (base + gap)));
}
function cellIn(el: HTMLElement, px: number, py: number) {
  const t = tracks(el);
  return { x: Math.min(t.cols.length - 1, trackIndex(px - t.rect.left, t.cols, t.colGap, 1)), y: trackIndex(py - t.rect.top, t.rows, t.rowGap, t.baseRow), t };
}

interface Drag {
  kind: Kind;
  keys: string[];
  source: "grid" | "gallery";
  pointerId: number;
  touch: boolean;
  startX: number;
  startY: number;
  x: number;
  y: number;
  lastX: number;
  active: boolean;
  timer?: number;
  el: HTMLElement;
  rect: DOMRect;
  /** where in the item the pointer grabbed it (grid cells) */
  grabCol: number;
  grabRow: number;
  ghost?: HTMLElement;
  scale: { x: number; v: number };
  tilt: number;
  base: ResolvedPage[];
  page: string;
  targetKey: string;
  result: ResolvedPage[] | null;
  tab: string | null;
  tabSince: number;
  raf: number;
}

const vibrate = (ms: number) => {
  try {
    navigator.vibrate?.(ms);
  } catch {}
};

export default function Editor(props: { conn: MainframeConn; uplinks: Uplink[]; page: string; onPage: (id: string) => void; isPhone: boolean; narrow: boolean; network: boolean; onExit: () => void }) {
  const { conn } = props;
  const [hist, setHist] = useState<History<ResolvedPage[]>>(() => createHistory(resolveLayout(props.uplinks, conn.layout.value, { includeHidden: true })));
  const [preview, setPreview] = useState<ResolvedPage[] | null>(null);
  const pages = preview ?? current(hist);
  const page = pages.find((p) => p.id === props.page) ?? pages[0];
  const [sel, setSel] = useState<Set<string>>(new Set());
  const [selCard, setSelCard] = useState<string | null>(null);
  const [panel, setPanel] = useState<"settings" | "add" | "help" | null>(null);
  const [dragging, setDragging] = useState<Set<string>>(new Set());
  const [badge, setBadge] = useState<{ key: string; text: string; warn?: boolean } | null>(null);
  const [tabHover, setTabHover] = useState<string | null>(null);
  const [dropZone, setDropZone] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ title: string; text: string; label: string; color: string; ok: () => void } | null>(null);
  const [announce, setAnnounce] = useState("");
  const [kbd, setKbd] = useState<{ kind: Kind; key: string; base: ResolvedPage[] } | null>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const flip = useMemo(() => new Flip(() => gridRef.current), []);
  const flipPending = useRef(false);
  const drag = useRef<Drag | null>(null);
  const justDragged = useRef(0);
  const focusAfter = useRef<{ kind: Kind; key: string } | null>(null);
  const dirty = isDirty(hist);
  const histRef = useRef(hist);
  histRef.current = hist;

  /** Changes the preview with a FLIP reflow of everything else. */
  const showPreview = (next: ResolvedPage[] | null) => {
    flip.snapshot();
    flipPending.current = true;
    setPreview(next);
  };
  const commit = (next: ResolvedPage[], key?: string, animate = true) => {
    if (animate) {
      flip.snapshot();
      flipPending.current = true;
    }
    setPreview(null);
    setHist((h) => push(h, next, key));
  };
  useLayoutEffect(() => {
    if (flipPending.current) {
      flipPending.current = false;
      flip.play();
    }
    if (focusAfter.current) {
      const f = focusAfter.current;
      const el = document.querySelector<HTMLElement>(f.kind === "widget" ? `[data-w="${CSS.escape(f.key)}"]` : `[data-card="${CSS.escape(f.key)}"]`);
      if (el && document.activeElement !== el) el.focus({ preventScroll: false });
      if (!kbd) focusAfter.current = null;
    }
  });

  // ------------------------------------------------------------ pointer drag
  const begin = (e: PointerEvent, kind: Kind, keys: string[], el: HTMLElement, source: "grid" | "gallery", gallery?: ResolvedPage[]) => {
    if (e.button !== 0 || drag.current || kbd) return;
    const rect = el.getBoundingClientRect();
    const grid = kind === "widget" ? (el.closest("[data-wgrid]") as HTMLElement | null) : gridRef.current;
    let grabCol = 0;
    let grabRow = 0;
    if (grid && source === "grid") {
      const c = cellIn(grid, e.clientX, e.clientY);
      const t = tracks(grid);
      const at = cellIn(grid, rect.left + 2, rect.top + 2);
      grabCol = Math.max(0, c.x - at.x);
      grabRow = Math.max(0, c.y - at.y);
      void t;
    }
    const d: Drag = {
      kind,
      keys,
      source,
      pointerId: e.pointerId,
      touch: e.pointerType === "touch",
      startX: e.clientX,
      startY: e.clientY,
      x: e.clientX,
      y: e.clientY,
      lastX: e.clientX,
      active: false,
      el,
      rect,
      grabCol,
      grabRow,
      scale: { x: 1, v: 0 },
      tilt: 0,
      base: gallery ?? current(histRef.current),
      page: page.id,
      targetKey: "",
      result: null,
      tab: null,
      tabSince: 0,
      raf: 0,
    };
    drag.current = d;
    if (d.touch) d.timer = window.setTimeout(() => activate(d), 320);
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onCancel);
  };
  const blockTouch = (e: TouchEvent) => {
    if (drag.current?.active) e.preventDefault();
  };
  const activate = (d: Drag) => {
    if (d.active || drag.current !== d) return;
    d.active = true;
    vibrate(12);
    const ghost = d.el.cloneNode(true) as HTMLElement;
    ghost.removeAttribute("data-card");
    ghost.removeAttribute("data-w");
    ghost.removeAttribute("tabindex");
    ghost.setAttribute("aria-hidden", "true");
    ghost.classList.add("drag-ghost");
    ghost.classList.remove("is-selected");
    ghost.style.width = `${d.rect.width}px`;
    ghost.style.height = `${d.rect.height}px`;
    ghost.style.transform = `translate3d(${d.rect.left}px, ${d.rect.top}px, 0)`;
    for (const n of ghost.querySelectorAll("[data-card],[data-w],[data-wgrid]")) {
      n.removeAttribute("data-card");
      n.removeAttribute("data-w");
      n.removeAttribute("data-wgrid");
    }
    document.body.appendChild(ghost);
    d.ghost = ghost;
    document.body.classList.add("dnd-active");
    setDragging(new Set(d.keys));
    setDropZone(true);
    setAnnounce(`Picked up ${d.keys.length > 1 ? `${d.keys.length} widgets` : describe(d.base, d.kind, d.keys[0])}`);
    const loop = () => {
      if (drag.current !== d) return;
      frame(d);
      d.raf = requestAnimationFrame(loop);
    };
    d.raf = requestAnimationFrame(loop);
  };
  /** One animation frame: ghost transform (lift spring + tilt), auto-scroll, target. */
  const frame = (d: Drag) => {
    d.scale.v += (-260 * (d.scale.x - 1.04) - 22 * d.scale.v) / 60;
    d.scale.x += d.scale.v / 60;
    const vx = d.x - d.lastX;
    d.lastX = d.x;
    d.tilt += (Math.max(-3, Math.min(3, vx * 0.25)) - d.tilt) * 0.2;
    if (d.ghost) {
      const gx = d.x - (d.startX - d.rect.left);
      const gy = d.y - (d.startY - d.rect.top);
      d.ghost.style.transform = `translate3d(${gx}px, ${gy}px, 0) scale(${d.scale.x.toFixed(4)}) rotate(${d.tilt.toFixed(2)}deg)`;
    }
    // auto-scroll near the viewport edges
    const edge = 72;
    const top = (document.querySelector(".ed-bar")?.getBoundingClientRect().bottom ?? 0) + 8;
    const maxScroll = document.documentElement.scrollHeight - innerHeight;
    const speed = d.y < top + edge ? -Math.ceil((top + edge - d.y) / 5) : d.y > innerHeight - edge ? Math.ceil((d.y - (innerHeight - edge)) / 5) : 0;
    if ((speed < 0 && scrollY > 0) || (speed > 0 && scrollY < maxScroll)) window.scrollBy(0, Math.max(-24, Math.min(24, speed)));
    target(d);
  };
  const target = (d: Drag) => {
    const el = document.elementFromPoint(d.x, d.y) as HTMLElement | null;
    const tab = el?.closest<HTMLElement>("[data-tab]")?.dataset.tab ?? null;
    if (tab !== d.tab) {
      d.tab = tab;
      d.tabSince = performance.now();
      setTabHover(tab);
    }
    if (tab) {
      // spring-loaded tabs: hovering a tab opens that page
      if (tab !== d.page && performance.now() - d.tabSince > 650) {
        d.page = tab;
        props.onPage(tab);
      }
      if (d.targetKey !== `tab:${tab}`) {
        d.targetKey = `tab:${tab}`;
        d.result = null;
        showPreview(null);
      }
      return;
    }
    let key = "none";
    let result: ResolvedPage[] | null = null;
    const pg = gridRef.current;
    // never aim at things that only exist in the preview (the placeholder, a preview-only card): no feedback loops
    const baseCards = new Set(d.base.flatMap((p) => p.cards.map((c) => c.key)));
    const overCard = el?.closest<HTMLElement>("[data-card]")?.dataset.card;
    if (el?.closest(".is-dragging") || (overCard && !baseCards.has(overCard))) return;
    if (d.kind === "widget") {
      const wg = el?.closest<HTMLElement>("[data-wgrid]");
      if (wg) {
        const c = cellIn(wg, d.x, d.y);
        const card = wg.dataset.wgrid!;
        const first = findWidget(d.base, d.keys[0])?.widget;
        const w = first?.box.w ?? 1;
        const x = Math.max(0, Math.min(COLS - w, c.x - d.grabCol));
        const y = Math.max(0, c.y - d.grabRow);
        key = `w:${card}:${x}:${y}`;
        if (key !== d.targetKey) result = moveWidgetsTo(d.base, d.keys, card, x, y);
      } else if (pg && el && pg.contains(el) && !el.closest("[data-card]") && !props.narrow) {
        const c = cellIn(pg, d.x, d.y);
        // the gap between two cards is not "empty space"
        const covered = (d.base.find((p) => p.id === d.page)?.cards ?? []).some((k) => !k.hidden && c.x >= k.box.x && c.x < k.box.x + k.box.w && c.y >= k.box.y && c.y < k.box.y + k.box.h);
        if (covered) return;
        const x = Math.max(0, Math.min(COLS - 3, c.x));
        key = `n:${d.page}:${x}:${c.y}`;
        if (key !== d.targetKey) result = newCardWithWidgets(d.base, d.keys, d.page, x, c.y).pages;
      } else if (el?.closest("[data-dropzone]")) {
        key = `n:${d.page}:end`;
        if (key !== d.targetKey) result = newCardWithWidgets(d.base, d.keys, d.page, 0, bottom((d.base.find((p) => p.id === d.page)?.cards ?? []).filter((c) => !c.hidden).map((c) => ({ id: c.key, ...c.box })))).pages;
      }
    } else if (pg) {
      if (props.narrow) {
        // narrow screens: reading order – insert before/after the card under the pointer
        const over = el?.closest<HTMLElement>("[data-card]");
        const pageCards = (d.base.find((p) => p.id === d.page)?.cards ?? []).filter((c) => !c.hidden && c.key !== d.keys[0]);
        if (over && over.dataset.card !== d.keys[0]) {
          const r = over.getBoundingClientRect();
          const i = pageCards.findIndex((c) => c.key === over.dataset.card) + (d.y > r.top + r.height / 2 ? 1 : 0);
          key = `o:${d.page}:${i}`;
          if (key !== d.targetKey) result = moveCard(d.base, d.keys[0], d.page, i);
        }
      } else if (el && (pg.contains(el) || el.closest("[data-dropzone]"))) {
        const c = cellIn(pg, d.x, d.y);
        const card = d.base.flatMap((p) => p.cards).find((x) => x.key === d.keys[0]);
        const w = card?.box.w ?? 3;
        const x = Math.max(0, Math.min(COLS - w, c.x - d.grabCol));
        const y = Math.max(0, c.y - d.grabRow);
        key = `c:${d.page}:${x}:${y}`;
        if (key !== d.targetKey) result = moveCardTo(d.base, d.keys[0], d.page, x, y);
      }
    }
    if (key === d.targetKey) return;
    d.targetKey = key;
    d.result = key === "none" ? null : result;
    showPreview(d.result);
  };
  const onMove = (e: PointerEvent) => {
    const d = drag.current;
    if (!d || e.pointerId !== d.pointerId) return;
    d.x = e.clientX;
    d.y = e.clientY;
    if (!d.active) {
      const dist = Math.hypot(d.x - d.startX, d.y - d.startY);
      // touch: moving before the long-press fires means scrolling – let it scroll
      if (d.touch && dist > 8) return end(false);
      if (!d.touch && dist > 5) activate(d);
    }
  };
  const onUp = (e: PointerEvent) => {
    if (drag.current && e.pointerId === drag.current.pointerId) end(true);
  };
  const onCancel = () => end(false);
  const end = (drop: boolean) => {
    const d = drag.current;
    if (!d) return;
    drag.current = null;
    clearTimeout(d.timer);
    cancelAnimationFrame(d.raf);
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onUp);
    window.removeEventListener("pointercancel", onCancel);
    document.body.classList.remove("dnd-active");
    setTabHover(null);
    setDropZone(false);
    if (!d.active) return;
    justDragged.current = Date.now();
    let final: ResolvedPage[] | null = null;
    if (drop && d.tab) {
      // dropped on a page tab: onto that page (widgets get a new card there)
      final = d.kind === "widget" ? newCardWithWidgets(d.base, d.keys, d.tab).pages : moveCardTo(d.base, d.keys[0], d.tab);
      props.onPage(d.tab);
    } else if (drop) final = d.result;
    if (final) {
      commit(final);
      vibrate(8);
      setAnnounce(`Dropped. ${describe(final, d.kind, d.keys[0])}`);
    } else {
      showPreview(null);
      setAnnounce("Cancelled.");
    }
    // the ghost springs into the item's new (or old) place, then the item shows again
    const ghost = d.ghost;
    const settle = () => {
      ghost?.remove();
      setDragging(new Set());
    };
    if (!ghost) return settle();
    requestAnimationFrame(() => {
      const sel = d.kind === "widget" ? `[data-w="${CSS.escape(d.keys[0])}"]` : `[data-card="${CSS.escape(d.keys[0])}"]`;
      const dest = document.querySelector<HTMLElement>(sel);
      const r = dest?.getBoundingClientRect();
      const from = { x: d.x - (d.startX - d.rect.left), y: d.y - (d.startY - d.rect.top), s: d.scale.x };
      if (!r || r.width === 0) {
        ghost.style.transition = "opacity 160ms";
        ghost.style.opacity = "0";
        setTimeout(settle, 170);
        return;
      }
      ghost.style.width = `${r.width}px`;
      ghost.style.height = `${r.height}px`;
      springTo(ghost, from, { x: r.left, y: r.top, s: 1 }).then(settle);
    });
  };
  // a blocking touchmove listener must exist before the gesture starts, or the browser scrolls anyway
  useEffect(() => {
    document.addEventListener("touchmove", blockTouch, { passive: false });
    return () => {
      document.removeEventListener("touchmove", blockTouch);
      end(false);
    };
  }, []);

  // ------------------------------------------------------------------ resize
  const resize = (e: PointerEvent, kind: Kind, key: string, edge: Edge, start: Box) => {
    e.preventDefault();
    e.stopPropagation();
    const handle = e.currentTarget as HTMLElement;
    const grid = kind === "widget" ? (handle.closest("[data-wgrid]") as HTMLElement | null) : gridRef.current;
    if (!grid) return;
    handle.setPointerCapture(e.pointerId);
    const t = tracks(grid);
    const colUnit = (t.cols[0] ?? 20) + t.colGap;
    const rowUnit = t.baseRow + t.rowGap;
    const base = current(histRef.current);
    let last = `${start.w}x${start.h}xfalse`;
    let result: ResolvedPage[] | null = null;
    document.body.classList.add("dnd-active", edge === "r" ? "rz-ew" : edge === "b" ? "rz-ns" : "rz-nwse");
    const minW = kind === "card" ? MIN_CARD_W : minWidgetW(findWidget(base, key)?.widget.w.type ?? "");
    const move = (ev: PointerEvent) => {
      const dw = edge !== "b" ? Math.round((ev.clientX - e.clientX) / colUnit) : 0;
      const dh = edge !== "r" ? Math.round((ev.clientY - e.clientY) / rowUnit) : 0;
      const raw = Math.min(COLS - start.x, start.w + dw);
      // below the readable minimum: it stops there and the badge turns red
      const atMin = raw < minW;
      const w = Math.max(minW, raw);
      const h = Math.max(kind === "card" ? 3 : 1, start.h + dh);
      const sig = `${w}x${h}x${atMin}`;
      if (sig === last) return;
      if (atMin && !last.endsWith("true")) vibrate(6);
      last = sig;
      result = kind === "widget" ? resizeWidget(base, key, w, h) : resizeCard(base, key, w, h);
      setBadge({ key, text: atMin ? `min ${minW} × ${h}` : `${w} × ${h}`, warn: atMin });
      showPreview(result);
    };
    const up = () => {
      handle.removeEventListener("pointermove", move);
      handle.removeEventListener("pointerup", up);
      handle.removeEventListener("pointercancel", cancel);
      document.body.classList.remove("dnd-active", "rz-ew", "rz-ns", "rz-nwse");
      setBadge(null);
      justDragged.current = Date.now();
      // a short bounce when it was held at its minimum
      if (last.endsWith("true")) {
        const el = document.querySelector<HTMLElement>(kind === "widget" ? `[data-w="${CSS.escape(key)}"]` : `[data-card="${CSS.escape(key)}"]`);
        el?.classList.remove("bounce");
        void el?.offsetWidth;
        el?.classList.add("bounce");
        setTimeout(() => el?.classList.remove("bounce"), 400);
      }
      if (result) commit(result, undefined, false);
      else setPreview(null);
    };
    const cancel = () => {
      result = null;
      up();
    };
    handle.addEventListener("pointermove", move);
    handle.addEventListener("pointerup", up);
    handle.addEventListener("pointercancel", cancel);
    setBadge({ key, text: `${start.w} × ${start.h}` });
  };

  // ---------------------------------------------------------------- keyboard
  const itemKey = (e: KeyboardEvent, kind: Kind, key: string) => {
    if (kbd) {
      if (e.key === " " || e.key === "Enter") {
        e.preventDefault();
        commit(preview ?? kbd.base);
        setKbd(null);
        setAnnounce(`Dropped. ${describe(preview ?? kbd.base, kbd.kind, kbd.key)}`);
        focusAfter.current = { kind: kbd.kind, key: kbd.key };
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        showPreview(null);
        setKbd(null);
        setAnnounce("Move cancelled.");
        focusAfter.current = { kind: kbd.kind, key: kbd.key };
        return;
      }
      const r = kbdStep(preview ?? kbd.base, kbd.kind, kbd.key, e);
      if (r) {
        e.preventDefault();
        showPreview(r.pages);
        setAnnounce(r.message);
        focusAfter.current = { kind: kbd.kind, key: kbd.key };
      }
      return;
    }
    if (e.key === " ") {
      e.preventDefault();
      setKbd({ kind, key, base: current(hist) });
      setAnnounce(`Picked up. ${describe(current(hist), kind, key)} ${PICKUP_HELP}`);
      focusAfter.current = { kind, key };
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (kind === "widget") {
        setSel(new Set([key]));
        setSelCard(null);
      } else {
        setSelCard(key);
        setSel(new Set());
      }
      setPanel("settings");
    } else if (e.key === "Delete" || e.key === "Backspace") {
      e.preventDefault();
      if (kind === "widget") commit(updateWidget(current(hist), key, { hidden: true }));
      else commit(updateCard(current(hist), key, { hidden: true }));
      setAnnounce("Hidden. Find it again in Add.");
    }
  };
  // global shortcuts
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = (e.target as HTMLElement)?.closest?.("input, textarea, select, [contenteditable]");
      const mod = e.ctrlKey || e.metaKey;
      if (mod && e.key.toLowerCase() === "z") {
        e.preventDefault();
        setHist((h) => (e.shiftKey ? redo(h) : undo(h)));
        setAnnounce(e.shiftKey ? "Redo" : "Undo");
      } else if (mod && e.key.toLowerCase() === "y") {
        e.preventDefault();
        setHist((h) => redo(h));
      } else if (mod && e.key.toLowerCase() === "s") {
        e.preventDefault();
        save();
      } else if (!typing && !mod && e.key === "?") {
        setPanel((p) => (p === "help" ? null : "help"));
      } else if (!typing && !mod && e.key.toLowerCase() === "a" && !kbd) {
        setPanel("add");
      } else if (e.key === "Escape" && !kbd && !drag.current && !confirm) {
        if (sel.size || selCard) {
          setSel(new Set());
          setSelCard(null);
        } else if (panel && !props.isPhone) setPanel(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });
  // unsaved-changes guard (tab close / reload)
  useEffect(() => {
    if (!dirty) return;
    const h = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", h);
    return () => window.removeEventListener("beforeunload", h);
  }, [dirty]);
  // the header's pencil asks to leave
  useEffect(() => {
    const h = () => cancel();
    window.addEventListener("cccloud:edit-exit", h);
    return () => window.removeEventListener("cccloud:edit-exit", h);
  });

  // ------------------------------------------------------------- save, exit
  const save = async () => {
    const doc = snapshotLayout(current(histRef.current), props.uplinks);
    if (layoutSize(doc) > MAX_LAYOUT_BYTES) return setError(SAVE_ERRORS.too_large);
    setSaving(true);
    setError(null);
    try {
      await conn.saveLayout(doc);
      pushToast({ level: "info", title: "Layout saved", text: "Every browser of this mainframe now sees the new layout." });
      props.onExit();
    } catch (e) {
      setError(SAVE_ERRORS[(e as Error).message] ?? `Could not save (${(e as Error).message}).`);
    } finally {
      setSaving(false);
    }
  };
  const cancel = () => {
    if (!isDirty(histRef.current)) return props.onExit();
    setConfirm({ title: "Discard changes?", text: "You changed the layout. Leave the editor without saving?", label: "Discard", color: "red", ok: props.onExit });
  };
  const reset = () =>
    setConfirm({
      title: "Reset to default?",
      text: "Cards and widgets go back to the layout the scripts declare: positions, sizes, names, colours and hidden items. You can still undo this before saving.",
      label: "Reset",
      color: "yellow",
      ok: () => commit(resolveLayout(props.uplinks, null, { includeHidden: true })),
    });

  // ----------------------------------------------------------- card wiring
  const selectedWidget = sel.size === 1 ? findWidget(pages, [...sel][0]) : undefined;
  const selectedCard = selCard ? pages.flatMap((p) => p.cards).find((c) => c.key === selCard) : undefined;
  const editFor = (card: ResolvedCard): CardEdit => ({
    selected: sel,
    cardSelected: selCard === card.key,
    dragging,
    placeholder: null,
    badge,
    onCardPointerDown: (e, c) => {
      if ((e.target as HTMLElement).closest("button")) return;
      const el = (e.currentTarget as HTMLElement).closest<HTMLElement>("[data-card]");
      if (el) begin(e, "card", [c.key], el, "grid");
    },
    onCardResize: (e, c, edge) => resize(e, "card", c.key, edge, c.box),
    onWidgetPointerDown: (e, rw) => {
      e.stopPropagation();
      const keys = sel.has(rw.key) && sel.size > 1 ? [...sel] : [rw.key];
      begin(e, "widget", keys, e.currentTarget as HTMLElement, "grid");
    },
    onWidgetResize: (e, rw, edge) => resize(e, "widget", rw.key, edge, rw.box),
    onWidgetClick: (e, rw) => {
      e.stopPropagation();
      if (Date.now() - justDragged.current < 250) return;
      if (e.shiftKey || e.metaKey || e.ctrlKey) {
        const next = new Set(sel);
        if (next.has(rw.key)) next.delete(rw.key);
        else next.add(rw.key);
        setSel(next);
        setAnnounce(`${next.size} widget${next.size === 1 ? "" : "s"} selected`);
      } else {
        setSel(new Set([rw.key]));
        if (!props.isPhone) setPanel("settings");
      }
      setSelCard(null);
    },
    onCardClick: (c) => {
      if (Date.now() - justDragged.current < 250) return;
      setSelCard(c.key);
      setSel(new Set());
      if (!props.isPhone) setPanel("settings");
    },
    onCardSettings: (c) => {
      setSelCard(c.key);
      setSel(new Set());
      setPanel("settings");
    },
    onWidgetSettings: (rw) => {
      setSel(new Set([rw.key]));
      setSelCard(null);
      setPanel("settings");
    },
    onHideCard: (c) => {
      commit(updateCard(current(hist), c.key, { hidden: true }));
      pushToast({ level: "info", title: "Card hidden", text: `${c.title} is in Add now – drag it back any time.` });
    },
    onHideWidget: (rw) => {
      commit(updateWidget(current(hist), rw.key, { hidden: true }));
      setSel(new Set());
    },
    onKeyDown: itemKey,
  });

  const shown = page.cards.filter((c) => !c.hidden);
  const hiddenCount = pages.reduce((n, p) => n + p.cards.filter((c) => c.hidden).length + p.cards.reduce((m, c) => m + c.widgets.filter((w) => w.hidden).length, 0), 0);
  const side =
    panel === "add" ? (
      <AddGallery
        pages={current(hist)}
        page={page.id}
        conn={conn}
        onAdd={(next) => commit(next)}
        onDragStart={(e, kind, key, el, base) => begin(e, kind, [key], el, "gallery", base)}
        onNewCard={() => {
          const r = newCardWithWidgets(current(hist), [], page.id);
          commit(r.pages);
          setSelCard(r.key);
          setPanel("settings");
        }}
        onNewPage={() => {
          const r = addPage(current(hist), "New page", "star");
          commit(r.pages);
          props.onPage(r.id);
        }}
      />
    ) : panel === "help" ? (
      <ShortcutHelp />
    ) : panel === "settings" && selectedWidget ? (
      <WidgetSettings key={selectedWidget.widget.key} found={selectedWidget} pages={current(hist)} conn={conn} onChange={(next, key) => commit(next, key, false)} />
    ) : panel === "settings" && selectedCard ? (
      <CardSettings key={selectedCard.key} card={selectedCard} pages={current(hist)} onChange={(next, key) => commit(next, key, false)} onPage={props.onPage} />
    ) : panel === "settings" ? (
      <div class="ed-empty">
        <b>Nothing selected</b>
        <p class="muted small">Click a widget or a card to change it here. Shift-click selects several widgets to move them together.</p>
      </div>
    ) : null;
  const sideTitle = panel === "add" ? "Add" : panel === "help" ? "Keyboard & mouse" : selectedWidget ? "Widget" : selectedCard ? "Card" : "Settings";

  return (
    <div class={`editor ${panel && !props.isPhone ? "with-side" : ""}`}>
      <div class="ed-bar" role="toolbar" aria-label="Layout editor">
        <span class="ed-title">
          <span class="edit-dot" /> Edit layout
          {dirty && <span class="ed-dirty">unsaved</span>}
        </span>
        <span class="ed-tools">
          <button type="button" class="btn btn-small ed-add" onClick={() => setPanel(panel === "add" ? null : "add")} aria-pressed={panel === "add"}>
            + Add{hiddenCount ? ` (${hiddenCount})` : ""}
          </button>
          <button type="button" class="icon-btn" aria-label="Undo (Ctrl+Z)" title="Undo (Ctrl+Z)" disabled={!canUndo(hist)} onClick={() => setHist(undo)}>
            ↶
          </button>
          <button type="button" class="icon-btn" aria-label="Redo (Ctrl+Shift+Z)" title="Redo (Ctrl+Shift+Z)" disabled={!canRedo(hist)} onClick={() => setHist(redo)}>
            ↷
          </button>
          <button type="button" class="btn btn-small btn-ghost" onClick={reset}>
            Reset
          </button>
          <button type="button" class="icon-btn" aria-label="Keyboard shortcuts (?)" title="Keyboard shortcuts (?)" onClick={() => setPanel(panel === "help" ? null : "help")}>
            ?
          </button>
        </span>
        <span class="ed-actions">
          <button type="button" class="btn btn-small" onClick={cancel} disabled={saving}>
            Cancel
          </button>
          <button type="button" class="pxbtn pxbtn-gold pxbtn-small" onClick={save} disabled={saving}>
            <span>{saving ? "Saving…" : "Save"}</span>
          </button>
        </span>
        {error && (
          <p class="edit-bar-error" role="alert">
            {error}
          </p>
        )}
      </div>
      <Tabs
        pages={pages}
        network={props.network}
        current={page.id}
        isPhone={props.isPhone}
        editing
        dropHover={tabHover}
        onSelect={props.onPage}
        onAdd={(title, icon) => {
          const r = addPage(current(hist), title, icon);
          commit(r.pages);
          props.onPage(r.id);
        }}
        onChange={(next) => commit(next)}
      />
      <div class="ed-main">
        <div class="ed-canvas">
          {sel.size > 1 && (
            <div class="ed-multi" role="status">
              {sel.size} widgets selected – drag one of them to move all.{" "}
              <button type="button" class="link-btn" onClick={() => setSel(new Set())}>
                Clear
              </button>
            </div>
          )}
          <div class={`pgrid editing ${props.narrow ? "narrow" : ""}`} data-pgrid ref={gridRef}>
            {shown.map((c) => (
              <Card key={c.key} card={c} conn={conn} locked={false} edit={editFor(c)} />
            ))}
            {(dropZone || shown.length === 0) && (
              <div class="p-dropzone" data-dropzone style={placeVars({ x: 0, y: bottom(shown.map((c) => ({ id: c.key, ...c.box }))), w: 12, h: shown.length ? 3 : 6 })}>
                {shown.length === 0 ? (
                  <>
                    <b>This page is empty</b>
                    <span>Drag cards or widgets here – from another page via its tab, or from + Add.</span>
                  </>
                ) : (
                  <span>Drop here for a new card</span>
                )}
              </div>
            )}
          </div>
        </div>
        {side && !props.isPhone && (
          <aside class="ed-side" aria-label={sideTitle}>
            <header class="ed-side-head">
              <b>{sideTitle}</b>
              <button type="button" class="icon-btn sm" aria-label="Close panel" onClick={() => setPanel(null)}>
                ×
              </button>
            </header>
            <div class="ed-side-body">{side}</div>
          </aside>
        )}
      </div>
      {side && props.isPhone && (
        <Sheet title={sideTitle} onClose={() => setPanel(null)}>
          {side}
        </Sheet>
      )}
      <div class="sr-only" aria-live="assertive">
        {announce}
      </div>
      {kbd && (
        <div class="ed-kbd" role="status">
          Moving with the keyboard – arrows move, Shift+arrows resize, PgUp/PgDn change card, Space drops, Esc cancels.
        </div>
      )}
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          text={confirm.text}
          confirmLabel={confirm.label}
          color={confirm.color}
          onCancel={() => setConfirm(null)}
          onConfirm={() => {
            const ok = confirm.ok;
            setConfirm(null);
            ok();
          }}
        />
      )}
    </div>
  );
}

export type { ResolvedWidget };
