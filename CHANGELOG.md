# Changelog

All notable changes to CC: Cloud. Versions before 0.9.0 were development milestones and were not published.

## Unreleased

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
