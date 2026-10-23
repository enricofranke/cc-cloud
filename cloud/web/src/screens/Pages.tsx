// Pages/tabs + card grid, and the layout editor (edit mode).
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { ComponentType } from "preact";
import type { MainframeConn } from "../lib/conn.ts";
import {
  ACCENTS,
  ICON_NAMES,
  MAX_LAYOUT_BYTES,
  MAX_PAGES,
  addPage,
  deletePage,
  layoutSize,
  moveCard,
  movePage,
  moveWidget,
  resolveLayout,
  snapshotLayout,
  updateCard,
  updatePage,
  updateWidget,
  type Accent,
  type IconName,
  type ResolvedCard,
  type ResolvedPage,
  type ResolvedWidget,
} from "../lib/layout.ts";
import { pushToast } from "../lib/ui.ts";
import { adaptComputerCard } from "../lib/redstone.ts";
import { Card, EditCard } from "../components/Card.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { IconPlus } from "../components/Icons.tsx";

export const NETWORK_PAGE = "__network";

/** The network page is its own lazily loaded chunk. */
function NetworkLoader({ conn, onOpenCard }: { conn: MainframeConn; onOpenCard: (uplinkId: string) => void }) {
  const [Comp, setComp] = useState<ComponentType<{ conn: MainframeConn; onOpenCard?: (id: string) => void }> | null>(null);
  useEffect(() => {
    import("../network/Network.tsx").then((m) => setComp(() => m.default));
  }, []);
  return Comp ? <Comp conn={conn} onOpenCard={onOpenCard} /> : <div class="card skeleton net-skeleton" />;
}

function useIsPhone() {
  const mq = useMemo(() => matchMedia("(max-width: 719px)"), []);
  const [phone, setPhone] = useState(mq.matches);
  useEffect(() => {
    const h = (e: MediaQueryListEvent) => setPhone(e.matches);
    mq.addEventListener("change", h);
    return () => mq.removeEventListener("change", h);
  }, [mq]);
  return phone;
}

const pageKey = (id: string) => `cccloud.page.${id}`;
const SAVE_ERRORS: Record<string, string> = {
  read_only: "This device is read-only and cannot change the layout.",
  too_large: "The layout is too large (max 64 KB).",
  invalid_layout: "The server rejected the layout as invalid.",
  rate_limited: "Saving too often – try again in a moment.",
  disconnected: "Not connected – your changes are kept, try again when the connection is back.",
  timeout: "The server did not answer. Try again.",
};

export function Pages(props: { conn: MainframeConn; locked: boolean; dim: boolean; editing: boolean; onExitEdit: () => void }) {
  const { conn } = props;
  const isPhone = useIsPhone();
  const uplinks = conn.uplinks.value.map(adaptComputerCard);
  const view = resolveLayout(uplinks, conn.layout.value);
  const [draft, setDraft] = useState<ResolvedPage[] | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [current, setCurrent] = useState<string>(() => {
    try {
      return sessionStorage.getItem(pageKey(conn.info.value?.id ?? "")) ?? "";
    } catch {
      return "";
    }
  });

  // entering edit mode snapshots the current view (including hidden items)
  useEffect(() => {
    if (props.editing) {
      setDraft(resolveLayout(conn.uplinks.value.map(adaptComputerCard), conn.layout.value, { includeHidden: true }));
      setSaveError(null);
    } else setDraft(null);
  }, [props.editing]);

  const pages = draft ?? view;
  // the Network page is always there (not part of the editable layout)
  const showNetwork = !draft && conn.network.value !== null;
  const onNetwork = showNetwork && current === NETWORK_PAGE;
  const page = pages.find((p) => p.id === current) ?? pages[0];
  const select = (id: string) => {
    setCurrent(id);
    try {
      sessionStorage.setItem(pageKey(conn.info.value?.id ?? ""), id);
    } catch {}
  };
  const showTabs = pages.length > 1 || !!draft || showNetwork;
  const openCard = (uplinkId: string) => {
    const target = pages.find((p) => p.cards.some((c) => c.uplink.uplinkId === uplinkId));
    if (!target) return;
    select(target.id);
    const key = target.cards.find((c) => c.uplink.uplinkId === uplinkId)!.key;
    setTimeout(() => {
      const el = document.querySelector(`[data-card="${CSS.escape(key)}"]`);
      el?.scrollIntoView({ block: "start", behavior: "smooth" });
      el?.classList.add("flash-card");
      setTimeout(() => el?.classList.remove("flash-card"), 1600);
    }, 60);
  };

  const save = async () => {
    if (!draft) return;
    const doc = snapshotLayout(draft);
    if (layoutSize(doc) > MAX_LAYOUT_BYTES) return setSaveError(SAVE_ERRORS.too_large);
    setSaving(true);
    setSaveError(null);
    try {
      await conn.saveLayout(doc);
      pushToast({ level: "info", title: "Layout saved", text: "All devices now see the new layout." });
      props.onExitEdit();
    } catch (e) {
      setSaveError(SAVE_ERRORS[(e as Error).message] ?? `Could not save (${(e as Error).message}).`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      {draft && <EditBar saving={saving} error={saveError} onCancel={props.onExitEdit} onSave={save} />}
      {showTabs && (
        <Tabs
          pages={pages}
          network={showNetwork}
          current={onNetwork ? NETWORK_PAGE : page.id}
          isPhone={isPhone}
          editing={!!draft}
          onSelect={select}
          onAdd={(title, icon) => {
            if (!draft) return;
            const r = addPage(draft, title, icon);
            setDraft(r.pages);
            select(r.id);
          }}
          onChange={(next) => draft && setDraft(next)}
          onDropCard={(cardKey, pageId) => draft && setDraft(moveCard(draft, cardKey, pageId, Number.MAX_SAFE_INTEGER))}
        />
      )}
      {onNetwork ? (
        <NetworkLoader conn={conn} onOpenCard={openCard} />
      ) : draft ? (
        <EditGrid pages={draft} page={page} isPhone={isPhone} onChange={setDraft} />
      ) : page.cards.length === 0 ? (
        <div class="empty">
          <h2 class="px-title">Nothing on this page</h2>
          <p>Open the layout editor (pencil) to move cards here, or put widgets on it from Lua with opts.page.</p>
        </div>
      ) : (
        <div class={`grid ${props.dim ? "grid-dim" : ""}`}>
          {page.cards.map((c) => (
            <Card key={c.key} card={c} conn={conn} locked={props.locked} />
          ))}
        </div>
      )}
      {showTabs && isPhone && <div class="tabbar-spacer" />}
    </>
  );
}

// ---------------------------------------------------------------------- tabs
function Tabs(props: {
  pages: ResolvedPage[];
  network?: boolean;
  current: string;
  isPhone: boolean;
  editing: boolean;
  onSelect: (id: string) => void;
  onAdd: (title: string, icon?: IconName) => void;
  onChange: (pages: ResolvedPage[]) => void;
  onDropCard: (cardKey: string, pageId: string) => void;
}) {
  const [pageSheet, setPageSheet] = useState<ResolvedPage | "new" | null>(null);
  return (
    <>
      <nav class={props.isPhone ? "tabbar" : "toptabs"} aria-label="Pages">
        {props.pages.map((p) => (
          <button
            key={p.id}
            type="button"
            class={`tab ${p.id === props.current ? "on" : ""}`}
            aria-current={p.id === props.current ? "page" : undefined}
            onClick={() => (props.editing && p.id === props.current ? setPageSheet(p) : props.onSelect(p.id))}
            onDragOver={props.editing ? (e) => e.preventDefault() : undefined}
            onDrop={
              props.editing
                ? (e) => {
                    const key = e.dataTransfer?.getData("text/x-cccloud-card");
                    if (key) props.onDropCard(key, p.id);
                  }
                : undefined
            }
          >
            <PixelIcon name={p.icon ?? "box"} size={props.isPhone ? 18 : 14} />
            <span class="tab-text">{p.title}</span>
            {props.editing && p.id === props.current && <span class="tab-edit">✎</span>}
          </button>
        ))}
        {props.network && (
          <button
            type="button"
            class={`tab tab-network ${props.current === NETWORK_PAGE ? "on" : ""}`}
            aria-current={props.current === NETWORK_PAGE ? "page" : undefined}
            onClick={() => props.onSelect(NETWORK_PAGE)}
          >
            <PixelIcon name="network" size={props.isPhone ? 18 : 14} />
            <span class="tab-text">Network</span>
          </button>
        )}
        {props.editing && props.pages.length < MAX_PAGES && (
          <button type="button" class="tab tab-add" onClick={() => setPageSheet("new")} aria-label="Add page">
            <IconPlus size={props.isPhone ? 18 : 14} />
            <span class="tab-text">Page</span>
          </button>
        )}
      </nav>
      {pageSheet && (
        <PageSheet
          page={pageSheet === "new" ? null : pageSheet}
          canDelete={props.pages.length > 1}
          index={pageSheet === "new" ? -1 : props.pages.findIndex((p) => p.id === pageSheet.id)}
          count={props.pages.length}
          onClose={() => setPageSheet(null)}
          onSave={(title, icon) => {
            if (pageSheet === "new") props.onAdd(title, icon);
            else props.onChange(updatePage(props.pages, pageSheet.id, { title, icon }));
            setPageSheet(null);
          }}
          onMove={(dir) => pageSheet !== "new" && props.onChange(movePage(props.pages, pageSheet.id, dir))}
          onDelete={() => {
            if (pageSheet === "new") return;
            const next = deletePage(props.pages, pageSheet.id);
            props.onChange(next);
            props.onSelect(next[0].id);
            setPageSheet(null);
          }}
        />
      )}
    </>
  );
}

function IconPicker({ value, onChange }: { value?: IconName; onChange: (i?: IconName) => void }) {
  return (
    <div class="icon-grid" role="radiogroup" aria-label="Icon">
      {ICON_NAMES.map((n) => (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={n} title={n} class={`icon-cell ${value === n ? "on" : ""}`} onClick={() => onChange(value === n ? undefined : n)}>
          <PixelIcon name={n} size={20} />
        </button>
      ))}
    </div>
  );
}

function PageSheet(props: {
  page: ResolvedPage | null;
  canDelete: boolean;
  index: number;
  count: number;
  onClose: () => void;
  onSave: (title: string, icon?: IconName) => void;
  onMove: (dir: -1 | 1) => void;
  onDelete: () => void;
}) {
  const [title, setTitle] = useState(props.page?.title ?? "");
  const [icon, setIcon] = useState<IconName | undefined>(props.page?.icon ?? (props.page ? undefined : "star"));
  return (
    <Sheet title={props.page ? "Page" : "New page"} onClose={props.onClose}>
      <label class="field">
        <span class="field-label">Name</span>
        <input class="input" value={title} maxLength={48} autoFocus placeholder="e.g. Reactor" onInput={(e) => setTitle((e.target as HTMLInputElement).value)} />
      </label>
      <div class="field">
        <span class="field-label">Icon</span>
        <IconPicker value={icon} onChange={setIcon} />
      </div>
      {props.page && (
        <div class="field">
          <span class="field-label">Position</span>
          <div class="row-btns">
            <button type="button" class="btn" disabled={props.index <= 0} onClick={() => props.onMove(-1)}>
              ◀ Move left
            </button>
            <button type="button" class="btn" disabled={props.index >= props.count - 1} onClick={() => props.onMove(1)}>
              Move right ▶
            </button>
          </div>
        </div>
      )}
      <div class="sheet-actions">
        {props.page ? (
          <button type="button" class="btn btn-danger-ghost" disabled={!props.canDelete} onClick={props.onDelete} title={props.canDelete ? "Cards move to the first page" : "The last page cannot be deleted"}>
            Delete page
          </button>
        ) : (
          <button type="button" class="btn" onClick={props.onClose}>
            Cancel
          </button>
        )}
        <button type="button" class="pxbtn pxbtn-gold" disabled={!title.trim()} onClick={() => props.onSave(title.trim(), icon)}>
          <span>{props.page ? "Apply" : "Add page"}</span>
        </button>
      </div>
      {props.page && <p class="muted small">Deleting a page moves its cards to the first page.</p>}
    </Sheet>
  );
}

// ------------------------------------------------------------------ edit bar
function EditBar(props: { saving: boolean; error: string | null; onCancel: () => void; onSave: () => void }) {
  return (
    <div class="edit-bar" role="region" aria-label="Layout editor">
      <div class="edit-bar-main">
        <span class="edit-bar-title">
          <span class="edit-dot" /> Edit layout
        </span>
        <span class="edit-bar-hint">Shared by all devices of this mainframe</span>
      </div>
      <div class="edit-bar-actions">
        <button type="button" class="btn btn-small" onClick={props.onCancel} disabled={props.saving}>
          Cancel
        </button>
        <button type="button" class="pxbtn pxbtn-gold pxbtn-small" onClick={props.onSave} disabled={props.saving}>
          <span>{props.saving ? "Saving…" : "Save"}</span>
        </button>
      </div>
      {props.error && (
        <p class="edit-bar-error" role="alert">
          {props.error}
        </p>
      )}
    </div>
  );
}

// ----------------------------------------------------------------- edit grid
function EditGrid(props: { pages: ResolvedPage[]; page: ResolvedPage; isPhone: boolean; onChange: (p: ResolvedPage[]) => void }) {
  const { pages, page } = props;
  const [cardSheet, setCardSheet] = useState<string | null>(null);
  const [widgetSheet, setWidgetSheet] = useState<{ card: string; w: string } | null>(null);
  const [dropOn, setDropOn] = useState<string | null>(null);
  const drag = useRef<{ kind: "card" | "widget"; key: string; card?: string } | null>(null);
  const set = props.onChange;

  const findCard = (key: string) => pages.flatMap((p) => p.cards).find((c) => c.key === key);

  return (
    <>
      <p class="edit-help">
        {props.isPhone ? "Use ▲▼ to reorder, tap Edit to rename, recolour, resize or move a card to another page." : "Drag ⠿ to reorder cards and widgets, drag the right edge to resize, drop a card on a tab to move it."}
      </p>
      {page.cards.length === 0 && <div class="empty">This page is empty. Move cards here from another page (card → Edit → Page).</div>}
      <div
        class="grid grid-edit"
        onDragEnd={() => {
          drag.current = null;
          setDropOn(null);
        }}
      >
        {page.cards.map((c, i) => (
          <EditCard
            key={c.key}
            card={c}
            h={{
              isPhone: props.isPhone,
              index: i,
              count: page.cards.length,
              onMove: (dir) => set(moveCard(pages, c.key, page.id, i + dir)),
              onSettings: () => setCardSheet(c.key),
              onWidgetMove: (wk, dir) => set(moveWidget(pages, c.key, wk, c.widgets.findIndex((w) => w.key === wk) + dir)),
              onWidgetSettings: (rw) => setWidgetSheet({ card: c.key, w: rw.key }),
              onWidgetToggle: (rw) => set(updateWidget(pages, rw.key, { hidden: !rw.hidden })),
              onResize: (span) => set(updateCard(pages, c.key, { span })),
              dnd: {
                dropTarget: dropOn === c.key,
                onCardDragStart: (e) => {
                  drag.current = { kind: "card", key: c.key };
                  e.dataTransfer?.setData("text/x-cccloud-card", c.key);
                  if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
                },
                onCardDragOver: (e) => {
                  if (drag.current?.kind !== "card") return;
                  e.preventDefault();
                  if (dropOn !== c.key) setDropOn(c.key);
                },
                onCardDrop: (e) => {
                  e.preventDefault();
                  setDropOn(null);
                  const d = drag.current;
                  drag.current = null;
                  if (d?.kind !== "card" || d.key === c.key) return;
                  const from = page.cards.findIndex((x) => x.key === d.key);
                  const to = from >= 0 && from < i ? i : i; // insert at the drop target's position
                  set(moveCard(pages, d.key, page.id, to));
                },
                onWidgetDragStart: (e, rw) => {
                  e.stopPropagation();
                  drag.current = { kind: "widget", key: rw.key, card: c.key };
                  e.dataTransfer?.setData("text/plain", rw.key);
                },
                onWidgetDrop: (e, rw) => {
                  const d = drag.current;
                  if (d?.kind !== "widget") return; // a card drop: let it bubble to the card
                  e.preventDefault();
                  e.stopPropagation();
                  drag.current = null;
                  if (d.card !== c.key || d.key === rw.key) return;
                  set(moveWidget(pages, c.key, d.key, c.widgets.findIndex((w) => w.key === rw.key)));
                },
              },
            }}
          />
        ))}
      </div>
      {cardSheet && findCard(cardSheet) && (
        <CardSheet
          card={findCard(cardSheet)!}
          pages={pages}
          isPhone={props.isPhone}
          onClose={() => setCardSheet(null)}
          onApply={(patch, toPage) => {
            let next = updateCard(pages, cardSheet, patch);
            if (toPage && toPage !== findCard(cardSheet)!.page) next = moveCard(next, cardSheet, toPage, Number.MAX_SAFE_INTEGER);
            set(next);
            setCardSheet(null);
          }}
        />
      )}
      {widgetSheet &&
        (() => {
          const card = findCard(widgetSheet.card);
          const rw = card?.widgets.find((w) => w.key === widgetSheet.w);
          return rw ? (
            <WidgetSheet
              rw={rw}
              onClose={() => setWidgetSheet(null)}
              onApply={(patch) => {
                set(updateWidget(pages, rw.key, patch));
                setWidgetSheet(null);
              }}
            />
          ) : null;
        })()}
    </>
  );
}

function SpanPicker({ value, onChange, label = "Width" }: { value: number; onChange: (n: number) => void; label?: string }) {
  return (
    <div class="field">
      <span class="field-label">{label}</span>
      <div class="seg-ctl seg-wide" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={value === n} class={value === n ? "on" : ""} onClick={() => onChange(n)}>
            {n} col{n > 1 ? "s" : ""}
          </button>
        ))}
      </div>
    </div>
  );
}

function CardSheet(props: {
  card: ResolvedCard;
  pages: ResolvedPage[];
  isPhone: boolean;
  onClose: () => void;
  onApply: (patch: Partial<Pick<ResolvedCard, "title" | "span" | "hidden" | "color" | "icon">>, toPage?: string) => void;
}) {
  const c = props.card;
  const [title, setTitle] = useState(c.title);
  const [color, setColor] = useState<Accent>(c.color);
  const [icon, setIcon] = useState<IconName | undefined>(c.icon);
  const [span, setSpan] = useState(c.span);
  const [hidden, setHidden] = useState(c.hidden);
  const [page, setPage] = useState(c.page);
  return (
    <Sheet title="Card" onClose={props.onClose}>
      <label class="field">
        <span class="field-label">Title</span>
        <input class="input" value={title} maxLength={48} placeholder={c.defaultTitle} onInput={(e) => setTitle((e.target as HTMLInputElement).value)} />
      </label>
      <div class="field">
        <span class="field-label">Accent</span>
        <div class="swatches" role="radiogroup" aria-label="Accent colour">
          {ACCENTS.map((a) => (
            <button key={a} type="button" role="radio" aria-checked={color === a} aria-label={a} title={a} class={`swatch sw-${a} ${color === a ? "on" : ""}`} onClick={() => setColor(a)} />
          ))}
        </div>
      </div>
      <div class="field">
        <span class="field-label">Icon</span>
        <IconPicker value={icon} onChange={setIcon} />
      </div>
      <SpanPicker value={span} onChange={setSpan} label={props.isPhone ? "Width on larger screens" : "Width"} />
      <label class="field">
        <span class="field-label">Page</span>
        <select class="select" value={page} onChange={(e) => setPage((e.target as HTMLSelectElement).value)}>
          {props.pages.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </label>
      <label class="check-row">
        <input type="checkbox" checked={hidden} onChange={(e) => setHidden((e.target as HTMLInputElement).checked)} />
        <span>Hide this card</span>
      </label>
      <div class="sheet-actions">
        <button type="button" class="btn" onClick={props.onClose}>
          Cancel
        </button>
        <button type="button" class="pxbtn pxbtn-gold" onClick={() => props.onApply({ title: title.trim() || c.defaultTitle, color, icon, span, hidden }, page)}>
          <span>Apply</span>
        </button>
      </div>
    </Sheet>
  );
}

function WidgetSheet(props: { rw: ResolvedWidget; onClose: () => void; onApply: (patch: Partial<Pick<ResolvedWidget, "label" | "span" | "hidden">>) => void }) {
  const { rw } = props;
  const [label, setLabel] = useState(rw.label);
  const [span, setSpan] = useState(rw.span);
  const [hidden, setHidden] = useState(rw.hidden);
  return (
    <Sheet title="Widget" onClose={props.onClose}>
      <p class="muted small">
        <span class="type-tag">{rw.w.type}</span> <code>{rw.w.id}</code>
      </p>
      <label class="field">
        <span class="field-label">Label</span>
        <input class="input" value={label} maxLength={48} placeholder={rw.w.label ?? rw.w.id} onInput={(e) => setLabel((e.target as HTMLInputElement).value)} />
      </label>
      <SpanPicker value={span} onChange={setSpan} label="Width inside the card" />
      <label class="check-row">
        <input type="checkbox" checked={hidden} onChange={(e) => setHidden((e.target as HTMLInputElement).checked)} />
        <span>Hide this widget</span>
      </label>
      <div class="sheet-actions">
        <button type="button" class="btn" onClick={props.onClose}>
          Cancel
        </button>
        <button type="button" class="pxbtn pxbtn-gold" onClick={() => props.onApply({ label: label.trim() || rw.w.label || rw.w.id, span, hidden })}>
          <span>Apply</span>
        </button>
      </div>
    </Sheet>
  );
}
