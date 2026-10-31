---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Upgrades, tablet & display"
  position: 7
  icon: cccloud:cloud_tablet
item_ids:
  - cccloud:cloud_tablet
  - cccloud:advanced_cloud_tablet
  - cccloud:cloud_display
  - cccloud:quantum_core
  - cccloud:upgrade_chunk_anchor
  - cccloud:upgrade_bandwidth_allthemodium
  - cccloud:upgrade_bandwidth_vibranium
  - cccloud:upgrade_bandwidth_unobtainium
  - cccloud:upgrade_capacity_allthemodium
  - cccloud:upgrade_capacity_vibranium
  - cccloud:upgrade_capacity_unobtainium
  - cccloud:upgrade_efficiency_allthemodium
  - cccloud:upgrade_efficiency_vibranium
  - cccloud:upgrade_efficiency_unobtainium
  - cccloud:upgrade_memory_allthemodium
  - cccloud:upgrade_memory_vibranium
  - cccloud:upgrade_memory_unobtainium
  - cccloud:upgrade_range_allthemodium
  - cccloud:upgrade_range_vibranium
  - cccloud:upgrade_range_unobtainium
---

# Upgrades, tablet and display

New in v0.7: mainframe upgrades for big bases, the **Cloud Tablet** for the dashboard in-game, and the **Cloud Display** for cards on a wall.

## Mainframe upgrades

The mainframe has **4 upgrade slots** in its screen (hoppers cannot insert). To install one, right-click the mainframe with the upgrade in your hand, or put it in on the Upgrades tab. A higher tier replaces a card of the same type and you get the old card back; the same or a lower tier is refused. You need the same right as for the Upgrades tab (“Manage devices”). Without upgrades it handles:

| Limit | Without upgrades |
|---|---|
| Connected devices (uplinks, bridges, relays) | 16 |
| Browsers at the same time | 4 |
| Rules | 8 |
| History | 1 h |
| Node range of the mainframe | 96 |

Upgrade cards come in three tiers. Only one card per type counts – the highest tier wins.

| Card | Allthemodium | Vibranium | Unobtainium |
|---|---|---|---|
| Capacity (devices) | 48 | 128 | 512 |
| Bandwidth (browsers, update rate) | 8 browsers | 16 | 64 |
| Range (node range ×) | ×1.5 | ×2 | ×3 |
| Efficiency (mainframe FE ×) | ×0.8 | ×0.65 | ×0.5 |
| Memory (rules / history) | 24 / 24 h | 64 / 7 days | 128 / 30 days |

- **Quantum Core** (ATM Star tier, one per mainframe): unlimited devices and browsers, the mainframe works as an Ender node (across dimensions) and uses only a quarter of the FE.
- Recipes use the pack's metals (`#c:ingots/allthemodium` and so on). Without AllTheModium installed there are fallback recipes with diamond, netherite and nether stars. Look them up in JEI/EMI.
- **Over the limit:** new links are refused with a clear message. If you remove a card, the extra devices show **Over capacity** and pause (the ones with the most hops or linked last first) until there is room again.
- The dashboard shows the capacity in use (e.g. “12.5 / 48 devices”) on the Network tab and in the power popover; devices on fiber count half.

## Cloud Tablet

A handheld item that opens your dashboard **in-game** – a native screen with the same cards and widgets, no browser needed.

- Works within network range of any node (mainframe or relay). The **Advanced Cloud Tablet** also works anywhere an Ender Relay reaches.
- Uses **your role** on that mainframe: viewers watch, operators control, admins edit.
- Optional key binding to open it (unbound by default; set it under Controls) while the tablet is in your inventory or a Curios slot.
- Buttons that ask for confirmation say **Click again to confirm** after the first click; click them again within 3 seconds. Buttons with a PIN ask for it at the top of the list.
- The **Alerts** tab shows the latest alerts of the mainframe, also those from while the tablet was closed (kept until the server restarts). You see what your role may see, as on the dashboard.
- With an **Advanced Cloud Tablet** in your inventory or a Curios slot, alerts of its mainframe pop up as a toast, even with the tablet closed. A switch at the top of the Alerts tab turns that off for this tablet. If many alerts come at once, the toast counts the rest.
- Crafted from a CC pocket computer and a Cloud Core (advanced: plus gold and an ender pearl).

## Cloud Display

A display block that shows one dashboard card or page **in the world**, like a CC monitor.

- Place several next to each other to form a larger screen (rectangles up to **8 × 6**).
- Right-click to choose the card or page in its screen. It is read-only by default; operators can allow **clickable buttons** – then right-clicking a button on the display presses it.
- Updates at most 4× per second and is not drawn further than 48 blocks away, so it stays cheap for the server and your FPS.

## Wireless redstone

Uplinks get a **redstone mode** in their screen (under **Advanced**): **transmitter** or **receiver** on a channel name. Receivers output the strongest signal of all transmitters on that channel (0–15). It works over relays, cables and – with Ender Relays – across dimensions, with the next tick as latency. Up to 64 channels per network; very fast clocks (faster than 2 ticks) are smoothed.

In the dashboard, the **Redstone** chip on the Network tab lists every channel with its level, transmitters and receivers. Players with the “Switch redstone” permission (operators and up) can **override** a channel – a level from 0 to 15 for 30 s, 5 min, 1 h or until reset – for example to open a door from your phone. The override wins over the transmitters until it ends or you clear it; every override is logged.

