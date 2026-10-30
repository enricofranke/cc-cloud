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

Every uplink becomes a **card**; [redstone devices](redstone.md) share one card per group. A computer without its own program shows the automatic **Computer card**: power, Turn on / Reboot / Shut down and its six redstone sides (tap a side to switch its output, the LED shows the input; analog levels under "Advanced").

**Machines on the computer's cable network.** If the computer has a wired modem with CC networking cable to other machines (their modems switched on), the uplink on the computer also shows a card for each of those machines – the same card an uplink sitting on the machine would show (energy, tanks, storage controllers, mod machines), with the same controls and permissions. Up to 8 per uplink (server setting `wiredMachineCards`); a machine with its own uplink or probe keeps that one, and machines with the same name are told apart by the computer's name for them ("Energy Cell (energy_cell_2)"). Plain chests have no card.

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

Programs can put widgets on **pages** (tabs: on top in wide windows, at the bottom on narrow screens) and in **groups**. Admins see a **pencil**: it turns the dashboard itself into the editor – you arrange the real, live cards.

- **Move:** drag a card by its header, or a widget anywhere. The others make room and glide aside; a dashed outline shows where it lands. Drop a widget on another card, on empty space (it gets its **own new card**) or on a page tab (hover a moment to open that page).
- **Resize:** drag the right edge, the bottom edge or the corner – the badge shows the size (cards use 12 columns of the page, widgets 12 columns of their card).
- **Select several widgets** with Shift-click and drag one of them to move them together.
- **Settings** (⚙ or click): label, accent colour, icon, size and the display style with a live preview – a gauge as dial, bar or big number, a label as row or big number, a chart as chart or sparkline, an indicator as chip or lamp. Cards get a title, icon, accent and a compact or comfortable style.
- **Hide** with ×. **+ Add** lists everything hidden (drag it back or press Show) and shows every widget type live with the Lua line that creates it.
- **Touch:** press and hold to pick up (a short vibration), then drag – a normal swipe still scrolls. **Keyboard:** Tab to a card or widget, Space picks it up, arrow keys move, Shift + arrow keys resize, Page Up/Down move a widget to the previous/next card, Space drops, Esc cancels. Press **?** for all shortcuts.
- **Undo/redo** (Ctrl/⌘ + Z, Ctrl/⌘ + Shift + Z), **Reset** to the scripts' default, and **Save** (Ctrl/⌘ + S). Leaving with unsaved changes asks first.

**Save** stores the layout on the server – all browsers see it. A script restart keeps the layout because widget ids stay the same. On narrow screens the cards stack in reading order (two columns on phones).

## Alerts

Programs send alerts with `cloud.alert(text, level)`. They pop up as toasts and are kept in the **bell** log. Info toasts fade; warn and crit stay until dismissed. Tap **Enable notifications** in the alert log to get system notifications for crit alerts while the tab is in the background.

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
| Read-only device | this device may watch but not control |
| Server not reachable | see [troubleshooting](faq.md) |

