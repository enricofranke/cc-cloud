---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Upgrades, tablet & display"
  position: 6
  icon: cccloud:cloud_tablet
---

# Upgrades, tablet and display

New in v0.7: mainframe upgrades for big bases, the **Cloud Tablet** for the dashboard in-game, and the **Cloud Display** for cards on a wall.

## Mainframe upgrades

The mainframe has **4 upgrade slots** in its screen (hoppers cannot insert). Without upgrades it handles:

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

## Cloud Tablet

A handheld item that opens your dashboard **in-game** – a native screen with the same cards and widgets, no browser needed.

- Works within network range of any node (mainframe or relay). The **Advanced Cloud Tablet** also works anywhere an Ender Relay reaches.
- Uses **your role** on that mainframe: viewers watch, operators control, admins edit.
- Optional key binding to open it (unbound by default; set it under Controls) while the tablet is in your inventory or a Curios slot.
- Crafted from a CC pocket computer and a Cloud Core (advanced: plus gold and an ender pearl).

## Cloud Display

A display block that shows one dashboard card or page **in the world**, like a CC monitor.

- Place several next to each other to form a larger screen (rectangles up to **8 × 6**).
- Right-click to choose the card or page in its screen. It is read-only by default; operators can allow **clickable buttons** – then right-clicking a button on the display presses it.
- Updates at most 4× per second and is not drawn further than 48 blocks away, so it stays cheap for the server and your FPS.

## Wireless redstone

Uplinks get a **redstone mode** in their screen: **transmitter** or **receiver** on a channel name. Receivers output the strongest signal of all transmitters on that channel (0–15). It works over relays, cables and – with Ender Relays – across dimensions, with the next tick as latency. Up to 64 channels per network; very fast clocks (faster than 2 ticks) are smoothed.

