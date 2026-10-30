---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Network, relays & range"
  position: 6
  icon: cccloud:cloud_relay
---

# Network, relays and range

Every uplink and bridge connects to the **nearest reachable node** – the mainframe or a relay – and the network routes it to the mainframe over the best path (fewest hops, then the best signal; at most 8 hops). Like a Wi-Fi mesh: place relays to reach far-away bases.

Right-click an uplink, bridge or relay to see what it is connected to, the card it puts on the dashboard (as a live preview), its signal and – when something is wrong – a one-line fix. **Show in dashboard** sends a chat link that opens exactly that card; **Rename** changes the card's title for everyone.

## Ranges

| Block | Range to nodes | Range for devices |
|---|---|---|
| Cloud Mainframe | 96 | 96 |
| Cloud Relay | 96 | 48 |
| Advanced Cloud Relay | 256 | 128 |
| Ender Relay | any distance and dimension to other Ender Relays | 48 |
| Cloud Uplink | – | 48 |
| Advanced Cloud Uplink | – | 128 |
| ME / RS Cloud Bridge | – | 48 |
| Cloud Switch / Button / Lamp / Sensor | – | 32 |

**Node range** = how far a relay or the mainframe reaches **other relays and the mainframe**. **Device range** = how far it reaches **uplinks and bridges**. On the map the node range is the **solid circle**, the device range the **dashed circle**; click a relay to see both labelled, and the **?** on the map explains how to plan relays.

Distances are measured in blocks in a straight line (also up and down), within one dimension. A link exists when one end reaches the other – the larger of the two ranges counts. Cables (below) have no range limit.

## Signal

Signal = 1 − distance / range, shown as 0–100 % and bars. Below 15 % a link is **weak**: it still works but shows yellow – move the device closer or add a relay.

## Relays

- **Cloud Relay** – a mast for medium distances.
- **Advanced Cloud Relay** – longer range for nodes and devices.
- **Ender Relay** – links to other Ender Relays at any distance and **across dimensions** (e.g. to the Nether). Link Ender Relays into a group with the Link Card.

Place a relay, then link it like any device: Link Card on the mainframe, then on the relay.

### Cloud Relay
<RecipesFor id="cccloud:cloud_relay" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Advanced Cloud Relay
<RecipesFor id="cccloud:advanced_cloud_relay" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Ender Relay
<RecipesFor id="cccloud:ender_relay" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

> **Tip:** If linking fails, the message names the nearest node and how far away it is. Place a relay in between.

## Cables (wired links)

Devices can also join **by cable**, like AE2 cables: connect the uplink or bridge to the mainframe or a relay with CC: Tweaked **networking cable** and **wired modems** (right-click the modems so they turn on). Everything on the same CC wired network is a wired member.

- Cables have **no range limit**, always **100 %** signal and cost **no extra FE per hop**.
- A device can have both cable and radio; the cable is preferred, radio is the backup.
- In the dashboard cables are **solid lines** (grey = CC cable, glowing cyan = fiber), radio links **dashed** (coloured by signal), ender links **dotted**.

**Fiber Cable** (ships with CC: Cloud) works the same, but only connects our devices – a clean network of its own. Devices on fiber count **half** towards the mainframe's device capacity and pay **half** their FE. Use the **Fiber Junction** for splits and walls, and an **Ender Fiber Link** pair to join two fiber networks across any distance or dimension.

## When a relay fails

If a relay is broken, unloaded or out of power, everything behind it **re-routes** over another path. If there is none, those devices show **No route**. Relays never force-load chunks; parts in unloaded chunks show **Unloaded**.

Devices linked before v0.4 that are out of range under the new rules keep working and show **Out of range (legacy)** until you place a relay nearby or re-link them.

## Power

The network costs FE. The mainframe uses 40 FE/t + 4 per uplink + 8 per advanced uplink + 10 per bridge + 2 per connected dashboard + 1 per extra hop of each routed device. Relays need their own FE: Cloud Relay 20 FE/t, Advanced 60 FE/t, Ender Relay 120 FE/t. Click the power bar in the dashboard header to see the breakdown.

The breakdown also shows devices on fiber (they pay half), the **Chunk Anchor** if one is installed, and an **Efficiency** upgrade as a green line with the FE it saves. Below it, **Device capacity** shows how many devices the mainframe carries out of what its upgrade cards allow; the same number is a chip on the Network tab.

## The network map

The **Network** tab shows everything that is connected, in three views:

- **Topology** – the mainframe at the root, relays as branches, devices as leaves. Cables are solid, radio links dashed and coloured by signal (green strong, cyan ok, yellow weak), ender links purple and dotted, legacy links orange; the dot shows the status. Devices without a route are grouped under **Not connected**.
- **Map** – a top-down x/z map per dimension on a real map of the world: **Surface** (like a Minecraft map) or **Slice** – a cut at a height you choose with the Y slider, so an underground base at y −60 shows its rooms and caves (darker = deeper). Only places near your network are shown, and only chunks that were loaded. With a coordinate grid, range circles (toggle; solid = node range, dashed = device range), links and status dots. Drag to pan, scroll or pinch to zoom, ⤢ fits everything.
- **3D** – see below.

A **Cloud Probe** has its own icon and draws a thin purple line to the machine it reads (also in 3D, where the machine gets a purple frame); its details say which machine and how far away. Like all coordinates, the line is only sent to players allowed to see positions.

With FTB Chunks and Waystones installed, the map also shows your team's **claimed chunks** (team colour, force-loaded ones dashed) and your **waystones** (violet diamonds, global ones gold) – toggle them with **Claims** and **Waystones**. Waystones are also marked in the 3D view. Like all coordinates, they are only sent to players allowed to see positions.

Click or tap any node for details, with sub-menus: **Info** (type, position, dimension, status, signal or cable, range), **Route** (every hop to the mainframe and what a relay carries), **Power** (usage and buffer) and **Card** (the device's live card, controls as your role allows). **Open card** jumps to it on the dashboard; **Unlink** removes it from the network (admins). Positions are only shown to browsers that may control the mainframe.

## 3D view

The **3D** view shows your base with the real block models at their real coordinates: links as lines (cable solid, radio dashed, ender dotted), ranges as translucent spheres (toggle **Ranges**) and – if the server sends it – the coarse terrain around your blocks (toggle **Terrain**; only loaded chunks, black holes are unloaded areas).

- **Mouse:** drag to rotate, right-drag or Shift-drag to pan, wheel to zoom. **Touch:** one finger rotates, two fingers pinch and pan. **Keyboard:** focus the view, arrow keys pan, + and − zoom, Esc clears the selection. ⤢ fits everything again.
- Click a block for its details. Clicking a **relay** (or the mainframe) highlights everything routed through it and dims the rest.
- **Fly to…** finds a block by name and flies the camera there; the dimension tabs switch between Overworld, Nether and others.
- **Blocks:** the world around your network as voxels – the real terrain, caves and your base, coloured like a Minecraft map, with soft shadows in corners, glowing lights and see-through water. Only blocks that touch air are sent (no x-ray for ores), only within about 40 blocks of your devices and only from loaded chunks. **Cut** removes everything above a height (slider) so you can look into an underground base; **Night** dims the world and lets the lights glow.
- The blocks load nearest to the mainframe first, many at once, and this browser keeps them: the next time you open 3D the base is there at once and only changed blocks are fetched again. The line next to the switches says what is happening (loading, updating, building). Removing the mainframe from this browser also removes its stored blocks.
- Far away, blocks are drawn larger so the base stays readable; the coloured dot above each block is its status.
- 3D needs WebGL. Without it (switched off, very old graphics) the dashboard shows the 2D map instead. The 3D view is loaded only when you open it, so it does not slow down the rest of the dashboard.

From Lua, `cloud_mainframe.getNetwork()` returns the same nodes, links and routes.

