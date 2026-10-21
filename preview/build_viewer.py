#!/usr/bin/env python3
"""Builds preview/viewer.html: a single self-contained file (models + textures inlined as
data URIs) that renders the cccloud models with three.js r128 from cdnjs. Works from file://.

    python3 preview/build_viewer.py
"""
import base64
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "cccloud")


def main():
    models, textures = {}, {}
    for sub in ("block", "item"):
        d = os.path.join(ASSETS, "models", sub)
        for f in sorted(os.listdir(d)):
            if f.endswith(".json"):
                with open(os.path.join(d, f)) as fh:
                    models[f"cccloud:{sub}/{f[:-5]}"] = json.load(fh)
    td = os.path.join(ASSETS, "textures")
    for dp, _, fs in os.walk(td):
        for f in sorted(fs):
            if f.endswith(".png"):
                rel = os.path.relpath(os.path.join(dp, f), td)[:-4].replace(os.sep, "/")
                with open(os.path.join(dp, f), "rb") as fh:
                    textures[f"cccloud:{rel}"] = "data:image/png;base64," + base64.b64encode(fh.read()).decode()
    html = TEMPLATE.replace("__MODELS__", json.dumps(models, separators=(",", ":"))) \
                   .replace("__TEXTURES__", json.dumps(textures, separators=(",", ":")))
    with open(os.path.join(HERE, "viewer.html"), "w") as fh:
        fh.write(html)
    print("viewer.html:", len(html) // 1024, "KiB,", len(models), "models,", len(textures), "textures")


TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CC: Cloud Models</title>
<style>
  :root { --bg:#25282b; --panel:#1b1d1f; --fg:#e6e6e6; --muted:#9aa0a6; --accent:#5ec8e5; --line:#3a3e42; }
  @media (prefers-color-scheme: light) { :root:not([data-theme="dark"]) { --bg:#d9dcdf; --panel:#f3f4f5; --fg:#1d1f21; --muted:#5b6166; --line:#c4c8cc; } }
  :root[data-theme="dark"] { --bg:#25282b; --panel:#1b1d1f; --fg:#e6e6e6; --muted:#9aa0a6; --line:#3a3e42; }
  html, body { margin:0; height:100%; background:var(--bg); color:var(--fg); font:14px/1.4 system-ui, sans-serif; overflow:hidden; }
  #c { display:block; width:100vw; height:100vh; touch-action:none; }
  #ui { position:fixed; top:12px; left:12px; right:12px; max-width:340px; background:var(--panel); border:1px solid var(--line);
        border-radius:8px; padding:10px 12px; box-sizing:border-box; }
  #ui h1 { font-size:15px; margin:0 0 6px; }
  .row { display:flex; flex-wrap:wrap; gap:6px; margin:6px 0; align-items:center; }
  .row span { color:var(--muted); min-width:64px; }
  button { background:transparent; color:var(--fg); border:1px solid var(--line); border-radius:6px; padding:4px 9px; cursor:pointer; font:inherit; }
  button.on { border-color:var(--accent); color:var(--accent); }
  #hint { color:var(--muted); font-size:12px; margin-top:4px; }
  #err { color:#d34f4f; font-size:12px; }
</style>
</head>
<body>
<canvas id="c"></canvas>
<div id="ui">
  <h1>CC: Cloud: model preview</h1>
  <div class="row"><span>Show</span>
    <button data-g="show" data-v="all" class="on">All</button>
    <button data-g="show" data-v="mainframe">Mainframe</button>
    <button data-g="show" data-v="uplink">Uplink</button>
    <button data-g="show" data-v="advanced">Adv. Uplink</button>
    <button data-g="show" data-v="bridges">Bridges</button>
  </div>
  <div class="row"><span>Status</span>
    <button data-g="status" data-v="online" class="on">online</button>
    <button data-g="status" data-v="connecting">connecting</button>
    <button data-g="status" data-v="offline">offline</button>
  </div>
  <div class="row"><span>Light</span>
    <button data-g="night" data-v="0" class="on">Day</button>
    <button data-g="night" data-v="1">Night</button>
    <span></span>
  </div>
  <div class="row"><span>Uplinks</span>
    <button data-g="direct" data-v="0" class="on">via computer</button>
    <button data-g="direct" data-v="1">direct (ring)</button>
  </div>
  <div class="row"><span>Anim</span>
    <button data-g="anim" data-v="1" class="on">on</button>
    <button data-g="anim" data-v="0">off</button>
  </div>
  <div id="hint">Drag to orbit, wheel/pinch to zoom. Nearest-neighbour textures, vanilla face shading.</div>
  <div id="err"></div>
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script>
const MODELS = __MODELS__;
const TEXTURES = __TEXTURES__;
const state = { show: "all", status: "online", night: "0", direct: "0", anim: "1" };

if (!window.THREE) { document.getElementById("err").textContent = "three.js could not be loaded (offline?)."; }

const renderer = new THREE.WebGLRenderer({ canvas: document.getElementById("c"), antialias: false });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(35, 1, 0.05, 100);
let yaw = 0.3, pitch = 0.42, dist = 11;
const target = new THREE.Vector3(0.5, 0.9, 0.5);

function bgColor() { return getComputedStyle(document.documentElement).getPropertyValue("--bg").trim() || "#25282b"; }

// ---------------------------------------------------------------- textures
const texCache = {};
const texInfo = {};
function loadTex(id) {
  if (typeof id === "object") id = id.sprite;
  if (texCache[id]) return texCache[id];
  const img = new Image();
  const t = new THREE.Texture(img);
  img.onload = () => {
    t.needsUpdate = true;
    const cv = document.createElement("canvas"); cv.width = img.width; cv.height = img.height;
    const g = cv.getContext("2d"); g.drawImage(img, 0, 0);
    texInfo[id] = { data: g.getImageData(0, 0, img.width, img.height).data, w: img.width, h: img.height };
    rebuild();
  };
  img.src = TEXTURES[id];
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.generateMipmaps = false; t.flipY = false;
  texCache[id] = t;
  return t;
}
// partially transparent texels inside a uv rect => translucent quad (same rule as MC 26.1)
function isTranslucent(id, uv) {
  const ti = texInfo[id]; if (!ti) return false;
  const x0 = Math.floor(Math.min(uv[0], uv[2]) / 16 * ti.w), x1 = Math.ceil(Math.max(uv[0], uv[2]) / 16 * ti.w);
  const y0 = Math.floor(Math.min(uv[1], uv[3]) / 16 * ti.h), y1 = Math.ceil(Math.max(uv[1], uv[3]) / 16 * ti.h);
  for (let y = y0; y < Math.max(y1, y0 + 1); y++) for (let x = x0; x < Math.max(x1, x0 + 1); x++) {
    const a = ti.data[(y * ti.w + x) * 4 + 3]; if (a > 0 && a < 255) return true; }
  return false;
}

// ---------------------------------------------------------------- model parsing
function resolveModel(id, override) {
  let textures = {}, elements = null; const chain = [];
  while (id && MODELS[id]) { chain.push(MODELS[id]); id = MODELS[id].parent; }
  for (let i = chain.length - 1; i >= 0; i--) Object.assign(textures, chain[i].textures || {});
  if (override) Object.assign(textures, override);
  for (const m of chain) if (m.elements) { elements = m.elements; break; }
  return { textures, elements: elements || [] };
}
function resolveRef(tx, r) { let n = 0; while (typeof r === "string" && r[0] === "#" && n++ < 10) r = tx[r.slice(1)]; return r; }

const CORNERS = {
  north: [[1,1,0],[0,1,0],[0,0,0],[1,0,0]], south: [[0,1,1],[1,1,1],[1,0,1],[0,0,1]],
  west: [[0,1,0],[0,1,1],[0,0,1],[0,0,0]], east: [[1,1,1],[1,1,0],[1,0,0],[1,0,1]],
  up: [[0,1,0],[1,1,0],[1,1,1],[0,1,1]], down: [[0,0,1],[1,0,1],[1,0,0],[0,0,0]],
};
function rotMat(axis, deg) {
  const m = new THREE.Matrix4(), r = THREE.MathUtils.degToRad(deg);
  if (axis === "x") m.makeRotationX(r); else if (axis === "y") m.makeRotationY(r); else m.makeRotationZ(r);
  return m;
}
function shadeOf(n) { const up = n.y > 0 ? 1 : 0.5; return Math.min(n.x*n.x*0.6 + n.y*n.y*up + n.z*n.z*0.8, 1); }

// builds a THREE.Group (in px units) for a model id
function buildModel(id, override) {
  const { textures, elements } = resolveModel(id, override);
  const buckets = {};
  for (const el of elements) {
    const f = el.from, t = el.to, emit = (el.light_emission || 0) > 0;
    let R = null, O = null;
    if (el.rotation) { R = rotMat(el.rotation.axis, el.rotation.angle); O = new THREE.Vector3(...el.rotation.origin); }
    for (const [d, face] of Object.entries(el.faces || {})) {
      const tex = resolveRef(textures, face.texture);
      const pts = CORNERS[d].map(c => { const v = new THREE.Vector3(c[0] ? t[0] : f[0], c[1] ? t[1] : f[1], c[2] ? t[2] : f[2]);
        if (R) v.sub(O).applyMatrix4(R).add(O); return v; });
      const uv = face.uv; let uvs = [[uv[0],uv[1]],[uv[2],uv[1]],[uv[2],uv[3]],[uv[0],uv[3]]];
      const rot = (face.rotation || 0) / 90; if (rot) uvs = uvs.slice(rot).concat(uvs.slice(0, rot));
      const n = new THREE.Vector3().subVectors(pts[3], pts[0]).cross(new THREE.Vector3().subVectors(pts[1], pts[0])).normalize();
      const trans = isTranslucent(typeof tex === "object" ? tex.sprite : tex, uv);
      const key = (typeof tex === "object" ? tex.sprite : tex) + "|" + (emit ? 1 : 0) + "|" + (trans ? 1 : 0);
      const b = buckets[key] || (buckets[key] = { tex, emit, trans, pos: [], uv: [], col: [], idx: [] });
      const s = el.shade === false ? 1 : shadeOf(n), base = b.pos.length / 3;
      for (let i = 0; i < 4; i++) { b.pos.push(pts[i].x, pts[i].y, pts[i].z); b.uv.push(uvs[i][0] / 16, uvs[i][1] / 16); b.col.push(s, s, s); }
      b.idx.push(base, base + 2, base + 1, base, base + 3, base + 2);
    }
  }
  const g = new THREE.Group();
  for (const b of Object.values(buckets)) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.Float32BufferAttribute(b.pos, 3));
    geo.setAttribute("uv", new THREE.Float32BufferAttribute(b.uv, 2));
    geo.setAttribute("color", new THREE.Float32BufferAttribute(b.col, 3));
    geo.setIndex(b.idx);
    const mat = new THREE.MeshBasicMaterial({ map: loadTex(b.tex), vertexColors: true, side: THREE.FrontSide,
      transparent: b.trans, depthWrite: !b.trans, alphaTest: b.trans ? 0.01 : 0.1 });
    if (!b.emit) mat.userData.dimmable = true;
    const mesh = new THREE.Mesh(geo, mat); mesh.renderOrder = b.trans ? 2 : 0;
    g.add(mesh);
  }
  return g;
}

// ---------------------------------------------------------------- scene
const world = new THREE.Group(); world.scale.setScalar(1 / 16); scene.add(world);
const animated = [];
const FACING = { north: [0, 0], east: [0, 90], south: [0, 180], west: [0, 270], up: [270, 0], down: [90, 0] };

function placed(modelId, bx, by, bz, facing, override, pivot) {
  // block -> facing rotation around block centre (vanilla: x then y, clockwise) -> optional animated pivot
  const outer = new THREE.Group(); outer.position.set(bx * 16 + 8, by * 16 + 8, bz * 16 + 8);
  const [rx, ry] = FACING[facing];
  const rot = new THREE.Group(); rot.rotation.order = "YXZ";
  rot.rotation.y = THREE.MathUtils.degToRad(-ry); rot.rotation.x = THREE.MathUtils.degToRad(-rx);
  outer.add(rot);
  const center = new THREE.Group(); center.position.set(-8, -8, -8); rot.add(center);
  const model = buildModel(modelId, override);
  if (pivot) {
    const p = new THREE.Group(); p.position.set(...pivot.at); center.add(p);
    const spin = new THREE.Group(); p.add(spin);
    model.position.set(-pivot.at[0], -pivot.at[1], -pivot.at[2]); spin.add(model);
    animated.push({ spin, ...pivot });
  } else center.add(model);
  world.add(outer);
  return outer;
}

function dummyBlock(bx, by, bz, color) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(16, 16, 16), new THREE.MeshBasicMaterial({ color }));
  m.material.userData.dimmable = true; m.material.userData.base = new THREE.Color(color);
  m.position.set(bx * 16 + 8, by * 16 + 8, bz * 16 + 8); world.add(m);
  const e = new THREE.LineSegments(new THREE.EdgesGeometry(m.geometry), new THREE.LineBasicMaterial({ color: 0x2d2d2d }));
  m.add(e);
}

function rebuild() {
  while (world.children.length) world.remove(world.children[0]);
  animated.length = 0;
  const s = state.status, show = state.show, direct = state.direct === "1";
  if (show === "all" || show === "mainframe") {
    const ox = show === "all" ? 0 : 0;
    placed(`cccloud:block/mainframe_lower_${s}`, ox, 0, 0, "south");
    placed(`cccloud:block/mainframe_upper_${s}`, ox, 1, 0, "south");
    placed("cccloud:block/mainframe_fan", ox, 0, 0, "south", null,
      { at: [13.5, 8, 8], axis: "x", speed: s === "online" ? 6 : s === "connecting" ? 2 : 0 });
    placed("cccloud:block/mainframe_hologram", ox, 1, 0, "south", null,
      { at: [8, 23.5, 8], axis: "y", speed: 0.6, holo: true });
  }
  const ups = [];
  if (show === "all" || show === "uplink") ups.push([false, show === "all" ? 2 : 0]);
  if (show === "all" || show === "advanced") ups.push([true, show === "all" ? -2 : 0]);
  for (const [adv, x] of ups) {
    dummyBlock(x, 0, 0, adv ? 0xddd973 : 0x7f7f7f);
    const base = adv ? "uplink_advanced" : "uplink";
    // uplink in front of the dummy block (attached to its south face => facing north)
    placed(`cccloud:block/${base}_${s}`, x, 0, 1, "north");
    const spd = s === "offline" ? 0 : s === "connecting" ? (adv ? 4 : 12) : (adv ? 1.6 : 3.1);
    placed(adv ? "cccloud:block/uplink_advanced_core" : "cccloud:block/uplink_ring", x, 0, 1, "north", null,
      { at: [8, 8, 1.375], axis: "z", speed: spd });
    if (direct) placed("cccloud:block/uplink_direct_ring", x, 0, 1, "north");
  }
  if (show === "all" || show === "bridges") {
    const all = show === "all";
    placed(`cccloud:block/me_cloud_bridge_${s}`, all ? -4 : -0.75, 0, all ? -1 : 0, "south");
    placed(`cccloud:block/rs_cloud_bridge_${s}`, all ? 4 : 0.75, 0, all ? -1 : 0, "south");
  }
  applyLight();
}

function applyLight() {
  const night = state.night === "1";
  scene.background = new THREE.Color(night ? "#07090c" : bgColor());
  world.traverse(o => { if (o.material && o.material.userData.dimmable) {
    const base = o.material.userData.base || new THREE.Color(1, 1, 1);
    o.material.color.copy(base).multiplyScalar(night ? 0.13 : 1); } });
}

// ---------------------------------------------------------------- ui
document.querySelectorAll("#ui button").forEach(b => b.addEventListener("click", () => {
  state[b.dataset.g] = b.dataset.v;
  document.querySelectorAll(`#ui button[data-g="${b.dataset.g}"]`).forEach(x => x.classList.toggle("on", x === b));
  if (b.dataset.g === "night") applyLight(); else if (b.dataset.g !== "anim") rebuild();
}));
const cv = renderer.domElement; let drag = null; const pts = new Map();
cv.addEventListener("pointerdown", e => { cv.setPointerCapture(e.pointerId); pts.set(e.pointerId, [e.clientX, e.clientY]); drag = [e.clientX, e.clientY]; });
cv.addEventListener("pointermove", e => {
  if (!pts.has(e.pointerId)) return;
  if (pts.size === 2) { const [a, b] = [...pts.values()]; const d0 = Math.hypot(a[0]-b[0], a[1]-b[1]);
    pts.set(e.pointerId, [e.clientX, e.clientY]); const [c, d] = [...pts.values()]; const d1 = Math.hypot(c[0]-d[0], c[1]-d[1]);
    dist = Math.min(14, Math.max(1.5, dist * d0 / Math.max(d1, 1))); return; }
  pts.set(e.pointerId, [e.clientX, e.clientY]);
  yaw -= (e.clientX - drag[0]) * 0.008; pitch = Math.min(1.5, Math.max(-1.2, pitch + (e.clientY - drag[1]) * 0.008)); drag = [e.clientX, e.clientY]; });
const up = e => pts.delete(e.pointerId);
cv.addEventListener("pointerup", up); cv.addEventListener("pointercancel", up);
cv.addEventListener("wheel", e => { e.preventDefault(); dist = Math.min(14, Math.max(1.5, dist * (1 + Math.sign(e.deltaY) * 0.1))); }, { passive: false });

function resize() { const w = innerWidth, h = innerHeight; renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); }
addEventListener("resize", resize); resize();

const clock = new THREE.Clock();
function frame() {
  const t = clock.getElapsedTime();
  if (state.anim === "1") for (const a of animated) {
    const ang = t * a.speed;
    a.spin.rotation.set(0, 0, 0); a.spin.rotation[a.axis] = ang;
    if (a.holo) {
      a.spin.position.y = Math.sin(t * 2) * 0.5;
      const off = state.status === "offline", con = state.status === "connecting";
      a.spin.visible = !off && !(con && Math.sin(t * 23) > 0.6);
    }
  }
  camera.position.set(target.x + dist * Math.cos(pitch) * Math.sin(yaw), target.y + dist * Math.sin(pitch),
                      target.z + dist * Math.cos(pitch) * Math.cos(yaw));
  camera.lookAt(target);
  renderer.render(scene, camera);
  requestAnimationFrame(frame);
}
for (const id in TEXTURES) loadTex(id);
rebuild();
frame();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
