# Style notes: CC: Tweaked look (measured from the 26.1 textures)

Reference: `preview/reference_sheet.png` (all CC block textures, 8x). Values below were read
from the PNGs with a script, not guessed. CC textures are a style reference only; every pixel
in `cccloud` is drawn by `tools/gen_textures.py`.

1. **Palette is tiny.** Grey bodies use 4 neighbouring shades (#7a #7d #7f #83) at random,
   the light band uses #ac/#af/#b1/#b5. Gold uses ~#dbd76f to #ddd97b with a few warmer pixels.
   Per texture there are roughly 6 to 10 meaningful colours; the rest is +/-3 noise.
2. **Noise is very subtle.** Neighbouring pixels differ by 2 to 6 RGB steps. No speckles, no
   gradients, no dithering patterns. Our materials use the same "pick 1 of 4 shades" noise.
3. **1 px dark outline on every block face**: #24 to #4d greys, varied per pixel (not a flat
   line). The left/bottom edge is slightly lighter (#4b) on the lower band than the top edge.
4. **Two-tone body instead of painted lighting.** Sides are split: upper 9 rows base colour,
   lower 7 rows a lighter band (grey #7d -> #b0, gold #dbd975 -> #ecec9c). There are no
   top-left highlights or drop shadows on the flat faces.
5. **Insets are drawn as dark frames:** disk slot, printer trays, modem face = 1 px dark
   (#24 to #2b) rectangle with a grey or black interior. Depth is read from that dark line.
6. **Screens and holes are almost black** (#111111 screen, #161616 to #1e1e1e speaker grille).
7. **Accent colours are small and saturated**: modem "on" = 1 px deep red frame #8a0000,
   disk accepted LED = 2x1 px green, relay = 2x2 red dot with dark border. Accents never
   cover large areas.
8. **Normal vs Advanced**: identical layout, only the body material swaps stone grey for gold.
   The dark outline, black screen and dark details stay the same in both tiers.
9. **Mean brightness**: grey sides ~126 luma, gold sides ~176, fronts with a screen 66 to 83.
   Our textures must land in the same ranges (checked in `preview/side_by_side.png`).
10. **Models are flat**: full cubes plus the modem slab (12x12x3) and the cable (6x6). The only
    geometric depth CC has is the modem and the turtle body; detail lives in the texture.
11. **Texel density is exactly 16 px per block**; no element samples a texture at another scale.
12. **Emissive parts are separate overlay elements** with `light_emission: 15`
    (`computer_on.json`), so "on" pixels glow in the dark while the body stays lit normally.
13. **Small icons are made of 1 px lines** (wireless waves, backpack latch), no anti-aliasing.

## Rules derived for CC: Cloud
- Gold = Advanced tier (mainframe, advanced uplink), stone grey = normal uplink. Same shades as above.
- Depth comes from real elements (insets, louvres, posts); textures stay flat with CC noise.
- The CC two-tone band is used on posts and plate faces instead of top-left shading. "Light from
  top left" is only used on tiny details (screw heads, LED lenses, item sprites).
- Cyan (#5ec8e5 / #a8e6f5 / #2a7f99) only on LEDs, lens, ports, hologram, accent lines. Target
  below ~10 % of the visible surface of any block.
- Dark outlines #24 to #4b, interiors #111 to #1e, never pure black.
