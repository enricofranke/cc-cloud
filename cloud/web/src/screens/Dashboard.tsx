import { useEffect, useState } from "preact/hooks";
import { getConn, syncConns, accountLoading } from "../lib/conns.ts";
import { formatDuration } from "../lib/conn.ts";
import { removeSaved, saved } from "../lib/store.ts";
import { statusOf } from "../lib/status.ts";
import { navigate, notifyPermission, requestNotifications, setTheme, themePref, timeAgo } from "../lib/ui.ts";
import { Pages } from "./Pages.tsx";
import { MainframeSwitcher } from "../components/MainframeSwitcher.tsx";
import { ROLE_INFO } from "./Account.tsx";
import { DocsLink } from "../components/DocsLink.tsx";
import { fmtFE } from "../lib/format.ts";
import { PixelIcon } from "../components/PixelIcon.tsx";
import type { PowerInfo } from "../lib/types.ts";
import type { MainframeConn } from "../lib/conn.ts";
import { SetupGuide } from "./SetupGuide.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { IconBack, IconBell, IconGear, IconLock, IconPower, IconWifiOff, IconAlert, IconPencil } from "../components/Icons.tsx";

function useNow(active: boolean) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [active]);
  return now;
}

const BREAKDOWN_LABEL: Record<string, (n?: number) => string> = {
  base: () => "Mainframe base",
  uplinks: (n) => `${n ?? 0} uplink${n === 1 ? "" : "s"}`,
  advanced_uplinks: (n) => `${n ?? 0} advanced uplink${n === 1 ? "" : "s"}`,
  bridges: (n) => `${n ?? 0} bridge${n === 1 ? "" : "s"}`,
  clients: (n) => `${n ?? 0} dashboard${n === 1 ? "" : "s"} connected`,
  hops: (n) => `${n ?? 0} extra hop${n === 1 ? "" : "s"}`,
};

/** Header popover: what the mainframe's FE/t is spent on, plus relay power. */
function PowerPopover({ p, conn, onClose }: { p: PowerInfo; conn: MainframeConn; onClose: () => void }) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    addEventListener("keydown", h);
    return () => removeEventListener("keydown", h);
  }, [onClose]);
  const relays = (conn.network.value?.nodes ?? []).filter((n) => n.power && n.type !== "mainframe");
  const pct = p.capacity > 0 ? Math.round((p.stored / p.capacity) * 100) : 0;
  return (
    <>
      <div class="pop-backdrop" onClick={onClose} />
      <div class="power-pop" role="dialog" aria-label="Power breakdown">
        <div class="pp-head">
          <span class="px-title">Power</span>
          <span class={`pp-state ${p.ok ? "ok" : "bad"}`}>{p.ok ? `${pct} % buffer` : "NO POWER"}</span>
        </div>
        <div class="pp-buffer">
          <span class="pwr-bar big" aria-hidden="true">
            <span class="pwr-fill" style={{ width: `${pct}%` }} />
          </span>
          <span class="mono small">
            {fmtFE(p.stored)} / {fmtFE(p.capacity)}
          </span>
        </div>
        {p.breakdown && p.breakdown.length > 0 ? (
          <table class="pp-table">
            <tbody>
              {p.breakdown
                .filter((b) => b.fe > 0 || b.key === "base")
                .map((b) => (
                  <tr key={b.key}>
                    <td>{(BREAKDOWN_LABEL[b.key] ?? (() => b.key))(b.count)}</td>
                    <td class="mono">{fmtFE(b.fe, true)}</td>
                  </tr>
                ))}
              <tr class="pp-total">
                <td>Mainframe total</td>
                <td class="mono">{fmtFE(p.usage, true)}</td>
              </tr>
            </tbody>
          </table>
        ) : (
          <p class="muted small">Using {fmtFE(p.usage, true)}.</p>
        )}
        {relays.length > 0 && (
          <>
            <div class="pp-sub">Relays (own FE)</div>
            <table class="pp-table">
              <tbody>
                {relays.map((r) => (
                  <tr key={r.id} class={r.power!.ok ? "" : "bad"}>
                    <td>{r.name}</td>
                    <td class="mono">{r.power!.ok ? fmtFE(r.power!.usage, true) : "no FE"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
        <p class="muted small pp-foot">
          <DocsLink to="/docs/network#power">How power scales</DocsLink>
        </p>
      </div>
    </>
  );
}

/** Compact FE buffer bar + usage for the header. */
function PowerMeter({ p, onClick }: { p: PowerInfo; onClick?: () => void }) {
  const pct = p.capacity > 0 ? Math.min(100, (p.stored / p.capacity) * 100) : 0;
  const tone = !p.ok || pct < 5 ? "crit" : pct < 25 ? "warn" : "ok";
  return (
    <button
      type="button"
      onClick={onClick}
      class={`pwr pwr-${tone}`}
      title={`Power: ${Math.round(p.stored).toLocaleString("en-US")} / ${Math.round(p.capacity).toLocaleString("en-US")} FE, using ${Math.round(p.usage)} FE/t`}
      role="meter"
      aria-label={`Power ${Math.round(pct)}%, ${fmtFE(p.usage, true)}`}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(pct)}
    >
      <PixelIcon name="bolt" size={10} class="pwr-bolt" />
      <span class="pwr-bar" aria-hidden="true">
        <span class="pwr-fill" style={{ width: `${pct}%` }} />
      </span>
      <span class="pwr-usage">{p.ok ? fmtFE(p.usage, true) : "NO POWER"}</span>
      <span class="pwr-stored">{fmtFE(p.stored)}</span>
    </button>
  );
}

export function Dashboard({ id }: { id: string }) {
  const entry = saved.value.find((m) => m.mainframeId === id);
  const conn = getConn(id);
  const [sheet, setSheet] = useState<"alerts" | "settings" | null>(null);
  const [editing, setEditing] = useState(false);
  const [powerOpen, setPowerOpen] = useState(false);
  const st = statusOf(conn);
  const retryAt = conn?.nextRetryAt.value ?? null;
  const pinLock = conn?.pinLockedUntil.value ?? 0;
  const now = useNow(retryAt != null || pinLock > Date.now());

  useEffect(() => {
    // a linked account's mainframes arrive a moment after start
    // read the live values: this effect may run after a later render already changed them
    if (!saved.value.some((m) => m.mainframeId === id) && !accountLoading.value) navigate("/", true);
  }, [entry, accountLoading.value]);
  if (!entry || !conn) return null;

  const info = conn.info.value;
  const uplinks = conn.uplinks.value;
  const readOnly = !conn.canControl.value;
  const locked = st.kind !== "online" || readOnly;
  const unread = conn.unread.value;
  const secs = retryAt ? Math.max(0, Math.ceil((retryAt - now) / 1000)) : 0;

  return (
    <>
      <header class="dash-head">
        <button type="button" class="icon-btn" aria-label="Back to mainframes" onClick={() => navigate("/")}>
          <IconBack />
        </button>
        <div class="dash-title">
          <span class="overline-row">
            <span class="overline">Mainframe</span>
            {info?.power && <PowerMeter p={info.power} onClick={() => setPowerOpen(!powerOpen)} />}
          </span>
          <MainframeSwitcher currentId={id} name={info?.name ?? entry.name} isAdmin={conn.isAdmin} />
        </div>
        <span class={`pill pill-${st.kind}`}>
          <span class="pill-dot" />
          <span class="pill-text">{st.label}</span>
        </span>
        <button
          type="button"
          class="icon-btn"
          aria-label={`Alerts${unread ? ` (${unread} new)` : ""}`}
          onClick={() => {
            setSheet("alerts");
            conn.markRead();
          }}
        >
          <IconBell />
          {unread > 0 && <span class="badge badge-dot">{unread > 9 ? "9+" : unread}</span>}
        </button>
        {conn.isAdmin && conn.status.value === "open" && uplinks.length > 0 && (
          <button type="button" class={`icon-btn ${editing ? "on" : ""}`} aria-label={editing ? "Leave layout editor" : "Edit layout"} aria-pressed={editing} onClick={() => (editing ? window.dispatchEvent(new Event("cccloud:edit-exit")) : setEditing(true))}>
            <IconPencil />
          </button>
        )}
        <DocsLink class="icon-btn help-btn hide-narrow" label="Help & docs">
          ?
        </DocsLink>
        <button type="button" class="icon-btn" aria-label="Settings" onClick={() => setSheet("settings")}>
          <IconGear />
        </button>
      </header>

      {powerOpen && info?.power && <PowerPopover p={info.power} conn={conn} onClose={() => setPowerOpen(false)} />}
      <main class="page page-dash" id="main" tabIndex={-1}>
        {st.text && (
          <div class={`banner banner-${st.kind}`} role="status">
            <span class="banner-icon">
              {st.kind === "disabled" || st.kind === "no_power" ? <IconPower size={18} /> : st.kind === "offline" ? <IconAlert size={18} /> : <IconWifiOff size={18} />}
            </span>
            <div class="banner-body">
              <div class="banner-text">{st.text}</div>
              {(st.kind === "reconnecting" || st.kind === "unreachable" || st.kind === "lan_only") && (
                <div class="banner-actions">
                  <span class="banner-sub">{secs > 0 ? `Next try in ${secs}s` : "Trying…"}</span>
                  <button type="button" class="btn btn-small" onClick={() => conn.retryNow()}>
                    Retry now
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
        {pinLock > now && (
          <div class="banner banner-disabled" role="status">
            <span class="banner-icon">
              <IconLock size={18} />
            </span>
            <div class="banner-text">
              Too many wrong PINs: PIN-protected controls on this device are locked for {formatDuration(Math.ceil((pinLock - now) / 1000))}. The owner was notified in-game.
            </div>
          </div>
        )}
        {readOnly && st.kind === "online" && (
          <div class="banner banner-readonly" role="status">
            <span class="banner-icon">
              <IconLock size={18} />
            </span>
            <div class="banner-text">
              {conn.role.value === "viewer" ? (
                <>
                  <b>Viewer:</b> you can watch values and alerts but not operate this mainframe. An admin can give your player the operator role.
                </>
              ) : (
                "Read-only device: you can watch values and alerts, but not press controls."
              )}
            </div>
          </div>
        )}

        {st.kind === "connecting" && uplinks.length === 0 ? (
          <div class="grid">
            {[0, 1].map((i) => (
              <div key={i} class="card skeleton" />
            ))}
          </div>
        ) : uplinks.length === 0 ? (
          <SetupGuide />
        ) : (
          <Pages conn={conn} locked={locked} dim={st.kind !== "online"} editing={editing && conn.canControl.value} onExitEdit={() => setEditing(false)} />
        )}
      </main>

      {sheet === "alerts" && (
        <Sheet title="Alerts" onClose={() => setSheet(null)}>
          {notifyPermission.value === "default" && (
            <div class="notify-cta">
              <p>Get a system notification for critical alerts while this tab is in the background.</p>
              <button type="button" class="btn" onClick={requestNotifications}>
                Enable notifications
              </button>
            </div>
          )}
          {notifyPermission.value === "denied" && <p class="muted small">Notifications are blocked in your browser settings.</p>}
          {conn.alerts.value.length === 0 ? (
            <p class="muted">No alerts yet. Scripts send them with cloud.alert(text, "info"|"warn"|"crit").</p>
          ) : (
            <ul class="alert-log">
              {[...conn.alerts.value].reverse().map((a, i) => (
                <li key={`${a.ts}-${i}`} class={`alert-item lvl-${a.level}`}>
                  <span class="alert-level">{a.level}</span>
                  <div class="alert-main">
                    <div class="alert-text">{a.text}</div>
                    <div class="alert-meta">
                      {a.uplinkTitle ? `${a.uplinkTitle} · ` : ""}
                      {timeAgo(a.ts)}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Sheet>
      )}

      {sheet === "settings" && (
        <Sheet title="This mainframe" onClose={() => setSheet(null)}>
          <dl class="facts">
            <dt>Name</dt>
            <dd>{info?.name ?? entry.name}</dd>
            <dt>This browser</dt>
            <dd>
              {entry.deviceName}
              {readOnly ? " (read-only)" : ""}
            </dd>
            {conn.account.value && (
              <>
                <dt>Player</dt>
                <dd>{conn.account.value.player}</dd>
              </>
            )}
            {conn.role.value && conn.role.value !== "share" && (
              <>
                <dt>Your role</dt>
                <dd>
                  <span class={`role-badge r-${conn.role.value}`}>{conn.role.value}</span> <span class="muted small">{ROLE_INFO[conn.role.value]}</span>
                </dd>
              </>
            )}
            <dt>Paired</dt>
            <dd>{new Date(entry.pairedAt).toLocaleString()}</dd>
            <dt>Mainframe ID</dt>
            <dd class="mono small">{id}</dd>
          </dl>
          <div class="theme-row">
            <span class="muted small">Theme</span>
            <div class="seg-ctl" role="radiogroup" aria-label="Theme">
              {(["dark", "light", "system"] as const).map((t) => (
                <button key={t} type="button" role="radio" aria-checked={themePref.value === t} class={themePref.value === t ? "on" : ""} onClick={() => setTheme(t)}>
                  {t}
                </button>
              ))}
            </div>
          </div>
          <div class="settings-links">
            <a href="#/devices">Devices</a>
            {conn.isAdmin && <a href={`#/m/${encodeURIComponent(id)}/access`}>Access &amp; roles</a>}
            {conn.isAdmin && <a href={`#/m/${encodeURIComponent(id)}/shares`}>Share links</a>}
            {!conn.isAdmin && conn.role.value && <span class="muted small">Access and share links need the admin role.</span>}
          </div>
          <p class="small">
            <DocsLink to="/docs">Help &amp; docs</DocsLink> · <DocsLink to="/docs/faq">Troubleshooting</DocsLink>
          </p>
          <p class="muted small">
            The owner can list and revoke devices in-game with <code>/cccloud devices</code> and <code>/cccloud revoke</code>.
          </p>
          <button
            type="button"
            class="btn btn-danger"
            onClick={() => {
              if (!confirm(`Remove ${info?.name ?? entry.name} from this browser? You will need a new pairing code to add it again.`)) return;
              removeSaved(id);
              syncConns();
              navigate(saved.value.length ? "/" : "/pair", true);
            }}
          >
            Remove from this browser
          </button>
        </Sheet>
      )}
    </>
  );
}
