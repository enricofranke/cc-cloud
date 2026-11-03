---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "The dashboard"
  position: 2
  icon: cccloud:cloud_uplink
---

# The dashboard

Open the URL shown by the mainframe. One browser can hold several mainframes; tap one to open its dashboard.

## Cards and widgets

Every uplink becomes a **card**; [redstone devices](redstone.md) share one card per group. A computer (CC: Tweaked) without its own program shows the automatic **Computer card**: power, Turn on / Reboot / Shut down and its six redstone sides (tap a side to switch its output, the LED shows the input; analog levels under "Advanced").

**Machines on the computer's cable network** (CC: Tweaked). If the computer has a wired modem with CC networking cable to other machines (their modems switched on), the uplink on the computer also shows a card for each of those machines – the same card an uplink sitting on the machine would show (energy, tanks, storage controllers, mod machines), with the same controls and permissions. Up to 8 per uplink (server setting `wiredMachineCards`); a machine with its own uplink or probe keeps that one, and machines with the same name are told apart by the computer's name for them ("Energy Cell (energy_cell_2)"). Plain chests have no card.

**Peripheral card – for everything else** (CC: Tweaked). An uplink or probe on a block that has no card of its own, but that CC: Tweaked offers as a peripheral (the methods a computer could call on it, including CC's energy, item and fluid methods), shows the **Peripheral card**: the block's name and its peripheral types. Pick what it shows in the [layout editor](#edit-mode): click the card, then under **Values** tick up to 6 methods that take no arguments (`get…`, `is…`, `list…`, and `size`, `list`, `tanks` of storage blocks), up to 2 numbers also as a chart, and **Save values**. A yes/no method becomes a lamp, everything else a short text. If the block has a `set…(true/false)` method, you can make it the card's **Switch** (operators switch it, with a confirm step and – unless you turn it off – the PIN). Only the methods you picked are ever called, every few seconds and less often if they are slow; methods that need arguments, and those of peripherals that do not say which arguments they take, are not offered – a Lua program can still use them. Choosing values needs the right to edit the layout. A peripheral on a computer's cable network gets no Peripheral card (only machines with a real card do).

| Widget | Shows / does |
|---|---|
| gauge | value on an arc with warn/crit colours |
| chart | line or sparkline of recent values |
| progress | bar with value / max |
| indicator | LED on/off |
| label | text or key/value |
| button | sends an action (optional confirm and PIN) |
| toggle | on/off switch (updates instantly, rolls back if the computer does not confirm) |
| slider | number, sent when you let go |
| select, buttons | pick one option |
| number, text | input fields (Enter or Set/Send) |
| item search, watchlist, jobs | storage widgets of the [bridges](bridges.md) |

## Pages and the layout editor

Programs can put widgets on **pages** (tabs: on top in wide windows, at the bottom on narrow screens) and in **groups**. Everyone with the **Edit the layout** permission sees a **pencil**: it turns the dashboard itself into the editor – you arrange the real, live cards.

- **Move:** drag a card by its header, or a widget anywhere. The others make room and glide aside; a dashed outline shows where it lands. Drop a widget on another card, on empty space (it gets its **own new card**) or on a page tab (hover a moment to open that page).
- **Resize:** drag the right edge, the bottom edge or the corner – the badge shows the size (cards use 12 columns of the page, widgets 12 columns of their card).
- **Select several widgets** with Shift-click and drag one of them to move them together.
- **Settings** (✎ or click): label, accent colour (a button takes it as its colour – also a Cloud Button's, which is cyan by default), icon, size and the display style with a live preview – a gauge as dial, bar or big number, a label as row or big number, a chart as chart or sparkline, an indicator as chip or lamp. Cards get a title, icon, accent, a compact or comfortable style and a [design](#card-design) (background, custom CSS, card parts); a [Peripheral card](#widgets) also its values and switch.
- **Toolbox:** opens on the side (a sheet on phones; press **A**). **Cards** has an empty card and ready-made templates, **Widgets** has every widget type: drag one onto a card or free space, then pick its data. **Hidden** lists what you hid.
- **Hide** with ×, or drag it onto the toolbox. To bring it back, click it under **Hidden** or drag it out.
- **Touch:** press and hold to pick up (a short vibration), then drag – a normal swipe still scrolls. **Keyboard:** Tab to a card or widget, Space picks it up, arrow keys move, Shift + arrow keys resize, Page Up/Down move a widget to the previous/next card, Space drops, Esc cancels. Press **?** for all shortcuts.
- **Undo/redo** (Ctrl/⌘ + Z, Ctrl/⌘ + Shift + Z), **Reset** to the scripts' default, and **Save** (Ctrl/⌘ + S). Leaving with unsaved changes asks first.

**Presets** in **+ Add** puts a ready-made page on your dashboard – see [Dashboard presets](presets.md).

**Save** stores the layout on the server – all browsers see it. A script restart keeps the layout because widget ids stay the same. On narrow screens the cards stack in reading order (two columns on phones).

## Card design

In a card's settings you choose a **background** and, if you like, a few lines of **custom CSS** for the card and for its [parts](#card-parts). All of it is saved with the layout, travels with share codes and presets, and works in the light and the dark theme. A program can bring a [design of its own](#card-script-design).

- **Background:** Default, a **tint** of one of the eight accent colours, four **gradients** (Accent, Aurora, Ember, Lagoon), four **patterns** (Grid, Stripes, Dots, Circuit) or **Custom** with a colour of your own. Tints, gradients and patterns follow the theme and the card's accent. A custom colour chooses light or dark text by itself and lays a faint veil over the colour where that is needed to keep text readable.
- **Icons:** a widget's icon (in its settings) shows in front of its label – on rows, big numbers, gauges, charts, switches, sliders and inputs – just like a card's icon in the header. Widgets without a label, such as item search, have no icon setting.

### Custom CSS

**Custom CSS** (collapsed at first) takes declarations, one per line, `property: value;`:

```css
background: linear-gradient(135deg, #1e3a5f, var(--panel));
border: 2px solid var(--accent);
border-radius: 12px;
letter-spacing: 0.04em;
```

Only declarations – no selectors, no `{ }` blocks, no `@` rules. Below the field the editor lists every declaration it dropped and why. What you can use:

| What | Properties |
|---|---|
| Background | background, background-color, -image, -size, -position, -repeat, -blend-mode |
| Frame | border (also -top, -right, -bottom, -left, -width up to 8 px, -color, -style), border-radius up to 32 px, box-shadow (inset only) |
| Surface | opacity, filter (blur, brightness, contrast, grayscale, hue-rotate, saturate, sepia; gentle values) |
| Text | color, font-family (silkscreen, monospace, sans-serif, serif, system-ui), font-weight, font-style, letter-spacing, text-transform, a small text-shadow |

Values can be colours (`#hex`, `rgb()`, `hsl()`, `oklch()`, `color-mix()` …), gradients and the dashboard's theme tokens with `var()`, e.g. `var(--accent)`, `var(--panel)`, `var(--text)`, `var(--muted)`, `var(--line)`, so the card follows the theme. Units: px, em, rem, %, deg.

**Why so strict?** A layout can come from anyone with edit rights, from a share code or from the store. The dashboard treats every line as untrusted and checks it again each time it draws a card:

- **Nothing loads or runs:** no `url()`, `image-set()`, `attr()`, `expression` or `javascript:`, no comments, backslash escapes, quotes or `!important`, plain ASCII only. A card can't fetch anything or learn who is looking at it.
- **Nothing leaves the card:** no `position`, `top`/`left`/`inset`, `z-index`, `transform`, `margin`, `width`/`height`, `content`, `pointer-events`, `cursor` or outer shadows (a part's small glow stays inside the card), and numbers have limits. The background is painted on its own layer, clipped to the card and never in the way of a click, so a card can't cover other cards, the PIN prompt or the login.
- **Text stays readable:** a text colour too close to the background is dropped (and listed).
- The server checks the same rules before it stores a layout.

### Card parts

Under **Parts**, below Custom CSS, you style the pieces inside a card one at a time: pick a part, then write declarations for it as above. A dot marks the parts that have CSS. Each part takes only the properties in its row:

| Part | What it is | Properties |
|---|---|---|
| title_bar | the strip with the card's title | background, background-color, -image, border-bottom, box-shadow (inset) |
| title | the title text | text, font-size (10–22 px) |
| screen | the dark tiles of gauges, numbers and lamps | background, background-color, -image, border, border-color, border-radius, box-shadow |
| row | the boxes of label rows and indicators | like screen |
| label | every widget's caption | text |
| value | the readings: numbers, texts, states | text |
| gauge_track | the unlit part of gauges and bars | color (the dial), background, background-color, -image, border, border-color, border-radius, box-shadow (inset) |
| gauge_fill | the lit part of gauges and bars | color (the dial), background, background-color, -image, border-radius, box-shadow, filter |
| chart_line | the line of charts | color (also tints the area below), filter |
| button | every button | background-color, background-image, color, border, border-color, border-radius, box-shadow, font-family, font-weight, letter-spacing, text-transform, text-shadow |
| toggle | the switch of toggles (its background while off) | background, background-color, -image, border, border-color, border-radius, box-shadow (inset) |
| toggle_on | a switch while it is on | background, background-color, -image, border-color, box-shadow |

*Text* is color, font-family, font-weight, font-style, letter-spacing, text-transform and text-shadow. A glowing gauge, as **gauge_fill**:

```css
color: #5ef0ff;
box-shadow: 0 0 6px rgba(94, 240, 255, 0.7);
filter: drop-shadow(0 0 3px rgba(94, 240, 255, 0.9));
```

- **Glow:** screen, row, gauge_fill, button and toggle_on may have an outer box-shadow, gauge_fill and chart_line a `drop-shadow()` in filter. Only px, an offset of up to 2 px, blur up to 6 px, spread up to 2 px and 8 px in all, so it stays inside the card.
- **Warnings stay:** the colours of gauge_fill, chart_line and value apply in the normal range. In the warning and critical range the dashboard's yellow and red show.
- Up to 512 characters and 12 declarations per part, with the same checks as Custom CSS. A text colour too close to the background is dropped here too. Dialogs a widget opens (item search, the PIN pad, confirmations) are never styled.

### A program's design

A program can give its card a design: `dash:style{...}` in [cloud.ui](ui-library.md) (a group takes `style` in its options), `setStyle` on the [uplink](lua.md), or `cloud.setStyle(card, design)` in a [Cloud Script](scripts.md). A design is a table with `bg`, `bgColor`, `css` and `parts`, checked like the editor's:

```lua
-- the Reactor Console look: dark glass, a glowing gauge, monospace read-outs
dash:style({
  bg = "custom", bgColor = "#07121c",
  css = "border: 1px solid rgba(94, 240, 255, 0.3); border-radius: 14px;",
  parts = {
    title = "color: #7ff3ff; font-family: monospace; letter-spacing: 3px; text-transform: uppercase;",
    value = "color: #e9fdff; font-family: monospace; font-weight: 700;",
    gauge_fill = "color: #5ef0ff; filter: drop-shadow(0 0 3px rgba(94, 240, 255, 0.9));",
    button = "border: 1px solid rgba(255, 130, 120, 0.65); box-shadow: 0 0 6px rgba(255, 80, 70, 0.6); letter-spacing: 4px;",
  },
})
```

- The card shows the program's design until someone changes the design in the editor. The first change copies the program's design into the layout, so you only edit what you want different. From then on the layout's design is used as a whole, and **Use the script's design** in the card's settings goes back to the program's.
- `cloud.setStyle` from a Cloud Script saves the design in the layout, like the editor (it needs the right to edit the layout); `cloud.setStyle(card)` removes it again.

## Alerts

Programs send alerts with `cloud.alert(text, level)`. They pop up as toasts and are kept in the **bell** log. Info toasts fade; warn and crit stay until dismissed. Tap **Enable notifications** in the alert log to get system notifications for crit alerts while the tab is in the background. The log comes from the server, so a browser that reconnects still has the latest 50 (until the server restarts). On a [Cloud Tablet](upgrades.md) they are on the Alerts tab.

## PIN-protected controls

Widgets with `pin=true` ask for the mainframe PIN on a keypad before they act. The owner sets it with `/cccloud pin set <4-8 digits>`. After 5 wrong PINs the device is locked for 5 minutes and the owner is notified in-game.

## States you may see

| Message | Meaning |
|---|---|
| Dashboard disabled | the dashboard is switched off in the mainframe screen |
| Offline | the mainframe chunk is unloaded or the redstone kill switch is on |
| Removed | the mainframe block was broken – place its item again to bring everything back ([how](index.md)) |
| Mainframe has no power | the FE buffer is empty – see [power](index.md) |
| Uplink offline | that uplink is unloaded or out of range |
| Blocked – this machine is in …'s claim | another team claimed the machine's chunk – see [claims](faq.md) |
| Read-only device | this device may watch but not control |
| Server not reachable | see [troubleshooting](faq.md) |

