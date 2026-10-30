import * as C from "./code.ts";

const { code, recipe, RECIPES: R } = C;

export const en: Record<string, string> = {
  // ---------------------------------------------------------------- start
  start: `# Getting started

CC: Cloud puts your ComputerCraft computers and storage on a dashboard in any browser – PC, tablet or phone. The Minecraft server hosts the dashboard itself – no external service, no sign-up.

> **Tip:** The quickest path: craft a Cloud Mainframe, power it, put a Cloud Uplink on a computer, link the two with a Cloud Link Card and pair your browser. The computer appears on the dashboard automatically – no code needed.
>
> Only want to switch a lamp? Place a **Cloud Lamp** (or a Cloud Switch next to your redstone lamps) and link it – it shows up as a switch on the dashboard. See [Redstone devices](/docs/redstone).

## The blocks {#blocks}

| Block / item | What it does |
|---|---|
| **Cloud Mainframe** | The heart of a base. Two blocks tall. Hosts the connection to the dashboard, shows the pairing code and manages devices. One per base or team. Needs FE power. |
| **Cloud Uplink** | Sticks to a block face like a modem; on low or small faces (slab sides, solar panels) it becomes a compact module; on top or bottom it needs a full face. On a computer it becomes that computer's card on the dashboard. Range 128 blocks, same dimension. |
| **Advanced Cloud Uplink** | Same, but unlimited range and works across dimensions. |
| **Cloud Probe** | Reads **one** machine up to 8 blocks away as if an uplink were attached to it – for machines built into walls or with every face taken by pipes and cables. Expensive; counts as two devices. See *Machines without a free face* below. |
| **Cloud Link Card** | Links uplinks, bridges and redstone devices to a mainframe. |
| **Cloud Switch / Button / Lamp / Sensor** | Small [redstone devices](/docs/redstone) without a computer: link them and switch lamps, doors and machines from the dashboard. Devices with the same group share one card. |
| **ME / RS Cloud Bridge** | Joins an Applied Energistics 2 or Refined Storage network. With an uplink on it you get a ready-made storage card (search, craft, watchlist). Only exists when AE2 / RS is installed. |
| **Cloud Core** | Crafting ingredient. |

## Crafting recipes {#recipes}

Ingredients shown as "(any)" accept every variant from the tag (e.g. any gold ingot). JEI/EMI show the same recipes in-game.

### Cloud Core
${recipe(R.core)}

### Cloud Mainframe
${recipe(R.mainframe)}

### Cloud Uplink
${recipe(R.uplink)}

### Advanced Cloud Uplink
${recipe(R.advancedUplink)}

### Cloud Probe
${recipe(R.probe)}
Without Allthemodium the vibranium ingots are 4 diamonds and 2 netherite scrap (all recipes: JEI/EMI or the recipes page).

### Cloud Link Card
${recipe(R.linkCard)}

### ME and RS Cloud Bridge
${recipe(R.meBridge)}
${recipe(R.rsBridge)}

## Place the mainframe {#mainframe}

1. Place the **Cloud Mainframe** (it takes two blocks of height). Whoever places it is its **owner**.
2. Right-click it: the screen shows the status, the dashboard address (URL) and a pairing code.
3. Turn the dashboard **on** in that screen (or sneak + right-click with an empty hand).

A redstone signal on either half is a **kill switch**: the mainframe goes offline until the signal is gone.

### Breaking and moving the mainframe {#moving}

The mainframe keeps its identity in its item. Break it (pickaxe) and place the item again – anywhere, also in another base – and it is the **same mainframe**: devices, accounts, PIN, layout, rules, scenes and API keys are back, and linked uplinks and bridges reconnect by themselves. It still belongs to its owner, whoever places it. Upgrade cards drop as separate items; put them back in.

- **Nothing is deleted right away.** After a break the server keeps everything for **7 days** (\`mainframeRetentionDays\`); the dashboard shows the mainframe as **Removed** meanwhile. Only after that, or with \`/cccloud mainframe purge <id>\`, is its data deleted.
- **Lost the item?** In creative a break drops nothing: \`/cccloud mainframe recover\` lists your removed mainframes and hands you their item (once per break). If the item did drop, place that one – or ask a server admin, who can always recover.
- **Misclick guard** (\`mainframeBreakProtection\`): in creative the mainframe only breaks while you **sneak**; in survival only its owner, its admins and server admins can break it. Claim mods protect it as well.

## Power (FE) {#power}

The mainframe runs on **FE** (Forge Energy, also called RF) – the unit used by Powah, Ender IO, Mekanism's FE output, Thermal, AE2 and most tech mods. Connect any FE cable or generator to either half.

| | Default |
|---|---|
| Base consumption | 40 FE/t |
| Per uplink / advanced uplink / bridge | +4 / +8 / +10 FE/t |
| Per connected dashboard | +2 FE/t |
| Per extra hop of a routed device | +1 FE/t |
| Buffer | 200,000 FE |
| Max input | 2,000 FE/t |

Without power the mainframe goes offline (after 5 seconds on an empty buffer) and the dashboard says **"Mainframe has no power"**. It reconnects by itself once the buffer refills. Uplinks and bridges need no FE of their own; [relays](/docs/network#power) do. Server admins can turn the requirement off (\`requirePower = false\`).

## Link uplinks and bridges {#linking}

1. Place a **Cloud Uplink** on a side of a computer (or on an ME / RS Cloud Bridge).
2. Right-click the **mainframe** with a **Cloud Link Card** – the card now says "Linked to Mainframe @ x y z".
3. Right-click the **uplink** (or bridge) with the card. Done – its status light turns green.

Sneak + right-click into the air clears the card. Only the owner and trusted players can link.

### Machines without a free face: Cloud Probe {#probe}

An uplink reads the block it sits on. If a machine is built into a wall, or every face is taken by pipes and cables, use a **Cloud Probe** somewhere within 8 blocks (same dimension):

1. Place the probe on any full face nearby and link it to the mainframe like an uplink.
2. **Sneak + right-click the machine** with the Link Card – the card remembers it (and the face you clicked).
3. **Right-click the probe** with the card. It now shows the machine's card, exactly as an uplink on that face would.

Picking the machine is a normal interaction, so claims and spawn protection apply: where you may not use the machine, you cannot pick it. Only you can hand your pick to a probe, within 10 minutes. The probe keeps checking: if another team claims the machine's chunk, or the server lowers the range, it stops reading until that changes; a probe that is moved (with a block mover or a contraption) forgets its machine. Right-click the probe for its screen: it shows what it reads and has **Clear target**; the Cloud Analyzer, the dashboard's network map (2D and 3D) and the mini map in the mainframe screen draw a thin purple line to the machine. If the machine is removed, the probe forgets it.

If an uplink shows no card and the screen says *"This side exposes nothing"*, the machine offers its energy, items or fluids on another face – move the uplink there (the screen names one) or use a probe.

## Pair a browser {#pairing}

1. Right-click the mainframe and note the **pairing code** (8 characters, valid 10 minutes, single use).
2. Open the dashboard URL from the mainframe screen in your browser (e.g. \`http://192.168.1.20:8787\`). On the same Wi-Fi this just works.
3. Enter the code and a name for this browser, click **Pair device**.
4. In Minecraft, click **[Allow]** in the chat (or in the mainframe screen). The browser continues by itself.

> **Tip:** Link your player once with \`/cccloud link\` and the browser sees every mainframe you have a role on – see [accounts and roles](/docs/accounts). Install the dashboard like an app: "Install app" in desktop Chrome/Edge, "Add to Home Screen" on tablets and phones.

What you see next: [the dashboard](/docs/dashboard). Want your own widgets? Start with [CC basics](/docs/cc-basics) and the [Lua reference](/docs/lua).
`,

  // ------------------------------------------------------------ cc basics
  "cc-basics": `# CC basics for beginners

New to ComputerCraft? This page explains the few things you need to run a dashboard program.

## Shell vs. Lua {#shell-vs-lua}

A computer starts in the **shell** – a command line like a terminal. The shell runs **programs** by name (\`edit\`, \`ls\`, \`cloud-demo\`). It does **not** understand Lua code.

> **Warning:** If you type Lua directly into the shell, e.g. \`cloud.setTitle("x")\`, you get **"No such program"**. The shell looks for a program called \`cloud.setTitle("x")\`. Put Lua code into a file (below) or use the \`lua\` prompt.

## Write a program with edit {#edit}

${code("", C.SHELL_EDIT)}

1. Type \`edit startup.lua\` and press Enter. An editor opens.
2. Type or paste your code. (Pasting from the dashboard: copy with the **Copy** button, then Ctrl+V in Minecraft.)
3. Press **Ctrl**, choose **Save** with the arrow keys and Enter, then **Ctrl** → **Exit**.

A file named \`startup.lua\` runs automatically whenever the computer boots – perfect for dashboards.

## Run a program {#run}

Type its name without \`.lua\`:

${code("", C.SHELL_RUN)}

Stop a running program with **Ctrl+T** (hold it for a second). Reboot with **Ctrl+R**.

## Drag files onto the computer {#drag-files}

Write the code on your PC in any editor, save it as \`startup.lua\` and **drag the file onto the open computer screen** in Minecraft. CC: Tweaked uploads it into the current folder.

## Quick tests with the lua prompt {#lua-prompt}

\`lua\` opens an interactive Lua prompt – handy to try one line at a time:

${code("", C.SHELL_LUA)}

## Useful commands {#commands}

| Command | Does |
|---|---|
| \`ls\` | list files |
| \`edit <file>\` | edit or create a file |
| \`rm <file>\` | delete a file |
| \`help cloud\` | CC: Cloud quick reference |
| \`cloud-demo\` | shows every dashboard widget |
| \`cloud-storage\` | custom storage dashboard for an ME / RS bridge |
| \`peripheral\` | in Lua: \`peripheral.getNames()\` lists attached blocks |

Next: [the Lua reference](/docs/lua) or the [cloud.ui helper](/docs/ui-library).
`,

  // ------------------------------------------------------------- dashboard
  dashboard: `# The dashboard

Open the URL shown by the mainframe. One browser can hold several mainframes; tap one to open its dashboard.

## Cards and widgets {#widgets}

Every uplink becomes a **card**; [redstone devices](/docs/redstone) share one card per group. A computer without its own program shows the automatic **Computer card**: power, Turn on / Reboot / Shut down and its six redstone sides (tap a side to switch its output, the LED shows the input; analog levels under "Advanced").

**Machines on the computer's cable network.** If the computer has a wired modem with CC networking cable to other machines (their modems switched on), the uplink on the computer also shows a card for each of those machines – the same card an uplink sitting on the machine would show (energy, tanks, storage controllers, mod machines), with the same controls and permissions. Up to 8 per uplink (server setting \`wiredMachineCards\`); a machine with its own uplink or probe keeps that one, and machines with the same name are told apart by the computer's name for them ("Energy Cell (energy_cell_2)"). Plain chests have no card.

**Peripheral card – for everything else.** An uplink or probe on a block that has no card of its own, but that CC: Tweaked offers as a peripheral (the methods a computer could call on it, including CC's energy, item and fluid methods), shows the **Peripheral card**: the block's name and its peripheral types. Pick what it shows in the [layout editor](#edit-mode): click the card, then under **Values** tick up to 6 methods that take no arguments (\`get…\`, \`is…\`, \`list…\`, and \`size\`, \`list\`, \`tanks\` of storage blocks), up to 2 numbers also as a chart, and **Save values**. A yes/no method becomes a lamp, everything else a short text. If the block has a \`set…(true/false)\` method, you can make it the card's **Switch** (operators switch it, with a confirm step and – unless you turn it off – the PIN). Only the methods you picked are ever called, every few seconds and less often if they are slow; methods that need arguments, and those of peripherals that do not say which arguments they take, are not offered – a Lua program can still use them. Choosing values needs the right to edit the layout. A peripheral on a computer's cable network gets no Peripheral card (only machines with a real card do).

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
| item search, watchlist, jobs | storage widgets of the [bridges](/docs/bridges) |

## Pages and the layout editor {#edit-mode}

Programs can put widgets on **pages** (tabs: on top in wide windows, at the bottom on narrow screens) and in **groups**. Admins see a **pencil**: it turns the dashboard itself into the editor – you arrange the real, live cards.

- **Move:** drag a card by its header, or a widget anywhere. The others make room and glide aside; a dashed outline shows where it lands. Drop a widget on another card, on empty space (it gets its **own new card**) or on a page tab (hover a moment to open that page).
- **Resize:** drag the right edge, the bottom edge or the corner – the badge shows the size (cards use 12 columns of the page, widgets 12 columns of their card).
- **Select several widgets** with Shift-click and drag one of them to move them together.
- **Settings** (⚙ or click): label, accent colour (a button takes it as its colour – also a Cloud Button's, which is cyan by default), icon, size and the display style with a live preview – a gauge as dial, bar or big number, a label as row or big number, a chart as chart or sparkline, an indicator as chip or lamp. Cards get a title, icon, accent and a compact or comfortable style; a [Peripheral card](#widgets) also its values and switch.
- **Hide** with ×. **+ Add** lists everything hidden (drag it back or press Show) and shows every widget type live with the Lua line that creates it.
- **Touch:** press and hold to pick up (a short vibration), then drag – a normal swipe still scrolls. **Keyboard:** Tab to a card or widget, Space picks it up, arrow keys move, Shift + arrow keys resize, Page Up/Down move a widget to the previous/next card, Space drops, Esc cancels. Press **?** for all shortcuts.
- **Undo/redo** (Ctrl/⌘ + Z, Ctrl/⌘ + Shift + Z), **Reset** to the scripts' default, and **Save** (Ctrl/⌘ + S). Leaving with unsaved changes asks first.

**Save** stores the layout on the server – all browsers see it. A script restart keeps the layout because widget ids stay the same. On narrow screens the cards stack in reading order (two columns on phones).

## Alerts {#alerts}

Programs send alerts with \`cloud.alert(text, level)\`. They pop up as toasts and are kept in the **bell** log. Info toasts fade; warn and crit stay until dismissed. Tap **Enable notifications** in the alert log to get system notifications for crit alerts while the tab is in the background.

## PIN-protected controls {#pin}

Widgets with \`pin=true\` ask for the mainframe PIN on a keypad before they act. The owner sets it with \`/cccloud pin set <4-8 digits>\`. After 5 wrong PINs the device is locked for 5 minutes and the owner is notified in-game.

## States you may see {#states}

| Message | Meaning |
|---|---|
| Dashboard disabled | the dashboard is switched off in the mainframe screen |
| Offline | the mainframe chunk is unloaded or the redstone kill switch is on |
| Removed | the mainframe block was broken – place its item again to bring everything back ([how](/docs/start#moving)) |
| Mainframe has no power | the FE buffer is empty – see [power](/docs/start#power) |
| Uplink offline | that uplink is unloaded or out of range |
| Read-only device | this device may watch but not control |
| Server not reachable | see [troubleshooting](/docs/faq#not-reachable) |
`,

  // ------------------------------------------------------------- redstone
  redstone: `# Redstone devices

Switch a lamp from your phone without a computer, Lua or channels: **place it, link it with the Link Card, done.** Four small blocks, each cheaper than an uplink – and the [Advanced Cloud Sensor](/docs/redstone#advanced-sensor), which tells you who or what comes into a zone.

| Block | What it does | On the dashboard |
|---|---|---|
| **Cloud Switch** | Sticks to any face like a lever – on a wall, on the floor or under a ceiling. Powers like a lever: strong into the block it sits on, weak around it. On/off (0 or 15) or analog (0–15). | a switch, or a slider in analog mode |
| **Cloud Button** | Like a button: a short pulse, 2–40 ticks (default 10 = half a second). | a button |
| **Cloud Lamp** | A lamp block that lights up by itself – no redstone needed. Brightness 1–15; can also light up on a redstone signal. | a switch and a brightness slider |
| **Cloud Sensor** | Reads the redstone signal it gets (the strongest neighbour, 0–15). | a light (on/off) and the level |
| **Advanced Cloud Sensor** | Watches a zone in front of it: who comes in (a player's name) or what (the kind of mob). Pushes a notification and powers redstone while something is inside. | its own card: who is inside, the last events, today's count, arm/disarm and the filters |

## Set up {#setup}

1. Craft and place the device (recipes [below](/docs/redstone#recipes)).
2. Right-click the mainframe with a **Cloud Link Card**, then the device – like an uplink, and with the same rights (owner, admins, “Link devices”).
3. It shows up on the dashboard by itself: on the card **Redstone**, or on the card of its group.

In the world, a click switches a switch or a lamp and presses a button. **Sneak + right-click** (any click on a sensor) opens the device's screen: what it is, its state, name and group, mode or pulse length, PIN and confirmation, and how it is connected.

## Groups and cards {#groups}

- Every device has a **name** and an optional **group**. Devices with the same group on the same mainframe share **one card** – for example “Hall” with six lamp switches. Upper and lower case do not matter.
- Devices without a group share the automatic card **Redstone** (one per mainframe).
- A card with two or more switches or lamps gets **All on** and **All off**.
- At most 32 devices per card; more continue on “Hall (2)”.
- Name and group are set in the device's screen, or on the dashboard: **Network** → tap the device → **Dashboard** (needs “Link devices”).
- The cards are normal cards: arrange them in the [layout editor](/docs/dashboard#edit-mode), move single controls onto your own cards, show them on a Cloud Tablet or Cloud Display and share them read-only.

## Rules, scenes and the API {#automation}

- **Rules:** pick a sensor under **When** → “a widget value” (e.g. *above 0* = on) and send a push notification, run a scene or switch a lamp. Example: when *Door sensor* is above 0 – then *Porch light* on and an alert “Someone is at the door”.
- **Scenes:** a step “use a control” on a switch, a lamp (also its brightness) or a button.
- Rules and scenes keep working when a device moves to another group.
- **[Public API](/docs/api):** the cards are ordinary sections – read them with \`read:sections\`, switch with \`action:widgets\` (\`POST …/sections/<card>/widgets/<id>\` with \`{"value": true}\`). Devices with a PIN are not reachable through the API.
- **History:** the lamp's light and the sensor's level are recorded (24 h, limited by the mainframe's Memory upgrade) – tap the chart symbol.

## Rights and protection {#rights}

- Using the controls needs **“Switch redstone”** (operators have it). Viewers and share links only watch.
- Per device: **Ask for the mainframe PIN** and **Ask before switching** (in its screen, needs “Link devices”). “All on / All off” asks if any device of the card does.
- Linking, renaming and the settings need **“Link devices”** on the mainframe; anyone who may use the block can still click it by hand, like a lever.
- Each device takes at most 4 changes per second from the dashboard, rules, scenes and the API (\`redstoneDeviceChangesPerSecond\`, short bursts of 8); clicks in the world are not limited.

## Range, capacity and power {#network}

| | Redstone device | Cloud Uplink |
|---|---|---|
| Range to the nearest node | 32 blocks (\`redstoneDeviceRange\`) | 48 |
| Device capacity | 0.25 (\`redstoneDeviceCapacityWeight\`) | 1 |
| Mainframe power | 1 FE/t (\`mainframePerRedstoneDeviceFE\`) | 4 FE/t |

- Radio only (no cables); [relays](/docs/network) extend the range as for uplinks. Out of range or over capacity, its controls answer “no connection to the mainframe”.
- A device in an unloaded chunk stays on its card with its last state; its controls answer “not loaded”.
- The devices show on the network map, in the Cloud Analyzer and in Jade.

## Recipes {#recipes}

### Cloud Switch
${recipe(R.cloudSwitch)}

### Cloud Button
${recipe(R.cloudButton)}

### Cloud Lamp
${recipe(R.cloudLamp)}

### Cloud Sensor
${recipe(R.cloudSensor)}

### Advanced Cloud Sensor
${recipe(R.advancedSensor)}
${recipe(R.advancedSensorFallback)}

## Advanced Cloud Sensor {#advanced-sensor}

A room nobody should enter? Place an **Advanced Cloud Sensor** – and you get a push notification as soon as someone comes in, and **who** it was: a player's name, or the kind of mob.

1. Craft it (recipe [above](/docs/redstone#recipes)), place it on a wall, floor or ceiling and link it with the Link Card like any redstone device.
2. Click it to open its screen: the zone, what it detects, notifications. While you hold an Advanced Cloud Sensor or a Link Card, or have the screen open, the zone is outlined in the world (cyan = watching, grey = not watching).
3. On the dashboard it gets **its own card** – and an “occupied” light on its group's Redstone card.

**The zone** is a box in front of the sensor (starting at the sensor, away from the block it sits on) or around it: width × height × depth along the world's X, Y and Z, 3 × 3 × 3 by default, at most 9 × 5 × 9 (server config \`advancedSensorMaxZoneX/Y/Z\`). It must lie in your own claim or in unclaimed land – not in another team's claim (FTB Chunks), not in spawn protection and not beyond the world border. If another team claims part of it later, the sensor stops watching until that changes (its card says so).

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

**Notifications:** every entry becomes an alert in the dashboard and goes to your [push targets](/docs/automation#push) (Discord, ntfy, webhook) from their level up: “Steve entered Reactor room”. No spam: the first alert goes out at once, whatever comes in during the next 10 seconds follows as **one** summary (“3 Zombies entered Reactor room”), and an entity that walks out and back in within 60 seconds is not reported again (\`advancedSensorAlertCooldown\`, \`advancedSensorEntityCooldown\`).

**Rules:** under **When** pick “an Advanced Sensor detects …” – anyone or anything, a player (optionally one player by name), a mob, a hostile or passive mob, an item or something else. Write \`{who}\` in an alert: “{who} betritt den Reaktorraum” becomes “Steve betritt den Reaktorraum” or “2 Zombies betritt …”. See [Rules](/docs/automation#rules).

### Who sees names {#advanced-sensor-names}

Player names are personal data on a shared server, so:

- Only players with the **operator** role or higher on the mainframe (the right “See coordinates”) see names – on the card, in alerts and in the API. Viewers, share links and Cloud Displays always see **“a player”**; mobs keep their type.
- Push targets get names (admins set them up).
- The server can turn names off completely: \`advancedSensorPlayerNames = false\` – then everyone sees “a player”, no names are stored, and rules for one player by name never fire. The *only* and *never* lists keep working.

### Rights, range and power {#advanced-sensor-rights}

- Arming and disarming need **“Switch redstone”** (operators) – on the card and in its screen; PIN and confirmation from its screen protect the arm switch. Every other setting needs **“Link devices”** (admins).
- Range 32 blocks like the other redstone devices; it counts **0.5** of a device against the mainframe's capacity (\`advancedSensorCapacityWeight\`) and costs **4 FE/t while armed** (\`mainframePerAdvancedSensorFE\`), 1 FE/t disarmed.
`,

  // ------------------------------------------------------------------ lua
  lua: `# Lua reference

Everything a program can do with CC: Cloud. In-game, \`help cloud\` prints a short version.

## Getting the peripheral {#peripheral}

${code("lua", 'local cloud = peripheral.find("cloud_uplink")')}

The uplink must touch the computer or sit on its wired modem network. Several uplinks? Use \`peripheral.wrap("cloud_uplink_0")\`.

## A first program {#first-program}

${code("lua", C.LUA_FIRST)}

## Widgets {#widgets}

Every widget takes an **id** (1–32 characters: a–z, 0–9, _), a label and an optional options table. Calling it again with the same id updates it.

| Method | Options |
|---|---|
| \`cloud.setTitle(text)\` | card title (max 48 characters) |
| \`cloud.button(id, label, opts)\` | color, confirm, pin |
| \`cloud.toggle(id, label, opts)\` | value, confirm, pin |
| \`cloud.slider(id, label, opts)\` | min, max, step, value, unit, pin |
| \`cloud.gauge(id, label, opts)\` | min, max, unit, warn, crit, value |
| \`cloud.label(id, text)\` | – |
| \`cloud.chart(id, label, opts)\` | min, max, unit, points (60–300), interval, style = "line" \\| "sparkline" |
| \`cloud.progress(id, label, opts)\` | min, max, unit, value |
| \`cloud.indicator(id, label, opts)\` | value, onColor, offColor |
| \`cloud.select(id, label, opts)\` | options = {"low","mid","high"}, value |
| \`cloud.number(id, label, opts)\` | min, max, step, value, unit |
| \`cloud.text(id, label, opts)\` | maxLength (≤ 256), value, placeholder |
| \`cloud.buttons(id, label, opts)\` | options = {"A","B","C"} |
| \`cloud.itemSearch(id, label, opts)\` | bridge = peripheral name (a side, or a bridge on the computer's cable network such as \`me_cloud_bridge_0\`), max |
| \`cloud.watchlist(id, label, opts)\` | bridge |
| \`cloud.jobs(id, label, opts)\` | bridge |

Colours: cyan, green, yellow, red, gray. A gauge is "low is bad" when crit < warn (e.g. coolant).

## Values {#values}

| Method | Does |
|---|---|
| \`cloud.set(id, value)\` | update a gauge, label, toggle, chart (appends a point), … |
| \`cloud.setMany({id=value, …})\` | several values in one go |
| \`cloud.remove(id)\` | remove a widget |
| \`cloud.clear()\` | remove everything (the automatic Computer card returns) |

${code("lua", C.LUA_SETMANY)}

## Events {#events}

When someone uses a widget, every computer attached to the uplink gets a \`cloud_action\` event:

${code("lua", C.LUA_EVENT)}

| Widget | value |
|---|---|
| button | \`true\` |
| toggle | \`true\` / \`false\` |
| slider, number | number |
| select, buttons | the chosen option (string) |
| text | string (plain text) |
| itemSearch craft | table {op="craft", name, count, jobId} |

\`user\` is the device name, \`side\` the uplink's peripheral name. At most 64 actions wait in the queue per computer.

## Layout: pages and groups {#layout}

${code("lua", C.LUA_LAYOUT)}

- \`cloud.page(id, title, {icon=})\` – a tab. With pages an uplink may hold 64 widgets instead of 32.
- \`cloud.group(id, title, {page=, cols=1..4})\` – a card; widgets join it with \`group=\`.
- Every widget also accepts \`page\`, \`group\`, \`span\` (1–4), \`order\` (0–10000), \`color\` and \`icon\`.
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick, lever, button, lamp, sensor.

## Alerts and status {#alerts}

| Method | Does |
|---|---|
| \`cloud.alert(text, level)\` | toast + log; level "info", "warn" or "crit"; max 1 per second |
| \`cloud.notify(text, level)\` | same as alert |
| \`cloud.isOnline()\` | true if the mainframe is online and the dashboard on |
| \`cloud.getClients()\` | number of connected dashboards |
| \`cloud.getLastClientSeen()\` | seconds since a dashboard was connected, or nil |

## cloud_mainframe {#mainframe}

A computer next to the mainframe can read it:

| Method | Returns |
|---|---|
| \`isEnabled()\` | dashboard switched on |
| \`isOnline()\` | loaded, powered, not redstone-killed and enabled |
| \`setEnabled(bool)\` | switch the dashboard on/off |
| \`getUplinks()\` | list of {id, title, online, loaded, x, y, z, dimension} |
| \`getEnergy()\`, \`getEnergyCapacity()\`, \`getEnergyUsage()\` | FE stored, buffer size, FE/t |
| \`getNetwork()\` | {nodes, links, routes} – see [network](/docs/network#map) |

${code("lua", C.LUA_MAINFRAME)}

The pairing code is never readable from Lua.

## Bridges {#bridges}

Peripheral types \`me_cloud_bridge\` and \`rs_cloud_bridge\`, same methods:

| Method | Returns |
|---|---|
| \`isConnected()\` | connected to a powered network |
| \`getEnergy()\`, \`getMaxEnergy()\`, \`getEnergyUsage()\` | network energy |
| \`getStorage()\` | {itemsUsed, itemsTotal, typesUsed, fluidsUsed, fluidsTotal} |
| \`listItems([filter], [limit=256])\` | {name, displayName, count, craftable}… |
| \`findItems(query, [limit=50])\` | same, matching id or name |
| \`getItem(name)\` | one item or nil |
| \`isCraftable(name)\` | boolean |
| \`craft(name, count)\` | {ok, jobId} or {ok=false, error} |
| \`getCraftingJobs()\` | running jobs |
| \`cancelCraft(jobId)\` | where supported |
| \`listFluids([limit])\` | fluids |

${code("lua", C.LUA_BRIDGE)}

## Limits {#limits}

- ids: 1–32 characters a–z, 0–9, _
- 32 widgets per uplink, 64 with pages
- titles and labels 48 characters, text values 256, alert text 200
- alerts: 1 per second per uplink

## Upload scripts from the browser {#upload}

Admins can put Lua programs on a computer without typing them in-game: click **</>** in the header of a computer's card.

- **Upload:** drop a \`.lua\` file or paste code, choose the file name (\`name.lua\` or \`folder/name.lua\`), then **Upload & run**. Existing files are only replaced after you confirm.
- **Run it now** starts the program in a new tab on advanced computers. Basic computers have no tabs: the dashboard asks to restart the computer and run it once.
- **Start it automatically** writes a \`startup.lua\` that runs your file at every boot.
- **Examples:** ready-made programs (control room, test program, the widget demo) – pick one, adjust it, upload.
- **Files:** see what is on the computer, open, download or delete files, and how much disk space is left.

Only admins of the mainframe see this, and changes need the mainframe PIN if one is set. The code runs on that CC computer inside CC's sandbox – never on the Minecraft server itself. Every upload is logged and the owner gets a chat note. Limits: 256 KB per file, 10 changes per minute.

## Script Store {#store}

Ready-made programs from the community: **Script Store** in the mainframe's menu (admins). Each script shows what it does, what it **uses** (redstone, peripherals, internet …) and its full source – checked against the SHA-256 in the store's index before you see it. The index is signed by the store: your browser checks the signature where it can (**signed ✓**), and the Minecraft server checks it again before it writes any file. That proves the files come from the store – not that the code is safe, so **read the code before you install it**. Pick a computer and install; run it now or start it automatically. Installed store scripts show **Update available** when there is a newer version, and you are warned before overwriting a file you changed on the computer.

On the project website, **Install** opens your dashboard at \`/store#install=<script>\` – the same view, nothing is installed without your click. Server admins can switch the store off (\`scriptStore=false\`). Community scripts run on your CC computer and can control what it controls – they cannot touch the Minecraft server.
`,

  // ---------------------------------------------------------- ui library
  "ui-library": `# cloud.ui helper library

\`cloud.ui\` ships in every computer's ROM. It wraps the uplink in a builder and runs the event loop for you: one function per widget, handlers instead of \`os.pullEvent\`, timers with \`every\`.

## Quick start {#quick-start}

${code("lua", C.UI_QUICK)}

## API {#api}

| Function | Does |
|---|---|
| \`ui.connect([uplink])\` | finds the uplink (or takes a name / wrapped peripheral), returns \`dash\` |
| \`dash:title(text)\` | card title |
| \`dash:page(id, title, opts)\`, \`dash:group(id, title, opts)\` | layout |
| \`dash:gauge(id, label, opts)\` … | every widget type: button, toggle, slider, gauge, chart, progress, indicator, select, number, text, buttons, label, itemSearch, watchlist, jobs |
| \`(…, opts, handler)\` | optional last argument: \`function(user, value)\` called on use |
| \`widget:set(value)\` | update; \`widget:remove()\`, \`widget:on(handler)\` |
| \`dash:set(id, value)\`, \`dash:setMany(t)\` | update by id |
| \`dash:on(id, handler)\` | handler for a widget |
| \`dash:every(seconds, fn)\` | timer (first call right away) |
| \`dash:onEvent(event, fn)\` | any other CC event (redstone, timer, …) |
| \`dash:notify(text, level)\` | alert |
| \`dash:clients()\`, \`dash:isOnline()\` | status |
| \`dash:clear()\`, \`dash:run()\`, \`dash:stop()\` | lifecycle |

Errors inside handlers are printed and do not stop the loop. Ctrl+T ends it.

## Example: tree farm {#example-farm}

${code("lua", C.UI_FARM)}

## Example: reactor panel {#example-reactor}

Adjust the peripheral name and methods to your reactor mod.

${code("lua", C.UI_REACTOR)}

## Built-in programs {#programs}

- \`cloud-demo\` – every widget on two pages
- \`cloud-storage [bridge] [uplink]\` – a custom storage dashboard for an ME / RS bridge
`,

  network: `# Network, relays and range

Every uplink and bridge connects to the **nearest reachable node** – the mainframe or a relay – and the network routes it to the mainframe over the best path (fewest hops, then the best signal; at most 8 hops). Like a Wi-Fi mesh: place relays to reach far-away bases.

Right-click an uplink, bridge or relay to see what it is connected to, the card it puts on the dashboard (as a live preview), its signal and – when something is wrong – a one-line fix. **Show in dashboard** sends a chat link that opens exactly that card; **Rename** changes the card's title for everyone.

## Ranges {#ranges}

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

## Signal {#signal}

Signal = 1 − distance / range, shown as 0–100 % and bars. Below 15 % a link is **weak**: it still works but shows yellow – move the device closer or add a relay.

## Relays {#relays}

- **Cloud Relay** – a mast for medium distances.
- **Advanced Cloud Relay** – longer range for nodes and devices.
- **Ender Relay** – links to other Ender Relays at any distance and **across dimensions** (e.g. to the Nether). Link Ender Relays into a group with the Link Card.

Place a relay, then link it like any device: Link Card on the mainframe, then on the relay.

### Cloud Relay
${recipe(R.relay)}

### Advanced Cloud Relay
${recipe(R.advancedRelay)}

### Ender Relay
${recipe(R.enderRelay)}

> **Tip:** If linking fails, the message names the nearest node and how far away it is. Place a relay in between.

## Cables (wired links) {#cables}

Devices can also join **by cable**, like AE2 cables: connect the uplink or bridge to the mainframe or a relay with CC: Tweaked **networking cable** and **wired modems** (right-click the modems so they turn on). Everything on the same CC wired network is a wired member.

- Cables have **no range limit**, always **100 %** signal and cost **no extra FE per hop**.
- A device can have both cable and radio; the cable is preferred, radio is the backup.
- In the dashboard cables are **solid lines** (grey = CC cable, glowing cyan = fiber), radio links **dashed** (coloured by signal), ender links **dotted**.

**Fiber Cable** (ships with CC: Cloud) works the same, but only connects our devices – a clean network of its own. Devices on fiber count **half** towards the mainframe's device capacity and pay **half** their FE. Use the **Fiber Junction** for splits and walls, and an **Ender Fiber Link** pair to join two fiber networks across any distance or dimension.

## When a relay fails {#reroute}

If a relay is broken, unloaded or out of power, everything behind it **re-routes** over another path. If there is none, those devices show **No route**. Relays never force-load chunks; parts in unloaded chunks show **Unloaded**.

Devices linked before v0.4 that are out of range under the new rules keep working and show **Out of range (legacy)** until you place a relay nearby or re-link them.

## Power {#power}

The network costs FE. The mainframe uses 40 FE/t + 4 per uplink + 8 per advanced uplink + 10 per bridge + 2 per connected dashboard + 1 per extra hop of each routed device. Relays need their own FE: Cloud Relay 20 FE/t, Advanced 60 FE/t, Ender Relay 120 FE/t. Click the power bar in the dashboard header to see the breakdown.

The breakdown also shows devices on fiber (they pay half), the **Chunk Anchor** if one is installed, and an **Efficiency** upgrade as a green line with the FE it saves. Below it, **Device capacity** shows how many devices the mainframe carries out of what its upgrade cards allow; the same number is a chip on the Network tab.

## The network map {#map}

The **Network** tab shows everything that is connected, in three views:

- **Topology** – the mainframe at the root, relays as branches, devices as leaves. Cables are solid, radio links dashed and coloured by signal (green strong, cyan ok, yellow weak), ender links purple and dotted, legacy links orange; the dot shows the status. Devices without a route are grouped under **Not connected**.
- **Map** – a top-down x/z map per dimension on a real map of the world: **Surface** (like a Minecraft map) or **Slice** – a cut at a height you choose with the Y slider, so an underground base at y −60 shows its rooms and caves (darker = deeper). Only places near your network are shown, and only chunks that were loaded. Every dimension of your network gets a tab with its name and icon – also modded ones like the Aether, the Undergarden or the Mining Dimension – and the Y slider covers that dimension's own height. Dimensions with a roof (Nether, Undergarden) open on a slice at your network's height, because their surface is the roof. With a coordinate grid, range circles (toggle; solid = node range, dashed = device range), links and status dots. Drag to pan, scroll or pinch to zoom, ⤢ fits everything.
- **3D** – see below.

A **Cloud Probe** has its own icon and draws a thin purple line to the machine it reads (also in 3D, where the machine gets a purple frame); its details say which machine and how far away. Like all coordinates, the line is only sent to players allowed to see positions.

With FTB Chunks and Waystones installed, the map also shows your team's **claimed chunks** (team colour, force-loaded ones dashed) and your **waystones** (violet diamonds, global ones gold) – toggle them with **Claims** and **Waystones**. Waystones are also marked in the 3D view. Like all coordinates, they are only sent to players allowed to see positions.

Click or tap any node for details, with sub-menus: **Info** (type, position, dimension, status, signal or cable, range), **Route** (every hop to the mainframe and what a relay carries), **Power** (usage and buffer) and **Card** (the device's live card, controls as your role allows). **Open card** jumps to it on the dashboard; **Unlink** removes it from the network (admins). Positions are only shown to browsers that may control the mainframe.

## 3D view {#3d}

The **3D** view shows your base with the real block models at their real coordinates: links as lines (cable solid, radio dashed, ender dotted), ranges as translucent spheres (toggle **Ranges**) and – if the server sends it – the coarse terrain around your blocks (toggle **Terrain**; only loaded chunks, black holes are unloaded areas).

- **Mouse:** drag to rotate, right-drag or Shift-drag to pan, wheel to zoom. **Touch:** one finger rotates, two fingers pinch and pan. **Keyboard:** focus the view, arrow keys pan, + and − zoom, Esc clears the selection. ⤢ fits everything again.
- Click a block for its details. Clicking a **relay** (or the mainframe) highlights everything routed through it and dims the rest.
- **Fly to…** finds a block by name and flies the camera there; the dimension tabs switch between Overworld, Nether and others.
- **Blocks:** the world around your network as voxels – the real terrain, caves and your base, coloured like a Minecraft map, with soft shadows in corners, glowing lights and see-through water. Only blocks that touch air are sent (no x-ray for ores), only within about 40 blocks of your devices and only from loaded chunks. **Cut** removes everything above a height (slider) so you can look into an underground base; **Night** dims the world and lets the lights glow.
- The blocks load nearest to the mainframe first, many at once, and this browser keeps them: the next time you open 3D the base is there at once and only changed blocks are fetched again. The line next to the switches says what is happening (loading, updating, building). Removing the mainframe from this browser also removes its stored blocks.
- Far away, blocks are drawn larger so the base stays readable; the coloured dot above each block is its status.
- 3D needs WebGL. Without it (switched off, very old graphics) the dashboard shows the 2D map instead. The 3D view is loaded only when you open it, so it does not slow down the rest of the dashboard.

From Lua, \`cloud_mainframe.getNetwork()\` returns the same nodes, links and routes.
`,
  upgrades: `# Upgrades, tablet and display

New in v0.7: mainframe upgrades for big bases, the **Cloud Tablet** for the dashboard in-game, and the **Cloud Display** for cards on a wall.

## Mainframe upgrades {#upgrades}

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
- Recipes use the pack's metals (\`#c:ingots/allthemodium\` and so on). Without AllTheModium installed there are fallback recipes with diamond, netherite and nether stars. Look them up in JEI/EMI.
- **Over the limit:** new links are refused with a clear message. If you remove a card, the extra devices show **Over capacity** and pause (the ones with the most hops or linked last first) until there is room again.
- The dashboard shows the capacity in use (e.g. “12.5 / 48 devices”) on the Network tab and in the power popover; devices on fiber count half.

## Cloud Tablet {#tablet}

A handheld item that opens your dashboard **in-game** – a native screen with the same cards and widgets, no browser needed.

- Works within network range of any node (mainframe or relay). The **Advanced Cloud Tablet** also works anywhere an Ender Relay reaches.
- Uses **your role** on that mainframe: viewers watch, operators control, admins edit.
- Optional key binding to open it (unbound by default; set it under Controls) while the tablet is in your inventory or a Curios slot.
- Crafted from a CC pocket computer and a Cloud Core (advanced: plus gold and an ender pearl).

## Cloud Display {#display}

A display block that shows one dashboard card or page **in the world**, like a CC monitor.

- Place several next to each other to form a larger screen (rectangles up to **8 × 6**).
- Right-click to choose the card or page in its screen. It is read-only by default; operators can allow **clickable buttons** – then right-clicking a button on the display presses it.
- Updates at most 4× per second and is not drawn further than 48 blocks away, so it stays cheap for the server and your FPS.

## Wireless redstone {#redstone}

Uplinks get a **redstone mode** in their screen (under **Advanced**): **transmitter** or **receiver** on a channel name. Receivers output the strongest signal of all transmitters on that channel (0–15). It works over relays, cables and – with Ender Relays – across dimensions, with the next tick as latency. Up to 64 channels per network; very fast clocks (faster than 2 ticks) are smoothed.

In the dashboard, the **Redstone** chip on the Network tab lists every channel with its level, transmitters and receivers. Players with the “Switch redstone” permission (operators and up) can **override** a channel – a level from 0 to 15 for 30 s, 5 min, 1 h or until reset – for example to open a door from your phone. The override wins over the transmitters until it ends or you clear it; every override is logged.
`,
  automation: `# Rules, scenes, notifications and history

Automation that runs on the **server** – no browser and no computer program needed – plus scenes, schedules, alerts on your phone, long-term charts and production statistics.

## Rules {#rules}

Open **Rules** in the dashboard settings (⚙). A rule reads like a sentence: **When** something happens, **if** conditions hold, **then** do something.

| Part | Choices |
|---|---|
| When | a widget value crosses a number, an item count in a storage card, mainframe power in %, a card goes offline, an [Advanced Cloud Sensor](/docs/redstone#advanced-sensor) detects someone or something, every N minutes, on days at a time, an in-game time of day ([schedules](/docs/automation#schedules)) |
| If (optional, all must hold) | widget value, item count, power, a card is online |
| Then (1–10, in order) | set a redstone side of a computer, use a control (button, toggle, slider, select, number, text), craft an item, send an alert, turn the dashboard on/off, run a [scene](/docs/automation#scenes) |

- Value triggers fire **once** when the check becomes true, not again while it stays true. **Cooldown** (at least 5 s) is the minimum time between two runs.
- **Run now** runs the actions once, ignoring trigger, conditions and cooldown – handy for testing.
- Safety: a rule may not change the widget its own trigger watches (that would loop), controls protected by the PIN cannot be used, and at most 20 rule runs per second per mainframe. Every run is logged (**Recent runs**).
- The number of rules depends on the mainframe's **Memory** upgrade (8 without, then 24 / 64 / 128).
- Permissions: “Edit rules” to create, change and delete; “Enable rules” to switch them on/off and use **Run now** (operators have it).
- Lua: \`cloud_mainframe.getRules()\` and \`setRuleEnabled(id, enabled)\`.

**Example:** When *Core temperature (Fission Reactor)* is above 900 – then set redstone back of *Reactor computer* to 15 and alert “Reactor hot” (warning).

**Without a computer:** a [Cloud Sensor](/docs/redstone#automation) next to a pressure plate – when *Door sensor* is above 0, then *Porch light* on and a push “Someone is at the door”.

**Who was it?** When *Reactor room* (an [Advanced Cloud Sensor](/docs/redstone#advanced-sensor)) detects a player – then alert “{who} entered the reactor room” (warning) and the alarm lamp on. \`{who}\` becomes the player's name (operators and push) or “a player” (viewers), or the mobs (“2 Zombies”).

## Scenes {#scenes}

A scene is **one button that runs several steps in order** – “Night mode”: lights off, wait 2 s, door closed, reactor slider to 3, a notice. Open **Scenes** in the dashboard settings (⚙).

- **Steps** (up to 16): the same actions as rules – set a redstone side, use a control, craft, send an alert, dashboard on/off – plus **Wait** (0.05–300 s, at most 600 s per scene). ↑ and ↓ change the order.
- Every scene is a button on the **Scenes** card: press it on the dashboard, on a Cloud Tablet, on a Cloud Display (only scenes without confirmation or PIN) or move it to any card with the layout editor. **Test run** in the Scenes screen runs the saved scene and ticks off every step as it runs; the last run keeps a result per step.
- Options: colour, icon, **ask before running** and **always ask for the PIN**. A step on a PIN-protected control makes the scene ask for the PIN anyway.
- **Rights:** creating and changing a scene needs “Edit rules” **and** every permission its steps need – nobody can hand out more than they may do themselves. Pressing it needs “Use controls”; each step is checked again **when it runs**, with the rights of the player who pressed it. A step without its permission fails, the others still run.
- **Schedule…** opens a new rule that runs the scene on days at a time (below).
- Limits: 32 scenes per mainframe (\`maxScenesPerMainframe\`), the same scene never twice at once, at most 4 runs at once and 30 per minute per mainframe. \`scenesEnabled = false\` in \`[scenes]\` switches scenes off.

## Schedules {#schedules}

Schedules are rules with a **time trigger** – pick it under **When** in the rule editor:

| Trigger | Runs |
|---|---|
| every … minutes | real time, every N minutes (at least \`minIntervalMinutes\`, default 1) |
| on days at a time | real time: on the picked weekdays at HH:MM (24 h) in the **server's time zone** – the editor shows it (config \`timezone\`, empty = the server's system zone) |
| an in-game time of day | when the world clock passes the time: **dawn**, **noon**, **dusk**, **midnight** or any in-game hour |

- With the action **Run a scene**, one schedule runs many steps. A scheduled scene runs with the rights of its **owner** (who saved it last), never with a PIN – so scenes that need the PIN cannot be scheduled.
- The rules list shows **next run in …** for real-time schedules.
- **Missed runs:** after a server restart at most **one** missed run is made up, and only if it is younger than \`missedWindowHours\` (24 h); \`runMissed = false\` never catches up. In-game time stands still while the server is down, so in-game triggers never catch up.
- **Unloaded mainframes:** a run that is due while the mainframe is unloaded or offline is **skipped** – the log shows “skipped – mainframe unloaded” once per outage, and nothing is made up later. Keep the mainframe's chunk loaded (Chunk Anchor upgrade, FTB Chunks) for reliable schedules.
- At most 16 time-based rules per mainframe (\`maxSchedulesPerMainframe\` in \`[schedules]\`).

## Push notifications {#push}

Open **Notifications** in the dashboard settings. Add up to 5 targets per mainframe:

- **Discord:** in the channel settings → Integrations → Webhooks → New Webhook → copy the URL.
- **ntfy:** a topic URL like \`https://ntfy.sh/my-base-alerts\` (pick a long, hard to guess name, or use a token); subscribe to the same topic in the ntfy app.
- **Webhook:** any https endpoint; it gets a JSON POST (event, mainframe, title, text, level, time).

Choose the minimum level (everything, warnings and errors, or errors only) and the events: **alerts** (scripts, cards, crafting, rules), **rules** (runs of rules with “push every run”), **approvals** (a browser waits for approval) and **power** (no power / restored). **Send test** checks a target; the result appears next to it.

The links and tokens are stored on the server only – the dashboard shows them masked. The server only sends to public https addresses (no local network, no redirects), at most 30 messages per minute per mainframe. Needs the “Push targets” permission (admins).

## Long-term history {#history}

Widgets whose script asks for history (\`history="24h"\` in the widget options, or \`"1h"\`, \`"7d"\`, \`"30d"\`) get a small **chart button**. It opens the history with a range switcher (1 h, 24 h, 7 days, 30 days), min / average / max and the value under your finger or mouse; gaps mean the mainframe or the chunk was not loaded.

\`\`\`lua
cloud.gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", history = "7d" })
\`\`\`

How much the server keeps depends on the mainframe's **Memory** upgrade: 1 h without, 24 h / 7 days / 30 days with the tiers. **Export CSV** downloads the values (time, value) for spreadsheets – it needs the “Use controls” permission.

## Production statistics {#production}

The **Production** page (a tab next to Network, or ⚙ → Production) shows what every AE2 / Refined Storage network behind your cards **gains, consumes and crafts** – per minute or per hour, over the last hour.

- The server samples each storage every minute (\`sampleSeconds\`) and keeps a window of 60 samples (\`windowSamples\`). The first numbers arrive after one minute; a server restart starts the window fresh.
- Per storage: gained, consumed, net and crafted, a chart, and the **top producers and consumers** with their icons.
- Click an item – “what is eating my iron?” – for its rates and chart, its crafting jobs, crafting running on the network now, the last 24 hours (watched and favourite items) and when the stock runs out at this rate.
- **What it cannot tell:** AE2 and Refined Storage only report how much the storage gained and lost, not which machine, pipe or player took the items. Items that come and go between two samples cancel out. **Crafted** counts finished jobs started through the bridge (dashboard, Lua, auto-stock, rules); jobs started at a terminal are only visible while they run.
- **Energy:** every energy card with stored energy and in/out in FE/t, the totals and a chart. Blocks whose mod reports no rates show the measured change (“measured”).
- **Pin as card** puts an item's rate, a storage's net rate or the energy totals as a chart on the **Production** card – arrange it with the layout editor; tablets and displays show it too. Up to 16 pins; needs “Edit the layout” (and “Search storage” for items and storages).
- Storage numbers need the “Search storage” permission. \`productionEnabled = false\` in \`[production]\` switches the statistics off.

## Terminal {#terminal}

The **>_** button on a computer's card opens its **terminal** in the browser: the live screen (colours on advanced computers) and your keyboard, like standing in front of it. Click the screen and type; **Terminate** is Ctrl+T, and **Reboot**, **Shut down** and **Turn on** control the computer. Paste sends the first line of the clipboard. On phones, type into the field below the screen and press **Enter**.

It needs the “Terminal” permission (admins) and the mainframe PIN if one is set; opening a terminal is logged. The server admin can switch it off (\`terminalEnabled\`) or forbid scripts and the terminal for a player (\`cccloud.feature.scripts\`).

## Lua editor {#editor}

In a computer's **</>** sheet, **Open the editor** shows all its files and folders (up to 8 levels) next to a code editor with Lua highlighting, line numbers, search (Ctrl/⌘ + F) and undo. **Save** (Ctrl/⌘ + S) writes the file; **Save & run** also starts it in a new tab on the computer. **+ File**, **+ Folder**, rename/move (✎) and delete (×) work on the tree.

If the file was changed on the computer since you opened it, saving asks before it overwrites. Files are limited to 256 KB, \`rom\` and disk drives are never touched, and the editor needs the same permissions as uploading scripts (plus the PIN if one is set).
`,
  mods: `# Supported mods

CC: Cloud shows machines from many mods as ready-made cards – no Lua needed. Put a **Cloud Uplink** on the machine (or next to it), link it with the Link Card, and its card appears on the dashboard. Everything is optional: a card only exists when that mod is installed. All of this was checked against **All the Mods 11** (MC 26.1.2).

Besides these, generic cards work with almost any mod: the **Energy** card for every block that stores FE, the **Tank** card for plain fluid tanks, and the **Peripheral card** for every other block CC: Tweaked can talk to – you pick up to 6 of its values and an optional on/off switch in the card's settings ([how](/docs/dashboard#widgets)). So if CC: Tweaked sees it as a peripheral, it shows up. Pack makers can turn single blocks off with the block tag \`#cccloud:no_generic_card\`. AE2 and Refined Storage have their own [bridges](/docs/bridges).

Controls follow the [permissions](/docs/accounts#roles): buttons and switches need “Use controls”, a machine's redstone mode needs “Switch redstone”, and cards with coordinates (waystones, gates, quests) need “See coordinates”.

## By category {#list}

${C.modsSection("en")}

This page is generated from the compatibility manifest (docs/COMPAT.md in the source), so it is always up to date with the mod.
`,
  recipes: `# All recipes

Every crafting recipe of CC: Cloud, generated from the mod's own recipe files – so this list is always complete. Some recipes depend on other mods: a note says **Needs …** (only with that mod) or **Only without …** (the fallback recipe for packs without it).

Modpacks can change recipes (datapacks, KubeJS, CraftTweaker). JEI/EMI and the in-game handbook always show the recipes of the world you play in; so do the docs in your server's dashboard, which mark what the server changed (**Changed by this server/modpack**) or removed (**Not craftable on this server**).

## Basics {#basics}

${C.recipeSection("basics", "en")}

## Relays and cables {#network}

${C.recipeSection("network", "en")}

## Bridges {#bridges}

${C.recipeSection("bridges", "en")}

## Tablet, display, analyzer {#devices}

${C.recipeSection("devices", "en")}

## Redstone devices {#redstone}

${C.recipeSection("redstone", "en")}

## Mainframe upgrades {#upgrades}

${C.recipeSection("upgrades", "en")}

## Other {#misc}

${C.recipeSection("misc", "en")}

## For pack makers {#pack-makers}

The recipes are made to be changed by modpacks:

- **Stable recipe ids.** Every recipe is \`cccloud:<name>\` ([list below](/docs/recipes#recipe-ids)) and keeps its id across versions. Override one with a datapack file \`data/cccloud/recipe/<name>.json\`, remove it with a file that only holds a \`neoforge:never\` condition (below), or use KubeJS / CraftTweaker.
- **Tags instead of items.** Ingredients use the common \`c:\` tags (\`#c:ingots/gold\`, \`#c:nuggets/gold\`, \`#c:ender_pearls\`, \`#c:dusts/redstone\`, \`#c:glass_panes\`, \`#c:gems/diamond\`, \`#c:storage_blocks/gold\`, \`#c:ingots/vibranium\` …), so unification mods such as AlmostUnified swap the material without touching these recipes.
- **Conditional AllTheModium recipes.** The mainframe upgrades, the Cloud Probe and the Advanced Cloud Sensor use AllTheModium metals when AllTheModium is installed (\`neoforge:mod_loaded\`); without it their \`*_fallback\` variant (gold, diamond, netherite, nether star) loads instead (\`neoforge:not\`). Likewise the Ender Fiber Link (\`ender_fiber_link_unobtainium\` with AllTheModium, \`ender_fiber_link\` without) and the Quantum Core (the ATM Star of All The Tweaks, \`quantum_core_fallback\` without it). When you change one of them, change both ids – the table below says which one loads when.
- **FTB Quests:** the CC: Cloud chapters use item tasks (have or craft the item), never recipe ids – they keep working whatever you change.
- **What players see:** JEI/EMI, the in-game handbook and the server's dashboard show the live recipes; the dashboard marks changed, added and removed ones. The website shows the defaults.

Remove a recipe with a datapack (\`data/cccloud/recipe/cloud_core.json\` in your pack):

${code("json", C.DATAPACK_REMOVE)}

### KubeJS {#kubejs}

${code("js", C.KUBEJS_RECIPES)}

### Recipe ids {#recipe-ids}

${C.recipeIdTable("en")}
`,
  // --------------------------------------------------------------- bridges
  bridges: `# Bridges: AE2 and Refined Storage

The **ME Cloud Bridge** (Applied Energistics 2) and **RS Cloud Bridge** (Refined Storage) connect a storage network to the dashboard. They only exist when the mod is installed.

## Set up {#setup}

1. Craft the bridge ([recipe](/docs/start#recipes)) and place it **on the network** like any other device (ME: uses one channel; RS: a network node).
2. Put a **Cloud Uplink directly on the bridge** and link the uplink with the Link Card.
3. The dashboard shows a ready-made **Storage** card – no computer, no code.

The bridge uses the network's own power, no FE for the mainframe.

## The storage card {#storage-card}

- gauges: storage used, energy
- labels: total items, item types, energy usage
- **search**: type a name or id, see counts, a "craftable" badge and the mod of each item
- **watchlist** and **crafting jobs** (below)

## Crafting from the dashboard {#crafting}

Tap a craftable item → pick **1 / 16 / 64 / custom** → **Craft**. The job appears under crafting jobs with a progress bar. Errors are explained: no pattern, all CPUs busy, missing ingredients, amount too large (limit \`maxCraftAmount\`, default 4096). Only devices with control rights can craft.

## Watchlist {#watchlist}

Add items with a minimum amount (from a search result or by id). Items below their minimum are listed first in red and trigger a **warn** alert once per crossing. Edit the minimum or remove items any time. The watchlist is stored in the bridge.

## Browse, auto-stock and crafting CPUs {#storage-v2}

- **Browse** (next to the search): every item as a grid with icons – sorted by amount, name or mod, with a **Craftable** tab, **Fluids** and ★ **favourites** pinned on top. Scroll for more.
- **Item details:** amount, a 24-hour chart, recent jobs, ★, **Craft…** and **Keep in stock…**.
- **Craft preview:** before a craft starts you see what is taken from storage, what gets crafted and what is missing, plus free CPUs. Tick **Notify me** to get a notice when it is done or fails.
- **Keep in stock (auto-stock):** set a minimum and a target – when the amount drops below the minimum and there is a pattern, the server crafts up to the target in batches of 64 (one job per item, every 30 s at most). The badge shows the state: crafting, waiting for a CPU, missing resources or paused after 3 failures. While every crafting CPU is busy, the item has one "Waiting for CPU" row in the crafting jobs – it is tried again every 30 s and never counts as a failure. It runs on the server – no computer needed. From Lua: \`bridge.setAutoStock(name, min, target, enabled)\`.
- **CPUs & jobs:** every crafting CPU with its current job, all running jobs, and **Cancel** (operators).
- **Production:** what the network gains and uses per minute, and what eats an item – see [production statistics](/docs/automation#production).

## From Lua {#lua}

${code("lua", C.LUA_BRIDGE)}

Your own storage card with the same widgets:

${code("lua", C.LUA_BRIDGE_WIDGETS)}

Or just run \`cloud-storage\`. All methods: [Lua reference → Bridges](/docs/lua#bridges).
`,

  accounts: `# Accounts, roles and sharing

A **device** is any browser – on a PC, a tablet or a phone. Every page of the dashboard works the same on all of them, with mouse, keyboard or touch.

## Link your player {#link}

1. In Minecraft, type \`/cccloud link\` (or use "Link this browser" in the mainframe screen). You get a one-time code (10 minutes).
2. Open the dashboard, enter the code and a name for this browser, click **Pair device**.
3. Click **[Allow]** in the chat.

The browser is now linked to **your player**, not to a single mainframe: it shows every mainframe where you have a role, and you switch between them with the mainframe name in the header. Browsers paired with a mainframe code before keep working for that one mainframe.

## Roles {#roles}

| Role | May |
|---|---|
| viewer | watch values and alerts – no controls, no coordinates |
| operator | viewer + coordinates, buttons, toggles, sliders, redstone, crafting, enabling rules |
| admin | everything: scripts, terminal, layout editing, rules, devices, access, share links, PIN |
| owner | the player who placed the mainframe: admin + transfer and delete |

Roles are **presets**. Each player or team rank can get single permissions added or removed on top (**Custom** on the Access page) – for example an operator who may also edit the layout, or an admin without script uploads. Removing wins over adding. Without “View the dashboard” a player has no access at all. You can only give permissions you have yourself (the owner can give all of them). A server can switch scripts and the terminal off for everyone with the permission node \`cccloud.feature.scripts\`.

Roles come from several sources – the **highest one wins**: the owner, roles given to a player (\`/cccloud role <player> <role>\`, the mainframe screen or the **Access** page), team ranks and server permissions. Things you may not do are hidden or disabled, with the reason shown.

## Teams {#teams}

A mainframe can be assigned to a team from **FTB Teams**, **Open Parties and Claims** or a vanilla scoreboard team. Each team rank maps to a role on that mainframe (defaults: owner/officer/leader → admin, member → operator, ally → viewer). When someone joins or leaves the team, their access changes right away.

## The Access page {#access}

Admins open it from the mainframe menu in the header (**Access & roles**):

- the list of players with their role and where it comes from
- change or remove roles given directly, add a player by name
- assign a team and choose the role for each team rank
- **Custom** next to a role: add or remove single permissions, grouped (watching, controls, storage, scripts & terminal, layout & automation, management), each with a short explanation; the button shows how many differ from the preset (e.g. “−2”)

Who may open it is a permission too (“Manage access”); share links need “Share links”.

## Devices {#devices}

**Devices** in the header menu lists every browser linked to your player – name, type (computer, tablet, phone), last address and last use. Revoke a lost or old one there or in-game with \`/cccloud devices\` and \`/cccloud revoke\`.

## Share links {#shares}

Admins can share a **read-only** view – for a stream overlay, a friend or a wall display:

1. Mainframe menu → **Share links** → **New link**.
2. Pick whole pages or single cards, the expiry (1 hour, 24 hours, 7 days or never), how many people may watch at once, and optionally **hide exact numbers** (only gauges are shown).
3. Copy the link – it is shown only once.

The secret part after \`#\` never reaches the server's logs and is removed from the address bar as soon as the page opens. The browser keeps it only in memory – after a reload or from a bookmark, open the full link again. People with the link see **"Shared by …"** and cannot press anything: there are no controls at all, and the server refuses every action from a share link. Never included: coordinates, item search, the network map and history export. Revoking a link disconnects everyone viewing it immediately.

## Server permissions {#permissions}

Server admins control CC: Cloud with **LuckPerms** or **FTB Ranks** (or the op level when no permission mod is installed):

| Node | Default | Allows |
|---|---|---|
| \`cccloud.use\` | everyone | placing and using the blocks, linking browsers |
| \`cccloud.admin.view\` | op | viewing any mainframe |
| \`cccloud.admin.operate\` | op | operating any mainframe |
| \`cccloud.admin.manage\` | op | managing devices and roles of any mainframe |
| \`cccloud.limit.mainframes\` | 3 | mainframes per player |

When an admin looks at someone else's mainframe, the owner sees it in the log.

## Keyboard {#keyboard}

Tab moves through everything, Enter or Space activates, Escape closes dialogs and menus, the arrow keys move between page tabs and menu entries, and "Skip to content" jumps past the header.
`,
  // -------------------------------------------------------------- security
  api: `# Public API

The public API lets **your own tools** read values and press buttons: a Discord bot, Home Assistant, a spreadsheet script. It is a small HTTP JSON API on the dashboard's address, with **API keys** you create in the dashboard.

> **Note:** The API is **off by default**. A server admin switches it on; until then the routes do not exist.

## Turn it on {#enable}

In the world's \`serverconfig/cccloud-server.toml\` (for a dedicated server: \`world/serverconfig/\`), section \`[api]\`. Saving the file switches it on, no restart needed ([live config](/docs/security#live-config)):

${code("toml", C.TOML_API)}

Players need the **API keys** permission on the mainframe (admins and owners have it; see [roles](/docs/accounts#roles)). Server owners can forbid keys for a player with the permission node \`cccloud.api.keys\` – their keys then stop working too.

## Create a key {#keys}

Dashboard → ⚙ → **API keys** → **New key**:

1. A **name** (one key per tool, so you can revoke it on its own).
2. The **scopes** – what the key may do (below).
3. An **expiry**: 1 hour, 24 hours, 7, 30 or 90 days, or never.
4. Optional: the **IP addresses** it may be used from (up to 8, e.g. \`192.168.1.0/24\`).

The key (\`cck_…\`) is shown **exactly once** – copy it then. The server only keeps a fingerprint (hash). The list shows every key with its scopes, created, expiry, last use and IP; **Revoke** stops a key at once. Players with “Manage access” see and can revoke every key of the mainframe. A key also ends when the browser you created it with is revoked (lost phone: revoking it ends its keys too).

## Scopes {#scopes}

A request needs the scope **and** the key owner's current permission – a key never does more than its player may.

| Scope | Allows | Needs |
|---|---|---|
| \`read:sections\` | read cards, widgets and values; the live event stream | View |
| \`read:storage\` | search storage cards, item counts | Search storage |
| \`read:stats\` | production statistics | View (storages: Search storage) |
| \`action:widgets\` | press buttons, set toggles, sliders, selects, numbers and texts | Use controls (redstone widgets: Switch redstone) |
| \`run:scenes\` | list and run scenes | Use controls and every step's permission |

## Routes {#routes}

All JSON, \`Authorization: Bearer cck_…\` on every request. \`<id>\` is the key's mainframe (shown in the dashboard under ⚙ → This mainframe).

| Route | Scope |
|---|---|
| \`GET /api/v1/me\` | any – the key, its player and mainframe |
| \`GET /api/v1/mainframes/<id>/sections\` | read:sections |
| \`GET /api/v1/mainframes/<id>/sections/<sectionId>\` | read:sections |
| \`POST /api/v1/mainframes/<id>/sections/<sectionId>/widgets/<widgetId>\` with \`{"value": …}\` | action:widgets |
| \`GET /api/v1/mainframes/<id>/storage/<sectionId>/items?q=iron&limit=20\` | read:storage |
| \`GET /api/v1/mainframes/<id>/production\` | read:stats |
| \`GET /api/v1/mainframes/<id>/scenes\`, \`POST …/scenes/<sceneId>/run\` | run:scenes |
| \`GET /api/v1/mainframes/<id>/events\` (Server-Sent Events) | read:sections |
| \`GET /api/v1/openapi.json\` | public – the OpenAPI description |

## Limits {#limits}

- 60 requests per minute per key and 120 per IP address (\`apiRequestsPerMinute\`, \`apiIpRequestsPerMinute\`); more answers \`429 rate_limited\` with a \`Retry-After\` header. Item searches: 5 per second per key.
- Request bodies up to 2 KB (\`apiMaxBody\`), up to 5 keys per player (\`apiKeysPerPlayer\`).
- Event streams: 2 per key (\`apiStreamsPerKey\`), 32 in total; a comment line every 20 s keeps them open, slow readers are disconnected.

## Security {#security}

- **No PIN in the API, on purpose:** PIN-protected controls and scenes answer \`403 pin_required\` – a leaked key can never reach the second factor. Admin pages, access, devices, pairing, key management, the terminal, scripts, layout, rules, push and the map are never reachable with a key.
- Keys are shown **once** and stored only as a hash. Keep them in a secrets file, never in a URL – a key in the query string (\`?key=\`) is refused with \`400 key_in_query\`.
- Pick **few scopes**, an **expiry** and an **IP allow-list** where you can; **revoke** a key you no longer need or that may have leaked.
- The API follows the dashboard's rules: the same host and LAN-only settings, IP bans and connection limits. Use HTTPS when it is reachable from the internet ([internet access](/docs/security#internet)). Every widget action and scene run is written to the mainframe's audit log (“API key 'bot' (Steve) …”).
- Browser tools on other sites need their exact origin in \`apiCorsOrigins\`; otherwise browsers cannot call the API (servers and bots are not affected).

## Examples with curl {#curl}

${code("bash", C.API_CURL)}

Errors are JSON like \`{"error":"pin_required"}\`: \`401 unauthorized\` / \`expired\` (key), \`403 no_access\` (the player lost access), \`ip_not_allowed\`, \`pin_required\`, \`404 not_found\`, \`429 rate_limited\`.

## Home Assistant {#home-assistant}

A **REST sensor** reads a widget, a **rest_command** runs a scene (use it in automations or on a dashboard button). The key needs \`read:sections\` and \`run:scenes\`.

${code("yaml", C.API_HA)}

## A small Discord bot {#discord}

\`!temp\` answers with the reactor temperature, \`!night\` runs the “Night mode” scene. It needs Node 18 or newer, a Discord bot with the **Message Content** intent (Discord developer portal → Bot), and a key with \`read:sections\` and \`run:scenes\`. List your section and scene ids once with curl (\`/sections\`, \`/scenes\`).

${code("js", C.API_DISCORD)}
`,

  security: `# Security and hosting

The dashboard can control things in your world, so it is protected like a real web service.

## Default: LAN only {#lan-only}

Out of the box only devices in the server's local network (and the host itself) can open the dashboard. Everyone else gets "LAN only".

${code("toml", C.TOML_LAN)}

The config file is \`<world>/serverconfig/cccloud-server.toml\`; changes apply without a restart (see [below](/docs/security#live-config)).

## Approvals and devices {#devices}

- A pairing code is valid for 10 minutes and works once.
- After the code, the player who opened it must click **[Allow]** in-game (within 2 minutes).
- Every device belongs to that player. If the player loses access (untrusted, de-opped, banned), the device stops working immediately.
- Devices unused for 30 days expire.
- List and revoke devices in the mainframe screen or with commands.

## Owner, trusted players, ops {#trust}

The player who placed the mainframe owns it. Owner, trusted players and ops (level 2+) can see the pairing code, approve devices, link uplinks and change settings. Others see a read-only status.

## Commands {#commands}

Look at the mainframe and run:

${code("", C.COMMANDS)}

## PIN {#pin}

Critical widgets (\`pin=true\`) need a 4–8 digit PIN. 5 wrong tries lock that device for 5 minutes and notify the owner.

## Internet access {#internet}

> **Warning:** Plain HTTP over the internet sends your access token and PIN unencrypted. Use HTTPS (Caddy, Cloudflare Tunnel or TLS in the mod).

Set \`webAllowInternet = true\` and pick one of the setups below. The dashboard shows a warning when it runs over plain HTTP from a public address.

### Caddy reverse proxy (recommended) {#caddy}

${code("toml", C.TOML_CADDY)}

${code("", C.CADDYFILE)}

Caddy gets a certificate automatically and forwards WebSockets.

### Cloudflare Tunnel (no open port) {#cloudflare}

Same \`[web]\` settings as for Caddy, then:

${code("bash", C.CLOUDFLARED)}

${code("yaml", C.CLOUDFLARED_YML)}

### TLS in the mod {#tls}

${code("toml", C.TOML_TLS)}

Convert the Let's Encrypt key first: \`${C.PKCS8}\`. If the files cannot be loaded the dashboard does not start – it never falls back to plain HTTP silently.

### Port opened directly {#open-port}

Only for quick tests: set \`webAllowInternet = true\`, \`publicUrl = "http://mc.example.com:8787"\` and open TCP 8787 in your host's panel.

## Item icons on a dedicated server {#item-icons}

The dashboard shows real item icons. The best ones come from the game itself: every player with CC: Cloud renders icons in the background (a few minutes for a big pack, without costing FPS), and the server keeps them in \`<world>/cccloud/icons\`.

So that a fresh server has icons before anyone joins, it also draws the simple ones itself right after start, on one low-priority background thread:

- **Drawn by the server:** flat items (ingots, tools, most modded items) and plain blocks (stone, ores, logs, storage blocks, machines with a cube model).
- **Left for the players:** chests, shields, heads and other special models, slabs, stairs, fences and other shapes, 3D machine models. They show a letter tile until a player with the mod joins.

Players' renders replace the server's later; they look exactly like the inventory. In All the Mods 11 the server draws about 27,000 of 38,500 items on its own (29,000 with vanilla textures), with about 10 seconds of CPU time on one core, never on the server's main thread, so ticks are not affected.

**Vanilla items** need Minecraft's textures, and a dedicated server has none (the server jar ships no textures). Both ways to get them are your choice:

${code("toml", C.TOML_ICONS)}

- \`downloadVanillaAssets = true\` downloads the official Minecraft client jar of exactly your server's version from Mojang's servers, once (about 40 MB, checked against Mojang's SHA-1, kept in \`<server>/cccloud/cache\`). It is only read locally for textures; nothing from it is passed on – browsers only get the 32×32 icons. By turning it on you agree to Mojang's terms (the [Minecraft EULA](https://www.minecraft.net/eula)).
- \`vanillaAssetsJar\` points to a client jar you already have, e.g. \`.minecraft/versions/26.1.2/26.1.2.jar\`.

Without either, vanilla items get their icons when the first player with the mod joins; the server log says so once at start. The settings are in the \`[icons]\` section of \`<world>/serverconfig/cccloud-server.toml\` and apply at the next start. In singleplayer and on LAN the host's game renders every icon, so the server renderer stays off there.

\`/cccloud icons status\` shows how many icons come from players, how many from the server and how many are missing, and how far the server renderer is. \`/cccloud icons rebuild\` draws everything again, \`/cccloud icons clear\` deletes all icons.

## Big servers {#big-servers}

CC: Cloud is built for big bases: hundreds of machines on one mainframe, many mainframes and many browsers. Most of it needs no settings; this is what a server owner can do.

- **Pages:** the dashboard only draws the cards near the screen, but a page with 500 cards is still a long scroll. Split big bases into pages (layout editor, or \`opts.page\` in Lua); up to about 100 cards per page is comfortable. Layouts of up to 2048 cards can be saved.
- **Compression:** keep \`webCompression = true\` (the default). Browsers get the snapshot and the updates compressed (5–10× smaller); it costs a little CPU on the web server's own threads, never on the server thread. Reverse proxies (Caddy, nginx, Cloudflare) pass it through.
- **Browsers:** each browser of a mainframe gets all its updates. A 500-device mainframe where everything changes all the time sends about 130 KB/s per browser (about 60 KB/s compressed). The Bandwidth upgrade (or \`baseBrowsers\`) limits browsers per mainframe; share links count separately.
- **Long-term statistics:** each mainframe keeps at most 2 MB of history; \`historyMaxTotalMB\` (default 128) caps all mainframes together. Every series has a fixed size from the start (1 h ≈ 240 B, 24 h ≈ 5.6 KB, 7 days ≈ 14 KB, 30 days ≈ 20 KB).
- **Map tiles on disk:** the 2D map caches its tiles in \`<world>/cccloud/maptiles\`. Tiles nobody drew or looked at for 30 days are deleted (\`mapTileMaxAgeDays\`, 0 keeps them), and purging a mainframe deletes its cave slices.
- **In-game screens:** the mainframe screen lists the first 600 devices (problems first) and gets the network only when it changed; the dashboard's Network page shows every device. Big network maps leave out the links a device does not use.
- **Slow browsers:** a browser that falls more than 4 MB or 10 seconds behind is disconnected and reconnects with a fresh snapshot; nothing piles up on the server.
- **Advanced Cloud Sensors:** each looks at its zone twice a second with one bounded entity query, spread over the ticks; \`advancedSensorScansPerTick\` (default 32) caps the scans per tick on the whole server, and a disarmed sensor without redstone output does not scan at all.
- **Measuring:** \`/neoforge tps\` or spark show the server's tick time. CC: Cloud's own cost for a 500-device mainframe with 10 browsers is about 0.5–1 ms per tick (developers: \`./gradlew runGameTestServer -PscaleTest\` measures it).

## Changing settings without a restart {#live-config}

Every setting in \`cccloud-server.toml\` and \`cccloud-integrations-server.toml\` applies while the server runs. Edit the file and save it: NeoForge notices the change (within a few seconds) and CC: Cloud applies it. Or run, as an op (permission \`cccloud.command.reload\`):

${code("", "/cccloud config reload     # read both files again, apply, list what changed\n/cccloud config show       # all values (secrets only as \"set\")\n/cccloud config show webPort")}

\`config reload\` prints which values changed, what was applied live and what would need a restart (the last line of each setting's comment in the file says which; at the moment every setting applies live). It also picks up renewed TLS certificate files at the same paths and retries a dashboard that could not start. The same report goes to the server log.

What happens to the dashboard:

- **\`[web]\` (port, address, TLS, proxies, internet access, allowed hosts):** the web server restarts on its own, without the game. A new port or address is opened **before** the old one closes; if it cannot be opened (port in use, no permission), nothing changes, the old one keeps running and the log and \`config reload\` say why. Unreadable TLS files keep the old settings too – a reload never falls back to plain HTTP. Open dashboards reconnect by themselves; after a port, address or TLS change open the new address (the mainframe screen and \`/cccloud status\` show it).
- **Internet access:** switching \`webAllowInternet\` on behaves exactly like a server start with it (same warnings in the log, e.g. for plain HTTP); switching it off closes every connection at once, and only LAN browsers get back in.
- **Feature switches** (API, push, rules, scenes, terminal, voxels, production statistics, Script Store): apply to the next action; open dashboards update their menus.
- **Limits** (API rate limits, Cloud Switch changes per second, upgrade base limits, history memory, buffers): apply at once. A lowered limit counts what was already used; a raised one gives nothing back. Browsers already open over a lowered browser limit stay connected (like after removing a Bandwidth upgrade).
- **Ranges and power:** every network is recomputed within a second.

A file with a syntax error is replaced by NeoForge with the defaults (it keeps a backup next to it) – the defaults are LAN only, so nothing opens up by accident.

## What the server logs {#logs}

Every pairing, approval, revoke and dashboard action appears as \`[cccloud] …\` in the server log.
`,

  // ------------------------------------------------------------------- faq
  faq: `# Troubleshooting and FAQ

## "No such program" {#no-such-program}

You typed Lua code into the **shell**. The shell only runs programs. Put the code into a file with \`edit startup.lua\` and run \`startup\`, or type \`lua\` first. See [CC basics](/docs/cc-basics#shell-vs-lua).

## "No widgets yet" / empty card {#no-widgets}

- The uplink must be **on a computer** (or a bridge) and **linked** with the Link Card.
- A computer without its own program shows the automatic Computer card. If your program cleared all widgets (\`cloud.clear()\`), the card comes back after the program ends.
- Your program must actually run: put it in \`startup.lua\` so it starts after a reboot or chunk load.

## The bridge does not show up {#bridge}

- The uplink must sit **directly on the bridge**, not on a cable next to it.
- The bridge must be part of a powered network (ME: it needs a channel – check your controller and cable channel count; RS: the controller must have energy).
- Link the **uplink**, not the bridge, with the Link Card.

## The dashboard is not reachable {#not-reachable}

- Use the URL from the mainframe screen. \`localhost\` only works on the PC that runs Minecraft.
- The browser's device and the Minecraft server must be in the same network (e.g. the same Wi-Fi, not mobile data) – or see [internet access](/docs/security#internet).
- Allow Java through the PC firewall for private networks.
- Port 8787 must be free; if not, the server log says so and an admin can change \`webPort\`.
- On a hosted server the port must be opened, or use a reverse proxy / tunnel.

## "Mainframe has no power" {#no-power}

The FE buffer is empty. Connect a generator or FE cable to the mainframe; it reconnects by itself once the buffer refills. See [power](/docs/start#power).

## "Access revoked" {#revoked}

The device was removed (in the mainframe screen or with \`/cccloud revoke\`), it expired after 30 days, or the player who approved it lost access. Pair again with a new code.

## "LAN only" {#lan-only}

The server only allows local addresses. An admin can enable \`webAllowInternet\` – preferably behind HTTPS. See [security](/docs/security#internet).

## Controls are greyed out {#locked}

The dashboard is disabled, the mainframe is offline or without power, the uplink is offline, or your device is read-only. The banner at the top says which.

## My devices go offline when I walk away {#unloaded}

Minecraft only runs the chunks near players. When you walk away, the chunk of a computer, uplink or bridge unloads – it stops, and CC: Cloud can't talk to it. The dashboard keeps its card **greyed out with the last values** and shows "last seen 12 min ago – chunk not loaded"; its controls are disabled until the chunk loads again. CC: Cloud never loads chunks by itself.

To keep a base running while you are away, keep its chunks loaded: **force-load** them with FTB Chunks (claim, then force-load in the map), use a chunk loader from another mod, or have the server's settings keep spawn chunks loaded. Then the card comes back to life on its own.

## Items show a letter instead of an icon {#letter-icons}

The icon is not there yet. On a dedicated server the server draws the simple icons at start; chests, slabs, stairs, 3D machines – and vanilla items unless you allow the vanilla textures – wait for the first player with CC: Cloud, whose game renders them in the background. See [item icons on a dedicated server](/docs/security#item-icons).

## Wrong PIN / locked {#pin-locked}

After 5 wrong PINs the device is locked for 5 minutes. The owner can change the PIN with \`/cccloud pin set\`.
`,
};
