# Changelog

All notable changes to CC: Cloud. Versions before 0.9.0 were development milestones and were not published.

## Unreleased

### Internet access in one setting
- **`/cccloud internet http`** opens the dashboard to the internet with one command (server owner, op level 4): the chat
  shows the link to open, the port to forward in the router and how to test it from a phone on mobile data. Or set
  `internetAccess = "http"` (optionally `internetDomain`) in the server config. `/cccloud internet` shows the state,
  `/cccloud internet off` closes it again. It applies at once (live config). The old `[web]` keys keep working as
  advanced settings.
- **Plain HTTP just works** (easy mode): no PIN needed; PIN-protected controls keep their PIN. It can be read on the
  network path, so: new browsers still need [Allow] in game (requests from the internet are marked), browsers unused
  for 24 hours are logged out when they come from the internet (`publicTokenIdleHours`), stricter limits and a lockout
  after wrong codes for internet addresses, share links stay read-only, and API keys can't control anything over plain
  HTTP from the internet.
- **Stricter, optional:** `internetRequirePin = true` – every control action from the internet asks for the mainframe
  PIN (once, then 10 minutes per browser); mainframes without a PIN are view-only from outside, and their owners are
  told when they join.
- A warning line in the server log, a short line for ops when they join and a small dashboard banner (dismissible per
  browser session).

### Every CC peripheral shows up: the Peripheral card
- A block without a card of its own still gets one if CC: Tweaked can talk to it: put an uplink or a Cloud Probe on it
  and the **Peripheral card** appears. In the layout editor, click the card and pick up to 6 of its values (methods that
  take no arguments, like `getEnergy`, `isRunning` or a storage's `size`), up to 2 of them also as a chart, and – if the
  block has one – a `set…(true/false)` method as an on/off switch.
- Safe by design: only the methods you picked are ever called, every 2 seconds and less often if they are slow; methods
  that need arguments are never offered; texts are cut to 64 characters; the switch needs the "Use controls" right, a
  confirm step and, unless you turn it off, the PIN. Choosing values needs the right to edit the layout.
- New server settings `peripheralCard` (on) and `peripheralPollSeconds` (2).

### More mods from All the Mods 11
- **Just Dire Things:** generators (output, energy, burn or fuel tank), every machine with a redstone mode (breakers,
  placers, clickers, droppers, swappers, fluid machines, item collector, experience holder, paradox machine, energy
  transmitter) and sensors. Generators and machines can switch their redstone mode from the dashboard.
- **Productive Trees:** Sawmill, Pollen Sifter and Stripper (progress, input, output, by-products, axe).
- **Ranged Pumps:** what the pump is doing, its tank, energy and how far it has scanned.
- **Charging Gadgets:** the Charging Station's energy, fuel and the item it charges, with that item's charge.
- **wd's Selling Bin:** what is waiting to be sold and what it is worth, the bin's credit, auto-sell on/off and a
  "Sell now" button.
- **Cooking for Blockheads:** fridges, cabinets and counters as searchable storage cards, the oven (what is cooking, fuel,
  output) and the toaster with its switch.
- **Farming for Blockheads:** the Shipping Bin (next shipment, goods, emeralds paid out).
- **Training Dummy:** a DPS meter – the dummies near your mainframe with damage per second, last hit, streak total, who
  hit them and a DPS chart.
- **FTB Essentials:** a Warps card with the server's warps, their dimension and coordinates (read-only; homes stay
  private). New server setting `warpsCard`.
- Checked and left out, with the reasons in the compatibility list: Mystical Agradditions (no machines), Item Collectors
  (nothing to count, no switch), the Elevator mod (no cabin to call – it would mean teleporting players from the
  dashboard), Refined Flow Analytics (no public API yet), AE2 Network Analyser, LaserIO, More Red, Iron Jetpacks and
  Mining Gadgets; Power Armor's machines use the Energy card.

### Network map: modded dimensions
- The dimension tabs show the real names and an icon for the Aether, the Undergarden, All the Modium's Mining Dimension,
  The Other and The Beyond, Stellaris' Moon and Earth Orbit, Witchery's dream worlds and more; unknown dimensions get a
  name and an icon from their id. The tabs scroll on phones.
- The Y sliders of the 2D slice and the 3D cut cover each dimension's own height (the Aether starts at −32, The Beyond
  at −128).
- Dimensions with a roof – the Nether, the Undergarden – open the 2D map on a slice at your network's height instead of
  showing the bedrock roof.
### New: Advanced Cloud Sensor
A room nobody should enter? Place an **Advanced Cloud Sensor** – you get a push notification as soon as someone comes
in, and who it was: a player's name or the kind of mob.
- Watches a zone in front of it or around it (3×3×3 by default, up to 9×5×9; new configs `advancedSensorMaxZoneX/Y/Z`),
  outlined in the world while you hold the sensor or a Link Card or have its screen open. The zone must lie in your own
  claim or unclaimed land (FTB Chunks; not in spawn protection).
- Reports players (by name), hostile and passive mobs (with their type, e.g. "Zombie", "Villager"), items and other
  things that move – filtered by: everyone / players / mobs / hostile mobs, "ignore me and my team" (on by default), an
  allow and a deny list of player names. Spectators and vanished players are never reported.
- Its own dashboard card: who is inside, the last 20 events with relative time, today's count, arm/disarm and the
  filters; an "occupied" light on its group's Redstone card. Powers redstone like a lever while something matching is
  inside – also disarmed and without a mainframe.
- Alerts to the dashboard and to push targets (Discord, ntfy, webhook) without spam: the first at once, then one summary
  per cooldown ("3 Zombies entered Reactor room"), and a cooldown per entity (new configs `advancedSensorAlertCooldown`,
  `advancedSensorEntityCooldown`).
- Rules: new trigger "an Advanced Sensor detects …" (anyone, a player – optionally by name –, a mob, hostile, passive, an
  item, something else); `{who}` in an alert text names who it was ("{who} betritt den Reaktorraum").
- Privacy: only operators and up (`view.positions`) see player names; viewers, share links, Cloud Displays and API keys
  without that right see "a player". `advancedSensorPlayerNames = false` turns names off for the whole server.
- Recipe: Cloud Sensor, two observers, an eye of ender, a Cloud Core and AllTheModium ingots (without AllTheModium: gold
  and diamonds). Counts 0.5 devices (`advancedSensorCapacityWeight`) and costs 4 FE/t while armed
  (`mainframePerAdvancedSensorFE`). In JEI, Jade, the handbook and the docs (EN/DE).
### Settings without a restart
- Every setting of `cccloud-server.toml` and `cccloud-integrations-server.toml` now applies while the server runs: save
  the file, or run `/cccloud config reload`, which re-reads both files and lists what changed, what was applied live and
  what would need a restart (nothing does today). `/cccloud config show [key]` shows the values with their defaults;
  secrets such as the TLS key path are only shown as "set". Both need `cccloud.command.reload` (op level 4, console).
- The last comment line of every setting in the file says `(applies live)` or `(restart required)`; the file starts with
  a short note on live reload. NeoForge rewrites existing files once to the new comments (it keeps a backup).
- `[web]` changes restart only the dashboard's web server: a new port or address is opened before the old one closes; a
  port in use or unreadable TLS files keep the old server running with all its old settings (log and command say why,
  the next reload retries). Open dashboards reconnect by themselves. `/cccloud config reload` also loads renewed TLS
  certificates at the same paths and starts a dashboard that could not start before.
- Switching `webAllowInternet` on behaves exactly like a start with it (same log lines and warnings); switching it off
  disconnects every browser at once.
- Values that were only read at start now follow the file: API rate limits of keys and IPs already in use (a lowered
  limit holds at once, nothing is refilled), the API and event-stream switches (open streams end at once), the upgrade
  base limits (`baseDevices`, `baseBrowsers`, `baseRules`, `baseHistoryHours`), Cloud Switch changes per second,
  `mainframeMaxInputFE` and the relays' input rate, `trackedItemsPerStorage`, `mapTileMaxAgeDays` (sweeps at once), and
  the `[cards]` switches of the integrations (cards are detected again, so `mffsRequirePin` reaches shown cards).
- Open dashboards update their menus when a feature switch changes (API, push, rules, scenes, terminal, voxels,
  production statistics, Script Store).
- Fixes found on the way: after a `scriptStoreKey` change the store index verified with the old key was still trusted
  for up to 5 minutes; push retries used the allow-list of the first attempt; cave slices (`mapSlices = auto`) now count
  the dashboard as internet-facing while the running server allows the internet, even if a reload could not apply yet.
- **FTB Quests: the quests come with the mod.** A beta tester added CC: Cloud to his server and saw no quests: the
  chapters were separate files to install by hand. Now every release has two jars and a quest zip:
  - `cccloud-<version>.jar` – **playing on your own server or in singleplayer?** Take this one. With FTB Quests it adds
    the two chapters (CC: Tweaked, 24 quests; CC: Cloud, 39 quests; English and German) to the quest book on the first
    start, before FTB Quests reads it: backup first, never replaces other chapters or texts, ATM11's Tech group or a new
    "CC: Cloud" group, nothing at all if one of its ids is already taken, and its CC: Tweaked chapter only when the pack
    has none. Only once (`config/cccloud/quests-installed.json`): a chapter you delete stays deleted, and newer versions
    only update chapters and texts nobody changed. One log line says what happened; `installQuests = false` in
    `cccloud-server.toml` turns it off. Players get the quests from the server, as with any FTB Quests book.
  - `cccloud-<version>-modpack.jar` – **making a modpack?** The same mod (same id and version), but it never touches the
    quest book (`installQuests` is off and the quest files are not inside). Ship the chapters from
    `cccloud-quests-atm11-<version>.zip` (chapter and lang files, `install.sh`, `README.txt` EN/DE) in your quest book.
  - The release notes start with "Which download?". README, Getting started (new "Install" section with "Quests"),
    Troubleshooting and the in-game handbook (EN/DE) explain it, with a note for pack makers.
- The CC: Tweaked chapter writes its turtle-upgrade components as FTB Quests writes them back, so an FTB Quests re-save
  no longer counts as a change.

### Fix: item icons were rendered again after every server start
- On big packs only about 4,000 icons per player and hour were kept: the upload limit dropped everything over it
  silently, while the mainframe screen counted those icons as done – after a restart they were missing and rendered
  again ("Item icons: 9.472 / 36.754" climbing on a hosted server). The limit now fits one full render of the pack per
  player and hour, players at the limit get no further batches, and nothing over it is lost (it waits in the queue).
  The same fix for block textures of the 3D view.
- The icon store is written to disk within about a second of every upload (a small journal file, renamed into place),
  not only every minute and at a clean stop, so a hosting panel that kills the server loses at most the last second.
  Only items whose mod version really changed are rendered again.

### Item icons right after server start
- A dedicated server now draws the simple item icons itself at start, so the dashboard has icons before anyone joins:
  flat items (ingots, tools, most modded items, tinted like in the inventory) and plain cube blocks (stone, ores, logs,
  machines with a cube model) from the textures in the mod jars, on one low-priority background thread. In All the Mods
  11 that is about 27,000 of 38,500 items. Chests, slabs, stairs, 3D models and other shapes still come from the first
  player with CC: Cloud, whose game renders every icon in the background as before and replaces the server's (which are
  marked `source: "server"` in the icon manifest; client renders are never replaced by server renders). New config
  `renderIconsOnServer` (default true). Singleplayer and LAN are unchanged: the host's game renders everything.
- Vanilla items on a dedicated server: new opt-in config `downloadVanillaAssets` (default false) downloads the official
  Minecraft client jar of exactly the server's version from Mojang once (HTTPS to Mojang's hosts only, SHA-1 checked,
  cached in `<server>/cccloud/cache`) and reads only its item textures and models locally; nothing from it is passed on.
  Turning it on means agreeing to Mojang's terms. `vanillaAssetsJar` (a client jar you own) keeps working instead.
  Without either, the server logs one hint at start and vanilla items wait for the first player.
- `/cccloud icons status` shows icons by source (game clients / server / missing), the server renderer's progress and
  where vanilla textures come from; `rebuild` also redraws the server's icons.
- The `[icons]` settings apply while the server runs like all others: after a change the server opens the vanilla
  textures again and draws the icons that are still missing.
- The texture fallback found mod assets only for namespaces with an English language file; it now indexes every mod
  jar's `assets/` folder.

## 1.0.0-beta.1 – 2026-10-30

The first public beta. CC: Cloud needs **CC: Tweaked 1.120.0+** next to it (Minecraft 26.1.2, NeoForge 26.1.2.109+,
Java 25) – for 26.1.2 it is on [Modrinth](https://modrinth.com/mod/cc-tweaked) only. Drop both jars into `mods/` on the
client and the server, place a Cloud Mainframe and follow the pairing code
– or read [Getting started](https://cccloud.dev/en/docs/start/). Found a bug? Report it in the
[Discord](https://discord.gg/4fnQcvbzg6) #help forum or as an [issue](https://github.com/enricofranke/cc-cloud/issues/new/choose).

### Fixes and follow-ups
- Cloud Redstone devices placed by `/setblock`, a structure or a schematic paste with their link in the block data now
  show on the dashboard at once; before, they only appeared after their first state change.
- Cloud Probes are their own kind of device on the network map: own icon and label, and a thin purple line to the
  machine they read on the 2D map, in the 3D view (with a frame on the machine) and on the mini map of the mainframe
  screen; the detail panel says which machine and how far away. Before, a probe looked like an advanced uplink. For Lua:
  `cloud_mainframe.getNetwork()` now reports probes as `type = "probe"` (was `"advanced_uplink"`) with `target`.
- Map tiles on disk no longer grow forever: purging a mainframe deletes its cave slices, and tiles that were neither drawn
  nor shown for 30 days are deleted (new config `mapTileMaxAgeDays`, 0 keeps them).
- Production statistics: a huge stock change (thousands of item types at once, e.g. emptying a drive) no longer creates
  a short-lived statistics entry for every changed item; only the items that will be tracked are created.
- CC: Tweaked wired networks: an uplink on a computer now also shows cards for the machines the computer reaches through
  its wired modems and networking cable – the same cards an uplink on each machine would show, with the same controls and
  permissions (up to 8 per uplink, new config `wiredMachineCards`). Lua bridge widgets (`itemSearch`, `watchlist`, `jobs`)
  accept a bridge on the computer's cable network when the uplink sits directly on the computer.
- Railcraft Reborn: new cards for iron and steel tanks, coke ovens and blast furnaces. Like the steam boiler, an uplink
  or a Cloud Probe on any block of the formed multiblock shows the whole structure (fill and fluid; cooking progress,
  creosote and output; smelting progress, fuel, output and slag).
- Dashboard: buttons take the colour picked for them in the layout editor, also the buttons of Cloud Buttons (which
  were always cyan); purple and orange work as button colours too, readable in the dark and the light theme.
- Discord bot deployment: a NetworkPolicy (nothing in; out only DNS and HTTPS to public addresses) and how to pin the
  image by digest (`discord-bot/README.md`); the image workflow shows the digest in its summary.
- Dashboard: the storage browser and the "Watch an item" sheet keep their height while you type a filter; their top edge no longer jumps up and down as the results change.

### Security review before the release
- API keys end with the browser that created them: revoking a browser (in game, `/cccloud devices revoke`, the Account
  page) also revokes the API keys made with it, so a lost phone cannot keep control through a key. The key list no longer
  shows other players' IP addresses to moderators.
- Cloud Probe: the target is checked on every read and action, not only when linking – a lower `probeRange` applies to
  probes linked before, a chunk another team claims stops the reading, and a probe moved with its data (contraptions,
  block movers) forgets its target. Linking checks claims and spawn protection again; a picked machine must be handed to
  a probe within 10 minutes.
- Creative players without op can no longer place CC: Cloud blocks with copied block data (made-up targets, links or ids).
- Map slices: a chunk claimed by another team is also removed from tiles that were cached before the claim.
- Dashboard: a saved layout with overlapping cards can no longer make the dashboard hang (big layouts are placed in one
  pass); the 3D view's cached blocks are deleted as soon as the role loses "See coordinates".
- Smaller limits: at most 256 map tiles wait for the server, screen actions are limited per player, and before login a
  compressed WebSocket message may not inflate beyond 2 KB.

### Live recipes in the dashboard docs – right grids on modpack servers
- The dashboard's recipes now come from the server: packs that change CC: Cloud recipes with datapacks, KubeJS or
  CraftTweaker (All the Mods, …) no longer get the mod's default grids in `/docs`. A changed recipe carries the badge
  "Changed by this server/modpack" (the default one click away), a removed one "Not craftable on this server", a
  recipe the pack added for one of our items "Added by this server/modpack"; conditional variants that do not load on
  this server (AllTheModium vs. `*_fallback`) shrink to one line. Without a server (offline) the defaults show as before.
- New read-only endpoint `GET /api/recipes`: public like the docs and item icons (game data only; LAN-only rule,
  60 requests per minute per IP), only our items' recipes, built once per recipe reload (`/reload`, KubeJS) and cached.
- The website's recipes page says that it shows the defaults. The recipes page ends with "For pack makers": every
  recipe id, the `c:` tags (AlmostUnified), the AllTheModium / `*_fallback` variants, removing a recipe with a
  datapack, a KubeJS example, and that the FTB Quests chapters use items, not recipes.

### Big bases and big servers
- Hundreds of machines per mainframe, many mainframes and many browsers: on the scale benchmark (500 + 10 × 100
  devices, 100 rules, 50 browsers) CC: Cloud's server-thread time under load went from 6.4 to 1.6 ms per tick on average
  (p95 18 → 4 ms); a single mainframe with 500 devices and 10 browsers costs about 0.5–1 ms per tick. Network
  recomputes, redstone cards and history sampling no longer cause 30+ ms ticks.
- Dashboard: one message per update batch instead of one per value (5 800 → 12 messages per second for a big base),
  compressed WebSocket (`webCompression`, the 1.1 MB snapshot of a 500-device base is 112 KB on the wire), only the
  changed cards render again, cards far off screen render when they come near, big pages lay out in milliseconds instead
  of a minute: a 500-device mainframe shows its cards after 0.3 s (the old dashboard never finished rendering it).
- Fixed for big bases: the mainframe screen of a big network stayed empty (its view was over the packet limit), the
  Cloud Tablet never got a big base's snapshot (over the payload limit), browsers were disconnected as "too slow" right
  after a big snapshot, layouts with more than 256 cards could not be saved, only 256 cards of an unloaded base survived
  a restart.
- When many rules run in the same second, the server log gets one line for all of them.
- New config: `webCompression` (on), `historyMaxTotalMB` (128 MB of long-term statistics for all mainframes together).
- For developers: `./gradlew runGameTestServer -PscaleTest` (docs/SPEC.md "Scaling"), `tools/e2e/scale-render.mjs`.

### Cloud Redstone devices – a lamp on your phone without a computer
- Four small blocks that are linked with the Link Card like an uplink – no computer, no Lua, no channels:
  **Cloud Switch** (on any face like a lever, strong into its block and weak around; on/off or analog 0–15),
  **Cloud Button** (a pulse of 2–40 ticks), **Cloud Lamp** (light 0–15 set from the dashboard, optionally also lit by
  redstone) and **Cloud Sensor** (reads 0–15, also right after a world load). Right-click switches, sneak + right-click
  opens a small screen (name, group, mode, pulse, PIN/confirm, connection).
- Devices with the same group share one card (e.g. "Hall" with six lamp switches and All on / All off); devices without
  a group go to the mainframe's automatic "Redstone" card. The cards are normal cards: layout editor, Cloud Tablet,
  Cloud Display, share links, rules (e.g. "when the door sensor is on → push"), scenes, the public API and history
  (lamp light, sensor level). Rules and scenes follow a device to its new card when its group changes.
- Controls need "Switch redstone"; PIN and confirmation per device; at most 4 dashboard changes per second and device.
  A device counts 0.25 of the mainframe's capacity and costs 1 FE/t (range 32 blocks, radio only; all in the config).
  Shown on the network map (rename/regroup there), in the 3D view, the Cloud Analyzer and Jade.
- Cheap recipes from a lever / stone button / comparator / redstone lamp and prismarine crystals; new dashboard icons
  lever, button, lamp and sensor. Docs page "Redstone devices" (EN/DE), FTB Quests quests.

### Cloud Probe – read machines built into walls
- **Cloud Probe** (new block): reads exactly one machine up to 8 blocks away (server config `probeRange`, max 16) as
  if an uplink were attached to it – for machines built into walls or with every face taken by pipes and cables.
  Link Card: sneak + right-click the machine, then right-click the probe; claims and spawn protection are respected
  (the click is a normal interaction), only the picking player can use it, the uplink screen shows what it reads and
  can clear it, the Cloud Analyzer draws a line to the machine. Expensive on purpose: Advanced Cloud Uplink, Cloud Core,
  an eye of ender and 6 vibranium ingots (without Allthemodium: diamonds and netherite scrap); counts as 2 devices and costs
  24 FE/t (config). Normal uplinks stay adjacent-only.
- The uplink screen says when a machine exposes nothing on the touched face but does on another ("attach to another
  side (e.g. top) or use a Cloud Probe"). Cards already read side-configured machines through their internal view.
- Uplinks on a Productive Metalworks foundry part (tank, drain, capacitor) or a Productive Farming hatch show the
  controller's card, like Powah reactor parts, Advanced AE and Railcraft boilers already did.

### Visual polish: compact uplinks, quieter JourneyMap
- **Compact uplink**: on faces that cannot carry the 14x14 plate – slab sides, tray-height machines such as Energized
  Power solar panels, carpet-height blocks – the uplink becomes a small module on the visible part of the face instead
  of a plate sticking out like a sign. Chosen automatically from the attached block's shape, follows it when that block
  changes, and fixes itself in existing worlds after loading. The hitbox matches. Top and bottom faces only take the
  plate: placing an uplink on top of a slab or solar panel is refused with a hint ("Attach the uplink to the side of
  this block"); uplinks that already sit there keep working.
- **JourneyMap**: by default only mainframes and Ender Relays become waypoints (client config
  `journeyMapWaypointTypes` adds relays, uplinks, bridges), all in one waypoint group "CC: Cloud" that JourneyMap can
  hide at once; only mainframes get the in-world marker and beam; names are the card title (computer label) or the
  block name instead of "CC: Cloud – Mainframe @ x y z: uplink".

### v0.10 – Scenes, schedules, production statistics, public API
- **Scenes**: one named button that runs several actions in order (widgets on any card, redstone, crafting, alerts,
  the dashboard switch) with delays counted in server ticks, optional confirmation and PIN. Every step is checked
  against the rights of whoever pressed it, so a scene never does more than its presser could by hand; saving needs
  every permission the steps need. Scenes show up as a Scenes card (move the buttons anywhere in the layout editor) and
  work on the Cloud Tablet and the Cloud Display; the Scenes page edits them with the rule pickers and test-runs them
  with a result per step.
- **Schedules** as rule triggers: weekdays + time (server time zone), every N minutes, and in-game time with presets
  (dawn, noon, dusk, midnight). After a restart at most one missed run is caught up; schedules of unloaded mainframes
  are skipped and logged once. A rule can run a scene (with the scene owner's rights, never a PIN scene). Limits per
  mainframe and a minimum interval in the server config.
- **Production statistics** for AE2 and Refined Storage: items gained, consumed and crafted per minute and per hour,
  top producers and consumers, an item view ("what is eating my iron"), the energy flow of all energy cards, charts,
  and pinned rates as a Production card. Sampled once a minute from the bridges' item index with bounded memory; only
  who may search a storage sees its numbers.
- **Public API v1** (off by default): per-player API keys bound to one mainframe with scopes, optional expiry and IP
  allow-list, `/api/v1` REST routes (sections, widget values, storage search, production, scenes), a read-only
  Server-Sent Events stream, an OpenAPI description, rate limits, audit log; keys never get more than their owner's
  live rights and never reach PIN-protected controls. New permission `api.keys` and server node `cccloud.api.keys`.
- Docs (EN/DE): scenes, schedules, production statistics and a Public API page with curl, Home Assistant and a
  Discord bot example; handbook regenerated.

### Mainframe removal is no longer final
- A broken Cloud Mainframe drops its item with its identity (tooltip: id and owner). Placing it again – anywhere – brings
  back the same mainframe with its devices, accounts, PIN, layout, rules, scenes and API keys, still owned by its
  owner; uplinks and bridges reconnect. Upgrade cards still drop separately.
- Nothing is deleted at once: the data is kept for `mainframeRetentionDays` (default 7), the dashboard shows the
  mainframe as "Removed" with the deletion date. `/cccloud mainframe recover` gives owners their item back after a
  creative break (server admins always); `/cccloud mainframe purge <id>` deletes it earlier.
- Misclick guard (`mainframeBreakProtection`, default on): in creative the mainframe only breaks while sneaking; in
  survival only its owner, its admins and server admins can break it.

### 3D blocks and map tiles load in seconds
- The 3D view's blocks loaded one 16³ section per request, and every request also counted against the 120 API requests
  per minute per IP: the showcase base (536 sections) showed "Loading blocks 10 %" for minutes. The dashboard now
  asks for up to 64 sections per request (a compact binary batch, gzip), nearest to the mainframe first; the limit
  counts sections (2048 per device, refilled at 256 per second), so a base loads without waiting. Measured on the
  showcase world: the whole base in 2.2 s instead of 3.5 minutes (9 requests instead of 952), first blocks after 0.4 s,
  11 KB instead of 121 KB, and the server thread spends 15 ms (4–5 ms with warm caches) instead of 127 ms on it.
- This browser keeps the blocks (IndexedDB): reopening 3D shows the base at once (0.3–0.5 s) and the server only
  confirms unchanged sections. Removing the mainframe from the browser removes them too.
- Meshing runs in a small pool of workers with a 4× faster mesher, each region is meshed about once instead of five
  times, region meshes are merged (draw calls 183 → 108, triangles −15 %), and the loading line says what is
  happening (loading, updating, waiting for the server, building).
- Server: the server thread only copies the block-state containers of the asked sections (microseconds each); hashing,
  building and encoding run in the background, and the cache is checked against the blocks themselves, so changes
  that fire no neighbor update (block movers, `/setblock` variants) show too. Map tiles: the server thread copies the
  chunks' block states, sampling and PNG encoding run in the background, cached tiles are read from disk in the
  background (server thread per tile 2.3 → 1.0 ms; all 20 showcase tiles in 0.6 s, and they no longer wait for the
  API limit after the 3D view).
- `/cccloud map stats [reset]` (server admins) shows requests, cache hits, server-thread time per tick and bytes for
  the map tiles and the voxels. `tools/showcase/loadbench.mjs` measures the loading on the showcase world.

### Fixes
- Script Store: `door-controller` 1.0.1 no longer passes `icon` to `cloud.group()` (groups take `page` and `cols`; the
  card shows its first widget's icon), which the mod refused at start. A unit test now runs every store script on CC's
  Lua VM against the mod's real widget validation, with one dashboard action per control.
- Wireless redstone: a transmitter samples its input on its first tick after loading, so a channel driven by a lever
  that is already on is correct after a world load (it stayed at 0 until something changed next to the transmitter).
- Auto-stock no longer fills the crafting jobs with "failed (no_cpu)" rows while every crafting CPU is busy: the request
  waits as one "Waiting for CPU" row per item, is planned again after the 30 s cooldown and never counts as a failure.
  While AE2 has no idle CPU nothing is planned at all. Same logic for the ME and RS bridges and in the dashboard mock.
- Dashboard: Chrome sometimes got an empty response on the first load. A tab alone opens 7–8 connections, and the
  9th connection from an IP was closed without an answer. The limits are now 24 connections per IP and 256 in total,
  and a connection slightly over a limit gets `503` with `Retry-After` instead of an empty response (docs/SECURITY.md).
- Jade: the tooltip plugin failed to load with Jade 26.1.12 ("Data providers cannot implement IComponentProvider since
  Minecraft 1.21.6"). The network lines now come from a separate server data provider and client tooltip provider; a
  unit test checks that rule on the compiled classes. `-PintegrationMods=jade` puts Jade into the dev runs.
- Cloud Displays no longer reset to 1×1 ("was malformed") when a world loads: the block saved its position in the wall
  as `x`/`y`, which the chunk save overwrites with the block's coordinates. The index is now saved as
  `index_x`/`index_y`; walls saved by 0.9.0 join up again by themselves. The showcase builder writes the new keys.

## 0.9.0 – first public release candidate

Minecraft 26.1.2, NeoForge 26.1.2.109+, CC: Tweaked 1.120.0+. This release bundles everything built in the
milestones below.

### v0.1 – Mainframe, uplinks, dashboard
- **Cloud Mainframe** (two blocks tall) and **Cloud Uplinks** that attach to CC: Tweaked computers as the
  `cloud_uplink` peripheral; Lua scripts declare widgets (buttons, toggles, sliders, gauges, labels, charts, …).
- A **web server inside the mod** (HTTP + WebSocket, same origin) serves a mobile-first dashboard (Preact PWA).
- Devices pair with a code and an in-game approval; tokens are hashed, PINs protect critical widgets; LAN only by
  default, strict validation and rate limits everywhere.

### v0.2 – Bridges and custom dashboards
- **ME Cloud Bridge** (AE2) and **RS Cloud Bridge** (Refined Storage) as optional dependencies: item search,
  crafting, watchlists, job lists.
- Lua API v2: pages, groups, more widget types; `cloud.ui` helper library, `cloud-demo` and `cloud-storage` in the
  CC ROM; dashboard edit mode with a shared per-mainframe layout.

### v0.3 – Less setup
- Automatic **Computer card** for uplinks on computers (power, redstone per side) – no Lua needed.
- In-game screens for mainframe and uplink, a setup guide in the dashboard, bilingual docs (English/German).
- **Item icons** rendered by game clients and stored per mod version; mainframe **power (FE)**.

### v0.4 – Network
- Ranges, **Cloud Relays** (normal, advanced, ender), routing and power scaling by distance and load.
- **Network map** in the dashboard (topology and 2D map) and a power breakdown.

### v0.5 – Integrations, rules, push, terminal
- **Ready-made device cards** for machines from many mods (energy, Powah, Ender IO, Energy Meter, Hostile Neural
  Networks, Productive Bees, MFFS, ME Requester, …).
- Mainframe cards: **Server** (TPS/MSPT, players, memory), FTB Chunks claims, Simple Backups; FTB Teams trust.
- **Rules** (When → If → Then) with loop protection and run limits; **push notifications** to Discord, ntfy or
  webhooks with SSRF protection.
- **Script import**, a **browser terminal** and a **Lua editor** for computers behind an uplink.
- **Long-term statistics** for numeric widgets and storage items, with CSV export.
- **Storage card v2**: auto-stock (level maintainer), craft preview, crafting CPUs with cancel, fluids.

### v0.6 – Accounts and permissions
- One login per player for all mainframes, device management, **roles** (viewer, operator, admin, owner) with
  **fine-grained permissions** (allow/deny per grant and team rank), NeoForge permission nodes, team providers
  (scoreboard teams, FTB Teams).
- Read-only **share links** with expiry, viewer limit and hidden numbers.

### v0.7 – Upgrades and in-game devices
- **Mainframe upgrades** (range, capacity, efficiency, memory, Quantum Core), an optional **Chunk Anchor** card,
  **wireless redstone** channels.
- **Cables**: CC: Tweaked wired networks and our own Fiber Cable, Fiber Junction and Ender Fiber Link.
- **Cloud Tablet** and **Cloud Display** (multiblock) show the dashboard in game; the **Cloud Analyzer** shows the
  network in the world.
- Dashboard: 3D network view, map tiles (surface and cave slices), voxel view of the base (exposed blocks only,
  no X-ray), WYSIWYG layout editor (layout schema v2) and cached sections of unloaded chunks.

### v0.8 – All the Mods 11, wave 2 and 3
- Storage cards for Functional Storage, Sophisticated Storage, Occultism and DimStorage; Advanced AE quantum
  computers; QuarryPlus; Apothic Spawners, Gateways to Eternity, Easy Villagers; Iron Furnaces, Cobblegen Galore,
  Productive Metalworks/Farming, Agritech, Railcraft; Waystones and FTB Quests cards; Super Factory Manager,
  Modular Routers, Integrated Dynamics; generic Tank card (opt-out with `#cccloud:no_generic_card`), Witchery,
  Forbidden & Arcanus, Pylons, Ars Magica Legacy; JourneyMap waypoints.
- In-game **handbook** (GuideMe) generated from the dashboard docs; **KubeJS** bindings.

### Also in 0.9.0
- **Script Store** support on the server: config `scriptStore` / `scriptStoreUrl`, public `GET /api/config`,
  installed store scripts remembered per computer.
- Voxel view stage 2: **block textures** uploaded by game clients and served as an atlas.
- Release metadata, GitHub Actions build and release workflow.
