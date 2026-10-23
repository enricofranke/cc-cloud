#!/usr/bin/env python3
"""Deterministic texture generator for CC: Cloud (mod id `cccloud`).

Draws every texture pixel by pixel from a small palette (see STYLE_NOTES.md). No image
library is needed: PNGs are written with zlib from the standard library.

    python3 tools/gen_textures.py            # writes src/main/resources/assets/cccloud/textures/**

The atlas layout (tile and region positions) is exported via `LAYOUTS` so that
tools/gen_models.py can compute UVs from the same source of truth.

Style rules (from the CC: Tweaked reference, see STYLE_NOTES.md):
- body colours use 4 neighbouring shades picked by a hash (CC-style +/-3 noise)
- 1 px dark outline (#24..#4d) at block edges, lighter lower band instead of painted light
- cyan only as accent, dark interiors #1c..#22, screen black #111
"""
from __future__ import annotations

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "cccloud", "textures")


# --------------------------------------------------------------------------- palette
def hx(s: str, a: int = 255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def grey(v: int, a: int = 255):
    return (v, v, v, a)


GREY = [grey(0x7d), grey(0x7f), grey(0x83), grey(0x7d), grey(0x7f), grey(0x7a)]
GREY_L = [grey(0xaf), grey(0xb1), grey(0xb5), grey(0xaf), grey(0xac)]
GOLD = [hx("ddd973"), hx("ddd771"), hx("dcd872"), hx("dbda75"), hx("ddd975"), hx("dadb73")]
GOLD_L = [hx("ecec9c"), hx("eded9d"), hx("eceb94"), hx("eced93"), hx("ebec9a"), hx("edea91")]
EDGE = [grey(0x2d), grey(0x30), grey(0x32), grey(0x35), grey(0x38), grey(0x2f), grey(0x3b), grey(0x29)]
EDGE_L = [grey(0x4b), grey(0x4a), grey(0x4d), grey(0x3f)]
CHASSIS = [grey(0x3b), grey(0x3d), grey(0x40), grey(0x3b), grey(0x38)]
RECESS = [grey(0x1c), grey(0x1e), grey(0x20), grey(0x1e)]
SLOT = [grey(0x24), grey(0x27), grey(0x24), grey(0x2b)]
BLACK = [grey(0x11)]
CABLE = [grey(0x7d), grey(0x7f), grey(0x83), grey(0x7a)]
CABLE_D = [grey(0x5c), grey(0x5f), grey(0x61)]

CYAN = hx("5ec8e5")
CYAN_L = hx("a8e6f5")
CYAN_D = hx("2a7f99")
GREEN = hx("5fd35f")
GREEN_L = hx("a6eea6")
GREEN_D = hx("2f8f2f")
YELLOW = hx("e8c547")
YELLOW_L = hx("f5e3a0")
YELLOW_D = hx("a8862a")
RED = hx("d34f4f")
RED_L = hx("eea0a0")
RED_D = hx("8a2a2a")
ENDER = hx("2fa37a")
ENDER_L = hx("6fd3a9")
ENDER_D = hx("1d6b50")
FLUIX = hx("8a5ccf")    # AE2 accent (used sparingly)
FLUIX_L = hx("b48ae8")
FLUIX_D = hx("5b3a8f")
RS_GREEN = hx("4fc35a")  # RS-ish status green / blue (subtle)
RS_GREEN_L = hx("9be3a0")
RS_BLUE = hx("4f8fd3")
RS_DARK = [grey(0x3d), grey(0x40), grey(0x42), grey(0x3d), grey(0x3a)]
RS_DARK_L = [grey(0x4d), grey(0x4f), grey(0x52), grey(0x4d)]
OFF = hx("2a2d2e")      # unlit LED
OFF_L = hx("3a3f41")
CLEAR = (0, 0, 0, 0)


# --------------------------------------------------------------------------- canvas
def _hash(x: int, y: int, seed: int) -> int:
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return h ^ (h >> 16)


class Canvas:
    def __init__(self, w: int, h: int, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [[fill for _ in range(w)] for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def noise(self, x0, y0, w, h, shades, seed=0):
        """Fill a rect with CC-like noise: each pixel picks one of a few neighbouring shades."""
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, shades[_hash(x, y, seed) % len(shades)])

    def rect(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, c)

    def hline(self, x0, y, w, shades, seed=1):
        for x in range(x0, x0 + w):
            self.set(x, y, shades[_hash(x, y, seed) % len(shades)])

    def vline(self, x, y0, h, shades, seed=2):
        for y in range(y0, y0 + h):
            self.set(x, y, shades[_hash(x, y, seed) % len(shades)])

    def outline(self, x0, y0, w, h, shades=EDGE, seed=3):
        self.hline(x0, y0, w, shades, seed)
        self.hline(x0, y0 + h - 1, w, shades, seed)
        self.vline(x0, y0, h, shades, seed)
        self.vline(x0 + w - 1, y0, h, shades, seed)

    def ascii(self, x0, y0, rows, pal, seed=5):
        """Draw an ASCII pixel map. pal maps char -> colour or list of shades. ' ' = keep."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == " ":
                    continue
                v = pal[ch]
                if isinstance(v, list):
                    v = v[_hash(x0 + i, y0 + j, seed) % len(v)]
                self.set(x0 + i, y0 + j, v)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for (r, g, b, a) in row:
                raw += bytes((r, g, b, a))

        def chunk(tag, data):
            c = struct.pack(">I", len(data)) + tag + data
            return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)


# --------------------------------------------------------------------------- layouts
# Tiles are 16x16 texel blocks addressed by (col, row); a face using a tile gets vanilla-style
# "auto UV" (the texel under the face's block position), so neighbouring elements line up.
# Regions are explicit texel rectangles (x, y, w, h) for designed faces (blades, plates ...).
LAYOUTS = {
    "mainframe": {
        "size": 128,
        "tiles": {
            # face tiles (designed for one block face, posts/panels/cap sample them by position)
            "lower_front": (0, 0), "lower_west": (1, 0), "lower_east": (2, 0), "lower_back": (3, 0),
            "upper_front": (4, 0), "upper_side": (5, 0), "upper_back": (6, 0), "top": (7, 0),
            "bottom": (0, 1),
            # plain materials (no outline)
            "gold": (1, 1), "gold_l": (2, 1), "chassis": (3, 1), "recess": (4, 1),
            "grey": (5, 1), "grey_l": (6, 1), "cable": (7, 1), "black": (0, 2),
        },
        "regions": {
            "blade_a": (16, 32, 10, 4), "blade_b": (16, 36, 10, 4), "blade_c": (16, 40, 10, 4),
            "port_panel": (32, 32, 10, 7), "plug": (32, 40, 3, 3), "cable_end": (36, 40, 2, 2),
            "screw": (40, 40, 1, 1), "fan_hub": (44, 40, 2, 2), "clamp": (48, 32, 12, 1),
            "handle": (48, 34, 1, 8),
        },
    },
    "uplink": {
        "size": 64,
        "tiles": {
            "plate": (0, 0), "body": (1, 0), "body_l": (2, 0), "recess": (3, 0),
            "chassis": (0, 1), "cyan_d": (1, 1), "ring": (2, 1), "core": (3, 1),
        },
        "regions": {
            "spinner": (0, 32, 2, 1), "spinner_grey": (4, 32, 2, 1),
            "eye": (8, 32, 3, 3),
        },
    },
    "bridge": {
        "size": 64,
        "tiles": {
            # front: bezel (z 0..2) + port floor (z 2) sample this by position; side: the 5 other faces
            "front": (0, 0), "side": (1, 0), "pins": (2, 0), "wall": (3, 0),
        },
        "regions": {},
    },
    "relay": {
        "size": 64,
        "tiles": {"side": (0, 0), "top": (1, 0), "body": (2, 0), "body_l": (3, 0),
                  "dark": (0, 1), "ring": (1, 1), "beacon": (2, 1), "core": (3, 1)},
        "regions": {"eye": (0, 32, 4, 4)},
    },
    "fiber": {
        "size": 64,
        "tiles": {"jacket": (0, 0), "core": (1, 0), "junction": (2, 0), "link_side": (3, 0),
                  "link_top": (0, 1), "link_body": (1, 1), "dark": (2, 1)},
        "regions": {"eye": (48, 16, 4, 4)},
    },
}


def tile_origin(atlas, name):
    c, r = LAYOUTS[atlas]["tiles"][name]
    return c * 16, r * 16


# --------------------------------------------------------------------------- mainframe atlas
def mf_face_tile(cv, ox, oy, half, kind, seed):
    """One 16x16 face tile of the 2-tall mainframe.

    half = lower|upper. Posts sample columns 0..2 / 14..16, the recessed panel 2..14.
    lower: row 13 = joint above the plinth, row 15 = bottom outline, rows 9..15 = light band.
    upper: row 0 = top outline, row 2 = joint below the cap.
    """
    for y in range(16):
        band = half == "lower" and y >= 9
        cv.hline(ox, oy + y, 16, GOLD_L if band else GOLD, seed + y)
    # block-edge outline on the outer column of each post (like every CC face)
    cv.vline(ox, oy, 16, EDGE, seed + 20)
    cv.vline(ox + 15, oy, 16, EDGE, seed + 21)
    if half == "lower":
        for y in range(9, 15):
            cv.set(ox, oy + y, EDGE_L[_hash(ox, y, seed) % len(EDGE_L)])
        cv.hline(ox, oy + 13, 16, EDGE, seed + 22)
        cv.hline(ox, oy + 15, 16, EDGE, seed + 23)
    else:
        cv.hline(ox, oy + 0, 16, EDGE, seed + 22)
        cv.hline(ox, oy + 2, 16, EDGE, seed + 23)
    if kind == "front":
        return
    # recessed panel border (columns 2 and 13) between the posts
    y0, y1 = (0, 13) if half == "lower" else (3, 16)
    cv.vline(ox + 2, oy + y0, y1 - y0, EDGE, seed + 24)
    cv.vline(ox + 13, oy + y0, y1 - y0, EDGE, seed + 25)
    if kind == "fan":
        # CC-style dark lip (1 px) on the panel pixels around the round 8x8 fan opening
        # (block y 4..12, z 4..12 -> tile rows 4..11, cols 4..11; rows are 4,6,8,8,8,8,6,4 wide)
        widths = [4, 6, 8, 8, 8, 8, 6, 4]
        hole = {(8 - w // 2 + i, 4 + r) for r, w in enumerate(widths) for i in range(w)}
        for y in range(3, 13):
            for x in range(3, 13):
                if (x, y) not in hole and any((x + dx, y + dy) in hole for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    cv.set(ox + x, oy + y, EDGE[_hash(x, y, seed) % len(EDGE)])
    elif kind == "back":
        pass


def gen_mainframe():
    L = LAYOUTS["mainframe"]
    cv = Canvas(L["size"], L["size"], grey(0x30))
    T = lambda n: tile_origin("mainframe", n)

    mf_face_tile(cv, *T("lower_front"), "lower", "front", 10)
    mf_face_tile(cv, *T("lower_west"), "lower", "vent", 30)
    mf_face_tile(cv, *T("lower_east"), "lower", "fan", 50)
    mf_face_tile(cv, *T("lower_back"), "lower", "back", 70)
    mf_face_tile(cv, *T("upper_front"), "upper", "front", 90)
    mf_face_tile(cv, *T("upper_side"), "upper", "vent", 110)
    mf_face_tile(cv, *T("upper_back"), "upper", "back", 130)

    # cap top: CC-style framed gold top, with a darker seat ring under the projector
    ox, oy = T("top")
    for y in range(16):
        cv.hline(ox, oy + y, 16, GOLD_L if y >= 9 else GOLD, 150 + y)
    cv.outline(ox, oy, 16, 16, EDGE, 151)
    for y in range(9, 15):
        cv.set(ox, oy + y, EDGE_L[y % len(EDGE_L)])
    # 2x2 post caps outlined so the four posts read from above
    for (px, py) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        cv.outline(ox + px, oy + py, 2, 2, GOLD_L, 152)
    # bottom
    ox, oy = T("bottom")
    cv.noise(ox, oy, 16, 16, CHASSIS, 160)
    cv.outline(ox, oy, 16, 16, EDGE, 161)

    # plain materials
    for name, shades, seed in (("gold", GOLD, 170), ("gold_l", GOLD_L, 171), ("chassis", CHASSIS, 172),
                               ("recess", RECESS, 173), ("grey", GREY, 174), ("grey_l", GREY_L, 175),
                               ("cable", CABLE, 176), ("black", BLACK, 177)):
        ox, oy = T(name)
        cv.noise(ox, oy, 16, 16, shades, seed)
    # cable: CC cables are plain grey; add the darker underside the CC cable model gets from shading
    ox, oy = T("cable")
    for y in range(16):
        cv.set(ox + 0, oy + y, CABLE_D[y % 3])
        cv.set(ox + 15, oy + y, CABLE_D[(y + 1) % 3])

    # blade fronts (10x4). col 0 = viewer's left. cols 6..8 rows 1..2 = LED well (LED element on top)
    pal = {"o": EDGE, "g": GREY, "l": GREY_L, "k": SLOT, "w": RECESS, "y": GOLD, "K": BLACK}
    R = L["regions"]
    blades = {
        "blade_a": ["oggggggggo", "ogkkkgwwwo", "oglllgwwwo", "ollllllllo"],
        "blade_b": ["oggggggggo", "okgkgkwwwo", "ogggggwwwo", "ollllllllo"],
        "blade_c": ["oggggggggo", "okkgkkwwwo", "ollglgwwwo", "ollllllllo"],
    }
    for name, rows in blades.items():
        x, y, _, _ = R[name]
        cv.ascii(x, y, rows, pal, 180)

    # port panel (10x7): dark recessed panel with a gold label strip
    x, y, w, h = R["port_panel"]
    cv.noise(x, y, w, h, RECESS, 190)
    cv.outline(x, y, w, h, EDGE, 191)
    cv.hline(x + 2, y + 5, 6, [hx("8f8a4c"), hx("948f50")], 192)  # dim gold label strip
    # plug face 3x3, cable end 2x2
    x, y, _, _ = R["plug"]
    cv.ascii(x, y, ["ooo", "oko", "ooo"], {"o": CHASSIS, "k": SLOT}, 193)
    x, y, _, _ = R["cable_end"]
    cv.ascii(x, y, ["ck", "kc"], {"c": CABLE, "k": CABLE_D}, 194)
    x, y, _, _ = R["screw"]
    cv.set(x, y, grey(0x2b))
    x, y, _, _ = R["fan_hub"]
    cv.ascii(x, y, ["Gg", "gd"], {"G": GOLD_L[0], "g": GOLD[0], "d": hx("b9b45e")}, 195)
    x, y, w, h = R["clamp"]
    cv.hline(x, y, w, CHASSIS, 196)
    x, y, w, h = R["handle"]
    cv.vline(x, y, h, GOLD_L, 197)

    cv.save(os.path.join(TEX, "block", "mainframe.png"))


def gen_mainframe_glass():
    """Smoked glass: flat dark tint (CC has no glass; streaks read as dirt at this size)."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            cv.set(x, y, (26, 34, 38, 110))
    cv.save(os.path.join(TEX, "block", "mainframe_glass.png"))


def gen_mainframe_hologram():
    """Hologram voxels: translucent cyan, slightly lighter every second row (scanlines)."""
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = CYAN_L if y % 4 == 0 else CYAN
            a = 175 if y % 4 == 0 else 140
            cv.set(x, y, (c[0], c[1], c[2], a))
    # 1 px lighter rim so single voxels keep a readable silhouette
    for i in range(16):
        for (x, y) in ((i, 0), (0, i)):
            cv.set(x, y, (CYAN_L[0], CYAN_L[1], CYAN_L[2], 190))
    cv.save(os.path.join(TEX, "block", "mainframe_hologram.png"))


STATUS = {
    "online": {"led": [[GREEN, GREEN, CYAN], [GREEN, CYAN, GREEN], [CYAN, GREEN, GREEN],
                       [GREEN, GREEN, GREEN], [GREEN, CYAN, CYAN], [CYAN, GREEN, CYAN]],
               "lens": (CYAN_D, CYAN, CYAN_L), "port": CYAN, "tip": GREEN,
               "crystal": (GREEN_D, GREEN, GREEN_L)},
    "connecting": {"led": [[YELLOW, OFF, YELLOW], [OFF, YELLOW, OFF], [YELLOW, YELLOW, OFF],
                           [OFF, YELLOW, YELLOW], [YELLOW, OFF, OFF], [OFF, OFF, YELLOW]],
                   "lens": (OFF, CYAN_D, CYAN), "port": YELLOW, "tip": YELLOW,
                   "crystal": (YELLOW_D, YELLOW, YELLOW_L)},
    "offline": {"led": [[RED, OFF, OFF], [OFF, OFF, OFF], [RED, OFF, OFF],
                        [OFF, OFF, OFF], [RED, OFF, OFF], [OFF, OFF, OFF]],
                "lens": (OFF, OFF_L, OFF_L), "port": OFF_L, "tip": RED,
                "crystal": (RED_D, RED, RED_L)},
}

# region map of the 16x16 status textures (shared by mainframe_leds_* and uplink_status_*)
STATUS_REGIONS = {
    "led0": (0, 0, 3, 1), "led1": (0, 1, 3, 1), "led2": (0, 2, 3, 1),
    "led3": (0, 3, 3, 1), "led4": (0, 4, 3, 1), "led5": (0, 5, 3, 1),
    "lens": (4, 0, 4, 4), "lens_core": (8, 0, 2, 2), "port": (10, 0, 1, 1), "tip": (11, 0, 1, 1),
    "crystal": (12, 0, 2, 2),
    "strip": (0, 8, 8, 1),   # uplink status LED strip (uplink_status_* only)
}


def gen_status(prefix, state):
    s = STATUS[state]
    cv = Canvas(16, 16, OFF)
    for i, row in enumerate(s["led"]):
        for j, c in enumerate(row):
            cv.set(j, i, c)
    d, m, l = s["lens"]
    cv.ascii(4, 0, ["dmmd", "mllm", "mllm", "dmmd"], {"d": d, "m": m, "l": l})
    cv.ascii(8, 0, ["lm", "mm"], {"l": l if state != "offline" else OFF_L, "m": m})
    cv.set(10, 0, s["port"])
    cv.set(11, 0, s["tip"])
    cd, cm, cl = s["crystal"]
    cv.ascii(12, 0, ["lm", "md"], {"l": cl, "m": cm, "d": cd})
    if prefix == "uplink_status":
        # 8 px LED strip: lit segments separated by dark dividers
        pattern = {"online": "LMMDLMMD", "connecting": "MMDOMMDO", "offline": "MDOOOOOO"}[state]
        cv.ascii(0, 8, [pattern], {"L": cl, "M": cm, "D": cd, "O": OFF})
    cv.save(os.path.join(TEX, "block", f"{prefix}_{state}.png"))


# --------------------------------------------------------------------------- uplink atlases
def gen_uplink(advanced: bool):
    """Flat uplink plate (CC modem language): 14x14 face with 1 px dark outline, a recessed round
    port (8x8, dark floor + hub), pixel cloud emblem, dark slot for the LED strip, corner screws."""
    L = LAYOUTS["uplink"]
    cv = Canvas(L["size"], L["size"], grey(0x30))
    T = lambda n: tile_origin("uplink", n)
    R = L["regions"]
    body, body_l = (GOLD, GOLD_L) if advanced else (GREY, GREY_L)

    ox, oy = T("plate")
    for y in range(16):
        cv.hline(ox, oy + y, 16, body_l if y >= 9 else body, 200 + y)
    # plate is x/y 1..15 -> outline on tile cols/rows 1 and 14 (left edge lighter on the band, like CC)
    cv.outline(ox + 1, oy + 1, 14, 14, EDGE, 201)
    for y in range(9, 14):
        cv.set(ox + 1, oy + y, EDGE_L[y % len(EDGE_L)])
    # round 6x6 port (rows 5..10, widths 4,6,6,6,6,4): dark floor; the 1 px recess wall gives the lip
    widths = [4, 6, 6, 6, 6, 4]
    hole = {(8 - w // 2 + i, 5 + r) for r, w in enumerate(widths) for i in range(w)}
    for (x, y) in hole:
        cv.set(ox + x, oy + y, RECESS[_hash(x, y, 5) % len(RECESS)])
    # hub in the port centre (the ring/core turns around it)
    cv.ascii(ox + 7, oy + 7, ["hh", "hh"], {"h": CHASSIS})
    # pixel cloud emblem (cyan accent), top-left
    cv.ascii(ox + 2, oy + 2, [" Cc  ", "CccCc", "ccccc"], {"C": CYAN_L, "c": CYAN})
    # dark slot for the status LED strip (block y 2..3, x 4..12)
    cv.hline(ox + 4, oy + 13, 8, SLOT, 207)
    # corner screws
    for (x, y) in ((2, 13), (13, 13), (13, 2)):
        cv.set(ox + x, oy + y, grey(0x2b))

    for name, shades, seed in (("body", body, 210), ("body_l", body_l, 211), ("recess", RECESS, 212),
                               ("chassis", CHASSIS, 213)):
        ox, oy = T(name)
        cv.noise(ox, oy, 16, 16, shades, seed)
    ox, oy = T("cyan_d")
    cv.noise(ox, oy, 16, 16, [CYAN_D, hx("2c8299"), hx("287c95")], 214)
    ox, oy = T("ring")
    for y in range(16):
        for x in range(16):
            cv.set(ox + x, oy + y, CYAN_L if (x + y) % 4 == 0 else CYAN)
    ox, oy = T("core")
    for y in range(16):
        for x in range(16):
            cv.set(ox + x, oy + y, ENDER_L if (x + 2 * y) % 5 == 0 else ENDER)

    # spinner segments (2x1 faces): lit cyan / plain body
    x, y, _, _ = R["spinner"]
    cv.ascii(x, y, ["Cc"], {"C": CYAN_L, "c": CYAN})
    x, y, _, _ = R["spinner_grey"]
    cv.ascii(x, y, ["ll"], {"l": body_l})
    # ender eye (3x3): green iris, dark pupil, cyan glints on one diagonal -> rotation is visible
    x, y, _, _ = R["eye"]
    cv.ascii(x, y, ["dEC", "EkE", "CEd"],
             {"d": ENDER_D, "E": ENDER, "k": hx("102a22"), "C": CYAN_L})

    cv.save(os.path.join(TEX, "block", "uplink_advanced.png" if advanced else "uplink.png"))


# --------------------------------------------------------------------------- cloud bridges (v0.2)
def _bridge_port():
    rows = [6, 8, 8, 8, 8, 8, 8, 6]  # 8x8 port, 1 px corners cut
    return {(8 - w // 2 + i, 4 + r) for r, w in enumerate(rows) for i in range(w)}


def gen_bridge(kind: str):
    """Full-block network bridge in CC casing language.

    kind = "me" (AE2: stone casing, fluix-purple accents) or "rs" (Refined Storage: darker front
    bezel, green/blue pins). Front = recessed 8x8 port (2 px deep) holding the cloud emblem,
    status strip under it; the other 5 faces carry a cable socket with a glowing pin (overlay).
    """
    L = LAYOUTS["bridge"]
    cv = Canvas(L["size"], L["size"], grey(0x30))
    T = lambda n: tile_origin("bridge", n)
    port = _bridge_port()
    me = kind == "me"
    front_body, front_band = (GREY, GREY_L) if me else (RS_DARK, RS_DARK_L)

    # front tile
    ox, oy = T("front")
    for y in range(16):
        cv.hline(ox, oy + y, 16, front_band if y >= 9 else front_body, 400 + y)
    cv.outline(ox, oy, 16, 16, EDGE, 401)
    for y in range(9, 15):
        cv.set(ox, oy + y, EDGE_L[y % len(EDGE_L)])
    for (x, y) in port:
        cv.set(ox + x, oy + y, RECESS[_hash(x, y, 402) % len(RECESS)])
    # dark lip around the port; accent only on the four cut corners
    for y in range(3, 13):
        for x in range(3, 13):
            if (x, y) not in port and any((x + dx, y + dy) in port for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                cv.set(ox + x, oy + y, EDGE[_hash(x, y, 403) % len(EDGE)])
    acc, acc_d = (FLUIX, FLUIX_D) if me else (RS_BLUE, hx("2f5f93"))
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        cv.set(ox + x, oy + y, acc_d)
    for (x, y) in ((3, 4), (4, 3), (12, 4), (11, 3), (3, 11), (4, 12), (12, 11), (11, 12)):
        cv.set(ox + x, oy + y, acc if (x + y) % 2 else acc_d)
    # status strip slot (block y 1..2 -> row 14 is outline, use row 13) and corner screws
    cv.hline(ox + 4, oy + 13, 8, SLOT, 404)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        cv.set(ox + x, oy + y, grey(0x2b))

    # side tile: CC face with a cable socket (6x6 dark frame, 4x4 recess, 2x2 pin)
    ox, oy = T("side")
    for y in range(16):
        cv.hline(ox, oy + y, 16, GREY_L if y >= 9 else GREY, 410 + y)
    cv.outline(ox, oy, 16, 16, EDGE, 411)
    for y in range(9, 15):
        cv.set(ox, oy + y, EDGE_L[y % len(EDGE_L)])
    cv.ascii(ox + 5, oy + 5, ["oooooo", "orrrro", "orppro", "orppro", "orrrro", "oooooo"],
             {"o": EDGE, "r": RECESS, "p": SLOT}, 412)

    # pins overlay (transparent, emissive element on top of the 5 socket faces)
    ox, oy = T("pins")
    cv.rect(ox, oy, 16, 16, CLEAR)
    if me:
        cv.ascii(ox + 7, oy + 7, ["Lp", "pd"], {"L": FLUIX_L, "p": FLUIX, "d": FLUIX_D})
    else:
        cv.ascii(ox + 7, oy + 7, ["Gg", "gb"], {"G": RS_GREEN_L, "g": RS_GREEN, "b": RS_BLUE})

    # port walls
    ox, oy = T("wall")
    cv.noise(ox, oy, 16, 16, CHASSIS if me else RS_DARK, 414)

    cv.save(os.path.join(TEX, "block", f"{kind}_cloud_bridge.png"))


BRIDGE_STATUS_REGIONS = {"cloud": (0, 0, 6, 4), "strip": (0, 8, 8, 1)}


def gen_bridge_status(state):
    """Status texture shared by both bridges: cloud emblem (6x4, alpha) + 8 px LED strip."""
    cv = Canvas(16, 16, CLEAR)
    if state == "online":
        C, c, d = CYAN_L, CYAN, CYAN_D
    elif state == "connecting":
        C, c, d = CYAN, CYAN_D, hx("1f5f73")
    else:
        C, c, d = OFF_L, OFF, hx("232627")
    cv.ascii(0, 0, [" CC   ", "CccC  ", "ccccCc", " dddd "], {"C": C, "c": c, "d": d})
    s = STATUS[state]
    cd, cm, cl = s["crystal"]
    pattern = {"online": "LMMDLMMD", "connecting": "MMDOMMDO", "offline": "MDOOOOOO"}[state]
    cv.ascii(0, 8, [pattern], {"L": cl, "M": cm, "D": cd, "O": OFF})
    cv.save(os.path.join(TEX, "block", f"cloud_bridge_status_{state}.png"))


# --------------------------------------------------------------------------- v0.7: upgrade cards, quantum core, tablets
# ATM metal colour language (own ramps: outline, dark, mid, light, highlight)
ATM_TIERS = {
    "allthemodium": [hx("5a1d08"), hx("b0420f"), hx("e8800f"), hx("ffbf35"), hx("fff09a")],
    "vibranium": [hx("0b3040"), hx("15606f"), hx("1fa886"), hx("47d993"), hx("b0f7d4")],
    "unobtainium": [hx("1e1450"), hx("3d2a8e"), hx("7a2ad8"), hx("b44fe6"), hx("eaa6f6")],
}
UPGRADE_TYPES = ("capacity", "bandwidth", "range", "efficiency", "memory")
# 8x6 symbols drawn in the card's window (S = symbol light, s = symbol mid)
UPGRADE_SYMBOLS = {
    "capacity": ["SS SS SS", "SS SS SS", "        ", "SS SS SS", "SS SS SS", "        "],
    "bandwidth": ["   S    ", "  SSSSSS", "   S    ", "    s   ", "ssssss  ", "    s   "],
    "range": [" SSSSSS ", "S      S", "  SSSS  ", " S    S ", "   SS   ", "   SS   "],
    "efficiency": ["    SS  ", "   SS   ", "  SSSS  ", "   SS   ", "  SS    ", "  S     "],
    "memory": [" S S S S", "SSSSSSSS", "SsSsSsSs", "SSSSSSSS", " S S S S", "        "],
}


CARD_NEUTRAL = [hx("232323"), hx("5f5f5f"), hx("8a8a8a"), hx("b5b5b5"), hx("e2e2e2")]  # CC grey ramp
UPGRADE_SYMBOLS_EXTRA = {
    # chunk anchor: an anchor (cyan on the neutral card)
    "chunk_anchor": ["   ss   ", "  SSSS  ", "   SS   ", "S  SS  S", "SS SS SS", " SSSSSS "],
}


def gen_upgrade_card(kind, tier, out_name=None):
    """Shared card base (circuit card with gold contacts) in the ATM metal colour + type symbol.
    tier=None -> neutral CC-grey card with a cyan symbol (non-tiered cards like the chunk anchor)."""
    o, d, m, l, h = ATM_TIERS[tier] if tier else CARD_NEUTRAL
    rows = [
        "                ",
        " oooooooooooooo ",
        " ollllllllllllo ",
        " olmmmmmmmmmmdo ",
        " olmwwwwwwwwmdo ",
        " olmwwwwwwwwmdo ",
        " olmwwwwwwwwmdo ",
        " olmwwwwwwwwmdo ",
        " olmwwwwwwwwmdo ",
        " olmwwwwwwwwmdo ",
        " olmmmmmmmmmmdo ",
        " ohmmmmmmmmmmdo ",
        " oddddddddddddo ",
        " ooGoGoGoGoGooo ",
        "  oooooooooooo  ",
        "                ",
    ]
    cv = Canvas(16, 16)
    cv.ascii(0, 0, rows, {"o": o, "l": l, "m": m, "d": d, "h": h, "w": hx("141414"),
                          "G": GOLD_L[0]})
    # tiny status notch top-right (cloud cyan) and the symbol in the window
    cv.set(12, 2, CYAN_L)
    symbol = UPGRADE_SYMBOLS.get(kind) or UPGRADE_SYMBOLS_EXTRA[kind]
    cv.ascii(4, 4, symbol, {"S": h, "s": l} if tier else {"S": CYAN_L, "s": CYAN})
    cv.save(os.path.join(TEX, "item", (out_name or f"upgrade_{kind}_{tier}") + ".png"))


def gen_quantum_core():
    """Quantum Core: cyan quantum orb inside a four-point gold star with violet sparks (ATM star vibe)."""
    cv = Canvas(16, 16)
    gold = [hx("7a3410"), hx("d7741a"), hx("f2b51d"), hx("ffe25a")]  # outline, dark, mid, light
    # star arms (taper), drawn first
    for k in range(1, 8):
        w = 2 if k < 4 else 1
        for t in range(-w + 1, w):
            for (x, y) in ((7 + t, 7 - k), (8 + t, 8 + k), (7 - k, 8 + t), (8 + k, 7 + t)):
                if 0 <= x < 16 and 0 <= y < 16:
                    cv.set(x, y, gold[2] if k < 6 else gold[3])
    # outline the star
    snap = [[cv.get(x, y) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if snap[y][x][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and snap[y + dy][x + dx][3] > 0
                                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                cv.set(x, y, gold[0])
    # orb (radius ~3.4) with light from top-left
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if r <= 3.6:
                lightness = -(dx + dy) / 5.0
                c = CYAN_L if lightness > 0.5 else CYAN if lightness > -0.4 else CYAN_D
                cv.set(x, y, c)
            elif r <= 4.3 and cv.get(x, y)[3] == 0 or (3.6 < r <= 4.3):
                cv.set(x, y, hx("1f4e5c"))
    cv.set(6, 6, hx("ffffff"))
    # violet sparks on the diagonals (unobtainium accent)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        cv.set(x, y, ATM_TIERS["unobtainium"][3])
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        cv.set(x, y, ATM_TIERS["unobtainium"][2])
    cv.save(os.path.join(TEX, "item", "quantum_core.png"))


def gen_tablet(advanced):
    """Cloud Tablet: CC pocket-computer silhouette (portrait), dark screen with a cyan cloud."""
    body, band = (GOLD, GOLD_L) if advanced else (GREY, GREY_L)
    rows = [
        "                ",
        "  oooooooooooo  ",
        " obbbbbbbbbbbbo ",
        " obkkkkkkkkkkbo ",
        " obkkkkkkkkkkbo ",
        " obkkkkkkkkkkbo ",
        " obkkkkkkkkkkbo ",
        " obkkkkkkkkkkbo ",
        " obkkkkkkkkkkbo ",
        " obkkkkkkkkkkbo ",
        " oLLLLLLLLLLLLo ",
        " oLLLLLLLLLLLLo ",
        " oLLLLLLLSSLLLo ",
        " oLLLLLLLLLLLLo ",
        "  oooooooooooo  ",
        "                ",
    ]
    cv = Canvas(16, 16)
    cv.ascii(0, 0, rows, {"o": grey(0x24), "b": body, "L": band, "k": TERM, "S": grey(0x2b)})
    # cloud on the screen + two widget bars (the dashboard look)
    cv.ascii(4, 4, [" Cc  ", "CccCc", "ccccc"], {"C": CYAN_L, "c": CYAN})
    cv.hline(4, 8, 4, [GREEN])
    cv.hline(9, 8, 3, [CYAN_D])
    if advanced:
        cv.set(13, 12, CYAN)  # status light
    cv.save(os.path.join(TEX, "item", "advanced_cloud_tablet.png" if advanced else "cloud_tablet.png"))


def gen_analyzer():
    """Cloud Analyzer: CC pocket computer turned scanner. Stone-grey body, small screen with cyan
    signal bars, antenna nub with a cyan tip, two buttons and a green LED on the lighter band."""
    rows = [
        "          ooo   ",
        "          oCo   ",
        "          oso   ",
        "  oooooooooooo  ",
        "  obbbbbbbbbbo  ",
        "  obkkkkkkkkbo  ",
        "  obkkkkkkkkbo  ",
        "  obkkkkkkkkbo  ",
        "  obkkkkkkkkbo  ",
        "  obkkkkkkkkbo  ",
        "  oLLLLLLLLLLo  ",
        "  oLSLSLLLLLLo  ",
        "  oLLLLLLLGLLo  ",
        "  oLLLLLLLLLLo  ",
        "  oooooooooooo  ",
        "                ",
    ]
    cv = Canvas(16, 16)
    cv.ascii(0, 0, rows, {"o": grey(0x24), "b": GREY, "L": GREY_L, "k": TERM, "S": grey(0x2b),
                          "s": grey(0x3b), "C": CYAN_L, "G": GREEN})
    for i, x in enumerate((5, 7, 9, 11)):          # signal bars, rising to the right
        for y in range(9 - i, 10):
            cv.set(x, y, CYAN_L if y == 9 - i else CYAN)
    cv.save(os.path.join(TEX, "item", "cloud_analyzer.png"))


HUD_REGIONS = {f"bars_{n}": (16 * n, 0, 16, 16) for n in range(5)}
HUD_REGIONS.update({"arrow": (80, 0, 16, 16), "no_coverage": (96, 0, 16, 16)})


def gen_hud():
    """textures/gui/hud.png (128x16): bars_0..bars_4, arrow (points up; rotate in code), no_coverage.
    Every icon has a 1 px dark outline so it reads over any world background."""
    cv = Canvas(128, 16, CLEAR)
    out, off, off_l = hx("101214"), hx("3a3f42"), hx("4c5357")

    def bar(ox, i, lit, weak):
        x0, h = ox + 1 + 4 * i, 3 + 3 * i
        y0 = 15 - h
        body, top = (YELLOW, YELLOW_L) if weak else (CYAN, CYAN_L)
        if not lit:
            body, top = off, off_l
        cv.rect(x0, y0 - 1, 4, h + 1, out)
        cv.rect(x0 + 1, y0, 2, h - 1, body)
        cv.rect(x0 + 1, y0, 2, 1, top)

    for n in range(5):
        ox = 16 * n
        for i in range(4):
            bar(ox, i, i < n, n == 1)
    # arrow (points up), symmetric: head rows 0..5, shoulder row 6, 2 px stem with outline down to row 14
    ax = 80
    for r in range(6):
        x0, x1 = 7 - r, 8 + r
        cv.set(ax + x0, r, out); cv.set(ax + x1, r, out)
        for x in range(x0 + 1, x1):
            cv.set(ax + x, r, CYAN_L if x < 7 else (CYAN_D if x == x1 - 1 and r > 1 else CYAN))
    for x in range(2, 14):
        cv.set(ax + x, 6, out)
    for y in range(6, 14):
        cv.set(ax + 6, y, out); cv.set(ax + 9, y, out)
        cv.set(ax + 7, y, CYAN); cv.set(ax + 8, y, CYAN_D)
    for x in range(6, 10):
        cv.set(ax + x, 14, out)
    # no coverage: dimmed bars with a red slash
    for i in range(4):
        bar(96, i, False, False)
    for k in range(13):
        x, y = 97 + k, 1 + k
        cv.set(x, y, out); cv.set(x + 1, y, RED); cv.set(x + 2, y, RED_D if k % 2 else RED); cv.set(x + 3, y, out)
    cv.save(os.path.join(TEX, "gui", "hud.png"))


def gen_fiber():
    """Fiber cable / junction / ender fiber link atlas (textures/block/fiber.png)."""
    L = LAYOUTS["fiber"]
    cv = Canvas(L["size"], L["size"], grey(0x30))
    T = lambda n: tile_origin("fiber", n)
    ox, oy = T("jacket")             # CC cable grey, flat
    cv.noise(ox, oy, 16, 16, CABLE, 700)
    ox, oy = T("core")               # glowing fiber core
    for y in range(16):
        for x in range(16):
            cv.set(ox + x, oy + y, CYAN_L if (x + y) % 4 == 0 else CYAN)
    ox, oy = T("dark")
    cv.noise(ox, oy, 16, 16, CHASSIS, 701)

    def socket(ox, oy):
        cv.outline(ox + 5, oy + 5, 6, 6, EDGE, 702)
        cv.rect(ox + 6, oy + 6, 4, 4, RECESS[0])

    # junction box faces: box spans block 2..14 -> CC outline at cols/rows 2 and 13, band, socket
    ox, oy = T("junction")
    for y in range(16):
        cv.hline(ox, oy + y, 16, GREY_L if y >= 9 else GREY, 703 + y)
    cv.outline(ox + 2, oy + 2, 12, 12, EDGE, 704)
    for y in range(9, 13):
        cv.set(ox + 2, oy + y, EDGE_L[y % len(EDGE_L)])
    socket(ox, oy)
    # ender fiber link: gold CC casing, sockets on the sides, eye port (dark floor) on top
    ox, oy = T("link_side")
    for y in range(16):
        cv.hline(ox, oy + y, 16, GOLD_L if y >= 9 else GOLD, 705 + y)
    cv.outline(ox, oy, 16, 16, EDGE, 706)
    for y in range(9, 15):
        cv.set(ox, oy + y, EDGE_L[y % len(EDGE_L)])
    socket(ox, oy)
    ox, oy = T("link_top")
    for y in range(16):
        cv.hline(ox, oy + y, 16, GOLD, 707 + y)
    cv.outline(ox, oy, 16, 16, EDGE, 708)
    cv.rect(ox + 5, oy + 5, 6, 6, RECESS[0])
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        cv.set(ox + x, oy + y, CYAN_D)
    ox, oy = T("link_body")
    cv.noise(ox, oy, 16, 16, GOLD, 709)
    x, y, _, _ = L["regions"]["eye"]
    cv.ascii(x, y, ["dEEd", "EkCE", "EkEE", "dEEd"], {"d": ENDER_D, "E": ENDER, "k": hx("102a22"), "C": CYAN_L})
    cv.save(os.path.join(TEX, "block", "fiber.png"))


# --------------------------------------------------------------------------- v0.7: cloud display (CC-monitor-like multiblock)
DISPLAY_STATES = ("none", "l", "r", "lr", "u", "d", "ud", "lu", "ld", "ru", "rd", "lud", "rud", "lru", "lrd", "lrud")
DISPLAY_SIDE = ("none", "u", "ud", "d")   # east/west faces: only vertical continuity matters
DISPLAY_TOP = ("none", "l", "lr", "r")    # up/down faces: only horizontal continuity matters


def display_tiles():
    """Tile index per face kind in textures/block/display.png (8x8 tiles of 16)."""
    t = {}
    for i, st in enumerate(DISPLAY_STATES):
        t[("front", st)] = (i % 8, i // 8)
        t[("back", st)] = (i % 8, 2 + i // 8)
    for i, st in enumerate(DISPLAY_SIDE):
        t[("side", st)] = (i, 4)
    for i, st in enumerate(DISPLAY_TOP):
        t[("top", st)] = (4 + i, 4)
    return t


def gen_display():
    """Cloud Display atlas. "l"/"r"/"u"/"d" = a neighbour on the viewer's left/right/up/down (seen
    from the front) -> that edge of the frame is open so a wall of displays reads as one screen.
    Front: 3 px frame (dark outline, gold, cyan line) around a CC-black screen. Back: gold CC casing.
    Side textures (east/west) only care about u/d, top textures (up/down) only about l/r."""
    cv = Canvas(128, 128, CLEAR)
    tiles = display_tiles()

    def has(st, k):
        return st != "none" and k in st

    for (kind, st), (c, r) in tiles.items():
        ox, oy = c * 16, r * 16
        if kind == "front":
            cv.rect(ox, oy, 16, 16, TERM)
            # image-left = viewer's left (l), image-top = up (u). Draw inner layer first (cyan line,
            # then gold, then the dark outline) so outer layers win at the corners.
            bands = {"l": ("v", 0, 1), "r": ("v", 15, -1), "u": ("h", 0, 1), "d": ("h", 15, -1)}
            for depth, col in ((2, CYAN_D), (1, GOLD[0]), (0, None)):
                for k, (axis, start, step) in bands.items():
                    if has(st, k):
                        continue
                    pos = start + step * depth
                    shades = EDGE if col is None else [col]
                    if axis == "v":
                        cv.vline(ox + pos, oy, 16, shades, 600 + depth)
                    else:
                        cv.hline(ox, oy + pos, 16, shades, 600 + depth)
        else:
            for y in range(16):
                cv.hline(ox, oy + y, 16, GOLD_L if y >= 9 else GOLD, 610 + y)
            if kind == "back":
                # the back is seen mirrored: viewer's left (l) is image-right
                if not has(st, "l"):
                    cv.vline(ox + 15, oy, 16, EDGE, 611)
                if not has(st, "r"):
                    cv.vline(ox, oy, 16, EDGE, 612)
                if not has(st, "u"):
                    cv.hline(ox, oy, 16, EDGE, 613)
                if not has(st, "d"):
                    cv.hline(ox, oy + 15, 16, EDGE, 614)
            elif kind == "side":
                cv.vline(ox, oy, 16, EDGE, 615)
                cv.vline(ox + 15, oy, 16, EDGE, 616)
                if not has(st, "u"):
                    cv.hline(ox, oy, 16, EDGE, 617)
                if not has(st, "d"):
                    cv.hline(ox, oy + 15, 16, EDGE, 618)
            else:  # top / bottom face: u = x, so the viewer's left (+x) is image-right
                cv.hline(ox, oy, 16, EDGE, 619)
                cv.hline(ox, oy + 15, 16, EDGE, 620)
                if not has(st, "l"):
                    cv.vline(ox + 15, oy, 16, EDGE, 621)
                if not has(st, "r"):
                    cv.vline(ox, oy, 16, EDGE, 622)
    cv.save(os.path.join(TEX, "block", "display.png"))


def gui_slot(cv, x, y, ghost=None, gold=False):
    """18x18 inventory slot in CC GUI colours (dark top-left, light bottom-right, #8b8b8b well)."""
    cv.rect(x, y, 18, 18, GUI_GREY)
    cv.rect(x, y, 17, 1, hx("373737")); cv.rect(x, y, 1, 17, hx("373737"))
    cv.rect(x + 1, y + 17, 17, 1, hx("ffffff")); cv.rect(x + 17, y + 1, 1, 17, hx("ffffff"))
    cv.rect(x + 1, y + 1, 16, 16, hx("8b8b8b"))
    if gold:
        cv.outline(x + 1, y + 1, 16, 16, [GOLD[0]])
    if ghost == "card":
        cv.ascii(x + 3, y + 4, ["oooooooooooo", "o..........o", "o.oooooo...o", "o.o....o...o", "o.oooooo...o",
                                "o..........o", "oooooooooooo", " o o o o o  "],
                 {"o": hx("7a7a7a"), ".": hx("8b8b8b")})
    elif ghost == "star":
        cv.ascii(x + 4, y + 4, ["    oo    "[:10], "    oo    ", "   oooo   ", "oooooooooo", "oooooooooo",
                                "   oooo   ", "    oo    ", "    oo    "], {"o": hx("7a7a7a")})


# --------------------------------------------------------------------------- relays (v0.4)
RELAY_STATUS_REGIONS = {"strip": (0, 0, 6, 1), "tip": (0, 2, 1, 1)}


def gen_relay(tier):
    """Relay atlas. tier = normal (stone) | advanced (gold) | ender (gold, eye core).
    Base = 12x6x12 CC casing at block x/z 2..14, y 0..6: the side tile has the CC outline at
    cols 2/13 and rows 10/15, the lighter band and a dark LED slot (y 4..5 -> row 11, x 5..11)."""
    L = LAYOUTS["relay"]
    cv = Canvas(L["size"], L["size"], grey(0x30))
    T = lambda n: tile_origin("relay", n)
    gold = tier != "normal"
    body, body_l = (GOLD, GOLD_L) if gold else (GREY, GREY_L)

    ox, oy = T("side")
    for y in range(16):
        cv.hline(ox, oy + y, 16, body_l if y >= 13 else body, 500 + y)
    cv.outline(ox + 2, oy + 10, 12, 6, EDGE, 501)
    for y in range(13, 15):
        cv.set(ox + 2, oy + y, EDGE_L[y % len(EDGE_L)])
    cv.hline(ox + 5, oy + 11, 6, SLOT, 502)
    ox, oy = T("top")
    for y in range(16):
        cv.hline(ox, oy + y, 16, body, 510 + y)
    cv.outline(ox + 2, oy + 2, 12, 12, EDGE, 511)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        cv.set(ox + x, oy + y, grey(0x2b))
    for name, shades, seed in (("body", body, 520), ("body_l", body_l, 521), ("dark", CHASSIS, 522)):
        ox, oy = T(name)
        cv.noise(ox, oy, 16, 16, shades, seed)
    ox, oy = T("ring")
    cv.noise(ox, oy, 16, 16, GOLD_L if gold else GREY_L, 523)
    ox, oy = T("beacon")
    for y in range(16):
        for x in range(16):
            cv.set(ox + x, oy + y, CYAN_L if (x + y) % 3 == 0 else CYAN)
    ox, oy = T("core")
    for y in range(16):
        for x in range(16):
            cv.set(ox + x, oy + y, ENDER_L if (x + 2 * y) % 5 == 0 else ENDER)
    # ender eye face 4x4: green iris, dark pupil slit, cyan glint (rotation reads)
    x, y, _, _ = L["regions"]["eye"]
    cv.ascii(x, y, ["dEEd", "EkCE", "EkEE", "dEEd"],
             {"d": ENDER_D, "E": ENDER, "k": hx("102a22"), "C": CYAN_L})
    name = {"normal": "relay", "advanced": "relay_advanced", "ender": "relay_ender"}[tier]
    cv.save(os.path.join(TEX, "block", name + ".png"))


def gen_relay_status(state):
    """6 px LED strip + antenna tip. no_power = all LEDs dark (unlit)."""
    cv = Canvas(16, 16, OFF)
    if state == "no_power":
        cv.ascii(0, 0, ["oOoOoO"], {"o": OFF, "O": OFF_L})
        cv.set(0, 2, OFF_L)
    else:
        cd, cm, cl = STATUS[state]["crystal"]
        pattern = {"online": "LMDLMD", "connecting": "MDOMDO", "offline": "MDOOOO"}[state]
        cv.ascii(0, 0, [pattern], {"L": cl, "M": cm, "D": cd, "O": OFF})
        cv.set(0, 2, cm)
    cv.save(os.path.join(TEX, "block", f"relay_status_{state}.png"))


# --------------------------------------------------------------------------- GUI (v0.3)
# CC GUI language (measured from CC's computer border, drawn here from scratch):
#   rounded 1 px outline, 2 px highlight top/left, flat body, 2 px shadow bottom/right;
#   insets invert it: shadow above/left, outline, near-black fill, highlight below/right.
GUI_TIERS = {
    # name: (outline, highlight, body, shadow)
    "gold": (hx("232323"), hx("e8e6a0"), hx("cfcf74"), hx("8e8f4b")),
    "stone": (hx("232323"), hx("f4f4f4"), hx("c6c6c6"), hx("5f5f5f")),
    "dark": (hx("161616"), hx("4a4f52"), hx("33373a"), hx("1f2224")),
    "cyan": (hx("102a33"), CYAN_L, CYAN, CYAN_D),
}
TERM = hx("111111")      # CC terminal black
TERM_LINE = hx("1b1f21")  # faint row separators inside terminal panels


def gui_panel(cv, x, y, w, h, tier="gold", round_=True):
    o, hi, b, sh = GUI_TIERS[tier]
    cv.rect(x + 1, y + 1, w - 2, h - 2, b)
    # highlight 2 px top/left, shadow 2 px bottom/right
    cv.rect(x + 1, y + 1, w - 2, 2, hi)
    cv.rect(x + 1, y + 1, 2, h - 2, hi)
    cv.rect(x + 1, y + h - 3, w - 2, 2, sh)
    cv.rect(x + w - 3, y + 1, 2, h - 2, sh)
    cv.set(x + w - 3, y + 1, b); cv.set(x + w - 3, y + 2, b)          # soft corner where hi meets shadow
    cv.set(x + 1, y + h - 3, b); cv.set(x + 2, y + h - 3, b)
    # outline with rounded (2 px cut) corners
    cv.rect(x + 2, y, w - 4, 1, o)
    cv.rect(x + 2, y + h - 1, w - 4, 1, o)
    cv.rect(x, y + 2, 1, h - 4, o)
    cv.rect(x + w - 1, y + 2, 1, h - 4, o)
    if round_:
        for (cx, cy) in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
            cv.set(cx, cy, o)
        for (cx, cy) in ((x, y), (x + 1, y), (x, y + 1), (x + w - 1, y), (x + w - 2, y), (x + w - 1, y + 1),
                         (x, y + h - 1), (x + 1, y + h - 1), (x, y + h - 2),
                         (x + w - 1, y + h - 1), (x + w - 2, y + h - 1), (x + w - 1, y + h - 2)):
            cv.set(cx, cy, CLEAR)
    else:
        cv.outline(x, y, w, h, [o])


def gui_inset(cv, x, y, w, h, tier="gold", fill=TERM):
    """Terminal-style inset occupying (x, y, w, h) including its 1 px bevel ring."""
    o, hi, b, sh = GUI_TIERS[tier]
    cv.rect(x, y, w, 1, sh)
    cv.rect(x, y, 1, h, sh)
    cv.rect(x, y + h - 1, w, 1, hi)
    cv.rect(x + w - 1, y, 1, h, hi)
    cv.set(x, y + h - 1, b); cv.set(x + w - 1, y, b)
    cv.outline(x + 1, y + 1, w - 2, h - 2, [o])
    cv.rect(x + 2, y + 2, w - 4, h - 4, fill)


def gui_button(cv, x, y, w, h, state, tier="stone"):
    """Vanilla-sized button in CC colours. state: normal|hover|pressed|disabled."""
    o, hi, b, sh = GUI_TIERS[tier]
    if state == "hover":
        hi, b, sh = CYAN_L, hx("9fd9ea"), CYAN_D
    elif state == "disabled":
        o, hi, b, sh = hx("2b2b2b"), hx("8a8a8a"), hx("6f6f6f"), hx("555555")
    cv.rect(x, y, w, h, o)
    if state == "pressed":
        cv.rect(x + 1, y + 1, w - 2, h - 2, sh)
        cv.rect(x + 2, y + 2, w - 3, h - 3, b)
        cv.rect(x + 2, y + h - 2, w - 3, 1, hi)
    else:
        cv.rect(x + 1, y + 1, w - 2, h - 2, b)
        cv.rect(x + 1, y + 1, w - 2, 1, hi)
        cv.rect(x + 1, y + 1, 1, h - 2, hi)
        cv.rect(x + 1, y + h - 2, w - 2, 1, sh)
        cv.rect(x + w - 2, y + 1, 1, h - 2, sh)


# GUI layout contract: docs/GUI_LAYOUT.md (written by the Java side). All edges are uniform along
# their stretch direction and centres are flat, so 9-slice / 3-slice stretching never distorts.
GUI_GREY = hx("c6c6c6")
GUI_LIGHT = hx("e8e8e8")
GUI_DARK = hx("5a5a5a")
GUI_OUT = hx("232323")
GUI_BLACK = hx("000000")
DANGER = (hx("d9534f"), hx("ee8a86"), hx("9c2f2c"))
ACCEPT = (hx("5cb85c"), hx("93d693"), hx("3a7d3a"))
GUI_ACCENT = {
    # tier: (frame body, frame highlight, frame shadow, accent line, strip body, strip light, strip dark)
    "mainframe": (hx("cfcf74"), hx("e8e6a0"), hx("8e8f4b"), hx("232323"),
                  hx("dcd773"), hx("ecec9c"), hx("a9a454")),
    "uplink": (hx("c6c6c6"), hx("f4f4f4"), hx("7a7a7a"), CYAN_D,
               CYAN, CYAN_L, CYAN_D),
}


def _rounded_rect(cv, x, y, w, h, outline, fill, hi=None, sh=None, r=1, bottom_open=False):
    """Filled rect with 1 px outline, corners cut by r px, optional 1 px bevel (hi top/left, sh bottom/right)."""
    cv.rect(x, y, w, h, outline)
    cv.rect(x + 1, y + 1, w - 2, h - (1 if bottom_open else 2), fill)
    if hi:
        cv.rect(x + 1, y + 1, w - 2, 1, hi)
        cv.rect(x + 1, y + 1, 1, h - (1 if bottom_open else 2), hi)
    if sh:
        if not bottom_open:
            cv.rect(x + 1, y + h - 2, w - 2, 1, sh)
        cv.rect(x + w - 2, y + 1, 1, h - (1 if bottom_open else 2), sh)
        if hi:
            cv.set(x + w - 2, y + 1, fill)
            if not bottom_open:
                cv.set(x + 1, y + h - 2, fill)
    if bottom_open:
        cv.rect(x + 1, y + h - 1, w - 2, 1, fill)
    for k in range(r):
        for (cx, cy) in ((x + k, y), (x, y + k), (x + w - 1 - k, y), (x + w - 1, y + k)):
            cv.set(cx, cy, CLEAR)
        if not bottom_open:
            for (cx, cy) in ((x + k, y + h - 1), (x, y + h - 1 - k), (x + w - 1 - k, y + h - 1), (x + w - 1, y + h - 1 - k)):
                cv.set(cx, cy, CLEAR)
    if r:
        cv.set(x + 1, y + 1, outline); cv.set(x + w - 2, y + 1, outline)
        if not bottom_open:
            cv.set(x + 1, y + h - 2, outline); cv.set(x + w - 2, y + h - 2, outline)


def gen_gui_screen(kind):
    """textures/gui/{mainframe,uplink}.png, layout per docs/GUI_LAYOUT.md (256x256)."""
    fb, fh, fs, line, sb, sl, sd = GUI_ACCENT[kind]
    cv = Canvas(256, 256, CLEAR)

    # Panel frame 0,0 48x48, 9-slice border 8: tier-coloured CC bevel ring (px 0..5), a 1 px accent
    # line (px 6), then the neutral inner panel with its own 1 px bevel (px 7) and flat #C6C6C6 body.
    x, y, w, h = 0, 0, 48, 48
    cv.rect(x + 1, y + 1, w - 2, h - 2, fb)
    cv.rect(x + 1, y + 1, w - 2, 2, fh); cv.rect(x + 1, y + 1, 2, h - 2, fh)
    cv.rect(x + 1, y + h - 3, w - 2, 2, fs); cv.rect(x + w - 3, y + 1, 2, h - 2, fs)
    for (cx, cy) in ((w - 3, 1), (w - 3, 2), (w - 2, 2), (1, h - 3), (2, h - 3), (2, h - 2)):
        cv.set(x + cx, y + cy, fb)
    _round = [(0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1), (0, h - 1), (1, h - 1), (0, h - 2),
              (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)]
    cv.rect(x + 2, y, w - 4, 1, GUI_OUT); cv.rect(x + 2, y + h - 1, w - 4, 1, GUI_OUT)
    cv.rect(x, y + 2, 1, h - 4, GUI_OUT); cv.rect(x + w - 1, y + 2, 1, h - 4, GUI_OUT)
    for (cx, cy) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        cv.set(x + cx, y + cy, GUI_OUT)
    for (cx, cy) in _round:
        cv.set(x + cx, y + cy, CLEAR)
    cv.outline(x + 6, y + 6, w - 12, h - 12, [line])
    cv.rect(x + 7, y + 7, w - 14, h - 14, GUI_GREY)
    cv.rect(x + 7, y + 7, w - 14, 1, GUI_LIGHT); cv.rect(x + 7, y + 7, 1, h - 14, GUI_LIGHT)
    cv.rect(x + 7, y + h - 8, w - 14, 1, GUI_DARK); cv.rect(x + w - 8, y + 7, 1, h - 14, GUI_DARK)

    # Title bar 48,0 48x16, 3-slice caps 6: accent strip, rounded caps, bevel top/bottom
    _rounded_rect(cv, 48, 0, 48, 16, GUI_OUT, sb, sl, sd, r=2)

    # Terminal inset 0,48 24x24, 9-slice border 4: panel bevel ring (dark TL / light BR), outline,
    # 1 px inner shadow, #111 body
    x, y = 0, 48
    cv.rect(x, y, 24, 24, GUI_GREY)
    cv.rect(x, y, 24, 1, GUI_DARK); cv.rect(x, y, 1, 24, GUI_DARK)
    cv.rect(x, y + 23, 24, 1, GUI_LIGHT); cv.rect(x + 23, y, 1, 24, GUI_LIGHT)
    cv.outline(x + 1, y + 1, 22, 22, [GUI_OUT])
    cv.rect(x + 2, y + 2, 20, 20, TERM)
    cv.rect(x + 2, y + 2, 20, 1, GUI_BLACK); cv.rect(x + 2, y + 2, 1, 20, GUI_BLACK)

    # Tabs 32x14, caps 5. Inactive: darker grey, closed bottom. Active: inset-black body with a
    # 2 px accent top edge, open bottom so it merges into the terminal inset (use light text).
    _rounded_rect(cv, 48, 16, 32, 14, GUI_OUT, hx("adadad"), GUI_LIGHT, hx("7f7f7f"), r=2)
    _rounded_rect(cv, 48, 30, 32, 14, GUI_OUT, TERM, None, None, r=2, bottom_open=True)
    cv.rect(50, 31, 28, 2, sb); cv.rect(49, 32, 1, 1, sb); cv.rect(78, 32, 1, 1, sb)
    cv.rect(50, 31, 28, 1, sl)

    # Status LEDs 8x8 at 96/104/112/120,0: dark socket, 6x6 round lens, light from top-left
    leds = [(RED, RED_L, RED_D), (YELLOW, YELLOW_L, YELLOW_D), (GREEN, GREEN_L, GREEN_D),
            (hx("6e1f1f"), hx("8a3a3a"), hx("3d1010"))]
    for i, (m_, l_, d_) in enumerate(leds):
        ox = 96 + 8 * i
        cv.ascii(ox, 0, ["  oooo  ", " ollmmo ", "olmmmmdo", "ommmmmdo", "ommmmmdo", "ommmmddo",
                          " odddd o"[:7] + " ", "  oooo  "],
                 {"o": GUI_OUT, "l": l_, "m": m_, "d": d_})
        cv.set(ox + 6, 6, GUI_OUT)
        if i == 3:  # killed: crossed out
            for k in range(2, 6):
                cv.set(ox + k, k, GUI_OUT); cv.set(ox + 7 - k, k, GUI_OUT)

    # Big status badge 96,8 64x14, caps 5: neutral dark pill (terminal look, light text + LED on it)
    _rounded_rect(cv, 96, 8, 64, 14, GUI_OUT, hx("1c1c1c"), hx("2e2e2e"), hx("0c0c0c"), r=2)

    # v0.7 upgrade slots (18x18, no slicing): plain, empty-card ghost, quantum-core slot (gold rim, star ghost)
    gui_slot(cv, 0, 80)
    gui_slot(cv, 18, 80, ghost="card")
    gui_slot(cv, 36, 80, ghost="star", gold=True)

    cv.save(os.path.join(TEX, "gui", f"{kind}.png"))


def gen_gui_widgets():
    """textures/gui/widgets.png, layout per docs/GUI_LAYOUT.md (256x256)."""
    cv = Canvas(256, 256, CLEAR)
    btn = (hx("d8d8d8"), hx("f6f6f6"), hx("8c8c8c"))

    def button(x, y, body, hi, sh, outline=GUI_OUT, pressed=False, flat=False):
        if pressed:
            _rounded_rect(cv, x, y, 48, 14, outline, body, sh, hi, r=1)
        elif flat:
            _rounded_rect(cv, x, y, 48, 14, outline, body, None, None, r=1)
        else:
            _rounded_rect(cv, x, y, 48, 14, outline, body, hi, sh, r=1)

    button(0, 0, *btn)
    button(0, 14, hx("e6e6e6"), hx("ffffff"), hx("9a9a9a"), outline=hx("2a7f99"))
    cv.rect(1, 15, 46, 1, CYAN); cv.rect(1, 26, 46, 1, CYAN)          # cyan outline inside the edge
    cv.rect(1, 15, 1, 12, CYAN); cv.rect(46, 15, 1, 12, CYAN)
    cv.set(1, 15, hx("2a7f99")); cv.set(46, 15, hx("2a7f99")); cv.set(1, 26, hx("2a7f99")); cv.set(46, 26, hx("2a7f99"))
    button(0, 28, hx("bcbcbc"), hx("f6f6f6"), hx("7a7a7a"), pressed=True)
    button(0, 42, hx("a3a3a3"), None, None, outline=GUI_DARK, flat=True)
    # danger / accept
    button(48, 0, *DANGER)
    button(48, 14, hx("e5716d"), hx("f6aaa7"), hx("a8383a"))
    button(48, 28, *ACCEPT)
    button(48, 42, hx("74c874"), hx("aee3ae"), hx("468f46"))

    # toggles 24x12: rounded dark track, 10x10 knob (CC grey bevel); on = cyan track, knob right
    for (y, on) in ((0, False), (12, True)):
        track = CYAN_D if on else hx("2b2b2b")
        track_l = CYAN if on else hx("3a3a3a")
        _rounded_rect(cv, 96, y, 24, 12, GUI_OUT, track, None, None, r=2)
        cv.rect(98, y + 2, 20, 1, track_l)
        kx = 96 + (13 if on else 1)
        _rounded_rect(cv, kx, y + 1, 10, 10, GUI_OUT, hx("d8d8d8"), hx("f6f6f6"), hx("8c8c8c"), r=1)
        if on:
            cv.rect(99, y + 5, 1, 2, CYAN_L)  # tiny "I" mark in the track

    # text field 48x14 caps 3 (dark inset), focused = cyan 1 px border
    for (y, focus) in ((56, False), (70, True)):
        cv.rect(0, y, 48, 14, GUI_GREY)
        cv.rect(0, y, 48, 1, GUI_DARK); cv.rect(0, y, 1, 14, GUI_DARK)
        cv.rect(0, y + 13, 48, 1, GUI_LIGHT); cv.rect(47, y, 1, 14, GUI_LIGHT)
        cv.outline(1, y + 1, 46, 12, [CYAN if focus else GUI_BLACK])
        cv.rect(2, y + 2, 44, 10, TERM)

    # list rows 48x18 caps 3: subtle bands on the inset, 1 px separator; hover = lighter + cyan marker
    for (y, body, mark) in ((56, hx("1a1a1a"), None), (74, hx("242424"), CYAN)):
        cv.rect(48, y, 48, 18, body)
        cv.rect(48, y + 17, 48, 1, hx("0d0d0d"))
        if mark:
            cv.rect(48, y, 2, 17, mark)

    # scrollbar track 6x32 / thumb 6x16 (3-slice vertical, caps 3)
    _rounded_rect(cv, 96, 24, 6, 32, GUI_BLACK, hx("0a0a0a"), None, None, r=1)
    _rounded_rect(cv, 102, 24, 6, 16, CYAN_D, CYAN, CYAN_L, CYAN_D, r=1)

    # copy icon 9x9 (two overlapping pages) and warning icon 9x9
    cv.ascii(112, 24, ["oooooo   ", "o....o   ", "o.oooooo ", "o.o....o ", "ooo....o ",
                       "  o....o ", "  o....o ", "  oooooo ", "         "],
             {"o": GUI_OUT, ".": hx("f4f4f4")})
    cv.ascii(121, 24, ["    o    ", "   oyo   ", "   oyo   ", "  oykyo  ", "  oykyo  ",
                       " oyykyyo ", " oyyyyyo ", "oyyykyyyo", "ooooooooo"],
             {"o": hx("5a4310"), "y": YELLOW, "k": GUI_OUT})
    cv.save(os.path.join(TEX, "gui", "widgets.png"))


# --------------------------------------------------------------------------- items
def gen_items():
    # Keycard: light grey card, dark outline, gold chip, small cyan cloud, magnetic stripe.
    card = [
        "                ",
        "                ",
        "                ",
        " oooooooooooooo ",
        " oLLLLLLLLLLLLo ",
        " oLgggLLLLLLLLo ",
        " oLgGgLLLLccLLo ",
        " oLgggLLLcCCcLo ",
        " oLLLLLLLccccLo ",
        " ollllllllllllo ",
        " okkkkkkkkkkkko ",
        " ollllllllllllo ",
        " oooooooooooooo ",
        "                ",
        "                ",
        "                ",
    ]
    pal = {"o": grey(0x2d), "L": GREY_L, "l": GREY, "g": hx("b9b45e"), "G": GOLD_L[0],
           "c": CYAN, "C": CYAN_L, "k": grey(0x3b)}
    cv = Canvas(16, 16)
    cv.ascii(0, 0, card, pal, 300)
    cv.save(os.path.join(TEX, "item", "cloud_link_card.png"))

    # Cloud core: cyan crystal (light from top left) held by a gold setting.
    core = [
        "                ",
        "       oo       ",
        "      oCCo      ",
        "     oCcCco     ",
        "    oCccccco    ",
        "   GoCcccccoG   ",
        "  GGocccccdoGG  ",
        "  gGoccccddogg  ",
        "  ggocccdddogg  ",
        "   goccddddog   ",
        "    ocdddddo    ",
        "     odddddo    ",
        "    GGoddoGG    ",
        "     gggggg     ",
        "                ",
        "                ",
    ]
    pal = {"o": hx("1f4e5c"), "C": CYAN_L, "c": CYAN, "d": CYAN_D, "G": GOLD_L[0], "g": hx("c9c461")}
    cv = Canvas(16, 16)
    cv.ascii(0, 0, core, pal, 310)
    cv.save(os.path.join(TEX, "item", "cloud_core.png"))

    # (the data_packet particle was removed in v0.4, see docs/SPEC.md)


def main():
    gen_mainframe()
    gen_mainframe_glass()
    gen_mainframe_hologram()
    for st in STATUS:
        gen_status("mainframe_leds", st)
        gen_status("uplink_status", st)
    gen_uplink(False)
    gen_uplink(True)
    gen_bridge("me")
    gen_bridge("rs")
    for st in STATUS:
        gen_bridge_status(st)
    for tier in ("normal", "advanced", "ender"):
        gen_relay(tier)
    for st in ("online", "connecting", "offline", "no_power"):
        gen_relay_status(st)
    for kind in UPGRADE_TYPES:
        for tier in ATM_TIERS:
            gen_upgrade_card(kind, tier)
    gen_upgrade_card("chunk_anchor", None, "upgrade_chunk_anchor")
    gen_quantum_core()
    gen_tablet(False)
    gen_tablet(True)
    gen_display()
    gen_analyzer()
    gen_fiber()
    gen_hud()
    gen_gui_screen("mainframe")
    gen_gui_screen("uplink")
    gen_gui_widgets()
    gen_items()
    print("textures written to", TEX)


if __name__ == "__main__":
    main()
