---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Redstone devices"
  position: 3
  icon: cccloud:cloud_lamp
---

# Redstone devices

Switch a lamp from your phone without a computer, Lua or channels: **place it, link it with the Link Card, done.** Four small blocks, each cheaper than an uplink – and the [Advanced Cloud Sensor](redstone.md), which tells you who or what comes into a zone.

| Block | What it does | On the dashboard |
|---|---|---|
| **Cloud Switch** | Sticks to any face like a lever – on a wall, on the floor or under a ceiling. Powers like a lever: strong into the block it sits on, weak around it. On/off (0 or 15) or analog (0–15). | a switch, or a slider in analog mode |
| **Cloud Button** | Like a button: a short pulse, 2–40 ticks (default 10 = half a second). | a button |
| **Cloud Lamp** | A lamp block that lights up by itself – no redstone needed. Brightness 1–15; can also light up on a redstone signal. | a switch and a brightness slider |
| **Cloud Sensor** | Reads the redstone signal it gets (the strongest neighbour, 0–15). | a light (on/off) and the level |
| **Advanced Cloud Sensor** | Watches a zone in front of it: who comes in (a player's name) or what (the kind of mob). Pushes a notification and powers redstone while something is inside. | its own card: who is inside, the last events, today's count, arm/disarm and the filters |

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
- A device in another team's claim is blocked: its card shows the claim instead of the switch – see [claims](faq.md).

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
<RecipesFor id="cccloud:cloud_switch" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Button
<RecipesFor id="cccloud:cloud_button" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Lamp
<RecipesFor id="cccloud:cloud_lamp" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Cloud Sensor
<RecipesFor id="cccloud:cloud_sensor" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

### Advanced Cloud Sensor
<RecipesFor id="cccloud:advanced_cloud_sensor" fallbackText="This recipe is not available in this modpack (a required mod is missing or the pack changed it)." />

## Advanced Cloud Sensor

A room nobody should enter? Place an **Advanced Cloud Sensor** – and you get a push notification as soon as someone comes in, and **who** it was: a player's name, or the kind of mob.

1. Craft it (recipe [above](redstone.md)), place it on a wall, floor or ceiling and link it with the Link Card like any redstone device.
2. Click it to open its screen: the zone, what it detects, notifications. While you hold an Advanced Cloud Sensor or a Link Card, or have the screen open, the zone is outlined in the world (cyan = watching, grey = not watching).
3. On the dashboard it gets **its own card** – and an “occupied” light on its group's Redstone card.

**The zone** is a box in front of the sensor (starting at the sensor, away from the block it sits on) or around it: width × height × depth along the world's X, Y and Z, 3 × 3 × 3 by default, at most 9 × 5 × 9 (server config `advancedSensorMaxZoneX/Y/Z`). It must lie in your own claim or in unclaimed land – not in another team's claim (FTB Chunks), not in spawn protection and not beyond the world border. If another team claims part of it later, the sensor stops watching until that changes (its card says so).

**What it sees:** players (by name), hostile mobs, passive mobs (animals, villagers, golems), dropped items and other things that move (minecarts, boats, armor stands) – each with its type, e.g. “Zombie” or “Villager”. It looks twice a second, only in loaded chunks. Spectators and players hidden by a vanish mod are never reported.

| Setting | What it does |
|---|---|
| Detect | everyone, players only, mobs only, or hostile mobs only |
| Ignore me and my team | on by default: you (who placed it) and the members of your teams (scoreboard team, FTB Teams) are not reported |
| Only these players | when set, just these players are reported – even from your team |
| Never these players | never reported |
| Armed | disarmed: nothing is logged, notified or handed to rules – the redstone output still works. Arming does not report who is already inside. |
| Redstone output | while something that passes the filter is inside, it powers like a lever: strong into the block it sits on, weak around it – no mainframe needed |
| Notify | Off, Info, Warning (default) or Critical: the level of its alerts |

**The card** shows who is inside right now, the last 20 events (“Steve came in · 2m ago”) and how many came in today, plus the controls above and the zone. *Occupied* keeps a 24 h history.

**Notifications:** every entry becomes an alert in the dashboard and goes to your [push targets](automation.md) (Discord, ntfy, webhook) from their level up: “Steve entered Reactor room”. No spam: the first alert goes out at once, whatever comes in during the next 10 seconds follows as **one** summary (“3 Zombies entered Reactor room”), and an entity that walks out and back in within 60 seconds is not reported again (`advancedSensorAlertCooldown`, `advancedSensorEntityCooldown`).

**Rules:** under **When** pick “an Advanced Sensor detects …” – anyone or anything, a player (optionally one player by name), a mob, a hostile or passive mob, an item or something else. Write `{who}` in an alert: “\{who\} betritt den Reaktorraum” becomes “Steve betritt den Reaktorraum” or “2 Zombies betritt …”. See [Rules](automation.md).

### Who sees names

Player names are personal data on a shared server, so:

- Only players with the **operator** role or higher on the mainframe (the right “See coordinates”) see names – on the card, in alerts and in the API. Viewers, share links and Cloud Displays always see **“a player”**; mobs keep their type.
- Push targets get names (admins set them up).
- The server can turn names off completely: `advancedSensorPlayerNames = false` – then everyone sees “a player”, no names are stored, and rules for one player by name never fire. The *only* and *never* lists keep working.

### Rights, range and power

- Arming and disarming need **“Switch redstone”** (operators) – on the card and in its screen; PIN and confirmation from its screen protect the arm switch. Every other setting needs **“Link devices”** (admins).
- Range 32 blocks like the other redstone devices; it counts **0.5** of a device against the mainframe's capacity (`advancedSensorCapacityWeight`) and costs **4 FE/t while armed** (`mainframePerAdvancedSensorFE`), 1 FE/t disarmed.

