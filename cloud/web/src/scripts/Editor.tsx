// Lazy chunk (with CodeMirror 6): the Lua editor for the computer behind a card (v0.5 E).
// File tree up to 8 levels, open/edit/save with a conflict check, new file/folder, rename,
// delete, "Save & run". Uses the script import API; the mainframe PIN is asked once.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { EditorState } from "@codemirror/state";
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter, drawSelection } from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { StreamLanguage, HighlightStyle, bracketMatching, indentOnInput, syntaxHighlighting } from "@codemirror/language";
import { lua } from "@codemirror/legacy-modes/mode/lua";
import { searchKeymap, highlightSelectionMatches } from "@codemirror/search";
import { tags as t } from "@lezer/highlight";
import type { MainframeConn } from "../lib/conn.ts";
import { MAX_SCRIPT, ScriptError, contentHash, fmtBytes, pathError, scriptApi, scriptErrorText, type FileEntry, type FileList } from "../lib/scripts.ts";
import { pushToast } from "../lib/ui.ts";
import { ConfirmDialog, PinDialog, Sheet, type PinState } from "../components/Overlays.tsx";

const highlight = HighlightStyle.define([
  { tag: t.keyword, color: "var(--cm-kw)" },
  { tag: [t.string, t.special(t.string)], color: "var(--cm-str)" },
  { tag: t.number, color: "var(--cm-num)" },
  { tag: t.comment, color: "var(--cm-com)", fontStyle: "italic" },
  { tag: [t.variableName, t.propertyName], color: "var(--cm-var)" },
  { tag: [t.bool, t.null, t.atom], color: "var(--cm-num)" },
  { tag: t.operator, color: "var(--cm-op)" },
  { tag: t.definitionKeyword, color: "var(--cm-kw)" },
  { tag: t.standard(t.variableName), color: "var(--cm-builtin)" },
]);
const theme = EditorView.theme({
  "&": { height: "100%", fontSize: "14px", backgroundColor: "var(--screen)", color: "var(--text)" },
  ".cm-scroller": { overflow: "auto" },
  ".cm-content": { fontFamily: 'ui-monospace, "SF Mono", Menlo, Consolas, monospace', caretColor: "var(--cyan)" },
  ".cm-gutters": { backgroundColor: "var(--panel-2)", color: "var(--muted)", border: "none" },
  ".cm-activeLine": { backgroundColor: "color-mix(in srgb, var(--cyan) 7%, transparent)" },
  ".cm-activeLineGutter": { backgroundColor: "color-mix(in srgb, var(--cyan) 12%, transparent)" },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground": { backgroundColor: "color-mix(in srgb, var(--cyan) 28%, transparent)" },
  ".cm-cursor": { borderLeftColor: "var(--cyan)" },
});

type Open = { path: string; hash: string | null; saved: string };
type Confirm = { title: string; text: string; label: string; color: string; resolve: (ok: boolean) => void };

export default function LuaEditor({ conn, uplinkId, title, initialPath, onClose }: { conn: MainframeConn; uplinkId: string; title: string; initialPath?: string; onClose: () => void }) {
  const api = useMemo(() => scriptApi(conn.baseUrl.replace(/\/$/, ""), conn.token, conn.info.value?.id ?? "", uplinkId), [conn, uplinkId]);
  const [tree, setTree] = useState<(FileList & { truncated?: boolean }) | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Open | null>(null);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [closedDirs, setClosedDirs] = useState<Set<string>>(new Set());
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [pinAsk, setPinAsk] = useState<{ state: PinState; resolve: (p: string | null) => void } | null>(null);
  const [showTree, setShowTree] = useState(true);
  const pin = useRef<string | undefined>(undefined);
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const openRef = useRef<Open | null>(null);
  openRef.current = open;
  const canWrite = conn.can("scripts.upload");

  const refresh = () =>
    api
      .tree()
      .then((l) => (setTree(l), setError(null)))
      .catch((e) => setError(scriptErrorText(e)));
  useEffect(() => {
    refresh();
  }, [api]);

  // the editor itself (one instance; documents are swapped)
  useEffect(() => {
    // inside a shadow root CodeMirror's styles go into a constructed style sheet (CSSOM) –
    // a <style> element would be blocked by the dashboard's strict CSP
    const shadow = host.current!.shadowRoot ?? host.current!.attachShadow({ mode: "open" });
    view.current = new EditorView({
      parent: shadow,
      root: shadow,
      state: EditorState.create({ doc: "", extensions: extensions() }),
    });
    return () => view.current?.destroy();
  }, []);
  function extensions() {
    return [
      lineNumbers(),
      highlightActiveLineGutter(),
      highlightActiveLine(),
      drawSelection(),
      history(),
      indentOnInput(),
      bracketMatching(),
      highlightSelectionMatches(),
      StreamLanguage.define(lua),
      syntaxHighlighting(highlight),
      EditorState.tabSize.of(2),
      EditorView.lineWrapping,
      EditorView.contentAttributes.of({ "aria-label": "Lua code" }),
      EditorState.readOnly.of(!canWrite),
      keymap.of([{ key: "Mod-s", preventDefault: true, run: () => (save(false), true) }, indentWithTab, ...defaultKeymap, ...historyKeymap, ...searchKeymap]),
      EditorView.updateListener.of((u) => {
        if (u.docChanged && openRef.current) setDirty(u.state.doc.toString() !== openRef.current.saved);
      }),
      theme,
    ];
  }
  const text = () => view.current?.state.doc.toString() ?? "";
  const setText = (s: string) => view.current?.setState(EditorState.create({ doc: s, extensions: extensions() }));

  const ask = (c: Omit<Confirm, "resolve">) => new Promise<boolean>((resolve) => setConfirm({ ...c, resolve }));
  async function withPin<T>(fn: (p?: string) => Promise<T>): Promise<T> {
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
        if (e.code === "pin_locked") {
          setPinAsk(null);
          throw e;
        }
        const state: PinState = e.code === "wrong_pin" ? { kind: "error", text: `Wrong PIN${e.attemptsLeft !== undefined ? ` – ${e.attemptsLeft} attempts left` : ""}.` } : { kind: "idle" };
        const p = await new Promise<string | null>((resolve) => setPinAsk({ state, resolve }));
        if (p === null) {
          setPinAsk(null);
          throw new ScriptError(403, "cancelled");
        }
        pin.current = p;
      }
    }
  }
  const fail = (e: unknown) => {
    if (e instanceof ScriptError && e.code === "cancelled") return;
    pushToast({ level: "warn", title: "Editor", text: scriptErrorText(e) });
  };

  async function openFile(path: string) {
    if (dirty && !(await ask({ title: "Discard changes?", text: `${open?.path} has unsaved changes.`, label: "Discard", color: "red" }))) return;
    setBusy(true);
    try {
      const src = await api.read(path);
      setOpen({ path, hash: await contentHash(src), saved: src });
      setText(src);
      setDirty(false);
      if (matchMedia("(max-width: 760px)").matches) setShowTree(false);
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (initialPath) openFile(initialPath);
  }, []);

  async function save(run: boolean) {
    const cur = openRef.current;
    if (!cur || !canWrite) return;
    const code = text();
    if (new TextEncoder().encode(code).length > MAX_SCRIPT) return pushToast({ level: "warn", title: "Editor", text: `Too large: max ${fmtBytes(MAX_SCRIPT)}.` });
    setBusy(true);
    try {
      let r: { hash: string } | null = null;
      try {
        r = await withPin((p) => api.save(cur.path, code, cur.hash, p));
      } catch (e) {
        if (!(e instanceof ScriptError && e.code === "changed")) throw e;
        const overwrite = await ask({ title: "Changed on the computer", text: `${cur.path} was changed on the computer (or by someone else) since you opened it. Overwrite it with your version? Cancel keeps your text here so you can copy it.`, label: "Overwrite", color: "red" });
        if (!overwrite) return;
        r = await withPin((p) => api.write(cur.path, code, { overwrite: true, pin: p }).then(async (x) => ({ ...x, hash: await contentHash(code) })));
      }
      setOpen({ ...cur, hash: r!.hash ?? (await contentHash(code)), saved: code });
      setDirty(false);
      if (run) {
        const started = await withPin((p) => api.run(cur.path, "auto", p));
        pushToast({ level: "info", title: "Editor", text: started === "reboot" ? `Saved – ${cur.path} runs after the reboot.` : `Saved and started ${cur.path} in a new tab.` });
      } else pushToast({ level: "info", title: "Editor", text: `Saved ${cur.path}` }, 2500);
      refresh();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  async function create(dir: boolean, under = "") {
    const name = prompt(dir ? "New folder name" : "New file name", dir ? "lib" : "program.lua")?.trim();
    if (!name) return;
    const path = under ? `${under}/${name}` : name;
    const bad = pathError(path, 8);
    if (bad) return pushToast({ level: "warn", title: "Editor", text: bad });
    try {
      if (dir) await withPin((p) => api.mkdir(path, p));
      else {
        await withPin((p) => api.write(path, "", { pin: p }));
        await refresh();
        await openFile(path);
      }
      refresh();
    } catch (e) {
      fail(e);
    }
  }
  async function rename(entry: FileEntry) {
    const to = prompt(`Move or rename ${entry.path} to`, entry.path)?.trim();
    if (!to || to === entry.path) return;
    const bad = pathError(to, 8);
    if (bad) return pushToast({ level: "warn", title: "Editor", text: bad });
    try {
      await withPin((p) => api.move(entry.path, to, p));
      if (open && (open.path === entry.path || open.path.startsWith(`${entry.path}/`))) setOpen({ ...open, path: to + open.path.slice(entry.path.length) });
      refresh();
    } catch (e) {
      fail(e);
    }
  }
  async function remove(entry: FileEntry) {
    if (!(await ask({ title: `Delete ${entry.path}?`, text: entry.dir ? "The folder and everything in it is deleted from the computer." : "The file is deleted from the computer.", label: "Delete", color: "red" }))) return;
    try {
      await withPin((p) => api.remove(entry.path, p));
      if (open && (open.path === entry.path || open.path.startsWith(`${entry.path}/`))) (setOpen(null), setText(""), setDirty(false));
      refresh();
    } catch (e) {
      fail(e);
    }
  }
  const close = async () => {
    if (dirty && !(await ask({ title: "Discard changes?", text: `${open?.path} has unsaved changes.`, label: "Discard", color: "red" }))) return;
    onClose();
  };

  const visible = (tree?.files ?? []).filter((f) => !f.path.split("/").slice(0, -1).some((_, i, a) => closedDirs.has(a.slice(0, i + 1).join("/"))));
  return (
    <Sheet title={`${title} – editor`} onClose={close} wide>
      <div class={`lua-editor ${showTree ? "" : "tree-hidden"}`}>
        <div class="ed-toolbar">
          <button type="button" class="btn btn-small ed-tree-toggle" aria-pressed={showTree} onClick={() => setShowTree(!showTree)}>
            Files
          </button>
          <span class="ed-path mono">
            {open ? open.path : "No file open"}
            {dirty && <span class="ed-dirty" title="Unsaved changes"> ●</span>}
          </span>
          <span class="perm-spacer" />
          {canWrite && (
            <>
              <button type="button" class="btn btn-small" disabled={!open || busy || !dirty} onClick={() => save(false)} title="Ctrl/⌘ + S">
                Save
              </button>
              {conn.can("scripts.run") && (
                <button type="button" class="pxbtn pxbtn-gold ed-run" disabled={!open || busy || !/\.lua$/.test(open.path)} onClick={() => save(true)}>
                  <span>Save &amp; run</span>
                </button>
              )}
            </>
          )}
        </div>
        <aside class="ed-tree" aria-label="Files">
          {canWrite && (
            <div class="ed-tree-actions">
              <button type="button" class="btn btn-s" onClick={() => create(false)}>
                + File
              </button>
              <button type="button" class="btn btn-s" onClick={() => create(true)}>
                + Folder
              </button>
            </div>
          )}
          {error && <p class="form-error small">{error}</p>}
          {tree && (
            <div class="files-usage" role="img" aria-label={`${fmtBytes(tree.used)} of ${fmtBytes(tree.capacity)} used`}>
              <span
                ref={(el) => {
                  if (el) el.style.width = `${Math.min(100, Math.round((tree.used / Math.max(1, tree.capacity)) * 100))}%`;
                }}
              />
            </div>
          )}
          <ul class="ed-files" role="tree">
            {visible.map((f) => {
              const depth = f.path.split("/").length - 1;
              const name = f.path.split("/").at(-1);
              const closedDir = f.dir && closedDirs.has(f.path);
              return (
                <li key={f.path} role="treeitem" aria-expanded={f.dir ? !closedDir : undefined} class={`ed-file ${f.dir ? "dir" : ""} ${open?.path === f.path ? "on" : ""}`} data-depth={depth}>
                  <button
                    type="button"
                    class="ed-name"
                    ref={(el) => {
                      if (el) el.style.paddingLeft = `${8 + depth * 14}px`;
                    }}
                    onClick={() => (f.dir ? setClosedDirs((s) => (s.has(f.path) ? new Set([...s].filter((x) => x !== f.path)) : new Set([...s, f.path]))) : openFile(f.path))}
                  >
                    <span aria-hidden="true">{f.dir ? (closedDir ? "▸ " : "▾ ") : ""}</span>
                    {name}
                    {f.dir ? "/" : ""}
                  </button>
                  {canWrite && (
                    <span class="ed-file-tools">
                      {f.dir && (
                        <button type="button" class="icon-btn xs" title="New file here" aria-label={`New file in ${f.path}`} onClick={() => create(false, f.path)}>
                          +
                        </button>
                      )}
                      <button type="button" class="icon-btn xs" title="Rename or move" aria-label={`Rename ${f.path}`} onClick={() => rename(f)}>
                        ✎
                      </button>
                      {conn.can("scripts.delete") && (
                        <button type="button" class="icon-btn xs" title="Delete" aria-label={`Delete ${f.path}`} onClick={() => remove(f)}>
                          ×
                        </button>
                      )}
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
          {tree?.truncated && <p class="muted small">Only the first 500 entries are shown.</p>}
          {tree && tree.files.length === 0 && <p class="muted small">No files yet.</p>}
        </aside>
        <div class="ed-main">
          <div ref={host} class="ed-cm" />
          {!open && <div class="ed-empty muted">Pick a file on the left{canWrite ? " or create one" : ""}.</div>}
        </div>
      </div>
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          text={confirm.text}
          confirmLabel={confirm.label}
          color={confirm.color}
          onCancel={() => (confirm.resolve(false), setConfirm(null))}
          onConfirm={() => (confirm.resolve(true), setConfirm(null))}
        />
      )}
      {pinAsk && (
        <PinDialog
          title="Mainframe PIN"
          text="Changing files on a computer needs the mainframe PIN."
          color="gold"
          state={pinAsk.state}
          onCancel={() => (pinAsk.resolve(null), setPinAsk(null))}
          onSubmit={(p) => (setPinAsk({ ...pinAsk, state: { kind: "checking" } }), pinAsk.resolve(p))}
        />
      )}
    </Sheet>
  );
}
