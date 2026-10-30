#!/usr/bin/env python3
"""Deterministic model / blockstate / item-definition generator for CC: Cloud (`cccloud`).

    python3 tools/gen_models.py

Writes (all under src/main/resources/assets/cccloud/):
  models/block/*.json, models/item/*.json, blockstates/*.json, items/*.json, docs/MODELS_TABLE.md
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
        m.box([14.3, 4, 7.5], [14.7, 12, 8.5], mt("chassis"))
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
        for (cx, cz, ang) in ((4.25, 4.25, 45), (11.75, 4.25, -45), (4.25, 11.75, -45), (11.75, 11.75, 45)):
            m.box([cx - 1.5, 16, cz - 0.5], [cx + 1.5, 17.4, cz + 0.5], ring,
                  rot=("y", ang, [cx, 16, cz]), skip=("down",))
        m.box([6, 16, 6], [10, 17, 10], led("lens"), emit=15, skip=("down",))
        m.box([6, 16, 6], [10, 16.9, 10], led("lens"), emit=15, skip=("down",), rot=("y", 45, [8, 16, 8]))
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
    m.box([13.3, 4.25, 7.25], [13.7, 11.75, 8.75], mt("grey_l"), rot=("x", 22.5, [13.5, 8, 8]))
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
    # flat plate like the CC modem; gui translation re-centres the plate (model z 0..2.25)
    "gui": {"rotation": [30, 45, 0], "translation": [3.66, -1.83, 0], "scale": [0.75, 0.75, 0.75]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def uplink_display():
    m = Model(parent="minecraft:block/block")
    m.extra["display"] = UPLINK_DISPLAY
    return m


# Flat plate layout (CC modem language). Model space = attached to the north face, +Z = away
# from the block. Depth budget: plate z 0..2, port floor at z 1, LED strip proud to z 2.25.
SPIN_PIVOT = (8, 8, 1.375)   # ring (normal) and eye core (advanced) turn in place around Z


def uplink_body(advanced):
    tex = f"{NS}:block/uplink_advanced" if advanced else f"{NS}:block/uplink"
    m = Model(parent=f"{NS}:block/uplink_display", textures={
        "particle": tex, "atlas": tex, "status": f"{NS}:block/uplink_status_online"})
    plate, body_l, wall = ut("plate"), ut("body_l"), ut("chassis")
    # back layer 14x14x1; its front face inside the port is the port floor (recess + hub)
    m.box([1, 1, 0], [15, 15, 1], {"*": body_l, "south": plate, "north": plate})
    # front layer 14x14x1 with a round 6x6 port (rows 4,6,6,6,6,4 wide)
    f = {"*": body_l, "south": plate}
    inner = {"*": wall, "south": plate}
    m.box([1, 1, 1], [15, 5, 2], {**f, "up": wall}, skip=("north",))
    m.box([1, 11, 1], [15, 15, 2], {**f, "down": wall}, skip=("north",))
    m.box([1, 5, 1], [5, 11, 2], {**f, "east": wall}, skip=("north", "up", "down"))
    m.box([11, 5, 1], [15, 11, 2], {**f, "west": wall}, skip=("north", "up", "down"))
    for (x0, x1, y0, y1) in ((5, 6, 10, 11), (10, 11, 10, 11), (5, 6, 5, 6), (10, 11, 5, 6)):
        m.box([x0, y0, 1], [x1, y1, 2], inner, skip=("north",))
    # status LED strip along the bottom edge, 0.25 proud, emissive
    m.box([4, 2, 2], [12, 3, 2.25], st("strip"), emit=15, skip=("north",))
    return m


def uplink_ring():
    """Spinner ring inside the port: 4 segments (2 lit cyan) with gaps at the corners, so the
    rotation reads. Turns in place around Z through SPIN_PIVOT; stays inside the port (z 1..1.75)."""
    m = Model(parent=f"{NS}:block/uplink_display",
              textures={"particle": f"{NS}:block/uplink", "atlas": f"{NS}:block/uplink"})
    lit, grey_ = ur("spinner"), ur("spinner_grey")
    side = ut("cyan_d")
    z0, z1 = 1, 1.75
    m.box([7, 9.5, z0], [9, 10.25, z1], {"*": side, "south": lit}, emit=15, skip=("north",))
    m.box([7, 5.75, z0], [9, 6.5, z1], {"*": side, "south": lit}, emit=15, skip=("north",))
    m.box([5.75, 7, z0], [6.5, 9, z1], {"*": ut("body_l"), "south": grey_}, skip=("north",))
    m.box([9.5, 7, z0], [10.25, 9, z1], {"*": ut("body_l"), "south": grey_}, skip=("north",))
    return m


def uplink_core():
    """Flush ender-eye core in the port (two 3x3 discs, one turned 45 deg = octagon), emissive.
    Turns in place around Z through SPIN_PIVOT."""
    tex = f"{NS}:block/uplink_advanced"
    m = Model(parent=f"{NS}:block/uplink_display", textures={"particle": tex, "atlas": tex})
    eye, rim = ur("eye"), ut("core")
    m.box([6.5, 6.5, 1], [9.5, 9.5, 1.75], {"*": rim, "south": eye}, emit=15, skip=("north",))
    m.box([6.5, 6.5, 1], [9.5, 9.5, 1.6], {"*": rim, "south": eye}, emit=15, skip=("north",),
          rot=("z", 45, list(SPIN_PIVOT)))
    return m


def uplink_direct_ring():
    """Emissive cyan ring hugging the plate edge, flush (same depth as the plate)."""
    tex = f"{NS}:block/uplink"
    m = Model(parent=f"{NS}:block/uplink_display", textures={"particle": tex, "atlas": tex})
    ring = ut("ring")
    for (a, b) in (([0.5, 0.5, 0], [15.5, 1, 2]), ([0.5, 15, 0], [15.5, 15.5, 2]),
                   ([0.5, 1, 0], [1, 15, 2]), ([15, 1, 0], [15.5, 15, 2])):
        m.box(a, b, ring, emit=15, skip=("north",))
    return m


# Compact uplink ("puck") for side faces that cannot carry the 14x14 plate: slab sides, tray-height
# machines (solar panels), carpet-height blocks. Blockstate property `mount` (the Java side computes
# it from the attached block's outline shape, see CloudUplinkBlock / UplinkMount):
#   full                    the 14x14 plate (above); the only mount on top / bottom faces
#   bottom|low|center|high|top   8x6 puck flush on the face, bottom edge at model y COMPACT_Y
# Puck: x 4..12, y v..v+6, z 0..2, LED 4x1 proud to z 2.25. The shapes in UplinkMount (Java) use
# exactly these boxes.
MOUNTS = ("full", "bottom", "low", "center", "high", "top")
COMPACT_Y = {"bottom": 0, "low": 1, "center": 5, "high": 9, "top": 10}
UC = "uplink_compact"


def uct(name):
    return Tile("atlas", UC, name)


def uplink_compact(mount):
    """8x6x2 puck (front = the compact atlas' 8x6 region), 4 px status LED."""
    v = COMPACT_Y[mount]
    tex = f"{NS}:block/uplink_compact"
    m = Model(parent=f"{NS}:block/uplink_display", textures={
        "particle": tex, "atlas": tex, "status": f"{NS}:block/uplink_status_online"})
    m.box([4, v, 0], [12, v + 6, 2], {"*": uct("body_l"), "south": Region("atlas", UC, "front"),
                                      "north": uct("body")})
    m.box([6, v + 1, 2], [10, v + 2, 2.25], st("strip4"), emit=15, skip=("north",))
    return m


def uplink_compact_ring(mount):
    """Direct-mode ring around the puck: like the plate's ring, 0.5 px wide, flush, emissive."""
    v = COMPACT_Y[mount]
    tex = f"{NS}:block/uplink"
    m = Model(parent=f"{NS}:block/uplink_display", textures={"particle": tex, "atlas": tex})
    for (a, b) in (([3.5, v - 0.5, 0], [12.5, v, 2]), ([3.5, v + 6, 0], [12.5, v + 6.5, 2]),
                   ([3.5, v, 0], [4, v + 6, 2]), ([12, v, 0], [12.5, v + 6, 2])):
        m.box(a, b, ut("ring"), emit=15, skip=("north",))
    return m


# Cloud Probe: the plate (one 14x14x2 box, front = designed plate tile), status LED like the uplink, and a lens that
# stands proud: 6x6 gold rim (z 2..3) with a 4x4 emissive glass (z 2..3.5). No moving part. Shape in CloudProbeBlock:
# plate 1..15 x 1..15 x 0..2.25 + lens 5..11 x 5..11 x 0..3.5.
PA = "probe"


def probe_body():
    tex = f"{NS}:block/cloud_probe"
    m = Model(parent=f"{NS}:block/uplink_display", textures={
        "particle": tex, "atlas": tex, "status": f"{NS}:block/uplink_status_online"})
    plate = Tile("atlas", PA, "plate")
    m.box([1, 1, 0], [15, 15, 2], {"*": Tile("atlas", PA, "body_l"), "south": plate, "north": plate})
    m.box([4, 2, 2], [12, 3, 2.25], st("strip"), emit=15, skip=("north",))
    m.box([5, 5, 2], [11, 11, 3], {"*": Tile("atlas", PA, "gold"), "south": Region("atlas", PA, "ring")}, skip=("north",))
    m.box([6, 6, 2], [10, 10, 3.5], {"*": Tile("atlas", PA, "glass_side"), "south": Region("atlas", PA, "glass")},
          emit=15, skip=("north",))
    return m


def uplink_body_model(advanced, mount, status):
    """Model id the blockstate uses for a tier / mount / status."""
    if mount == "full":
        return f"{NS}:block/{'uplink_advanced' if advanced else 'uplink'}_{status}"
    return f"{NS}:block/{'uplink_advanced_compact' if advanced else 'uplink_compact'}_{mount}_{status}"


def uplink_ring_model(mount):
    return f"{NS}:block/uplink_direct_ring" if mount == "full" else f"{NS}:block/uplink_compact_ring_{mount}"


# --------------------------------------------------------------------------- cloud bridges (v0.2)
def bridge(kind):
    """Full-block ME / RS cloud bridge (front = north). CC casing, 2 px deep 8x8 port with a
    glowing cloud emblem, status strip under it, glowing cable-socket pins (small proud elements)
    on the other 5 faces."""
    tex = f"{NS}:block/{kind}_cloud_bridge"
    m = Model(textures={"particle": tex, "atlas": tex, "status": f"{NS}:block/cloud_bridge_status_online"})
    T = lambda n: Tile("atlas", "bridge", n)
    front, side, wall, pins = T("front"), T("side"), T("wall"), T("pins")
    bs = lambda n: Region("status", None, n, size=16, rect=gt.BRIDGE_STATUS_REGIONS[n])
    # casing behind the port (its north face at z=2 is the port floor)
    m.box([0, 0, 2], [16, 16, 16], {"*": side, "north": front})
    # front bezel, 2 px deep, around the 8x8 port
    outer = {"*": side, "north": front}
    m.box([0, 0, 0], [16, 4, 2], {**outer, "up": wall}, skip=("south",))
    m.box([0, 12, 0], [16, 16, 2], {**outer, "down": wall}, skip=("south",))
    m.box([0, 4, 0], [4, 12, 2], {**outer, "east": wall}, skip=("south", "up", "down"))
    m.box([12, 4, 0], [16, 12, 2], {**outer, "west": wall}, skip=("south", "up", "down"))
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):   # cut port corners
        m.box([x, y, 0], [x + 1, y + 1, 2], {"*": wall, "north": front}, skip=("south",))
    # glowing cloud emblem inside the port, status strip below it
    m.box([5, 6, 1.75], [11, 10, 2], bs("cloud"), emit=15, only=("north",))
    m.box([4, 2, -0.25], [12, 3, 0], bs("strip"), emit=15, skip=("south",))
    # emissive socket pins: small real 2x2 elements standing 0.1 px proud of the 5 connection faces
    # (a full-face overlay over the split casing/bezel faces z-fought in game)
    px, py = gt.tile_origin("bridge", "pins")
    pin = Region("atlas", "bridge", None, rect=(px + 7, py + 7, 2, 2))
    e = 0.1
    for (frm, to, d) in (([7, 7, 16], [9, 9, 16 + e], "south"), ([-e, 7, 7], [0, 9, 9], "west"),
                         ([16, 7, 7], [16 + e, 9, 9], "east"), ([7, 16, 7], [9, 16 + e, 9], "up"),
                         ([7, -e, 7], [9, 0, 9], "down")):
        el = m.box(frm, to, pin, emit=15, skip=({"south": "north", "west": "east", "east": "west",
                                                  "up": "down", "down": "up"}[d],))
        el["faces"][d]["cullface"] = d   # hidden like the face below when a block sits against it
    return m


# --------------------------------------------------------------------------- relays (v0.4)
RELAY_TEX = {"cloud_relay": "relay", "advanced_cloud_relay": "relay_advanced", "ender_relay": "relay_ender"}
RING_PIVOT = (8, 13, 8)        # emitter ring: rotates around Y
ENDER_CORE_PIVOT = (8, 11.5, 8)  # ender eye core: rotates around Y (may bob +/-0.5 px)
RELAY_STATES = ("online", "connecting", "offline", "no_power")


def _relay_parts(block):
    tex = f"{NS}:block/{RELAY_TEX[block]}"
    T = lambda n: Tile("atlas", "relay", n)
    return tex, T


def relay_base(block):
    """CC casing base 12x6x12, stepped plinth, status LED strip on all 4 sides (no facing),
    then either a thin mast + antenna tip (relays) or a gold cradle (ender relay)."""
    tex, T = _relay_parts(block)
    m = Model(textures={"particle": tex, "atlas": tex, "status": f"{NS}:block/relay_status_online"})
    rs = lambda n: Region("status", None, n, size=16, rect=gt.RELAY_STATUS_REGIONS[n])
    m.box([2, 0, 2], [14, 6, 14], {"*": T("side"), "up": T("top"), "down": T("dark")})
    m.box([4, 6, 4], [12, 7, 12], {"*": T("body_l"), "up": T("top")}, skip=("down",))
    strip = rs("strip")
    m.box([5, 4, 1.9], [11, 5, 2], strip, emit=15, skip=("south",))
    m.box([5, 4, 14], [11, 5, 14.1], strip, emit=15, skip=("north",))
    m.box([1.9, 4, 5], [2, 5, 11], strip, emit=15, skip=("east",))
    m.box([14, 4, 5], [14.1, 5, 11], strip, emit=15, skip=("west",))
    if block != "ender_relay":
        m.box([7.5, 7, 7.5], [8.5, 12.6, 8.5], T("dark"), skip=("down",))
        m.box([7, 9, 7], [9, 9.5, 9], T("body"))
        m.box([7.75, 13.4, 7.75], [8.25, 15.2, 8.25], T("dark"), skip=("down",))
        m.box([7.6, 15.2, 7.6], [8.4, 16, 8.4], rs("tip"), emit=15)
    else:
        # 4 gold prongs splayed 22.5 deg outward, emissive cyan tips; the core floats between them
        for (frm, to, tf, tt, rot) in (
            ([7.6, 7, 4.1], [8.4, 12, 4.9], [7.5, 12, 4], [8.5, 12.8, 5], ("x", -22.5, [8, 7, 4.5])),
            ([7.6, 7, 11.1], [8.4, 12, 11.9], [7.5, 12, 11], [8.5, 12.8, 12], ("x", 22.5, [8, 7, 11.5])),
            ([4.1, 7, 7.6], [4.9, 12, 8.4], [4, 12, 7.5], [5, 12.8, 8.5], ("z", 22.5, [4.5, 7, 8])),
            ([11.1, 7, 7.6], [11.9, 12, 8.4], [11, 12, 7.5], [12, 12.8, 8.5], ("z", -22.5, [11.5, 7, 8])),
        ):
            m.box(frm, to, T("ring"), rot=rot, skip=("down",))
            m.box(tf, tt, T("beacon"), rot=rot, emit=15)
    return m


def relay_ring(block):
    """Slim octagonal emitter ring with glowing beacons, hub and two spokes. Rotates around Y
    through RING_PIVOT. Normal: 10 px wide, 2 beacons; advanced: 12 px wide, 4 beacons."""
    tex, T = _relay_parts(block)
    m = Model(textures={"particle": tex, "atlas": tex})
    h = 5 if block == "cloud_relay" else 6
    y0, y1 = 12.5, 13.5
    ring, beacon = {"*": T("ring"), "up": T("body_l")}, T("beacon")
    c = 8
    m.box([c - h + 2, y0, c - h], [c + h - 2, y1, c - h + 1], ring)
    m.box([c - h + 2, y0, c + h - 1], [c + h - 2, y1, c + h], ring)
    m.box([c - h, y0, c - h + 2], [c - h + 1, y1, c + h - 2], ring)
    m.box([c + h - 1, y0, c - h + 2], [c + h, y1, c + h - 2], ring)
    k = c - h + 1.25
    for (cx, cz, ang) in ((k, k, 45), (16 - k, k, -45), (k, 16 - k, -45), (16 - k, 16 - k, 45)):
        m.box([cx - 1.5, 12.6, cz - 0.5], [cx + 1.5, 13.4, cz + 0.5], ring, rot=("y", ang, [cx, 13, cz]))
    beacons = [([7.5, 12.6, c - h - 0.3], [8.5, 13.4, c - h], "south"),
               ([7.5, 12.6, c + h], [8.5, 13.4, c + h + 0.3], "north")]
    if block != "cloud_relay":
        beacons += [([c - h - 0.3, 12.6, 7.5], [c - h, 13.4, 8.5], "east"),
                    ([c + h, 12.6, 7.5], [c + h + 0.3, 13.4, 8.5], "west")]
    for (a, b, inner) in beacons:
        m.box(a, b, beacon, emit=15, skip=(inner,))
    m.box([7.4, 12.6, 7.4], [8.6, 13.4, 8.6], T("dark"))
    m.box([c - h + 1, 12.8, 7.8], [c + h - 1, 13.2, 8.2], T("dark"), skip=("west", "east"))
    m.box([7.85, 12.85, c - h + 1], [8.15, 13.15, c + h - 1], T("dark"), skip=("north", "south"))
    return m


def ender_core():
    """Big floating ender eye: three 4 px cubes turned 45 deg on X/Y/Z + cyan heart, emissive.
    Rotates around Y through ENDER_CORE_PIVOT."""
    tex, T = _relay_parts("ender_relay")
    m = Model(textures={"particle": tex, "atlas": tex})
    cx, cy, cz = ENDER_CORE_PIVOT
    eye = Region("atlas", "relay", "eye")
    h = 2
    for axis in ("x", "y", "z"):
        m.box([cx - h, cy - h, cz - h], [cx + h, cy + h, cz + h], eye, emit=15, rot=(axis, 45, [cx, cy, cz]))
    m.box([cx - 1.25, cy - 1.25, cz - 1.25], [cx + 1.25, cy + 1.25, cz + 1.25], T("beacon"), emit=15)
    return m


# --------------------------------------------------------------------------- v0.7: cloud display + items
DISPLAY_ORIENT = {"north": 0, "down": 90, "up": 270}   # same property scheme as CC monitors


def display_model(state):
    """Full cube; front = north. Face tiles chosen by the connection state (see gen_textures.gen_display)."""
    tiles = gt.display_tiles()
    side = "".join(k for k in "ud" if k in state) or "none"
    top = "".join(k for k in "lr" if k in state) or "none"
    if state == "none":
        side, top = "none", "none"

    def reg(kind, st):
        c, r = tiles[(kind, st)]
        return Region("atlas", None, None, size=128, rect=(c * 16, r * 16, 16, 16))
    m = Model(textures={"particle": f"{NS}:block/display", "atlas": f"{NS}:block/display"})
    m.box([0, 0, 0], [16, 16, 16], {"north": reg("front", state), "south": reg("back", state),
                                    "east": reg("side", side), "west": reg("side", side),
                                    "up": reg("top", top), "down": reg("top", top)})
    return m


V07_ITEMS = [f"upgrade_{k}_{t}" for k in gt.UPGRADE_TYPES for t in gt.ATM_TIERS] + \
    ["upgrade_chunk_anchor", "quantum_core", "cloud_tablet", "advanced_cloud_tablet", "cloud_analyzer"]


# --------------------------------------------------------------------------- fiber (cables part B)
FIBER_DIRS = {"south": {}, "north": {"y": 180}, "west": {"y": 90}, "east": {"y": 270},
              "up": {"x": 90}, "down": {"x": 270}}   # identical to CC's cable_arm rotations


def _fiber_tex():
    tex = f"{NS}:block/fiber"
    T = lambda n: Tile("atlas", "fiber", n)
    return tex, T


def _fiber_segment(m, T, lo, hi):
    """A 4x4 fiber run from z=lo to z=hi: two jacket halves around a 1 px slit + an inset emissive core
    (6.25..9.75) that glows through the slit. No coplanar faces. Open ends (the neighbour covers them)."""
    m.box([6, 6, lo], [10, 7.5, hi], T("jacket"), only=("down", "west", "east", "up"))
    m.box([6, 8.5, lo], [10, 10, hi], T("jacket"), only=("down", "west", "east", "up"))
    # core starts 0.25 px early so it meets the centre core (6.25..9.75) without a dark break
    m.box([6.25, 7.5, lo - 0.25], [9.75, 8.5, hi], T("core"), emit=15, only=("west", "east"))


def fiber_core():
    tex, T = _fiber_tex()
    m = Model(textures={"particle": tex, "atlas": tex})
    m.box([6, 6, 6], [10, 7.5, 10], T("jacket"))
    m.box([6, 8.5, 6], [10, 10, 10], T("jacket"))
    m.box([6.25, 7.5, 6.25], [9.75, 8.5, 9.75], T("core"), emit=15, skip=("up", "down"))
    return m


def fiber_arm(start=10):
    """Arm towards +Z (south) from the core to the block edge, rotated per direction like CC."""
    tex, T = _fiber_tex()
    m = Model(textures={"particle": tex, "atlas": tex})
    _fiber_segment(m, T, start, 16)
    return m


def fiber_cable_item():
    """Straight 12 px piece with closed ends, like CC's cable item (z 2..14)."""
    tex, T = _fiber_tex()
    m = Model(textures={"particle": tex, "atlas": tex})
    m.box([6, 6, 2], [10, 7.5, 14], T("jacket"))
    m.box([6, 8.5, 2], [10, 10, 14], T("jacket"))
    m.box([6.25, 7.5, 2], [9.75, 8.5, 14], T("core"), emit=15, skip=("up", "down"))
    return m


def fiber_junction():
    """12x12x12 CC casing box, a socket on every face with a glowing 2x2 pin (0.1 px proud)."""
    tex, T = _fiber_tex()
    m = Model(textures={"particle": tex, "atlas": tex})
    m.box([2, 2, 2], [14, 14, 14], T("junction"))
    e = 0.1
    for (frm, to, d) in (([7, 7, 14], [9, 9, 14 + e], "south"), ([7, 7, 2 - e], [9, 9, 2], "north"),
                         ([2 - e, 7, 7], [2, 9, 9], "west"), ([14, 7, 7], [14 + e, 9, 9], "east"),
                         ([7, 14, 7], [9, 14 + e, 9], "up"), ([7, 2 - e, 7], [9, 2, 9], "down")):
        m.box(frm, to, T("core"), emit=15, skip=({"south": "north", "north": "south", "west": "east",
                                                    "east": "west", "up": "down", "down": "up"}[d],))
    return m


def ender_fiber_link():
    """Gold CC block: recessed ender-eye port on top (6x6, 1 px deep), fiber sockets with glowing pins
    on the four sides and the bottom."""
    tex, T = _fiber_tex()
    m = Model(textures={"particle": tex, "atlas": tex})
    side, top, body, dark = T("link_side"), T("link_top"), T("link_body"), T("dark")
    m.box([0, 0, 0], [16, 15, 16], {"*": side, "up": top, "down": side})
    f = {"*": side, "up": top}
    m.box([0, 15, 0], [16, 16, 5], {**f, "south": dark}, skip=("down",))
    m.box([0, 15, 11], [16, 16, 16], {**f, "north": dark}, skip=("down",))
    m.box([0, 15, 5], [5, 16, 11], {**f, "east": dark}, skip=("down", "north", "south"))
    m.box([11, 15, 5], [16, 16, 11], {**f, "west": dark}, skip=("down", "north", "south"))
    eye = Region("atlas", "fiber", "eye")
    m.box([6, 15, 6], [10, 15.6, 10], {"*": T("core"), "up": eye}, emit=15, skip=("down",))
    e = 0.1
    for (frm, to, d) in (([7, 7, 16], [9, 9, 16 + e], "south"), ([7, 7, -e], [9, 9, 0], "north"),
                         ([-e, 7, 7], [0, 9, 9], "west"), ([16, 7, 7], [16 + e, 9, 9], "east"),
                         ([7, -e, 7], [9, 0, 9], "down")):
        el = m.box(frm, to, T("core"), emit=15, skip=({"south": "north", "north": "south", "west": "east",
                                                         "east": "west", "down": "up"}[d],))
        el["faces"][d]["cullface"] = d
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
        "block/uplink": uplink_body(False), "block/uplink_ring": uplink_ring(),
        "block/uplink_advanced": uplink_body(True), "block/uplink_advanced_core": uplink_core(),
        "block/uplink_direct_ring": uplink_direct_ring(),
        "block/me_cloud_bridge": bridge("me"),
        "block/rs_cloud_bridge": bridge("rs"),
        "block/cloud_relay": relay_base("cloud_relay"),
        "block/advanced_cloud_relay": relay_base("advanced_cloud_relay"),
        "block/ender_relay": relay_base("ender_relay"),
        "block/cloud_relay_ring": relay_ring("cloud_relay"),
        "block/advanced_cloud_relay_ring": relay_ring("advanced_cloud_relay"),
        "block/ender_relay_core": ender_core(),
        "block/fiber_cable_core": fiber_core(),
        "block/fiber_cable_arm": fiber_arm(10),
        "block/fiber_junction": fiber_junction(),
        "block/fiber_junction_stub": fiber_arm(14),
        "block/ender_fiber_link": ender_fiber_link(),
        "item/fiber_cable": fiber_cable_item(),
    }
    models["block/cloud_probe"] = probe_body()
    for mt_ in MOUNTS[1:]:
        models[f"block/uplink_compact_{mt_}"] = uplink_compact(mt_)
        models[f"block/uplink_compact_ring_{mt_}"] = uplink_compact_ring(mt_)
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
    for s in STATES:
        write(f"models/block/cloud_probe_{s}.json", variant_child(f"{NS}:block/cloud_probe", {"status": f"{NS}:block/uplink_status_{s}"}))
    # compact uplinks: the advanced tier only swaps the atlas; status children swap the LED
    for mt_ in MOUNTS[1:]:
        write(f"models/block/uplink_advanced_compact_{mt_}.json",
              variant_child(f"{NS}:block/uplink_compact_{mt_}", {"particle": f"{NS}:block/uplink_advanced_compact",
                                                                   "atlas": f"{NS}:block/uplink_advanced_compact"}))
        for s in STATES:
            for base in ("uplink_compact", "uplink_advanced_compact"):
                write(f"models/block/{base}_{mt_}_{s}.json",
                      variant_child(f"{NS}:block/{base}_{mt_}", {"status": f"{NS}:block/uplink_status_{s}"}))

    for kind in ("me", "rs"):
        for st_ in STATES:
            write(f"models/block/{kind}_cloud_bridge_{st_}.json",
                  variant_child(f"{NS}:block/{kind}_cloud_bridge", {"status": f"{NS}:block/cloud_bridge_status_{st_}"}))
        write(f"blockstates/{kind}_cloud_bridge.json", {"variants": {
            f"facing={f},status={st_}": {"model": f"{NS}:block/{kind}_cloud_bridge_{st_}", **r}
            for f, r in HORIZONTAL.items() for st_ in STATES}})
        write(f"items/{kind}_cloud_bridge.json",
              {"model": {"type": "minecraft:model", "model": f"{NS}:block/{kind}_cloud_bridge_online"}})

    for block, part in (("cloud_relay", "cloud_relay_ring"), ("advanced_cloud_relay", "advanced_cloud_relay_ring"),
                        ("ender_relay", "ender_relay_core")):
        for st_ in RELAY_STATES:
            write(f"models/block/{block}_{st_}.json",
                  variant_child(f"{NS}:block/{block}", {"status": f"{NS}:block/relay_status_{st_}"}))
        write(f"blockstates/{block}.json", {"variants": {
            f"status={st_}": {"model": f"{NS}:block/{block}_{st_}"} for st_ in RELAY_STATES}})
        write(f"items/{block}.json", {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": f"{NS}:block/{block}_online"},
            {"type": "minecraft:model", "model": f"{NS}:block/{part}"}]}})

    # fiber cable (multipart like CC's cable: core + one arm per connected side)
    for block, base, arm in (("fiber_cable", "fiber_cable_core", "fiber_cable_arm"),
                             ("fiber_junction", "fiber_junction", "fiber_junction_stub")):
        parts = [{"apply": {"model": f"{NS}:block/{base}"}}]
        for d, r in FIBER_DIRS.items():
            parts.append({"when": {d: "true"}, "apply": {"model": f"{NS}:block/{arm}", **r}})
        write(f"blockstates/{block}.json", {"multipart": parts})
    write("blockstates/ender_fiber_link.json", {"variants": {"": {"model": f"{NS}:block/ender_fiber_link"}}})
    write("items/fiber_cable.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/fiber_cable"}})
    write("items/fiber_junction.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/fiber_junction"}})
    write("items/ender_fiber_link.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/ender_fiber_link"}})

    # v0.7 cloud display (CC-monitor-like multiblock)
    for st_ in gt.DISPLAY_STATES:
        name = "cloud_display" if st_ == "none" else f"cloud_display_{st_}"
        write(f"models/block/{name}.json", display_model(st_).json())
    dv = {}
    for f, r in HORIZONTAL.items():
        for o, xr in DISPLAY_ORIENT.items():
            for st_ in gt.DISPLAY_STATES:
                name = "cloud_display" if st_ == "none" else f"cloud_display_{st_}"
                v = {"model": f"{NS}:block/{name}"}
                if xr:
                    v["x"] = xr
                v.update(r)
                dv[f"facing={f},orientation={o},state={st_}"] = v
    write("blockstates/cloud_display.json", {"variants": dv})
    write("items/cloud_display.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/cloud_display"}})

    # flat items
    for it in ("cloud_core", "cloud_link_card", *V07_ITEMS):
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

    for block, advanced in (("cloud_uplink", False), ("advanced_cloud_uplink", True)):
        parts = []
        for mt_ in MOUNTS:
            for f, r in ALL6.items():
                for s in STATES:
                    parts.append({"when": {"facing": f, "mount": mt_, "status": s},
                                  "apply": {"model": uplink_body_model(advanced, mt_, s), **r}})
        for mt_ in MOUNTS:
            for f, r in ALL6.items():
                parts.append({"when": {"direct": "true", "facing": f, "mount": mt_},
                              "apply": {"model": uplink_ring_model(mt_), **r}})
        write(f"blockstates/{block}.json", {"multipart": parts})

    # Cloud Probe: always the plate with the lens (no mount variants); direct = it has a target -> the plate's ring
    parts = []
    for f, r in ALL6.items():
        for s in STATES:
            parts.append({"when": {"facing": f, "status": s}, "apply": {"model": f"{NS}:block/cloud_probe_{s}", **r}})
    for f, r in ALL6.items():
        parts.append({"when": {"direct": "true", "facing": f}, "apply": {"model": f"{NS}:block/uplink_direct_ring", **r}})
    write("blockstates/cloud_probe.json", {"multipart": parts})
    write("items/cloud_probe.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/cloud_probe_online"}})

    # item model definitions (26.1 items/*.json)
    write("items/cloud_mainframe.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/mainframe_item"}})
    write("items/cloud_uplink.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{NS}:block/uplink_online"},
        {"type": "minecraft:model", "model": f"{NS}:block/uplink_ring"}]}})
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


_AXIS = {"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1)}


def coplanar_faces(elements):
    """Flag z-fighting: two elements with the same rotation whose faces point the same way, lie in
    the same plane and overlap with positive area (e.g. a full-face overlay over split faces)."""
    out = []
    for i in range(len(elements)):
        for j in range(i + 1, len(elements)):
            a, b = elements[i], elements[j]
            if a.get("rotation") != b.get("rotation"):
                continue
            for d, (ax, hi) in _AXIS.items():
                if d not in a["faces"] or d not in b["faces"]:
                    continue
                pa = (a["to"] if hi else a["from"])[ax]
                pb = (b["to"] if hi else b["from"])[ax]
                if abs(pa - pb) > 1e-6:
                    continue
                o1, o2 = [k for k in range(3) if k != ax]
                ov1 = min(a["to"][o1], b["to"][o1]) - max(a["from"][o1], b["from"][o1])
                ov2 = min(a["to"][o2], b["to"][o2]) - max(a["from"][o2], b["from"][o2])
                if ov1 > 1e-6 and ov2 > 1e-6:
                    out.append(f"coplanar {d} faces of elements {i} and {j} overlap (z-fighting)")
    return out


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
        problems.extend(f"{rel}: {msg}" for msg in coplanar_faces(m.get("elements", [])))
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
    if len(data.get("blockstates/cloud_display.json", {}).get("variants", {})) != 4 * 3 * 16:
        problems.append("cloud_display: expected 192 variants (facing x orientation x state)")
    for it in V07_ITEMS:
        if f"items/{it}.json" not in data:
            problems.append(f"missing item definition {it}")
    for blk in ("fiber_cable", "fiber_junction"):
        mp = data.get(f"blockstates/{blk}.json", {}).get("multipart", [])
        props = sorted(next(iter(p["when"])) for p in mp if "when" in p)
        if props != sorted(FIBER_DIRS):
            problems.append(f"{blk}: multipart must have one arm per direction, got {props}")
    for blk in ("cloud_relay", "advanced_cloud_relay", "ender_relay"):
        if len(data.get(f"blockstates/{blk}.json", {}).get("variants", {})) != len(RELAY_STATES):
            problems.append(f"{blk}: expected {len(RELAY_STATES)} status variants (incl. no_power)")
    for kind in ("me", "rs"):
        bv = data.get(f"blockstates/{kind}_cloud_bridge.json", {}).get("variants", {})
        if len(bv) != 4 * 3:
            problems.append(f"{kind}_cloud_bridge: {len(bv)} variants, expected 12")
    problems.extend(validate_uplinks(data))
    return problems


def validate_uplinks(data):
    """Uplink blockstates cover facing x mount x status (one body each) and facing x mount (one direct
    ring each) with the plate's rotations; compact pucks stay in the cell, and body + ring of every
    mount are free of coplanar overlaps."""
    problems = []
    for blk, adv in (("cloud_uplink", False), ("advanced_cloud_uplink", True)):
        body, ring = {}, {}
        for p in data.get(f"blockstates/{blk}.json", {}).get("multipart", []):
            w, a = p.get("when", {}), p["apply"]
            rot = {k: a[k] for k in ("x", "y") if k in a}
            if set(w) == {"facing", "mount", "status"}:
                body.setdefault((w["facing"], w["mount"], w["status"]), []).append((a["model"], rot))
            elif set(w) == {"direct", "facing", "mount"} and w["direct"] == "true":
                ring.setdefault((w["facing"], w["mount"]), []).append((a["model"], rot))
            else:
                problems.append(f"{blk}: unexpected multipart condition {w}")
        for f, r in ALL6.items():
            for mt_ in MOUNTS:
                got = ring.get((f, mt_), [])
                if got != [(uplink_ring_model(mt_), r)]:
                    problems.append(f"{blk}: facing={f} mount={mt_} direct ring {got}")
                for s in STATES:
                    got = body.get((f, mt_, s), [])
                    if got != [(uplink_body_model(adv, mt_, s), r)]:
                        problems.append(f"{blk}: facing={f} mount={mt_} status={s} body {got}")
        if len(body) != len(ALL6) * len(MOUNTS) * len(STATES) or len(ring) != len(ALL6) * len(MOUNTS):
            problems.append(f"{blk}: {len(body)} body / {len(ring)} ring entries, expected "
                            f"{len(ALL6) * len(MOUNTS) * len(STATES)} / {len(ALL6) * len(MOUNTS)}")

    def elements(rid):
        return data.get(f"models/{rid.split(':', 1)[1]}.json", {}).get("elements", [])

    pairs = [("full", elements(f"{NS}:block/uplink"))]
    for mt_ in MOUNTS[1:]:
        els = elements(f"{NS}:block/uplink_compact_{mt_}")
        v = COMPACT_Y[mt_]
        pairs.append((mt_, els))
        if not els or els[0]["from"] != [4, v, 0] or els[0]["to"] != [12, v + 6, 2]:
            problems.append(f"uplink_compact_{mt_}: puck must be [4,{v},0]..[12,{v + 6},2] (CloudUplinkBlock shapes)")
        for i, el in enumerate(els):
            lo, hi = el["from"], el["to"]
            if min(lo) < 0 or max(hi[0], hi[1]) > 16 or hi[2] > 2.25:
                problems.append(f"uplink_compact_{mt_} element {i}: outside x/y 0..16, z 0..2.25")
    for mt_, els in pairs:
        ring_els = elements(uplink_ring_model(mt_))
        problems.extend(f"uplink {mt_} body + direct ring: {msg}" for msg in coplanar_faces(els + ring_els))
    # Cloud Probe: facing x status bodies, a direct ring per facing, lens bounds = the Java shape, no z-fighting
    body, ring = {}, {}
    for p in data.get("blockstates/cloud_probe.json", {}).get("multipart", []):
        w, a = p.get("when", {}), p["apply"]
        rot = {k: a[k] for k in ("x", "y") if k in a}
        (body if "status" in w else ring).setdefault((w["facing"], w.get("status")), []).append((a["model"], rot))
    for f, r in ALL6.items():
        if ring.get((f, None)) != [(f"{NS}:block/uplink_direct_ring", r)]:
            problems.append(f"cloud_probe: facing={f} direct ring {ring.get((f, None))}")
        for s in STATES:
            if body.get((f, s)) != [(f"{NS}:block/cloud_probe_{s}", r)]:
                problems.append(f"cloud_probe: facing={f} status={s} body {body.get((f, s))}")
    probe = elements(f"{NS}:block/cloud_probe")
    lo = [min(e["from"][i] for e in probe) for i in range(3)] if probe else []
    hi = [max(e["to"][i] for e in probe) for i in range(3)] if probe else []
    if lo != [1, 1, 0] or hi != [15, 15, 3.5]:
        problems.append(f"cloud_probe: bounds {lo}..{hi}, CloudProbeBlock's shape expects [1,1,0]..[15,15,3.5]")
    problems.extend(f"cloud_probe body + direct ring: {msg}" for msg in coplanar_faces(probe + elements(f"{NS}:block/uplink_direct_ring")))
    return problems


PIVOTS = """\
| Model | Rendered for | Pivot (px, model space) | Axis | Suggested animation |
|---|---|---|---|---|
| `block/mainframe_fan` | **lower** half | (13.5, 8, 8) | X | spin, ~1 rev/s online, slow down to 0 when offline |
| `block/mainframe_hologram` | **upper** half | (8, 23.5, 8) | Y | rotate ~20 deg/s, bob +/-0.5 px (sin), flicker alpha when connecting/offline, hide when offline |
| `block/uplink_ring` | normal uplink | (8, 8, 1.375) | Z (in the plate plane) | spin in place: ~0.5 rev/s online, fast ~2 rev/s connecting, stopped offline; no translation (it lives inside the 1 px port recess) |
| `block/cloud_relay_ring` | cloud relay | (8, 13, 8) | Y | slow spin ~0.25 rev/s online, faster connecting, stopped offline / no power |
| `block/advanced_cloud_relay_ring` | advanced relay | (8, 13, 8) | Y | as above |
| `block/ender_relay_core` | ender relay | (8, 11.5, 8) | Y (+ bob +/-0.5 px on Y) | spin ~0.5 rev/s, stop when offline / no power |
| `block/uplink_advanced_core` | advanced uplink | (8, 8, 1.375) | Z (in the plate plane) | spin in place ~0.25 rev/s (the diagonal cyan glints make it visible); no translation |
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
Uplink depth: plate z 0..2 (port floor z 1), LED strip to z 2.25, moving parts z 1..1.75.
Uplink facing rotations (same as the CC modem): north = none, south = y180, east = y90,
west = y270, up = x270, down = x90. Model space is "attached to the north face"; +Z points away
from the block the uplink sits on.
Mainframe: north = none, east = y90, south = y180, west = y270 (front with the glass door = north).

## Element counts

| Model | Elements |
|---|---|
{rows}
"""
    path = os.path.join(ROOT, "docs", "MODELS_TABLE.md")
    with open(path, "w") as f:
        f.write(readme)
    written.append("docs/MODELS_TABLE.md")

    doc = f"""# CC: Cloud models: technical notes

Generated by `tools/gen_models.py` + `tools/gen_textures.py`. Source of truth for UVs is
`LAYOUTS` in `gen_textures.py`.

{readme.split('## Moving parts', 1)[1].join(['## Moving parts', ''])}

## Compact uplink (non-full faces)

The 14x14 plate only fits faces that carry it. On a slab side, a tray-height machine (Energized Power
solar panel, 4 px), a carpet-height block or an inset face it stuck out like a floating sign, so the
uplink blockstates have a `mount` property (`{" | ".join(MOUNTS)}`) that the server computes
from the attached block's outline shape (`UplinkMount.compute`, on placement, when that neighbour
changes and once after loading, so older worlds fix themselves):

- `full`: the face is sturdy, or flush (inset <= 1 px) and covers >= 90 % of the plate area (or of
  its 1 px outline, e.g. a hopper rim) -> the plate.
- `bottom|low|center|high|top` (side faces only): smaller -> an 8x6 puck (`block/uplink_compact_<mount>`,
  x 4..12, z 0..2, bottom edge at model y {", ".join(f"{k} {v}" for k, v in COMPACT_Y.items())}).
  The position with the largest overlap with the face wins (ties: nearest to the face's centre), so it
  sits on the visible part: solar panel / carpet side -> `bottom`, slab side -> `low`, top slab -> `high`.
- Top and bottom faces only take the plate: placing an uplink on a top / bottom face that does not
  qualify for `full` (top of a slab or solar panel) is refused with an action bar hint. Uplinks that
  already sit there (older versions allowed it on block entities) stay and keep the plate.
- Unknown saved values (e.g. the removed `post*` mounts of a dev build) load as `full` (vanilla state
  codec default) and are re-computed on the first tick.

Front: 8x6 region `front` of `textures/block/uplink{{,_advanced}}_compact.png` (32x32: same outline, lower
band, mini cloud, screws; gold for advanced), status LED = first 4 px of the status strip (`strip4`,
emissive, proud to z 2.25). No moving part (the BER skips compact mounts). Direct mode:
`block/uplink_compact_ring_<mount>` (0.5 px emissive ring around the puck, flush). Tiers share the
geometry: `uplink_advanced_compact_<mount>` only swaps the atlas; `_<status>` children swap the LED.
`validate()` checks that every facing x mount x status has one body and every facing x mount one ring
(with the plate's rotations), that pucks match the Java shapes, and that body + ring have no coplanar
overlaps. The item and the dashboard's 3D view use `mount=full`.

## Cloud Probe

`block/cloud_probe` (+ `_online/_connecting/_offline` children swapping `uplink_status_*`), atlas
`textures/block/cloud_probe.png` (64x64, layout `probe`): the uplink plate in slate blue (outline, lighter lower
band, cloud emblem, LED slot, screws) as one 14x14x2 box, the status strip like the uplink, and a lens that stands
proud – a 6x6 gold rim (z 2..3) holding a 4x4 emissive glass (z 2..3.5). Clearly not an uplink, no post or antenna.
No moving part (the BER skips it). Blockstate `cloud_probe`: facing x status, plus `uplink_direct_ring` while
`direct=true` (it has a target); the `mount` property exists (shared block class) but is always `full`.
Shape (`CloudProbeBlock.NORTH_SHAPE`): plate 1..15 x 1..15 x 0..2.25 + lens 5..11 x 5..11 x 0..3.5; `validate()`
checks the model bounds against it. Item: `items/cloud_probe.json` -> the `_online` model.

## Cloud bridges (v0.2)

`block/me_cloud_bridge`, `block/rs_cloud_bridge` (+ `_online/_connecting/_offline` children that swap
`cloud_bridge_status_*`). Full blocks, front = north, blockstate `facing` (n/s/e/w, y-rotation like the
mainframe) x `status`. No moving parts. Emissive: cloud emblem in the port, status strip, and the socket
pins on the 5 connection faces: small 2x2 elements standing 0.1 px proud of each face (with cullface).
A full-face overlay (CC's `computer_on` trick) z-fought here: CC's overlay covers one identical base quad,
ours sat over faces split into casing + bezel quads. `validate()` now rejects coplanar overlapping faces.
Items: `items/{{me,rs}}_cloud_bridge.json` -> the `_online` block model (vanilla `block/block` display).

## Relays (v0.4)

`block/cloud_relay`, `block/advanced_cloud_relay`, `block/ender_relay`: no facing (symmetric), blockstate
`status` only. Each has `_online/_connecting/_offline/_no_power` children (swap `relay_status_*`);
`_no_power` = all LEDs dark. The blockstates map all four `status` values
(online, connecting, offline, no_power). Moving parts (BER, not in blockstates):
emitter rings and the ender core, pivots in the table above. Items: composite of the `_online` base + its
moving part (vanilla `block/block` display).

## Cloud Display (v0.7)

`block/cloud_display` (+ 15 `cloud_display_<state>` models), atlas `textures/block/display.png`.
Same blockstate scheme as CC monitors: `facing` (n/e/s/w) x `orientation` (north / up / down, x-rotation
0 / 270 / 90) x `state` (none, l, r, lr, u, d, ud, lu, ld, ru, rd, lud, rud, lru, lrd, lrud; letters =
neighbour on the viewer's left/right/up/down seen from the front) = 192 variants. Front = north. The
frame edge is open on every connected side, so a wall reads as one screen; east/west faces only use u/d,
up/down faces only l/r (like CC). The screen area is plain CC black #111: the BER draws the card on
top of the front face (screen inset 3 px on closed edges, 0 px on connected edges).

## Upgrade cards, Quantum Core, tablets (v0.7)

Flat `item/generated` items: `upgrade_<capacity|bandwidth|range|efficiency|memory>_<allthemodium|vibranium|unobtainium>`,
`quantum_core`, `cloud_tablet`, `advanced_cloud_tablet`, `cloud_analyzer` (handheld scanner: stone-grey
pocket-computer body, cyan signal bars, antenna nub). One shared card base in the ATM metal colour
ramp, the symbol shows the type (grid = capacity, arrows = bandwidth, arcs = range, bolt = efficiency,
chip = memory). `upgrade_chunk_anchor` uses the same base in neutral CC grey with a cyan anchor.

## Fiber cable, junction, ender fiber link (cables part B)

Blockstate properties (the cables code must use exactly these):
- `cccloud:fiber_cable`: `north`, `south`, `east`, `west`, `up`, `down` = `true|false` (connected on that
  side). Multipart: `block/fiber_cable_core` always, `block/fiber_cable_arm` per `true` side. The arm points
  +Z (south) and is rotated like CC's `cable_arm`: south none, north y180, west y90, east y270, up x90,
  down x270.
- `cccloud:fiber_junction`: the same six boolean properties; `block/fiber_junction` (12x12x12 box) always,
  `block/fiber_junction_stub` (a 2 px fiber stub from the box to the block edge, same rotations) per
  connected side.
- `cccloud:ender_fiber_link`: no properties (single variant `""`); eye port on top, sockets on the sides.
Fiber geometry: 4x4 jacket (6..10) in two halves around a 1 px slit (y 7.5..8.5), emissive cyan core
inset to 6.25..9.75 so it glows through the slit without coplanar faces. Items: `item/fiber_cable`
(straight 12 px piece with closed ends, like CC's cable item), the junction and link use their block models. No BER.

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

## Particles

None. The `data_packet` particle was removed in v0.4 (performance, see docs/SPEC.md).
"""
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    with open(os.path.join(ROOT, "docs", "MODELS.md"), "w") as f:
        f.write(doc)


if __name__ == "__main__":
    main()
