#!/usr/bin/env python3
"""Deterministic model / blockstate / item-definition generator for CC: Cloud (`cccloud`).

    python3 tools/gen_models.py

Writes (all under src/main/resources/assets/cccloud/):
  models/block/*.json, models/item/*.json, blockstates/*.json, items/*.json, models/README.md
and docs/MODELS.md (pivots, render types, emissive notes).

UVs are computed from the atlas layout in tools/gen_textures.py (LAYOUTS), so textures and
models always agree. Faces either use a 16x16 *tile* with vanilla auto-UV (the texel under the
face's block position) or an explicit *region* (designed face, e.g. a blade front).
Texel density is always 16 px per block.
"""
from __future__ import annotations

import copy
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures as gt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "cccloud")
NS = "cccloud"
DIRS = ("north", "south", "west", "east", "up", "down")
STATES = ("online", "connecting", "offline")


# --------------------------------------------------------------------------- UV helpers
def auto_uv(frm, to, d):
    """Vanilla FaceBakery.defaultFaceUV."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {
        "down": (x0, 16 - z1, x1, 16 - z0),
        "up": (x0, z0, x1, z1),
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
        "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0),
        "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
    }[d]


def _wrap(a, b):
    """Shift a 1D uv span into 0..16 (faces never exceed one tile)."""
    while a < 0:
        a, b = a + 16, b + 16
    while b > 16:
        a, b = a - 16, b - 16
    return max(a, 0), min(b, 16)


class Tile:
    """Auto-UV inside a 16x16 tile of an atlas (or a whole 16x16 texture when atlas is None)."""

    def __init__(self, tex, atlas=None, name=None):
        self.tex, self.atlas, self.name = tex, atlas, name

    def uv(self, frm, to, d):
        u0, v0, u1, v1 = auto_uv(frm, to, d)
        u0, u1 = _wrap(u0, u1)
        v0, v1 = _wrap(v0, v1)
        if self.atlas is None:
            return [u0, v0, u1, v1]
        size = gt.LAYOUTS[self.atlas]["size"]
        ox, oy = gt.tile_origin(self.atlas, self.name)
        k = 16 / size
        return [(ox + u0) * k, (oy + v0) * k, (ox + u1) * k, (oy + v1) * k]


class Region:
    """Explicit texel rectangle, mapped onto the whole face (unmirrored when seen from outside)."""

    def __init__(self, tex, atlas, name, size=None, rect=None, rotation=0):
        self.tex, self.atlas, self.name, self.rotation = tex, atlas, name, rotation
        self.size = size if size is not None else gt.LAYOUTS[atlas]["size"]
        if rect is None:
            rect = gt.LAYOUTS[atlas]["regions"][name] if atlas else gt.STATUS_REGIONS[name]
        self.rect = rect

    def uv(self, frm, to, d):
        x, y, w, h = self.rect
        k = 16 / self.size
        return [x * k, y * k, (x + w) * k, (y + h) * k]


def num(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


# --------------------------------------------------------------------------- model builder
class Model:
    def __init__(self, parent="minecraft:block/block", textures=None):
        self.parent = parent
        self.textures = dict(textures or {})
        self.elements = []
        self.extra = {}

    def box(self, frm, to, faces, rot=None, emit=0, skip=(), only=None, shade=True, cull=True):
        """faces: a spec for all faces, or {dir: spec}, missing dirs fall back to faces.get('*')."""
        if not isinstance(faces, dict):
            faces = {"*": faces}
        el = {"from": [num(v) for v in frm], "to": [num(v) for v in to]}
        if rot:
            axis, angle, origin = rot
            assert angle in (-45, -22.5, 0, 22.5, 45), angle
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [num(v) for v in origin]}
        if emit:
            el["light_emission"] = emit
        if not shade:
            el["shade"] = False
        fd = {}
        for d in DIRS:
            if d in skip or (only is not None and d not in only):
                continue
            spec = faces.get(d, faces.get("*"))
            if spec is None:
                continue
            f = {"uv": [num(v) for v in spec.uv(frm, to, d)], "texture": "#" + spec.tex}
            if getattr(spec, "rotation", 0):
                f["rotation"] = spec.rotation
            if cull and not rot:
                c = _cull(frm, to, d)
                if c:
                    f["cullface"] = c
            fd[d] = f
        el["faces"] = fd
        self.elements.append(el)
        return el

    def json(self):
        out = {"parent": self.parent} if self.parent else {}
        out.update(self.extra)
        if self.textures:
            out["textures"] = self.textures
        if self.elements:
            out["elements"] = self.elements
        return out


def _cull(frm, to, d):
    if d == "north" and frm[2] == 0:
        return "north"
    if d == "south" and to[2] == 16:
        return "south"
    if d == "west" and frm[0] == 0:
        return "west"
    if d == "east" and to[0] == 16:
        return "east"
    if d == "down" and frm[1] == 0:
        return "down"
    if d == "up" and to[1] == 16:
        return "up"
    return None


# --------------------------------------------------------------------------- mainframe
MA = "mainframe"


def mt(name):
    return Tile("atlas", MA, name)


def mr(name):
    return Region("atlas", MA, name)


def led(name):
    return Region("leds", None, name, size=16)


def face_map(half):
    """Direction -> face tile for posts/panels/cap/plinth (position-consistent auto UV)."""
    if half == "lower":
        return {"north": mt("lower_front"), "south": mt("lower_back"), "west": mt("lower_west"),
                "east": mt("lower_east"), "up": mt("gold"), "down": mt("bottom")}
    return {"north": mt("upper_front"), "south": mt("upper_back"), "west": mt("upper_side"),
            "east": mt("upper_side"), "up": mt("top"), "down": mt("gold")}


def side_panel(m, half, west, opening, fm, round_hole=False):
    """Gold side panel (2 px thick, 1 px recessed from the posts) with an opening."""
    x0, x1 = (1, 3) if west else (13, 15)
    (z0, z1), (y0, y1) = opening
    lo, hi = (2, 16) if half == "lower" else (0, 14)
    inner = mt("recess")
    f = dict(fm)
    f["east" if west else "west"] = inner  # back side of the panel (only visible at the rim)
    m.box([x0, lo, 2], [x1, y0, 14], f)                     # below opening
    m.box([x0, y1, 2], [x1, hi, 14], f)                     # above opening
    m.box([x0, y0, 2], [x1, y1, z0], f)                     # front stile
    m.box([x0, y0, z1], [x1, y1, 14], f)                    # back stile
    if round_hole:
        # round the 8x8 opening: rows of width 4,6,8,8,8,8,6,4
        for (zz0, zz1, yy0, yy1) in ((z0, z0 + 2, y1 - 1, y1), (z1 - 2, z1, y1 - 1, y1),
                                     (z0, z0 + 1, y1 - 2, y1 - 1), (z1 - 1, z1, y1 - 2, y1 - 1),
                                     (z0, z0 + 2, y0, y0 + 1), (z1 - 2, z1, y0, y0 + 1),
                                     (z0, z0 + 1, y0 + 1, y0 + 2), (z1 - 1, z1, y0 + 1, y0 + 2)):
            m.box([x0, yy0, zz0], [x1, yy1, zz1], f)


def louvres(m, west, opening):
    """Angled gold louvre slats (real elements, 22.5 deg) inside a vent opening."""
    (z0, z1), (y0, y1) = opening
    xc = 2 if west else 14
    ang = -22.5 if west else 22.5
    for c in range(y0 + 1, y1, 2):
        m.box([xc - 0.5, c - 0.5, z0], [xc + 0.5, c + 0.5, z1],
              {"*": mt("gold"), "west" if west else "east": mt("gold_l")},
              rot=("z", ang, [xc, c, 8]), skip=("north", "south"))


def blades(m, ys, zfronts, led_ids):
    names = ("blade_a", "blade_b", "blade_c")
    for i, ((y0, y1), zf, li) in enumerate(zip(ys, zfronts, led_ids)):
        m.box([3, y0, zf], [13, y1, 3], {"*": mt("chassis"), "north": mr(names[(li) % 3])}, skip=("south",))
        # emissive LED bar sits in the blade's LED well (blade texture cols 6..8, row 1)
        m.box([4, y0 + 2, zf - 0.25], [7, y0 + 3, zf], led(f"led{li}"), emit=15, skip=("south",))


def mainframe_half(half):
    m = Model(textures={
        "particle": f"{NS}:block/mainframe",
        "atlas": f"{NS}:block/mainframe",
        "glass": f"{NS}:block/mainframe_glass",
        "leds": f"{NS}:block/mainframe_leds_online",
    })
    m.extra["render_type"] = "translucent"   # glass; 26.1 also derives this per quad from alpha
    fm = face_map(half)
    if half == "lower":
        lo, hi = 2, 16
        # plinth (gold base, full footprint)
        m.box([0, 0, 0], [16, 2, 16], {**fm, "up": mt("gold")})
        # chassis core (dark recess walls seen through vents, fan opening and blade bay)
        m.box([3, 2, 3], [13, 16, 14], mt("recess"), only=("north", "west", "east"))
        # posts
        for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
            m.box([x, 2, z], [x + 2, 16, z + 2], fm, skip=("down",))
        # side panels: west = louvre vent, east = round fan opening (fan blades = separate model)
        side_panel(m, half, True, ((4, 12), (4, 12)), fm)
        louvres(m, True, ((4, 12), (4, 12)))
        side_panel(m, half, False, ((4, 12), (4, 12)), fm, round_hole=True)
        # fan guard (cross bars in front of the rotating fan)
        m.box([14.25, 7.5, 4], [14.75, 8.5, 12], mt("chassis"))
        m.box([14.25, 4, 7.5], [14.75, 12, 8.5], mt("chassis"))
        # back panel + cable gland on the floor
        m.box([2, 2, 14], [14, 16, 15], fm, only=("south",))
        m.box([3, 2, 15], [13, 3.5, 17.5], mt("chassis"), skip=("down",))
        for x in (3.5, 7, 10.5):
            m.box([x, 3.5, 15], [x + 2, 16, 17], {"*": mt("cable")}, only=("south", "west", "east"))
        m.box([3, 8, 15], [13, 9, 17.25], mt("chassis"), skip=("north",))  # cable strap
        # front: door frame (dark), smoked glass, blades behind it
        m.box([2, 2, 0], [3, 16, 1], mt("chassis"), skip=("down",))
        m.box([13, 2, 0], [14, 16, 1], mt("chassis"), skip=("down",))
        m.box([3, 2, 0], [13, 3, 1], mt("chassis"), skip=("down",))
        blades(m, ((3, 7), (7.5, 11.5), (12, 16)), (1.5, 1.0, 2.0), (0, 1, 2))
        m.box([3, 3, 0.5], [13, 16, 0.75], Tile("glass"), only=("north",))
        # door handle (gold), continues into the upper half
        m.box([11, 9, -0.75], [12, 16, -0.25], mt("gold_l"))
        m.box([11.25, 10, -0.25], [11.75, 10.5, 0.5], mt("gold"), only=("up", "down", "west", "east"))
    else:
        lo, hi = 0, 14
        # cap
        m.box([0, 14, 0], [16, 16, 16], fm)
        m.box([3, 0, 3], [13, 14, 14], mt("recess"), only=("north", "west", "east"))
        for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
            m.box([x, 0, z], [x + 2, 14, z + 2], fm, skip=("up",))
        opening = ((4, 12), (3, 11))
        side_panel(m, half, True, opening, fm)
        louvres(m, True, opening)
        side_panel(m, half, False, opening, fm)
        louvres(m, False, opening)
        # back: panel, port panel with emissive cyan ports, cables plugged in and running down
        m.box([2, 0, 14], [14, 14, 15], fm, only=("south",))
        m.box([3, 5, 15], [13, 12, 15.5], {"*": mt("chassis"), "south": mr("port_panel")}, skip=("north",))
        for x in (3.5, 7, 10.5):
            m.box([x, 0, 15], [x + 2, 7, 17], mt("cable"), only=("south", "west", "east"))
            m.box([x - 0.25, 7, 15.5], [x + 2.25, 9.5, 17.25],
                  {"*": mt("chassis"), "south": mr("plug")}, skip=("north",))
            m.box([x + 0.5, 10, 15.5], [x + 1.5, 11, 15.75], led("port"), emit=15, skip=("north",))
        # front
        m.box([2, 0, 0], [3, 14, 1], mt("chassis"), skip=("up",))
        m.box([13, 0, 0], [14, 14, 1], mt("chassis"), skip=("up",))
        m.box([3, 13, 0], [13, 14, 1], mt("chassis"), skip=("up",))
        blades(m, ((0.5, 4.5), (5, 9), (9.5, 13.5)), (1.0, 2.0, 1.5), (3, 4, 5))
        m.box([3, 0, 0.5], [13, 13, 0.75], Tile("glass"), only=("north",))
        m.box([11, 0, -0.75], [12, 4, -0.25], mt("gold_l"))
        m.box([11.25, 3, -0.25], [11.75, 3.5, 0.5], mt("gold"), only=("up", "down", "west", "east"))
        # top: hologram projector (octagonal 10x10 emitter ring, emissive lens) + 4 antennas
        m.box([4, 16, 4], [12, 16.5, 12], mt("recess"), skip=("down",))
        gold, gold_l = mt("gold"), mt("gold_l")
        ring = {"*": gold, "up": gold_l}
        m.box([5, 16, 3], [11, 17.5, 4], ring, skip=("down",))
        m.box([5, 16, 12], [11, 17.5, 13], ring, skip=("down",))
        m.box([3, 16, 5], [4, 17.5, 11], ring, skip=("down",))
        m.box([12, 16, 5], [13, 17.5, 11], ring, skip=("down",))
        for (cx, cz, ang) in ((4.25, 4.25, -45), (11.75, 4.25, 45), (4.25, 11.75, 45), (11.75, 11.75, -45)):
            m.box([cx - 1.5, 16, cz - 0.5], [cx + 1.5, 17.5, cz + 0.5], ring,
                  rot=("y", ang, [cx, 16, cz]), skip=("down",))
        m.box([6, 16, 6], [10, 17, 10], led("lens"), emit=15, skip=("down",))
        m.box([6, 16, 6], [10, 17, 10], led("lens"), emit=15, skip=("down",), rot=("y", 45, [8, 16, 8]))
        m.box([7, 17, 7], [9, 17.25, 9], led("lens_core"), emit=15, skip=("down",))
        for (x, z, h) in ((1, 1, 2.0), (15, 1, 3.2), (1, 15, 2.6), (15, 15, 1.4)):
            m.box([x - 0.5, 16, z - 0.5], [x + 0.5, 16.5, z + 0.5], mt("chassis"), skip=("down",))
            m.box([x - 0.25, 16.5, z - 0.25], [x + 0.25, 16 + h, z + 0.25], mt("chassis"), skip=("down", "up"))
            m.box([x - 0.4, 16 + h, z - 0.4], [x + 0.4, 16 + h + 0.8, z + 0.4], led("tip"), emit=15)
    return m


def mainframe_fan():
    """Fan blades for the east-side fan. Rotates around the X axis through (13.5, 8, 8)."""
    m = Model(textures={"particle": f"{NS}:block/mainframe", "atlas": f"{NS}:block/mainframe"})
    m.box([13, 7, 7], [14, 9, 9], {"*": mt("chassis"), "east": mr("fan_hub")})
    # 4 blades = two crossing bars, each pitched 22.5 deg so the fan reads as a rotor
    m.box([13.25, 7.25, 4.25], [13.75, 8.75, 11.75], mt("grey_l"), rot=("x", 22.5, [13.5, 8, 8]))
    m.box([13.25, 4.25, 7.25], [13.75, 11.75, 8.75], mt("grey_l"), rot=("x", 22.5, [13.5, 8, 8]))
    return m


CLOUD = [          # front silhouette, top row first (10 x 6 px)
    "   ###    ",
    "  #####   ",
    "  ###### ##",
    " #########",
    "##########",
    " ######## ",
]


def cloud_voxels():
    """Pixel cloud (10x6x6): front silhouette extruded 6 deep, outer rim pixels only 4 deep."""
    vox = set()
    h = len(CLOUD)
    for j, row in enumerate(CLOUD):
        y = h - 1 - j
        for x, ch in enumerate(row[:10]):
            if ch != "#":
                continue
            rim = (x == 0 or x == 9 or row[x - 1:x] == " " or row[x + 1:x + 2] in (" ", "")
                   or j == 0 or CLOUD[j - 1][x:x + 1] in (" ", ""))
            for z in (range(1, 5) if rim else range(0, 6)):
                vox.add((x, y, z))
    return vox


def greedy_boxes(vox):
    """Merge voxels into boxes (x runs, then z, then y) to keep the element count low."""
    left = set(vox)
    boxes = []
    for (x, y, z) in sorted(vox, key=lambda p: (p[1], p[2], p[0])):
        if (x, y, z) not in left:
            continue
        x1 = x
        while (x1 + 1, y, z) in left:
            x1 += 1
        z1 = z
        while all((i, y, z1 + 1) in left for i in range(x, x1 + 1)):
            z1 += 1
        y1 = y
        while all((i, y1 + 1, k) in left for i in range(x, x1 + 1) for k in range(z, z1 + 1)):
            y1 += 1
        for i in range(x, x1 + 1):
            for j in range(y, y1 + 1):
                for k in range(z, z1 + 1):
                    left.discard((i, j, k))
        boxes.append(((x, y, z), (x1 + 1, y1 + 1, z1 + 1)))
    return boxes


HOLO_OFFSET = (3, 20.5, 5)


def mainframe_hologram():
    """Translucent cyan voxel cloud above the projector (upper-half coordinates)."""
    m = Model(textures={"particle": f"{NS}:block/mainframe_hologram",
                        "holo": f"{NS}:block/mainframe_hologram"})
    m.extra["render_type"] = "translucent"
    ox, oy, oz = HOLO_OFFSET
    for (a, b) in greedy_boxes(cloud_voxels()):
        m.box([a[0] + ox, a[1] + oy, a[2] + oz], [b[0] + ox, b[1] + oy, b[2] + oz], Tile("holo"),
              emit=15, cull=False)
    return m


def mainframe_item(lower, upper, fan):
    """Whole 2-block rack scaled to 50 % so it fits one item slot. UVs stay untouched."""
    m = Model(parent="minecraft:block/block", textures=dict(lower.textures))
    m.extra["render_type"] = "translucent"

    def add(el, dy):
        e = copy.deepcopy(el)
        s = 0.5
        tf = lambda p: [num(8 + (p[0] - 8) * s), num((p[1] + dy) * s), num(8 + (p[2] - 8) * s)]
        e["from"], e["to"] = tf(e["from"]), tf(e["to"])
        if "rotation" in e:
            e["rotation"]["origin"] = tf(e["rotation"]["origin"])
        for f in e["faces"].values():
            f.pop("cullface", None)
        m.elements.append(e)

    for el in lower.elements:
        add(el, 0)
    for el in fan.elements:
        add(el, 0)
    for el in upper.elements:
        # upper half sits one block higher before scaling
        add(el, 16)
    m.extra["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -0.5, 0], "scale": [0.95, 0.95, 0.95]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.9, 0.9, 0.9]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 1], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 1], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    }
    return m


# --------------------------------------------------------------------------- uplink
UA = "uplink"


def ut(name):
    return Tile("atlas", UA, name)


def ur(name):
    return Region("atlas", UA, name)


def st(name):
    return Region("status", None, name, size=16)


UPLINK_DISPLAY = {
    "gui": {"rotation": [30, 45, 0], "translation": [4.65, -2.35, 0], "scale": [1.2, 1.2, 1.2]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, -7], "scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}


def uplink_display():
    m = Model(parent="minecraft:block/block")
    m.extra["display"] = UPLINK_DISPLAY
    return m


# Slim "sensor clip" layout (model space = attached to the north face, +Z = away from the block).
# Depth budget: plate z 0..1, housing z 1..3, mast/dish/core end at z ~5.
DISH_PIVOT = (8, 8, 3.75)
CORE_PIVOT = (8, 8, 4.0)


def uplink_body(advanced):
    tex = f"{NS}:block/uplink_advanced" if advanced else f"{NS}:block/uplink"
    m = Model(parent=f"{NS}:block/uplink_display", textures={
        "particle": tex, "atlas": tex, "status": f"{NS}:block/uplink_status_online"})
    body, body_l = ut("body"), ut("body_l")
    # mounting plate 8x8x1 against the block (north = attached side)
    m.box([4, 4, 0], [12, 12, 1], {"*": body_l, "south": ut("plate"), "north": ut("plate"), "up": body})
    for (x, y) in ((4.75, 4.75), (10.75, 4.75), (4.75, 10.75), (10.75, 10.75)):
        m.box([x, y, 1], [x + 0.5, y + 0.5, 1.25], ur("screw"), skip=("north",))
    # housing 4x4x2 with a dark front frame + cyan window
    m.box([6, 6, 1], [10, 10, 3], {"*": body_l, "south": ur("step_front"), "up": body}, skip=("north",))
    # thin cooling fins, 2 per side
    for y in (6.75, 8.75):
        m.box([5.5, y, 1.25], [6, y + 0.5, 2.75], body, skip=("east",))
        m.box([10, y, 1.25], [10.5, y + 0.5, 2.75], body, skip=("west",))
    # small status crystal on top (about 1.2 px, diamond seen from the front), emissive
    m.box([7.4, 10.25, 1.5], [8.6, 11.45, 2.7], st("crystal"), emit=15, rot=("z", 45, [8, 10.85, 2.1]))
    if not advanced:
        # thin, short mast (the dish is the separate, rotating uplink_dish model)
        m.box([7.75, 7.75, 3], [8.25, 8.25, 3.6], body, skip=("north",))
    else:
        # gold cradle: 4 short prongs splayed 22.5 deg with emissive cyan tips; core = separate model
        prong, tip = ur("prong"), ut("ring")
        for (frm, to, tfrm, tto, rot) in (
            ([7.75, 9.25, 3], [8.25, 9.75, 4.25], [7.7, 9.2, 4.25], [8.3, 9.8, 4.75], ("x", -22.5, [8, 9.5, 3])),
            ([7.75, 6.25, 3], [8.25, 6.75, 4.25], [7.7, 6.2, 4.25], [8.3, 6.8, 4.75], ("x", 22.5, [8, 6.5, 3])),
            ([9.25, 7.75, 3], [9.75, 8.25, 4.25], [9.2, 7.7, 4.25], [9.8, 8.3, 4.75], ("y", 22.5, [9.5, 8, 3])),
            ([6.25, 7.75, 3], [6.75, 8.25, 4.25], [6.2, 7.7, 4.25], [6.8, 8.3, 4.75], ("y", -22.5, [6.5, 8, 3])),
        ):
            m.box(frm, to, prong, rot=rot, skip=("north",))
            m.box(tfrm, tto, tip, rot=rot, emit=15)
    return m


def uplink_dish():
    """4 px dish (rounded rim + deeper cyan centre), tilted 22.5 deg; spins around the mast
    (Z axis through x=8, y=8). Pivot DISH_PIVOT."""
    m = Model(parent=f"{NS}:block/uplink_display",
              textures={"particle": f"{NS}:block/uplink", "atlas": f"{NS}:block/uplink"})
    tilt = ("x", -22.5, list(DISH_PIVOT))
    body, body_l, cyan, dark = ut("body"), ut("body_l"), ut("cyan_d"), ut("chassis")
    m.box([7, 7, 3.5], [9, 9, 3.9], {"*": cyan, "north": dark}, rot=tilt)
    for (a, b) in (([7, 9, 3.75], [9, 10, 4.25]), ([7, 6, 3.75], [9, 7, 4.25]),
                   ([6, 7, 3.75], [7, 9, 4.25]), ([9, 7, 3.75], [10, 9, 4.25])):
        m.box(a, b, {"*": body, "south": body_l}, rot=tilt)
    m.box([7.85, 7.85, 3.9], [8.15, 8.15, 4.6], dark, rot=tilt, skip=("north",))
    m.box([7.65, 7.65, 4.6], [8.35, 8.35, 4.95], ur("feed_tip"), rot=tilt, emit=15)
    return m


def uplink_core():
    """Floating ender core: three 45 deg-rotated 1.5 px cubes + cyan heart, emissive.
    Rotates around the Z axis through (8, 8); centre CORE_PIVOT."""
    tex = f"{NS}:block/uplink_advanced"
    m = Model(parent=f"{NS}:block/uplink_display", textures={"particle": tex, "atlas": tex})
    c = CORE_PIVOT
    face = ur("core_face")
    h = 0.75
    a, b = [c[0] - h, c[1] - h, c[2] - h], [c[0] + h, c[1] + h, c[2] + h]
    for axis in ("z", "x", "y"):
        m.box(a, b, face, rot=(axis, 45, list(c)), emit=15)
    m.box([c[0] - 0.5, c[1] - 0.5, c[2] - 0.5], [c[0] + 0.5, c[1] + 0.5, c[2] + 0.5], ur("core_inner"), emit=15)
    return m


def uplink_direct_ring():
    """Emissive cyan ring hugging the 8x8 mounting plate (shown when direct=true)."""
    tex = f"{NS}:block/uplink"
    m = Model(parent=f"{NS}:block/uplink_display", textures={"particle": tex, "atlas": tex})
    ring = ut("ring")
    for (a, b) in (([3.5, 3.5, 0], [12.5, 4, 0.75]), ([3.5, 12, 0], [12.5, 12.5, 0.75]),
                   ([3.5, 4, 0], [4, 12, 0.75]), ([12, 4, 0], [12.5, 12, 0.75])):
        m.box(a, b, ring, emit=15, skip=("north",))
    return m


# --------------------------------------------------------------------------- writing
written = []


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    written.append(rel)


def variant_child(parent, textures):
    return {"parent": parent, "textures": textures}


HORIZONTAL = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
ALL6 = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
        "up": {"x": 270}, "down": {"x": 90}}


def main():
    lower, upper, fan = mainframe_half("lower"), mainframe_half("upper"), mainframe_fan()
    holo = mainframe_hologram()
    models = {
        "block/mainframe_lower": lower, "block/mainframe_upper": upper,
        "block/mainframe_fan": fan, "block/mainframe_hologram": holo,
        "item/mainframe_item": mainframe_item(lower, upper, fan),
        "block/uplink_display": uplink_display(),
        "block/uplink": uplink_body(False), "block/uplink_dish": uplink_dish(),
        "block/uplink_advanced": uplink_body(True), "block/uplink_advanced_core": uplink_core(),
        "block/uplink_direct_ring": uplink_direct_ring(),
    }
    counts = {}
    for name, m in models.items():
        write(f"models/{name}.json", m.json())
        counts[name] = len(m.elements)

    # status variants: children that only swap the status texture
    for s in STATES:
        for half in ("lower", "upper"):
            write(f"models/block/mainframe_{half}_{s}.json",
                  variant_child(f"{NS}:block/mainframe_{half}", {"leds": f"{NS}:block/mainframe_leds_{s}"}))
        write(f"models/block/uplink_{s}.json",
              variant_child(f"{NS}:block/uplink", {"status": f"{NS}:block/uplink_status_{s}"}))
        write(f"models/block/uplink_advanced_{s}.json",
              variant_child(f"{NS}:block/uplink_advanced", {"status": f"{NS}:block/uplink_status_{s}"}))

    # flat items
    for it in ("cloud_core", "cloud_link_card"):
        write(f"models/item/{it}.json", {"parent": "minecraft:item/generated",
                                         "textures": {"layer0": f"{NS}:item/{it}"}})
        write(f"items/{it}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{it}"}})

    # blockstates
    variants = {}
    for f, r in HORIZONTAL.items():
        for half in ("lower", "upper"):
            for s in STATES:
                variants[f"facing={f},half={half},status={s}"] = {"model": f"{NS}:block/mainframe_{half}_{s}", **r}
    write("blockstates/cloud_mainframe.json", {"variants": variants})

    for block, base in (("cloud_uplink", "uplink"), ("advanced_cloud_uplink", "uplink_advanced")):
        parts = []
        for f, r in ALL6.items():
            for s in STATES:
                parts.append({"when": {"facing": f, "status": s}, "apply": {"model": f"{NS}:block/{base}_{s}", **r}})
        for f, r in ALL6.items():
            parts.append({"when": {"facing": f, "direct": "true"}, "apply": {"model": f"{NS}:block/uplink_direct_ring", **r}})
        write(f"blockstates/{block}.json", {"multipart": parts})

    # item model definitions (26.1 items/*.json)
    write("items/cloud_mainframe.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/mainframe_item"}})
    write("items/cloud_uplink.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{NS}:block/uplink_online"},
        {"type": "minecraft:model", "model": f"{NS}:block/uplink_dish"}]}})
    write("items/advanced_cloud_uplink.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{NS}:block/uplink_advanced_online"},
        {"type": "minecraft:model", "model": f"{NS}:block/uplink_advanced_core"}]}})

    write_docs(counts)
    problems = validate()
    for k, v in counts.items():
        print(f"{v:4d}  {k}")
    print(len(written), "files written")
    if problems:
        print("VALIDATION FAILED:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("validation ok: json, parents, textures, rotations, bounds, blockstate/item references")


def validate():
    """Quality gate: parse everything back and check references and vanilla format limits."""
    problems = []
    data = {}
    for sub in ("models", "blockstates", "items"):
        for dp, _, fs in os.walk(os.path.join(ASSETS, sub)):
            for f in fs:
                if f.endswith(".json"):
                    path = os.path.join(dp, f)
                    try:
                        with open(path) as fh:
                            data[os.path.relpath(path, ASSETS)] = json.load(fh)
                    except ValueError as e:
                        problems.append(f"{path}: invalid json ({e})")

    def model_path(rid):
        ns, p = rid.split(":", 1)
        return f"models/{p}.json" if ns == NS else None

    def tex_exists(rid):
        if isinstance(rid, dict):
            rid = rid.get("sprite", "")
        ns, p = rid.split(":", 1)
        return ns == NS and os.path.exists(os.path.join(ASSETS, "textures", p + ".png"))

    for rel, m in data.items():
        if not rel.startswith("models/"):
            continue
        # resolve texture variables through the parent chain
        tex, chain, cur = {}, [], m
        while cur is not None:
            chain.append(cur)
            par = cur.get("parent")
            mp = model_path(par) if par and ":" in par and par.startswith(NS) else None
            if par and par.startswith(NS + ":") and mp not in data:
                problems.append(f"{rel}: missing parent {par}")
            cur = data.get(mp) if mp else None
        for c in reversed(chain):
            tex.update(c.get("textures", {}))
        for k, v in tex.items():
            if isinstance(v, str) and v.startswith("#"):
                continue
            if isinstance(v, (str, dict)) and str(v).startswith(("cccloud:", "{")) and not tex_exists(v):
                problems.append(f"{rel}: texture {k} -> {v} does not exist")
        for i, el in enumerate(m.get("elements", [])):
            for v in el["from"] + el["to"]:
                if not -16 <= v <= 32:
                    problems.append(f"{rel} element {i}: coordinate {v} outside -16..32")
            r = el.get("rotation")
            if r and (r.get("angle") not in (-45, -22.5, 0, 22.5, 45) or r.get("axis") not in ("x", "y", "z")):
                problems.append(f"{rel} element {i}: illegal rotation {r}")
            for d, f in el["faces"].items():
                if any(not 0 <= u <= 16 for u in f["uv"]):
                    problems.append(f"{rel} element {i} {d}: uv {f['uv']} outside 0..16")
                ref = f["texture"]
                seen = 0
                while isinstance(ref, str) and ref.startswith("#") and seen < 10:
                    ref = tex.get(ref[1:])
                    seen += 1
                if ref is None or not tex_exists(ref):
                    problems.append(f"{rel} element {i} {d}: unresolved texture {f['texture']}")
        if len(m.get("elements", [])) > 130:
            problems.append(f"{rel}: {len(m['elements'])} elements (> ~120)")

    def check_model_ref(rel, rid):
        if model_path(rid) not in data:
            problems.append(f"{rel}: references missing model {rid}")

    for rel, b in data.items():
        if rel.startswith("blockstates/"):
            for v in b.get("variants", {}).values():
                check_model_ref(rel, v["model"])
            for part in b.get("multipart", []):
                check_model_ref(rel, part["apply"]["model"])
        if rel.startswith("items/"):
            mm = b["model"]
            for sub in mm.get("models", [mm]):
                check_model_ref(rel, sub["model"])
    # blockstate property coverage
    ms = data.get("blockstates/cloud_mainframe.json", {}).get("variants", {})
    if len(ms) != 4 * 2 * 3:
        problems.append(f"cloud_mainframe: {len(ms)} variants, expected 24")
    return problems


PIVOTS = """\
| Model | Rendered for | Pivot (px, model space) | Axis | Suggested animation |
|---|---|---|---|---|
| `block/mainframe_fan` | **lower** half | (13.5, 8, 8) | X | spin, ~1 rev/s online, slow down to 0 when offline |
| `block/mainframe_hologram` | **upper** half | (8, 23.5, 8) | Y | rotate ~20 deg/s, bob +/-0.5 px (sin), flicker alpha when connecting/offline, hide when offline |
| `block/uplink_dish` | normal uplink | (8, 8, 3.75) | Z (the mast) | slow sweep, ~0.25 rev/s; dish is pre-tilted 22.5 deg so it scans like a radar |
| `block/uplink_advanced_core` | advanced uplink | (8, 8, 4.0) | Z (+ optional Y wobble) | rotate ~45 deg/s, bob +/-0.25 px along Z (keep it inside the 5 px depth) |
"""


def write_docs(counts):
    rows = "\n".join(f"| `{k}` | {v} |" for k, v in counts.items())
    readme = f"""# cccloud models

Generated by `tools/gen_models.py` (do not edit by hand; edit the generator and re-run).

## Moving parts (rendered by BlockEntityRenderers, NOT referenced from blockstates)

All coordinates are in model pixels (1/16 block) in the model's own space, i.e. **before** the
blockstate rotation. Render order in the BER: translate to block, apply the same facing rotation
the blockstate uses (around the block centre 8,8,8), then rotate the part around its pivot.

{PIVOTS}
Uplink facing rotations (same as the CC modem): north = none, south = y180, east = y90,
west = y270, up = x270, down = x90. Model space is "attached to the north face"; +Z points away
from the block the uplink sits on.
Mainframe: north = none, east = y90, south = y180, west = y270 (front with the glass door = north).

## Element counts

| Model | Elements |
|---|---|
{rows}
"""
    path = os.path.join(ASSETS, "models", "README.md")
    with open(path, "w") as f:
        f.write(readme)
    written.append("models/README.md")

    doc = f"""# CC: Cloud models: technical notes

Generated by `tools/gen_models.py` + `tools/gen_textures.py`. Source of truth for UVs is
`LAYOUTS` in `gen_textures.py`.

{readme.split('## Moving parts', 1)[1].join(['## Moving parts', ''])}

## Render types / translucency (MC 26.1)

In 26.1 the chunk layer is chosen **per quad** from the texture: `FaceBakery` calls
`SpriteContents.computeTransparency(uv rect)` and `ChunkSectionLayer.byTransparency(...)`
picks SOLID / CUTOUT / TRANSLUCENT. So the smoked glass quads (alpha 118 in
`mainframe_glass.png`) become TRANSLUCENT automatically, everything else stays SOLID. A texture
entry can also force it: `"glass": {{"sprite": "cccloud:block/mainframe_glass", "force_translucent": true}}`.
We additionally set `"render_type": "translucent"` on models with glass, the same key CC: Tweaked
26.1 keeps in its models (`turtle_base.json`); it is ignored where unsupported.

Models with translucent parts:
- `block/mainframe_lower`, `block/mainframe_upper` (door glass), `item/mainframe_item`.
- `block/mainframe_hologram` (BER): render with a translucent, full-bright type, e.g.
  `RenderTypes.entityTranslucentEmissive(atlas)` / `Sheets.translucentBlockItemSheet()` with packed
  light 0xF000F0, so it glows and can be faded (alpha) for the flicker.
- Fan, dish, core: opaque (`Sheets.cutoutBlockSheet()` / solid is fine). The core and dish tip use
  `light_emission: 15` on their elements; when rendered through a BER pass full-bright light
  (0xF000F0) for the core, or rely on the element emission if you bake via the block renderer.

## Emissive parts

`light_emission` (0..15) per element is supported by the 26.1 deserializer
(`CuboidModelElement.Deserializer`, field `light_emission`, same as CC's `computer_on.json`).
Used on: blade LED bars, projector lens + core, back ports, antenna tips (mainframe), status
crystal, dish feed tip, cradle tips, ender core, direct ring (uplinks), hologram voxels.

## Status textures

`mainframe_leds_{{online,connecting,offline}}.png` and `uplink_status_{{...}}.png` share one 16x16
layout (`STATUS_REGIONS` in gen_textures.py). The `_online/_connecting/_offline` child models only
override that texture, so geometry is defined once.

## Item models

- `items/cloud_mainframe.json` -> `models/item/mainframe_item.json`: both halves + fan merged and
  scaled to 50 % (UVs unchanged), hologram omitted.
- `items/cloud_uplink.json` / `advanced_cloud_uplink.json`: `minecraft:composite` of the online body
  + dish / core. All uplink models inherit the same `display` from `block/uplink_display`.
- `cloud_core`, `cloud_link_card`: flat `item/generated`.

## Particle

`textures/particle/data_packet.png` (4x4). The particle description file
`assets/cccloud/particles/data_packet.json` (`{{"textures": ["cccloud:data_packet"]}}`) belongs to the
particle registration and is not generated here.
"""
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    with open(os.path.join(ROOT, "docs", "MODELS.md"), "w") as f:
        f.write(doc)


if __name__ == "__main__":
    main()
