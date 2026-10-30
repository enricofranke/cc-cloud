# Changelog

All notable changes to CC: Cloud. Versions before 0.9.0 were development milestones and were not published.

## Unreleased

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
