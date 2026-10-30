// v0.4 Network page: UniFi-style topology and a top-down map per dimension.
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "preact/hooks";
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
import { rejectText, type MainframeConn } from "../lib/conn.ts";
import type { NetNode, NetworkInfo, NodeType } from "../lib/types.ts";
import { CapacityLine, fmtCap } from "../components/Capacity.tsx";
import { RedstonePanel } from "./Redstone.tsx";
import { feature } from "../lib/config.ts";
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
  linkLabel,
  mapBounds,
  overlaysOf,
  type MapOverlays,
  parentOf,
  pathOf,
  placeLabels,
  redstoneCardOf,
  signalBars,
  signalClass,
  statusCounts,
  uplinkOf,
} from "../lib/network.ts";
import { fmtFE } from "../lib/format.ts";
import { timeAgo, pushToast } from "../lib/ui.ts";
import { PanZoom, type View } from "./PanZoom.tsx";
import { TILE, TileStore, medianY, tileKey, tilePath, tilesFor, type Layer } from "../lib/maptiles.ts";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";
import { Card } from "../components/Card.tsx";
import { resolveLayout } from "../lib/layout.ts";
import { fetchTerrain, type Terrain } from "../lib/api.ts";
import { hasWebGL, searchNodes, voxelProgress, type VoxelStatus } from "../lib/net3d.ts";
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
    if (!on || !mf || !conn.can("view.positions")) return setT(null);
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
  probe: "probe",
  bridge: "bridge",
  device: "uplink",
  me_bridge: "bridge",
  rs_bridge: "bridge",
  cloud_switch: "lever",
  cloud_button: "button",
  cloud_lamp: "lamp",
  cloud_sensor: "sensor",
};

/** What a Cloud Probe reads (only shown with positions; the server leaves out a target it may not read now). */
function probeReads(node: NetNode): string {
  const t = node.target;
  if (!t || !node.pos) return "nothing right now – pick a machine with the Link Card (out of range, unloaded or claimed machines are not read)";
  const d = Math.round(Math.hypot(t.x - node.pos.x, t.y - node.pos.y, t.z - node.pos.z));
  return `the machine at ${t.x} ${t.y} ${t.z} · ${d} block${d === 1 ? "" : "s"} away`;
}

/** Cloud Redstone device: its name and group (= the card it sits on), editable with network.link. */
function RedstoneDeviceForm({ node, conn }: { node: NetNode; conn: MainframeConn }) {
  const [name, setName] = useState(node.name);
  const [group, setGroup] = useState(node.group ?? "");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    setName(node.name);
    setGroup(node.group ?? "");
  }, [node.id, node.name, node.group]);
  const groups = [...new Set(conn.uplinks.value.filter((u) => u.template === "redstone" && u.title && u.title !== "Redstone").map((u) => u.title.replace(/ \(\d+\)$/, "")))];
  const save = async (e: Event) => {
    e.preventDefault();
    setBusy(true);
    try {
      await conn.redstoneDevice(node.id, { name: name.trim(), group: group.trim() });
      pushToast({ level: "info", title: "Saved", text: group.trim() ? `${name.trim() || node.name} is on the card "${group.trim()}" now.` : `${name.trim() || node.name} is on the card "Redstone" now.` });
    } catch (err) {
      pushToast({ level: "warn", title: "Not saved", text: rejectText({ ok: false, error: (err as Error).message }) });
    } finally {
      setBusy(false);
    }
  };
  return (
    <form class="nd-rsform" onSubmit={save}>
      <label>
        <span>Name</span>
        <input class="input" type="text" maxLength={32} value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
      </label>
      <label>
        <span>Group</span>
        <input class="input" type="text" maxLength={32} list={`rsgroups-${node.id}`} placeholder="none (card Redstone)" value={group}
          onInput={(e) => setGroup((e.target as HTMLInputElement).value)} />
        <datalist id={`rsgroups-${node.id}`}>
          {groups.map((g) => (
            <option key={g} value={g} />
          ))}
        </datalist>
      </label>
      <p class="muted small">Devices with the same group share one card on the dashboard.</p>
      <button type="submit" class="btn" disabled={busy || (name.trim() === node.name && group.trim() === (node.group ?? ""))}>
        Save
      </button>
    </form>
  );
}

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
            // the trunk is shared by all children of a parent: drawn neutral, the drop to the child shows the link type
            const trunk = lr ? `M${a.x + 26},${a.y} H${a.x + 40} V${b.y}` : `M${a.x},${a.y + 26} V${midY} H${b.x}`;
            const drop = lr ? `M${a.x + 40},${b.y} H${b.x - 26}` : `M${b.x},${midY} V${b.y - 26}`;
            return (
              <g key={`${e.from}-${e.to}`} class={`edge ${cls} k-${e.link?.kind ?? "radio"} ${offline ? "dim" : ""}`}>
                <path d={trunk} class="trunk" vector-effect="non-scaling-stroke" />
                <path d={drop} vector-effect="non-scaling-stroke" />
                {e.link && upp < 1.4 && (
                  <text x={lr ? Math.max(a.x + 6, midX - 10) : b.x + 6} y={lr ? b.y - 6 : midY + (b.y - midY) / 2 + 4} class="edge-label">
                    {linkLabel(e.link)}
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
export interface MapTiles {
  store: TileStore;
  mf: string;
  layer: Layer;
  y: number;
}

/** Map tiles drawn on a canvas behind the SVG (same view; crisp pixels when zoomed in). */
function TileCanvas({ tiles, dim, view, upp, size, nodes }: { tiles: MapTiles; dim: string; view: View; upp: number; size: { w: number; h: number }; nodes: { x: number; z: number }[] }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const [, setTick] = useState(0);
  useEffect(() => {
    tiles.store.onLoad = () => setTick((t) => t + 1);
    return () => {
      tiles.store.onLoad = () => {};
    };
  }, [tiles.store]);
  const want = tilesFor(view, nodes, 80);
  useEffect(() => {
    tiles.store.want(want.map((t) => ({ key: tileKey(dim, tiles.layer, tiles.y, t), path: tilePath(tiles.mf, dim, tiles.layer, tiles.y, t) })));
  });
  useLayoutEffect(() => {
    const c = ref.current;
    if (!c) return;
    const dpr = Math.min(2, devicePixelRatio || 1);
    if (c.width !== Math.round(size.w * dpr)) c.width = Math.round(size.w * dpr);
    if (c.height !== Math.round(size.h * dpr)) c.height = Math.round(size.h * dpr);
    const g = c.getContext("2d");
    if (!g) return;
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.clearRect(0, 0, c.width, c.height);
    const k = dpr / upp;
    g.setTransform(k, 0, 0, k, -view.x * k, -view.y * k);
    // pixel art when zoomed in, smooth when zoomed far out
    g.imageSmoothingEnabled = upp > 1.5;
    for (const t of want) {
      const e = tiles.store.get(tileKey(dim, tiles.layer, tiles.y, t));
      // a block is drawn centred on its coordinate (nodes sit at x, z)
      if (e && typeof e === "object") g.drawImage(e, t.tx * TILE - 0.5, t.tz * TILE - 0.5, TILE, TILE);
    }
  });
  return <canvas ref={ref} class="map-tiles" aria-hidden="true" />;
}

const hex = (c: number) => `#${c.toString(16).padStart(6, "0")}`;

function MapView({ net, dim, selected, onSelect, ranges, tiles, overlays }: { net: NetworkInfo; dim: string; selected?: string; onSelect: (id?: string) => void; ranges: boolean; tiles?: MapTiles | null; overlays?: MapOverlays }) {
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
    <PanZoom
      bounds={bounds}
      fitKey={`map-${dim}`}
      minW={40}
      maxW={20000}
      class={`netmap ${tiles ? "has-tiles" : ""}`}
      label={`Network map, ${dimLabel(dim)}`}
      onBackgroundClick={() => onSelect(undefined)}
      background={tiles ? (v, u, size) => <TileCanvas tiles={tiles} dim={dim} view={v} upp={u} size={size} nodes={nodes.filter((n) => n.pos).map((n) => ({ x: n.pos!.x, z: n.pos!.z }))} /> : undefined}
    >
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
        const claims = (overlays?.claims ?? []).flatMap((c) => c.chunks.filter((k) => k.dim === dim).map((k) => ({ ...k, color: c.color, team: c.team })));
        const stones = (overlays?.waystones ?? []).filter((w) => w.dim === dim);
        return (
          <>
            <g>{lines}</g>
            {claims.length > 0 && (
              <g class="ov-claims">
                {claims.map((k) => (
                  <rect key={`${k.x},${k.z}`} x={k.x * 16} y={k.z * 16} width={16} height={16} class={`claim ${k.forced ? "forced" : ""}`} style={{ "--cc": hex(k.color) }}>
                    <title>{`${k.team || "Team"} claim · chunk ${k.x}, ${k.z}${k.forced ? " · force-loaded" : ""}`}</title>
                  </rect>
                ))}
              </g>
            )}
            {stones.length > 0 && (
              <g class="ov-waystones">
                {stones.map((w) => (
                  <g key={`${w.name}@${w.x},${w.z}`} class={`waystone ${w.global ? "global" : ""}`} transform={`translate(${w.x},${w.z})`}>
                    <path d={`M0,${-7 * upp} L${5 * upp},0 L0,${7 * upp} L${-5 * upp},0 Z`} class="ws-mark" />
                    <title>{`${w.name} (${w.kind}${w.global ? ", global" : ""}) · ${Math.round(w.x)} ${Math.round(w.y)} ${Math.round(w.z)}`}</title>
                  </g>
                ))}
              </g>
            )}
            {ranges && (
              <g class={`ranges ${selected && nodes.some((n) => n.id === selected && isNodeType(n.type)) ? "has-sel" : ""}`}>
                {nodes
                  .filter((n) => n.pos && isNodeType(n.type) && n.status === "online")
                  .map((n) => {
                    const dev = n.deviceRange ?? n.range;
                    const same = dev === n.range;
                    const sel = n.id === selected;
                    // labels sit on the circle edge, up-right (45°)
                    const at = (r: number) => ({ x: n.pos!.x + r * 0.7071, y: n.pos!.z - r * 0.7071 });
                    return (
                      <g key={n.id} class={sel ? "sel" : ""}>
                        <circle cx={n.pos!.x} cy={n.pos!.z} r={n.range} class={`range r-${n.type}`} vector-effect="non-scaling-stroke" />
                        {!same && n.type !== "ender_relay" && <circle cx={n.pos!.x} cy={n.pos!.z} r={dev} class="range dev" vector-effect="non-scaling-stroke" />}
                        {/* wide invisible rings for the hover tooltips */}
                        <circle cx={n.pos!.x} cy={n.pos!.z} r={n.range} class="range-hit" vector-effect="non-scaling-stroke">
                          <title>{same ? `${n.name}: node and device range ${n.range} blocks` : `${n.name}: node range ${n.range} blocks – reach to other relays and the mainframe`}</title>
                        </circle>
                        {!same && n.type !== "ender_relay" && (
                          <circle cx={n.pos!.x} cy={n.pos!.z} r={dev} class="range-hit" vector-effect="non-scaling-stroke">
                            <title>{`${n.name}: device range ${dev} blocks – reach for uplinks and bridges`}</title>
                          </circle>
                        )}
                        {sel && (
                          <>
                            <text {...at(n.range)} dx={4 * upp} class="range-label" font-size={11 * upp}>
                              {same ? `node & device range ${n.range}` : `node range ${n.range}`}
                            </text>
                            {!same && n.type !== "ender_relay" && (
                              <text {...at(dev)} dx={4 * upp} class="range-label dev" font-size={11 * upp}>
                                device range {dev}
                              </text>
                            )}
                          </>
                        )}
                      </g>
                    );
                  })}
              </g>
            )}
            {cached(cache, [net, dim, selected, upp, stones], () => {
              // labels: important ones first, never overlapping (the rest show when zoomed in); waystones last
              const labels = placeLabels([
                ...nodes
                  .filter((n) => n.pos)
                  .map((n) => ({
                    id: n.id,
                    x: n.pos!.x / upp,
                    y: n.pos!.z / upp,
                    text: n.name || "Device",
                    r: 9,
                    priority: n.id === selected ? 100 : n.type === "mainframe" ? 50 : isNodeType(n.type) ? 40 : n.status !== "online" ? 30 : 10,
                  })),
                ...stones.map((w, i) => ({ id: `ws:${i}`, x: w.x / upp, y: w.z / upp, text: w.name, r: 7, priority: 5 })),
              ]);
              return (
              <>
            {stones.map((w, i) => {
              const l = labels.get(`ws:${i}`);
              return l ? (
                <text key={`wsl${i}`} x={w.x + l.x * upp} y={w.z + l.y * upp} class={`ws-label mlabel a-${l.anchor}`} font-size={fs}>
                  {w.name}
                </text>
              ) : null;
            })}
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
            {nodes
              .filter((n) => n.type === "probe" && n.pos && n.target)
              .map((n) => (
                <g key={`aim-${n.id}`} class={`probe-aim ${selected === n.id ? "sel" : ""}`}>
                  <line x1={n.pos!.x} y1={n.pos!.z} x2={n.target!.x} y2={n.target!.z} class="probe-line" vector-effect="non-scaling-stroke" />
                  <rect x={n.target!.x - 3 * upp} y={n.target!.z - 3 * upp} width={6 * upp} height={6 * upp} class="probe-target" vector-effect="non-scaling-stroke">
                    <title>{`${n.name} reads the machine at ${n.target!.x} ${n.target!.y} ${n.target!.z}`}</title>
                  </rect>
                </g>
              ))}
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
                  {labels.get(n.id) && (
                    <text x={labels.get(n.id)!.x * upp} y={labels.get(n.id)!.y * upp} class={`mlabel a-${labels.get(n.id)!.anchor}`} font-size={fs}>
                      {n.name}
                    </text>
                  )}
                </g>
              ) : null,
            )}
              </>
              );
            })}
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
  const uplink = node.redstone
    ? redstoneCardOf(conn.uplinks.value, node.id)
    : node.uplinkId
      ? conn.uplinks.value.find((u) => u.uplinkId === node.uplinkId)
      : undefined;
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
                    ? link.medium === "fiber"
                      ? "Fiber cable · 100 % · half capacity and FE"
                      : "CC networking cable · 100 %"
                    : link.legacy
                      ? "out of range (legacy)"
                      : `Radio · ${Math.round(link.signal * 100)} %${link.dist ? ` · ${Math.round(link.dist)} blocks` : ""}`}
                {link.kind === "radio" && !link.legacy && (link.weak || link.signal < 0.15) && <span class="tag tag-weak">weak</span>}
              </dd>
            </>
          )}
          {node.via && !link && (
            <>
              <dt>Connection</dt>
              <dd>{node.via === "fiber" ? "Fiber cable" : "CC networking cable"}</dd>
            </>
          )}
          {node.capacity && (
            <>
              <dt>Devices</dt>
              <dd>
                <CapacityLine c={node.capacity} />
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
          {node.type === "probe" && node.pos && (
            <>
              <dt>Reads</dt>
              <dd>{probeReads(node)}</dd>
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
          {node.redstone && conn.can("network.link") && (
            <>
              <dt>Dashboard</dt>
              <dd>
                <RedstoneDeviceForm node={node} conn={conn} />
              </dd>
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
                        {i > 0 && <span class={`nd-hop ${l ? signalClass(l) : ""} k-${l?.kind ?? "radio"}`}>{l ? linkLabel(l).replace(/%$/, " %") : ""}</span>}
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
        {conn.can("network.link") && node.type !== "mainframe" && (
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
  const [rsOpen, setRsOpen] = useState(false);
  const [showClaims, setShowClaims] = useState(true);
  const [showStones, setShowStones] = useState(true);
  const allOverlays = useMemo(() => overlaysOf(conn.uplinks.value), [conn.uplinks.value]);
  const overlays: MapOverlays = { claims: showClaims ? allOverlays.claims : [], waystones: showStones ? allOverlays.waystones : [] };
  const [terrainOn, setTerrainOn] = useState(true);
  const [flyKey, setFlyKey] = useState(0);
  const webgl = useMemo(() => hasWebGL(), []);
  const [failed3d, setFailed3d] = useState<string | null>(null);
  const dims = net ? dimsOf(net) : [];
  const [dim, setDim] = useState<string>("minecraft:overworld");
  const activeDim = dims.includes(dim) ? dim : dims[0];
  const use3d = mode === "3d" && webgl && !failed3d;
  // map tiles (operator+: positions are sensitive); one store per mainframe connection
  const store = useMemo(() => new TileStore(conn.baseUrl, conn.token), [conn]);
  useEffect(() => () => store.dispose(), [store]);
  const [layer, setLayer] = useState<Layer | "off">("surface");
  const dimYs = (net?.nodes ?? []).filter((n) => n.dim === (dims.includes(dim) ? dim : dims[0]) && n.pos).map((n) => n.pos!.y);
  const [sliceY, setSliceY] = useState<number | null>(null);
  const [sliceDraft, setSliceDraft] = useState<number | null>(null);
  const yNow = sliceY ?? medianY(dimYs);
  useEffect(() => {
    if (sliceDraft === null) return;
    const t = setTimeout(() => setSliceY(sliceDraft), 280);
    return () => clearTimeout(t);
  }, [sliceDraft]);
  const mfId = conn.info.value?.id ?? "";
  const tiles: MapTiles | null = layer !== "off" && conn.can("view.positions") && feature("map") && !store.unavailable && mfId ? { store, mf: mfId, layer, y: yNow } : null;
  // voxel blocks (WebGL2, operator+); day/night and a cut height
  const webgl2 = useMemo(() => {
    try {
      return !!document.createElement("canvas").getContext("webgl2");
    } catch {
      return false;
    }
  }, []);
  const [blocks, setBlocks] = useState(true);
  const [night, setNight] = useState(false);
  const [clipOn, setClipOn] = useState(false);
  const [clipY, setClipY] = useState<number | null>(null);
  const [voxStatus, setVoxStatus] = useState<VoxelStatus | null>(null);
  const voxelsOn = use3d && blocks && webgl2 && conn.can("view.positions") && feature("voxels") && !voxStatus?.unavailable;
  const terrain = useTerrain(conn, activeDim ?? "", use3d && terrainOn && !voxelsOn);
  if (!net)
    return (
      <div class="empty">
        <h2 class="px-title">No network data</h2>
        <p>This server does not send a network map yet (CC: Cloud v0.4 or newer is needed).</p>
      </div>
    );
  const node = selected ? net.nodes.find((n) => n.id === selected) : undefined;
  const dimNodes = net.nodes.filter((n) => n.dim === activeDim && n.pos);
  const mfNode = dimNodes.find((n) => n.type === "mainframe") ?? dimNodes[0];
  const voxelSrc =
    voxelsOn && mfNode
      ? {
          base: conn.baseUrl,
          token: conn.token,
          mf: mfId,
          dim: activeDim,
          nodes: dimNodes.map((n) => ({ x: n.pos!.x, y: n.pos!.y, z: n.pos!.z })),
          focus: { ...mfNode.pos! },
          radius: 40,
          budget: isPhone ? 150_000 : 450_000,
        }
      : null;
  const defaultClip = (mfNode?.pos?.y ?? 64) + 5;
  const counts = statusCounts(net);
  const used = new Set(net.nodes.map((n) => [parentOf(net, n.id), n.id].sort().join("|")));
  const weak = net.links.filter((l) => l.kind === "radio" && !l.legacy && (l.weak || l.signal < 0.15) && used.has([l.a, l.b].sort().join("|")));
  const cables = net.links.filter((l) => l.kind === "wired" && l.medium !== "fiber").length;
  const fibers = net.links.filter((l) => l.kind === "wired" && l.medium === "fiber").length;
  const mfCap = net.nodes.find((n) => n.type === "mainframe")?.capacity;
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
          {cables > 0 && <span class="chip-s chip-cable">{cables} cable{cables > 1 ? "s" : ""}</span>}
          {fibers > 0 && <span class="chip-s chip-fiber">{fibers} fiber</span>}
          {(counts.over_capacity ?? 0) > 0 && <span class="chip-s st-over_capacity">{counts.over_capacity} over capacity</span>}
          {mfCap && mfCap.limit != null && (
            <span class={`chip-s chip-cap ${mfCap.used > mfCap.limit ? "over" : mfCap.used >= mfCap.limit * 0.9 ? "near" : ""}`} title="Device capacity (upgrade cards)">
              {fmtCap(mfCap.used)} / {mfCap.limit} devices
            </span>
          )}
          {(counts.no_route ?? 0) > 0 && <span class="chip-s st-no_route">{counts.no_route} no route</span>}
          {(counts.no_power ?? 0) > 0 && <span class="chip-s st-no_power">{counts.no_power} no power</span>}
          {(counts.unloaded ?? 0) > 0 && <span class="chip-s st-unloaded">{counts.unloaded} unloaded</span>}
          {(counts.legacy_out_of_range ?? 0) > 0 && <span class="chip-s st-legacy_out_of_range">{counts.legacy_out_of_range} legacy</span>}
          {weak.length > 0 && <span class="chip-s sig-weak-chip">{weak.length} weak link{weak.length > 1 ? "s" : ""}</span>}
          {conn.redstone.value && (
            <button type="button" class="chip-s chip-wr" onClick={() => setRsOpen(true)} aria-haspopup="dialog">
              <span class="wr-dot" aria-hidden="true" /> Redstone
              {conn.redstone.value.channels.length > 0 ? ` · ${conn.redstone.value.channels.length}` : ""}
            </button>
          )}
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
            {!use3d && conn.can("view.positions") && feature("map") && !store.unavailable && (
              <div class="map-layer">
                <div class="seg-ctl seg-s" role="radiogroup" aria-label="Map background">
                  {(["surface", "slice", "off"] as const).map((l) => (
                    <button key={l} type="button" role="radio" aria-checked={layer === l} class={layer === l ? "on" : ""} onClick={() => setLayer(l)}>
                      {l === "surface" ? "Surface" : l === "slice" ? "Slice" : "No map"}
                    </button>
                  ))}
                </div>
                {layer === "slice" && (
                  <label class="slice-y small">
                    <span>Y {sliceDraft ?? yNow}</span>
                    <input
                      type="range"
                      min={-64}
                      max={320}
                      step={1}
                      value={sliceDraft ?? yNow}
                      aria-label="Slice height (Y)"
                      onInput={(e) => setSliceDraft(Number((e.target as HTMLInputElement).value))}
                    />
                  </label>
                )}
              </div>
            )}
            <label class="check-row small">
              <input type="checkbox" checked={ranges} onChange={(e) => setRanges((e.target as HTMLInputElement).checked)} />
              {use3d ? "Ranges" : "Range circles"}
            </label>
            {allOverlays.claims.length > 0 && !use3d && (
              <label class="check-row small">
                <input type="checkbox" checked={showClaims} onChange={(e) => setShowClaims((e.target as HTMLInputElement).checked)} />
                Claims
              </label>
            )}
            {allOverlays.waystones.length > 0 && (
              <label class="check-row small">
                <input type="checkbox" checked={showStones} onChange={(e) => setShowStones((e.target as HTMLInputElement).checked)} />
                Waystones
              </label>
            )}
            {use3d && conn.can("view.positions") && feature("voxels") && webgl2 && !voxStatus?.unavailable && (
              <label class="check-row small">
                <input type="checkbox" checked={blocks} onChange={(e) => setBlocks((e.target as HTMLInputElement).checked)} />
                Blocks
              </label>
            )}
            {use3d && conn.can("view.positions") && !voxelsOn && (
              <label class="check-row small">
                <input type="checkbox" checked={terrainOn} onChange={(e) => setTerrainOn((e.target as HTMLInputElement).checked)} />
                Terrain
              </label>
            )}
            {voxelsOn && (
              <>
                <label class="check-row small">
                  <input type="checkbox" checked={night} onChange={(e) => setNight((e.target as HTMLInputElement).checked)} />
                  Night
                </label>
                <label class="check-row small">
                  <input type="checkbox" checked={clipOn} onChange={(e) => setClipOn((e.target as HTMLInputElement).checked)} />
                  Cut
                </label>
                {clipOn && (
                  <label class="slice-y small">
                    <span>Y {clipY ?? defaultClip}</span>
                    <input type="range" min={-64} max={320} value={clipY ?? defaultClip} aria-label="Cut everything above this height" onInput={(e) => setClipY(Number((e.target as HTMLInputElement).value))} />
                  </label>
                )}
                {voxStatus && voxelProgress(voxStatus) && (
                  <span class="muted small vox-progress" role="status">
                    {voxelProgress(voxStatus)}
                  </span>
                )}
              </>
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
            <View3DLoader
              net={net}
              dim={activeDim}
              selected={selected}
              onSelect={setSelected}
              ranges={ranges}
              terrain={terrain}
              flyKey={flyKey}
              onFail={setFailed3d}
              voxels={voxelSrc}
              clipY={clipOn ? (clipY ?? defaultClip) : null}
              night={night}
              onVoxelStatus={setVoxStatus}
              waystones={overlays.waystones}
            />
          ) : (
            <MapView net={net} dim={activeDim} selected={selected} onSelect={setSelected} ranges={ranges} tiles={tiles} overlays={overlays} />
          )}
          <Legend ranges={mode === "map" && !use3d && ranges} claims={mode !== "topology" && !use3d && overlays.claims.length > 0} waystones={mode !== "topology" && overlays.waystones.length > 0} />
          {mode === "map" && !use3d && <MapHelp />}
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
      {rsOpen && (
        <Sheet title="Wireless redstone" onClose={() => setRsOpen(false)} wide>
          <RedstonePanel conn={conn} />
        </Sheet>
      )}
    </section>
  );
}

function MapHelp() {
  const [open, setOpen] = useState(false);
  return (
    <div class="map-help">
      <button type="button" class="pz-btn map-help-btn" aria-expanded={open} aria-controls="map-help" aria-label="How to read the range circles" onClick={() => setOpen(!open)}>
        ?
      </button>
      {open && (
        <div class="map-help-pop" id="map-help" role="note">
          <b>Planning relays</b>
          <p>
            <span class="lg lg-range" /> The <b>solid circle</b> is a relay's or the mainframe's <b>node range</b>: how far it reaches other relays and the mainframe.
          </p>
          <p>
            <span class="lg lg-range dev" /> The <b>dashed circle</b> is its <b>device range</b>: uplinks and bridges must be inside it (or reach it with their own range).
          </p>
          <p>Place each new relay inside the solid circle of a relay that is already connected, then put devices inside the dashed circles. Cables (wired modems) have no range limit. Click a relay to see its two circles labelled.</p>
          <DocsLink to="/docs/network#ranges">More in the docs</DocsLink>
        </div>
      )}
    </div>
  );
}

export function Legend({ ranges, claims, waystones }: { ranges?: boolean; claims?: boolean; waystones?: boolean }) {
  return (
    <div class="net-legend" aria-hidden="true">
      {ranges && (
        <>
          <span class="lg-range-row">
            <i class="lg lg-range" /> solid circle = node range (reach to other relays / mainframe)
          </span>
          <span class="lg-range-row">
            <i class="lg lg-range dev" /> dashed = device range (reach for uplinks & bridges)
          </span>
        </>
      )}
      <span>
        <i class="lg sig-wired k-wired" /> CC cable
      </span>
      <span>
        <i class="lg sig-fiber k-wired" /> fiber
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
      {claims && (
        <span>
          <i class="lg-claim" /> team claim
        </span>
      )}
      {waystones && (
        <span>
          <i class="lg-ws" /> waystone
        </span>
      )}
    </div>
  );
}

export { findLink };

