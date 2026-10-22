#!/usr/bin/env python3
"""Software renderer for CC: Cloud art previews (needs Pillow + numpy).

Renders vanilla-format block models (our models and CC: Tweaked's for comparison) with
nearest-neighbour textures, vanilla face shading, element rotations, blockstate rotations,
emissive elements and translucency. Writes:

  preview/side_by_side.png   our blocks next to the CC blocks they must match
  preview/contact_sheet.png  every cccloud texture at 8x with its file name
  preview/renders/*.png      extra angles used while iterating (day + night)

Usage: python preview/render_preview.py [--cc /path/to/cct/assets/computercraft]
The CC assets are only read for the comparison render; nothing is copied.
"""
from __future__ import annotations

import argparse
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OURS = os.path.join(ROOT, "src", "main", "resources", "assets", "cccloud")
# AE2 / Refined Storage assets extracted read-only from the ATM11 jars, used only for comparison renders
MODREF = "~/ref/modref"
DEFAULT_CC = ("~/ref/"
              "scratchpad/cct-26.1/projects/common/src")


# --------------------------------------------------------------------------- asset lookup
class Assets:
    def __init__(self, cc_src):
        self.roots = {
            "cccloud": [OURS],
            "computercraft": [os.path.join(cc_src, "main/resources/assets/computercraft"),
                              os.path.join(cc_src, "generated/resources/assets/computercraft")],
            "ae2": [os.path.join(MODREF, "ae2/assets/ae2")],
            "refinedstorage": [os.path.join(MODREF, "rs/assets/refinedstorage")],
        }
        self.tex_cache = {}

    def find(self, rid, kind, ext):
        ns, path = rid.split(":", 1) if ":" in rid else ("minecraft", rid)
        for r in self.roots.get(ns, []):
            p = os.path.join(r, kind, path + ext)
            if os.path.exists(p):
                return p
        return None

    def model(self, rid):
        """Resolve parents; returns (elements, textures)."""
        textures, elements = {}, None
        chain = []
        while rid:
            p = self.find(rid, "models", ".json")
            if p is None:
                m = VANILLA.get(rid.replace("minecraft:", ""))
                if m is None:
                    break  # block/block etc. carry no elements we need
            else:
                with open(p) as f:
                    m = json.load(f)
            chain.append(m)
            rid = m.get("parent")
        for m in reversed(chain):
            textures.update(m.get("textures", {}))
        for m in chain:
            if "elements" in m:
                elements = m["elements"]
                break
        return elements or [], textures

    def texture(self, rid):
        if isinstance(rid, dict):
            rid = rid["sprite"]
        if rid in self.tex_cache:
            return self.tex_cache[rid]
        p = self.find(rid, "textures", ".png")
        im = Image.open(p).convert("RGBA")
        if im.height > im.width:  # animated strip -> first frame
            im = im.crop((0, 0, im.width, im.width))
        a = np.asarray(im).astype(np.float32) / 255.0
        self.tex_cache[rid] = a
        return a


_FULL = {"from": [0, 0, 0], "to": [16, 16, 16]}
VANILLA = {
    "block/block": {"display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                                        "scale": [0.625, 0.625, 0.625]}}},
    "block/cube": {"elements": [dict(_FULL, faces={d: {"texture": "#" + d, "cullface": d} for d in
                                                   ("north", "south", "west", "east", "up", "down")})]},
    "block/orientable": {"parent": "block/orientable_with_bottom",
                         "textures": {"bottom": "#top"}},
    "block/orientable_with_bottom": {"parent": "block/cube", "textures": {
        "north": "#front", "south": "#side", "west": "#side", "east": "#side", "up": "#top", "down": "#bottom"}},
    "block/cube_all": {"parent": "block/cube", "textures": {d: "#all" for d in
                                                         ("north", "south", "west", "east", "up", "down")}},
}


def resolve_tex(textures, ref):
    seen = 0
    while isinstance(ref, str) and ref.startswith("#") and seen < 10:
        ref = textures.get(ref[1:])
        seen += 1
    return ref


# --------------------------------------------------------------------------- geometry
def rot_matrix(axis, deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


FACE_CORNERS = {
    # corners TL, TR, BR, BL as seen from outside; index into (x0|x1, y0|y1, z0|z1)
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}


def default_uv(f, t, d):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {"down": (x0, 16 - z1, x1, 16 - z0), "up": (x0, z0, x1, z1),
            "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
            "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0)}[d]


class Quad:
    __slots__ = ("p", "uv", "tex", "emit", "normal", "shade")

    def __init__(self, p, uv, tex, emit, shade=True):
        self.p, self.uv, self.tex, self.emit, self.shade = p, uv, tex, emit, shade
        n = np.cross(p[3] - p[0], p[1] - p[0])
        ln = np.linalg.norm(n)
        self.normal = n / ln if ln > 1e-9 else np.array([0, 1.0, 0])


def model_quads(assets, rid, transform=None, tex_override=None):
    """Quads of a model in block space (pixels), transform = 4x4-ish callable on Nx3 arrays."""
    elements, textures = assets.model(rid)
    if tex_override:
        textures = {**textures, **tex_override}
    quads = []
    for el in elements:
        f = np.array(el["from"], float)
        t = np.array(el["to"], float)
        emit = el.get("light_emission", 0)
        R = None
        if "rotation" in el:
            r = el["rotation"]
            if "axis" in r:
                R = rot_matrix(r["axis"], r["angle"])
            else:
                R = rot_matrix("z", r.get("z", 0)) @ rot_matrix("y", r.get("y", 0)) @ rot_matrix("x", r.get("x", 0))
            origin = np.array(r["origin"], float)
        for d, face in el.get("faces", {}).items():
            pts = []
            for (ix, iy, iz) in FACE_CORNERS[d]:
                pts.append([t[0] if ix else f[0], t[1] if iy else f[1], t[2] if iz else f[2]])
            pts = np.array(pts, float)
            if R is not None:
                pts = (pts - origin) @ R.T + origin
            u0, v0, u1, v1 = face.get("uv", default_uv(f, t, d))
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            rot = face.get("rotation", 0) // 90
            uvs = uvs[rot:] + uvs[:rot] if rot else uvs
            tex = resolve_tex(textures, face["texture"])
            if transform is not None:
                pts = transform(pts)
            quads.append(Quad(pts, np.array(uvs, float) / 16.0, assets.texture(tex), emit, el.get("shade", True)))
    return quads


def blockstate_transform(x=0, y=0, offset=(0, 0, 0), extra=None):
    """Vanilla variant rotation (x then y, clockwise = negative right-handed) around the block centre."""
    Rx = rot_matrix("x", -x)
    Ry = rot_matrix("y", -y)
    R = Ry @ Rx
    off = np.array(offset, float) * 16.0
    c = np.array([8.0, 8.0, 8.0])

    def tf(p):
        if extra is not None:
            p = extra(p)
        return (p - c) @ R.T + c + off
    return tf


def spin(axis, deg, pivot):
    R = rot_matrix(axis, deg)
    pv = np.array(pivot, float)
    return lambda p: (p - pv) @ R.T + pv


# --------------------------------------------------------------------------- rasterizer
def vanilla_shade(n):
    x, y, z = n
    up = 1.0 if y > 0 else 0.5
    return min(x * x * 0.6 + y * y * up + z * z * 0.8, 1.0)


def render(quads, size=520, yaw=225, pitch=30, zoom=None, center=None, night=False, bg=(52, 56, 60)):
    """Orthographic view. yaw 225 = looking at the north + west faces (like a GUI block)."""
    allp = np.concatenate([q.p for q in quads])
    if center is None:
        center = (allp.min(0) + allp.max(0)) / 2
    Ry = rot_matrix("y", yaw)
    Rx = rot_matrix("x", pitch)
    V = Rx @ Ry

    def proj(p):
        return (p - center) @ V.T

    pp = proj(allp)
    if zoom is None:
        ext = max(pp[:, 0].max() - pp[:, 0].min(), pp[:, 1].max() - pp[:, 1].min())
        zoom = size * 0.86 / ext
    col = np.zeros((size, size, 3), np.float32)
    col[:] = np.array(bg, np.float32) / 255.0
    depth = np.full((size, size), -1e9, np.float32)
    trans = []
    view_dir = V.T @ np.array([0, 0, 1.0])  # direction towards the camera in world space

    def raster(q, blend):
        if np.dot(q.normal, view_dir) <= 0:  # back-face cull like MC
            return
        p = proj(q.p)
        sx = p[:, 0] * zoom + size / 2
        sy = -p[:, 1] * zoom + size / 2
        sz = p[:, 2]
        shade = vanilla_shade(q.normal) if q.shade else 1.0
        light = 1.0 if (q.emit or not night) else 0.13
        if night and not q.emit:
            shade *= 1.0
        tex = q.tex
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], sz[list(tri)]
            uv = q.uv[list(tri)]
            x0, x1 = int(max(math.floor(xs.min()), 0)), int(min(math.ceil(xs.max()), size - 1))
            y0, y1 = int(max(math.floor(ys.min()), 0)), int(min(math.ceil(ys.max()), size - 1))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not m.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            u = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
            v = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
            ti = np.clip((u * tw).astype(int), 0, tw - 1)
            tj = np.clip((v * th).astype(int), 0, th - 1)
            texel = tex[tj, ti]
            a = texel[..., 3]
            sub_d = depth[y0:y1 + 1, x0:x1 + 1]
            sub_c = col[y0:y1 + 1, x0:x1 + 1]
            if not blend:
                m = m & (a > 0.1) & (z > sub_d)
                sub_d[m] = z[m]
                sub_c[m] = texel[..., :3][m] * shade * light
            else:
                m = m & (a > 0.004) & (z > sub_d)
                aa = a[m][:, None]
                sub_c[m] = sub_c[m] * (1 - aa) + texel[..., :3][m] * shade * light * aa

    for q in quads:
        if (q.tex[..., 3] < 0.99).any() and ((q.tex[..., 3] > 0.01) & (q.tex[..., 3] < 0.99)).any():
            trans.append(q)
        else:
            raster(q, False)
    trans.sort(key=lambda q: proj(q.p)[:, 2].mean())
    for q in trans:
        raster(q, True)
    img = (np.clip(col, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(img, "RGB")


# --------------------------------------------------------------------------- scenes
def mainframe_quads(a, status="online", facing_y=0, pos=(0, 0, 0), t=0.0, holo=True):
    q = []
    q += model_quads(a, f"cccloud:block/mainframe_lower_{status}", blockstate_transform(y=facing_y, offset=pos))
    up = (pos[0], pos[1] + 1, pos[2])
    q += model_quads(a, f"cccloud:block/mainframe_upper_{status}", blockstate_transform(y=facing_y, offset=up))
    q += model_quads(a, "cccloud:block/mainframe_fan",
                     blockstate_transform(y=facing_y, offset=pos, extra=spin("x", t * 360, (13.5, 8, 8))))
    if holo and status != "offline":
        q += model_quads(a, "cccloud:block/mainframe_hologram",
                         blockstate_transform(y=facing_y, offset=up, extra=spin("y", t * 90, (8, 23.5, 8))))
    return q


FACING6 = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}


def uplink_quads(a, advanced=False, status="online", facing="north", pos=(0, 0, 0), direct=False, t=0.0):
    x, y = FACING6[facing]
    base = "uplink_advanced" if advanced else "uplink"
    q = model_quads(a, f"cccloud:block/{base}_{status}", blockstate_transform(x, y, pos))
    part = "cccloud:block/uplink_advanced_core" if advanced else "cccloud:block/uplink_ring"
    q += model_quads(a, part, blockstate_transform(x, y, pos, extra=spin("z", t * 360, (8, 8, 1.375))))
    if direct:
        q += model_quads(a, "cccloud:block/uplink_direct_ring", blockstate_transform(x, y, pos))
    return q


def cc(a, rid, x=0, y=0, pos=(0, 0, 0)):
    return model_quads(a, rid, blockstate_transform(x, y, pos))


def label(img, text, color=(235, 235, 235)):
    d = ImageDraw.Draw(img)
    d.text((8, 6), text, fill=color)
    return img


def grid(images, cols, pad=6, bg=(30, 32, 34)):
    w = max(i.width for i in images)
    h = max(i.height for i in images)
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * (w + pad) + pad, rows * (h + pad) + pad), bg)
    for k, im in enumerate(images):
        out.paste(im, (pad + (k % cols) * (w + pad), pad + (k // cols) * (h + pad)))
    return out


def side_by_side(a):
    S = 520
    tiles = []
    # 1) Mainframe next to an Advanced Computer (+ advanced monitor on top, a real CC tower)
    for night in (False, True):
        q = mainframe_quads(a, "online", 0, (0, 0, 0), t=0.06)
        q += cc(a, "computercraft:block/computer_advanced_on", pos=(-1, 0, 0))
        q += cc(a, "computercraft:block/disk_drive_empty", pos=(-2, 0, 0))
        tiles.append(label(render(q, S, yaw=200, pitch=24, night=night),
                           "Mainframe vs CC Advanced Computer + Disk Drive" + (" (night)" if night else "")))
    # 2) Advanced uplink vs Ender modem, each on a CC advanced computer
    for night in (False, True):
        q = cc(a, "computercraft:block/computer_advanced_on", pos=(0, 0, 0))
        q += uplink_quads(a, True, "online", "west", pos=(1, 0, 2), direct=False, t=0.1)
        q += cc(a, "computercraft:block/computer_advanced_on", pos=(0, 0, 2))
        q += cc(a, "computercraft:block/wireless_modem_advanced_on", y=270, pos=(1, 0, 0))
        tiles.append(label(render(q, S, yaw=240, pitch=26, night=night),
                           "Advanced Uplink (left) vs Ender Modem (right)" + (" (night)" if night else "")))
    # 3) normal uplink vs wired modem, on normal computers
    for night in (False, True):
        q = cc(a, "computercraft:block/computer_normal_on", pos=(0, 0, 0))
        q += uplink_quads(a, False, "online", "west", pos=(1, 0, 2), t=0.1)
        q += cc(a, "computercraft:block/computer_normal_on", pos=(0, 0, 2))
        q += cc(a, "computercraft:block/wired_modem_off", y=270, pos=(1, 0, 0))
        tiles.append(label(render(q, S, yaw=240, pitch=26, night=night),
                           "Uplink (left) vs Wired Modem (right)" + (" (night)" if night else "")))
    # 4) cloud bridges next to the AE2 ME Interface / RS Interface (and CC computers)
    for night in (False, True):
        tiles.append(label(render(bridge_scene(a, night), S, yaw=195, pitch=22, night=night),
                           "CC Adv. Computer | ME bridge | AE2 Interface | RS bridge | RS Interface"
                           + (" (night)" if night else "")))
    out = grid(tiles, 2)
    out.save(os.path.join(HERE, "side_by_side.png"))
    return out


def bridge_quads(a, kind, status="online", facing_y=0, pos=(0, 0, 0)):
    return model_quads(a, f"cccloud:block/{kind}_cloud_bridge_{status}", blockstate_transform(y=facing_y, offset=pos))


def bridge_scene(a, night=False):
    """One line, left to right: CC Advanced Computer, ME bridge, AE2 Interface, RS bridge, RS Interface."""
    have = os.path.isdir(MODREF)
    q = cc(a, "computercraft:block/computer_advanced_on", pos=(2, 0, 0))
    q += bridge_quads(a, "me", pos=(1, 0, 0))
    q += bridge_quads(a, "rs", pos=(-1, 0, 0))
    if have:
        q += cc(a, "ae2:block/interface", pos=(0, 0, 0))
        q += cc(a, "refinedstorage:block/interface/active", pos=(-2, 0, 0))
    return q


def bridge_renders(a):
    os.makedirs(os.path.join(HERE, "renders"), exist_ok=True)
    shots = []
    for kind in ("me", "rs"):
        for (yaw, pitch, nm) in ((200, 25, "front"), (150, 25, "front-right"), (20, 30, "back"), (245, 60, "top")):
            shots.append(label(render(bridge_quads(a, kind), 340, yaw=yaw, pitch=pitch), f"{kind} bridge {nm}"))
        for st in ("online", "connecting", "offline"):
            shots.append(label(render(bridge_quads(a, kind, st), 340, yaw=200, pitch=25, night=True),
                               f"{kind} bridge {st} night"))
    grid(shots, 7).save(os.path.join(HERE, "renders", "bridges.png"))


def detail_renders(a):
    os.makedirs(os.path.join(HERE, "renders"), exist_ok=True)
    S = 420
    shots = []
    for yaw, name in ((200, "front-left"), (160, "front-right"), (20, "back-left"), (300, "side-east (fan)"),
                      (110, "side-west (vents)")):
        shots.append(label(render(mainframe_quads(a, "online", t=0.06), S, yaw=yaw, pitch=22), f"mainframe {name}"))
    for st in ("connecting", "offline"):
        shots.append(label(render(mainframe_quads(a, st, t=0.0), S, yaw=200, pitch=22, night=True), f"mainframe {st} night"))
    shots.append(label(render(mainframe_quads(a, "online", t=0.0), S, yaw=200, pitch=22, night=True), "mainframe online night"))
    grid(shots, 4).save(os.path.join(HERE, "renders", "mainframe.png"))

    shots = []
    for adv in (False, True):
        for (yaw, pitch, nm) in ((330, 25, "3/4"), (0, 0, "front"), (90, 0, "side"), (30, 30, "direct")):
            q = uplink_quads(a, adv, "online", "north", direct=(nm == "direct"), t=0.12)
            shots.append(label(render(q, 360, yaw=yaw, pitch=pitch, center=np.array([8, 8, 1.0]),
                                      zoom=360 * 0.86 / 18), f"{'adv ' if adv else ''}uplink {nm}"))
        for st in ("connecting", "offline"):
            q = uplink_quads(a, adv, st, "north", t=0.12)
            shots.append(label(render(q, 360, yaw=330, pitch=25, night=True, center=np.array([8, 8, 1.0]),
                                      zoom=360 * 0.86 / 18), f"{'adv ' if adv else ''}uplink {st} night"))
    grid(shots, 6).save(os.path.join(HERE, "renders", "uplinks.png"))

    # in-world facing test: uplinks on all 6 sides of a computer
    q = cc(a, "computercraft:block/computer_normal_on", pos=(0, 0, 0))
    for f, off in (("north", (0, 0, 1)), ("east", (-1, 0, 0)), ("up", (0, -1, 0)),
                   ("south", (0, 0, -1)), ("west", (1, 0, 0)), ("down", (0, 1, 0))):
        q += uplink_quads(a, f in ("east", "down"), "online", f, pos=off, direct=(f == "up"))
    render(q, 520, yaw=210, pitch=30).save(os.path.join(HERE, "renders", "facings.png"))


def contact_sheet():
    files = []
    tex_root = os.path.join(OURS, "textures")
    for dp, _, fs in os.walk(tex_root):
        for f in sorted(fs):
            if f.endswith(".png"):
                files.append(os.path.join(dp, f))
    files.sort()
    cells = []
    for p in files:
        im = Image.open(p).convert("RGBA")
        sc = 8
        note = ""
        if os.sep + "gui" + os.sep in p:
            bbox = im.getchannel("A").getbbox()
            im = im.crop((0, 0, bbox[2], bbox[3]))   # GUI sheets: used area only, at 4x
            sc = 4
            note = ", used area, 4x"
        big = im.resize((im.width * sc, im.height * sc), Image.NEAREST)
        # checkerboard behind transparency
        cw, ch = big.size
        bg = Image.new("RGBA", (cw, ch), (90, 90, 90, 255))
        d = ImageDraw.Draw(bg)
        for y in range(0, ch, 16):
            for x in range(0, cw, 16):
                if (x // 16 + y // 16) % 2:
                    d.rectangle([x, y, x + 15, y + 15], fill=(120, 120, 120, 255))
        bg.alpha_composite(big)
        cell = Image.new("RGB", (max(cw, 200) + 16, ch + 30), (40, 42, 44))
        cell.paste(bg.convert("RGB"), (8, 24))
        ImageDraw.Draw(cell).text((8, 6), os.path.relpath(p, tex_root) + f"  ({Image.open(p).width}x{Image.open(p).height}{note})",
                                  fill=(230, 230, 230))
        cells.append(cell)
    # shelf packing
    W = 2200
    x = y = 0
    rowh = 0
    pos = []
    for c in cells:
        if x + c.width > W:
            x, y = 0, y + rowh
            rowh = 0
        pos.append((x, y))
        x += c.width
        rowh = max(rowh, c.height)
    out = Image.new("RGB", (W, y + rowh), (40, 42, 44))
    for c, p in zip(cells, pos):
        out.paste(c, p)
    out.save(os.path.join(HERE, "contact_sheet.png"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cc", default=DEFAULT_CC, help="CC: Tweaked projects/common/src directory")
    ap.add_argument("--only", default="all")
    args = ap.parse_args()
    a = Assets(args.cc)
    if args.only in ("all", "sbs"):
        side_by_side(a)
    if args.only in ("all", "detail"):
        detail_renders(a)
    if args.only in ("all", "detail", "bridges"):
        bridge_renders(a)
    if args.only in ("all", "contact"):
        contact_sheet()


if __name__ == "__main__":
    main()


# --------------------------------------------------------------------------- item (GUI) renders
def display_transform(assets, rid, key="gui"):
    disp = None
    while rid and disp is None:
        p = assets.find(rid, "models", ".json")
        if p is None:
            m = VANILLA.get(rid.replace("minecraft:", ""))
            if m is None:
                break
        else:
            with open(p) as f:
                m = json.load(f)
        disp = m.get("display", {}).get(key)
        rid = m.get("parent")
    disp = disp or {}
    rx, ry, rz = disp.get("rotation", [0, 0, 0])
    tx, ty, tz = disp.get("translation", [0, 0, 0])
    s = np.array(disp.get("scale", [1, 1, 1]), float)
    R = rot_matrix("x", rx) @ rot_matrix("y", ry) @ rot_matrix("z", rz)
    c = np.array([8.0, 8, 8])
    return lambda p: ((p - c) * s) @ R.T + c + np.array([tx, ty, tz])


def item_render(assets, models, size=160):
    tf = display_transform(assets, models[0])
    q = []
    for m in models:
        q += model_quads(assets, m, tf)
    # GUI: orthographic, looking at -Z, 16 px item slot = whole image
    return render(q, size, yaw=0, pitch=0, zoom=size / 16, center=np.array([8, 8, 8.0]), bg=(139, 139, 139))


def item_sheet(a):
    rows = [
        ["cccloud:item/mainframe_item"],
        ["cccloud:block/uplink_online", "cccloud:block/uplink_ring"],
        ["cccloud:block/uplink_advanced_online", "cccloud:block/uplink_advanced_core"],
        ["computercraft:block/computer_advanced_on"],
        ["computercraft:block/wireless_modem_advanced_on"],
        ["cccloud:block/me_cloud_bridge_online"],
        ["cccloud:block/rs_cloud_bridge_online"],
    ]
    ims = [item_render(a, r) for r in rows]
    for p in ("item/cloud_link_card", "item/cloud_core"):
        im = Image.open(os.path.join(OURS, "textures", p + ".png")).convert("RGBA").resize((160, 160), Image.NEAREST)
        bg = Image.new("RGBA", (160, 160), (139, 139, 139, 255))
        bg.alpha_composite(im)
        ims.append(bg.convert("RGB"))
    grid(ims, 9).save(os.path.join(HERE, "renders", "items_gui.png"))


if __name__ == "__main__" and os.environ.get("ITEMS"):
    item_sheet(Assets(DEFAULT_CC))
