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

Every uplink becomes a **card**. A computer without its own program shows the automatic **Computer card**: power, Turn on / Reboot / Shut down and its six redstone sides (tap a side to switch its output, the LED shows the input; analog levels under "Advanced").

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

Programs can put widgets on **pages** (tabs: on top in wide windows, at the bottom on narrow screens) and in **groups**. Players with control rights see a **pencil**:

- reorder cards and widgets (drag with the mouse, or ▲▼ on narrow screens and touch)
- resize cards (drag the right edge, 1–4 columns) and widgets
- rename, recolour, give an icon, hide
- create, rename and delete pages, move cards between them

**Save** stores the layout on the server – all devices see it. A script restart keeps the layout because widget ids stay the same.

## Alerts

Programs send alerts with `cloud.alert(text, level)`. They pop up as toasts and are kept in the **bell** log. Info toasts fade; warn and crit stay until dismissed. Tap **Enable notifications** in the alert log to get system notifications for crit alerts while the tab is in the background.

## PIN-protected controls

Widgets with `pin=true` ask for the mainframe PIN on a keypad before they act. The owner sets it with `/cccloud pin set <4-8 digits>`. After 5 wrong PINs the device is locked for 5 minutes and the owner is notified in-game.

## States you may see

| Message | Meaning |
|---|---|
| Dashboard disabled | the dashboard is switched off in the mainframe screen |
| Offline | the mainframe chunk is unloaded, the block was broken or the redstone kill switch is on |
| Mainframe has no power | the FE buffer is empty – see [power](index.md) |
| Uplink offline | that uplink is unloaded or out of range |
| Read-only device | this device may watch but not control |
| Server not reachable | see [troubleshooting](faq.md) |

