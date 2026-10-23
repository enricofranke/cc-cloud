// Page tabs (top bar on desktop, bottom bar on phones) and the page settings sheet.
import { useState } from "preact/hooks";
import { ICON_NAMES, MAX_PAGES, deletePage, movePage, updatePage, type IconName, type ResolvedPage } from "../lib/layout.ts";
import { PixelIcon } from "./PixelIcon.tsx";
import { Sheet } from "./Overlays.tsx";
import { IconPlus } from "./Icons.tsx";

export const NETWORK_PAGE = "__network";

export function Tabs(props: {
  pages: ResolvedPage[];
  network?: boolean;
  current: string;
  isPhone: boolean;
  editing: boolean;
  onSelect: (id: string) => void;
  onAdd: (title: string, icon?: IconName) => void;
  onChange: (pages: ResolvedPage[]) => void;
  /** edit mode: the tab a dragged card/widget hovers (drop = move to that page) */
  dropHover?: string | null;
}) {
  const [pageSheet, setPageSheet] = useState<ResolvedPage | "new" | null>(null);
  return (
    <>
      <nav
        class={props.isPhone ? "tabbar" : "toptabs"}
        aria-label="Pages"
        onKeyDown={(e) => {
          // arrow keys move between tabs
          if (e.key !== "ArrowLeft" && e.key !== "ArrowRight" && e.key !== "Home" && e.key !== "End") return;
          const tabs = [...(e.currentTarget as HTMLElement).querySelectorAll<HTMLElement>(".tab")];
          const i = tabs.indexOf(document.activeElement as HTMLElement);
          const next = e.key === "Home" ? 0 : e.key === "End" ? tabs.length - 1 : (i + (e.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
          tabs[next]?.focus();
          e.preventDefault();
        }}
      >
        {props.pages.map((p) => (
          <button
            key={p.id}
            type="button"
            class={`tab ${p.id === props.current ? "on" : ""} ${props.dropHover === p.id ? "drop-hover" : ""}`}
            data-tab={props.editing ? p.id : undefined}
            aria-current={p.id === props.current ? "page" : undefined}
            onClick={() => (props.editing && p.id === props.current ? setPageSheet(p) : props.onSelect(p.id))}
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

export function IconPicker({ value, onChange }: { value?: IconName; onChange: (i?: IconName) => void }) {
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

