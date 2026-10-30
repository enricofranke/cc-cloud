---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Getting started"
  position: 0
  icon: cccloud:cloud_mainframe
---

# Getting started

CC: Cloud puts your ComputerCraft computers and storage on a dashboard in any browser – PC, tablet or phone. The Minecraft server hosts the dashboard itself – no external service, no sign-up.

> **Tip:** The quickest path: craft a Cloud Mainframe, power it, put a Cloud Uplink on a computer, link the two with a Cloud Link Card and pair your browser. The computer appears on the dashboard automatically – no code needed.
>
> Only want to switch a lamp? Place a **Cloud Lamp** (or a Cloud Switch next to your redstone lamps) and link it – it shows up as a switch on the dashboard. See [Redstone devices](redstone.md).

## The blocks

| Block / item | What it does |
|---|---|
| **Cloud Mainframe** | The heart of a base. Two blocks tall. Hosts the connection to the dashboard, shows the pairing code and manages devices. One per base or team. Needs FE power. |
| **Cloud Uplink** | Sticks to a block face like a modem; on low or small faces (slab sides, solar panels) it becomes a compact module; on top or bottom it needs a full face. On a computer it becomes that computer's card on the dashboard. Range 128 blocks, same dimension. |
| **Advanced Cloud Uplink** | Same, but unlimited range and works across dimensions. |
| **Cloud Probe** | Reads **one** machine up to 8 blocks away as if an uplink were attached to it – for machines built into walls or with every face taken by pipes and cables. Expensive; counts as two devices. See *Machines without a free face* below. |
| **Cloud Link Card** | Links uplinks, bridges and redstone devices to a mainframe. |
| **Cloud Switch / Button / Lamp / Sensor** | Small [redstone devices](redstone.md) without a computer: link them and switch lamps, doors and machines from the dashboard. Devices with the same group share one card. |
| **ME / RS Cloud Bridge** | Joins an Applied Energistics 2 or Refined Storage network. With an uplink on it you get a ready-made storage card (search, craft, watchlist). Only exists when AE2 / RS is installed. |
| **Cloud Core** | Crafting ingredient. |

## Crafting recipes

Ingredients shown as "(any)" accept every variant from the tag (e.g. any gold ingot). JEI/EMI show the same recipes in-game.

### Cloud Core
<RecipesFor id="cccloud:cloud_core" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Mainframe
<RecipesFor id="cccloud:cloud_mainframe" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Uplink
<RecipesFor id="cccloud:cloud_uplink" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Advanced Cloud Uplink
<RecipesFor id="cccloud:advanced_cloud_uplink" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Probe
<RecipesFor id="cccloud:cloud_probe" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />
Without Allthemodium the vibranium ingots are 4 diamonds and 2 netherite scrap (all recipes: JEI/EMI or the recipes page).

### Cloud Link Card
<RecipesFor id="cccloud:cloud_link_card" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### ME and RS Cloud Bridge
<RecipesFor id="cccloud:me_cloud_bridge" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />
<RecipesFor id="cccloud:rs_cloud_bridge" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

## Place the mainframe

1. Place the **Cloud Mainframe** (it takes two blocks of height). Whoever places it is its **owner**.
2. Right-click it: the screen shows the status, the dashboard address (URL) and a pairing code.
3. Turn the dashboard **on** in that screen (or sneak + right-click with an empty hand).

A redstone signal on either half is a **kill switch**: the mainframe goes offline until the signal is gone.

### Breaking and moving the mainframe

The mainframe keeps its identity in its item. Break it (pickaxe) and place the item again – anywhere, also in another base – and it is the **same mainframe**: devices, accounts, PIN, layout, rules, scenes and API keys are back, and linked uplinks and bridges reconnect by themselves. It still belongs to its owner, whoever places it. Upgrade cards drop as separate items; put them back in.

- **Nothing is deleted right away.** After a break the server keeps everything for **7 days** (`mainframeRetentionDays`); the dashboard shows the mainframe as **Removed** meanwhile. Only after that, or with `/cccloud mainframe purge <id>`, is its data deleted.
- **Lost the item?** In creative a break drops nothing: `/cccloud mainframe recover` lists your removed mainframes and hands you their item (once per break). If the item did drop, place that one – or ask a server admin, who can always recover.
- **Misclick guard** (`mainframeBreakProtection`): in creative the mainframe only breaks while you **sneak**; in survival only its owner, its admins and server admins can break it. Claim mods protect it as well.

## Power (FE)

The mainframe runs on **FE** (Forge Energy, also called RF) – the unit used by Powah, Ender IO, Mekanism's FE output, Thermal, AE2 and most tech mods. Connect any FE cable or generator to either half.

| | Default |
|---|---|
| Base consumption | 40 FE/t |
| Per uplink / advanced uplink / bridge | +4 / +8 / +10 FE/t |
| Per connected dashboard | +2 FE/t |
| Per extra hop of a routed device | +1 FE/t |
| Buffer | 200,000 FE |
| Max input | 2,000 FE/t |

Without power the mainframe goes offline (after 5 seconds on an empty buffer) and the dashboard says **"Mainframe has no power"**. It reconnects by itself once the buffer refills. Uplinks and bridges need no FE of their own; [relays](network.md) do. Server admins can turn the requirement off (`requirePower = false`).

## Link uplinks and bridges

1. Place a **Cloud Uplink** on a side of a computer (or on an ME / RS Cloud Bridge).
2. Right-click the **mainframe** with a **Cloud Link Card** – the card now says "Linked to Mainframe @ x y z".
3. Right-click the **uplink** (or bridge) with the card. Done – its status light turns green.

Sneak + right-click into the air clears the card. Only the owner and trusted players can link.

### Machines without a free face: Cloud Probe

An uplink reads the block it sits on. If a machine is built into a wall, or every face is taken by pipes and cables, use a **Cloud Probe** somewhere within 8 blocks (same dimension):

1. Place the probe on any full face nearby and link it to the mainframe like an uplink.
2. **Sneak + right-click the machine** with the Link Card – the card remembers it (and the face you clicked).
3. **Right-click the probe** with the card. It now shows the machine's card, exactly as an uplink on that face would.

Picking the machine is a normal interaction, so claims and spawn protection apply: where you may not use the machine, you cannot pick it. Only you can hand your pick to a probe, within 10 minutes. The probe keeps checking: if another team claims the machine's chunk, or the server lowers the range, it stops reading until that changes; a probe that is moved (with a block mover or a contraption) forgets its machine. Right-click the probe for its screen: it shows what it reads and has **Clear target**; the Cloud Analyzer draws a thin line to the machine. If the machine is removed, the probe forgets it.

If an uplink shows no card and the screen says *"This side exposes nothing"*, the machine offers its energy, items or fluids on another face – move the uplink there (the screen names one) or use a probe.

## Pair a browser

1. Right-click the mainframe and note the **pairing code** (8 characters, valid 10 minutes, single use).
2. Open the dashboard URL from the mainframe screen in your browser (e.g. `http://192.168.1.20:8787`). On the same Wi-Fi this just works.
3. Enter the code and a name for this browser, click **Pair device**.
4. In Minecraft, click **[Allow]** in the chat (or in the mainframe screen). The browser continues by itself.

> **Tip:** Link your player once with `/cccloud link` and the browser sees every mainframe you have a role on – see [accounts and roles](accounts.md). Install the dashboard like an app: "Install app" in desktop Chrome/Edge, "Add to Home Screen" on tablets and phones.

What you see next: [the dashboard](dashboard.md). Want your own widgets? Start with [CC basics](cc-basics.md) and the [Lua reference](lua.md).

