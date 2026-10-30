// v0.7: 3D view of the network (lazy chunk with three.js, bundled – no CDN).
// Devices use their real block models (converted at build time by
// dev/build-models.ts), links are lines (cable solid, radio dashed, ender
// dotted), ranges translucent spheres, optional coarse terrain from the server.
//
// Performance budget (mid-range phones, integrated GPUs): one InstancedMesh per
// model part + texture (≤ ~50 draw calls for any network size), one draw call
// each for markers, cable/radio/ender links, range spheres and rings, one per
// terrain patch; rendering only on demand (camera moves, data changes), pixel
// ratio capped at 2 (1.5 on small screens), no shadows, no lights (baked shading).
import { useEffect, useRef } from "preact/hooks";
import {
  BufferAttribute,
  BufferGeometry,
  CanvasTexture,
  Color,
  DoubleSide,
  EdgesGeometry,
  BoxGeometry,
  FogExp2,
  Float32BufferAttribute,
  GridHelper,
  Group,
  InstancedMesh,
  LineBasicMaterial,
  LineDashedMaterial,
  LineSegments,
  Matrix4,
  Mesh,
  MeshBasicMaterial,
  NearestFilter,
  PerspectiveCamera,
  Points,
  PointsMaterial,
  SRGBColorSpace,
  Scene,
  SphereGeometry,
  Texture,
  Vector3,
  WebGLRenderer,
  type Material,
  type Object3D,
} from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import MODELS_RAW from "./models.gen.json";
import type { NetNode, NetworkInfo, Waystone } from "../lib/types.ts";
import type { Terrain } from "../lib/api.ts";
import { isNodeType } from "../lib/network.ts";
import { VoxelLayer, type VoxelSource } from "./voxelLayer.ts";
import { LINK_RGB, MODEL_OF, STATUS_RGB, frameNodes, instanceGroups, linkSegments, pickNearest, routedThrough, terrainMesh, type ModelDef, type VoxelStatus } from "../lib/net3d.ts";

interface Part {
  tex: string;
  emit: boolean;
  trans: boolean;
  pos: number[];
  uv: number[];
  shade: number[];
  idx: number[];
}
const MODELS = MODELS_RAW as unknown as { textures: Record<string, string>; models: Record<string, ModelDef & { parts: Part[] }> };

export interface View3DProps {
  net: NetworkInfo;
  dim: string;
  selected?: string;
  onSelect: (id?: string) => void;
  ranges: boolean;
  terrain: Terrain | null;
  /** changes whenever "fly to" is requested for `selected` */
  flyKey: number;
  /** WebGL failed at runtime (context lost, creation error) */
  onFail: (why: string) => void;
  /** voxel blocks around the network (null = off / not allowed) */
  voxels: VoxelSource | null;
  /** cut away everything above this Y (voxels only) */
  clipY: number | null;
  night: boolean;
  onVoxelStatus?: (s: VoxelStatus) => void;
  /** Waystones overlay (this dimension is picked here) */
  waystones?: Waystone[];
}

// ------------------------------------------------------------- shared assets
const geoCache = new Map<string, BufferGeometry>();
function partGeometry(model: string, pi: number) {
  const key = `${model}|${pi}`;
  let g = geoCache.get(key);
  if (g) return g;
  const p = MODELS.models[model].parts[pi];
  g = new BufferGeometry();
  const pos = new Float32Array(p.pos.length);
  for (let i = 0; i < p.pos.length; i += 3) {
    pos[i] = p.pos[i] / 16 - 0.5;
    pos[i + 1] = p.pos[i + 1] / 16;
    pos[i + 2] = p.pos[i + 2] / 16 - 0.5;
  }
  const uv = new Float32Array(p.uv.length);
  for (let i = 0; i < p.uv.length; i += 2) {
    uv[i] = p.uv[i];
    uv[i + 1] = 1 - p.uv[i + 1];
  }
  const col = new Float32Array((p.pos.length / 3) * 3);
  for (let q = 0; q < p.shade.length; q++) for (let v = 0; v < 4; v++) col.fill(p.shade[q], (q * 4 + v) * 3, (q * 4 + v) * 3 + 3);
  g.setAttribute("position", new BufferAttribute(pos, 3));
  g.setAttribute("uv", new BufferAttribute(uv, 2));
  g.setAttribute("color", new BufferAttribute(col, 3));
  g.setIndex(p.idx);
  g.computeBoundingSphere();
  geoCache.set(key, g);
  return g;
}

let texCache: Map<string, Texture> | null = null;
function texture(id: string, onLoad: () => void) {
  texCache ??= new Map();
  let t = texCache.get(id);
  if (t) return t;
  const img = new Image();
  t = new Texture(img);
  t.magFilter = NearestFilter;
  t.minFilter = NearestFilter;
  t.generateMipmaps = false;
  t.colorSpace = SRGBColorSpace;
  img.onload = () => {
    t!.needsUpdate = true;
    onLoad();
  };
  img.src = MODELS.textures[id] ?? "";
  texCache.set(id, t);
  return t;
}

const matCache = new Map<string, MeshBasicMaterial>();
function partMaterial(tex: string, part: Part, onLoad: () => void) {
  const key = `${tex}|${part.emit}|${part.trans}`;
  let m = matCache.get(key);
  if (m) return m;
  m = new MeshBasicMaterial({
    map: texture(tex, onLoad),
    vertexColors: true,
    transparent: part.trans,
    alphaTest: part.trans ? 0 : 0.5,
    depthWrite: !part.trans,
    side: DoubleSide,
  });
  matCache.set(key, m);
  return m;
}

function dotTexture() {
  const c = document.createElement("canvas");
  c.width = c.height = 32;
  const g = c.getContext("2d")!;
  g.fillStyle = "#0a0c0f";
  g.beginPath();
  g.arc(16, 16, 15, 0, Math.PI * 2);
  g.fill();
  g.fillStyle = "#fff";
  g.beginPath();
  g.arc(16, 16, 11, 0, Math.PI * 2);
  g.fill();
  const t = new CanvasTexture(c);
  t.colorSpace = SRGBColorSpace;
  return t;
}

const cssVar = (el: Element, name: string, fallback: string) => getComputedStyle(el).getPropertyValue(name).trim() || fallback;

// -------------------------------------------------------------------- view
class View3D {
  renderer: WebGLRenderer;
  scene = new Scene();
  camera = new PerspectiveCamera(50, 1, 0.3, 8000);
  controls: OrbitControls;
  content = new Group();
  terrainGroup = new Group();
  meshes: InstancedMesh[] = [];
  /** instance i of node n: [mesh, instance index] lists per node index */
  nodeIdx = new Map<number, [InstancedMesh, number][]>();
  nodes: NetNode[] = [];
  stones: Waystone[] = [];
  markers: Points | null = null;
  selBox: LineSegments;
  labels: HTMLDivElement;
  labelEls = new Map<string, HTMLDivElement>();
  dashed: LineDashedMaterial[] = [];
  scale = 1;
  pending = 0;
  anim = 0;
  ro: ResizeObserver;
  props: View3DProps;
  fittedFor = "";
  down: { x: number; y: number; t: number } | null = null;
  dot = dotTexture();
  owned: (BufferGeometry | Material)[] = [];
  voxels: VoxelLayer | null = null;
  statusTimer = 0;

  host: HTMLDivElement;

  constructor(host: HTMLDivElement, props: View3DProps) {
    this.host = host;
    this.props = props;
    const small = matchMedia("(max-width: 719px)").matches;
    this.renderer = new WebGLRenderer({ antialias: !small || devicePixelRatio < 2, powerPreference: "default", alpha: false });
    this.renderer.setPixelRatio(Math.min(devicePixelRatio || 1, small ? 1.5 : 2));
    this.renderer.localClippingEnabled = true;
    this.renderer.domElement.className = "n3d-canvas";
    this.renderer.domElement.tabIndex = 0;
    this.renderer.domElement.setAttribute("aria-label", "3D network view. Drag to rotate, two fingers or right mouse button to pan, scroll or pinch to zoom, arrow keys pan.");
    this.renderer.domElement.setAttribute("role", "img");
    host.prepend(this.renderer.domElement);
    this.labels = document.createElement("div");
    this.labels.className = "n3d-labels";
    host.appendChild(this.labels);

    this.scene.background = new Color(cssVar(host, "--screen", "#0a0c0f"));
    this.scene.add(this.terrainGroup, this.content);
    const box = new EdgesGeometry(new BoxGeometry(1.25, 1.25, 1.25));
    box.translate(0, 0.5, 0);
    this.selBox = new LineSegments(box, new LineBasicMaterial({ color: 0x5ec8e5, depthTest: false, transparent: true }));
    this.selBox.renderOrder = 10;
    this.selBox.visible = false;
    this.scene.add(this.selBox);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.maxPolarAngle = Math.PI * 0.495;
    this.controls.minDistance = 3;
    this.controls.maxDistance = 3000;
    this.controls.zoomToCursor = true;
    this.controls.listenToKeyEvents(this.renderer.domElement);
    this.controls.keyPanSpeed = 20;
    this.controls.addEventListener("change", () => this.onCamera());

    const el = this.renderer.domElement;
    el.addEventListener("webglcontextlost", (e) => {
      e.preventDefault();
      this.props.onFail("context_lost");
    });
    el.addEventListener("pointerdown", (e) => (this.down = { x: e.clientX, y: e.clientY, t: performance.now() }));
    el.addEventListener("pointerup", (e) => {
      const d = this.down;
      this.down = null;
      if (!d || Math.hypot(e.clientX - d.x, e.clientY - d.y) > 6 || performance.now() - d.t > 700) return;
      this.pick(e.clientX, e.clientY, e.pointerType === "touch" ? 30 : 20);
    });
    el.addEventListener("keydown", (e) => {
      if (e.key === "+" || e.key === "=") this.zoom(0.75);
      else if (e.key === "-") this.zoom(1 / 0.75);
      else if (e.key === "Escape") this.props.onSelect(undefined);
    });

    this.ro = new ResizeObserver(() => this.resize());
    this.ro.observe(host);
    this.resize();
  }

  // --------------------------------------------------------------- render loop
  request = () => {
    if (this.pending) return;
    this.pending = requestAnimationFrame(() => {
      this.pending = 0;
      this.render();
    });
  };
  render() {
    this.renderer.render(this.scene, this.camera);
    this.updateLabels();
    this.host.dataset.drawCalls = String(this.renderer.info.render.calls);
  }
  resize() {
    const w = this.host.clientWidth || 1;
    const h = this.host.clientHeight || 1;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.request();
  }
  onCamera() {
    // blocks are 1 m: far away they are scaled up so the base stays readable
    const dist = this.camera.position.distanceTo(this.controls.target);
    const s = Math.max(1, Math.min(8, dist / 45));
    if (Math.abs(s - this.scale) / this.scale > 0.12) {
      this.scale = s;
      this.placeInstances();
    }
    this.request();
  }

  // -------------------------------------------------------------------- build
  update(props: View3DProps, prev?: View3DProps) {
    this.props = props;
    this.updateVoxels(props, prev);
    const rebuild = !prev || prev.net !== props.net || prev.dim !== props.dim || prev.ranges !== props.ranges || prev.waystones !== props.waystones;
    if (!prev || prev.terrain !== props.terrain || prev.dim !== props.dim) this.buildTerrain();
    if (rebuild) this.build();
    else if (prev.selected !== props.selected) this.highlight();
    if (this.fittedFor !== props.dim) {
      this.fittedFor = props.dim;
      this.fit(false);
    }
    if (prev && props.flyKey !== prev.flyKey && props.selected) this.flyTo(props.selected);
    this.request();
  }

  updateVoxels(props: View3DProps, prev?: View3DProps) {
    if (props.voxels) {
      if (!this.voxels) {
        this.voxels = new VoxelLayer();
        this.scene.add(this.voxels.group);
        this.voxels.onChange = () => {
          this.request();
          // throttled, not debounced: the indicator keeps moving while meshes stream in
          if (this.statusTimer) return;
          this.statusTimer = window.setTimeout(() => {
            this.statusTimer = 0;
            const v = this.voxels;
            if (v) this.props.onVoxelStatus?.(v.status);
          }, 120);
        };
      }
      if (!prev?.voxels || prev.voxels.dim !== props.voxels.dim || prev.voxels.mf !== props.voxels.mf || prev.voxels.radius !== props.voxels.radius) this.voxels.load(props.voxels);
      this.voxels.group.visible = true;
      this.voxels.setClip(props.clipY);
      this.voxels.setNight(props.night);
    } else if (this.voxels) this.voxels.group.visible = false;
    const blocks = !!props.voxels && !this.voxels?.unavailable;
    this.terrainGroup.visible = !blocks;
    const bg = blocks ? (props.night ? "#070b14" : "#6f93bb") : cssVar(this.host, "--screen", "#0a0c0f");
    (this.scene.background as Color).set(bg);
    this.scene.fog = blocks ? new FogExp2(bg, props.night ? 0.0045 : 0.003) : null;
  }

  clear() {
    for (const o of this.owned) o.dispose();
    this.owned = [];
    this.content.clear();
    this.meshes = [];
    this.nodeIdx.clear();
    this.markers = null;
    this.dashed = [];
  }

  build() {
    this.clear();
    const { net, dim, ranges } = this.props;
    this.nodes = net.nodes.filter((n) => n.dim === dim && n.pos);
    // models
    for (const g of instanceGroups(this.nodes, MODELS.models)) {
      const part = MODELS.models[g.model].parts[g.part];
      const mesh = new InstancedMesh(partGeometry(g.model, g.part), partMaterial(g.tex, part, this.request), g.nodes.length);
      mesh.frustumCulled = false; // instances spread over the whole base
      if (part.trans) mesh.renderOrder = 2;
      g.nodes.forEach((ni, k) => {
        const list = this.nodeIdx.get(ni) ?? [];
        list.push([mesh, k]);
        this.nodeIdx.set(ni, list);
      });
      this.meshes.push(mesh);
      this.content.add(mesh);
    }
    // status markers above every block (one draw call; always visible)
    const mp = new Float32Array(this.nodes.length * 3);
    const mc = new Float32Array(this.nodes.length * 3);
    const c = new Color();
    this.nodes.forEach((n, i) => {
      c.setHex(STATUS_RGB[n.status] ?? 0x7d8790);
      mc.set([c.r, c.g, c.b], i * 3);
    });
    const mg = new BufferGeometry();
    mg.setAttribute("position", new BufferAttribute(mp, 3));
    mg.setAttribute("color", new BufferAttribute(mc, 3));
    const mm = new PointsMaterial({ size: 11, sizeAttenuation: false, vertexColors: true, map: this.dot, alphaTest: 0.4, depthTest: false, transparent: true });
    this.markers = new Points(mg, mm);
    this.markers.renderOrder = 5;
    this.markers.frustumCulled = false;
    this.content.add(this.markers);
    this.owned.push(mg, mm);
    // waystones: violet markers with a name label
    this.stones = (this.props.waystones ?? []).filter((w) => w.dim === dim).slice(0, 128);
    if (this.stones.length) {
      const sp = new Float32Array(this.stones.length * 3);
      this.stones.forEach((w, i) => sp.set([w.x + 0.5, w.y + 1.6, w.z + 0.5], i * 3));
      const sg = new BufferGeometry();
      sg.setAttribute("position", new BufferAttribute(sp, 3));
      const sm = new PointsMaterial({ size: 13, sizeAttenuation: false, color: 0xc28cff, map: this.dot, alphaTest: 0.4, depthTest: false, transparent: true });
      const pts = new Points(sg, sm);
      pts.renderOrder = 5;
      pts.frustumCulled = false;
      this.content.add(pts);
      this.owned.push(sg, sm);
    }
    this.placeInstances();
    this.buildLinks();
    if (ranges) this.buildRanges();
    this.highlight();
    this.syncLabels();
  }

  /** world position of the link/marker anchor of a node */
  anchor(n: NetNode, out = new Vector3()) {
    const tall = MODELS.models[MODEL_OF[n.type]]?.tall ?? 1;
    return out.set(n.pos!.x + 0.5, n.pos!.y + tall * 0.5 * this.scale, n.pos!.z + 0.5);
  }

  placeInstances() {
    const m = new Matrix4();
    const s = this.scale;
    this.nodes.forEach((n, i) => {
      // scaled blocks grow upwards from their own floor
      m.makeScale(s, s, s).setPosition(n.pos!.x + 0.5, n.pos!.y, n.pos!.z + 0.5);
      for (const [mesh, k] of this.nodeIdx.get(i) ?? []) mesh.setMatrixAt(k, m);
    });
    for (const mesh of this.meshes) mesh.instanceMatrix.needsUpdate = true;
    if (this.markers) {
      const a = this.markers.geometry.getAttribute("position") as BufferAttribute;
      this.nodes.forEach((n, i) => {
        const tall = MODELS.models[MODEL_OF[n.type]]?.tall ?? 1;
        a.setXYZ(i, n.pos!.x + 0.5, n.pos!.y + (tall + 0.7) * s, n.pos!.z + 0.5);
      });
      a.needsUpdate = true;
    }
    for (const d of this.dashed) {
      d.dashSize = d.userData.dash * s;
      d.gapSize = d.userData.gap * s;
    }
    this.placeSelection();
  }

  buildLinks() {
    const segs = linkSegments(this.props.net, this.props.dim);
    const buckets: Record<"wired" | "radio" | "spare" | "ender" | "legacy", { pos: number[]; col: number[]; ids: [string, string][] }> = {
      wired: { pos: [], col: [], ids: [] },
      radio: { pos: [], col: [], ids: [] },
      spare: { pos: [], col: [], ids: [] },
      ender: { pos: [], col: [], ids: [] },
      legacy: { pos: [], col: [], ids: [] },
    };
    const va = new Vector3();
    const vb = new Vector3();
    const c = new Color();
    for (const s of segs) {
      const kind = s.link.legacy ? "legacy" : s.link.kind === "wired" ? "wired" : s.link.kind === "ender" ? "ender" : s.used ? "radio" : "spare";
      const b = buckets[kind];
      // links attach at the block centre (scale 1); ok at every zoom
      va.set(s.a.pos!.x + 0.5, s.a.pos!.y + 0.5, s.a.pos!.z + 0.5);
      vb.set(s.b.pos!.x + 0.5, s.b.pos!.y + 0.5, s.b.pos!.z + 0.5);
      b.pos.push(va.x, va.y, va.z, vb.x, vb.y, vb.z);
      c.setHex(LINK_RGB[s.cls] ?? 0x5ec8e5);
      b.col.push(c.r, c.g, c.b, c.r, c.g, c.b);
      b.ids.push([s.a.id, s.b.id]);
    }
    const make = (k: keyof typeof buckets, mat: LineBasicMaterial | LineDashedMaterial) => {
      const b = buckets[k];
      if (!b.pos.length) return;
      const g = new BufferGeometry();
      g.setAttribute("position", new Float32BufferAttribute(b.pos, 3));
      g.setAttribute("color", new Float32BufferAttribute(b.col, 3));
      g.userData.base = Float32Array.from(b.col);
      g.userData.ids = b.ids;
      const line = new LineSegments(g, mat);
      line.frustumCulled = false;
      // links stay visible through hills and range spheres
      mat.depthTest = false;
      mat.transparent = true;
      line.renderOrder = 4;
      if (mat instanceof LineDashedMaterial) {
        line.computeLineDistances();
        this.dashed.push(mat);
      }
      this.content.add(line);
      this.owned.push(g, mat);
    };
    const dashed = (dash: number, gap: number, opacity = 1) => {
      const m = new LineDashedMaterial({ vertexColors: true, dashSize: dash * this.scale, gapSize: gap * this.scale, transparent: opacity < 1, opacity });
      m.userData = { dash, gap };
      return m;
    };
    make("wired", new LineBasicMaterial({ vertexColors: true }));
    make("radio", dashed(2.2, 1.4));
    make("spare", dashed(0.8, 1.6, 0.35));
    make("ender", dashed(0.4, 1.2));
    make("legacy", dashed(3, 1.5));
  }

  buildRanges() {
    const nodes = this.nodes.filter((n) => isNodeType(n.type) && n.status === "online" && n.type !== "ender_relay");
    if (!nodes.length) return;
    const sg = new SphereGeometry(1, 40, 20);
    const sm = new MeshBasicMaterial({ color: 0x5ec8e5, transparent: true, opacity: 0.05, depthWrite: false, side: DoubleSide });
    const spheres = new InstancedMesh(sg, sm, nodes.length);
    const m = new Matrix4();
    const c = new Color();
    nodes.forEach((n, i) => {
      m.makeScale(n.range, n.range, n.range).setPosition(n.pos!.x + 0.5, n.pos!.y + 0.5, n.pos!.z + 0.5);
      spheres.setMatrixAt(i, m);
      spheres.setColorAt(i, c.setHex(n.type === "mainframe" ? 0xddd771 : 0x5ec8e5));
    });
    spheres.renderOrder = 1;
    spheres.frustumCulled = false;
    // equator rings make the radius readable from above
    const ring: number[] = [];
    const rc: number[] = [];
    const seg = 72;
    for (const n of nodes) {
      c.setHex(n.type === "mainframe" ? 0xddd771 : 0x5ec8e5);
      for (let i = 0; i < seg; i++) {
        const a0 = (i / seg) * Math.PI * 2;
        const a1 = ((i + 1) / seg) * Math.PI * 2;
        ring.push(n.pos!.x + 0.5 + Math.cos(a0) * n.range, n.pos!.y + 0.5, n.pos!.z + 0.5 + Math.sin(a0) * n.range);
        ring.push(n.pos!.x + 0.5 + Math.cos(a1) * n.range, n.pos!.y + 0.5, n.pos!.z + 0.5 + Math.sin(a1) * n.range);
        rc.push(c.r, c.g, c.b, c.r, c.g, c.b);
      }
    }
    const rg = new BufferGeometry();
    rg.setAttribute("position", new Float32BufferAttribute(ring, 3));
    rg.setAttribute("color", new Float32BufferAttribute(rc, 3));
    const rm = new LineBasicMaterial({ vertexColors: true, transparent: true, opacity: 0.45, depthWrite: false });
    const rings = new LineSegments(rg, rm);
    rings.frustumCulled = false;
    this.content.add(spheres, rings);
    this.owned.push(sg, sm, rg, rm);
  }

  buildTerrain() {
    for (const o of this.terrainGroup.children as (Mesh | GridHelper)[]) {
      o.geometry.dispose();
      (o.material as Material).dispose();
    }
    this.terrainGroup.clear();
    const t = this.props.terrain;
    const nodes = this.props.net.nodes.filter((n) => n.dim === this.props.dim && n.pos);
    if (t && t.dim === this.props.dim && t.patches.length) {
      const mat = new MeshBasicMaterial({ vertexColors: true, side: DoubleSide });
      t.patches.forEach((p, i) => {
        const d = terrainMesh(p, t.step);
        const g = new BufferGeometry();
        g.setAttribute("position", new BufferAttribute(d.pos, 3));
        g.setAttribute("color", new BufferAttribute(d.col, 3));
        g.setIndex(new BufferAttribute(d.idx, 1));
        const mesh = new Mesh(g, i === 0 ? mat : mat.clone());
        this.terrainGroup.add(mesh);
      });
    } else {
      // no terrain: a grid at the lowest block, 16 blocks = 1 chunk
      const f = frameNodes(nodes, true);
      const size = Math.ceil((f.r * 2.2) / 16) * 16;
      const grid = new GridHelper(size, size / 16, 0x2a7f99, 0x1d242c);
      const minY = nodes.length ? Math.min(...nodes.map((n) => n.pos!.y)) : 64;
      grid.position.set(Math.round(f.cx / 16) * 16, minY - 0.01, Math.round(f.cz / 16) * 16);
      this.terrainGroup.add(grid);
    }
    this.request();
  }

  // ----------------------------------------------------------- selection etc.
  highlight() {
    const sel = this.props.selected;
    const node = sel ? this.props.net.nodes.find((n) => n.id === sel) : undefined;
    // relays and the mainframe: highlight everything routed through them; devices: their route
    const lit = node ? routedThrough(this.props.net, node.id) : null;
    const white = new Color(1, 1, 1);
    const dim = new Color(0.32, 0.32, 0.36);
    this.nodes.forEach((n, i) => {
      for (const [mesh, k] of this.nodeIdx.get(i) ?? []) mesh.setColorAt(k, !lit || lit.has(n.id) ? white : dim);
    });
    for (const mesh of this.meshes) if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    for (const o of this.content.children) {
      if (!(o instanceof LineSegments) || !o.geometry.userData.ids) continue;
      const g = o.geometry;
      const base = g.userData.base as Float32Array;
      const col = g.getAttribute("color") as BufferAttribute;
      (g.userData.ids as [string, string][]).forEach(([a, b], i) => {
        const f = !lit || (lit.has(a) && lit.has(b)) ? 1 : 0.22;
        for (let v = 0; v < 6; v++) col.array[i * 6 + v] = base[i * 6 + v] * f;
      });
      col.needsUpdate = true;
    }
    this.placeSelection();
    this.syncLabels();
    this.request();
  }

  placeSelection() {
    const n = this.props.selected ? this.nodes.find((x) => x.id === this.props.selected) : undefined;
    this.selBox.visible = !!n;
    if (!n) return;
    const tall = MODELS.models[MODEL_OF[n.type]]?.tall ?? 1;
    this.selBox.position.set(n.pos!.x + 0.5, n.pos!.y - 0.125 * this.scale, n.pos!.z + 0.5);
    this.selBox.scale.set(this.scale, this.scale * tall, this.scale);
  }

  pick(cx: number, cy: number, radius: number) {
    const r = this.renderer.domElement.getBoundingClientRect();
    const v = new Vector3();
    const pts = this.nodes.map((n, i) => {
      this.anchor(n, v).project(this.camera);
      return { i, x: ((v.x + 1) / 2) * r.width, y: ((1 - v.y) / 2) * r.height, depth: v.z };
    });
    const hit = pickNearest(pts, cx - r.left, cy - r.top, radius);
    this.props.onSelect(hit === undefined ? undefined : this.nodes[hit].id);
  }

  // ------------------------------------------------------------------ labels
  /** HTML labels (text only) for the mainframe, relays and the selection; at most 60. */
  syncLabels() {
    const want = this.nodes.filter((n) => isNodeType(n.type) || n.id === this.props.selected).slice(0, 60);
    const keep = new Set(want.map((n) => n.id));
    for (const [id, el] of this.labelEls)
      if (!keep.has(id)) {
        el.remove();
        this.labelEls.delete(id);
      }
    for (const n of want) {
      let el = this.labelEls.get(n.id);
      if (!el) {
        el = document.createElement("div");
        el.className = "n3d-label";
        this.labels.appendChild(el);
        this.labelEls.set(n.id, el);
      }
      el.textContent = n.name || "Device";
      el.classList.toggle("sel", n.id === this.props.selected);
      el.classList.toggle("off", n.status !== "online");
    }
    this.stones.forEach((w, i) => {
      const id = `ws:${i}`;
      keep.add(id);
      let el = this.labelEls.get(id);
      if (!el) {
        el = document.createElement("div");
        el.className = "n3d-label ws";
        this.labels.appendChild(el);
        this.labelEls.set(id, el);
      }
      el.textContent = w.name;
    });
    for (const [id, el] of this.labelEls)
      if (!keep.has(id)) {
        el.remove();
        this.labelEls.delete(id);
      }
  }
  updateLabels() {
    const w = this.host.clientWidth;
    const h = this.host.clientHeight;
    const v = new Vector3();
    for (const [id, el] of this.labelEls) {
      const ws = id.startsWith("ws:") ? this.stones[Number(id.slice(3))] : undefined;
      const n = ws ? undefined : this.nodes.find((x) => x.id === id);
      if (ws) v.set(ws.x + 0.5, ws.y + 1.6, ws.z + 0.5).project(this.camera);
      else if (n) {
        const tall = MODELS.models[MODEL_OF[n.type]]?.tall ?? 1;
        v.set(n.pos!.x + 0.5, n.pos!.y + (tall + 0.7) * this.scale, n.pos!.z + 0.5).project(this.camera);
      } else continue;
      const vis = v.z > -1 && v.z < 1 && Math.abs(v.x) < 1.1 && Math.abs(v.y) < 1.1;
      el.hidden = !vis;
      if (vis) el.style.transform = `translate(${Math.round(((v.x + 1) / 2) * w)}px, ${Math.round(((1 - v.y) / 2) * h) - 10}px) translate(-50%, -100%)`;
    }
  }

  // ------------------------------------------------------------------ camera
  fit(animate: boolean) {
    const f = frameNodes(this.nodes, false);
    const d = Math.max(40, f.r * 1.7);
    this.moveTo(new Vector3(f.cx, f.cy, f.cz), new Vector3(f.cx, f.cy + d * 0.6, f.cz + d * 0.8), animate);
  }
  flyTo(id: string) {
    const n = this.nodes.find((x) => x.id === id);
    if (!n) return;
    const target = new Vector3(n.pos!.x + 0.5, n.pos!.y + 0.5, n.pos!.z + 0.5);
    const dir = this.camera.position.clone().sub(this.controls.target).normalize();
    const dist = isNodeType(n.type) ? 22 : 14;
    this.moveTo(target, target.clone().add(dir.multiplyScalar(dist)), true);
  }
  zoom(f: number) {
    const off = this.camera.position.clone().sub(this.controls.target).multiplyScalar(f);
    const len = Math.max(this.controls.minDistance, Math.min(this.controls.maxDistance, off.length()));
    this.camera.position.copy(this.controls.target).add(off.setLength(len));
    this.controls.update();
  }
  moveTo(target: Vector3, pos: Vector3, animate: boolean) {
    cancelAnimationFrame(this.anim);
    const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!animate || reduce) {
      this.controls.target.copy(target);
      this.camera.position.copy(pos);
      this.controls.update();
      return;
    }
    const t0 = this.controls.target.clone();
    const p0 = this.camera.position.clone();
    const start = performance.now();
    const step = () => {
      const k = Math.min(1, (performance.now() - start) / 650);
      const e = k < 0.5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2;
      this.controls.target.lerpVectors(t0, target, e);
      this.camera.position.lerpVectors(p0, pos, e);
      this.controls.update();
      if (k < 1) this.anim = requestAnimationFrame(step);
    };
    this.anim = requestAnimationFrame(step);
  }

  dispose() {
    cancelAnimationFrame(this.anim);
    clearTimeout(this.statusTimer);
    this.voxels?.dispose();
    cancelAnimationFrame(this.pending);
    this.ro.disconnect();
    this.controls.dispose();
    this.clear();
    for (const o of this.terrainGroup.children as Object3D[]) {
      const m = o as Mesh;
      m.geometry?.dispose();
      (m.material as Material)?.dispose();
    }
    this.selBox.geometry.dispose();
    (this.selBox.material as Material).dispose();
    this.dot.dispose();
    this.renderer.dispose();
    this.renderer.domElement.remove();
    this.labels.remove();
  }
}

export default function Network3D(props: View3DProps) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<View3D | null>(null);
  const prev = useRef<View3DProps | undefined>(undefined);
  useEffect(() => {
    try {
      view.current = new View3D(host.current!, props);
    } catch (e) {
      props.onFail(String((e as Error)?.message ?? e));
      return;
    }
    return () => {
      view.current?.dispose();
      view.current = null;
      prev.current = undefined;
    };
  }, []);
  useEffect(() => {
    if (!view.current) return;
    view.current.update(props, prev.current);
    prev.current = props;
  });
  return (
    <div class="n3d" ref={host}>
      <div class="pz-controls">
        <button type="button" class="pz-btn" aria-label="Zoom in" onClick={() => view.current?.zoom(0.7)}>
          +
        </button>
        <button type="button" class="pz-btn" aria-label="Zoom out" onClick={() => view.current?.zoom(1 / 0.7)}>
          −
        </button>
        <button type="button" class="pz-btn pz-fit" aria-label="Fit to screen" onClick={() => view.current?.fit(true)}>
          ⤢
        </button>
      </div>
    </div>
  );
}
