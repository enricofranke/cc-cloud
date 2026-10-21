import { useEffect, useState } from "preact/hooks";
import { getConn, syncConns } from "../lib/conns.ts";
import { formatDuration } from "../lib/conn.ts";
import { removeSaved, saved } from "../lib/store.ts";
import { statusOf } from "../lib/status.ts";
import { navigate, notifyPermission, requestNotifications, setTheme, themePref, timeAgo } from "../lib/ui.ts";
import { UplinkCard } from "../components/UplinkCard.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { IconBack, IconBell, IconGear, IconLock, IconPower, IconWifiOff, IconAlert } from "../components/Icons.tsx";

function useNow(active: boolean) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [active]);
  return now;
}

export function Dashboard({ id }: { id: string }) {
  const entry = saved.value.find((m) => m.mainframeId === id);
  const conn = getConn(id);
  const [sheet, setSheet] = useState<"alerts" | "settings" | null>(null);
  const st = statusOf(conn);
  const retryAt = conn?.nextRetryAt.value ?? null;
  const pinLock = conn?.pinLockedUntil.value ?? 0;
  const now = useNow(retryAt != null || pinLock > Date.now());

  useEffect(() => {
    if (!entry) navigate("/", true);
  }, [entry]);
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
          <span class="overline">Mainframe</span>
          <h1 class="dash-name">{info?.name ?? entry.name}</h1>
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
        <button type="button" class="icon-btn" aria-label="Settings" onClick={() => setSheet("settings")}>
          <IconGear />
        </button>
      </header>

      <main class="page page-dash">
        {st.text && (
          <div class={`banner banner-${st.kind}`} role="status">
            <span class="banner-icon">
              {st.kind === "disabled" ? <IconPower size={18} /> : st.kind === "offline" ? <IconAlert size={18} /> : <IconWifiOff size={18} />}
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
            <div class="banner-text">Read-only device: you can watch values and alerts, but not press controls.</div>
          </div>
        )}

        {st.kind === "connecting" && uplinks.length === 0 ? (
          <div class="grid">
            {[0, 1].map((i) => (
              <div key={i} class="card skeleton" />
            ))}
          </div>
        ) : uplinks.length === 0 ? (
          <div class="empty">
            <h2 class="px-title">No uplinks yet</h2>
            <p>Place a Cloud Uplink next to a computer, bind it with a Link Card and build its section in Lua:</p>
            <pre class="code">{`local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("Fission Reactor")
cloud.gauge("temp", "Core temperature",
  {min=0, max=1200, unit="K", warn=900, crit=1100})
cloud.button("scram", "SCRAM", {color="red", confirm=true})`}</pre>
          </div>
        ) : (
          <div class={`grid ${st.kind !== "online" ? "grid-dim" : ""}`}>
            {uplinks.map((u) => (
              <UplinkCard key={u.uplinkId} uplink={u} conn={conn} locked={locked} />
            ))}
          </div>
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
            <dt>This device</dt>
            <dd>
              {entry.deviceName}
              {readOnly ? " (read-only)" : ""}
            </dd>
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
