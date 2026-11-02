// Cloud Scripts (lazy route #/m/<id>/scripts, one script at #/m/<id>/scripts/<scriptId>): Lua scripts that run inside
// the mainframe. The list shows status, CPU and the last error with start/stop/restart and autostart; the workspace is
// the editor (its own CodeMirror chunk), the live log and the version history. Everything goes over the dashboard
// socket ({"t":"code"} → code_result, code_status and code_log pushes; lib/conn.ts) and needs code.edit.
import type { ComponentChildren, ComponentType } from "preact";
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { getConn } from "../lib/conns.ts";
import type { MainframeConn } from "../lib/conn.ts";
import { navigate, pushToast, route } from "../lib/ui.ts";
import { lang } from "../lib/i18n.ts";
import { agoText } from "../lib/schedules.ts";
import { fmtBytes } from "../lib/scripts.ts";
import { MAX_NAME, parseDiagnostics, parseVersions, type CodeLimits, type Diagnostic, type LogLine, type ScriptSummary, type VersionMeta } from "../lib/code.ts";
import { byteSize, cleanName, cpuShare, diffLines, diffStats, foldDiff, logTime, markersFor, uniqueName, visibleLog, type Marker } from "../code/ide.ts";
import { ct, codeErrText, hasKey, type CodeKey } from "../code/strings.ts";
import { BLANK_SOURCE, templates } from "../code/templates.ts";
import type { EditorHandle } from "../code/CodeEditor.tsx";
import { IconBack, IconPlus } from "../components/Icons.tsx";
import { ConfirmDialog, Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const base = (mf: string) => `/m/${encodeURIComponent(mf)}/scripts`;
const fail = (title: string, e: unknown) => pushToast({ level: "warn", title, text: codeErrText(e) });

// small inline glyphs for the run buttons (no emoji, no icon font)
const Glyph = ({ d }: { d: string }) => (
  <svg width={12} height={12} viewBox="0 0 12 12" aria-hidden="true" fill="currentColor">
    <path d={d} />
  </svg>
);
const RestartGlyph = () => (
  <svg width={12} height={12} viewBox="0 0 12 12" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7">
    <path d="M10 6.5A4 4 0 1 1 8.6 3" />
    <path d="M9.6 0.8v2.8H6.8" />
  </svg>
);
const PLAY = "M2.5 1.5l8 4.5-8 4.5z";
const STOP = "M2 2h8v8H2z";

export default function CloudScriptsScreen({ id }: { id: string }) {
  const conn = getConn(id);
  if (!conn) return null;
  const r = route.value;
  const script = r.name === "code" ? r.script : undefined;
  return <Scripts key={id} conn={conn} mf={id} script={script} />;
}

/** Sends `list` while connected (again after a reconnect: the subscription belongs to the socket). */
function useList(conn: MainframeConn) {
  const [error, setError] = useState<string | null>(null);
  const open = conn.status.value === "open";
  const allowed = conn.can("code.edit");
  useEffect(() => {
    if (!open || !allowed) return;
    let live = true;
    conn
      .codeOp("list")
      .then(() => live && setError(null))
      .catch((e: Error) => live && setError(e.message));
    return () => {
      live = false;
    };
  }, [open, allowed]);
  return error;
}

function Scripts({ conn, mf, script }: { conn: MainframeConn; mf: string; script?: string }) {
  const L = lang.value;
  const error = useList(conn);
  const doc = conn.code.value;
  const allowed = conn.can("code.edit");
  const name = conn.info.value?.name ?? "Mainframe";
  const [creating, setCreating] = useState(false);
  const blocked = !allowed ? ct("noPerm") : error === "disabled" || doc?.enabled === false ? ct("disabled") : error === "timeout" || error === "invalid_message" ? ct("unsupported") : error ? codeErrText(new Error(error)) : null;
  const summary = script ? doc?.scripts.find((s) => s.id === script) : undefined;

  if (script && doc && !blocked)
    return summary ? (
      <Workspace key={script} conn={conn} mf={mf} s={summary} limits={doc.limits} />
    ) : (
      <main class="page code-page" id="main" tabIndex={-1} lang={L}>
        <Head mf={mf} title={ct("title")} back={() => navigate(base(mf))} backLabel={ct("allScripts")} />
        <p class="muted">{ct("err.unknown_script")}</p>
      </main>
    );

  const running = doc?.scripts.filter((s) => s.status === "running").length ?? 0;
  const mfDown = conn.info.value && (!conn.info.value.online || !conn.info.value.enabled);
  return (
    <main class="page page-narrow-wide rules-page code-page" id="main" tabIndex={-1} lang={L}>
      <Head mf={mf} title={ct("title")} back={() => navigate(`/m/${encodeURIComponent(mf)}`)} backLabel={ct("back")}>
        {doc && !blocked && (
          <button type="button" class="pxbtn pxbtn-gold" disabled={doc.scripts.length >= doc.limits.maxScripts} onClick={() => setCreating(true)}>
            <span>
              <IconPlus size={14} /> {ct("new")}
            </span>
          </button>
        )}
      </Head>
      <p class="muted">
        {ct("intro", { name })} <DocsLink to="/docs/scripts">{ct("docs")}</DocsLink>
      </p>
      {blocked ? (
        <p class="muted">{blocked}</p>
      ) : !doc ? (
        <p class="muted">{conn.status.value === "open" ? ct("loading") : ct("offline")}</p>
      ) : (
        <>
          <p class="muted small">{ct("count", { n: doc.scripts.length, max: doc.limits.maxScripts, r: running, maxR: doc.limits.maxRunning })}</p>
          {mfDown && <p class="field-hint warn">{ct("mfOffline")}</p>}
          {conn.status.value !== "open" && <p class="field-hint warn">{ct("offline")}</p>}
          {doc.scripts.length === 0 ? (
            <div class="rules-empty">
              <b>{ct("empty")}</b>
              <span class="muted small">{ct("emptyHint")}</span>
            </div>
          ) : (
            <ul class="rule-list code-list">
              {doc.scripts.map((s) => (
                <ScriptRow key={s.id} s={s} conn={conn} mf={mf} />
              ))}
            </ul>
          )}
        </>
      )}
      {creating && doc && <NewScript conn={conn} mf={mf} taken={doc.scripts.map((s) => s.name)} onClose={() => setCreating(false)} />}
    </main>
  );
}

function Head(props: { mf: string; title: string; back: () => void; backLabel: string; children?: ComponentChildren; small?: boolean }) {
  return (
    <div class="page-head acc-head code-head">
      <div class="code-head-title">
        <button type="button" class="link-back" onClick={props.back}>
          <IconBack size={18} /> {props.backLabel}
        </button>
        <h1 class={`px-heading ${props.small ? "code-h-small" : ""}`}>{props.title}</h1>
      </div>
      {props.children}
    </div>
  );
}

// ------------------------------------------------------------------ list

function statusText(s: ScriptSummary) {
  return ct(`st.${s.status}` as CodeKey);
}
/** Why it is not running, as a sentence ("" while running or without a reason). */
function reasonText(s: ScriptSummary) {
  if (s.status === "running" || !s.reason) return "";
  const key = `why.${s.reason}`;
  return hasKey(key) ? ct(key) : "";
}
const countText = (n: number, one: CodeKey, many: CodeKey) => (n === 1 ? ct(one) : ct(many, { n }));

/** Start / stop / restart with the busy state and the error as a toast. */
function useRun(conn: MainframeConn, s: ScriptSummary) {
  const [busy, setBusy] = useState<string | null>(null);
  const run = async (op: "start" | "stop" | "restart") => {
    setBusy(op);
    try {
      await conn.codeOp(op, { id: s.id });
    } catch (e) {
      fail(s.name, e);
    } finally {
      setBusy(null);
    }
  };
  return { busy, run };
}

function RunButtons({ conn, s }: { conn: MainframeConn; s: ScriptSummary }) {
  const { busy, run } = useRun(conn, s);
  const running = s.status === "running";
  return (
    <>
      {running ? (
        <button type="button" class="btn btn-small code-stop" disabled={!!busy} onClick={() => run("stop")}>
          <Glyph d={STOP} /> {ct("stop")}
        </button>
      ) : (
        <button type="button" class="btn btn-small btn-run code-start" disabled={!!busy} onClick={() => run("start")}>
          <Glyph d={PLAY} /> {ct("start")}
        </button>
      )}
      {running && (
        <button type="button" class="btn btn-small" disabled={!!busy} onClick={() => run("restart")} title={ct("restart")}>
          <RestartGlyph /> {ct("restart")}
        </button>
      )}
    </>
  );
}

function AutostartToggle({ conn, s }: { conn: MainframeConn; s: ScriptSummary }) {
  const [pending, setPending] = useState<boolean | null>(null);
  const on = pending ?? s.autostart;
  const set = async (enabled: boolean) => {
    setPending(enabled);
    try {
      await conn.codeOp("autostart", { id: s.id, enabled });
    } catch (e) {
      fail(s.name, e);
    } finally {
      setPending(null);
    }
  };
  return (
    <label class="check-row code-auto" title={ct("autostartHint")}>
      <input type="checkbox" checked={on} disabled={pending !== null} onChange={(e) => set((e.target as HTMLInputElement).checked)} />
      <span>{ct("autostart")}</span>
    </label>
  );
}

function CpuMeter({ s }: { s: ScriptSummary }) {
  const share = cpuShare(s.cpu);
  const pct = Math.min(100, Math.round(share * 100));
  const ms = s.cpu.avg < 10 ? s.cpu.avg.toFixed(1) : String(Math.round(s.cpu.avg));
  return (
    <span class={`code-cpu ${share >= 1 ? "full" : share >= 0.6 ? "high" : ""}`} title={share >= 1 ? ct("cpuPaused") : undefined}>
      <span class="code-cpu-label">{ct("cpu")}</span>
      <span class="code-cpu-bar" role="meter" aria-valuemin={0} aria-valuemax={s.cpu.limit} aria-valuenow={s.cpu.avg} aria-label={ct("cpu")}>
        <span style={{ width: `${pct}%` }} />
      </span>
      <span class="code-cpu-text">{ct("cpuText", { ms, limit: s.cpu.limit })}</span>
    </span>
  );
}

function StatusTag({ s }: { s: ScriptSummary }) {
  return (
    <span class={`code-status st-${s.status}`}>
      <span class="code-dot" aria-hidden="true" />
      {statusText(s)}
    </span>
  );
}

function ErrorLine({ e, onGo }: { e: NonNullable<ScriptSummary["lastError"]>; onGo?: (line: number) => void }) {
  const L = lang.value;
  return (
    <p class="code-err" role="status">
      <span class="kw">{ct("lastError")}</span>
      {e.line && onGo ? (
        <button type="button" class="link-btn" onClick={() => onGo(e.line!)}>
          {ct("lineN", { n: e.line })}
        </button>
      ) : e.line ? (
        <b>{ct("lineN", { n: e.line })}</b>
      ) : null}
      <span class="code-err-text">{e.message}</span>
      {e.time > 0 && <span class="muted small">{agoText(e.time, L)}</span>}
    </p>
  );
}

function ScriptRow({ s, conn, mf }: { s: ScriptSummary; conn: MainframeConn; mf: string }) {
  const L = lang.value;
  const [renaming, setRenaming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const href = `#${base(mf)}/${encodeURIComponent(s.id)}`;
  const why = reasonText(s);
  return (
    <li class={`rule-row code-row st-${s.status}`}>
      <div class="rule-top">
        <a class="rule-name code-name" href={href}>
          {s.name}
        </a>
        <StatusTag s={s} />
        <span class="rule-tools">
          <RunButtons conn={conn} s={s} />
          <a class="btn btn-small code-edit" href={href}>
            {ct("edit")}
          </a>
          <button type="button" class="btn btn-small" onClick={() => setRenaming(true)}>
            {ct("rename")}
          </button>
          <button type="button" class="btn btn-small btn-danger-ghost" onClick={() => setDeleting(true)}>
            {ct("delete")}
          </button>
        </span>
      </div>
      <div class="code-row-stats">
        {s.status === "running" && <CpuMeter s={s} />}
        {s.status === "running" && (
          <span class="muted small">
            {countText(s.handlers, "handler1", "handlers")} · {countText(s.timers, "timer1", "timers")}
          </span>
        )}
        {why && <span class="muted small">{why}</span>}
        <AutostartToggle conn={conn} s={s} />
      </div>
      {s.lastError && <ErrorLine e={s.lastError} />}
      <p class="muted small rule-meta">
        {ct("meta", { v: s.version, size: fmtBytes(s.size), author: s.author || "?", ago: agoText(s.updated, L) })}
        {s.status === "running" && s.startedAt && s.startedBy && ` · ${ct("startedBy", { by: s.startedBy, ago: agoText(s.startedAt, L) })}`}
      </p>
      {renaming && <RenameScript conn={conn} s={s} onClose={() => setRenaming(false)} />}
      {deleting && <DeleteScript conn={conn} s={s} onClose={() => setDeleting(false)} />}
    </li>
  );
}

function NewScript({ conn, mf, taken, onClose }: { conn: MainframeConn; mf: string; taken: string[]; onClose: () => void }) {
  const L = lang.value;
  const list = useMemo(() => templates(L), [L]);
  const [pick, setPick] = useState<string>(list[0]?.id ?? "");
  const [name, setName] = useState(() => uniqueName(list[0]?.name ?? "Script", taken));
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const choose = (id: string) => {
    setPick(id);
    const tp = list.find((x) => x.id === id);
    if (!touched) setName(uniqueName(tp?.name ?? "Script", taken));
  };
  const create = async () => {
    const n = cleanName(name);
    if (!n) return setError(ct("nameBad", { max: MAX_NAME }));
    const tp = list.find((x) => x.id === pick);
    setBusy(true);
    setError(null);
    try {
      const r = await conn.codeOp<{ id: string }>("save", { name: n, source: tp?.source ?? BLANK_SOURCE });
      onClose();
      navigate(`${base(mf)}/${encodeURIComponent(r.id)}`);
    } catch (e) {
      setError(codeErrText(e));
    } finally {
      setBusy(false);
    }
  };
  const options = [...list.map((x) => ({ id: x.id, title: x.title, doc: x.doc })), { id: "", title: ct("blank"), doc: ct("blankDoc") }];
  return (
    <Sheet title={ct("newTitle")} onClose={onClose}>
      <form
        class="rule-editor code-new"
        lang={L}
        onSubmit={(e) => {
          e.preventDefault();
          create();
        }}
      >
        <fieldset class="rule-block">
          <legend>
            <span class="kw">{ct("startFrom")}</span>
          </legend>
          <div class="code-templates" role="radiogroup" aria-label={ct("startFrom")}>
            {options.map((o) => (
              <button key={o.id || "blank"} type="button" role="radio" aria-checked={pick === o.id} class={`code-template ${pick === o.id ? "on" : ""}`} onClick={() => choose(o.id)}>
                <b>{o.title}</b>
                <span class="muted small">{o.doc}</span>
              </button>
            ))}
          </div>
        </fieldset>
        <label class="field">
          <span class="field-label">{ct("name")}</span>
          <input
            class="input"
            maxLength={MAX_NAME}
            value={name}
            placeholder={ct("namePh")}
            onInput={(e) => {
              setTouched(true);
              setName((e.target as HTMLInputElement).value);
            }}
          />
        </label>
        {error && (
          <p class="form-error" role="alert">
            {error}
          </p>
        )}
        <div class="perm-actions">
          <span class="perm-spacer" />
          <button type="button" class="btn" onClick={onClose}>
            {ct("back")}
          </button>
          <button type="submit" class="pxbtn pxbtn-gold" disabled={busy}>
            <span>{busy ? ct("creating") : ct("create")}</span>
          </button>
        </div>
      </form>
    </Sheet>
  );
}

function RenameScript({ conn, s, onClose }: { conn: MainframeConn; s: ScriptSummary; onClose: () => void }) {
  const [name, setName] = useState(s.name);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const save = async () => {
    const n = cleanName(name);
    if (!n) return setError(ct("nameBad", { max: MAX_NAME }));
    if (n === s.name) return onClose();
    setBusy(true);
    try {
      await conn.codeOp("rename", { id: s.id, name: n });
      onClose();
    } catch (e) {
      setError(codeErrText(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet title={ct("renameTitle")} onClose={onClose}>
      <form
        class="rule-editor"
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <label class="field">
          <span class="field-label">{ct("name")}</span>
          <input class="input" maxLength={MAX_NAME} value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
        </label>
        {error && (
          <p class="form-error" role="alert">
            {error}
          </p>
        )}
        <div class="perm-actions">
          <span class="perm-spacer" />
          <button type="button" class="btn" onClick={onClose}>
            {ct("back")}
          </button>
          <button type="submit" class="pxbtn pxbtn-gold" disabled={busy}>
            <span>{busy ? ct("saving") : ct("rename")}</span>
          </button>
        </div>
      </form>
    </Sheet>
  );
}

function DeleteScript({ conn, s, onClose, onDone }: { conn: MainframeConn; s: ScriptSummary; onClose: () => void; onDone?: () => void }) {
  return (
    <ConfirmDialog
      title={ct("deleteQ", { name: s.name })}
      text={ct("deleteText")}
      confirmLabel={ct("delete")}
      color="red"
      onCancel={onClose}
      onConfirm={async () => {
        onClose();
        try {
          await conn.codeOp("delete", { id: s.id });
          pushToast({ level: "info", title: ct("title"), text: ct("deleted", { name: s.name }) });
          onDone?.();
        } catch (e) {
          fail(s.name, e);
        }
      }}
    />
  );
}

// ------------------------------------------------------------------ workspace

let editorChunk: ComponentType<any> | null = null;
/** The CodeMirror chunk, loaded once. */
function useEditorChunk() {
  const [C, setC] = useState<ComponentType<any> | null>(() => editorChunk);
  useEffect(() => {
    if (editorChunk) return;
    import("../code/CodeEditor.tsx").then((m) => setC(() => (editorChunk = m.default)));
  }, []);
  return C;
}

type Side = "log" | "versions";
type Loaded = { source: string; version: number; author: string };

function Workspace({ conn, mf, s, limits }: { conn: MainframeConn; mf: string; s: ScriptSummary; limits: CodeLimits }) {
  const L = lang.value;
  const Editor = useEditorChunk();
  const handle = useRef<EditorHandle | null>(null);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [diags, setDiags] = useState<{ text: string; list: Diagnostic[] } | null>(null);
  const [checking, setChecking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [note, setNote] = useState<{ kind: "ok" | "warn"; text: string } | null>(null);
  const [side, setSide] = useState<Side>("log");
  const [phone, setPhone] = useState<"code" | "side">("code");
  const [conflict, setConflict] = useState<{ theirs: string; version: number; author: string } | null>(null);
  const [leaving, setLeaving] = useState<(() => void) | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [versionsKey, setVersionsKey] = useState(0);
  const dirty = !!loaded && text !== loaded.source;
  const readOnly = !conn.can("code.edit");
  const open = conn.status.value === "open";

  // load the source; again when the socket comes back (the log subscription is per socket)
  const dirtyRef = useRef(false);
  dirtyRef.current = dirty;
  const load = async () => {
    try {
      const r = await conn.codeOp<{ script: { source?: string; version?: number; author?: string } }>("get", { id: s.id });
      const src = typeof r.script?.source === "string" ? r.script.source : "";
      const l = { source: src, version: Number(r.script?.version) || s.version, author: String(r.script?.author ?? s.author) };
      setLoaded(l);
      setText(src);
      handle.current?.setText(src);
      setLoadError(null);
      return l;
    } catch (e) {
      setLoadError(codeErrText(e));
      return null;
    }
  };
  useEffect(() => {
    // after a reconnect, unsaved text stays: a newer version shows as a banner instead
    if (open && !dirtyRef.current) void load();
  }, [open]);
  useEffect(() => {
    if (!open) return;
    conn.codeOp("logs", { id: s.id }).catch(() => {});
    return () => void conn.codeOp("unwatch", { id: s.id }).catch(() => {});
  }, [open, s.id]);

  // someone else saved: follow when nothing is edited here, else offer it
  const newer = loaded && !saving && s.version > loaded.version ? s : null;
  useEffect(() => {
    if (!newer || dirty) return;
    load().then((l) => l && pushToast({ level: "info", title: s.name, text: ct("updated", { v: l.version, author: l.author }) }));
  }, [newer?.version]);

  // the compile check, debounced; only for text that changed since the last check
  useEffect(() => {
    if (!loaded || readOnly || !open) return;
    if (diags?.text === text) return;
    const timer = setTimeout(async () => {
      setChecking(true);
      try {
        const r = await conn.codeOp<{ diagnostics: unknown }>("check", { source: text });
        setDiags({ text, list: parseDiagnostics(r.diagnostics) });
      } catch {
        // rate limited or offline: the next edit checks again
      } finally {
        setChecking(false);
      }
    }, 700);
    return () => clearTimeout(timer);
  }, [text, loaded, open]);

  const markers = useMemo<Marker[]>(() => {
    const current = diags && diags.text === text ? diags.list : [];
    const runtimeApplies = !!loaded && text === loaded.source && loaded.version === s.version;
    return markersFor(text, current, s.lastError, runtimeApplies);
  }, [diags, text, loaded, s.lastError, s.version]);
  const problems = markers.length;

  // leaving with changes asks first; a reload or closed tab gets the browser's question
  useEffect(() => {
    if (!dirty) return;
    const h = (e: BeforeUnloadEvent) => e.preventDefault();
    addEventListener("beforeunload", h);
    return () => removeEventListener("beforeunload", h);
  }, [dirty]);
  const leave = (go: () => void) => (dirty ? setLeaving(() => go) : go());

  const saveRef = useRef<(force?: number) => void>(() => {});
  const save = async (baseOverride?: number) => {
    if (!loaded || saving || readOnly) return;
    const source = handle.current?.text() ?? text;
    if (byteSize(source) > limits.maxSource) return pushToast({ level: "warn", title: s.name, text: ct("err.too_large") });
    setSaving(true);
    setNote(null);
    try {
      const r = await conn.codeOp<{ version: number; diagnostics: unknown }>("save", { id: s.id, name: s.name, source, baseVersion: baseOverride ?? loaded.version });
      const list = parseDiagnostics(r.diagnostics);
      setLoaded({ source, version: Number(r.version) || loaded.version + 1, author: loaded.author });
      setDiags({ text: source, list });
      const v = Number(r.version) || loaded.version + 1;
      setNote(list.some((d) => d.severity === "error") ? { kind: "warn", text: ct("savedErrors", { v }) } : { kind: "ok", text: ct("saved", { v }) });
      setVersionsKey((k) => k + 1);
      setConflict(null);
    } catch (e) {
      if ((e as Error).message === "changed") {
        try {
          const g = await conn.codeOp<{ script: { source?: string; version?: number; author?: string } }>("get", { id: s.id });
          setConflict({ theirs: String(g.script?.source ?? ""), version: Number(g.script?.version) || 0, author: String(g.script?.author ?? "?") });
        } catch (e2) {
          fail(s.name, e2);
        }
      } else fail(s.name, e);
    } finally {
      setSaving(false);
    }
  };
  saveRef.current = save;
  // Ctrl/⌘+S anywhere on the workspace (the editor has its own binding)
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") {
        e.preventDefault();
        saveRef.current();
      }
    };
    addEventListener("keydown", h);
    return () => removeEventListener("keydown", h);
  }, []);
  useEffect(() => {
    if (!note || note.kind !== "ok") return;
    const t = setTimeout(() => setNote(null), 4000);
    return () => clearTimeout(t);
  }, [note]);

  const size = byteSize(text);
  const goLine = (n: number) => {
    setPhone("code");
    handle.current?.gotoLine(n);
  };
  const runVersion = (s as ScriptSummary & { runningVersion?: number }).runningVersion;
  const why = reasonText(s);
  const sideTabs = (
    <div class="seg-ctl code-side-tabs" role="tablist" aria-label={ct("log")}>
      {(["log", "versions"] as Side[]).map((x) => (
        <button key={x} type="button" role="tab" aria-selected={side === x} class={side === x ? "on" : ""} onClick={() => setSide(x)}>
          {ct(x)}
        </button>
      ))}
    </div>
  );
  return (
    <main class={`page code-page code-ws view-${phone}`} id="main" tabIndex={-1} lang={L}>
      <Head mf={mf} title={s.name} small back={() => leave(() => navigate(base(mf)))} backLabel={ct("allScripts")}>
        <StatusTag s={s} />
      </Head>
      <div class="code-toolbar" role="toolbar" aria-label={s.name}>
        <button type="button" class="pxbtn pxbtn-gold code-save" disabled={!dirty || saving || readOnly} onClick={() => save()} title="Ctrl+S / ⌘S">
          <span>{saving ? ct("saving") : ct("save")}</span>
        </button>
        <RunButtons conn={conn} s={s} />
        <AutostartToggle conn={conn} s={s} />
        <span class="code-toolbar-gap" />
        <button type="button" class="btn btn-small" onClick={() => setRenaming(true)}>
          {ct("rename")}
        </button>
        <button type="button" class="btn btn-small btn-danger-ghost" onClick={() => setDeleting(true)}>
          {ct("delete")}
        </button>
      </div>
      <div class="code-statusline muted small">
        {s.status === "running" && <CpuMeter s={s} />}
        {s.status === "running" && (
          <span>
            {countText(s.handlers, "handler1", "handlers")} · {countText(s.timers, "timer1", "timers")}
          </span>
        )}
        {s.status === "running" && s.startedAt && s.startedBy && <span>{ct("startedBy", { by: s.startedBy, ago: agoText(s.startedAt, L) })}</span>}
        {why && <span>{why}</span>}
        {s.status === "running" && runVersion && runVersion < s.version && <span class="t-warn">{ct("restartHint", { run: runVersion, v: s.version })}</span>}
      </div>
      {s.lastError && <ErrorLine e={s.lastError} onGo={goLine} />}
      {newer && dirty && (
        <p class="field-hint warn code-newer">
          {ct("newer", { v: newer.version, author: newer.author })}{" "}
          <button type="button" class="link-btn" onClick={() => load()}>
            {ct("loadNewer")}
          </button>
        </p>
      )}
      <div class="seg-ctl code-phone-tabs" role="tablist">
        {(
          [
            ["code", ct("code")],
            ["log", ct("log")],
            ["versions", ct("versions")],
          ] as const
        ).map(([k, label]) => {
          const on = k === "code" ? phone === "code" : phone === "side" && side === k;
          return (
            <button
              key={k}
              type="button"
              role="tab"
              aria-selected={on}
              class={on ? "on" : ""}
              onClick={() => {
                if (k === "code") setPhone("code");
                else {
                  setSide(k);
                  setPhone("side");
                }
              }}
            >
              {label}
            </button>
          );
        })}
      </div>
      <div class="code-grid">
        <section class="code-main" aria-label={ct("code")}>
          <div class="code-ide">
            {loadError ? (
              <p class="form-error code-load-error">{loadError}</p>
            ) : Editor && loaded ? (
              <Editor
                initial={loaded.source}
                lang={L}
                readOnly={readOnly}
                label={`${s.name} – Lua`}
                markers={markers}
                markersText={text}
                onChange={setText}
                onSave={() => saveRef.current()}
                handle={handle}
              />
            ) : (
              <p class="muted code-editor-loading">{ct("editorLoading")}</p>
            )}
          </div>
          <div class="code-editor-foot muted small">
            {dirty && <span class="code-dirty">● {ct("unsaved")}</span>}
            <span class={size > limits.maxSource ? "t-red" : ""}>{ct("size", { size: fmtBytes(size), max: fmtBytes(limits.maxSource) })}</span>
            <span>{checking ? ct("checking") : problems === 0 ? ct("noProblems") : problems === 1 ? ct("problem1") : ct("problems", { n: problems })}</span>
            {problems > 0 && (
              <button type="button" class="link-btn" onClick={() => goLine(lineOf(text, markers[0].from))}>
                {ct("lineN", { n: lineOf(text, markers[0].from) })}
              </button>
            )}
            {readOnly && <span>{ct("readOnly")}</span>}
            {note && <span class={note.kind === "ok" ? "t-ok" : "t-warn"}>{note.text}</span>}
          </div>
        </section>
        <section class="code-side" aria-label={ct(side)}>
          <div class="code-side-head">{sideTabs}</div>
          {side === "log" ? (
            <LogPanel lines={conn.codeLogs.value[s.id] ?? null} />
          ) : (
            <VersionsPanel
              key={versionsKey}
              conn={conn}
              s={s}
              limits={limits}
              current={loaded?.source ?? ""}
              onReverted={async (v) => {
                pushToast({ level: "info", title: s.name, text: ct("reverted", { v }) });
                await load();
                setVersionsKey((k) => k + 1);
              }}
            />
          )}
        </section>
      </div>
      {conflict && (
        <Sheet title={ct("conflictTitle")} onClose={() => setConflict(null)} wide>
          <div class="rule-editor">
            <p>{ct("conflictText", { author: conflict.author, v: conflict.version })}</p>
            <DiffView a={conflict.theirs} b={handle.current?.text() ?? text} />
            <div class="perm-actions">
              <span class="perm-spacer" />
              <button
                type="button"
                class="btn"
                onClick={() => {
                  handle.current?.setText(conflict.theirs);
                  setLoaded({ source: conflict.theirs, version: conflict.version, author: conflict.author });
                  setText(conflict.theirs);
                  setConflict(null);
                }}
              >
                {ct("takeTheirs")}
              </button>
              <button type="button" class="pxbtn pxbtn-gold" disabled={saving} onClick={() => save(conflict.version)}>
                <span>{ct("keepMine")}</span>
              </button>
            </div>
          </div>
        </Sheet>
      )}
      {leaving && (
        <ConfirmDialog
          title={ct("discardQ")}
          text={ct("discardText", { name: s.name })}
          confirmLabel={ct("discard")}
          color="red"
          onCancel={() => setLeaving(null)}
          onConfirm={() => {
            const go = leaving;
            setLeaving(null);
            setLoaded(null);
            go();
          }}
        />
      )}
      {renaming && <RenameScript conn={conn} s={s} onClose={() => setRenaming(false)} />}
      {deleting && <DeleteScript conn={conn} s={s} onClose={() => setDeleting(false)} onDone={() => navigate(base(mf))} />}
    </main>
  );
}

const lineOf = (text: string, pos: number) => {
  let n = 1;
  for (let i = 0; i < pos && i < text.length; i++) if (text.charCodeAt(i) === 10) n++;
  return n;
};

// ------------------------------------------------------------------ log

function LogPanel({ lines }: { lines: LogLine[] | null }) {
  const [follow, setFollow] = useState(true);
  const [clearedAt, setClearedAt] = useState(0);
  const box = useRef<HTMLOListElement>(null);
  const shown = lines ? visibleLog(lines, clearedAt) : [];
  useEffect(() => {
    if (follow && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [shown.length, follow, lines]);
  return (
    <div class="code-log">
      <div class="code-log-tools">
        <label class="check-row">
          <input type="checkbox" checked={follow} onChange={(e) => setFollow((e.target as HTMLInputElement).checked)} />
          <span>{ct("follow")}</span>
        </label>
        <button type="button" class="btn btn-small" disabled={!shown.length} onClick={() => setClearedAt(lines?.[lines.length - 1]?.ts ?? Date.now())}>
          {ct("clearView")}
        </button>
        <span class="muted small">{ct("logLines", { n: shown.length })}</span>
      </div>
      <ol
        ref={box}
        class="code-log-lines"
        aria-live="polite"
        onScroll={(e) => {
          // scrolling up stops following; back at the bottom it follows again
          const el = e.currentTarget;
          const atEnd = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
          if (atEnd !== follow) setFollow(atEnd);
        }}
      >
        {lines === null ? (
          <li class="muted">{ct("loading")}</li>
        ) : shown.length === 0 ? (
          <li class="muted">{clearedAt ? ct("logCleared") : ct("logEmpty")}</li>
        ) : (
          shown.map((l, i) => (
            <li key={`${l.ts}-${i}`} class={`lv-${l.level}`}>
              <time>{logTime(l.ts)}</time>
              <span class="code-log-text">{l.text}</span>
            </li>
          ))
        )}
      </ol>
    </div>
  );
}

// ------------------------------------------------------------------ versions

function VersionsPanel(props: { conn: MainframeConn; s: ScriptSummary; limits: CodeLimits; current: string; onReverted: (v: number) => void }) {
  const { conn, s } = props;
  const L = lang.value;
  const [list, setList] = useState<VersionMeta[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pick, setPick] = useState<number | null>(null);
  const [mode, setMode] = useState<"current" | "previous">("current");
  const [sources, setSources] = useState<Record<number, string>>({});
  const [reverting, setReverting] = useState<number | null>(null);
  useEffect(() => {
    conn
      .codeOp<{ versions: unknown }>("versions", { id: s.id })
      .then((r) => setList(parseVersions(r.versions)))
      .catch((e) => setError(codeErrText(e)));
  }, [s.id]);
  const need = async (v: number) => {
    if (sources[v] !== undefined) return;
    try {
      const r = await conn.codeOp<{ source: string }>("version", { id: s.id, version: v });
      setSources((x) => ({ ...x, [v]: String(r.source ?? "") }));
    } catch (e) {
      fail(s.name, e);
    }
  };
  const idx = list && pick !== null ? list.findIndex((v) => v.version === pick) : -1;
  const prev = list && idx >= 0 ? list[idx + 1] : undefined;
  useEffect(() => {
    if (pick === null) return;
    void need(pick);
    if (mode === "previous" && prev) void need(prev.version);
  }, [pick, mode, prev?.version]);
  const a = mode === "current" ? sources[pick ?? -1] : prev ? sources[prev.version] : undefined;
  const b = mode === "current" ? props.current : sources[pick ?? -1];
  return (
    <div class="code-versions">
      <p class="muted small">{ct("versionsHint", { n: props.limits.versionsKept })}</p>
      {error && <p class="form-error">{error}</p>}
      {!list && !error && <p class="muted">{ct("loading")}</p>}
      {list && (
        <ol class="code-version-list">
          {list.map((v) => {
            const isCurrent = v.version === s.version;
            return (
              <li key={v.version}>
                <button type="button" class={`code-version ${pick === v.version ? "on" : ""}`} aria-pressed={pick === v.version} onClick={() => setPick(v.version)}>
                  <b>v{v.version}</b>
                  {isCurrent && <span class="tag tag-gray">{ct("current")}</span>}
                  <span class="muted small">
                    {agoText(v.time, L)} · {v.author} · {fmtBytes(v.size)}
                  </span>
                  {v.note && <span class="muted small code-version-note">{v.note}</span>}
                </button>
              </li>
            );
          })}
        </ol>
      )}
      {pick === null ? (
        list && list.length > 0 && <p class="muted small">{ct("pickVersion")}</p>
      ) : (
        <div class="code-diff-box">
          <div class="code-diff-head">
            <div class="seg-ctl" role="tablist">
              <button type="button" role="tab" aria-selected={mode === "current"} class={mode === "current" ? "on" : ""} onClick={() => setMode("current")}>
                {ct("vsCurrent")}
              </button>
              <button type="button" role="tab" aria-selected={mode === "previous"} class={mode === "previous" ? "on" : ""} onClick={() => setMode("previous")}>
                {ct("vsPrevious")}
              </button>
            </div>
            {pick !== s.version && (
              <button type="button" class="btn btn-small" disabled={reverting !== null} onClick={() => setReverting(pick)}>
                {ct("revert", { v: pick })}
              </button>
            )}
          </div>
          {mode === "previous" && !prev ? <p class="muted small">{ct("oldest")}</p> : a === undefined || b === undefined ? <p class="muted small">{ct("loading")}</p> : <DiffView a={a} b={b} />}
        </div>
      )}
      {reverting !== null && (
        <ConfirmDialog
          title={ct("revertQ", { v: reverting })}
          text={ct("revertText")}
          confirmLabel={ct("revert", { v: reverting })}
          color="gold"
          onCancel={() => setReverting(null)}
          onConfirm={async () => {
            const v = reverting;
            setReverting(null);
            try {
              const r = await conn.codeOp<{ version: number }>("revert", { id: s.id, version: v });
              props.onReverted(Number(r.version) || v);
            } catch (e) {
              fail(s.name, e);
            }
          }}
        />
      )}
    </div>
  );
}

/** Old `a` against new `b`, unchanged runs folded. */
function DiffView({ a, b }: { a: string; b: string }) {
  const d = useMemo(() => diffLines(a, b), [a, b]);
  const st = diffStats(d);
  const rows = useMemo(() => foldDiff(d), [d]);
  if (!st.add && !st.del) return <p class="muted small">{ct("noDiff")}</p>;
  return (
    <div class="code-diff">
      <p class="muted small code-diff-stats">
        <span class="t-ok">+{st.add}</span> <span class="t-red">−{st.del}</span>
      </p>
      <ol class="code-diff-lines">
        {rows.map((r, i) =>
          r.kind === "skip" ? (
            <li key={i} class="dl-skip">
              {ct("skipLines", { n: r.count })}
            </li>
          ) : (
            <li key={i} class={`dl-${r.kind}`}>
              <span class="dl-no">{r.kind === "add" ? "" : r.a}</span>
              <span class="dl-no">{r.kind === "del" ? "" : r.b}</span>
              <span class="dl-sign">{r.kind === "add" ? "+" : r.kind === "del" ? "−" : " "}</span>
              <span class="dl-text">{r.text || " "}</span>
            </li>
          ),
        )}
      </ol>
    </div>
  );
}
