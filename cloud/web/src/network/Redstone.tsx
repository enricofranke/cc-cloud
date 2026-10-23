// v0.7 wireless redstone (docs/SPEC.md "v0.7 web protocol: wireless redstone channels"):
// every channel with its level, transmitters and receivers; operators with act.redstone
// can override a channel for a while or until reset.
import { useEffect, useState } from "preact/hooks";
import { formatDuration, type MainframeConn } from "../lib/conn.ts";
import type { RedstoneChannel } from "../lib/types.ts";
import { pushToast } from "../lib/ui.ts";
import { DocsLink } from "../components/DocsLink.tsx";

const DURATIONS: { label: string; seconds: number | null }[] = [
  { label: "30 s", seconds: 30 },
  { label: "5 min", seconds: 300 },
  { label: "1 h", seconds: 3600 },
  { label: "Until reset", seconds: null },
];
const CHANNEL = /^[A-Za-z0-9_.-]{1,32}$/;
const ERR: Record<string, string> = {
  not_allowed: "You may not switch redstone on this mainframe.",
  read_only: "This device is read-only.",
  invalid_channel: "Channel names use letters, digits, _ . - (max 32).",
  too_many_channels: "This network already has the maximum number of channels.",
  rate_limited: "Too many changes – wait a moment.",
  offline: "The mainframe is offline.",
};

function useNow(active: boolean) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [active]);
  return now;
}

/** 0–15 as 15 small segments (redstone dust style). */
export function Level({ level, label }: { level: number; label?: string }) {
  return (
    <span class="wr-level" role="img" aria-label={label ?? `Signal ${level} of 15`}>
      {Array.from({ length: 15 }, (_, i) => (
        <span key={i} class={i < level ? "on" : ""} />
      ))}
      <b class="mono">{level}</b>
    </span>
  );
}

export function RedstonePanel({ conn }: { conn: MainframeConn }) {
  const info = conn.redstone.value;
  const may = conn.can("act.redstone");
  const now = useNow(!!info?.channels.some((c) => c.override?.until));
  const [open, setOpen] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  if (!info) return <p class="muted">This server does not send wireless redstone channels (CC: Cloud v0.7 or newer).</p>;
  return (
    <div class="wr-panel">
      <p class="muted small">
        Uplinks set to <b>Transmitter</b> or <b>Receiver</b> (in the uplink's screen) share a channel across the whole network – relays, cables and ender links included. Receivers output the strongest transmitter.{" "}
        <DocsLink to="/docs/upgrades#redstone">More</DocsLink>
      </p>
      {info.channels.length === 0 ? (
        <div class="wr-empty">
          <b>No channels yet.</b>
          <span class="muted small">Put a Cloud Uplink next to a lever or a computer's redstone output, open its screen and set it to Transmitter on a channel name.</span>
        </div>
      ) : (
        <ul class="wr-list">
          {info.channels.map((c) => (
            <ChannelRow key={c.name} c={c} conn={conn} may={may} now={now} open={open === c.name} onToggle={() => setOpen(open === c.name ? null : c.name)} />
          ))}
        </ul>
      )}
      <p class="muted small">
        {info.channels.length} / {info.limit} channels
        {!may && " · overriding needs the “Switch redstone” permission"}
      </p>
      {may && (
        <form
          class="wr-new"
          onSubmit={(e) => {
            e.preventDefault();
            if (CHANNEL.test(newName)) {
              setOpen(newName);
              setNewName("");
            }
          }}
        >
          <input class="input" placeholder="Override another channel…" value={newName} maxLength={32} aria-label="Channel name" onInput={(e) => setNewName((e.target as HTMLInputElement).value.replace(/[^A-Za-z0-9_.-]/g, ""))} />
          <button type="submit" class="btn" disabled={!CHANNEL.test(newName)}>
            Open
          </button>
        </form>
      )}
      {may && open && !info.channels.some((c) => c.name === open) && (
        <ul class="wr-list">
          <ChannelRow c={{ name: open, level: 0, signal: 0, transmitters: 0, receivers: 0 }} conn={conn} may now={now} open onToggle={() => setOpen(null)} />
        </ul>
      )}
    </div>
  );
}

function ChannelRow(props: { c: RedstoneChannel; conn: MainframeConn; may: boolean; now: number; open: boolean; onToggle: () => void }) {
  const { c, conn } = props;
  const [level, setLevel] = useState(c.override?.level ?? (c.signal > 0 ? 0 : 15));
  const [dur, setDur] = useState<number | null>(300);
  const [busy, setBusy] = useState(false);
  const left = c.override?.until ? Math.max(0, Math.round((c.override.until - props.now) / 1000)) : null;
  const send = async (lv: number | null, seconds: number | null) => {
    setBusy(true);
    try {
      await conn.rsOverride(c.name, lv, seconds);
      pushToast({ level: "info", title: "Redstone", text: lv === null ? `${c.name}: override cleared` : `${c.name} set to ${lv}${seconds ? ` for ${formatDuration(seconds)}` : " until reset"}` });
      if (lv === null) props.onToggle();
    } catch (e) {
      const code = (e as Error).message;
      pushToast({ level: "warn", title: "Redstone", text: ERR[code] ?? code });
    } finally {
      setBusy(false);
    }
  };
  return (
    <li class={`wr-row ${c.override ? "has-override" : ""} ${props.open ? "open" : ""}`}>
      <div class="wr-main">
        <span class="wr-name mono">{c.name}</span>
        <Level level={c.level} label={`${c.name}: receivers output ${c.level}`} />
        <span class="wr-meta muted small">
          {c.transmitters} transmitter{c.transmitters === 1 ? "" : "s"} → {c.receivers} receiver{c.receivers === 1 ? "" : "s"}
          {c.override && c.signal !== c.level && ` · transmitters say ${c.signal}`}
        </span>
        {c.override && (
          <span class="tag tag-override">
            override {c.override.level} · {left !== null ? `${formatDuration(left)} left` : "until reset"}
          </span>
        )}
        {props.may && (
          <button type="button" class="btn btn-small" aria-expanded={props.open} onClick={props.onToggle}>
            Override…
          </button>
        )}
      </div>
      {props.may && props.open && (
        <div class="wr-edit">
          <label class="wr-slider">
            <span class="small">Level {level}</span>
            <input type="range" min={0} max={15} step={1} value={level} aria-label={`Override level for ${c.name}`} onInput={(e) => setLevel(Number((e.target as HTMLInputElement).value))} />
          </label>
          <div class="seg-ctl seg-s" role="radiogroup" aria-label="How long">
            {DURATIONS.map((d) => (
              <button key={d.label} type="button" role="radio" aria-checked={dur === d.seconds} class={dur === d.seconds ? "on" : ""} onClick={() => setDur(d.seconds)}>
                {d.label}
              </button>
            ))}
          </div>
          <div class="wr-actions">
            {c.override && (
              <button type="button" class="btn btn-small" disabled={busy} onClick={() => send(null, null)}>
                Clear override
              </button>
            )}
            <button type="button" class="pxbtn pxbtn-gold" disabled={busy} onClick={() => send(level, dur)}>
              <span>{busy ? "Sending…" : "Apply"}</span>
            </button>
          </div>
        </div>
      )}
    </li>
  );
}
