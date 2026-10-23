import { saved } from "../lib/store.ts";
import { getConn } from "../lib/conns.ts";
import { statusOf } from "../lib/status.ts";
import { navigate, pendingInstall } from "../lib/ui.ts";
import { useEffect } from "preact/hooks";
import { IconChevron, IconPlus } from "../components/Icons.tsx";

export function MainframeList() {
  const list = saved.value;
  const want = pendingInstall.value;
  // a store install link with only one mainframe: go straight there
  useEffect(() => {
    if (want && list.length === 1) navigate(`/m/${encodeURIComponent(list[0].mainframeId)}`, true);
  }, [want, list.length]);
  return (
    <main class="page" id="main" tabIndex={-1}>
      <div class="page-head">
        <h1 class="px-heading">Mainframes</h1>
        <button type="button" class="btn btn-ghost" onClick={() => navigate("/pair")}>
          <IconPlus size={18} /> Pair
        </button>
      </div>
      {want && (
        <div class="banner banner-readonly" role="status">
          <div class="banner-text">
            Pick the mainframe to install <b>{want.id}</b> on from the Script Store.{" "}
            <button type="button" class="link-btn" onClick={() => (pendingInstall.value = null)}>
              Cancel
            </button>
          </div>
        </div>
      )}
      <div class="mf-grid">
        {list.map((m) => {
          const conn = getConn(m.mainframeId);
          const st = statusOf(conn);
          const uplinks = conn?.uplinks.value ?? [];
          const online = uplinks.filter((u) => u.online).length;
          const unread = conn?.unread.value ?? 0;
          const last = conn?.alerts.value.at(-1);
          return (
            <button type="button" class="mf-card" key={m.mainframeId} onClick={() => navigate(`/m/${encodeURIComponent(m.mainframeId)}`)}>
              <div class="mf-icon" aria-hidden="true">
                <span class={`mf-icon-led st-${st.kind}`} />
              </div>
              <div class="mf-main">
                <div class="mf-name">{conn?.info.value?.name ?? m.name}</div>
                <div class="mf-meta">
                  <span class={`pill pill-${st.kind}`}>
                    <span class="pill-dot" />
                    {st.label}
                  </span>
                  {uplinks.length > 0 && (
                    <span class="mf-sub">
                      {online}/{uplinks.length} uplinks
                    </span>
                  )}
                </div>
                {last && <div class={`mf-last lvl-${last.level}`}>{last.text}</div>}
              </div>
              {unread > 0 && <span class="badge">{unread > 9 ? "9+" : unread}</span>}
              <IconChevron size={18} class="mf-chevron" />
            </button>
          );
        })}
        <button type="button" class="mf-card mf-add" onClick={() => navigate("/pair")}>
          <IconPlus size={22} />
          <span>Pair another mainframe</span>
        </button>
      </div>
    </main>
  );
}
