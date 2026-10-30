// Pages/tabs + the card grid (12-column page grid, layout v2). Edit mode is the
// WYSIWYG editor in its own lazy chunk (edit/Editor.tsx).
import { useEffect, useMemo, useState } from "preact/hooks";
import type { ComponentType } from "preact";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import { resolveLayout } from "../lib/layout.ts";
import { adaptComputerCard } from "../lib/redstone.ts";
import { Card } from "../components/Card.tsx";
import { NETWORK_PAGE, PRODUCTION_PAGE, Tabs } from "../components/PageTabs.tsx";
import { feature } from "../lib/config.ts";
import { t } from "../lib/i18n.ts";
import { route } from "../lib/ui.ts";

export { NETWORK_PAGE, PRODUCTION_PAGE };

/** v0.10: the production page is its own lazily loaded chunk. */
function ProductionLoader({ conn }: { conn: MainframeConn }) {
  const [Comp, setComp] = useState<ComponentType<{ conn: MainframeConn }> | null>(null);
  useEffect(() => {
    import("./Production.tsx").then((m) => setComp(() => m.default));
  }, []);
  return Comp ? <Comp conn={conn} /> : <div class="card skeleton net-skeleton" />;
}

/** The network page is its own lazily loaded chunk. */
function NetworkLoader({ conn, onOpenCard }: { conn: MainframeConn; onOpenCard: (uplinkId: string) => void }) {
  const [Comp, setComp] = useState<ComponentType<{ conn: MainframeConn; onOpenCard?: (id: string) => void }> | null>(null);
  useEffect(() => {
    import("../network/Network.tsx").then((m) => setComp(() => m.default));
  }, []);
  return Comp ? <Comp conn={conn} onOpenCard={onOpenCard} /> : <div class="card skeleton net-skeleton" />;
}

function useMedia(q: string) {
  const mq = useMemo(() => matchMedia(q), [q]);
  const [m, setM] = useState(mq.matches);
  useEffect(() => {
    const h = (e: MediaQueryListEvent) => setM(e.matches);
    mq.addEventListener("change", h);
    return () => mq.removeEventListener("change", h);
  }, [mq]);
  return m;
}

const pageKey = (id: string) => `cccloud.page.${id}`;

export function Pages(props: { conn: MainframeConn; locked: boolean; dim: boolean; editing: boolean; onExitEdit: () => void }) {
  const { conn } = props;
  const isPhone = useMedia("(max-width: 719px)");
  const narrow = useMedia("(max-width: 1099px)");
  const uplinks = conn.uplinks.value.map(adaptComputerCard);
  const pages = resolveLayout(uplinks, conn.layout.value);
  const [current, setCurrent] = useState<string>(() => {
    try {
      return sessionStorage.getItem(pageKey(conn.info.value?.id ?? "")) ?? "";
    } catch {
      return "";
    }
  });
  // the Network page is always there (not part of the editable layout)
  const showNetwork = conn.network.value !== null;
  const onNetwork = showNetwork && current === NETWORK_PAGE && !props.editing;
  // v0.10: the Production page too (#/m/<id>/production selects it)
  const showProduction = feature("production") && !conn.isShare;
  const onProduction = showProduction && current === PRODUCTION_PAGE && !props.editing;
  const page = pages.find((p) => p.id === current) ?? pages[0];
  const mfId = conn.info.value?.id ?? "";
  const select = (id: string) => {
    setCurrent(id);
    try {
      sessionStorage.setItem(pageKey(mfId), id);
    } catch {}
    // keep the address in step with the production page (replace: tabs are not history entries)
    const want = id === PRODUCTION_PAGE ? `#/m/${encodeURIComponent(mfId)}/production` : `#/m/${encodeURIComponent(mfId)}`;
    if (mfId && location.hash.startsWith("#/m/") && location.hash !== want) history.replaceState(null, "", `/${want}`);
  };
  const r = route.value;
  useEffect(() => {
    if (r.name === "mainframe" && r.tab === "production" && showProduction) select(PRODUCTION_PAGE);
  }, [r, showProduction]);
  const showTabs = pages.length > 1 || showNetwork || showProduction;
  const openCard = (uplinkId: string) => {
    const target = pages.find((p) => p.cards.some((c) => c.uplink.uplinkId === uplinkId || c.widgets.some((w) => w.uplink.uplinkId === uplinkId)));
    if (!target) return;
    select(target.id);
    const key = target.cards.find((c) => c.uplink.uplinkId === uplinkId || c.widgets.some((w) => w.uplink.uplinkId === uplinkId))!.key;
    setTimeout(() => {
      const el = document.querySelector(`[data-card="${CSS.escape(key)}"]`);
      el?.scrollIntoView({ block: "start", behavior: "smooth" });
      el?.classList.add("flash-card");
      setTimeout(() => el?.classList.remove("flash-card"), 1600);
    }, 60);
  };

  // #/m/<id>?card=<uplinkId> (in-game "Show in dashboard"): open the card's page and scroll to it once it is there
  const wantCard = r.name === "mainframe" && !props.editing ? r.card : undefined;
  const cardThere = !!wantCard && pages.some((p) => p.cards.some((c) => c.uplink.uplinkId === wantCard || c.widgets.some((w) => w.uplink.uplinkId === wantCard)));
  useEffect(() => {
    if (!wantCard || !cardThere) return;
    openCard(wantCard);
    if (mfId) history.replaceState(null, "", `/#/m/${encodeURIComponent(mfId)}`);
    route.value = { name: "mainframe", id: r.name === "mainframe" ? r.id : mfId };
  }, [wantCard, cardThere]);

  if (props.editing)
    return <EditorLoader conn={conn} uplinks={uplinks} page={current && current !== NETWORK_PAGE && current !== PRODUCTION_PAGE ? current : page.id} onPage={select} isPhone={isPhone} narrow={narrow} network={showNetwork} onExit={props.onExitEdit} />;

  return (
    <>
      {showTabs && (
        <Tabs
          pages={pages}
          network={showNetwork}
          production={showProduction ? t("pr.tab") : undefined}
          current={onNetwork ? NETWORK_PAGE : onProduction ? PRODUCTION_PAGE : page.id}
          isPhone={isPhone}
          editing={false}
          onSelect={select}
          onAdd={() => {}}
          onChange={() => {}}
        />
      )}
      {onNetwork ? (
        <NetworkLoader conn={conn} onOpenCard={openCard} />
      ) : onProduction ? (
        <ProductionLoader conn={conn} />
      ) : page.cards.length === 0 ? (
        <div class="empty">
          <h2 class="px-title">Nothing on this page</h2>
          <p>Open the layout editor (pencil) to move cards here, or put widgets on it from Lua with opts.page.</p>
        </div>
      ) : (
        <div class={`pgrid ${narrow ? "narrow" : ""} ${props.dim ? "grid-dim" : ""}`}>
          {page.cards.map((c) => (
            <Card key={c.key} card={c} conn={conn} locked={props.locked} />
          ))}
        </div>
      )}
      {showTabs && isPhone && <div class="tabbar-spacer" />}
    </>
  );
}

type EditorProps = { conn: MainframeConn; uplinks: Uplink[]; page: string; onPage: (id: string) => void; isPhone: boolean; narrow: boolean; network: boolean; onExit: () => void };
let EditorComp: ComponentType<EditorProps> | null = null;
function EditorLoader(props: EditorProps) {
  const [C, setC] = useState<ComponentType<EditorProps> | null>(() => EditorComp);
  useEffect(() => {
    if (!C) import("../edit/Editor.tsx").then((m) => setC(() => (EditorComp = m.default)));
  }, []);
  return C ? <C {...props} /> : <div class="card skeleton net-skeleton" />;
}
