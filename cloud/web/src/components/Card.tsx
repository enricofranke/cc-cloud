// One dashboard card (an uplink, a Lua group, or an uplink's widgets on a page),
// in view mode or in layout edit mode.
import type { ComponentChildren } from "preact";
import { useRef } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import type { ResolvedCard, ResolvedWidget } from "../lib/layout.ts";
import { ActionButton, Gauge, LabelRow, Slider, Toggle } from "./Widgets.tsx";
import { ItemSearch, Jobs, Watchlist } from "./Storage.tsx";
import { ButtonGroup, Chart, Indicator, NumberInput, Progress, Select, TextInput } from "./Widgets2.tsx";
import { PixelIcon } from "./PixelIcon.tsx";

/** Body columns: 2 for a 1-column card, 4 for wider cards (phones always use 2, see CSS). */
const bodyCols = (span: number) => (span >= 2 ? 4 : 2);

type Block = { kind: "gauges" | "labels" | "buttons"; items: ResolvedWidget[]; span: number } | { kind: "one"; item: ResolvedWidget; span: number };

/** Consecutive gauges share one "screen", consecutive labels one readout list,
 *  consecutive buttons one wrapping row (they stack instead of getting too narrow). */
function blocks(widgets: ResolvedWidget[], cols: number): Block[] {
  const out: Block[] = [];
  for (const rw of widgets) {
    const last = out[out.length - 1];
    const kind = rw.w.type === "gauge" ? "gauges" : rw.w.type === "label" ? "labels" : rw.w.type === "button" ? "buttons" : null;
    if (kind && last && last.kind === kind) {
      last.items.push(rw);
      last.span = kind === "gauges" ? Math.min(cols, last.items.reduce((s, x) => s + x.span, 0)) : kind === "buttons" ? cols : Math.max(last.span, rw.span);
    } else if (kind) out.push({ kind, items: [rw], span: kind === "buttons" ? cols : Math.min(cols, rw.span) });
    else out.push({ kind: "one", item: rw, span: Math.min(cols, rw.span) });
  }
  return out;
}

function WidgetView({ rw, card, conn, locked }: { rw: ResolvedWidget; card: ResolvedCard; conn: MainframeConn; locked: boolean }) {
  const w = rw.label !== rw.w.label ? { ...rw.w, label: rw.label } : rw.w;
  const p = { w, uplink: card.uplink, conn, locked };
  switch (w.type) {
    case "gauge":
      return <Gauge {...p} />;
    case "label":
      return <LabelRow {...p} />;
    case "toggle":
      return <Toggle {...p} />;
    case "slider":
      return <Slider {...p} />;
    case "button":
      return <ActionButton {...p} />;
    case "item_search":
      return <ItemSearch {...p} />;
    case "watchlist":
      return <Watchlist {...p} />;
    case "jobs":
      return <Jobs w={w} />;
    case "chart":
      return <Chart {...p} />;
    case "progress":
      return <Progress {...p} />;
    case "indicator":
      return <Indicator {...p} />;
    case "select":
      return <Select {...p} />;
    case "number":
      return <NumberInput {...p} />;
    case "text":
      return <TextInput {...p} />;
    case "buttons":
      return <ButtonGroup {...p} />;
  }
}

export function Card({ card, conn, locked, children }: { card: ResolvedCard; conn: MainframeConn; locked: boolean; children?: ComponentChildren }) {
  const cardLocked = locked || !card.uplink.online;
  const cols = bodyCols(card.span);
  return (
    <article class={`card cs${card.span} accent-${card.color} ${card.uplink.online ? "" : "card-offline"}`} aria-label={card.title}>
      <header class="card-head">
        {card.icon ? <PixelIcon name={card.icon} size={16} class="card-icon" /> : <span class={`led ${card.uplink.online ? "led-on" : "led-off"}`} aria-hidden="true" />}
        <h3 class="card-title">{card.title || "Untitled"}</h3>
        {!card.uplink.online ? <span class="tag tag-red">Uplink offline</span> : card.icon && <span class="led led-on" aria-hidden="true" />}
      </header>
      {card.widgets.length === 0 ? (
        <p class="card-empty">No widgets yet. Use cloud.button(), cloud.gauge() … on the computer next to this uplink.</p>
      ) : (
        <div class={`card-body cb bc-${cols}`}>
          {blocks(card.widgets, cols).map((b) => {
            if (b.kind === "one")
              return (
                <div key={b.item.key} class={`wcell s${b.span} t-${b.item.w.type}`}>
                  <WidgetView rw={b.item} card={card} conn={conn} locked={cardLocked} />
                </div>
              );
            const inner = b.items.map((rw) => <WidgetView key={rw.key} rw={rw} card={card} conn={conn} locked={cardLocked} />);
            if (b.kind === "buttons")
              return (
                <div key={b.items[0].key} class={`btn-row s${b.span}`}>
                  {inner}
                </div>
              );
            return b.kind === "gauges" ? (
              <div key={b.items[0].key} class={`screen gauges s${b.span}`}>
                {inner}
              </div>
            ) : (
              <div key={b.items[0].key} class={`readouts s${b.span}`}>
                {inner}
              </div>
            );
          })}
        </div>
      )}
      {children}
    </article>
  );
}

// ------------------------------------------------------------------ edit mode
export interface EditHandlers {
  isPhone: boolean;
  index: number;
  count: number;
  onMove: (dir: -1 | 1) => void;
  onSettings: () => void;
  onWidgetMove: (widgetKey: string, dir: -1 | 1) => void;
  onWidgetSettings: (rw: ResolvedWidget) => void;
  onWidgetToggle: (rw: ResolvedWidget) => void;
  onResize: (span: number) => void;
  /** desktop drag & drop */
  dnd: {
    onCardDragStart: (e: DragEvent) => void;
    onCardDragOver: (e: DragEvent) => void;
    onCardDrop: (e: DragEvent) => void;
    onWidgetDragStart: (e: DragEvent, rw: ResolvedWidget) => void;
    onWidgetDrop: (e: DragEvent, rw: ResolvedWidget) => void;
    dropTarget: boolean;
  };
}

const TYPE_LABEL: Record<string, string> = {
  item_search: "search", watchlist: "watch", buttons: "segments", indicator: "LED",
};

export function EditCard({ card, h }: { card: ResolvedCard; h: EditHandlers }) {
  const ref = useRef<HTMLElement>(null);
  const startResize = (e: PointerEvent) => {
    const el = ref.current;
    const grid = el?.parentElement;
    if (!el || !grid) return;
    e.preventDefault();
    const cols = getComputedStyle(grid).gridTemplateColumns.split(" ").filter(Boolean).length || 1;
    const gap = parseFloat(getComputedStyle(grid).columnGap) || 0;
    const colW = (grid.clientWidth + gap) / cols;
    const left = el.getBoundingClientRect().left;
    const target = e.currentTarget as HTMLElement;
    target.setPointerCapture(e.pointerId);
    let last = card.span;
    const move = (ev: PointerEvent) => {
      const span = Math.max(1, Math.min(Math.min(4, cols), Math.round((ev.clientX - left + gap) / colW)));
      if (span !== last) {
        last = span;
        h.onResize(span);
      }
    };
    const up = () => {
      target.removeEventListener("pointermove", move);
      target.removeEventListener("pointerup", up);
      target.removeEventListener("pointercancel", up);
    };
    target.addEventListener("pointermove", move);
    target.addEventListener("pointerup", up);
    target.addEventListener("pointercancel", up);
  };

  return (
    <article
      ref={ref}
      class={`card card-edit cs${card.span} accent-${card.color} ${card.hidden ? "is-hidden" : ""} ${h.dnd.dropTarget ? "drop-target" : ""}`}
      aria-label={`Edit ${card.title}`}
      onDragOver={h.isPhone ? undefined : h.dnd.onCardDragOver}
      onDrop={h.isPhone ? undefined : h.dnd.onCardDrop}
    >
      <header class="card-head">
        {!h.isPhone && (
          <span class="drag-handle" draggable onDragStart={h.dnd.onCardDragStart} title="Drag to reorder" aria-label="Drag to reorder">
            ⠿
          </span>
        )}
        <PixelIcon name={card.icon ?? "box"} size={16} class="card-icon" />
        <h3 class="card-title">{card.title || "Untitled"}</h3>
        {card.hidden && <span class="tag tag-gray">hidden</span>}
        {h.isPhone && (
          <>
            <button type="button" class="icon-btn sm" aria-label="Move card up" disabled={h.index === 0} onClick={() => h.onMove(-1)}>
              ▲
            </button>
            <button type="button" class="icon-btn sm" aria-label="Move card down" disabled={h.index === h.count - 1} onClick={() => h.onMove(1)}>
              ▼
            </button>
          </>
        )}
        <button type="button" class="btn btn-small" onClick={h.onSettings} aria-label={`Card settings for ${card.title}`}>
          Edit
        </button>
      </header>
      <ul class="edit-widgets">
        {card.widgets.map((rw, i) => (
          <li
            key={rw.key}
            class={`edit-w ${rw.hidden ? "is-hidden" : ""}`}
            onDragOver={h.isPhone ? undefined : (e) => e.preventDefault()}
            onDrop={h.isPhone ? undefined : (e) => h.dnd.onWidgetDrop(e, rw)}
          >
            {!h.isPhone && (
              <span class="drag-handle sm" draggable onDragStart={(e) => h.dnd.onWidgetDragStart(e, rw)} aria-label="Drag to reorder">
                ⠿
              </span>
            )}
            <button type="button" class="edit-w-main" onClick={() => h.onWidgetSettings(rw)}>
              <span class="type-tag">{TYPE_LABEL[rw.w.type] ?? rw.w.type}</span>
              <span class="edit-w-label">{rw.label}</span>
              {!h.isPhone && <span class="span-tag">×{rw.span}</span>}
            </button>
            <button type="button" class={`icon-btn sm eye ${rw.hidden ? "off" : ""}`} aria-label={rw.hidden ? `Show ${rw.label}` : `Hide ${rw.label}`} onClick={() => h.onWidgetToggle(rw)}>
              {rw.hidden ? "◌" : "◉"}
            </button>
            <button type="button" class="icon-btn sm" aria-label={`Move ${rw.label} up`} disabled={i === 0} onClick={() => h.onWidgetMove(rw.key, -1)}>
              ▲
            </button>
            <button type="button" class="icon-btn sm" aria-label={`Move ${rw.label} down`} disabled={i === card.widgets.length - 1} onClick={() => h.onWidgetMove(rw.key, 1)}>
              ▼
            </button>
          </li>
        ))}
      </ul>
      {!h.isPhone && <span class="resize-handle" onPointerDown={startResize} title="Drag to resize" aria-label="Resize card" role="separator" />}
    </article>
  );
}
