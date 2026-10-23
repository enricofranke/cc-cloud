# GUI texture layout (v0.3 in-game screens)

Contract between the Java screens and the art generator. The Java side draws all text and dynamic parts; the textures only provide frames and widget backgrounds. If a texture file is missing, the screens fall back to flat fills in the same colours, so the art can land at any time.

All textures are plain PNGs (RGBA, no `.mcmeta`), **256 × 256** canvas each (unused space transparent). Coordinates below are `x, y, width × height` in texture pixels. The screens never scale pixel art by fractional factors: stretchable regions are drawn with **9-slice / 3-slice** rules, so

- corners are drawn 1:1,
- edges are **stretched** along their length (keep every edge uniform along the stretch direction: no gradients, notches or rivets in the middle of an edge),
- centers are stretched in both directions (use a flat colour or a pattern that survives stretching: flat is safest).

Screens are laid out dynamically: the panel is `min(320, guiWidth − 12)` × `min(232, guiHeight − 12)` GUI pixels. At GUI scale 4–5 on 1080p this is about 300–370 × 200–260; on small windows it can shrink to ~200 × 120 (lists scroll). Never bake text, sizes or a fixed layout into the art.

## Palette (CC: Tweaked look)
| Use | Colour |
| --- | --- |
| Outer bevel light / dark | `#E8E8E8` / `#5A5A5A` (like the CC computer border) |
| Panel body | `#C6C6C6` |
| Terminal inset (lists) | `#111111`, 1 px inner shadow `#000000` |
| Cyan accent (normal) | `#4FC3D7` |
| Gold accent (advanced / owner) | `#E0B03A` |
| Danger | `#D9534F` |
| Text on inset | `#E6E6E6` (Java) |

## `textures/gui/mainframe.png` and `textures/gui/uplink.png`
Same layout in both files; the mainframe uses the **gold** accent, the uplink the **cyan** accent.

| Region | x, y, w × h | Slicing | Notes |
| --- | --- | --- | --- |
| Panel frame | `0, 0, 48 × 48` | 9-slice, border **8** | CC-style beveled outer frame, body `#C6C6C6`. |
| Title bar | `48, 0, 48 × 16` | 3-slice horizontal, caps **6** | Sits inside the panel top (y = 4). Accent-coloured strip, dark text is drawn on it. |
| Terminal inset | `0, 48, 24 × 24` | 9-slice, border **4** | Dark "screen" behind every scrollable list. |
| Tab, inactive | `48, 16, 32 × 14` | 3-slice horizontal, caps **5** | Top-rounded tab, body slightly darker than the panel. |
| Tab, active | `48, 30, 32 × 14` | 3-slice horizontal, caps **5** | Same shape, accent-coloured top edge, merges into the inset below (no bottom border). |
| Status LED | `96, 0, 8 × 8` each, 4 frames in a row | none | offline (red) `96,0`, connecting (yellow) `104,0`, online (green) `112,0`, killed (dark red, crossed) `120,0`. |
| Big status badge | `96, 8, 64 × 14` | 3-slice horizontal, caps **5** | Neutral pill behind the status word; the Java side tints nothing, it just draws text + LED on it. |
| Upgrade slot (v0.7) | `0, 80, 18 × 18` | none | Vanilla-sized inventory slot (dark top/left, white bottom/right, `#8B8B8B` well). The item renders at +1,+1. |
| Upgrade slot, empty hint | `18, 80, 18 × 18` | none | Same slot with a faint card silhouette; draw it while the slot is empty. |
| Quantum Core slot | `36, 80, 18 × 18` | none | Same slot with a gold rim and a faint star silhouette (empty hint); draw the plain gold-rimmed look by covering the ghost with the item. |

## `textures/gui/widgets.png`
Shared widgets for both screens.

| Region | x, y, w × h | Slicing | Notes |
| --- | --- | --- | --- |
| Button normal | `0, 0, 48 × 14` | 3-slice horizontal, caps **4** | CC button: light bevel top/left, dark bevel bottom/right. |
| Button hover | `0, 14, 48 × 14` | same | Cyan outline or brighter body. |
| Button pressed | `0, 28, 48 × 14` | same | Bevel inverted. |
| Button disabled | `0, 42, 48 × 14` | same | Greyed out. |
| Danger button normal / hover | `48, 0, 48 × 14` / `48, 14, 48 × 14` | same | Red body (`#D9534F`), for Deny / Revoke / Unlink / Clear. |
| Accent button normal / hover | `48, 28, 48 × 14` / `48, 42, 48 × 14` | same | Green body for Allow / Add / Set. |
| Toggle off | `96, 0, 24 × 12` | none | Switch knob left, dark track. |
| Toggle on | `96, 12, 24 × 12` | none | Knob right, cyan track. |
| Text field | `0, 56, 48 × 14` | 3-slice horizontal, caps **3** | Dark inset field (the vanilla text cursor and text are drawn on top, 4 px left padding). |
| Text field focused | `0, 70, 48 × 14` | same | Cyan 1 px border. |
| List row | `48, 56, 48 × 18` | 3-slice horizontal, caps **3** | Row background on the terminal inset (very subtle, e.g. `#1A1A1A`). |
| List row hover | `48, 74, 48 × 18` | same | Slightly lighter (`#242424`). |
| Scrollbar track | `96, 24, 6 × 32` | 3-slice vertical, caps **3** | Inside the inset, right edge. |
| Scrollbar thumb | `102, 24, 6 × 16` | 3-slice vertical, caps **3** | Cyan. Height is computed (min 10 px). |
| Copy icon | `112, 24, 9 × 9` | none | Two overlapping pages; drawn inside a 14 px button. |
| Warning icon | `121, 24, 9 × 9` | none | For LAN-only / no TLS hints. |

## What the Java side draws itself
Fonts and every text, the pairing code (large, monospace style via the vanilla font), countdowns, URL text, device rows, the scroll position, and the masked PIN dots. Colours of text are chosen to contrast the regions above (dark text on the title bar and buttons, light text on the inset).

## `textures/gui/hud.png` (in-game overlay, v0.7)
128 × 16, one row of 16 × 16 icons, drawn 1:1 (no slicing). Each icon has a 1 px dark outline so it reads over any background.

| Region | x, y, w × h | Notes |
| --- | --- | --- |
| Signal bars 0 | `0, 0, 16 × 16` | all four bars dark (no signal) |
| Signal bars 1 | `16, 0, 16 × 16` | one bar, yellow (weak link, < 15 %) |
| Signal bars 2 / 3 / 4 | `32, 0` / `48, 0` / `64, 0`, 16 × 16 | two / three / four cyan bars |
| Direction arrow | `80, 0, 16 × 16` | points up (north of the icon); rotate around its centre (8, 8) towards the target |
| No coverage | `96, 0, 16 × 16` | dark bars with a red slash |
