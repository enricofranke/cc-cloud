// v0.4 Network page: UniFi-style topology and a top-down map per dimension.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { ComponentChildren, ComponentType } from "preact";

/**
 * Re-uses the last rendered SVG layer while its inputs are unchanged, so
 * panning a 500-node graph only moves the viewBox instead of rebuilding nodes.
 */
type Cache = { deps: unknown[]; el: ComponentChildren };
function cached(ref: { current?: Cache }, deps: unknown[], make: () => ComponentChildren) {
  const c = ref.current;
  if (c && c.deps.length === deps.length && c.deps.every((d, i) => d === deps[i])) return c.el;
  const el = make();
  ref.current = { deps, el };
  return el;
}
import type { MainframeConn } from "../lib/conn.ts";
import type { NetNode, NetworkInfo, NodeType } from "../lib/types.ts";
import {
  STATUS_HINT,
  STATUS_LABEL,
  TREE_DX,
  TREE_DY,
  TYPE_LABEL,
  dimLabel,
  dimsOf,
  findLink,
  gridStep,
  hopsOf,
  isNodeType,
  isRelay,
  layoutTree,
  mapBounds,
  parentOf,
  pathOf,
  signalBars,
  signalClass,
  statusCounts,
  uplinkOf,
} from "../lib/network.ts";
import { fmtFE } from "../lib/format.ts";
import { timeAgo, pushToast } from "../lib/ui.ts";
import { PanZoom } from "./PanZoom.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";
import { Card } from "../components/Card.tsx";
import { resolveLayout } from "../lib/layout.ts";
import { fetchTerrain, type Terrain } from "../lib/api.ts";
import { hasWebGL, searchNodes } from "../lib/net3d.ts";
import type { View3DProps } from "./Network3D.tsx";

// ------------------------------------------------------------------- 3D view
let View3DComp: ComponentType<View3DProps> | null = null;
function View3DLoader(props: View3DProps) {
  const [Comp, setComp] = useState<ComponentType<View3DProps> | null>(() => View3DComp);
  useEffect(() => {
    if (Comp) return;
    import("./Network3D.tsx")
      .then((m) => setComp(() => (View3DComp = m.default)))
      .catch(() => props.onFail("load_failed"));
  }, []);
  return Comp ? <Comp {...props} /> : <div class="n3d n3d-loading">Loading 3D view…</div>;
}

/** Terrain per mainframe + dimension, kept for the session (the server caches 60 s and rate-limits). */
const terrainCache = new Map<string, Terrain | null>();
function useTerrain(conn: MainframeConn, dim: string, on: boolean) {
  const mf = conn.info.value?.id ?? "";
  const key = `${mf}|${dim}`;
  const [t, setT] = useState<Terrain | null>(terrainCache.get(key) ?? null);
  useEffect(() => {
    if (!on || !mf || !conn.canControl.value) return setT(null);
    if (terrainCache.has(key)) return setT(terrainCache.get(key)!);
    setT(null);
    let stop = false;
    let timer = 0;
    const load = (retry: boolean) =>
      fetchTerrain(conn.token, mf, dim, conn.baseUrl)
        .then((r) => {
          terrainCache.set(key, r);
          if (!stop) setT(r);
        })
        .catch((e: Error) => {
          const m = /^rate_limited:(\d+)/.exec(e.message);
          if (m && retry && !stop) timer = window.setTimeout(() => load(false), Number(m[1]) * 1000);
        });
    load(true);
    return () => {
      stop = true;
      clearTimeout(timer);
    };
  }, [key, on]);
  return t;
}

function FlySearch({ net, onPick }: { net: NetworkInfo; onPick: (id: string) => void }) {
  const [q, setQ] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const hits = useMemo(() => searchNodes(net, q), [net, q]);
  const pick = (n: NetNode) => {
    onPick(n.id);
    setQ("");
    setOpen(false);
  };
  return (
    <div class="fly-search">
      <input
        type="search"
        class="fly-input"
        placeholder="Fly to…"
        aria-label="Fly to a block"
        role="combobox"
        aria-expanded={open && hits.length > 0}
        aria-controls="fly-list"
        value={q}
        onInput={(e) => {
          setQ((e.target as HTMLInputElement).value);
          setOpen(true);
          setActive(0);
        }}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") setActive((a) => Math.min(hits.length - 1, a + 1));
          else if (e.key === "ArrowUp") setActive((a) => Math.max(0, a - 1));
          else if (e.key === "Enter" && hits[active]) pick(hits[active]);
          else if (e.key === "Escape") setOpen(false);
          else return;
          e.preventDefault();
        }}
      />
      {open && hits.length > 0 && (
        <ul class="fly-list" id="fly-list" role="listbox">
          {hits.map((n, i) => (
            <li key={n.id} role="option" aria-selected={i === active} class={i === active ? "on" : ""} onMouseDown={(e) => (e.preventDefault(), pick(n))}>
              <span class={`sdot-s st-${n.status}`} />
              <span class="fly-name">{n.name || "Device"}</span>
              <span class="muted small">
                {TYPE_LABEL[n.type].replace("Cloud ", "")} · {dimLabel(n.dim)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const ICON: Record<NodeType, string> = {
  mainframe: "mainframe",
  relay: "relay",
  advanced_relay: "relay",
  ender_relay: "ender",
  uplink: "uplink",
  advanced_uplink: "uplink",
  bridge: "bridge",
  device: "uplink",
  me_bridge: "bridge",
  rs_bridge: "bridge",
};

function useIsPhone() {
  const mq = useMemo(() => matchMedia("(max-width: 899px)"), []);
  const [phone, setPhone] = useState(mq.matches);
  useEffect(() => {
    const h = (e: MediaQueryListEvent) => setPhone(e.matches);
    mq.addEventListener("change", h);
    return () => mq.removeEventListener("change", h);
  }, [mq]);
  return phone;
}

function Bars({ signal, kind }: { signal: number; kind?: string }) {
  const n = kind === "ender" ? 4 : signalBars(signal);
  return (
    <span class={`bars b${n}`} aria-label={`${Math.round(signal * 100)} % signal`}>
      {[1, 2, 3, 4].map((i) => (
        <span key={i} class={i <= n ? "on" : ""} />
      ))}
    </span>
  );
}

// ------------------------------------------------------------------ topology
function Topology({ net, selected, onSelect, lr }: { net: NetworkInfo; selected?: string; onSelect: (id?: string) => void; lr: boolean }) {
  const tree = useMemo(() => layoutTree(net, lr ? "lr" : "tb"), [net, lr]);
  const cache = useRef<Cache>();
  const pos = new Map(tree.nodes.map((t) => [t.node.id, t]));
  const bounds = lr
    ? { minX: -45, minY: -50, maxX: tree.width + 175, maxY: tree.height + 50 }
    : { minX: -TREE_DX * 0.7, minY: -TREE_DY * 0.55, maxX: tree.width + TREE_DX * 0.7, maxY: tree.height - TREE_DY * 0.25 };
  const orphans = tree.nodes.filter((t) => t.orphan);
  const parents = new Set(tree.edges.map((e) => e.from));
  const oMinX = Math.min(...orphans.map((o) => o.x));
  const oMaxX = Math.max(...orphans.map((o) => o.x));
  const oMinY = Math.min(...orphans.map((o) => o.y));
  const oMaxY = Math.max(...orphans.map((o) => o.y));
  return (
    <PanZoom bounds={bounds} fitKey={`topo-${net.nodes.length}-${lr}`} minW={150} maxW={8000} class="topo" label="Network topology" onBackgroundClick={() => onSelect(undefined)}>
      {(_v, upp) => cached(cache, [tree, selected, lr, upp < 1.4], () => (
        <>
          {orphans.length > 0 && (
            <g class="orphan-zone">
              <rect
                x={oMinX - (lr ? 40 : TREE_DX * 0.5)}
                y={oMinY - (lr ? 44 : 50)}
                width={oMaxX - oMinX + (lr ? 220 : TREE_DX)}
                height={oMaxY - oMinY + (lr ? 84 : 120)}
                rx={8}
              />
              <text x={oMinX - (lr ? 32 : TREE_DX * 0.42)} y={oMinY - (lr ? 30 : 34)} class="zone-label">
                NOT CONNECTED
              </text>
            </g>
          )}
          {tree.edges.map((e) => {
            const a = pos.get(e.from)!;
            const b = pos.get(e.to)!;
            const cls = b.node.status === "legacy_out_of_range" ? "sig-legacy" : e.link ? signalClass(e.link) : "sig-ok";
            const offline = b.node.status !== "online";
            const midX = (a.x + b.x) / 2;
            const midY = (a.y + b.y) / 2;
            // portrait: file-tree style trunk below/above the parent tile, so labels stay readable
            // portrait: a trunk just right of the parent tile; parent labels sit above-right, clear of every line
            const d = lr ? `M${a.x + 26},${a.y} H${a.x + 40} V${b.y} H${b.x - 26}`
              : `M${a.x},${a.y + 26} V${midY} H${b.x} V${b.y - 26}`;
            return (
              <g key={`${e.from}-${e.to}`} class={`edge ${cls} k-${e.link?.kind ?? "radio"} ${offline ? "dim" : ""}`}>
                <path d={d} vector-effect="non-scaling-stroke" />
                {e.link && upp < 1.4 && (
                  <text x={lr ? Math.max(a.x + 6, midX - 10) : b.x + 6} y={lr ? b.y - 6 : midY + (b.y - midY) / 2 + 4} class="edge-label">
                    {e.link.kind === "ender" ? "ender" : e.link.kind === "wired" ? "cable" : e.link.legacy ? "legacy" : `${Math.round(e.link.signal * 100)}%`}
                  </text>
                )}
              </g>
            );
          })}
          {tree.nodes.map((t) => (
            <g
              key={t.node.id}
              class={`tnode st-${t.node.status} ${selected === t.node.id ? "sel" : ""} ${isNodeType(t.node.type) ? "is-node" : "is-device"} ${lr ? "lr" : ""}`}
              transform={`translate(${t.x},${t.y})`}
              onClick={(e) => {
                e.stopPropagation();
                onSelect(t.node.id);
              }}
              role="button"
              tabIndex={0}
              aria-label={`${t.node.name}, ${TYPE_LABEL[t.node.type]}, ${STATUS_LABEL[t.node.status]}`}
              onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onSelect(t.node.id)}
            >
              <rect x={-26} y={-26} width={52} height={52} rx={t.node.type === "mainframe" ? 6 : 10} class="tile" />
              <g transform="translate(-12,-12)">
                <PixelIcon name={ICON[t.node.type]} size={24} class="tile-icon" />
              </g>
              <circle cx={20} cy={-20} r={6} class="sdot" />
              {lr && parents.has(t.node.id) ? (
                // branch nodes in portrait: label above the tile, the links leave to the right
                <text x={30} y={-34} class="tlabel end">
                  {t.node.name.length > 18 ? `${t.node.name.slice(0, 17)}…` : t.node.name}
                </text>
              ) : !lr && parents.has(t.node.id) ? (
                // branch nodes in landscape: label beside the tile, the trunk leaves downwards
                <>
                  <text x={32} y={-4} class="tlabel start">
                    {t.node.name.length > 14 ? `${t.node.name.slice(0, 13)}…` : t.node.name}
                  </text>
                  <text x={32} y={10} class="tsub start">
                    {t.node.status === "online" ? TYPE_LABEL[t.node.type].replace("Cloud ", "") : STATUS_LABEL[t.node.status]}
                  </text>
                </>
              ) : (
                <>
                  <text x={lr ? 36 : 0} y={lr ? -2 : 44} class={`tlabel ${lr ? "start" : ""}`}>
                    {t.node.name.length > 18 ? `${t.node.name.slice(0, 17)}…` : t.node.name}
                  </text>
                  <text x={lr ? 36 : 0} y={lr ? 14 : 58} class={`tsub ${lr ? "start" : ""}`}>
                    {t.node.status === "online" ? TYPE_LABEL[t.node.type].replace("Cloud ", "") : STATUS_LABEL[t.node.status]}
                  </text>
                </>
              )}
            </g>
          ))}
        </>
      ))}
    </PanZoom>
  );
}

// ----------------------------------------------------------------------- map
function MapView({ net, dim, selected, onSelect, ranges }: { net: NetworkInfo; dim: string; selected?: string; onSelect: (id?: string) => void; ranges: boolean }) {
  const nodes = net.nodes.filter((n) => n.dim === dim);
  // fit to the nodes; range circles may extend past the screen
  const bounds = useMemo(() => mapBounds(nodes, false), [net, dim]);
  const cache = useRef<Cache>();
  if (!nodes.some((n) => n.pos))
    return (
      <div class="net-empty">
        <p>Positions are only shown to devices that can control this mainframe.</p>
        <p class="muted small">The topology view works for every device.</p>
      </div>
    );
  const byId = new Map(net.nodes.map((n) => [n.id, n]));
  const routeLinks = useMemo(() => {
    const set = new Set<string>();
    for (const n of net.nodes) {
      const p = parentOf(net, n.id);
      if (p) set.add([p, n.id].sort().join("|"));
    }
    return set;
  }, [net]);
  return (
    <PanZoom bounds={bounds} fitKey={`map-${dim}`} minW={40} maxW={20000} class="netmap" label={`Network map, ${dimLabel(dim)}`} onBackgroundClick={() => onSelect(undefined)}>
      {(v, upp) => {
        const step = gridStep(v.w);
        const lines = [];
        for (let x = Math.ceil(v.x / step) * step; x <= v.x + v.w; x += step)
          lines.push(<line key={`x${x}`} x1={x} x2={x} y1={v.y} y2={v.y + v.h} class={x === 0 ? "axis" : "grid"} vector-effect="non-scaling-stroke" />);
        for (let y = Math.ceil(v.y / step) * step; y <= v.y + v.h; y += step)
          lines.push(<line key={`y${y}`} y1={y} y2={y} x1={v.x} x2={v.x + v.w} class={y === 0 ? "axis" : "grid"} vector-effect="non-scaling-stroke" />);
        const fs = 11 * upp;
        const labels = [];
        for (let x = Math.ceil(v.x / step) * step; x <= v.x + v.w; x += step)
          labels.push(
            <text key={`lx${x}`} x={x + 3 * upp} y={v.y + 12 * upp} class="coord" font-size={fs}>
              x {x}
            </text>,
          );
        for (let y = Math.ceil(v.y / step) * step; y <= v.y + v.h; y += step)
          labels.push(
            <text key={`ly${y}`} x={v.x + 4 * upp} y={y - 3 * upp} class="coord" font-size={fs}>
              z {y}
            </text>,
          );
        const r = 9 * upp;
        return (
          <>
            <g>{lines}</g>
            {ranges && (
              <g class="ranges">
                {nodes
                  .filter((n) => n.pos && isNodeType(n.type) && n.status === "online")
                  .map((n) => (
                    <g key={n.id}>
                      <circle cx={n.pos!.x} cy={n.pos!.z} r={n.range} class={`range r-${n.type}`} vector-effect="non-scaling-stroke" />
                      {n.deviceRange && n.deviceRange !== n.range && (
                        <circle cx={n.pos!.x} cy={n.pos!.z} r={n.deviceRange} class="range dev" vector-effect="non-scaling-stroke" />
                      )}
                    </g>
                  ))}
              </g>
            )}
            {cached(cache, [net, dim, selected, upp], () => (
              <>
            {net.links
              .filter((l) => byId.get(l.a)?.dim === dim && byId.get(l.b)?.dim === dim && l.kind !== "ender")
              .map((l) => {
                const a = byId.get(l.a)!;
                const b = byId.get(l.b)!;
                if (!a.pos || !b.pos) return null;
                const used = routeLinks.has([l.a, l.b].sort().join("|"));
                return (
                  <line
                    key={`${l.a}-${l.b}`}
                    x1={a.pos.x}
                    y1={a.pos.z}
                    x2={b.pos.x}
                    y2={b.pos.z}
                    class={`mlink ${signalClass(l)} k-${l.kind} ${used ? "used" : "spare"}`}
                    vector-effect="non-scaling-stroke"
                  />
                );
              })}
            {nodes.map((n) =>
              n.pos ? (
                <g
                  key={n.id}
                  class={`mnode st-${n.status} ${selected === n.id ? "sel" : ""} ${isNodeType(n.type) ? "is-node" : "is-device"} t-${n.type}`}
                  transform={`translate(${n.pos.x},${n.pos.z})`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelect(n.id);
                  }}
                  role="button"
                  tabIndex={0}
                  aria-label={`${n.name}, ${STATUS_LABEL[n.status]}`}
                  onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onSelect(n.id)}
                >
                  {isNodeType(n.type) ? (
                    <rect x={-r} y={-r} width={2 * r} height={2 * r} rx={2 * upp} class="mdot" />
                  ) : (
                    <circle r={r * 0.75} class="mdot" />
                  )}
                  {n.type === "ender_relay" && <circle r={r * 1.6} class="ender-ring" vector-effect="non-scaling-stroke" />}
                  {(isNodeType(n.type) || upp < 0.45 || selected === n.id || n.status !== "online") && (
                    <text y={r * 2.4} class="mlabel" font-size={fs}>
                      {n.name}
                    </text>
                  )}
                </g>
              ) : null,
            )}
              </>
            ))}
          </>
        );
      }}
    </PanZoom>
  );
}

// -------------------------------------------------------------- detail panel
type DetailTab = "info" | "route" | "power" | "card";
interface DetailProps {
  net: NetworkInfo;
  node: NetNode;
  conn: MainframeConn;
  onOpenCard?: (uplinkId: string) => void;
  onClose: () => void;
  tab: DetailTab;
  onTab: (t: DetailTab) => void;
  onFly?: () => void;
}
function Detail(props: DetailProps) {
  const { net, node, conn, onOpenCard, onClose } = props;
  const link = uplinkOf(net, node.id);
  const hops = hopsOf(net, node.id);
  const route = pathOf(net, node.id);
  const byId = new Map(net.nodes.map((n) => [n.id, n]));
  const uplink = node.uplinkId ? conn.uplinks.value.find((u) => u.uplinkId === node.uplinkId) : undefined;
  const behind = net.nodes.filter((n) => n.id !== node.id && pathOf(net, n.id)?.includes(node.id));
  const enderPeers = node.type === "ender_relay" ? net.links.filter((l) => l.kind === "ender" && (l.a === node.id || l.b === node.id)) : [];
  const [busy, setBusy] = useState(false);
  const unlink = async () => {
    if (!confirm(`Unlink ${node.name} from the network? It has to be linked again with a Link Card.`)) return;
    setBusy(true);
    try {
      await conn.unlinkNode(node.id);
      pushToast({ level: "info", title: "Network", text: `${node.name} was unlinked.` });
      onClose();
    } catch (e) {
      pushToast({ level: "warn", title: "Unlink failed", text: (e as Error).message });
    } finally {
      setBusy(false);
    }
  };
  const cards = uplink ? resolveLayout(conn.uplinks.value, conn.layout.value).flatMap((p) => p.cards).filter((c) => c.uplink.uplinkId === uplink.uplinkId) : [];
  const tabs: [DetailTab, string][] = [["info", "Info"], ["route", "Route"]];
  if (node.power || isRelay(node.type)) tabs.push(["power", "Power"]);
  if (cards.length) tabs.push(["card", "Card"]);
  const tab = tabs.some(([t]) => t === props.tab) ? props.tab : "info";
  return (
    <div class="ndetail">
      <div class="nd-head">
        <span class={`nd-icon st-${node.status}`}>
          <PixelIcon name={ICON[node.type]} size={28} />
        </span>
        <div class="nd-title">
          <div class="nd-name">{node.name}</div>
          <div class="muted small">{TYPE_LABEL[node.type]}</div>
        </div>
        <span class={`nd-status st-${node.status}`}>
          <span class="sdot-s" /> {STATUS_LABEL[node.status]}
        </span>
      </div>
      {STATUS_HINT[node.status] && <p class={`nd-hint st-${node.status}`}>{STATUS_HINT[node.status]}</p>}
      <div class="nd-tabs" role="tablist" aria-label="Details">
        {tabs.map(([t, label]) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={t === tab}
            class={`nd-tab ${t === tab ? "on" : ""}`}
            onClick={() => props.onTab(t)}
            onKeyDown={(e) => {
              const i = tabs.findIndex(([x]) => x === t);
              const n = e.key === "ArrowRight" ? tabs[(i + 1) % tabs.length] : e.key === "ArrowLeft" ? tabs[(i - 1 + tabs.length) % tabs.length] : null;
              if (!n) return;
              e.preventDefault();
              props.onTab(n[0]);
              ((e.currentTarget as HTMLElement).parentElement?.children[tabs.indexOf(n)] as HTMLElement | undefined)?.focus();
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "info" && (
        <dl class="facts nd-facts" role="tabpanel">
          <dt>Dimension</dt>
          <dd>{dimLabel(node.dim)}</dd>
          {node.pos && (
            <>
              <dt>Position</dt>
              <dd class="mono">
                {node.pos.x} {node.pos.y} {node.pos.z}
              </dd>
            </>
          )}
          {link && (
            <>
              <dt>{link.kind === "wired" ? "Connection" : "Signal"}</dt>
              <dd class="nd-signal">
                <Bars signal={link.signal} kind={link.kind} />
                {link.kind === "ender"
                  ? "Ender link"
                  : link.kind === "wired"
                    ? "Cable (wired) · 100 %"
                    : link.legacy
                      ? "out of range (legacy)"
                      : `Radio · ${Math.round(link.signal * 100)} %${link.dist ? ` · ${Math.round(link.dist)} blocks` : ""}`}
                {link.kind === "radio" && !link.legacy && (link.weak || link.signal < 0.15) && <span class="tag tag-weak">weak</span>}
              </dd>
            </>
          )}
          {isNodeType(node.type) && (
            <>
              <dt>Range</dt>
              <dd>
                {node.type === "ender_relay" ? "any distance to other Ender Relays · " : `${node.range} blocks to nodes · `}
                {node.deviceRange ?? node.range} to devices
              </dd>
            </>
          )}
          {!isNodeType(node.type) && (
            <>
              <dt>Range</dt>
              <dd>{node.range} blocks (radio) · cables have no limit</dd>
            </>
          )}
          {uplink && (
            <>
              <dt>Card</dt>
              <dd>
                {uplink.title || "Untitled"} · {uplink.widgets.length} widgets
              </dd>
            </>
          )}
          {node.lastSeen && node.status !== "online" && (
            <>
              <dt>Last seen</dt>
              <dd>{timeAgo(node.lastSeen)}</dd>
            </>
          )}
        </dl>
      )}
      {tab === "route" && (
        <dl class="facts nd-facts" role="tabpanel">
          <dt>Hops</dt>
          <dd>{node.type === "mainframe" ? "this is the mainframe" : hops !== undefined ? `${hops} hop${hops === 1 ? "" : "s"}` : "no route"}</dd>
          {route && node.type !== "mainframe" && (
            <>
              <dt>Path</dt>
              <dd>
                <ol class="nd-route">
                  {route.map((id, i) => {
                    const l = i > 0 ? findLink(net, route[i - 1], id) : undefined;
                    return (
                      <li key={id}>
                        {i > 0 && <span class={`nd-hop ${l ? signalClass(l) : ""} k-${l?.kind ?? "radio"}`}>{l?.kind === "wired" ? "cable" : l?.kind === "ender" ? "ender" : l ? `${Math.round(l.signal * 100)} %` : ""}</span>}
                        <span>{byId.get(id)?.name ?? id}</span>
                      </li>
                    );
                  })}
                </ol>
              </dd>
            </>
          )}
          {(isRelay(node.type) || node.type === "mainframe") && (
            <>
              <dt>Carries</dt>
              <dd>{behind.length ? `${behind.length}: ${behind.map((n) => n.name || "Device").join(", ")}` : "nothing"}</dd>
            </>
          )}
          {enderPeers.length > 0 && (
            <>
              <dt>Ender link</dt>
              <dd>
                {enderPeers
                  .map((l) => byId.get(l.a === node.id ? l.b : l.a))
                  .map((p) => `${p?.name} (${dimLabel(p?.dim ?? "")})`)
                  .join(", ")}
              </dd>
            </>
          )}
        </dl>
      )}
      {tab === "power" && (
        <dl class="facts nd-facts" role="tabpanel">
          {node.power ? (
            <>
              <dt>Usage</dt>
              <dd>{node.power.ok ? fmtFE(node.power.usage, true) : "no FE"}</dd>
              <dt>Buffer</dt>
              <dd>
                {fmtFE(node.power.stored)} / {fmtFE(node.power.capacity)}
                <span class="nd-bar" aria-hidden="true">
                  <span
                    ref={(el) => {
                      if (el) el.style.width = `${Math.round((node.power!.stored / Math.max(1, node.power!.capacity)) * 100)}%`;
                    }}
                  />
                </span>
              </dd>
            </>
          ) : (
            <>
              <dt>Power</dt>
              <dd>unknown</dd>
            </>
          )}
          <dt>Note</dt>
          <dd class="muted small">Relays need FE to forward traffic. Cabled devices cost no extra FE per hop.</dd>
        </dl>
      )}
      {tab === "card" && (
        <div class="nd-cards" role="tabpanel">
          {cards.map((c) => (
            <Card key={c.key} card={c} conn={conn} locked />
          ))}
        </div>
      )}
      <div class="nd-actions">
        {props.onFly && node.pos && (
          <button type="button" class="btn" onClick={props.onFly}>
            Fly to
          </button>
        )}
        {uplink && onOpenCard && (
          <button type="button" class="btn" onClick={() => onOpenCard(uplink.uplinkId)}>
            Open card
          </button>
        )}
        {conn.isAdmin && node.type !== "mainframe" && (
          <button type="button" class="btn btn-danger-ghost" disabled={busy} onClick={unlink}>
            Unlink
          </button>
        )}
        <DocsLink to="/docs/network" class="btn btn-ghost">
          Help
        </DocsLink>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------- page
type Mode = "topology" | "map" | "3d";
const MODE_KEY = "cccloud.netview";
const readMode = (): Mode => {
  try {
    const m = localStorage.getItem(MODE_KEY);
    return m === "map" || m === "3d" ? m : "topology";
  } catch {
    return "topology";
  }
};

export default function NetworkPage({ conn, onOpenCard }: { conn: MainframeConn; onOpenCard?: (uplinkId: string) => void }) {
  const net = conn.network.value;
  const isPhone = useIsPhone();
  const [mode, setModeState] = useState<Mode>(readMode);
  const setMode = (m: Mode) => {
    setModeState(m);
    try {
      localStorage.setItem(MODE_KEY, m);
    } catch {}
  };
  const [selected, setSelectedState] = useState<string | undefined>();
  const [tab, setTab] = useState<DetailTab>("info");
  const setSelected = (id?: string) => {
    if (id !== selected) setTab("info");
    setSelectedState(id);
  };
  const [ranges, setRanges] = useState(true);
  const [terrainOn, setTerrainOn] = useState(true);
  const [flyKey, setFlyKey] = useState(0);
  const webgl = useMemo(() => hasWebGL(), []);
  const [failed3d, setFailed3d] = useState<string | null>(null);
  const dims = net ? dimsOf(net) : [];
  const [dim, setDim] = useState<string>("minecraft:overworld");
  const activeDim = dims.includes(dim) ? dim : dims[0];
  const use3d = mode === "3d" && webgl && !failed3d;
  const terrain = useTerrain(conn, activeDim ?? "", use3d && terrainOn);
  if (!net)
    return (
      <div class="empty">
        <h2 class="px-title">No network data</h2>
        <p>This server does not send a network map yet (CC: Cloud v0.4 or newer is needed).</p>
      </div>
    );
  const node = selected ? net.nodes.find((n) => n.id === selected) : undefined;
  const counts = statusCounts(net);
  const used = new Set(net.nodes.map((n) => [parentOf(net, n.id), n.id].sort().join("|")));
  const weak = net.links.filter((l) => l.kind === "radio" && !l.legacy && (l.weak || l.signal < 0.15) && used.has([l.a, l.b].sort().join("|")));
  const cables = net.links.filter((l) => l.kind === "wired").length;
  const hasPos = net.nodes.some((n) => n.pos);
  const flyTo = (id: string) => {
    const n = net.nodes.find((x) => x.id === id);
    if (n && n.dim !== activeDim) setDim(n.dim);
    setSelected(id);
    setFlyKey((k) => k + 1);
  };
  const detail = node && (
    <Detail
      net={net}
      node={node}
      conn={conn}
      onOpenCard={onOpenCard}
      onClose={() => setSelected(undefined)}
      tab={tab}
      onTab={setTab}
      onFly={use3d ? () => flyTo(node.id) : undefined}
    />
  );
  return (
    <section class="netpage">
      <div class="net-toolbar">
        <div class="seg-ctl" role="radiogroup" aria-label="View">
          {(["topology", "map", "3d"] as const).map((m) => (
            <button key={m} type="button" role="radio" aria-checked={mode === m} class={mode === m ? "on" : ""} onClick={() => setMode(m)}>
              {m === "topology" ? "Topology" : m === "map" ? "Map" : "3D"}
            </button>
          ))}
        </div>
        <div class="net-chips">
          <span class="chip-s st-online">{counts.online ?? 0} online</span>
          {cables > 0 && <span class="chip-s chip-cable">{cables} cabled</span>}
          {(counts.no_route ?? 0) > 0 && <span class="chip-s st-no_route">{counts.no_route} no route</span>}
          {(counts.no_power ?? 0) > 0 && <span class="chip-s st-no_power">{counts.no_power} no power</span>}
          {(counts.unloaded ?? 0) > 0 && <span class="chip-s st-unloaded">{counts.unloaded} unloaded</span>}
          {(counts.legacy_out_of_range ?? 0) > 0 && <span class="chip-s st-legacy_out_of_range">{counts.legacy_out_of_range} legacy</span>}
          {weak.length > 0 && <span class="chip-s sig-weak-chip">{weak.length} weak link{weak.length > 1 ? "s" : ""}</span>}
        </div>
      </div>
      {mode !== "topology" && (
        <div class="net-toolbar sub">
          <div class="dim-tabs" role="tablist" aria-label="Dimension">
            {dims.map((d) => (
              <button key={d} type="button" role="tab" aria-selected={d === activeDim} class={`dim-tab ${d === activeDim ? "on" : ""}`} onClick={() => setDim(d)}>
                {dimLabel(d)}
              </button>
            ))}
          </div>
          <div class="net-opts">
            {use3d && hasPos && <FlySearch net={net} onPick={flyTo} />}
            <label class="check-row small">
              <input type="checkbox" checked={ranges} onChange={(e) => setRanges((e.target as HTMLInputElement).checked)} />
              {use3d ? "Ranges" : "Range circles"}
            </label>
            {use3d && conn.canControl.value && (
              <label class="check-row small">
                <input type="checkbox" checked={terrainOn} onChange={(e) => setTerrainOn((e.target as HTMLInputElement).checked)} />
                Terrain
              </label>
            )}
          </div>
        </div>
      )}
      {mode === "3d" && !use3d && (
        <div class="banner banner-readonly n3d-fallback" role="status">
          <div class="banner-text">
            {failed3d === "context_lost" ? "The 3D view stopped (the graphics driver reset)." : "3D needs WebGL, which this browser has turned off or does not support."} Showing the 2D map instead.
            {failed3d && (
              <>
                {" "}
                <button type="button" class="link-btn" onClick={() => setFailed3d(null)}>
                  Try again
                </button>
              </>
            )}
          </div>
        </div>
      )}
      <div class={`net-body ${node && !isPhone ? "with-panel" : ""}`}>
        <div class="net-canvas">
          {mode === "topology" ? (
            <Topology net={net} selected={selected} onSelect={setSelected} lr={isPhone} />
          ) : use3d && hasPos ? (
            <View3DLoader net={net} dim={activeDim} selected={selected} onSelect={setSelected} ranges={ranges} terrain={terrain} flyKey={flyKey} onFail={setFailed3d} />
          ) : (
            <MapView net={net} dim={activeDim} selected={selected} onSelect={setSelected} ranges={ranges} />
          )}
          <Legend />
        </div>
        {node && !isPhone && (
          <aside class="net-panel">
            <button type="button" class="icon-btn net-panel-close" aria-label="Close details" onClick={() => setSelected(undefined)}>
              ×
            </button>
            {detail}
          </aside>
        )}
      </div>
      {node && isPhone && (
        <Sheet title={node.name} onClose={() => setSelected(undefined)}>
          {detail}
        </Sheet>
      )}
    </section>
  );
}

export function Legend() {
  return (
    <div class="net-legend" aria-hidden="true">
      <span>
        <i class="lg sig-wired k-wired" /> cable
      </span>
      <span>
        <span class="lg-group">
          <i class="lg sig-good k-radio" />
          <i class="lg sig-ok k-radio" />
          <i class="lg sig-weak k-radio" />
        </span>{" "}
        radio strong · ok · weak
      </span>
      <span>
        <i class="lg sig-ender k-ender" /> ender
      </span>
      <span>
        <i class="lg sig-legacy" /> legacy
      </span>
    </div>
  );
}

export { findLink };
