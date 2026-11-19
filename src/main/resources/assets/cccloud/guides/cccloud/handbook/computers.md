---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Cloud Computers"
  position: 5
  icon: cccloud:advanced_cloud_computer
item_ids:
  - cccloud:cloud_computer
  - cccloud:advanced_cloud_computer
---

# Cloud Computers

A computer you place in the world – no other mod needed. It runs programs on CC: Cloud's own script engine, shows them on its screen, reads and sets redstone, and appears on the dashboard like any computer.

| Block | Screen | Mouse |
|---|---|---|
| **Cloud Computer** | 51×19 characters, shades of grey | – |
| **Advanced Cloud Computer** | 51×19 characters, 16 colours | click, drag, scroll |

## Use it

1. Craft and place it (recipes [below](computers.md)). It faces you; its screen side is the **front**.
2. **Right-click** opens its screen. While it is off, click the screen to turn it on.
3. Type as on a keyboard; **Ctrl+V** pastes one line (up to 512 characters). **Esc** closes the screen, the computer keeps running.
4. **Hold** for a second: **Ctrl+T** stops the running program, **Ctrl+R** restarts the computer, **Ctrl+S** shuts it down.

Breaking the computer keeps it: the item remembers its number and label, and placing it again brings back its files. Only the player who owns it gets it back – another player placing the item gets a new, empty computer.

## Redstone

Programs name the sides as the computer sees them: **front** (the screen), **back**, **left**, **right**, **top** and **bottom**. Outputs power like a redstone block (strong, 0–15); inputs read the strongest signal on that side. Shutting down turns every output off.

## On the dashboard

Put a [Cloud Uplink](index.md) on the computer and link it: it gets the automatic **Computer** card (power, label, redstone per side), the browser terminal and the script editor – exactly like a CC: Tweaked computer.

## Rights

- The player who places a computer owns it. The owner, the members of the owner's team (FTB Teams) and server admins may open its screen, type and switch it on or off; everyone else gets a message.
- A computer placed by a machine or a command has no owner and is open to everyone.
- A computer in another team's claim shuts down and cannot be used until the claim allows it – see [claims](faq.md).

## Recipes

<RecipesFor id="cccloud:cloud_computer" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

<RecipesFor id="cccloud:advanced_cloud_computer" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

