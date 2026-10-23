// v0.5 D: push notification targets (Discord, ntfy, webhook) per mainframe (lazy route #/m/<id>/push).
import { useEffect, useState } from "preact/hooks";
import { getConn } from "../lib/conns.ts";
import { saved } from "../lib/store.ts";
import { navigate, pushToast, timeAgo } from "../lib/ui.ts";
import { PUSH_ERRORS, PUSH_EVENTS, RESULT_TEXT, getPush, postPush, urlProblem, type PushDoc, type PushEvent, type PushInput, type PushLevel, type PushTarget, type PushType } from "../lib/push.ts";
import { IconBack, IconPlus } from "../components/Icons.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const TYPE_LABEL: Record<PushType, string> = { discord: "Discord", ntfy: "ntfy", webhook: "Webhook" };
const TYPE_HELP: Record<PushType, string> = {
  discord: "In Discord: channel settings → Integrations → Webhooks → New Webhook → Copy Webhook URL.",
  ntfy: "A topic URL such as https://ntfy.sh/my-base-alerts – subscribe to the same topic in the ntfy app. Use a long, hard to guess topic name or a token.",
  webhook: "Any https:// endpoint; it receives a JSON POST with event, mainframe, title, text, level and time.",
};
const PLACEHOLDER: Record<PushType, string> = { discord: "https://discord.com/api/webhooks/…", ntfy: "https://ntfy.sh/my-base-alerts", webhook: "https://example.com/hooks/cccloud" };
const EVENT_HELP: Record<PushEvent, string> = {
  alerts: "Alerts from scripts, cards, crafting jobs and rules",
  rules: "Runs of rules marked “push every run”",
  approvals: "A browser waits for approval at the mainframe",
  power: "No power / power restored",
};
const LEVEL_LABEL: Record<PushLevel, string> = { info: "everything", warn: "warnings and errors", error: "errors only" };
const err = (e: unknown) => PUSH_ERRORS[(e as Error).message] ?? (e as Error).message;

export default function PushScreen({ id }: { id: string }) {
  const conn = getConn(id);
  const token = saved.value.find((m) => m.mainframeId === id)?.token ?? "";
  const [doc, setDoc] = useState<PushDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [form, setForm] = useState<{ target?: PushTarget } | null>(null);
  const load = () =>
    getPush(location.origin, token, id)
      .then(setDoc)
      .catch((e) => setError(err(e)));
  useEffect(() => {
    if (token) load();
  }, [id, token]);
  // the result of a test arrives a moment later as lastResult
  useEffect(() => {
    if (!busy?.startsWith("test:")) return;
    const t = setTimeout(() => (load(), setBusy(null)), 1500);
    return () => clearTimeout(t);
  }, [busy]);
  const op = async (body: Record<string, unknown>, key: string, ok?: string) => {
    setBusy(key);
    setError(null);
    try {
      setDoc(await postPush(location.origin, token, id, body));
      if (ok) pushToast({ level: "info", title: "Notifications", text: ok });
    } catch (e) {
      setError(err(e));
    } finally {
      if (!key.startsWith("test:")) setBusy(null);
    }
  };
  const name = conn?.info.value?.name ?? "Mainframe";
  return (
    <main class="page page-narrow-wide push-page" id="main" tabIndex={-1}>
      <div class="page-head acc-head">
        <div>
          <button type="button" class="link-back" onClick={() => navigate(`/m/${encodeURIComponent(id)}`)}>
            <IconBack size={18} /> Back
          </button>
          <h1 class="px-heading">Notifications</h1>
        </div>
        {doc && (
          <button type="button" class="pxbtn pxbtn-gold" disabled={doc.targets.length >= doc.limit} onClick={() => setForm({})}>
            <span>
              <IconPlus size={14} /> Add target
            </span>
          </button>
        )}
      </div>
      <p class="muted">
        Get alerts from <b>{name}</b> on your phone or in Discord even when no dashboard is open. The server sends them; links and tokens stay on the server.{" "}
        <DocsLink to="/docs/automation#push">Setup guide</DocsLink>
      </p>
      {error && (
        <p class="form-error" role="alert">
          {error}
        </p>
      )}
      {doc && !doc.enabled && (
        <div class="banner banner-warn" role="status">
          <div class="banner-text">Push notifications are switched off on this server (config pushEnabled). Targets are kept but nothing is sent.</div>
        </div>
      )}
      {!doc && !error && <div class="card skeleton" style={{ height: "140px" }} />}
      {doc && (
        <>
          <p class="muted small">
            {doc.targets.length} / {doc.limit} targets · at most {doc.perMinute} messages per minute
            {doc.allowedHosts.length > 0 && ` · allowed hosts: ${doc.allowedHosts.join(", ")}`}
          </p>
          {doc.targets.length === 0 ? (
            <div class="rules-empty">
              <b>No targets yet.</b>
              <span class="muted small">Add a Discord webhook or an ntfy topic – then use “Send test”.</span>
            </div>
          ) : (
            <ul class="push-list">
              {doc.targets.map((t) => (
                <li key={t.id} class={`push-row ${t.enabled ? "" : "off"}`}>
                  <div class="rule-top">
                    <button
                      type="button"
                      role="switch"
                      aria-checked={t.enabled}
                      aria-label={`${t.name} enabled`}
                      disabled={!!busy}
                      class={`switch ${t.enabled ? "on" : ""}`}
                      onClick={() => op({ op: "update", id: t.id, target: { enabled: !t.enabled } }, t.id)}
                    >
                      <span class="switch-text">{t.enabled ? "ON" : "OFF"}</span>
                      <span class="switch-knob" />
                    </button>
                    <span class={`push-type pt-${t.type}`}>{TYPE_LABEL[t.type]}</span>
                    <b class="rule-name">{t.name}</b>
                    <span class="rule-tools">
                      <button type="button" class="btn btn-small" disabled={!!busy || !doc.enabled} onClick={() => op({ op: "test", id: t.id }, `test:${t.id}`, `Test sent to ${t.name}`)}>
                        {busy === `test:${t.id}` ? "Sending…" : "Send test"}
                      </button>
                      <button type="button" class="btn btn-small" onClick={() => setForm({ target: t })}>
                        Edit
                      </button>
                      <button type="button" class="btn btn-small btn-danger-ghost" disabled={!!busy} onClick={() => confirm(`Delete “${t.name}”?`) && op({ op: "delete", id: t.id }, t.id)}>
                        Delete
                      </button>
                    </span>
                  </div>
                  <p class="mono small push-url">
                    {t.url}
                    {t.hasToken && <span class="tag tag-gray">token</span>}
                  </p>
                  <p class="muted small rule-meta">
                    {LEVEL_LABEL[t.minLevel]} · {t.events.length ? t.events.join(", ") : "no events"}
                    {t.lastSent ? ` · last sent ${timeAgo(t.lastSent)}` : " · nothing sent yet"}
                    {t.lastResult && <span class={`rule-res ${t.lastResult === "ok" ? "ok" : "bad"}`}>{RESULT_TEXT[t.lastResult] ?? t.lastResult}</span>}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {form && (
        <TargetForm
          target={form.target}
          onClose={() => setForm(null)}
          onSave={async (input) => {
            const r = await postPush(location.origin, token, id, form.target ? { op: "update", id: form.target.id, target: input } : { op: "add", target: input });
            setDoc(r);
            pushToast({ level: "info", title: "Notifications", text: form.target ? "Target saved" : "Target added – use “Send test” to try it." });
            setForm(null);
          }}
        />
      )}
    </main>
  );
}

function TargetForm({ target, onClose, onSave }: { target?: PushTarget; onClose: () => void; onSave: (t: PushInput) => Promise<void> }) {
  const [type, setType] = useState<PushType>(target?.type ?? "discord");
  const [name, setName] = useState(target?.name ?? "");
  const [url, setUrl] = useState("");
  const [token, setToken] = useState("");
  const [dropToken, setDropToken] = useState(false);
  const [minLevel, setMinLevel] = useState<PushLevel>(target?.minLevel ?? "warn");
  const [events, setEvents] = useState<PushEvent[]>(target?.events ?? ["alerts", "rules", "power"]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const urlNeeded = !target || type !== target.type;
  const problem = url ? urlProblem(type, url) : urlNeeded ? "Paste the URL." : null;
  const save = async () => {
    if (problem) return setError(problem);
    setBusy(true);
    setError(null);
    try {
      await onSave({
        ...(target ? {} : { type }),
        ...(target && type !== target.type ? { type } : {}),
        name: name.trim() || TYPE_LABEL[type],
        ...(url ? { url } : {}),
        ...(type === "ntfy" && token ? { token } : dropToken ? { token: "" } : {}),
        minLevel,
        events,
      });
    } catch (e) {
      setError(err(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet title={target ? `Edit ${target.name}` : "Add a notification target"} onClose={onClose}>
      <form
        class="push-form"
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <div class="seg-ctl" role="radiogroup" aria-label="Type">
          {(["discord", "ntfy", "webhook"] as const).map((t) => (
            <button key={t} type="button" role="radio" aria-checked={type === t} class={type === t ? "on" : ""} onClick={() => setType(t)}>
              {TYPE_LABEL[t]}
            </button>
          ))}
        </div>
        <p class="muted small">{TYPE_HELP[type]}</p>
        <label class="field">
          <span class="field-label">Name</span>
          <input class="input" maxLength={48} value={name} placeholder={TYPE_LABEL[type]} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
        </label>
        <label class="field">
          <span class="field-label">URL</span>
          <input class="input mono" type="url" autoComplete="off" spellcheck={false} value={url} placeholder={target && !urlNeeded ? `${target.url} (unchanged)` : PLACEHOLDER[type]} onInput={(e) => setUrl((e.target as HTMLInputElement).value.trim())} />
          <span class="field-hint">Stored on the server only; the dashboard never shows it in full again.</span>
        </label>
        {type === "ntfy" && (
          <label class="field">
            <span class="field-label">Access token (optional)</span>
            <input class="input mono" type="password" autoComplete="off" value={token} placeholder={target?.hasToken ? "•••••• (unchanged)" : "tk_…"} onInput={(e) => setToken((e.target as HTMLInputElement).value.trim())} />
            {target?.hasToken && (
              <label class="check-row small">
                <input type="checkbox" checked={dropToken} onChange={(e) => setDropToken((e.target as HTMLInputElement).checked)} />
                Remove the stored token
              </label>
            )}
          </label>
        )}
        <label class="field">
          <span class="field-label">Send</span>
          <select class="select" value={minLevel} onChange={(e) => setMinLevel((e.target as HTMLSelectElement).value as PushLevel)}>
            {(["info", "warn", "error"] as const).map((l) => (
              <option key={l} value={l}>
                {LEVEL_LABEL[l]}
              </option>
            ))}
          </select>
        </label>
        <fieldset class="perm-group">
          <legend>Events</legend>
          {PUSH_EVENTS.map((ev) => (
            <label key={ev} class="perm-row">
              <input type="checkbox" checked={events.includes(ev)} onChange={(e) => setEvents((e.target as HTMLInputElement).checked ? [...events, ev] : events.filter((x) => x !== ev))} />
              <span class="perm-text">
                <span class="perm-name">{ev}</span>
                <span class="muted small">{EVENT_HELP[ev]}</span>
              </span>
            </label>
          ))}
        </fieldset>
        {error && (
          <p class="form-error" role="alert">
            {error}
          </p>
        )}
        <div class="perm-actions">
          <span class="perm-spacer" />
          <button type="button" class="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" class="pxbtn pxbtn-gold" disabled={busy}>
            <span>{busy ? "Saving…" : "Save"}</span>
          </button>
        </div>
      </form>
    </Sheet>
  );
}
