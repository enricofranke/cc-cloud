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


# --------------------------------------------------------------------------- items / particle
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

    # Data packet particle: 4x4 cyan cube, light top-left.
    cv = Canvas(4, 4)
    cv.ascii(0, 0, ["CCCc", "Cccd", "Cccd", "cddd"], {"C": CYAN_L, "c": CYAN, "d": CYAN_D})
    cv.save(os.path.join(TEX, "particle", "data_packet.png"))


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
    gen_items()
    print("textures written to", TEX)


if __name__ == "__main__":
    main()
