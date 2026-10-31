// Floating panels of the layout editor: widget/card settings with live preview,
// the toolbox (ready-made cards, every widget type, hidden items), the picker
// for a new widget's data source and the shortcut help.
import { useMemo, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink, Widget, WidgetType } from "../lib/types.ts";
import {
  ACCENTS,
  CARD_STYLES,
  COLS,
  DISPLAYS_FOR,
  MAX_CARD_H,
  MAX_WIDGET_H,
  MIN_CARD_W,
  minWidgetH,
  minWidgetW,
  defaultWidgetH,
  defaultWidgetW,
  viewSources,
  moveCardTo,
  moveWidgetsTo,
  removeCustomCard,
  resizeCard,
  resizeWidget,
  updateCard,
  updateWidget,
  type Accent,
  type Display,
  type IconName,
  type ResolvedCard,
  type ResolvedPage,
  type ResolvedWidget,
} from "../lib/layout.ts";
import { WidgetView, placeVars } from "../components/Card.tsx";
import { IconPicker } from "../components/PageTabs.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { CopyButton } from "../components/CodeBlock.tsx";
import { Sheet } from "../components/Overlays.tsx";
import type { CardTemplate } from "./templates.ts";

type Found = { page: ResolvedPage; card: ResolvedCard; widget: ResolvedWidget };

const DISPLAY_LABEL: Record<Display, string> = {
  auto: "Default",
  dial: "Dial",
  bar: "Bar",
  number: "Big number",
  row: "Row",
  big: "Big number",
  line: "Chart",
  sparkline: "Sparkline",
  chip: "Chip",
  lamp: "Lamp",
};

function Stepper({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (n: number) => void }) {
  return (
    <div class="ed-stepper">
      <span class="field-label">{label}</span>
      <div class="stepper-row">
        <button type="button" class="icon-btn sm" aria-label={`${label} smaller`} disabled={value <= min} onClick={() => onChange(value - 1)}>
          −
        </button>
        <span class="mono" aria-live="polite">
          {value}
        </span>
        <button type="button" class="icon-btn sm" aria-label={`${label} larger`} disabled={value >= max} onClick={() => onChange(value + 1)}>
          +
        </button>
      </div>
    </div>
  );
}

function Swatches({ value, onChange, allowNone }: { value?: Accent; onChange: (a?: Accent) => void; allowNone?: boolean }) {
  return (
    <div class="swatches" role="radiogroup" aria-label="Colour">
      {allowNone && (
        <button type="button" role="radio" aria-checked={!value} class={`swatch sw-none ${!value ? "on" : ""}`} aria-label="Default colour" onClick={() => onChange(undefined)} />
      )}
      {ACCENTS.map((a) => (
        <button key={a} type="button" role="radio" aria-checked={value === a} aria-label={a} title={a} class={`swatch sw-${a} ${value === a ? "on" : ""}`} onClick={() => onChange(a)} />
      ))}
    </div>
  );
}

/** A widget rendered on its own at its size (card-width preview frame). */
function LivePreview({ rw, conn }: { rw: ResolvedWidget; conn: MainframeConn }) {
  return (
    <div class="ed-preview" aria-label="Preview">
      <div class="card ed-preview-card">
        <div class="card-body wgrid">
          <div class={`wcell t-${rw.w.type} d-${rw.display} ${rw.color ? `waccent-${rw.color}` : ""}`} style={placeVars({ ...rw.box, x: 0, y: 0 })}>
            <div class="wcontent" inert>
              <WidgetView rw={rw} conn={conn} locked />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export function WidgetSettings({ found, pages, conn, onChange, onRebind, onRemove }: { found: Found; pages: ResolvedPage[]; conn: MainframeConn; onChange: (p: ResolvedPage[], key?: string) => void; onRebind: () => void; onRemove: () => void }) {
  const { widget: rw, card } = found;
  const k = rw.key;
  const [label, setLabel] = useState(rw.label);
  const displays = DISPLAYS_FOR[rw.w.type];
  const cards = pages.flatMap((p) => p.cards.filter((c) => !c.hidden).map((c) => ({ c, p })));
  return (
    <div class="ed-form">
      <LivePreview rw={{ ...rw, label }} conn={conn} />
      <label class="field">
        <span class="field-label">Label</span>
        <input
          class="input"
          value={label}
          maxLength={48}
          placeholder={rw.w.label || rw.w.id}
          onInput={(e) => {
            const v = (e.target as HTMLInputElement).value;
            setLabel(v);
            onChange(updateWidget(pages, k, { label: v || rw.w.label || rw.w.id }), `label:${k}`);
          }}
        />
        <span class="field-hint">
          {rw.w.type} · from {rw.uplink.title || "an uplink"} · id {rw.w.id}
        </span>
      </label>
      {rw.view && (
        <div class="ed-row">
          <button type="button" class="btn btn-small" onClick={onRebind}>
            Change data
          </button>
        </div>
      )}
      {displays && (
        <div class="field">
          <span class="field-label">Display</span>
          <div class="seg-ctl seg-wide" role="radiogroup" aria-label="Display style">
            {displays.map((d) => (
              <button key={d} type="button" role="radio" aria-checked={rw.display === d} class={rw.display === d ? "on" : ""} onClick={() => onChange(updateWidget(pages, k, { display: d }))}>
                {DISPLAY_LABEL[d]}
              </button>
            ))}
          </div>
        </div>
      )}
      <div class="field">
        <span class="field-label">Size (of {COLS} columns)</span>
        <div class="ed-size">
          <Stepper label="Width" value={rw.box.w} min={minWidgetW(rw.w.type)} max={COLS} onChange={(w) => onChange(resizeWidget(pages, k, w, rw.box.h))} />
          <Stepper label="Height" value={rw.box.h} min={minWidgetH(rw.w.type, rw.display)} max={MAX_WIDGET_H} onChange={(h) => onChange(resizeWidget(pages, k, rw.box.w, h))} />
          <button type="button" class="btn btn-small btn-ghost" onClick={() => onChange(resizeWidget(pages, k, defaultWidgetW(rw.w.type), defaultWidgetH(rw.w.type, rw.display)))}>
            Auto
          </button>
        </div>
      </div>
      <div class="field">
        <span class="field-label">Accent</span>
        <Swatches value={rw.color} allowNone onChange={(c) => onChange(updateWidget(pages, k, { color: c }))} />
      </div>
      <div class="field">
        <span class="field-label">Icon</span>
        <IconPicker value={rw.icon} onChange={(i) => onChange(updateWidget(pages, k, { icon: i }))} />
      </div>
      <label class="field">
        <span class="field-label">Card</span>
        <select
          class="select"
          value={card.key}
          onChange={(e) => {
            const to = (e.target as HTMLSelectElement).value;
            const target = cards.find((x) => x.c.key === to)?.c;
            if (target) onChange(moveWidgetsTo(pages, [k], to, 0, target.widgets.filter((w) => !w.hidden).reduce((m, w) => Math.max(m, w.box.y + w.box.h), 0)));
          }}
        >
          {cards.map(({ c, p }) => (
            <option key={c.key} value={c.key}>
              {c.title} ({p.title})
            </option>
          ))}
        </select>
        <span class="field-hint">Or drag the widget onto another card, onto empty space for a new card, or onto a page tab.</span>
      </label>
      <div class="ed-row">
        <button type="button" class="btn btn-small" onClick={() => onChange(updateWidget(pages, k, { hidden: true }))}>
          Hide widget
        </button>
        {rw.view && (
          <button type="button" class="btn btn-small btn-danger-ghost" onClick={onRemove}>
            Delete widget
          </button>
        )}
      </div>
    </div>
  );
}

const SETTINGS_ERRORS: Record<string, string> = {
  unknown_method: "This block does not offer one of the picked methods any more.",
  too_many: "Too many values or charts.",
  invalid_settings: "These settings are not valid.",
  unsupported: "This card has no settings.",
  uplink_offline: "The uplink is offline.",
  not_allowed: "You may not change this dashboard's layout.",
  read_only: "You may not change this dashboard's layout.",
  rate_limited: "Too many changes at once – try again in a moment.",
  timeout: "No answer from the server.",
  disconnected: "Not connected.",
};

/**
 * Wave 4: the Peripheral card's values – which getters (methods without arguments) the card shows, which of them
 * also get a chart, and an optional set…(true/false) method as the card's switch (confirm, PIN by default).
 */
export function PeripheralCardSettings({ conn, uplink }: { conn: MainframeConn; uplink: Uplink }) {
  const info = uplink.cardSettings!;
  const maxValues = info.maxValues ?? 6;
  const maxCharts = info.maxCharts ?? 2;
  const [show, setShow] = useState<string[]>(info.settings.show);
  const [charts, setCharts] = useState<string[]>(info.settings.charts ?? []);
  const [toggle, setToggle] = useState<string>(info.settings.toggle ?? "");
  const [pin, setPin] = useState(info.settings.pin !== false);
  const [state, setState] = useState<{ busy?: boolean; ok?: boolean; error?: string }>({});
  const numeric = new Set(info.getters.filter((g) => g.value === "number").map((g) => g.name));
  const pick = (name: string, on: boolean) => {
    setState({});
    setShow((s) => (on ? (s.includes(name) || s.length >= maxValues ? s : [...s, name]) : s.filter((n) => n !== name)));
    if (!on) setCharts((c) => c.filter((n) => n !== name));
  };
  const chart = (name: string, on: boolean) => {
    setState({});
    setCharts((c) => (on ? (c.includes(name) || c.length >= maxCharts ? c : [...c, name]) : c.filter((n) => n !== name)));
  };
  const save = async () => {
    setState({ busy: true });
    try {
      await conn.cardSettings(uplink.uplinkId, { show, charts: charts.filter((c) => show.includes(c)), toggle: toggle || null, pin });
      setState({ ok: true });
    } catch (e) {
      const code = (e as Error).message;
      setState({ error: SETTINGS_ERRORS[code] ?? `Not saved (${code}).` });
    }
  };
  const canEdit = conn.can("layout.edit");
  return (
    <div class="field ed-peripheral">
      <span class="field-label">Values ({show.length} of {maxValues})</span>
      <span class="field-hint">
        Methods of this {info.type ?? "peripheral"} that take no arguments. Only the ones you pick are read, every few seconds.
      </span>
      {info.getters.length === 0 && <p class="muted small">This block offers no such methods.</p>}
      <ul class="ed-methods" role="list">
        {info.getters.map((g) => {
          const on = show.includes(g.name);
          return (
            <li key={g.name}>
              <label class="check-row">
                <input type="checkbox" checked={on} disabled={!canEdit || (!on && show.length >= maxValues)} onChange={(e) => pick(g.name, (e.target as HTMLInputElement).checked)} />
                <span>
                  {g.caption} <code class="mono muted small">{g.name}()</code>
                </span>
              </label>
              {on && numeric.has(g.name) && (
                <label class="check-row sub">
                  <input
                    type="checkbox"
                    checked={charts.includes(g.name)}
                    disabled={!canEdit || (!charts.includes(g.name) && charts.length >= maxCharts)}
                    onChange={(e) => chart(g.name, (e.target as HTMLInputElement).checked)}
                  />
                  Also as a chart
                </label>
              )}
            </li>
          );
        })}
      </ul>
      {(info.unknown ?? 0) > 0 && (
        <p class="muted small">
          {info.unknown} method{info.unknown === 1 ? " does" : "s do"} not say which arguments {info.unknown === 1 ? "it takes" : "they take"} and {info.unknown === 1 ? "is" : "are"} not offered – a Lua program can still call {info.unknown === 1 ? "it" : "them"}.
        </p>
      )}
      {info.setters.length > 0 && (
        <>
          <label class="field">
            <span class="field-label">Switch</span>
            <select class="select" value={toggle} disabled={!canEdit} onChange={(e) => setToggle((e.target as HTMLSelectElement).value)}>
              <option value="">No switch</option>
              {info.setters.map((s) => (
                <option key={s.name} value={s.name}>
                  {s.caption} – {s.name}(true/false)
                </option>
              ))}
            </select>
            <span class="field-hint">Operators can switch it on the card (with a confirm step).</span>
          </label>
          {toggle && (
            <label class="check-row">
              <input type="checkbox" checked={pin} disabled={!canEdit} onChange={(e) => setPin((e.target as HTMLInputElement).checked)} />
              Ask for the dashboard PIN
            </label>
          )}
        </>
      )}
      <div class="ed-row">
        <button type="button" class="btn btn-small" disabled={!canEdit || state.busy} onClick={save}>
          {state.busy ? "Saving…" : "Save values"}
        </button>
      </div>
      {state.ok && <p class="muted small" role="status">Saved. The card shows the new values when you leave the editor.</p>}
      {state.error && (
        <p class="form-error" role="alert">
          {state.error}
        </p>
      )}
    </div>
  );
}

export function CardSettings({
  card,
  pages,
  onChange,
  onPage,
  conn,
  uplink,
  onWidget,
  onAddWidget,
}: {
  card: ResolvedCard;
  pages: ResolvedPage[];
  onChange: (p: ResolvedPage[], key?: string) => void;
  onPage: (id: string) => void;
  conn?: MainframeConn;
  /** the card's uplink as the server sends it now (the editor keeps the state it was opened with) */
  uplink?: Uplink;
  onWidget: (key: string) => void;
  onAddWidget: (kind: WidgetKind) => void;
}) {
  const k = card.key;
  const [title, setTitle] = useState(card.title);
  const live = uplink ?? card.uplink;
  return (
    <div class="ed-form">
      <label class="field">
        <span class="field-label">Title</span>
        <input
          class="input"
          value={title}
          maxLength={48}
          placeholder={card.defaultTitle}
          onInput={(e) => {
            const v = (e.target as HTMLInputElement).value;
            setTitle(v);
            onChange(updateCard(pages, k, { title: v || card.defaultTitle }), `title:${k}`);
          }}
        />
        <span class="field-hint">{card.custom ? "Your own card – it can hold widgets from any computer." : `From ${card.uplink.title || "an uplink"}.`}</span>
      </label>
      <div class="field">
        <span class="field-label">Size</span>
        <div class="seg-ctl seg-wide" role="radiogroup" aria-label="Card size">
          {SIZES.map((z) => (
            <button key={z.name} type="button" role="radio" aria-checked={card.box.w === z.w} class={card.box.w === z.w ? "on" : ""} onClick={() => onChange(resizeCard(pages, k, z.w, Math.max(z.h, card.box.h)))}>
              {z.name}
            </button>
          ))}
        </div>
        <div class="ed-size">
          <Stepper label="Width" value={card.box.w} min={MIN_CARD_W} max={COLS} onChange={(w) => onChange(resizeCard(pages, k, w, card.box.h))} />
          <Stepper label="Height" value={card.box.h} min={3} max={MAX_CARD_H} onChange={(h) => onChange(resizeCard(pages, k, card.box.w, h))} />
        </div>
      </div>
      <div class="field">
        <span class="field-label">Widgets</span>
        {card.widgets.some((w) => !w.hidden) && (
          <ul class="ed-wlist">
            {card.widgets
              .filter((w) => !w.hidden)
              .map((w) => (
                <li key={w.key}>
                  <button type="button" class="ed-wrow" onClick={() => onWidget(w.key)}>
                    <span>{w.label}</span>
                    <span aria-hidden="true">✎</span>
                  </button>
                </li>
              ))}
          </ul>
        )}
        <div class="ed-kinds" role="group" aria-label="Add a widget">
          {WIDGET_KINDS.slice(0, 8).map((kind) => (
            <button key={kind.id} type="button" class="chip" onClick={() => onAddWidget(kind)}>
              + {kind.name}
            </button>
          ))}
        </div>
      </div>
      <div class="field">
        <span class="field-label">Style</span>
        <div class="seg-ctl seg-wide" role="radiogroup" aria-label="Card style">
          {CARD_STYLES.map((s) => (
            <button key={s} type="button" role="radio" aria-checked={card.style === s} class={card.style === s ? "on" : ""} onClick={() => onChange(updateCard(pages, k, { style: s }))}>
              {s === "comfortable" ? "Comfortable" : "Compact"}
            </button>
          ))}
        </div>
      </div>
      <div class="field">
        <span class="field-label">Accent</span>
        <Swatches value={card.color} onChange={(c) => onChange(updateCard(pages, k, { color: c ?? "gold" }))} />
      </div>
      <div class="field">
        <span class="field-label">Icon</span>
        <IconPicker value={card.icon} onChange={(i) => onChange(updateCard(pages, k, { icon: i }))} />
      </div>
      <label class="field">
        <span class="field-label">Page</span>
        <select
          class="select"
          value={card.page}
          onChange={(e) => {
            const to = (e.target as HTMLSelectElement).value;
            onChange(moveCardTo(pages, k, to));
            onPage(to);
          }}
        >
          {pages.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </label>
      <div class="ed-row">
        <button type="button" class="btn btn-small" onClick={() => onChange(updateCard(pages, k, { hidden: true }))}>
          Hide card
        </button>
        {card.custom && (
          <button type="button" class="btn btn-small btn-danger-ghost" onClick={() => onChange(removeCustomCard(pages, k))}>
            Delete card
          </button>
        )}
      </div>
      {card.custom && <p class="muted small">Deleting your own card sends its widgets back to their computers' cards.</p>}
      {!card.custom && conn && live.cardSettings?.kind === "peripheral" && <PeripheralCardSettings key={live.uplinkId} conn={conn} uplink={live} />}
    </div>
  );
}

// ------------------------------------------------------------------ gallery
const LUA: Record<WidgetType, string> = {
  gauge: 'cloud.gauge("temp", "Temperature", { min = 0, max = 1200, unit = "K", warn = 900, crit = 1100 })',
  label: 'cloud.label("status", "Status", { value = "Running" })',
  toggle: 'cloud.toggle("power", "Power", { value = true })',
  slider: 'cloud.slider("speed", "Speed", { min = 0, max = 100 })',
  button: 'cloud.button("start", "Start", { color = "green" })',
  chart: 'cloud.chart("load", "Load", { unit = "kFE/t", points = 60 })',
  progress: 'cloud.progress("charge", "Charge", { max = 500, unit = "MFE" })',
  indicator: 'cloud.indicator("fault", "Fault", { onColor = "red" })',
  select: 'cloud.select("mode", "Mode", { options = { "eco", "boost" } })',
  number: 'cloud.number("limit", "Limit", { min = 0, max = 500, step = 10 })',
  text: 'cloud.text("sign", "Sign text", { maxLength = 32 })',
  buttons: 'cloud.buttons("prio", "Priority", { options = { "Low", "High" } })',
  item_search: "-- added by an ME/RS Cloud Bridge card",
  watchlist: "-- added by an ME/RS Cloud Bridge card",
  jobs: "-- added by an ME/RS Cloud Bridge card",
  redstone_sides: "-- the automatic Computer card",
  sensor_log: "-- an Advanced Cloud Sensor's card",
  canvas: 'local map = dash:canvas("map", "Base map", { width = 160, height = 90 })',
};
const DEMO: Widget[] = [
  { id: "gauge", type: "gauge", label: "Gauge", value: 72, opts: { min: 0, max: 100, unit: "%", warn: 80 } },
  { id: "label", type: "label", label: "Label", value: "Running" },
  { id: "toggle", type: "toggle", label: "Toggle", value: true },
  { id: "slider", type: "slider", label: "Slider", value: 40, opts: { min: 0, max: 100 } },
  { id: "button", type: "button", label: "Button", opts: { color: "green" } },
  { id: "chart", type: "chart", label: "Chart", value: 14, history: [3, 6, 5, 9, 8, 12, 10, 14] },
  { id: "progress", type: "progress", label: "Progress", value: 312, opts: { max: 500, unit: "MFE" } },
  { id: "indicator", type: "indicator", label: "Indicator", value: true },
  { id: "select", type: "select", label: "Select", value: "eco", opts: { options: ["eco", "boost"] } },
  { id: "number", type: "number", label: "Number", value: 250, opts: { min: 0, max: 500, step: 10 } },
  { id: "text", type: "text", label: "Text", value: "Hello" },
  { id: "buttons", type: "buttons", label: "Buttons", value: "Low", opts: { options: ["Low", "High"] } },
];
const DEMO_UPLINK: Uplink = { uplinkId: "__demo", title: "Demo", online: true, widgets: DEMO };

const SIZES = [
  { name: "S", w: 3, h: 6 },
  { name: "M", w: 4, h: 9 },
  { name: "L", w: 6, h: 11 },
  { name: "XL", w: 8, h: 14 },
];

export interface WidgetKind {
  id: string;
  type: WidgetType;
  display: Display;
  name: string;
  hint: string;
  icon: IconName;
}
export const WIDGET_KINDS: WidgetKind[] = [
  { id: "stat", type: "gauge", display: "number", name: "Number", hint: "One value, big and clear", icon: "chart" },
  { id: "bar", type: "gauge", display: "bar", name: "Bar", hint: "How full something is", icon: "drop" },
  { id: "dial", type: "gauge", display: "dial", name: "Dial", hint: "A value on a scale", icon: "gear" },
  { id: "chart", type: "chart", display: "line", name: "Chart", hint: "Values over time", icon: "chart" },
  { id: "toggle", type: "toggle", display: "auto", name: "Switch", hint: "On and off", icon: "lever" },
  { id: "button", type: "button", display: "auto", name: "Button", hint: "Runs an action", icon: "button" },
  { id: "lamp", type: "indicator", display: "lamp", name: "Lamp", hint: "Shows a state", icon: "lamp" },
  { id: "label", type: "label", display: "row", name: "Label", hint: "Any value as text", icon: "eye" },
  { id: "slider", type: "slider", display: "auto", name: "Slider", hint: "Set a value", icon: "gear" },
  { id: "number", type: "number", display: "auto", name: "Number input", hint: "Type a value", icon: "gear" },
  { id: "select", type: "select", display: "auto", name: "Choice", hint: "Pick from a list", icon: "gear" },
  { id: "buttons", type: "buttons", display: "auto", name: "Button row", hint: "One of several options", icon: "button" },
  { id: "text", type: "text", display: "auto", name: "Text field", hint: "Send a text", icon: "gear" },
  { id: "search", type: "item_search", display: "auto", name: "Item search", hint: "Search the storage network", icon: "box" },
  { id: "watch", type: "watchlist", display: "auto", name: "Watchlist", hint: "Items you keep an eye on", icon: "box" },
  { id: "jobs", type: "jobs", display: "auto", name: "Crafting jobs", hint: "What is being crafted", icon: "box" },
  { id: "sides", type: "redstone_sides", display: "auto", name: "Redstone sides", hint: "All six sides of a computer", icon: "lever" },
  { id: "log", type: "sensor_log", display: "auto", name: "Sensor log", hint: "What a sensor saw", icon: "sensor" },
];

/** What a new widget shows while it is dragged in, before it has a data source. */
export function draftWidget(kind: WidgetKind): Widget {
  const demo = DEMO.find((w) => w.type === kind.type);
  return demo ? { ...demo, id: "draft", label: kind.name } : { id: "draft", type: "label", label: kind.name, value: "Pick its data" };
}

function Row({ icon, title, hint, onDown, onClick }: { icon: IconName; title: string; hint: string; onDown: (e: PointerEvent) => void; onClick: () => void }) {
  return (
    <li class="ed-tool" tabIndex={0} role="button" onPointerDown={onDown} onClick={onClick} onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), onClick())}>
      <PixelIcon name={icon} size={18} />
      <span class="ed-tool-text">
        <b>{title}</b>
        <small>{hint}</small>
      </span>
    </li>
  );
}

type TrayTab = "cards" | "widgets" | "hidden";

export function Toolbox(props: {
  pages: ResolvedPage[];
  page: string;
  uplinks: Uplink[];
  conn: MainframeConn;
  templates: CardTemplate[];
  /** what a card would change to on this page, and its key */
  cardFromTemplate: (t: CardTemplate) => { pages: ResolvedPage[]; key: string };
  emptyCard: () => { pages: ResolvedPage[]; key: string };
  onAdd: (p: ResolvedPage[]) => void;
  onDragStart: (e: PointerEvent, kind: "card" | "widget", key: string, el: HTMLElement, base: ResolvedPage[]) => void;
  onKindDown: (e: PointerEvent, kind: WidgetKind, el: HTMLElement) => void;
  onKind: (kind: WidgetKind) => void;
  onNewPage: () => void;
}) {
  const { pages } = props;
  const hiddenCards = pages.flatMap((p) => p.cards.filter((c) => c.hidden).map((c) => ({ c, p })));
  const hiddenWidgets = pages.flatMap((p) => p.cards.flatMap((c) => c.widgets.filter((w) => w.hidden).map((w) => ({ w, c }))));
  const others = pages.filter((p) => p.id !== props.page).flatMap((p) => p.cards.filter((c) => !c.hidden).map((c) => ({ c, p })));
  const [tab, setTab] = useState<TrayTab>("cards");
  const hiddenCount = hiddenCards.length + hiddenWidgets.length;
  const drag = (make: () => { pages: ResolvedPage[]; key: string }) => (e: PointerEvent) => {
    const r = make();
    props.onDragStart(e, "card", r.key, e.currentTarget as HTMLElement, r.pages);
  };
  const tabs: [TrayTab, string][] = [
    ["cards", "Cards"],
    ["widgets", "Widgets"],
    ["hidden", `Hidden${hiddenCount ? ` (${hiddenCount})` : ""}`],
  ];
  return (
    <div class="ed-gallery">
      <div class="nd-tabs" role="tablist" aria-label="Toolbox">
        {tabs.map(([id, label]) => (
          <button key={id} type="button" role="tab" aria-selected={tab === id} class={`nd-tab ${tab === id ? "on" : ""}`} onClick={() => setTab(id)}>
            {label}
          </button>
        ))}
      </div>
      {tab === "cards" && (
        <>
          <ul class="ed-items">
            <Row icon="box" title="Empty card" hint="Fill it with widgets yourself" onDown={drag(props.emptyCard)} onClick={() => props.onAdd(props.emptyCard().pages)} />
            {props.templates.map((t) => (
              <Row key={t.id} icon={t.icon} title={t.title} hint={t.hint} onDown={drag(() => props.cardFromTemplate(t))} onClick={() => props.onAdd(props.cardFromTemplate(t).pages)} />
            ))}
          </ul>
          {others.length > 0 && <h4 class="ed-h">On other pages</h4>}
          <ul class="ed-items">
            {others.map(({ c, p }) => (
              <Row key={c.key} icon={c.icon ?? "box"} title={c.title} hint={p.title} onDown={drag(() => ({ pages: moveCardTo(pages, c.key, props.page), key: c.key }))} onClick={() => props.onAdd(moveCardTo(pages, c.key, props.page))} />
            ))}
          </ul>
          <button type="button" class="btn btn-small" onClick={props.onNewPage}>
            + Page
          </button>
        </>
      )}
      {tab === "widgets" && (
        <>
          <p class="muted small">Drag one onto a card or onto free space, then pick its data.</p>
          <ul class="ed-items">
            {WIDGET_KINDS.map((k) => (
              <Row key={k.id} icon={k.icon} title={k.name} hint={k.hint} onDown={(e) => props.onKindDown(e, k, e.currentTarget as HTMLElement)} onClick={() => props.onKind(k)} />
            ))}
          </ul>
        </>
      )}
      {tab === "hidden" && (
        <>
          {!hiddenCount && <p class="muted small">Nothing is hidden. Drag a card or widget here to hide it.</p>}
          <ul class="ed-items">
            {hiddenCards.map(({ c, p }) => {
              const show = () => ({ pages: moveCardTo(updateCard(pages, c.key, { hidden: false }), c.key, props.page), key: c.key });
              return <Row key={c.key} icon={c.icon ?? "box"} title={c.title} hint={`${c.widgets.length} widgets · ${p.title}`} onDown={drag(show)} onClick={() => props.onAdd(show().pages)} />;
            })}
            {hiddenWidgets.map(({ w, c }) => (
              <Row
                key={w.key}
                icon={w.icon ?? "gear"}
                title={w.label}
                hint={c.title}
                onDown={(e) => props.onDragStart(e, "widget", w.key, e.currentTarget as HTMLElement, pages)}
                onClick={() => props.onAdd(updateWidget(pages, w.key, { hidden: false }))}
              />
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

const shown = (w: Widget) => {
  const v = w.type === "chart" ? w.history?.at(-1) : w.value ?? w.opts?.value;
  if (typeof v === "boolean") return v ? "ON" : "OFF";
  if (typeof v === "number") return `${Number.isInteger(v) ? v : v.toFixed(1)}${w.opts?.unit ? ` ${w.opts.unit}` : ""}`;
  return typeof v === "string" ? v : "";
};

/** "Where does the data come from?": every widget of the base this kind can show, with its live value. */
export function SourcePicker({ kind, uplinks, onPick, onClose }: { kind: WidgetKind; uplinks: Uplink[]; onPick: (uplink: Uplink, widget: Widget) => void; onClose: () => void }) {
  const [q, setQ] = useState("");
  const all = useMemo(() => viewSources(uplinks, kind.type), [uplinks, kind]);
  const needle = q.trim().toLowerCase();
  const list = needle ? all.filter((s) => `${s.uplink.title} ${s.widget.label ?? s.widget.id}`.toLowerCase().includes(needle)) : all;
  const groups = [...new Set(list.map((s) => s.uplink))];
  const lua = LUA[kind.type];
  return (
    <Sheet title={`${kind.name}: where does the data come from?`} onClose={onClose}>
      {all.length > 6 && <input class="input" type="search" placeholder="Search…" value={q} maxLength={64} aria-label="Search data" onInput={(e) => setQ((e.target as HTMLInputElement).value)} />}
      {!all.length && (
        <div class="ed-form">
          <p class="muted small">Nothing in your base offers this yet. It appears here once a device or a program publishes it.</p>
          <span class="ed-item-foot">
            <code class="ed-lua">{lua}</code>
            <CopyButton text={lua} />
          </span>
        </div>
      )}
      {all.length > 0 && !list.length && <p class="muted small">No match.</p>}
      {groups.map((u) => (
        <div key={u.uplinkId} class="ed-src-group">
          <h4 class="ed-h">{u.title || "Uplink"}</h4>
          <ul class="ed-items">
            {list
              .filter((s) => s.uplink === u)
              .map((s) => (
                <li key={s.widget.id}>
                  <button type="button" class="ed-src" onClick={() => onPick(s.uplink, s.widget)}>
                    <span>{s.widget.label ?? s.widget.id}</span>
                    <span class="mono muted">{shown(s.widget)}</span>
                  </button>
                </li>
              ))}
          </ul>
        </div>
      ))}
      {kind.type === "chart" && all.some((s) => s.widget.type !== "chart") && <p class="muted small">Values without a history start recording while the dashboard is open.</p>}
    </Sheet>
  );
}

export function ShortcutHelp() {
  const rows: [string, string][] = [
    ["Drag a card header / a widget", "Move it (others make room)"],
    ["Drag onto empty space", "A widget gets its own new card"],
    ["Drag onto the toolbox", "Hide it"],
    ["Drag onto a page tab", "Move it to that page (hover to open the page)"],
    ["Drag an edge or corner", "Resize – the badge shows the size"],
    ["Shift-click widgets", "Select several, then drag one to move all"],
    ["Touch: press and hold", "Pick up (short vibration), then drag"],
    ["Tab", "Focus cards and widgets"],
    ["Space", "Pick up / drop the focused item"],
    ["Arrow keys", "Move the picked-up item one cell"],
    ["Shift + arrow keys", "Resize it"],
    ["Page Up / Page Down", "Move a widget to the previous / next card"],
    ["Esc", "Cancel the move / clear the selection"],
    ["Enter", "Settings of the focused item"],
    ["Delete", "Hide the focused item"],
    ["Ctrl/⌘ + Z, Ctrl/⌘ + Shift + Z", "Undo, redo"],
    ["Ctrl/⌘ + S", "Save"],
    ["A", "Open the toolbox"],
    ["?", "This help"],
  ];
  return (
    <dl class="facts ed-keys">
      {rows.map(([k, v]) => (
        <>
          <dt key={`k${k}`}>
            <kbd>{k}</kbd>
          </dt>
          <dd key={`v${k}`}>{v}</dd>
        </>
      ))}
    </dl>
  );
}
