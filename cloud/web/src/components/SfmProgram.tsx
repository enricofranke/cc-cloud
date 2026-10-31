// Lazy chunk: a Factory Manager's program. Reading needs view.positions, writing device.program too (plus the PIN).
import { useEffect, useRef, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { MAX_PROGRAM, PROGRAM_ERRORS, PROGRAM_FILES, lineCount, programDiff, programFileError, type ProgramDetail } from "../lib/sfm.ts";
import { CodeBlock } from "./CodeBlock.tsx";
import { Sheet } from "./Overlays.tsx";
import { pushToast } from "../lib/ui.ts";

const errorText = (e: unknown) => {
  const code = e instanceof Error ? e.message : String(e);
  return PROGRAM_ERRORS[code] ?? code;
};

function Lines({ title, lines, tone }: { title: string; lines?: string[]; tone: "err" | "warn" }) {
  if (!lines?.length) return null;
  return (
    <section class={`sfm-lines sfm-${tone}`} aria-label={title}>
      <h4 class="sec-title">
        {title} ({lines.length})
      </h4>
      <ul>
        {lines.map((l, i) => (
          <li key={i}>{l}</li>
        ))}
      </ul>
    </section>
  );
}

export default function SfmProgramSheet({ conn, uplinkId, title, onClose }: { conn: MainframeConn; uplinkId: string; title: string; onClose: () => void }) {
  const [doc, setDoc] = useState<ProgramDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** The text being edited; null = reading. */
  const [draft, setDraft] = useState<string | null>(null);
  /** Compile result of the draft: the confirm step. */
  const [review, setReview] = useState<{ errors: string[] } | null>(null);
  const [busy, setBusy] = useState(false);
  const [pinAsk, setPinAsk] = useState<{ text?: string } | null>(null);
  const [pin, setPin] = useState("");
  const pendingSave = useRef<(pin: string) => void>();
  const canWrite = conn.can("device.program");
  const load = () =>
    conn
      .cardDetail<ProgramDetail>(uplinkId)
      .then((d) => (setDoc(d), setError(null)))
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    load();
  }, [uplinkId]);

  const fail = (e: unknown, retry: (pin: string) => void) => {
    const x = e as Error & { attemptsLeft?: number; retryAfter?: number };
    if (x.message === "pin_required") {
      pendingSave.current = retry;
      setPinAsk({});
    } else if (x.message === "wrong_pin") {
      pendingSave.current = retry;
      setPin("");
      setPinAsk({ text: `Wrong PIN${x.attemptsLeft !== undefined ? ` – ${x.attemptsLeft} attempts left` : ""}.` });
    } else if (x.message === "pin_locked") {
      setPinAsk(null);
      setError(`Too many wrong PINs – locked${x.retryAfter ? ` for ${Math.ceil(x.retryAfter / 60)} min` : ""}.`);
    } else setError(errorText(e));
  };
  const check = async () => {
    if (draft === null) return;
    setBusy(true);
    setError(null);
    try {
      const r = await conn.cardWrite<{ errors: string[] }>(uplinkId, "check", { text: draft });
      setReview({ errors: r.errors ?? [] });
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  const save = async (usePin?: string) => {
    if (draft === null || !review) return;
    setBusy(true);
    setError(null);
    try {
      const d = await conn.cardWrite<ProgramDetail>(uplinkId, "save", { text: draft, force: review.errors.length > 0, ...(usePin ? { pin: usePin } : {}) });
      setDoc(d);
      setDraft(null);
      setReview(null);
      setPinAsk(null);
      pushToast({ level: "info", title: "Program written to the disk", text: d.errors?.length ? "It has errors, so the manager does not run it." : "The manager runs the new program." });
    } catch (e) {
      fail(e, save);
    } finally {
      setBusy(false);
    }
  };
  const undo = async (usePin?: string) => {
    setBusy(true);
    setError(null);
    try {
      setDoc(await conn.cardWrite<ProgramDetail>(uplinkId, "undo", usePin ? { pin: usePin } : {}));
      setPinAsk(null);
      pushToast({ level: "info", title: "Previous program restored", text: "Undo works once." });
    } catch (e) {
      fail(e, undo);
    } finally {
      setBusy(false);
    }
  };
  const pickFile = async (e: Event) => {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = "";
    if (!file) return;
    const bad = programFileError(file.name, file.size);
    if (bad) return setError(bad);
    setError(null);
    setReview(null);
    setDraft(await file.text());
  };

  const old = doc?.program ?? "";
  const diff = draft !== null && review ? programDiff(old, draft) : null;
  const unchanged = diff !== null && diff.removed.length === 0 && diff.added.length === 0;
  return (
    <Sheet title={`${title} – program`} onClose={onClose} wide tall>
      <div class="sfm">
        {error && (
          <p class="form-error" role="alert">
            {error}
          </p>
        )}
        {!doc && !error && <p class="muted">Loading…</p>}
        {doc && !doc.disk && <p class="muted">There is no disk in this manager.</p>}
        {doc?.disk && draft === null && (
          <>
            <p class="muted small">
              {doc.state}
              {doc.name ? ` · ${doc.name}` : ""} · {lineCount(old)} lines
            </p>
            <Lines title="Errors" lines={doc.errors} tone="err" />
            <Lines title="Warnings" lines={doc.warnings} tone="warn" />
            {old ? <CodeBlock code={old} label="SFML" /> : <p class="muted">The disk has no program yet.</p>}
            {doc.truncated && <p class="muted small">Only the start of this program is shown: it is too long for the dashboard.</p>}
            {canWrite && (
              <div class="sheet-actions">
                {doc.undo && (
                  <button type="button" class="btn" disabled={busy} onClick={() => undo()}>
                    Undo last change
                  </button>
                )}
                <button type="button" class="pxbtn pxbtn-gold" disabled={doc.truncated} onClick={() => (setDraft(old), setReview(null))}>
                  <span>Edit or import</span>
                </button>
              </div>
            )}
          </>
        )}
        {doc?.disk && draft !== null && !review && (
          <>
            <label class="field">
              <span class="field-label">Program ({draft.length.toLocaleString("en-US")} / {MAX_PROGRAM.toLocaleString("en-US")} characters)</span>
              <textarea class="input sfm-edit mono" rows={18} spellcheck={false} maxLength={MAX_PROGRAM} value={draft} onInput={(e) => setDraft((e.target as HTMLTextAreaElement).value)} />
            </label>
            <div class="sheet-actions">
              <label class="btn sfm-file">
                Load a file…
                <input type="file" accept={PROGRAM_FILES} class="sr-only" onChange={pickFile} />
              </label>
              <button type="button" class="btn" onClick={() => (setDraft(null), setError(null))}>
                Cancel
              </button>
              <button type="button" class="pxbtn pxbtn-gold" disabled={busy || !draft.trim()} onClick={check}>
                <span>{busy ? "Checking…" : "Check and review"}</span>
              </button>
            </div>
          </>
        )}
        {doc?.disk && diff && review && (
          <>
            <p class="sfm-count">
              {diff.oldLines} lines now → {diff.newLines} lines after
              {unchanged ? " · nothing changed" : ` · ${diff.removed.length} removed, ${diff.added.length} added from line ${diff.same + 1}`}
            </p>
            <Lines title="The new program has errors" lines={review.errors} tone="err" />
            {!unchanged && (
              <pre class="sfm-diff" tabIndex={0} aria-label="Changes">
                {diff.removed.map((l, i) => (
                  <span key={`r${i}`} class="sfm-del">
                    {`- ${l}\n`}
                  </span>
                ))}
                {diff.added.map((l, i) => (
                  <span key={`a${i}`} class="sfm-add">
                    {`+ ${l}\n`}
                  </span>
                ))}
              </pre>
            )}
            {review.errors.length > 0 && <p class="form-error">If you write this, the manager stops running: it does not run a program with errors. The old program can be restored with Undo.</p>}
            <div class="sheet-actions">
              <button type="button" class="btn" onClick={() => setReview(null)}>
                Back to the editor
              </button>
              <button type="button" class={review.errors.length ? "btn btn-danger-ghost" : "pxbtn pxbtn-gold"} disabled={busy || unchanged} onClick={() => save()}>
                <span>{busy ? "Writing…" : review.errors.length ? "Write it anyway" : "Write to the disk"}</span>
              </button>
            </div>
          </>
        )}
        {pinAsk && (
          <form
            class="sfm-pin"
            onSubmit={(e) => {
              e.preventDefault();
              pendingSave.current?.(pin);
            }}
          >
            <label class="field">
              <span class="field-label">Mainframe PIN</span>
              <input class="input" type="password" inputMode="numeric" autoComplete="off" value={pin} onInput={(e) => setPin((e.target as HTMLInputElement).value.replace(/\D/g, "").slice(0, 8))} />
            </label>
            {pinAsk.text && <p class="form-error">{pinAsk.text}</p>}
            <button type="submit" class="pxbtn pxbtn-gold" disabled={busy || pin.length < 4}>
              <span>Confirm</span>
            </button>
          </form>
        )}
      </div>
    </Sheet>
  );
}
