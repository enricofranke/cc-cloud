// One dashboard card (an uplink, a Lua group, or an uplink's widgets on a page),
// in view mode or in layout edit mode.
import type { ComponentChildren, ComponentType } from "preact";
import { useEffect, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import type { Box, ResolvedCard, ResolvedWidget } from "../lib/layout.ts";
import { DISPLAYS_FOR, captionOf } from "../lib/layout.ts";
import { ActionButton, Gauge, LabelRow, Slider, Toggle } from "./Widgets.tsx";
import { ItemSearch, Jobs, Watchlist } from "./Storage.tsx";
import { ButtonGroup, Chart, Indicator, NumberInput, Progress, Select, TextInput } from "./Widgets2.tsx";
import { PixelIcon } from "./PixelIcon.tsx";
import { RedstoneSides } from "./Redstone.tsx";
import { powerButtonState } from "../lib/redstone.ts";
import { DocsLink } from "./DocsLink.tsx";
import { timeAgo } from "../lib/ui.ts";
import { historyOf } from "../lib/history.ts";
import type { Uplink, Widget } from "../lib/types.ts";

/** Widgets that only exist to act: left out entirely in read-only (share) views. */
const ACTION_ONLY = new Set(["button", "item_search"]);

/** A read-only stand-in for an input widget (share links: no action UI at all). */
function StaticValue({ w }: { w: ResolvedWidget["w"] }) {
  const v = w.value ?? w.opts?.value;
  const text =
    typeof v === "boolean" ? (v ? "ON" : "OFF") : typeof v === "number" ? `${Number.isInteger(v) ? v : v.toFixed(1)}${w.opts?.unit ? ` ${w.opts.unit}` : ""}` : typeof v === "string" && v ? v : "—";
  return (
    <div class="kv static-value">
      <span class="kv-key">{w.label ?? w.id}</span>
      <span class={`kv-val ${typeof v === "boolean" ? (v ? "on" : "off") : ""}`}>{text}</span>
    </div>
  );
}

export function WidgetView({ rw, conn, locked, viewOnly }: { rw: ResolvedWidget; conn: MainframeConn; locked: boolean; viewOnly?: boolean }) {
  // a label set in the editor wins; without any caption (resolver fell back to the id) keep none
  const caption = captionOf(rw);
  const display = rw.display !== "auto" && rw.display !== (DISPLAYS_FOR[rw.w.type]?.[0] ?? "auto") ? rw.display : undefined;
  const w =
    caption !== rw.w.label || display || rw.icon
      ? { ...rw.w, label: caption, opts: { ...(rw.w.opts ?? {}), ...(display ? { display, ...(display === "sparkline" ? { style: "sparkline" as const } : display === "line" ? { style: "line" as const } : {}) } : {}), ...(rw.icon ? { wicon: rw.icon } : {}) } }
      : rw.w;
  const card = { uplink: rw.uplink };
  if (viewOnly) {
    if (["toggle", "slider", "select", "number", "text", "buttons"].includes(w.type)) return <StaticValue w={w} />;
    if (w.type === "watchlist") return <Watchlist w={w} uplink={card.uplink} conn={conn} locked viewOnly />;
    if (w.type === "redstone_sides") return <RedstoneSides w={w} uplink={card.uplink} conn={conn} locked />;
  }
  // auto Computer card: disable power buttons that make no sense right now (keeps the layout stable)
  const pb = w.type === "button" && card.uplink.mode === "auto" ? powerButtonState(card.uplink, w.id) : undefined;
  const p = { w, uplink: card.uplink, conn, locked: locked || !!pb?.disabled };
  if (pb?.disabled)
    return (
      <span class="btn-muted" title={pb.reason}>
        <ActionButton {...p} />
      </span>
    );
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
      return <Jobs w={w} uplink={card.uplink} conn={conn} />;
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
    case "redstone_sides":
      return <RedstoneSides {...p} />;
  }
}

// script import lives in its own lazy chunk (with the bundled examples)
type ScriptSheetProps = { conn: MainframeConn; uplinkId: string; title: string; onClose: () => void };
let ScriptSheetComp: ComponentType<ScriptSheetProps> | null = null;
function ScriptsLoader(props: ScriptSheetProps) {
  const [C, setC] = useState<ComponentType<ScriptSheetProps> | null>(() => ScriptSheetComp);
  useEffect(() => {
    if (!C) import("../scripts/ScriptSheet.tsx").then((m) => setC(() => (ScriptSheetComp = m.default)));
  }, []);
  return C ? <C {...props} /> : null;
}

type TermProps = { conn: MainframeConn; uplinkId: string; title: string; onClose: () => void };
let TermComp: ComponentType<TermProps> | null = null;
function TermLoader(props: TermProps) {
  const [C, setC] = useState<ComponentType<TermProps> | null>(() => TermComp);
  useEffect(() => {
    if (!C) import("../scripts/Terminal.tsx").then((m) => setC(() => (TermComp = m.default)));
  }, []);
  return C ? <C {...props} /> : null;
}

type HistoryProps = { conn: MainframeConn; uplink: Uplink; w: Widget; onClose: () => void };
let HistoryComp: ComponentType<HistoryProps> | null = null;
function HistoryLoader(props: HistoryProps) {
  const [C, setC] = useState<ComponentType<HistoryProps> | null>(() => HistoryComp);
  useEffect(() => {
    if (!C) import("./History.tsx").then((m) => setC(() => (HistoryComp = m.default)));
  }, []);
  return C ? <C {...props} /> : null;
}

export interface CardEdit {
  selected: Set<string>;
  cardSelected: boolean;
  /** keys hidden while their ghost is dragged */
  dragging: Set<string>;
  /** where a dragged widget would land in this card */
  placeholder: Box | null;
  badge: { key: string; text: string; warn?: boolean } | null;
  onCardPointerDown: (e: PointerEvent, card: ResolvedCard) => void;
  onCardResize: (e: PointerEvent, card: ResolvedCard, edge: Edge) => void;
  onWidgetPointerDown: (e: PointerEvent, rw: ResolvedWidget) => void;
  onWidgetResize: (e: PointerEvent, rw: ResolvedWidget, edge: Edge) => void;
  onWidgetClick: (e: MouseEvent, rw: ResolvedWidget) => void;
  onCardClick: (card: ResolvedCard) => void;
  onCardSettings: (card: ResolvedCard) => void;
  onWidgetSettings: (rw: ResolvedWidget) => void;
  onHideCard: (card: ResolvedCard) => void;
  onHideWidget: (rw: ResolvedWidget) => void;
  onKeyDown: (e: KeyboardEvent, kind: "card" | "widget", key: string) => void;
}
export type Edge = "r" | "b" | "rb";

/** Grid placement as CSS custom properties (CSSOM, CSP-safe). Narrow screens use --pw/--tw spans in reading order. */
export function placeVars(b: Box): Record<string, string> {
  return {
    "--x": String(b.x + 1),
    "--y": String(b.y + 1),
    "--w": String(b.w),
    "--h": String(b.h),
    "--tw": String(b.w <= 4 ? 3 : 6),
    "--pw": String(b.w <= 2 ? 1 : 2),
  };
}

function Resizers({ onDown, label }: { onDown: (e: PointerEvent, edge: Edge) => void; label: string }) {
  return (
    <>
      <span class="rz rz-r" onPointerDown={(e) => onDown(e, "r")} aria-hidden="true" />
      <span class="rz rz-b" onPointerDown={(e) => onDown(e, "b")} aria-hidden="true" />
      <span class="rz rz-rb" onPointerDown={(e) => onDown(e, "rb")} title={`Resize ${label}`} aria-hidden="true" />
    </>
  );
}

export function Card({ card: full, conn, locked, viewOnly, children, edit }: { card: ResolvedCard; conn: MainframeConn; locked: boolean; viewOnly?: boolean; children?: ComponentChildren; edit?: CardEdit }) {
  const card = viewOnly ? { ...full, widgets: full.widgets.filter((w) => !ACTION_ONLY.has(w.w.type)) } : full;
  const online = card.custom ? card.widgets.every((w) => w.uplink.online) : card.uplink.online;
  // the chunk is unloaded: the mod sends the cached last snapshot
  const cached = !card.custom && card.uplink.loaded === false;
  const [scripts, setScripts] = useState(false);
  const [hist, setHist] = useState<ResolvedWidget | null>(null);
  const [term, setTerm] = useState(false);
  const shown = card.widgets.filter((w) => !w.hidden);
  return (
    <article
      class={`card accent-${card.color} style-${card.style} ${cached ? "card-cached" : online ? "" : "card-offline"} ${edit ? "card-editing" : ""} ${edit?.cardSelected ? "is-selected" : ""} ${edit?.dragging.has(card.key) ? "is-dragging" : ""}`}
      aria-label={card.title}
      data-card={card.key}
      style={placeVars(card.box)}
      tabIndex={edit ? 0 : undefined}
      onKeyDown={edit ? (e) => e.target === e.currentTarget && edit.onKeyDown(e, "card", card.key) : undefined}
      onClick={edit ? (e) => e.target === e.currentTarget && edit.onCardClick(card) : undefined}
    >
      <header class="card-head" onPointerDown={edit ? (e) => edit.onCardPointerDown(e, card) : undefined}>
        {edit && (
          <span class="grip" aria-hidden="true">
            ⠿
          </span>
        )}
        {card.icon ? <PixelIcon name={card.icon} size={16} class="card-icon" /> : <span class={`led ${online ? "led-on" : "led-off"}`} aria-hidden="true" />}
        <h3 class="card-title">{card.title || "Untitled"}</h3>
        {card.uplink.mode === "auto" && !card.custom && (
          <span class="tag tag-auto" title="Automatic card – disappears as soon as a Lua program on this computer defines widgets">
            auto
          </span>
        )}
        {cached ? <span class="tag tag-gray">Not loaded</span> : !online ? <span class="tag tag-red">Uplink offline</span> : card.icon && <span class="led led-on" aria-hidden="true" />}
        {!edit && !viewOnly && !card.custom && conn.can("terminal") && online && card.uplink.mode !== "direct" && (
          <button type="button" class="icon-btn card-term" title="Terminal" aria-label={`Terminal of ${card.title || "this computer"}`} onClick={() => setTerm(true)}>
            <span aria-hidden="true">{">_"}</span>
          </button>
        )}
        {!edit && !viewOnly && !card.custom && conn.can("scripts.upload") && online && card.uplink.mode !== "direct" && (
          <button type="button" class="icon-btn card-scripts" title="Scripts on this computer" aria-label={`Upload script to ${card.title || "this computer"}`} onClick={() => setScripts(true)}>
            <span aria-hidden="true">{"</>"}</span>
          </button>
        )}
        {edit && (
          <span class="card-tools" onPointerDown={(e) => e.stopPropagation()}>
            <button type="button" class="icon-btn sm" aria-label={`Settings for ${card.title}`} title="Settings" onClick={() => edit.onCardSettings(card)}>
              ⚙
            </button>
            <button type="button" class="icon-btn sm" aria-label={`Hide ${card.title}`} title="Hide card" onClick={() => edit.onHideCard(card)}>
              ×
            </button>
          </span>
        )}
      </header>
      {cached && (
        <p class="card-cached-note">
          {card.uplink.lastSeen ? `Last seen ${timeAgo(card.uplink.lastSeen)}` : "Not seen yet"} – chunk not loaded. Showing the last values.{" "}
          <DocsLink to="/docs/faq#unloaded">Why?</DocsLink>
        </p>
      )}
      {term && <TermLoader conn={conn} uplinkId={card.uplink.uplinkId} title={card.title || card.uplink.title || "Computer"} onClose={() => setTerm(false)} />}
      {hist && <HistoryLoader conn={conn} uplink={hist.uplink} w={hist.w} onClose={() => setHist(null)} />}
      {scripts && <ScriptsLoader conn={conn} uplinkId={card.uplink.uplinkId} title={card.title || card.uplink.title || "Computer"} onClose={() => setScripts(false)} />}
      {shown.length === 0 && !edit ? (
        <p class="card-empty">
          Nothing to show yet – this uplink is not attached to a computer or bridge, or its program cleared all widgets.{" "}
          <DocsLink to="/docs/faq#no-widgets">Why?</DocsLink>
        </p>
      ) : (
        <div class="card-body wgrid" data-wgrid={card.key}>
          {shown.map((rw) => (
            <div
              key={rw.key}
              class={`wcell t-${rw.w.type} d-${rw.display} ${rw.color ? `waccent-${rw.color}` : ""} ${edit?.selected.has(rw.key) ? "is-selected" : ""} ${edit?.dragging.has(rw.key) ? "is-dragging" : ""}`}
              data-w={rw.key}
              style={placeVars(rw.box)}
              tabIndex={edit ? 0 : undefined}
              aria-label={edit ? `${captionOf(rw) ?? rw.w.type} widget, ${rw.box.w} by ${rw.box.h}. Space to pick up, Enter for settings.` : undefined}
              onKeyDown={edit ? (e) => e.target === e.currentTarget && edit.onKeyDown(e, "widget", rw.key) : undefined}
              onPointerDown={edit ? (e) => edit.onWidgetPointerDown(e, rw) : undefined}
              onClick={edit ? (e) => edit.onWidgetClick(e, rw) : undefined}
            >
              <div
                class="wcontent"
                inert={edit ? true : undefined}
                title={rw.uplink.loaded === false && !edit ? "Its chunk is not loaded, so it cannot be controlled right now. Keep the chunk loaded (e.g. FTB Chunks force-load) or go near it." : undefined}
              >
                <WidgetView rw={rw} conn={conn} locked={locked || !rw.uplink.online || rw.uplink.loaded === false || !!viewOnly} viewOnly={viewOnly} />
              </div>
              {!edit && !viewOnly && !conn.isShare && historyOf(rw.w) && (
                <button type="button" class="w-hist" aria-label={`History of ${rw.label}`} title="History" onClick={() => setHist(rw)}>
                  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                    <path d="M1 13h14M2 11l3-4 3 2 3-5 3 3" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                  </svg>
                </button>
              )}
              {edit && (
                <>
                  <span class="w-tools" onPointerDown={(e) => e.stopPropagation()}>
                    <button type="button" class="icon-btn xs" tabIndex={-1} aria-label={`Settings for ${rw.label}`} title="Settings" onClick={(e) => (e.stopPropagation(), edit.onWidgetSettings(rw))}>
                      ⚙
                    </button>
                    <button type="button" class="icon-btn xs" tabIndex={-1} aria-label={`Hide ${rw.label}`} title="Hide" onClick={(e) => (e.stopPropagation(), edit.onHideWidget(rw))}>
                      ×
                    </button>
                  </span>
                  <Resizers onDown={(e, edge) => edit.onWidgetResize(e, rw, edge)} label={rw.label} />
                  {edit.badge?.key === rw.key && <span class={`size-badge ${edit.badge.warn ? "warn" : ""}`}>{edit.badge.text}</span>}
                </>
              )}
            </div>
          ))}
          {edit?.placeholder && <div class="w-placeholder" style={placeVars(edit.placeholder)} aria-hidden="true" />}
          {edit && shown.length === 0 && <div class="w-empty">Drop widgets here</div>}
        </div>
      )}
      {children}
      {edit && (
        <>
          <Resizers onDown={(e, edge) => edit.onCardResize(e, card, edge)} label={card.title} />
          {edit.badge?.key === card.key && <span class={`size-badge ${edit.badge.warn ? "warn" : ""}`}>{edit.badge.text}</span>}
        </>
      )}
    </article>
  );
}

