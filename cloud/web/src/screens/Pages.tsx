// Pages/tabs + the card grid (12-column page grid, layout v2). Edit mode is the
// WYSIWYG editor in its own lazy chunk (edit/Editor.tsx).
import { useEffect, useMemo, useState } from "preact/hooks";
import type { ComponentType } from "preact";
import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import { resolveLayout } from "../lib/layout.ts";
import { adaptComputerCard } from "../lib/redstone.ts";
import { Card } from "../components/Card.tsx";
import { NETWORK_PAGE, Tabs } from "../components/PageTabs.tsx";

export { NETWORK_PAGE };

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
  const page = pages.find((p) => p.id === current) ?? pages[0];
  const select = (id: string) => {
    setCurrent(id);
    try {
      sessionStorage.setItem(pageKey(conn.info.value?.id ?? ""), id);
    } catch {}
  };
  const showTabs = pages.length > 1 || showNetwork;
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

  if (props.editing)
    return <EditorLoader conn={conn} uplinks={uplinks} page={current && current !== NETWORK_PAGE ? current : page.id} onPage={select} isPhone={isPhone} narrow={narrow} network={showNetwork} onExit={props.onExitEdit} />;

  return (
    <>
      {showTabs && <Tabs pages={pages} network={showNetwork} current={onNetwork ? NETWORK_PAGE : page.id} isPhone={isPhone} editing={false} onSelect={select} onAdd={() => {}} onChange={() => {}} />}
      {onNetwork ? (
        <NetworkLoader conn={conn} onOpenCard={openCard} />
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
