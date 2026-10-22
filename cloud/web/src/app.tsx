import { useState } from "preact/hooks";
import { effect } from "@preact/signals";
import { route, navigate, theme, themePref, cycleTheme, applyTheme, inDocs } from "./lib/ui.ts";
import { DocsLink } from "./components/DocsLink.tsx";
import { useEffect } from "preact/hooks";
import type { ComponentType } from "preact";
import { saved } from "./lib/store.ts";
import { syncConns } from "./lib/conns.ts";
import { insecureContext } from "./lib/security.ts";
import { MainframeList } from "./screens/MainframeList.tsx";
import { PairScreen } from "./screens/Pair.tsx";
import { Dashboard } from "./screens/Dashboard.tsx";
import { Toasts } from "./components/Overlays.tsx";
import { IconAuto, IconMoon, IconSun, Logo, IconAlert, IconClose } from "./components/Icons.tsx";

effect(() => {
  theme.value;
  applyTheme();
});

// Open a connection per paired mainframe before the first render, and keep the
// set in sync when mainframes are paired or removed.
effect(() => {
  saved.value;
  syncConns();
});

function TopBar() {
  const pref = themePref.value;
  return (
    <header class="topbar">
      <button type="button" class="brand" onClick={() => navigate("/")} aria-label="CC: Cloud home">
        <Logo size={26} />
        <span class="brand-text">
          <span class="brand-cc">CC:</span>
          <span class="brand-cloud">Cloud</span>
        </span>
      </button>
      <span class="topbar-actions">
        <DocsLink class="icon-btn help-btn" label="Help & docs">
          ?
        </DocsLink>
        <button type="button" class="icon-btn" onClick={cycleTheme} aria-label={`Theme: ${pref}`} title={`Theme: ${pref}`}>
          {pref === "dark" ? <IconMoon /> : pref === "light" ? <IconSun /> : <IconAuto />}
        </button>
      </span>
    </header>
  );
}

function InsecureBanner() {
  const [hidden, setHidden] = useState(() => sessionStorage.getItem("cccloud.httpwarn") === "1");
  if (!insecureContext() || hidden) return null;
  return (
    <div class="banner banner-insecure" role="alert">
      <span class="banner-icon">
        <IconAlert size={18} />
      </span>
      <div class="banner-text">
        <b>Unencrypted connection.</b> This dashboard is served over plain HTTP from a public address, so your access token and actions can be read on the way. Ask the server admin to enable TLS or put it behind an HTTPS proxy.
      </div>
      <button
        type="button"
        class="icon-btn"
        aria-label="Dismiss warning"
        onClick={() => {
          try {
            sessionStorage.setItem("cccloud.httpwarn", "1");
          } catch {}
          setHidden(true);
        }}
      >
        <IconClose size={16} />
      </button>
    </div>
  );
}

/** The docs are a separate, lazily loaded chunk. */
function DocsLoader() {
  const [Comp, setComp] = useState<ComponentType | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    import("./docs/Docs.tsx").then((m) => setComp(() => m.default)).catch(() => setFailed(true));
  }, []);
  if (failed)
    return (
      <main class="page page-narrow">
        <p class="form-error">The docs could not be loaded. Check the connection and reload.</p>
      </main>
    );
  return Comp ? <Comp /> : <div class="docs-loading" aria-busy="true" />;
}

export function App() {
  if (inDocs.value)
    return (
      <div class="app">
        <DocsLoader />
      </div>
    );
  const r = route.value;
  // no mainframes yet -> the pairing screen is the home screen
  const effective = r.name === "list" && saved.value.length === 0 ? { name: "pair" as const } : r;

  return (
    <div class="app">
      {effective.name !== "mainframe" && <TopBar />}
      <InsecureBanner />
      {effective.name === "pair" && <PairScreen initialCode={"code" in effective ? effective.code : undefined} />}
      {effective.name === "list" && <MainframeList />}
      {effective.name === "mainframe" && <Dashboard key={effective.id} id={effective.id} />}
      <Toasts />
    </div>
  );
}
