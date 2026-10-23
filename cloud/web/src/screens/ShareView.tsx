// v0.6: /s/<id>#<secret> – a read-only share-link view.
// The secret only ever lives in the URL fragment (never sent in requests, logs or
// Referer). We take it from location.hash, remove it from the address bar with
// history.replaceState, keep it in memory only (never in any storage) and send it
// in the first WebSocket message. After a reload the link has to be opened again.
import { useEffect, useMemo, useState } from "preact/hooks";
import { MainframeConn } from "../lib/conn.ts";
import { forgetStoredShareSecrets, takeShareSecret } from "../lib/share.ts";
import { resolveLayout } from "../lib/layout.ts";
import { timeAgo } from "../lib/ui.ts";
import { Card } from "../components/Card.tsx";
import { Logo } from "../components/Icons.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Toasts } from "../components/Overlays.tsx";

const ERRORS: Record<string, string> = {
  share_expired: "This share link has expired.",
  share_revoked: "This share link was revoked by its owner.",
  share_invalid: "This share link is not valid.",
  share_full: "Too many people are viewing this link right now. Try again in a while.",
};

export function ShareView() {
  const [{ id, secret }] = useState(() => {
    try {
      forgetStoredShareSecrets(sessionStorage);
    } catch {}
    const r = takeShareSecret(location);
    // remove the secret from the address bar and the history entry
    if (r.scrub) history.replaceState(null, "", `/s/${r.id}`);
    return r;
  });
  const conn = useMemo(() => (id && secret ? new MainframeConn({ baseUrl: location.origin, token: "", share: { id, secret } }) : null), [id, secret]);
  const [page, setPage] = useState<string | null>(null);
  const [narrow, setNarrow] = useState(() => matchMedia("(max-width: 1099px)").matches);
  useEffect(() => {
    const mq = matchMedia("(max-width: 1099px)");
    const h = (e: MediaQueryListEvent) => setNarrow(e.matches);
    mq.addEventListener("change", h);
    return () => mq.removeEventListener("change", h);
  }, []);
  const share = conn?.shareInfo.value ?? null;

  useEffect(() => {
    if (!conn) return;
    conn.start();
    return () => conn.stop();
  }, [conn]);
  useEffect(() => {
    document.title = share ? `${share.label} – CC: Cloud` : "Shared dashboard – CC: Cloud";
  }, [share]);

  if (!id || !secret || !conn)
    return (
      <div class="app">
        <main class="page page-narrow" id="main">
          {id ? (
            <>
              <h1 class="px-heading">Open the link again</h1>
              <p class="muted">For safety, the secret part of a share link (everything after #) is never stored in this browser – so after a reload or a bookmark it is gone. Open the complete link you were sent again.</p>
            </>
          ) : (
            <>
              <h1 class="px-heading">Share link incomplete</h1>
              <p class="muted">The link is missing its secret part (everything after #). Ask for the complete link.</p>
            </>
          )}
        </main>
      </div>
    );

  const status = conn.status.value;
  const info = conn.info.value;
  const err = conn.lastError.value;
  const pages = resolveLayout(conn.uplinks.value, conn.layout.value).filter((p) => p.cards.length);
  const current = pages.find((p) => p.id === page) ?? pages[0];

  return (
    <div class={`app share-app ${share?.hideNumbers ? "hide-numbers" : ""}`}>
      <header class="topbar share-top">
        <span class="brand">
          <Logo size={26} />
          <span class="brand-text">
            <span class="brand-cc">CC:</span>
            <span class="brand-cloud">Cloud</span>
          </span>
        </span>
        <span class="pill pill-readonly">
          <span class="pill-dot" />
          Read-only
        </span>
      </header>
      <main class="page" id="main" tabIndex={-1}>
        {status === "unauthorized" ? (
          <div class="empty">
            <h1 class="px-title">Not available</h1>
            <p>{ERRORS[err ?? ""] ?? "This share link cannot be opened."}</p>
          </div>
        ) : (
          <>
            <div class="banner banner-share" role="status">
              <span class="banner-icon" aria-hidden="true">
                ⇗
              </span>
              <div class="banner-text">
                <b>{share?.label ?? "Shared dashboard"}</b> – shared by <b>{share?.sharedBy ?? "…"}</b>
                {info?.name ? ` from ${info.name}` : ""}.{" "}
                {share?.expires ? (share.expires > Date.now() ? `Expires ${expiresText(share.expires)}.` : "Expired.") : ""} You can watch, not control.
              </div>
            </div>
            {status !== "open" && <div class="banner banner-reconnecting">{status === "unreachable" ? "Server not reachable – retrying…" : "Connecting…"}</div>}
            {info && !info.online && <div class="banner banner-offline">The mainframe is offline. Showing the last known values.</div>}
            {pages.length > 1 && (
              <nav class="toptabs" aria-label="Pages">
                {pages.map((p) => (
                  <button key={p.id} type="button" class={`tab ${p.id === current?.id ? "on" : ""}`} aria-current={p.id === current?.id ? "page" : undefined} onClick={() => setPage(p.id)}>
                    <PixelIcon name={p.icon ?? "box"} size={14} />
                    <span class="tab-text">{p.title}</span>
                  </button>
                ))}
              </nav>
            )}
            {current ? (
              <div class={`pgrid ${narrow ? "narrow" : ""}`}>
                {current.cards.map((c) => (
                  <Card key={c.key} card={c} conn={conn} locked viewOnly />
                ))}
              </div>
            ) : (
              status === "open" && <div class="empty">Nothing is shared here right now.</div>
            )}
          </>
        )}
      </main>
      <Toasts />
    </div>
  );
}

function expiresText(ts: number) {
  const s = Math.round((ts - Date.now()) / 1000);
  if (s < 3600) return `in ${Math.max(1, Math.round(s / 60))} min`;
  if (s < 86400) return `in ${Math.round(s / 3600)} h`;
  return `on ${new Date(ts).toLocaleDateString()}`;
}

export { timeAgo };
