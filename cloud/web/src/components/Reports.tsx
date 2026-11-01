// Crash reports, usage stats and feedback (docs/PRIVACY.md). The prompt comes once, to the server owner only, with an
// equal Yes and No per channel; the sheet shows the exact payloads and is where a yes is taken back; every feedback
// message is shown as it would go out and needs its own Send.
import { useEffect, useState } from "preact/hooks";
import { lang, t, type Key } from "../lib/i18n.ts";
import { pushToast } from "../lib/ui.ts";
import {
  CONTACT_MAX,
  FEEDBACK_CATEGORIES,
  FEEDBACK_MAX,
  REPORT_CHANNELS,
  decideReport,
  dismissReports,
  feedbackProblem,
  getPreview,
  getReports,
  pretty,
  previewFeedback,
  privacyUrl,
  sendFeedback,
  type FeedbackCategory,
  type ReportChannel,
  type ReportPreview,
  type ReportState,
  type ReportsDoc,
} from "../lib/reports.ts";
import { Sheet } from "./Overlays.tsx";
import { IconClose } from "./Icons.tsx";

const base = () => location.origin;
const problem = (e: unknown) => {
  const code = (e as Error).message;
  const known: Record<string, Key> = { rate_limited: "rp.err.limit", too_long: "rp.fb.tooLong", empty: "rp.fb.empty", unreachable: "rp.err.unreachable" };
  return t(known[code] ?? "rp.err.failed");
};

/** The reports document of this mainframe for this browser; null while unknown or on a server without reports. */
export function useReports(id: string, token: string, online: boolean) {
  const [doc, setDoc] = useState<ReportsDoc | null>(null);
  useEffect(() => {
    if (!token || !online) return;
    let live = true;
    getReports(base(), token, id)
      .then((d) => live && setDoc(d))
      .catch(() => {}); // an older server without the routes: nothing to show
    return () => {
      live = false;
    };
  }, [id, token, online]);
  return [doc, setDoc] as const;
}

function PrivacyNote({ text = "rp.operator" }: { text?: Key }) {
  return (
    <p class="muted small">
      {t(text)}{" "}
      <a href={privacyUrl(lang.value)} target="_blank" rel="noopener noreferrer">
        {t("rp.privacy")}
      </a>
    </p>
  );
}

/** Yes and No are the same size and the same style on purpose. */
function Choice(props: { channel: ReportChannel; state: ReportState; busy: boolean; onDecide: (yes: boolean) => void }) {
  const { channel, state, busy, onDecide } = props;
  const label = t(`rp.${channel}` as Key);
  return (
    <div class="rp-choice">
      <div class="rp-choice-text">
        <strong>{label}</strong>
        <span class="muted small">{t(`rp.${channel}.hint` as Key)}</span>
      </div>
      <div class="seg-ctl rp-seg" role="radiogroup" aria-label={label}>
        <button type="button" role="radio" aria-checked={state === "active"} class={state === "active" ? "on" : ""} disabled={busy} onClick={() => onDecide(true)}>
          {t("rp.yes")}
        </button>
        <button type="button" role="radio" aria-checked={state === "declined"} class={state === "declined" ? "on" : ""} disabled={busy} onClick={() => onDecide(false)}>
          {t("rp.no")}
        </button>
      </div>
    </div>
  );
}

/** The one-time question above the dashboard. Closing it answers nothing, sends nothing and it does not come back. */
export function ReportsPrompt(props: { id: string; token: string; doc: ReportsDoc; onChange: (doc: ReportsDoc) => void; onShow: () => void }) {
  const { id, token, doc, onChange, onShow } = props;
  const [busy, setBusy] = useState(false);
  if (!doc.prompt || !doc.canDecide || !doc.channels) return null;
  const run = (work: Promise<ReportsDoc>) => {
    setBusy(true);
    work
      .then(onChange)
      .catch((e) => pushToast({ level: "warn", title: t("rp.title"), text: problem(e) }))
      .finally(() => setBusy(false));
  };
  return (
    <section class="banner banner-reports" aria-label={t("rp.title")}>
      <div class="banner-body">
        <div class="banner-text">
          <strong>{t("rp.title")}</strong>
        </div>
        <p class="small">{t("rp.intro")}</p>
        {REPORT_CHANNELS.map((c) => (
          <Choice key={c} channel={c} state={doc.channels![c]} busy={busy} onDecide={(yes) => run(decideReport(base(), token, id, c, yes))} />
        ))}
        <PrivacyNote />
        <div class="banner-actions">
          <button type="button" class="btn btn-small" onClick={onShow}>
            {t("rp.show")}
          </button>
        </div>
      </div>
      <button type="button" class="icon-btn" aria-label={t("rp.later")} title={t("rp.later")} disabled={busy} onClick={() => run(dismissReports(base(), token, id))}>
        <IconClose />
      </button>
    </section>
  );
}

function Payload({ title, value, note }: { title: string; value: unknown | null; note?: string }) {
  return (
    <div class="rp-payload">
      <h3 class="field-label">{title}</h3>
      {note && <p class="muted small">{note}</p>}
      {value === null ? <p class="muted small">{t("rp.dropped")}</p> : <pre class="code rp-json" tabIndex={0}>{pretty(value)}</pre>}
    </div>
  );
}

/** The server owner's overview: both decisions, any time, and exactly what goes out. */
export function ReportsSheet(props: { id: string; token: string; doc: ReportsDoc; onChange: (doc: ReportsDoc) => void; onClose: () => void }) {
  const { id, token, doc, onChange, onClose } = props;
  const [preview, setPreview] = useState<ReportPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    getPreview(base(), token, id)
      .then(setPreview)
      .catch((e) => setError(problem(e)));
  }, [id, token, doc.channels?.crash, doc.channels?.stats]);
  const decide = (channel: ReportChannel, yes: boolean) => {
    setBusy(true);
    setError(null);
    decideReport(base(), token, id, channel, yes)
      .then(onChange)
      .catch((e) => setError(problem(e)))
      .finally(() => setBusy(false));
  };
  return (
    <Sheet title={t("rp.sheet")} onClose={onClose} wide>
      <p>{t("rp.intro")}</p>
      {doc.channels && REPORT_CHANNELS.map((c) => <Choice key={c} channel={c} state={doc.channels![c]} busy={busy} onDecide={(yes) => decide(c, yes)} />)}
      <p class="muted small">{t("rp.withdraw")}</p>
      <PrivacyNote />
      {error && <p class="form-error" role="alert">{error}</p>}
      <h2 class="px-heading">{t("rp.what")}</h2>
      {!preview && !error && <p class="muted small">{t("rp.loading")}</p>}
      {preview && (
        <>
          <Payload title={t("rp.crash")} value={preview.crash.payload} note={preview.crash.example ? t("rp.example") : t("rp.lastCrash")} />
          <Payload title={t("rp.stats")} value={preview.stats.payload} note={t("rp.statsNow")} />
        </>
      )}
      <p class="muted small">{t("rp.never")}</p>
    </Sheet>
  );
}

/** Writing, then the exact message, then Send: nothing goes out from the first step. */
export function FeedbackSheet(props: { id: string; token: string; onClose: () => void }) {
  const { id, token, onClose } = props;
  const [category, setCategory] = useState<FeedbackCategory>("idea");
  const [text, setText] = useState("");
  const [contact, setContact] = useState("");
  const [technical, setTechnical] = useState(false);
  const [review, setReview] = useState<unknown | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const input = { category, text, contact, technical };
  const blocked = feedbackProblem(input);
  const next = () => {
    setBusy(true);
    setError(null);
    previewFeedback(base(), token, id, input)
      .then(setReview)
      .catch((e) => setError(problem(e)))
      .finally(() => setBusy(false));
  };
  const send = () => {
    setBusy(true);
    setError(null);
    sendFeedback(base(), token, id, input)
      .then(() => {
        pushToast({ level: "info", title: t("rp.fb.title"), text: t("rp.fb.queued") });
        onClose();
      })
      .catch((e) => setError(problem(e)))
      .finally(() => setBusy(false));
  };
  if (review !== null) {
    return (
      <Sheet title={t("rp.fb.title")} onClose={onClose} onBack={() => setReview(null)}>
        <p>{t("rp.fb.review")}</p>
        <pre class="code rp-json" tabIndex={0}>{pretty(review)}</pre>
        <PrivacyNote text="rp.fb.minor" />
        {error && <p class="form-error" role="alert">{error}</p>}
        <div class="sheet-actions">
          <button type="button" class="btn btn-ghost" onClick={() => setReview(null)}>
            {t("rp.fb.edit")}
          </button>
          <button type="button" class="btn" disabled={busy} onClick={send}>
            {t("rp.fb.send")}
          </button>
        </div>
      </Sheet>
    );
  }
  return (
    <Sheet title={t("rp.fb.title")} onClose={onClose}>
      <p class="muted small">{t("rp.fb.lead")}</p>
      <div class="field">
        <span class="field-label">{t("rp.fb.category")}</span>
        <div class="seg-ctl" role="radiogroup" aria-label={t("rp.fb.category")}>
          {FEEDBACK_CATEGORIES.map((c) => (
            <button key={c} type="button" role="radio" aria-checked={category === c} class={category === c ? "on" : ""} onClick={() => setCategory(c)}>
              {t(`rp.fb.cat.${c}` as Key)}
            </button>
          ))}
        </div>
      </div>
      <label class="field">
        <span class="field-label">{t("rp.fb.text")}</span>
        <textarea class="input rp-text" rows={6} maxLength={FEEDBACK_MAX} value={text} onInput={(e) => setText((e.target as HTMLTextAreaElement).value)} />
        <span class="field-hint">{t("rp.fb.warning")}</span>
      </label>
      <label class="field">
        <span class="field-label">{t("rp.fb.contact")}</span>
        <input class="input" maxLength={CONTACT_MAX} autoComplete="off" value={contact} onInput={(e) => setContact((e.target as HTMLInputElement).value)} />
        <span class="field-hint">{t("rp.fb.contactHint")}</span>
      </label>
      <label class="check-row">
        <input type="checkbox" checked={technical} onChange={(e) => setTechnical((e.target as HTMLInputElement).checked)} />
        <span>{t("rp.fb.technical")}</span>
      </label>
      <PrivacyNote text="rp.fb.minor" />
      {blocked === "too_long" && <p class="form-error" role="alert">{t("rp.fb.tooLong")}</p>}
      {error && <p class="form-error" role="alert">{error}</p>}
      <div class="sheet-actions">
        <button type="button" class="btn btn-ghost" onClick={onClose}>
          {t("rp.fb.cancel")}
        </button>
        <button type="button" class="btn" disabled={busy || blocked !== null} onClick={next}>
          {t("rp.fb.next")}
        </button>
      </div>
    </Sheet>
  );
}
