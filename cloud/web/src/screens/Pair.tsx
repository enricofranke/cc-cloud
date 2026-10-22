import { useEffect, useRef, useState } from "preact/hooks";
import { ApiError, pair, pollPairing, type Paired } from "../lib/api.ts";
import { addSaved, saved } from "../lib/store.ts";
import { syncConns } from "../lib/conns.ts";
import { guessDeviceName, navigate, pushToast } from "../lib/ui.ts";
import { IconBack } from "../components/Icons.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const ALPHABET = /[^ABCDEFGHJKMNPQRSTUVWXYZ23456789]/g;
const POLL_MS = 2000;
const APPROVAL_WINDOW_MS = 120_000;

/** Uppercases, drops characters that are not in the code alphabet and inserts the dash. */
function formatCode(raw: string) {
  const clean = raw.toUpperCase().replace(/[^A-Z0-9]/g, "").replace(ALPHABET, "").slice(0, 8);
  return clean.length > 4 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
}

type Phase = { kind: "form" } | { kind: "waiting"; pairingId: string; since: number } | { kind: "denied" } | { kind: "expired" };

export function PairScreen({ initialCode }: { initialCode?: string }) {
  const [code, setCode] = useState(formatCode(initialCode ?? ""));
  const [name, setName] = useState(guessDeviceName());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>({ kind: "form" });
  const complete = code.replace("-", "").length === 8 && name.trim().length > 0;

  function finish(res: Paired) {
    addSaved({ mainframeId: res.mainframeId, token: res.token, name: res.name, deviceName: name.trim(), pairedAt: Date.now(), canControl: res.canControl });
    syncConns();
    pushToast({ level: "info", title: "Paired", text: `This device is now connected to ${res.name}.` });
    navigate(`/m/${encodeURIComponent(res.mainframeId)}`, true);
  }

  async function submit(e: Event) {
    e.preventDefault();
    if (!complete || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await pair(code, name.trim().slice(0, 40));
      if (res.status === "approved") finish(res);
      else setPhase({ kind: "waiting", pairingId: res.pairingId, since: Date.now() });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  }

  const restart = () => {
    setCode("");
    setError(null);
    setPhase({ kind: "form" });
  };

  return (
    <main class="page page-narrow">
      {saved.value.length > 0 && phase.kind === "form" && (
        <button type="button" class="link-back" onClick={() => navigate("/")}>
          <IconBack size={18} /> Mainframes
        </button>
      )}
      {phase.kind === "waiting" ? (
        <Waiting
          pairingId={phase.pairingId}
          since={phase.since}
          deviceName={name.trim()}
          onApproved={finish}
          onDone={(k) => setPhase({ kind: k })}
          onCancel={restart}
        />
      ) : phase.kind === "denied" || phase.kind === "expired" ? (
        <div class="pair-result">
          <div class={`pair-result-icon ${phase.kind}`} aria-hidden="true">
            {phase.kind === "denied" ? "✕" : "⌛"}
          </div>
          <h1 class="px-heading">{phase.kind === "denied" ? "Request denied" : "Request expired"}</h1>
          <p class="muted">
            {phase.kind === "denied"
              ? "The player clicked [Deny] in-game, so this device was not connected. Ask them to try again if that was a mistake."
              : "Nobody approved the request within 2 minutes. Right-click the mainframe for a new code and try again."}
          </p>
          <button type="button" class="pxbtn pxbtn-gold pxbtn-wide" onClick={restart}>
            <span>Try again</span>
          </button>
        </div>
      ) : (
        <>
          <div class="pair-hero">
            <div class="pair-terminal" aria-hidden="true">
              <div class="pair-terminal-bar">
                <span /> <span /> <span />
              </div>
              <pre class="pair-terminal-body">
                {"> mainframe.status()\n"}
                <span class="t-green">{"ONLINE"}</span>
                {"\n> pairing code\n"}
                <span class="t-gold">{code.length ? code.padEnd(9, "_") : "XXXX-XXXX"}</span>
                <span class="cursor">█</span>
              </pre>
            </div>
            <h1 class="px-heading">Pair a mainframe</h1>
            <ol class="steps">
              <li>
                <span>
                  In Minecraft, <b>right-click the Cloud Mainframe</b> with an empty hand.
                </span>
              </li>
              <li>
                <span>
                  The chat shows this dashboard's address and an <b>8-character pairing code</b> (valid 10 minutes, single use).
                </span>
              </li>
              <li>
                <span>
                  Enter it below, then <b>click [Allow]</b> in the chat to approve this device.
                </span>
              </li>
            </ol>
          </div>

          <form class="form" onSubmit={submit} novalidate>
            <label class="field">
              <span class="field-label">Pairing code</span>
              <input
                class="input input-code"
                value={code}
                onInput={(e) => setCode(formatCode((e.target as HTMLInputElement).value))}
                placeholder="XXXX-XXXX"
                inputMode="text"
                autoComplete="one-time-code"
                autoCapitalize="characters"
                autoCorrect="off"
                spellcheck={false}
                maxLength={9}
                aria-invalid={!!error}
                autoFocus
              />
            </label>
            <label class="field">
              <span class="field-label">Device name</span>
              <input
                class="input input-name"
                value={name}
                onInput={(e) => setName((e.target as HTMLInputElement).value)}
                maxLength={40}
                placeholder="e.g. Enricos iPhone"
              />
              <span class="field-hint">Shown in the in-game approval prompt and in /cccloud devices.</span>
            </label>
            {error && (
              <p class="form-error" role="alert">
                {error}
              </p>
            )}
            <button type="submit" class="pxbtn pxbtn-gold pxbtn-wide" disabled={!complete || busy}>
              <span>{busy ? "Checking code…" : "Pair device"}</span>
            </button>
          </form>
          <p class="muted small pair-help">
            First time? <DocsLink to="/docs/start">Getting started</DocsLink> explains the mainframe, power and the Link Card ·{" "}
            <DocsLink to="/docs/faq#not-reachable">Can't connect?</DocsLink>
          </p>
        </>
      )}
    </main>
  );
}

function Waiting(props: {
  pairingId: string;
  since: number;
  deviceName: string;
  onApproved: (p: Paired) => void;
  onDone: (k: "denied" | "expired") => void;
  onCancel: () => void;
}) {
  const [now, setNow] = useState(Date.now());
  const [netError, setNetError] = useState<string | null>(null);
  const cb = useRef(props);
  cb.current = props;

  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      if (!alive) return;
      if (Date.now() - props.since > APPROVAL_WINDOW_MS) return cb.current.onDone("expired");
      try {
        const r = await pollPairing(props.pairingId);
        if (!alive) return;
        setNetError(null);
        if (r.status === "approved") return cb.current.onApproved(r);
        if (r.status === "denied" || r.status === "expired") return cb.current.onDone(r.status);
      } catch (e) {
        if (!alive) return;
        // keep polling through short network hiccups
        setNetError(e instanceof ApiError ? e.message : "Connection problem – retrying…");
      }
      timer = setTimeout(tick, POLL_MS);
    };
    timer = setTimeout(tick, POLL_MS);
    const clock = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      alive = false;
      clearTimeout(timer);
      clearInterval(clock);
    };
  }, [props.pairingId, props.since]);

  const left = Math.max(0, Math.ceil((APPROVAL_WINDOW_MS - (now - props.since)) / 1000));
  const pct = (left / (APPROVAL_WINDOW_MS / 1000)) * 100;
  return (
    <div class="waiting" role="status" aria-live="polite">
      <div class="handshake" aria-hidden="true">
        <div class="hs-node hs-phone">
          <span class="hs-screen" />
        </div>
        <div class="hs-wire">
          <span class="hs-packet" />
          <span class="hs-packet d2" />
          <span class="hs-packet d3" />
        </div>
        <div class="hs-node hs-mainframe">
          <span class="hs-led" />
          <span class="hs-led d2" />
          <span class="hs-led d3" />
        </div>
      </div>
      <h1 class="px-heading">
        Waiting for approval <span class="nowrap">in-game…</span>
      </h1>
      <p class="muted">
        The player who opened the code now sees a chat message:
      </p>
      <div class="chat-preview">
        <span class="chat-name">{props.deviceName || "This device"}</span> wants to connect – <span class="chat-allow">[Allow]</span>{" "}
        <span class="chat-deny">[Deny]</span>
      </div>
      <p class="muted small">Click [Allow] in Minecraft. This page continues automatically.</p>
      <div class="countdown" aria-label={`${left} seconds left`}>
        <div class="countdown-bar" style={{ width: `${pct}%` }} />
      </div>
      <div class="countdown-text">
        {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")} left
      </div>
      {netError && <p class="form-error">{netError}</p>}
      <button type="button" class="btn btn-ghost waiting-cancel" onClick={props.onCancel}>
        Cancel
      </button>
    </div>
  );
}
