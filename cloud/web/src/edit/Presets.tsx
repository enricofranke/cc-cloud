// Presets in the layout editor: the toolbox tab with the official presets, the community presets of the store and pasted
// codes; the sheet that shows what a preset does on this base; and "Share this page as preset".
//
// A preset never touches a page that exists: it lands on new pages, through the editor's history, so Undo takes it back
// and Save goes through the same check as every other layout.
import { createPortal } from "preact/compat";
import { useEffect, useMemo, useState } from "preact/hooks";
import { CopyButton } from "../components/CodeBlock.tsx";
import { Card } from "../components/Card.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import type { MainframeConn } from "../lib/conn.ts";
import { lang, t, type Key } from "../lib/i18n.ts";
import { MAX_PAGES, resolveLayout, snapshotLayout, type LayoutDoc, type ResolvedPage } from "../lib/layout.ts";
import { CodeError, decodePreset, encodePreset, readInput, type CodeErrorCode } from "../lib/presets/code.ts";
import { matchPreset, mergeLayout, recommend } from "../lib/presets/expand.ts";
import { pageToPreset } from "../lib/presets/export.ts";
import { textOf, type Preset } from "../lib/presets/format.ts";
import { matchSlots, type MatchState, type SlotMatch, type SlotPicks } from "../lib/presets/match.ts";
import { ACTION_TYPES } from "../lib/presets/roles.ts";
import { StoreError, getStorePreset, listStorePresets, type StoreErrorCode, type StorePresetEntry } from "../lib/presets/store.ts";
import type { Uplink } from "../lib/types.ts";
import { pendingInstall } from "../lib/ui.ts";
import { officialPresets } from "../presets/official.ts";

export type PresetOrigin = "official" | "store" | "code";
interface Picked {
  preset: Preset;
  origin: PresetOrigin;
}

const CODE_ERRORS: Record<CodeErrorCode, Key> = {
  empty: "ps.errEmpty",
  not_a_code: "ps.errNotACode",
  newer_code: "ps.errNewer",
  newer_format: "ps.errNewer",
  damaged: "ps.errDamaged",
  too_large: "ps.errTooLarge",
  unreadable: "ps.errUnreadable",
  invalid_preset: "ps.errInvalid",
  unsupported: "ps.errUnsupported",
};
const STORE_ERRORS: Record<StoreErrorCode, Key> = {
  store_disabled: "ps.storeDisabled",
  store_not_configured: "ps.storeNotConfigured",
  store_unreachable: "ps.storeUnreachable",
  store_bad_signature: "ps.storeBadSignature",
  store_mismatch: "ps.storeBadSignature",
  invalid_preset: "ps.storeInvalid",
  unknown_preset: "ps.storeUnknown",
  not_allowed: "ps.storeNotAllowed",
  remote_locked: "ps.storeNotAllowed",
  unreachable: "ps.storeUnreachable",
};
const STATE_TEXT: Record<MatchState, Key> = { ready: "ps.stateReady", partial: "ps.statePartial", missing: "ps.stateMissing" };
const STATE_TAG: Record<MatchState, string> = { ready: "pr-ready", partial: "pr-partial", missing: "pr-missing" };

const countOf = (n: number, one: Key, many: Key) => (n === 1 ? t(one) : t(many, { n }));
const stateOf = (slots: SlotMatch[]): MatchState => (slots.some((s) => s.missing > 0) ? "missing" : slots.some((s) => !s.chosen.length) ? "partial" : "ready");

function Row({ preset, state, hint, onClick }: { preset: { name: Preset["name"]; description?: Preset["description"]; icon?: Preset["icon"] }; state: MatchState; hint?: string; onClick: () => void }) {
  return (
    <li>
      <button type="button" class={`pr-row ${state === "missing" ? "pr-row-dim" : ""}`} onClick={onClick}>
        <PixelIcon name={preset.icon ?? "star"} size={16} />
        <span class="pr-row-text">
          <b>{textOf(preset.name, lang.value)}</b>
          <small>{hint ?? textOf(preset.description, lang.value)}</small>
        </span>
        <span class={`tag ${STATE_TAG[state]}`}>{t(STATE_TEXT[state])}</span>
      </button>
    </li>
  );
}

export function PresetsTab(props: { conn: MainframeConn; uplinks: Uplink[]; pages: ResolvedPage[]; page: string; onApply: (doc: LayoutDoc, firstPage: string) => void }) {
  const [source, setSource] = useState<"official" | "community" | "code">("official");
  const [picked, setPicked] = useState<Picked | null>(null);
  const [sharing, setSharing] = useState(false);
  const mf = props.conn.info.value?.id ?? "";
  const current = props.pages.find((p) => p.id === props.page);
  return (
    <>
      <div class="seg-ctl seg-wide" role="radiogroup" aria-label={t("ps.source")}>
        {(["official", "community", "code"] as const).map((id) => (
          <button key={id} type="button" role="radio" aria-checked={source === id} class={source === id ? "on" : ""} onClick={() => setSource(id)}>
            {t(id === "official" ? "ps.official" : id === "community" ? "ps.community" : "ps.code")}
          </button>
        ))}
      </div>
      {source === "official" && <OfficialList uplinks={props.uplinks} onPick={setPicked} />}
      {source === "community" && <CommunityList uplinks={props.uplinks} mainframe={mf} onPick={setPicked} />}
      {source === "code" && <CodeImport mainframe={mf} onPick={setPicked} />}
      <button type="button" class="btn btn-small" disabled={!current?.cards.some((c) => !c.hidden)} onClick={() => setSharing(true)}>
        {t("ps.shareThis")}
      </button>
      {/* the toolbox floats in a transformed panel: a sheet has to leave it to cover the page */}
      {picked && createPortal(<PresetSheet {...picked} conn={props.conn} uplinks={props.uplinks} pages={props.pages} onClose={() => setPicked(null)} onApply={(doc, first) => (setPicked(null), props.onApply(doc, first))} />, document.body)}
      {sharing && current && createPortal(<ShareSheet page={current} uplinks={props.uplinks} onClose={() => setSharing(false)} />, document.body)}
    </>
  );
}

function OfficialList({ uplinks, onPick }: { uplinks: Uplink[]; onPick: (p: Picked) => void }) {
  const l = lang.value;
  const matches = useMemo(() => officialPresets().map((p) => matchPreset(p, uplinks, l)), [uplinks, l]);
  const best = recommend(matches, uplinks);
  return (
    <ul class="ed-items pr-list">
      {matches.map((m) => (
        <Row key={m.preset.id} preset={m.preset} state={m.state} hint={m === best ? t("ps.recommended") : undefined} onClick={() => onPick({ preset: m.preset, origin: "official" })} />
      ))}
    </ul>
  );
}

function CommunityList({ uplinks, mainframe, onPick }: { uplinks: Uplink[]; mainframe: string; onPick: (p: Picked) => void }) {
  const [list, setList] = useState<StorePresetEntry[] | null>(null);
  const [error, setError] = useState<StoreErrorCode | null>(null);
  const [opening, setOpening] = useState("");
  const load = () => {
    setError(null);
    setList(null);
    listStorePresets(mainframe)
      .then(setList)
      .catch((e) => setError(e instanceof StoreError ? e.code : "unreachable"));
  };
  useEffect(load, [mainframe]);
  const open = async (id: string) => {
    setOpening(id);
    setError(null);
    try {
      onPick({ preset: await getStorePreset(mainframe, id), origin: "store" });
    } catch (e) {
      setError(e instanceof StoreError ? e.code : "unreachable");
    } finally {
      setOpening("");
    }
  };
  if (error && !list)
    return (
      <div class="pr-note" role="alert">
        <p>{t(STORE_ERRORS[error])}</p>
        <button type="button" class="btn btn-small" onClick={load}>
          {t("ps.retry")}
        </button>
      </div>
    );
  if (!list) return <p class="muted small">{t("ps.loading")}</p>;
  if (!list.length) return <p class="muted small">{t("ps.communityEmpty")}</p>;
  return (
    <>
      {error && <p class="pr-note pr-note-warn">{t(STORE_ERRORS[error])}</p>}
      <p class="muted small">{t("ps.communityHint")}</p>
      <ul class="ed-items pr-list">
        {list.map((e) => (
          <Row key={e.id} preset={e} state={stateOf(matchSlots({ slots: e.slots }, uplinks))} hint={opening === e.id ? t("ps.loading") : e.author ? t("ps.by", { author: e.author }) : undefined} onClick={() => open(e.id)} />
        ))}
      </ul>
    </>
  );
}

function CodeImport({ mainframe, onPick }: { mainframe: string; onPick: (p: Picked) => void }) {
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      const input = readInput(text);
      if (input.kind === "store") onPick({ preset: await getStorePreset(mainframe, input.id), origin: "store" });
      else onPick({ preset: await decodePreset(input.code), origin: "code" });
      setText("");
    } catch (e) {
      if (e instanceof CodeError) setError(t(CODE_ERRORS[e.code]) + (e.path ? ` (${e.path.slice(0, 80)})` : ""));
      else if (e instanceof StoreError) setError(t(STORE_ERRORS[e.code]));
      else setError(t("ps.errUnreadable"));
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <label class="field">
        <span class="field-label">{t("ps.pasteLabel")}</span>
        <textarea class="input pr-code" rows={5} spellcheck={false} autocomplete="off" value={text} placeholder="ccp1.…" onInput={(e) => (setText((e.currentTarget as HTMLTextAreaElement).value), setError(null))} />
      </label>
      {error && (
        <p class="pr-note pr-note-warn" role="alert">
          {error}
        </p>
      )}
      <button type="button" class="pxbtn pxbtn-gold" disabled={busy || !text.trim()} onClick={run}>
        {t("ps.pasteCheck")}
      </button>
      <p class="muted small">{t("ps.pasteHint")}</p>
    </>
  );
}

function Origin({ origin }: { origin: PresetOrigin }) {
  if (origin === "official") return <span class="tag pr-ready">{t("ps.official")}</span>;
  if (origin === "store") return <span class="tag pr-ready">{t("ps.signed")}</span>;
  return <span class="tag tag-red">{t("ps.unsigned")}</span>;
}

/** What a preset does on this base, with a live preview. Nothing changes until Apply. */
export function PresetSheet(props: Picked & { conn: MainframeConn; uplinks: Uplink[]; pages: ResolvedPage[]; narrow?: boolean; onClose: () => void; onApply: (doc: LayoutDoc, firstPage: string) => void }) {
  const { preset, uplinks } = props;
  const l = lang.value;
  const [picks, setPicks] = useState<SlotPicks>({});
  const [tab, setTab] = useState(0);
  const existing = useMemo(() => snapshotLayout(props.pages, uplinks), [props.pages, uplinks]);
  const match = useMemo(() => matchPreset(preset, uplinks, l, picks, existing), [preset, uplinks, l, picks, existing]);
  const room = useMemo(() => mergeLayout(existing, match.expanded), [existing, match]);
  const preview = useMemo(() => {
    const cards = resolveLayout(uplinks, match.expanded.doc);
    return match.expanded.pages.map((p) => ({ ...p, cards: (cards.find((c) => c.id === p.id)?.cards ?? []).filter((c) => c.key in match.expanded.doc.cards) }));
  }, [match, uplinks]);
  const shown = preview[Math.min(tab, preview.length - 1)];
  const controls = useMemo(() => preview.flatMap((p) => p.cards.flatMap((c) => c.widgets.filter((w) => ACTION_TYPES.has(w.w.type)).map((w) => ({ key: w.key, label: w.label, device: w.uplink.title })))), [preview]);
  const script = preset.scripts[0];
  const pick = (s: SlotMatch, uplink: string) => {
    const now = picks[s.slot.id] ?? s.chosen.map((u) => u.uplinkId);
    const on = now.includes(uplink);
    const next = s.slot.max === 1 ? [uplink] : on ? now.filter((id) => id !== uplink) : [...now, uplink].slice(-s.slot.max);
    setPicks({ ...picks, [s.slot.id]: next });
  };
  const problem =
    match.state === "missing"
      ? t("ps.cannotApply")
      : "problem" in room
        ? room.problem === "too_many_pages"
          ? t("ps.tooManyPages", { need: match.expanded.pages.length, free: room.free ?? 0, max: MAX_PAGES })
          : room.problem === "too_large"
            ? t("ps.tooLarge")
            : t("ps.cannotApply")
        : null;
  return (
    <Sheet title={textOf(preset.name, l)} onClose={props.onClose} wide tall>
      <div class="pr-head">
        <Origin origin={props.origin} />
        <span class={`tag ${STATE_TAG[match.state]}`}>{t(STATE_TEXT[match.state])}</span>
        <span class="muted small">{t("ps.counts", { pages: countOf(match.expanded.pages.length, "ps.page1", "ps.pageN"), cards: countOf(match.expanded.cards, "ps.card1", "ps.cardN"), widgets: countOf(match.widgets, "ps.widget1", "ps.widgetN") })}</span>
      </div>
      {preset.description && <p>{textOf(preset.description, l)}</p>}
      {props.origin === "code" && <p class="pr-note pr-note-warn">{t("ps.unsignedNote")}</p>}
      <h3 class="ed-h">{t("ps.slots")}</h3>
      <ul class="pr-slots">
        {match.slots.map((s) => (
          <li key={s.slot.id} class={`pr-slot ${s.missing ? "is-missing" : s.chosen.length ? "is-filled" : "is-empty"}`}>
            <span class="pr-slot-head">
              <b>{textOf(s.slot.label, l)}</b>
              <span class="muted small">{s.missing ? t("ps.slotMissing") : s.chosen.length ? s.chosen.map((u) => u.title).join(", ") : t("ps.slotOptional")}</span>
            </span>
            {s.choice && (
              <span class="pr-choose" role="group" aria-label={t("ps.choose", { slot: textOf(s.slot.label, l) })}>
                {s.candidates.map((u) => (
                  <button key={u.uplinkId} type="button" class={`chip ${s.chosen.includes(u) ? "on" : ""}`} aria-pressed={s.chosen.includes(u)} onClick={() => pick(s, u.uplinkId)}>
                    {u.title}
                  </button>
                ))}
              </span>
            )}
          </li>
        ))}
      </ul>
      {controls.length > 0 && (
        <>
          <h3 class="ed-h">{t("ps.controls")}</h3>
          <p class="muted small">{t("ps.controlsHint")}</p>
          <ul class="pr-controls">
            {controls.slice(0, 24).map((c) => (
              <li key={c.key}>
                <b>{c.label}</b> <span class="muted">{c.device}</span>
              </li>
            ))}
            {controls.length > 24 && <li class="muted">{t("ps.moreControls", { n: controls.length - 24 })}</li>}
          </ul>
        </>
      )}
      {script && (
        <p class="pr-note">
          {t("ps.scriptHint", { id: script })}{" "}
          <button type="button" class="link-btn" onClick={() => (props.onClose(), (pendingInstall.value = { id: script }))}>
            {t("ps.scriptOpen")}
          </button>
        </p>
      )}
      {shown && (
        <>
          <h3 class="ed-h">{t("ps.preview")}</h3>
          {preview.length > 1 && (
            <div class="seg-ctl seg-s" role="radiogroup" aria-label={t("ps.preview")}>
              {preview.map((p, i) => (
                <button key={p.id} type="button" role="radio" aria-checked={i === tab} class={i === tab ? "on" : ""} onClick={() => setTab(i)}>
                  {p.title}
                </button>
              ))}
            </div>
          )}
          <div class="pr-preview" {...({ inert: true } as object)} aria-hidden="true">
            <div class={`pgrid ${matchMedia("(max-width: 719px)").matches ? "narrow" : ""}`}>
              {shown.cards.map((c) => (
                <Card key={c.key} card={c} conn={props.conn} locked />
              ))}
            </div>
          </div>
        </>
      )}
      {problem && (
        <p class="pr-note pr-note-warn" role="alert">
          {problem}
        </p>
      )}
      <div class="pr-actions">
        <button type="button" class="btn btn-small" onClick={props.onClose}>
          {t("ps.cancel")}
        </button>
        <button type="button" class="pxbtn pxbtn-gold" disabled={!!problem || !("doc" in room)} onClick={() => "doc" in room && props.onApply(room.doc, match.expanded.pages[0].id)}>
          {match.expanded.pages.length > 1 ? t("ps.applyPages", { n: match.expanded.pages.length }) : t("ps.applyPage")}
        </button>
      </div>
    </Sheet>
  );
}

function ShareSheet({ page, uplinks, onClose }: { page: ResolvedPage; uplinks: Uplink[]; onClose: () => void }) {
  const [name, setName] = useState(page.title);
  const [description, setDescription] = useState("");
  const result = useMemo(() => {
    try {
      return pageToPreset(page, uplinks, { name, description });
    } catch {
      return null; // nothing on the page has a source that could be shared
    }
  }, [page, uplinks, name, description]);
  const [code, setCode] = useState("");
  const [error, setError] = useState<Key | null>(null);
  useEffect(() => {
    if (!result) return;
    let live = true;
    encodePreset(result.preset)
      .then((c) => live && (setCode(c), setError(null)))
      .catch((e) => live && (setCode(""), setError(e instanceof CodeError ? CODE_ERRORS[e.code] : "ps.errUnreadable")));
    return () => {
      live = false;
    };
  }, [result]);
  return (
    <Sheet title={t("ps.shareTitle")} onClose={onClose}>
      <p>{t("ps.shareHint")}</p>
      <label class="field">
        <span class="field-label">{t("ps.shareName")}</span>
        <input class="input" maxLength={60} value={name} onInput={(e) => setName((e.currentTarget as HTMLInputElement).value)} />
      </label>
      <label class="field">
        <span class="field-label">{t("ps.shareDescription")}</span>
        <textarea class="input" rows={2} maxLength={400} value={description} onInput={(e) => setDescription((e.currentTarget as HTMLTextAreaElement).value)} />
      </label>
      {!result && <p class="pr-note pr-note-warn">{t("ps.shareNothing")}</p>}
      {!!result?.skipped && <p class="pr-note pr-note-warn">{t("ps.shareSkipped", { n: result.skipped })}</p>}
      {!!result?.restricted && <p class="pr-note">{t("ps.shareRestricted", { n: result.restricted })}</p>}
      {error && (
        <p class="pr-note pr-note-warn" role="alert">
          {t(error)}
        </p>
      )}
      {result && code && (
        <>
          <label class="field">
            <span class="field-label">{t("ps.shareCode")}</span>
            <textarea class="input pr-code" rows={5} readOnly spellcheck={false} value={code} onFocus={(e) => (e.currentTarget as HTMLTextAreaElement).select()} />
          </label>
          <div class="pr-actions">
            <span class="muted small">{t("ps.shareLength", { n: code.length })}</span>
            <CopyButton text={code} />
          </div>
        </>
      )}
    </Sheet>
  );
}
