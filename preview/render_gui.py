#!/usr/bin/env python3
"""GUI mock-up renderer for CC: Cloud (needs Pillow).

Composes the generated GUI textures (textures/gui/*.png) with sample content and renders text
with the vanilla Minecraft ASCII font, then scales everything by the GUI scale (default 3) with
nearest-neighbour, like the game does. The font is read from the local Minecraft client jar
(or an extracted copy) for the preview only; nothing is copied into the mod.

    python preview/render_gui.py      -> preview/renders/gui_mockup.png
"""
from __future__ import annotations

import io
import json
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GUI = os.path.join(ROOT, "src", "main", "resources", "assets", "cccloud", "textures", "gui")
FONT_SOURCES = [
    "~/ref/"
    "mcfont/assets/minecraft/textures/font/ascii.png",
    os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar"),
]


def load_font():
    for src in FONT_SOURCES:
        if src.endswith(".png") and os.path.exists(src):
            return Image.open(src).convert("RGBA")
        if src.endswith(".jar") and os.path.exists(src):
            with zipfile.ZipFile(src) as z:
                return Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
    raise SystemExit("vanilla font not found (ascii.png)")


class Font:
    """Vanilla bitmap font: 16x16 grid of 8x8 glyphs, width = rightmost opaque column + 1."""

    def __init__(self):
        self.img = load_font()
        self.cell = self.img.width // 16
        self.widths = {}
        for code in range(256):
            gx, gy = (code % 16) * self.cell, (code // 16) * self.cell
            w = 0
            for x in range(self.cell):
                if any(self.img.getpixel((gx + x, gy + y))[3] > 0 for y in range(self.cell)):
                    w = x + 1
            self.widths[code] = w
        self.widths[32] = 3  # space advance 4 incl. spacing

    def width(self, text):
        return sum(self.widths.get(ord(c), 5) + 1 for c in text) - (1 if text else 0)

    def draw(self, canvas: Image.Image, x, y, text, color=(255, 255, 255), shadow=True):
        if shadow:
            sc = tuple(int(c * 0.25) for c in color)
            self._draw(canvas, x + 1, y + 1, text, sc)
        self._draw(canvas, x, y, text, color)
        return self.width(text)

    def _draw(self, canvas, x, y, text, color):
        cx = x
        for ch in text:
            code = ord(ch) if ord(ch) < 256 else ord("?")
            gx, gy = (code % 16) * self.cell, (code // 16) * self.cell
            glyph = self.img.crop((gx, gy, gx + self.cell, gy + self.cell))
            tint = Image.new("RGBA", glyph.size, color + (255,))
            tint.putalpha(glyph.getchannel("A"))
            canvas.alpha_composite(tint, (cx, y))
            cx += self.widths.get(code, 5) + 1


# --------------------------------------------------------------------------- slicing (as GUI_LAYOUT.md defines it)
class Sheet:
    def __init__(self, name):
        self.img = Image.open(os.path.join(GUI, name + ".png")).convert("RGBA")

    def blit(self, dst, src, x, y):
        sx, sy, w, h = src
        dst.alpha_composite(self.img.crop((sx, sy, sx + w, sy + h)), (x, y))

    def _stretch(self, dst, rect, x, y, w, h):
        if w <= 0 or h <= 0:
            return
        sx, sy, sw, sh = rect
        dst.alpha_composite(self.img.crop((sx, sy, sx + sw, sy + sh)).resize((w, h), Image.NEAREST), (x, y))

    def nine(self, dst, src, b, x, y, w, h):
        sx, sy, sw, sh = src
        cols = [(sx, b, x, b), (sx + b, sw - 2 * b, x + b, w - 2 * b), (sx + sw - b, b, x + w - b, b)]
        rows = [(sy, b, y, b), (sy + b, sh - 2 * b, y + b, h - 2 * b), (sy + sh - b, b, y + h - b, b)]
        for (csx, csw, cx, cw) in cols:
            for (rsy, rsh, ry, rh) in rows:
                self._stretch(dst, (csx, rsy, csw, rsh), cx, ry, cw, rh)

    def three_h(self, dst, src, cap, x, y, w):
        sx, sy, sw, sh = src
        self._stretch(dst, (sx, sy, cap, sh), x, y, cap, sh)
        self._stretch(dst, (sx + cap, sy, sw - 2 * cap, sh), x + cap, y, w - 2 * cap, sh)
        self._stretch(dst, (sx + sw - cap, sy, cap, sh), x + w - cap, y, cap, sh)

    def three_v(self, dst, src, cap, x, y, h):
        sx, sy, sw, sh = src
        self._stretch(dst, (sx, sy, sw, cap), x, y, sw, cap)
        self._stretch(dst, (sx, sy + cap, sw, sh - 2 * cap), x, y + cap, sw, h - 2 * cap)
        self._stretch(dst, (sx, sy + sh - cap, sw, cap), x, y + h - cap, sw, cap)


DARK_TEXT = (0x3f, 0x3f, 0x3f)
LIGHT_TEXT = (0xe6, 0xe6, 0xe6)
DIM_TEXT = (0x99, 0x99, 0x99)
GOLD_TEXT = (0xe0, 0xd2, 0x6a)
CYAN_TEXT = (0x5e, 0xc8, 0xe5)


def mockup_mainframe(font, scale=3):
    mf, wd = Sheet("mainframe"), Sheet("widgets")
    W, H = 320, 232
    g = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def button(x, y, label, kind="normal", w=None):
        src = {"normal": (0, 0, 48, 14), "hover": (0, 14, 48, 14), "pressed": (0, 28, 48, 14),
               "disabled": (0, 42, 48, 14), "danger": (48, 0, 48, 14), "accept": (48, 28, 48, 14)}[kind]
        w = w or font.width(label) + 10
        wd.three_h(g, src, 4, x, y, w)
        col = (0xa0, 0xa0, 0xa0) if kind == "disabled" else (DARK_TEXT if kind in ("normal", "hover", "pressed") else (255, 255, 255))
        font.draw(g, x + (w - font.width(label)) // 2, y + 3, label, col, shadow=kind in ("danger", "accept"))
        return w

    # frame + title bar
    mf.nine(g, (0, 0, 48, 48), 8, 0, 0, W, H)
    mf.three_h(g, (48, 0, 48, 16), 6, 6, 4, W - 12)
    font.draw(g, 12, 8, "Cloud Mainframe", DARK_TEXT, shadow=False)
    t = "@ 12 64 -30"
    font.draw(g, W - 12 - font.width(t), 8, t, (0x6b, 0x67, 0x2a), shadow=False)

    # status row: badge with LED + word, dashboard toggle
    mf.three_h(g, (96, 8, 64, 14), 5, 10, 25, 92)
    mf.blit(g, (112, 0, 8, 8), 14, 28)
    wx = 26 + font.draw(g, 26, 28, "ONLINE", (0x7f, 0xe0, 0x7f)) + 6
    font.draw(g, wx, 28, "3 users", DIM_TEXT)
    font.draw(g, 196, 28, "Dashboard", DARK_TEXT, shadow=False)
    wd.blit(g, (96, 12, 24, 12), 250, 26)
    font.draw(g, 278, 28, "ON", (0x2a, 0x7f, 0x99), shadow=False)

    # tabs
    x = 10
    for i, name in enumerate(("Overview", "Devices", "Trusted", "Security")):
        w = font.width(name) + 14
        if i == 0:
            mf.three_h(g, (48, 30, 32, 14), 5, x, 44, w)
            font.draw(g, x + 7, 48, name, LIGHT_TEXT)
        else:
            mf.three_h(g, (48, 16, 32, 14), 5, x, 44, w)
            font.draw(g, x + 7, 47, name, DARK_TEXT, shadow=False)
        x += w + 2

    # terminal inset with content
    ix, iy, iw, ih = 8, 57, W - 16, H - 57 - 9
    mf.nine(g, (0, 48, 24, 24), 4, ix, iy, iw, ih)
    cx, cy, cw = ix + 6, iy + 6, iw - 20

    def header(y, text):
        font.draw(g, cx, y, text, GOLD_TEXT)
        return y + 11

    def row(y, hover=False):
        wd.three_h(g, (48, 74 if hover else 56, 48, 18), 3, cx - 2, y, cw + 2)

    y = header(cy, "Dashboard")
    for i, url in enumerate(("http://192.168.1.42:8787", "https://cloud.ezfox.ai/m/7f3a")):
        row(y, hover=(i == 0))
        font.draw(g, cx + 4, y + 5, url, LIGHT_TEXT)
        wd.three_h(g, (0, 14 if i == 0 else 0, 48, 14), 4, cx + cw - 18, y + 2, 14)
        wd.blit(g, (112, 24, 9, 9), cx + cw - 15, y + 4)
        y += 18
    y += 3
    y = header(y, "Pairing code")
    row(y)
    code = "FOX-7K2Q"
    big = Image.new("RGBA", (font.width(code) + 2, 10), (0, 0, 0, 0))
    font.draw(big, 0, 0, code, CYAN_TEXT)
    g.alpha_composite(big.resize((big.width * 2, big.height * 2), Image.NEAREST)
                      .crop((0, 0, big.width * 2, 18)), (cx + 4, y + 1))
    font.draw(g, cx + 112, y + 5, "9:41", DIM_TEXT)
    button(cx + cw - 62, y + 2, "New code", "normal", w=58)
    y += 21
    y = header(y, "Pending approval")
    row(y)
    font.draw(g, cx + 4, y + 5, "Enricos iPhone", LIGHT_TEXT)
    font.draw(g, cx + 4 + font.width("Enricos iPhone") + 6, y + 5, "Enrico", DIM_TEXT)
    button(cx + cw - 82, y + 2, "Allow", "accept", w=38)
    button(cx + cw - 41, y + 2, "Deny", "danger", w=38)
    y += 22
    wd.blit(g, (121, 24, 9, 9), cx, y)
    font.draw(g, cx + 13, y + 1, "LAN only: set publicUrl for internet", (0xe8, 0xc5, 0x47))

    # scrollbar
    sx = ix + iw - 9
    wd.three_v(g, (96, 24, 6, 32), 3, sx, iy + 4, ih - 8)
    wd.three_v(g, (102, 24, 6, 16), 3, sx, iy + 6, 70)

    # place on a dimmed "world" background and scale like the game (GUI scale 3)
    M = 14
    bg = Image.new("RGBA", (W + 2 * M, H + 2 * M), (0x16, 0x1b, 0x1f, 255))
    for yy in range(0, bg.height, 8):
        for xx in range(0, bg.width, 8):
            if (xx // 8 + yy // 8) % 2:
                bg.paste((0x1a, 0x20, 0x25, 255), (xx, yy, xx + 8, yy + 8))
    bg.alpha_composite(g, (M, M))
    return bg.resize((bg.width * scale, bg.height * scale), Image.NEAREST)


def mockup_uplink(font, scale=3):
    up, wd = Sheet("uplink"), Sheet("widgets")
    W, H = 220, 150
    g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    up.nine(g, (0, 0, 48, 48), 8, 0, 0, W, H)
    up.three_h(g, (48, 0, 48, 16), 6, 6, 4, W - 12)
    font.draw(g, 12, 8, "Cloud Uplink", DARK_TEXT, shadow=False)
    up.three_h(g, (96, 8, 64, 14), 5, 10, 25, 84)
    up.blit(g, (104, 0, 8, 8), 14, 28)
    font.draw(g, 26, 28, "CONNECTING", (0xe8, 0xc5, 0x47))
    ix, iy, iw, ih = 8, 44, W - 16, H - 44 - 26
    up.nine(g, (0, 48, 24, 24), 4, ix, iy, iw, ih)
    rows = [("Mainframe", "@ 12 64 -30"), ("Distance", "42 m  (range OK)"), ("Title", "Fission Reactor"),
            ("Widgets", "6"), ("Mode", "Lua")]
    y = iy + 6
    for i, (k, v) in enumerate(rows):
        wd.three_h(g, (48, 56, 48, 18), 3, ix + 4, y - 4, iw - 8)
        font.draw(g, ix + 8, y + 1, k, DIM_TEXT)
        font.draw(g, ix + 72, y + 1, v, LIGHT_TEXT)
        y += 15
    wd.three_h(g, (48, 0, 48, 14), 4, W - 10 - 50, H - 22, 50)
    font.draw(g, W - 10 - 50 + (50 - font.width("Unlink")) // 2, H - 19, "Unlink", (255, 255, 255))
    M = 14
    bg = Image.new("RGBA", (W + 2 * M, H + 2 * M), (0x16, 0x1b, 0x1f, 255))
    bg.alpha_composite(g, (M, M))
    return bg.resize((bg.width * scale, bg.height * scale), Image.NEAREST)


def main():
    font = Font()
    os.makedirs(os.path.join(HERE, "renders"), exist_ok=True)
    out = mockup_mainframe(font, 3)
    out.convert("RGB").save(os.path.join(HERE, "renders", "gui_mockup.png"))
    up = mockup_uplink(font, 3)
    up.convert("RGB").save(os.path.join(HERE, "renders", "gui_mockup_uplink.png"))
    print("gui_mockup.png", out.size, "gui_mockup_uplink.png", up.size)


if __name__ == "__main__":
    main()
