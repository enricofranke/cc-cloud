// v0.4 Network page: UniFi-style topology and a top-down map per dimension.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";

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
              <g key={`${e.from}-${e.to}`} class={`edge ${cls} ${offline ? "dim" : ""}`}>
                <path d={d} vector-effect="non-scaling-stroke" />
                {e.link && upp < 1.4 && (
                  <text x={lr ? Math.max(a.x + 6, midX - 10) : b.x + 6} y={lr ? b.y - 6 : midY + (b.y - midY) / 2 + 4} class="edge-label">
                    {e.link.kind === "ender" ? "ender" : e.link.legacy ? "legacy" : `${Math.round(e.link.signal * 100)}%`}
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
              .filter((l) => byId.get(l.a)?.dim === dim && byId.get(l.b)?.dim === dim && l.kind === "radio")
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
                    class={`mlink ${signalClass(l)} ${used ? "used" : "spare"}`}
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
function Detail({ net, node, conn, onOpenCard, onClose }: { net: NetworkInfo; node: NetNode; conn: MainframeConn; onOpenCard?: (uplinkId: string) => void; onClose: () => void }) {
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
      <dl class="facts nd-facts">
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
            <dt>Signal</dt>
            <dd class="nd-signal">
              <Bars signal={link.signal} kind={link.kind} />
              {link.kind === "ender"
                ? "Ender link"
                : link.legacy
                  ? "out of range (legacy)"
                  : `${Math.round(link.signal * 100)} %${link.dist ? ` · ${Math.round(link.dist)} blocks` : ""}`}
              {link.kind === "radio" && !link.legacy && (link.weak || link.signal < 0.15) && <span class="tag tag-weak">weak</span>}
            </dd>
          </>
        )}
        {hops !== undefined && node.type !== "mainframe" && (
          <>
            <dt>Route</dt>
            <dd>
              {hops} hop{hops === 1 ? "" : "s"}: {route!.map((id) => byId.get(id)?.name ?? id).join(" → ")}
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
            <dd>{node.range} blocks</dd>
          </>
        )}
        {node.power && (
          <>
            <dt>Power</dt>
            <dd>
              {node.power.ok ? `${fmtFE(node.power.usage, true)} · ${fmtFE(node.power.stored)} / ${fmtFE(node.power.capacity)}` : "no FE"}
            </dd>
          </>
        )}
        {isRelay(node.type) && (
          <>
            <dt>Carries</dt>
            <dd>{behind.length ? behind.map((n) => n.name).join(", ") : "nothing"}</dd>
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
      <div class="nd-actions">
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
export default function NetworkPage({ conn, onOpenCard }: { conn: MainframeConn; onOpenCard?: (uplinkId: string) => void }) {
  const net = conn.network.value;
  const isPhone = useIsPhone();
  const [mode, setMode] = useState<"topology" | "map">("topology");
  const [selected, setSelected] = useState<string | undefined>();
  const [ranges, setRanges] = useState(true);
  const dims = net ? dimsOf(net) : [];
  const [dim, setDim] = useState<string>("minecraft:overworld");
  const activeDim = dims.includes(dim) ? dim : dims[0];
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
  return (
    <section class="netpage">
      <div class="net-toolbar">
        <div class="seg-ctl" role="radiogroup" aria-label="View">
          {(["topology", "map"] as const).map((m) => (
            <button key={m} type="button" role="radio" aria-checked={mode === m} class={mode === m ? "on" : ""} onClick={() => setMode(m)}>
              {m === "topology" ? "Topology" : "Map"}
            </button>
          ))}
        </div>
        <div class="net-chips">
          <span class="chip-s st-online">{counts.online ?? 0} online</span>
          {(counts.no_route ?? 0) > 0 && <span class="chip-s st-no_route">{counts.no_route} no route</span>}
          {(counts.no_power ?? 0) > 0 && <span class="chip-s st-no_power">{counts.no_power} no power</span>}
          {(counts.unloaded ?? 0) > 0 && <span class="chip-s st-unloaded">{counts.unloaded} unloaded</span>}
          {(counts.legacy_out_of_range ?? 0) > 0 && <span class="chip-s st-legacy_out_of_range">{counts.legacy_out_of_range} legacy</span>}
          {weak.length > 0 && <span class="chip-s sig-weak-chip">{weak.length} weak link{weak.length > 1 ? "s" : ""}</span>}
        </div>
      </div>
      {mode === "map" && (
        <div class="net-toolbar sub">
          <div class="dim-tabs" role="tablist" aria-label="Dimension">
            {dims.map((d) => (
              <button key={d} type="button" role="tab" aria-selected={d === activeDim} class={`dim-tab ${d === activeDim ? "on" : ""}`} onClick={() => setDim(d)}>
                {dimLabel(d)}
              </button>
            ))}
          </div>
          <label class="check-row small">
            <input type="checkbox" checked={ranges} onChange={(e) => setRanges((e.target as HTMLInputElement).checked)} />
            Range circles
          </label>
        </div>
      )}
      <div class={`net-body ${node && !isPhone ? "with-panel" : ""}`}>
        <div class="net-canvas">
          {mode === "topology" ? (
            <Topology net={net} selected={selected} onSelect={setSelected} lr={isPhone} />
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
            <Detail net={net} node={node} conn={conn} onOpenCard={onOpenCard} onClose={() => setSelected(undefined)} />
          </aside>
        )}
      </div>
      {node && isPhone && (
        <Sheet title={node.name} onClose={() => setSelected(undefined)}>
          <Detail net={net} node={node} conn={conn} onOpenCard={onOpenCard} onClose={() => setSelected(undefined)} />
        </Sheet>
      )}
    </section>
  );
}

function Legend() {
  return (
    <div class="net-legend" aria-hidden="true">
      <span>
        <i class="lg sig-good" /> strong
      </span>
      <span>
        <i class="lg sig-ok" /> ok
      </span>
      <span>
        <i class="lg sig-weak" /> weak
      </span>
      <span>
        <i class="lg sig-ender" /> ender
      </span>
      <span>
        <i class="lg sig-legacy" /> legacy
      </span>
    </div>
  );
}

export { findLink };
