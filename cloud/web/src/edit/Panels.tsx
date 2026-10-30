// Side panels of the layout editor: widget/card settings with live preview,
// the Add gallery (hidden items with live previews, every widget type with its
// Lua one-liner, new card/page) and the shortcut help.
import { useState } from "preact/hooks";
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
  minWidgetW,
  defaultWidgetH,
  defaultWidgetW,
  moveCardTo,
  moveWidgetsTo,
  removeCustomCard,
  resizeCard,
  resizeWidget,
  updateCard,
  updateWidget,
  type Accent,
  type Display,
  type ResolvedCard,
  type ResolvedPage,
  type ResolvedWidget,
} from "../lib/layout.ts";
import { WidgetView, placeVars } from "../components/Card.tsx";
import { IconPicker } from "../components/PageTabs.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { CopyButton } from "../components/CodeBlock.tsx";

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

export function WidgetSettings({ found, pages, conn, onChange }: { found: Found; pages: ResolvedPage[]; conn: MainframeConn; onChange: (p: ResolvedPage[], key?: string) => void }) {
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
          <Stepper label="Height" value={rw.box.h} min={1} max={MAX_WIDGET_H} onChange={(h) => onChange(resizeWidget(pages, k, rw.box.w, h))} />
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
      </div>
    </div>
  );
}

export function CardSettings({ card, pages, onChange, onPage }: { card: ResolvedCard; pages: ResolvedPage[]; onChange: (p: ResolvedPage[], key?: string) => void; onPage: (id: string) => void }) {
  const k = card.key;
  const [title, setTitle] = useState(card.title);
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
      <div class="field">
        <span class="field-label">Size (of {COLS} columns)</span>
        <div class="ed-size">
          <Stepper label="Width" value={card.box.w} min={MIN_CARD_W} max={COLS} onChange={(w) => onChange(resizeCard(pages, k, w, card.box.h))} />
          <Stepper label="Height" value={card.box.h} min={3} max={MAX_CARD_H} onChange={(h) => onChange(resizeCard(pages, k, card.box.w, h))} />
        </div>
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

export function AddGallery(props: {
  pages: ResolvedPage[];
  page: string;
  conn: MainframeConn;
  onAdd: (p: ResolvedPage[]) => void;
  onDragStart: (e: PointerEvent, kind: "card" | "widget", key: string, el: HTMLElement, base: ResolvedPage[]) => void;
  onNewCard: () => void;
  onNewPage: () => void;
}) {
  const { pages } = props;
  const hiddenCards = pages.flatMap((p) => p.cards.filter((c) => c.hidden).map((c) => ({ c, p })));
  const hiddenWidgets = pages.flatMap((p) => p.cards.flatMap((c) => c.widgets.filter((w) => w.hidden).map((w) => ({ w, c }))));
  const [tab, setTab] = useState<"hidden" | "types">(hiddenCards.length + hiddenWidgets.length ? "hidden" : "types");
  return (
    <div class="ed-gallery">
      <div class="ed-row">
        <button type="button" class="btn btn-small" onClick={props.onNewCard}>
          + Empty card
        </button>
        <button type="button" class="btn btn-small" onClick={props.onNewPage}>
          + Page
        </button>
      </div>
      <div class="nd-tabs" role="tablist" aria-label="Add">
        <button type="button" role="tab" aria-selected={tab === "hidden"} class={`nd-tab ${tab === "hidden" ? "on" : ""}`} onClick={() => setTab("hidden")}>
          Hidden ({hiddenCards.length + hiddenWidgets.length})
        </button>
        <button type="button" role="tab" aria-selected={tab === "types"} class={`nd-tab ${tab === "types" ? "on" : ""}`} onClick={() => setTab("types")}>
          All widget types
        </button>
      </div>
      {tab === "hidden" && (
        <>
          {!hiddenCards.length && !hiddenWidgets.length && <p class="muted small">Nothing is hidden. Hide cards or widgets with × – they wait here until you drag them back.</p>}
          {hiddenCards.length > 0 && <h4 class="ed-h">Cards</h4>}
          <ul class="ed-items">
            {hiddenCards.map(({ c, p }) => (
              <li
                key={c.key}
                class="ed-item ed-item-card"
                onPointerDown={(e) => {
                  if ((e.target as HTMLElement).closest("button")) return;
                  const base = updateCard(pages, c.key, { hidden: false });
                  props.onDragStart(e, "card", c.key, e.currentTarget as HTMLElement, moveCardTo(base, c.key, props.page));
                }}
              >
                <span class="ed-item-head">
                  <PixelIcon name={c.icon ?? "box"} size={14} />
                  <b>{c.title}</b>
                  <span class="muted small">
                    {c.widgets.length} widgets · {p.title}
                  </span>
                </span>
                <button type="button" class="btn btn-small" onClick={() => props.onAdd(moveCardTo(updateCard(pages, c.key, { hidden: false }), c.key, props.page))}>
                  Show here
                </button>
              </li>
            ))}
          </ul>
          {hiddenWidgets.length > 0 && <h4 class="ed-h">Widgets</h4>}
          <ul class="ed-items">
            {hiddenWidgets.map(({ w, c }) => (
              <li key={w.key} class="ed-item" onPointerDown={(e) => !(e.target as HTMLElement).closest("button") && props.onDragStart(e, "widget", w.key, e.currentTarget as HTMLElement, pages)}>
                <div class="ed-item-prev wgrid">
                  <div class={`wcell t-${w.w.type} d-${w.display}`} style={placeVars({ ...w.box, x: 0, y: 0, w: 12 })}>
                    <div class="wcontent" inert>
                      <WidgetView rw={w} conn={props.conn} locked />
                    </div>
                  </div>
                </div>
                <span class="ed-item-foot">
                  <span class="muted small">
                    {w.label} · {c.title}
                  </span>
                  <button type="button" class="btn btn-small" onClick={() => props.onAdd(updateWidget(pages, w.key, { hidden: false }))}>
                    Show
                  </button>
                </span>
              </li>
            ))}
          </ul>
          {hiddenWidgets.length > 0 && <p class="muted small">Drag a widget onto a card, onto empty space (new card) or onto a page tab – or press Show.</p>}
        </>
      )}
      {tab === "types" && (
        <>
          <p class="muted small">Widgets come from the Lua program on a computer. Every type, live – add one line to your script and it appears on its card.</p>
          <ul class="ed-items">
            {DEMO.map((w) => {
              const rw: ResolvedWidget = { key: `__demo/${w.id}`, w, uplink: DEMO_UPLINK, home: "", label: w.label ?? w.id, box: { x: 0, y: 0, w: 12, h: defaultWidgetH(w.type) }, hidden: false, display: DISPLAYS_FOR[w.type]?.[0] ?? "auto" };
              return (
                <li key={w.id} class="ed-item">
                  <div class="ed-item-prev wgrid">
                    <div class={`wcell t-${w.type}`} style={placeVars(rw.box)}>
                      <div class="wcontent" inert>
                        <WidgetView rw={rw} conn={props.conn} locked />
                      </div>
                    </div>
                  </div>
                  <span class="ed-item-foot">
                    <code class="ed-lua">{LUA[w.type]}</code>
                    <CopyButton text={LUA[w.type]} />
                  </span>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

export function ShortcutHelp() {
  const rows: [string, string][] = [
    ["Drag a card header / a widget", "Move it (others make room)"],
    ["Drag onto empty space", "A widget gets its own new card"],
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
    ["A", "Open Add"],
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
