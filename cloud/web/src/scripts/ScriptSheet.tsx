// Script import (lazy chunk): upload a .lua file or pasted code to the computer
// behind a card, pick from the example gallery, list/download/delete its files.
// Admin only; the mainframe PIN (if set) is asked once and kept in memory for this sheet.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { MAX_SCRIPT, ScriptError, fmtBytes, pathError, scriptApi, scriptErrorText, suggestName, type FileList } from "../lib/scripts.ts";
import { pushToast } from "../lib/ui.ts";
import { ConfirmDialog, PinDialog, Sheet, type PinState } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";
import { EXAMPLES, type Example } from "./examples.ts";
import type { ComponentType } from "preact";

type EditorProps = { conn: MainframeConn; uplinkId: string; title: string; initialPath?: string; onClose: () => void };
let EditorComp: ComponentType<EditorProps> | null = null;
/** The Lua editor (CodeMirror) is its own chunk, loaded on first use. */
function EditorLoader(props: EditorProps) {
  const [C, setC] = useState<ComponentType<EditorProps> | null>(() => EditorComp);
  useEffect(() => {
    if (!C) import("./Editor.tsx").then((m) => setC(() => (EditorComp = m.default)));
  }, []);
  return C ? <C {...props} /> : null;
}

type Tab = "upload" | "examples" | "files";
type Confirm = { title: string; text: string; label: string; color: string; resolve: (ok: boolean) => void };

export default function ScriptSheet({ conn, uplinkId, title, onClose }: { conn: MainframeConn; uplinkId: string; title: string; onClose: () => void }) {
  const api = useMemo(() => scriptApi(conn.baseUrl.replace(/\/$/, ""), conn.token, conn.info.value?.id ?? "", uplinkId), [conn, uplinkId]);
  const [tab, setTab] = useState<Tab>("upload");
  const [list, setList] = useState<FileList | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [runNow, setRunNow] = useState(true);
  const [startup, setStartup] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [drag, setDrag] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [pinAsk, setPinAsk] = useState<{ state: PinState; resolve: (pin: string | null) => void } | null>(null);
  const [editor, setEditor] = useState<{ path?: string } | null>(null);
  const pin = useRef<string | undefined>(undefined);
  const fileInput = useRef<HTMLInputElement>(null);

  const refresh = () =>
    api
      .list()
      .then((l) => {
        setList(l);
        setListError(null);
      })
      .catch((e) => setListError(scriptErrorText(e)));
  useEffect(() => {
    refresh();
  }, [api]);

  const ask = (c: Omit<Confirm, "resolve">) => new Promise<boolean>((resolve) => setConfirm({ ...c, resolve }));

  /** Runs a write with the PIN; asks for it on pin_required / wrong_pin and retries. */
  async function withPin<T>(fn: (pin?: string) => Promise<T>): Promise<T> {
    for (;;) {
      try {
        const r = await fn(pin.current);
        setPinAsk(null);
        return r;
      } catch (e) {
        if (!(e instanceof ScriptError) || !["pin_required", "wrong_pin", "pin_locked"].includes(e.code)) {
          setPinAsk(null);
          throw e;
        }
        if (e.code !== "pin_required") pin.current = undefined;
        const state: PinState =
          e.code === "pin_locked"
            ? { kind: "locked", text: scriptErrorText(e) }
            : e.code === "wrong_pin"
              ? { kind: "error", text: `Wrong PIN${e.attemptsLeft !== undefined ? ` – ${e.attemptsLeft} attempts left` : ""}.` }
              : { kind: "idle" };
        const p = await new Promise<string | null>((resolve) => setPinAsk({ state, resolve }));
        if (p === null) throw new ScriptError(0, "cancelled");
        pin.current = p;
      }
    }
  }

  const loadFile = async (f: File) => {
    setError(null);
    if (f.size > MAX_SCRIPT) return setError("The file is too large (max 256 KB).");
    const text = await f.text();
    setCode(text);
    setName(suggestName(f.name));
    setDone(null);
  };

  const useExample = (ex: Example) => {
    setCode(ex.code);
    setName(ex.name);
    setTab("upload");
    setDone(null);
    setError(null);
  };

  const upload = async (e?: Event) => {
    e?.preventDefault();
    const path = name.trim();
    const why = pathError(path) ?? (!code.trim() ? "Paste some code or drop a .lua file first." : new Blob([code]).size > MAX_SCRIPT ? "The file is too large (max 256 KB)." : null);
    if (why) return setError(why);
    setError(null);
    setBusy(true);
    try {
      let overwrite = false;
      if (list?.files.some((f) => f.path === path)) {
        overwrite = await ask({ title: `Replace ${path}?`, text: `${path} already exists on ${list.computer.label || `Computer #${list.computer.id}`}. The old version is overwritten.`, label: "Replace", color: "yellow" });
        if (!overwrite) return;
      }
      if (startup && list?.files.some((f) => f.path === "startup.lua") && path !== "startup.lua") {
        if (!(await ask({ title: "Replace startup.lua?", text: `The computer's startup.lua will only run ${path}.`, label: "Replace startup", color: "yellow" }))) return;
      }
      try {
        await withPin((p) => api.write(path, code, { overwrite, startup, pin: p }));
      } catch (err) {
        // someone else created it meanwhile
        if (err instanceof ScriptError && err.code === "exists" && (await ask({ title: `Replace ${path}?`, text: `${path} already exists.`, label: "Replace", color: "yellow" })))
          await withPin((p) => api.write(path, code, { overwrite: true, startup, pin: p }));
        else throw err;
      }
      let msg = `${path} saved on ${list?.computer.label || "the computer"}.`;
      if (runNow) {
        try {
          const started = await withPin((p) => api.run(path, "auto", p));
          msg += started === "event" ? " It runs in a new tab." : "";
        } catch (err) {
          if (!(err instanceof ScriptError) || err.code !== "needs_reboot") throw err;
          if (await ask({ title: "Restart the computer?", text: "This computer cannot start programs in the background (it is not an advanced computer). Restart it to run the script once? A program running now is stopped.", label: "Restart & run", color: "yellow" })) {
            await withPin((p) => api.run(path, "reboot", p));
            msg += " The computer restarts and runs it.";
          }
        }
      }
      setDone(msg);
      pushToast({ level: "info", title: "Script uploaded", text: msg });
      refresh();
    } catch (err) {
      if (!(err instanceof ScriptError && err.code === "cancelled")) setError(scriptErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  const download = async (path: string) => {
    try {
      const text = await api.read(path);
      const url = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = path.split("/").pop()!;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      pushToast({ level: "warn", title: "Download failed", text: scriptErrorText(e) });
    }
  };
  const remove = async (path: string) => {
    if (!(await ask({ title: `Delete ${path}?`, text: "The file is removed from the computer. This cannot be undone.", label: "Delete", color: "red" }))) return;
    try {
      await withPin((p) => api.remove(path, p));
      pushToast({ level: "info", title: "Deleted", text: `${path} was deleted.` });
      refresh();
    } catch (e) {
      if (!(e instanceof ScriptError && e.code === "cancelled")) pushToast({ level: "warn", title: "Delete failed", text: scriptErrorText(e) });
    }
  };

  const comp = list?.computer;
  const nameErr = name ? pathError(name.trim()) : null;
  return (
    <Sheet title={`Scripts – ${title}`} onClose={onClose}>
      <div class="scripts">
        <div class="scripts-comp">
          {comp ? (
            <>
              <span class={`led ${comp.on ? "led-on" : "led-off"}`} aria-hidden="true" />
              <b>{comp.label || `Computer #${comp.id}`}</b>
              <span class="muted small">
                #{comp.id} · {comp.advanced ? "advanced" : "basic"} · {fmtBytes(list!.used)} of {fmtBytes(list!.capacity)} used
              </span>
            </>
          ) : (
            <span class="muted small">{listError ?? "Connecting to the computer…"}</span>
          )}
        </div>
        <button type="button" class="btn btn-small open-editor" onClick={() => setEditor({})}>
          Open the editor
        </button>
        <div class="nd-tabs" role="tablist" aria-label="Scripts">
          {(
            [
              ["upload", "Upload"],
              ["examples", `Examples (${EXAMPLES.length})`],
              ["files", `Files${list ? ` (${list.files.filter((f) => !f.dir).length})` : ""}`],
            ] as const
          ).map(([t, label]) => (
            <button key={t} type="button" role="tab" aria-selected={tab === t} class={`nd-tab ${tab === t ? "on" : ""}`} onClick={() => setTab(t)}>
              {label}
            </button>
          ))}
        </div>

        {tab === "upload" && (
          <form class="form scripts-upload" onSubmit={upload} novalidate>
            <div
              class={`dropzone ${drag ? "drag" : ""}`}
              onDragOver={(e) => {
                e.preventDefault();
                setDrag(true);
              }}
              onDragLeave={() => setDrag(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDrag(false);
                const f = e.dataTransfer?.files?.[0];
                if (f) loadFile(f);
              }}
            >
              <span>Drop a .lua file here or</span>
              <button type="button" class="btn" onClick={() => fileInput.current?.click()}>
                Choose file
              </button>
              <input
                ref={fileInput}
                type="file"
                accept=".lua,text/plain,text/x-lua"
                hidden
                onChange={(e) => {
                  const f = (e.target as HTMLInputElement).files?.[0];
                  if (f) loadFile(f);
                  (e.target as HTMLInputElement).value = "";
                }}
              />
            </div>
            <label class="field">
              <span class="field-label">Code</span>
              <textarea
                class="code-area"
                value={code}
                onInput={(e) => {
                  setCode((e.target as HTMLTextAreaElement).value);
                  setDone(null);
                }}
                placeholder={'-- paste Lua here\nlocal ui = require("cloud.ui")\nlocal dash = ui.connect()\n…'}
                spellcheck={false}
                autoCapitalize="off"
                autoCorrect="off"
                rows={12}
              />
              <span class="field-hint">
                {code ? `${code.split("\n").length} lines · ${fmtBytes(new Blob([code]).size)}` : "Max 256 KB."} The full editor comes later.
              </span>
            </label>
            <label class="field">
              <span class="field-label">File name on the computer</span>
              <input
                class="input"
                value={name}
                onInput={(e) => setName((e.target as HTMLInputElement).value)}
                onBlur={() => name && !/\.lua$/i.test(name) && !name.includes("/") && setName(suggestName(name))}
                placeholder="control-room.lua"
                maxLength={129}
                aria-invalid={!!nameErr}
                spellcheck={false}
                autoCapitalize="off"
              />
              {nameErr ? <span class="field-hint err">{nameErr}</span> : list?.files.some((f) => f.path === name.trim()) && <span class="field-hint warn">Exists – you will be asked before it is replaced.</span>}
            </label>
            <label class="check-row">
              <input type="checkbox" checked={runNow} onChange={(e) => setRunNow((e.target as HTMLInputElement).checked)} />
              Run it now{comp && !comp.advanced ? " (restarts this basic computer)" : " (in a new tab)"}
            </label>
            <label class="check-row">
              <input type="checkbox" checked={startup} onChange={(e) => setStartup((e.target as HTMLInputElement).checked)} />
              Start it automatically (startup.lua)
            </label>
            {error && (
              <p class="form-error" role="alert">
                {error}
              </p>
            )}
            {done && (
              <p class="form-ok" role="status">
                ✓ {done}
              </p>
            )}
            <button type="submit" class="pxbtn pxbtn-gold pxbtn-wide" disabled={busy || !code.trim() || !name.trim() || !!nameErr}>
              <span>{busy ? "Uploading…" : runNow ? "Upload & run" : "Upload"}</span>
            </button>
            <p class="muted small">
              The code runs on this CC computer only, inside CC's sandbox – not on the Minecraft server. Admins only; the mainframe PIN is asked if one is set.{" "}
              <DocsLink to="/docs/lua#upload">More</DocsLink>
            </p>
          </form>
        )}

        {tab === "examples" && (
          <ul class="ex-grid">
            {EXAMPLES.map((ex) => (
              <li key={ex.name} class="ex-card">
                <div class="ex-head">
                  <b>{ex.title}</b>
                  <span class="mono small muted">{ex.name}</span>
                </div>
                <p class="ex-text">{ex.text}</p>
                <pre class="ex-code" aria-hidden="true">
                  {ex.code.split("\n").filter((l) => l.trim() && !l.trim().startsWith("--")).slice(0, 4).join("\n")}
                </pre>
                <div class="ex-foot">
                  <span class="muted small">{ex.lines} lines</span>
                  <button type="button" class="btn" onClick={() => useExample(ex)}>
                    Use this
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {tab === "files" && (
          <div class="files">
            {list && (
              <div class="files-usage" aria-label={`${fmtBytes(list.used)} of ${fmtBytes(list.capacity)} used`}>
                <span
                  ref={(el) => {
                    if (el) el.style.width = `${Math.min(100, Math.round((list.used / Math.max(1, list.capacity)) * 100))}%`;
                  }}
                />
              </div>
            )}
            {listError && <p class="form-error">{listError}</p>}
            {list && list.files.length === 0 && <p class="muted">No files yet. Upload a script or pick an example.</p>}
            <ul class="file-list">
              {list?.files.map((f) =>
                f.dir ? (
                  <li key={f.path} class="file-dir">
                    {f.path}/
                  </li>
                ) : (
                  <li key={f.path} class={`file-row ${f.path.includes("/") ? "nested" : ""}`}>
                    <span class="file-name mono">{f.path.includes("/") ? f.path.split("/")[1] : f.path}</span>
                    <span class="muted small">{fmtBytes(f.size)}</span>
                    <span class="file-actions">
                      <button type="button" class="btn btn-s" onClick={() => setEditor({ path: f.path })}>
                        Edit
                      </button>
                      <button type="button" class="btn btn-s" onClick={() => download(f.path)} aria-label={`Download ${f.path}`}>
                        Download
                      </button>
                      <button type="button" class="btn btn-s btn-danger-ghost" onClick={() => remove(f.path)} aria-label={`Delete ${f.path}`}>
                        Delete
                      </button>
                    </span>
                  </li>
                ),
              )}
            </ul>
          </div>
        )}
      </div>
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          text={confirm.text}
          confirmLabel={confirm.label}
          color={confirm.color}
          onCancel={() => {
            confirm.resolve(false);
            setConfirm(null);
          }}
          onConfirm={() => {
            confirm.resolve(true);
            setConfirm(null);
          }}
        />
      )}
      {pinAsk && (
        <PinDialog
          title="Mainframe PIN"
          text="Changing files on a computer needs the mainframe PIN."
          color="gold"
          state={pinAsk.state}
          onCancel={() => {
            pinAsk.resolve(null);
            setPinAsk(null);
          }}
          onSubmit={(p) => {
            setPinAsk({ ...pinAsk, state: { kind: "checking" } });
            pinAsk.resolve(p);
          }}
        />
      )}
      {editor && <EditorLoader conn={conn} uplinkId={uplinkId} title={title} initialPath={editor.path} onClose={() => (setEditor(null), refresh())} />}
    </Sheet>
  );
}
