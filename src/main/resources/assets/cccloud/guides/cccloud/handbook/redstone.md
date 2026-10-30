---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Redstone devices"
  position: 3
  icon: cccloud:cloud_lamp
---

# Redstone devices

Switch a lamp from your phone without a computer, Lua or channels: **place it, link it with the Link Card, done.** Four small blocks, each cheaper than an uplink.

| Block | What it does | On the dashboard |
|---|---|---|
| **Cloud Switch** | Sticks to any face like a lever – on a wall, on the floor or under a ceiling. Powers like a lever: strong into the block it sits on, weak around it. On/off (0 or 15) or analog (0–15). | a switch, or a slider in analog mode |
| **Cloud Button** | Like a button: a short pulse, 2–40 ticks (default 10 = half a second). | a button |
| **Cloud Lamp** | A lamp block that lights up by itself – no redstone needed. Brightness 1–15; can also light up on a redstone signal. | a switch and a brightness slider |
| **Cloud Sensor** | Reads the redstone signal it gets (the strongest neighbour, 0–15). | a light (on/off) and the level |

## Set up

1. Craft and place the device (recipes [below](redstone.md)).
2. Right-click the mainframe with a **Cloud Link Card**, then the device – like an uplink, and with the same rights (owner, admins, “Link devices”).
3. It shows up on the dashboard by itself: on the card **Redstone**, or on the card of its group.

In the world, a click switches a switch or a lamp and presses a button. **Sneak + right-click** (any click on a sensor) opens the device's screen: what it is, its state, name and group, mode or pulse length, PIN and confirmation, and how it is connected.

## Groups and cards

- Every device has a **name** and an optional **group**. Devices with the same group on the same mainframe share **one card** – for example “Hall” with six lamp switches. Upper and lower case do not matter.
- Devices without a group share the automatic card **Redstone** (one per mainframe).
- A card with two or more switches or lamps gets **All on** and **All off**.
- At most 32 devices per card; more continue on “Hall (2)”.
- Name and group are set in the device's screen, or on the dashboard: **Network** → tap the device → **Dashboard** (needs “Link devices”).
- The cards are normal cards: arrange them in the [layout editor](dashboard.md), move single controls onto your own cards, show them on a Cloud Tablet or Cloud Display and share them read-only.

## Rules, scenes and the API

- **Rules:** pick a sensor under **When** → “a widget value” (e.g. *above 0* = on) and send a push notification, run a scene or switch a lamp. Example: when *Door sensor* is above 0 – then *Porch light* on and an alert “Someone is at the door”.
- **Scenes:** a step “use a control” on a switch, a lamp (also its brightness) or a button.
- Rules and scenes keep working when a device moves to another group.
- **[Public API](api.md):** the cards are ordinary sections – read them with `read:sections`, switch with `action:widgets` (`POST …/sections/<card>/widgets/<id>` with `{"value": true}`). Devices with a PIN are not reachable through the API.
- **History:** the lamp's light and the sensor's level are recorded (24 h, limited by the mainframe's Memory upgrade) – tap the chart symbol.

## Rights and protection

- Using the controls needs **“Switch redstone”** (operators have it). Viewers and share links only watch.
- Per device: **Ask for the mainframe PIN** and **Ask before switching** (in its screen, needs “Link devices”). “All on / All off” asks if any device of the card does.
- Linking, renaming and the settings need **“Link devices”** on the mainframe; anyone who may use the block can still click it by hand, like a lever.
- Each device takes at most 4 changes per second from the dashboard, rules, scenes and the API (`redstoneDeviceChangesPerSecond`, short bursts of 8); clicks in the world are not limited.

## Range, capacity and power

| | Redstone device | Cloud Uplink |
|---|---|---|
| Range to the nearest node | 32 blocks (`redstoneDeviceRange`) | 48 |
| Device capacity | 0.25 (`redstoneDeviceCapacityWeight`) | 1 |
| Mainframe power | 1 FE/t (`mainframePerRedstoneDeviceFE`) | 4 FE/t |

- Radio only (no cables); [relays](network.md) extend the range as for uplinks. Out of range or over capacity, its controls answer “no connection to the mainframe”.
- A device in an unloaded chunk stays on its card with its last state; its controls answer “not loaded”.
- The devices show on the network map, in the Cloud Analyzer and in Jade.

## Recipes

### Cloud Switch
<RecipeFor id="cccloud:cloud_switch" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Button
<RecipeFor id="cccloud:cloud_button" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Lamp
<RecipeFor id="cccloud:cloud_lamp" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Sensor
<RecipeFor id="cccloud:cloud_sensor" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

