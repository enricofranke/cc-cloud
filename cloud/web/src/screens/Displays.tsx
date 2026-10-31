// Cloud Displays (lazy route #/m/<id>/displays): every display linked to this mainframe, what it shows, a preview and
// its settings. The list is a request ({"t":"display"}), polled while the screen is open, so positions never go out as
// a broadcast. The preview is the ordinary Card with data the dashboard already has.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { getConn } from "../lib/conns.ts";
import type { MainframeConn } from "../lib/conn.ts";
import { dimLabel } from "../lib/dims.ts";
import { previewCards, previewFrame, targetOptions, type DisplayChange, type DisplayRow, type DisplayStatus } from "../lib/displays.ts";
import { lang, t, type Key } from "../lib/i18n.ts";
import { resolveLayout, type ResolvedPage } from "../lib/layout.ts";
import type { Uplink } from "../lib/types.ts";
import { navigate, pushToast } from "../lib/ui.ts";
import { Card } from "../components/Card.tsx";
import { DocsLink } from "../components/DocsLink.tsx";
import { IconBack } from "../components/Icons.tsx";
import { ConfirmDialog } from "../components/Overlays.tsx";

const POLL_MS = 3000;
const STATUS_TAG: Record<DisplayStatus, string> = {
  ok: "dp-ok",
  no_section: "tag-gray",
  no_rights: "tag-red",
  missing: "tag-red",
  too_large: "tag-red",
  restricted: "tag-red",
  offline: "tag-red",
  unloaded: "tag-gray",
};
const ERR_KEYS: Record<string, Key> = {
  not_allowed: "dp.err.not_allowed",
  read_only: "dp.err.not_allowed",
  forbidden: "dp.err.not_allowed",
  unknown_display: "dp.err.unknown_display",
  unknown_target: "dp.err.unknown_target",
  unloaded: "dp.err.unloaded",
  disconnected: "dp.err.disconnected",
  timeout: "dp.err.disconnected",
  offline: "dp.err.disconnected",
  rate_limited: "dp.err.rate_limited",
};
const errText = (e: unknown) => {
  const code = (e as Error).message;
  return ERR_KEYS[code] ? t(ERR_KEYS[code]) : code;
};

export default function DisplaysScreen({ id }: { id: string }) {
  const conn = getConn(id);
  const [rows, setRows] = useState<DisplayRow[] | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const refresh = useRef<() => void>(() => {});
  const canEdit = conn?.can("layout.edit") ?? false;
  useEffect(() => {
    if (!conn || !canEdit) return;
    let alive = true;
    const load = () =>
      conn
        .listDisplays()
        .then((list) => {
          if (!alive) return;
          setRows(list);
          setFailed(null);
        })
        .catch((e) => alive && setFailed(errText(e)));
    refresh.current = load;
    load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [conn, canEdit]);
  if (!conn) return null;
  // the preview follows live values; the layout is resolved once per structure or layout change
  const uplinks = conn.uplinks.value;
  const layout = conn.layout.value;
  const pages = resolveLayout(uplinks, layout);
  return (
    <main class="page page-narrow-wide displays-page" id="main" tabIndex={-1} lang={lang.value}>
      <div class="page-head acc-head">
        <div>
          <button type="button" class="link-back" onClick={() => navigate(`/m/${encodeURIComponent(id)}`)}>
            <IconBack size={18} /> {t("c.back")}
          </button>
          <h1 class="px-heading">{t("dp.title")}</h1>
        </div>
      </div>
      <p class="muted">
        {t("dp.intro", { name: conn.info.value?.name ?? "Mainframe" })} <DocsLink to="/docs/upgrades#display">{t("dp.docs")}</DocsLink>
      </p>
      {!canEdit ? (
        <p class="muted">{t("dp.needEdit")}</p>
      ) : failed ? (
        <p class="field-hint warn" role="alert">
          {failed}
        </p>
      ) : rows === null ? (
        <p class="muted">{t("dp.loading")}</p>
      ) : rows.length === 0 ? (
        <div class="rules-empty">
          <b>{t("dp.empty")}</b>
          <span class="muted small">{t("dp.emptyHint")}</span>
        </div>
      ) : (
        <ul class="dp-list">
          {rows.map((d) => (
            <DisplayItem key={d.id} d={d} conn={conn} uplinks={uplinks} pages={pages} onChanged={() => refresh.current()} />
          ))}
        </ul>
      )}
      {canEdit && rows !== null && rows.length > 0 && <p class="muted small">{t("dp.limit")}</p>}
    </main>
  );
}

function DisplayItem({ d, conn, uplinks, pages, onChanged }: { d: DisplayRow; conn: MainframeConn; uplinks: Uplink[]; pages: ResolvedPage[]; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<"restricted" | "unlink" | null>(null);
  const layout = conn.layout.value;
  const options = useMemo(() => targetOptions(uplinks, layout, d.allowRestricted), [uplinks, layout, d.allowRestricted]);
  const run = async (f: () => Promise<void>) => {
    setBusy(true);
    try {
      await f();
      onChanged();
    } catch (e) {
      pushToast({ level: "warn", title: t("dp.title"), text: errText(e) });
    } finally {
      setBusy(false);
    }
  };
  const set = (change: DisplayChange) => run(() => conn.setDisplay(d.id, change));
  const locked = busy || d.status === "unloaded";
  const canRestricted = conn.can("view.positions");
  const known = options.some((o) => o.value === d.target);
  const group = (kind: "section" | "card" | "page", label: string) => {
    const list = options.filter((o) => o.kind === kind);
    if (!list.length) return null;
    return (
      <optgroup label={label}>
        {list.map((o) => (
          <option key={o.value} value={o.value}>
            {`${o.title || t("ed.untitled")}${o.restricted ? ` · ${t("dp.restrictedMark")}` : ""}`}
          </option>
        ))}
      </optgroup>
    );
  };
  return (
    <li class="dp-item">
      <div class="dp-main">
        <div class="dp-head">
          <b class="dp-size">
            {d.w} × {d.h}
          </b>
          <span class={`tag ${STATUS_TAG[d.status]}`}>{t(`dp.st.${d.status}` as Key)}</span>
        </div>
        <p class="muted small dp-where">
          {d.x !== undefined ? (
            <>
              {dimLabel(d.dim ?? "")} · <span class="mono">{d.x} {d.y} {d.z}</span>
            </>
          ) : (
            t("dp.noPosition")
          )}
        </p>
        <label class="dp-field">
          <span class="muted small">{t("dp.shows")}</span>
          <select class="select" disabled={locked} value={d.target} aria-label={t("dp.shows")} onChange={(e) => set({ target: (e.target as HTMLSelectElement).value })}>
            <option value="">{t("dp.nothing")}</option>
            {!known && d.target && <option value={d.target}>{t("dp.notAvailable")}</option>}
            {group("section", t("dp.group.section"))}
            {group("card", t("dp.group.card"))}
            {group("page", t("dp.group.page"))}
          </select>
        </label>
        <Switch label={t("dp.interactive")} on={d.interactive} disabled={locked} onChange={(v) => set({ interactive: v })} />
        {canRestricted ? (
          <Switch label={t("dp.allowRestricted")} on={d.allowRestricted} disabled={locked} onChange={(v) => (v ? setConfirm("restricted") : set({ allowRestricted: false }))} />
        ) : (
          d.allowRestricted && <p class="muted small">{t("dp.restrictedOn")}</p>
        )}
        {d.allowRestricted && (
          <p class="field-hint warn" role="note">
            {t("dp.warning")}
          </p>
        )}
        {conn.can("network.link") && (
          <button type="button" class="link-btn dp-unlink" disabled={locked} onClick={() => setConfirm("unlink")}>
            {t("dp.unlink")}
          </button>
        )}
      </div>
      <Preview d={d} conn={conn} uplinks={uplinks} pages={pages} />
      {confirm === "restricted" && (
        <ConfirmDialog
          title={t("dp.allowTitle")}
          text={t("dp.allowText")}
          confirmLabel={t("dp.allowConfirm")}
          color="yellow"
          onCancel={() => setConfirm(null)}
          onConfirm={() => {
            setConfirm(null);
            set({ allowRestricted: true });
          }}
        />
      )}
      {confirm === "unlink" && (
        <ConfirmDialog
          title={t("dp.unlinkTitle")}
          text={t("dp.unlinkText")}
          confirmLabel={t("dp.unlink")}
          color="red"
          onCancel={() => setConfirm(null)}
          onConfirm={() => {
            setConfirm(null);
            run(() => conn.unlinkDisplay(d.id));
          }}
        />
      )}
    </li>
  );
}

function Switch({ label, on, disabled, onChange }: { label: string; on: boolean; disabled: boolean; onChange: (on: boolean) => void }) {
  return (
    <div class="dp-switch">
      <button type="button" role="switch" aria-checked={on} aria-label={label} disabled={disabled} class={`switch ${on ? "on" : ""}`} onClick={() => onChange(!on)}>
        <span class="switch-text">{on ? "ON" : "OFF"}</span>
        <span class="switch-knob" />
      </button>
      <span class="small">{label}</span>
    </div>
  );
}

function Preview({ d, conn, uplinks, pages }: { d: DisplayRow; conn: MainframeConn; uplinks: Uplink[]; pages: ResolvedPage[] }) {
  const f = previewFrame(d.w, d.h);
  const cards = d.status === "ok" ? previewCards(d.target, uplinks, pages, d.allowRestricted) : null;
  return (
    <figure class="dp-preview">
      <div class="dp-frame" style={{ width: `${f.frameW}px`, height: `${f.frameH}px` }} aria-label={t("dp.preview")}>
        {cards?.length ? (
          // inert: a picture of the card, the display has its own controls
          <div class="dp-canvas" inert style={{ width: `${f.canvasW}px`, height: `${f.canvasH}px`, transform: `scale(${f.scale})` }}>
            {cards.map((c) => (
              <Card key={c.key} card={c} conn={conn} locked />
            ))}
          </div>
        ) : (
          <span class="dp-blank">{d.status === "ok" ? t("dp.noPreview") : t(`dp.st.${d.status}` as Key)}</span>
        )}
      </div>
      {cards?.length ? <figcaption class="muted small">{t("dp.approx")}</figcaption> : null}
    </figure>
  );
}
