---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Supported mods"
  position: 10
---

# Supported mods

CC: Cloud shows machines from many mods as ready-made cards – no Lua needed. Put a **Cloud Uplink** on the machine (or next to it), link it with the Link Card, and its card appears on the dashboard. Everything is optional: a card only exists when that mod is installed. All of this was checked against **All the Mods 11** (MC 26.1.2).

Besides these, two generic cards work with almost any mod: the **Energy** card for every block that stores FE, and the **Tank** card for plain fluid tanks. Pack makers can turn single blocks off with the block tag `#cccloud:no_generic_card`. AE2 and Refined Storage have their own [bridges](bridges.md).

Controls follow the [permissions](accounts.md): buttons and switches need “Use controls”, a machine's redstone mode needs “Switch redstone”, and cards with coordinates (waystones, gates, quests) need “See coordinates”.

## By category

### Energy, server and teams

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Powah (`powah`) | 7.0.4-alpha | Energy cells, generators and the reactor: stored FE, in/out, reactor temperature and fuel | reactor on/off where exposed | yes |
| Ender IO (`enderio`) | 9.0.6-alpha | Capacitor banks and machines: stored FE, in/out, progress | redstone mode | yes |
| Oritech (`oritech`) | 2.0.0-exp11 | Energy storage and generators: stored FE, generation | – | yes |
| Energized Power (`energizedpower`) | 3.0.1 | Machines and batteries: stored FE, progress | redstone mode | yes |
| Energy Meter (`energymeter`) | 0.5.0 | Flow per tick, total transferred, a 60-minute chart | transfer mode | yes |
| Generator Galore (`generatorgalore`) | 1.7.0 | Generators: stored FE, generation, fuel | redstone mode | yes |
| Hostile Neural Networks (`hostilenetworks`) | 7.0.0 | Simulation chamber: running, data model, accuracy, output | – | yes |
| Productive Bees (`productivebees`) | 15.3.0 | Hives and centrifuges: honey level, bees inside, output | – | yes |
| MFFS (`mffs`) | 5.8.39 | Force field projector: fortron, field blocks, status | field on/off (PIN) | yes |
| ME Requester (`merequester`) | 1.5.1 | Kept-in-stock requests: amount, status | request on/off, amount | yes |
| spark (`spark`) | 1.10.187 | Server card: TPS, MSPT, CPU | – | yes |
| FTB Teams / FTB Chunks (`ftbteams`) | 26.1.2 | Team trust for roles, team claims on the network map | – | yes |
| Simple Backups (`simplebackups`) | 26.1.6 | Backups card: last backup, size, running | backup now | yes |

### Storage controllers

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Functional Storage (`functionalstorage`) | 1.6.4 | Storage Controller, Framed Storage Controller, Controller Access Points: Storage card | – | yes (bare controller; drawers are linked with the Linking Tool only) |
| Sophisticated Storage (`sophisticatedstorage`) | 1.5.121 | Controller: Storage card | – | yes (controller + chest) |
| Occultism (`occultism`) | 1.256.1 | Dimensional Storage Actuator (all four variants): Storage card | – | yes (wave 3: actuator alone, 100 iron ingots; runtime Modonomicon, Curios, GeckoLib) |
| DimStorage (`dimstorage`) | 10.0.1 | Dimensional Chest / Dimensional Tank: Storage / Tank (fill %, fluid, mB) card | – | yes (both) |

### Applied Energistics add-ons

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Advanced AE (`advanced_ae`) | 26.1.7 | **Quantum Computer** card on any block of the multiblock: status (online + name / not formed / no power), free storage, co-processor threads, jobs running, up to 6 job rows ("64× Iron Ingot – 42 %, 1 min 3 s"); info alert when all jobs are done. Quantum CPU jobs also show in the ME Cloud Bridge's job list, because Advanced AE adds its CPUs to AE2's `getCpus()`. | "Cancel all jobs" (`act.buttons`, confirm) | partly: a single quantum unit block ("Not formed", cancel → `unavailable`); a formed quantum computer needs the full multiblock on a powered grid |

### Quarries

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| QuarryPlus, published as "Additional Enchanted Miner" (`quarryplus`) | 26.12.160 | **Quarry** / **Chunk Destroyer** cards: status (the machine's state from its public client-sync tag: building the frame, mining, removing fluids, finished, …), working indicator, energy %, area size and bottom Y (no coordinates), dig depth % (quarry: from the drill head), buffered items; warn alert once when 10 000+ mined items pile up in the internal buffer (nothing pulls them out), info alert when finished | none: QuarryPlus has no redstone or API pause | yes (quarry: no area → placed with its default area → working) |

### Spawners and mob farms

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Apothic Spawners (`apothic_spawners`) | 2.0.1 | **Spawner** card on (upgraded) spawners: mob, spawning now (player in range or "ignore players"; with Redstone Control only while powered), "needs redstone" indicator, next spawn %, delay range, mobs per spawn, max nearby, player range, the boolean upgrades | none: Redstone Control is an upgrade item applied in-game; a dashboard switch would bypass the progression | yes (pig spawner, default stats) |
| Gateways to Eternity (`gateways`) | 6.0.2 | **Gateways** mainframe card (restricted, `view.positions`): gates open within 256 blocks of the mainframe – name, wave n / total, enemies alive, lives, distance; alerts when a gate opens, completes or closes. Gates are entities, so this is a mainframe card. Config `world.gatewaysCard`. | none | yes (Emerald Grove gate opened and removed) |
| Easy Villagers (`easy_villagers`) | 1.1.47 | **Villager Breeder** (villagers, breeding / needs food), **Iron Farm** (villager, golem cycle %), **Villager Farmer** (villager, crop) with output slots used, output full indicator and a warn alert once when the output is full | none | yes (empty iron farm) |

### Processing machines

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Iron Furnaces (`ironfurnaces`) | 4.4.2 | **Furnace** card on every tier: status (smelting X / idle / no fuel / output full), smelting %, fuel left, input, output, energy; warn alert once when the output slot is full | furnace redstone mode (Ignore / Run without signal / Run with signal), select with confirm, `act.redstone` | yes (smelts raw iron, mode switch) |
| Cobblegen Galore (`cobblegengalore`) | 0.2.9 | **Block generator** card: what it makes, tier speed (×modifier per tick-rate), buffer n / 64; warn alert once when the buffer is full (nothing pulls the blocks out). The exact items/min depend on the recipe's private speed and are not shown. | none | yes (stone generator between water and lava) |
| Productive Metalworks (`productivemetalworks`) | 1.17.1 | **Foundry** card on the controller: formed + coil type, fuel, power (energy coil), items melting, molten metals (up to 4) | none | yes (lone controller, "Not formed") |
| Agritech: Evolved (`agritechevolved`) | 2.2.0.5 | **Planter** / **Advanced Planter** cards: plant, soil, growth %, energy (advanced), output full indicator + warn alert | none | yes (empty basic planter) |
| Productive Farming (`productivefarming`) | 0.13.0 | **Farm** card on the farm controller: formed, water, output slots used, output full + warn alert | none | yes (lone controller). Note: Productive Farming's own GameTest `agritech_compat_recipes_present` fails in a dev run without Agritech Two; ours passes. |
| Mystical Agriculture (`mysticalagriculture`) | 9.0.10 | **Machine** card for Essence Furnace, Seed Reprocessor, Soul Extractor, Harvester, Ore Infuser, Soulium Spawner: status, progress, energy, fuel left, upgrade tier | none | untested (retried in wave 3): any dev server with Mystical Agriculture 9.0.10 + Cucumber 9.0.6 (also with Mystical Agradditions) crashes at start. CC: Tweaked rebuilds the creative tabs on server start (`CommonHooks.onServerStarted` → `CreativeModeTabs.tryRebuildTabContents`), and MA's own tab then adds "Inferium Essence" twice (`ModCreativeModeTabs` via Cucumber's `FeatureFlagDisplayItemGenerator`). ATM11 runs the same code with its crop/tag configuration (`config/mysticalcustomization`, `cucumber-tags.json`), so it is not our bug, but a minimal test world cannot be started; the card stays compile-checked against the exact jar. |
| Railcraft Reborn (`railcraft`) | 1.4.5 | **Steam Boiler** card on any boiler block (firebox or tank): status + °C, temperature %, water %, steam %, burning; crit alert once when the water runs low while the boiler is hot (refilling a dry hot boiler makes it explode) | none | yes (lone firebox, "Not formed") |

### Logistics

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Super Factory Manager (`sfm`) | 4.34.0 | **Factory Manager** card: state (running / no disk / no program / program has errors), program name, average tick time, errors, warnings, first error (SFM's translation key on dedicated servers), queued redstone pulses; warn alert when the program becomes invalid | none: SFM has no on/off – taking the disk out is its off switch | yes (manager without a disk) |
| Modular Routers (`modularrouters`) | 26.1.2.2 | **Modular Router** card: status, modules, speed (tick rate, items, eco mode), buffer | redstone mode (Always / Redstone low / Redstone high / Never / Redstone pulse) – "Never" switches the router off; select with confirm, `act.redstone` | yes (empty router, switched to Never) |

### Integrated Dynamics

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Integrated Dynamics (`integrateddynamics`) | 1.36.1 | **Display Panels** card: an uplink next to a logic cable that carries Display/Light Panels shows each panel's current value with its type (one label per side; the card rebuilds when panels are added/removed). ID evaluates the variables on its own network; the card only reads the panel's display value. Variable Stores are not read (their variables are evaluated only by readers/panels). | none | untested: placing parts on a cable needs ID's part placement and a network; compiled against the exact jar and loaded in a dev server |

### World & progression

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| Waystones (`waystones`) | 26.1.2.16 | **Waystones** mainframe card (restricted, `view.positions`): waystones owned by the mainframe owner or a member of the owner's team (team providers, e.g. FTB Teams), plus global waystones – name, dimension, coordinates (8 rows) and the network-map overlay in the snapshot: `"waystones":{"items":[{"name","dim","x","y","z","kind","global"?}],"truncated"}` (max 512, re-read every 10 s). Config `world.waystonesCard`. The dashboard does not draw the overlay yet. | none | yes (own waystone listed, a stranger's private one hidden) |
| FTB Quests (`ftbquests`) | 26.1.2.9 | **Quests** mainframe card (restricted, `view.positions`): the owner's FTB team – overall %, chapters done / total, quests done / total, the 5 most recent completions with chapter and age; info alert on a new completion. Config `world.questsCard`. | none | partly: card, restricted flag and quest file access; a mock player has no FTB team, so progress values are not covered |
| JourneyMap (`journeymap`) | 6.0.10 (API 2.0.0) | client plugin (`client/compat/journeymap`, found by JourneyMap's `@JourneyMapPlugin`): the mainframes, relays and devices of every network where the player has `view.positions` as temporary (non-persistent) waypoints named "CC: Cloud – \<mainframe>: \<device>", coloured by type. The client subscribes with the `map_nodes_subscribe` payload while the client config `journeyMapWaypoints` (default true) is on; the server (`map/MapNodesService`) re-checks the permission every 5 s, sends `map_nodes` only when it changed, at most 512 nodes. | client config toggle | server part yes (GameTest `map_nodes_filtering`: owner gets his network, a stranger nothing, no payload without subscription; unit test `MapNodesTest`); the JourneyMap side needs a client and is untested |

### In-game handbook

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| GuideME (`guideme`) | 26.1.15-beta | **CC: Cloud Handbook**: a data-driven GuideMe guide (`assets/cccloud/guideme_guides/handbook.json`, pages in `assets/cccloud/guides/cccloud/handbook/`, German in `_de_de/`), generated from the dashboard docs by `tools/gen_handbook.ts` (11 pages per language, recipes as `<RecipeFor>` with a fallback text). Item: GuideMe's guide item with `guideme:guide_id = cccloud:handbook`, crafted shapeless from a book + Cloud Core (recipe only when GuideMe is loaded). No code dependency. | – | yes (`guideme_handbook` parses every page with GuideMe's parser; screenshots of the EN and DE guide in a dev client). The ME/RS bridge recipe boxes show GuideMe's "missing item" note when AE2 / RS are absent. |

### KubeJS bindings

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| KubeJS (`kubejs`) | 26.1.2-8.0.6 (Rhino 2101.2.8-build.91) | Server scripts get **`CCCloudEvents.action`** (an accepted dashboard action: mainframeId, sectionId, section, widget, value, playerName, player, device), **`CCCloudEvents.deviceLinked`** (uplink/bridge bound with a Link Card: mainframeId, deviceId, type, dimension, pos, player) and **`CCCloudEvents.alert`** (mainframeId, sectionId, text, level), and the binding **`CCCloud`**: `mainframes()`, `mainframesOf(player)`, `has(player, mainframeId, "act.buttons")`, `section(mainframeId, key).title(..).gauge/toggle/button/slider/select/buttons/number/text/label/chart/progress/indicator(..).restricted(true).as(player).onAction(ctx => …).publish()`, `set`, `get`, `alert`, `remove`, `sections`. Widgets and values are validated like Lua; dashboard actions need the same per-widget permission (`act.buttons`) and validation, then `onAction` runs (`ctx.reject("code")` refuses); `as(player)` needs `layout.edit`. | – | yes (`script_sections` without KubeJS, `kubejs_bindings` with a real server script) |

### More ATM11 machines

| Mod | Version | What the card shows | Controls | Tested |
|---|---|---|---|---|
| any mod: plain fluid tanks | – | generic **Tank** card (kind `tank`, lowest priority, below the Energy card): a block entity with a fluid capability, no energy capability and at most 2 item slots – fill %, fluid, mB (up to 8 tanks summed). Covers EvilCraft's Dark Tank, Stellaris fluid tanks and similar. | none | yes (EvilCraft Dark Tank with 1 bucket of water) |
| Witchery (`witchery`) | 0.5.17 | **Altar** card on the altar core: power gauge, current / max power, range, augment rate and boost; warn alert below 10 % | none | no: the altar is a multiblock; the mod loads in the dev server (Kotlin for Forge, Curios, Modonomicon, Lithostitched) |
| Forbidden & Arcanus (`forbidden_arcanus`) | 2.8.0-beta.2 | **Hephaestus Forge** card: tier, ritual running, main item, the four essences (aureal, ectoplasm, blood, experience) as gauges and amount / limit; info alert when a ritual finishes | none | yes (lone forge: tier 1, idle, empty) |
| Pylons (`pylons`) | 6.0.1 | **Pylon** card for all five pylons: on/off, owner, range | on/off toggle (the pylon's own switch), confirm, `act.buttons` | yes (expulsion pylon toggled off and on) |
| EvilCraft (`evilcraft`) | 1.2.107 | Dark Tank via the generic Tank card; Blood Infuser / Spirit Furnace keep the generic Energy/tank cards | – | see Tank |
| Ars Magica Legacy (`arsmagicalegacy`) | 1.7.0 | **Etherium** card for every block of its public `EtheriumHandler` API (obelisk, celestial prism, black aurem, altar core): gauge + amount / max per etherium type; the widgets follow the block's types | none | yes (obelisk) |

### No card of their own

- **Integrated Terminals / Tunnels**: –: skipped: their networks expose no aggregated inventory capability on a block (only through ID parts/variables); see item 7 card
- **ExtendedAE** (`extendedae`): no level maintainer or CPU-like block; its machines (assemblers, pattern providers) are covered by the ME bridge's job list
- **AE2 Infinity Booster / Network Analyzer**: nothing to show (range card / client-side analyzer)
- **Silent Gear Metalworks** (`sgearmetalworks`): adds only recipes to the Productive Metalworks foundry, no blocks of its own
- **Mystical Automation** (`mysticalautomation`): its machines only expose energy, so the generic Energy card applies
- **LaserIO, Pipez**: pipes/nodes without state worth a card (no buffers, no machine status)
- **Logistics Networks** (`logisticsnetworks`): it talks to CC: Tweaked natively (peripherals), so a Lua program on an uplink covers it
- **Stellaris** (`stellaris`): fluid tanks via the generic Tank card, generators via the Energy card where they expose the capabilities (not verified in a test); rockets are entities
- **Theurgy** (`theurgy`): its machines (calcination oven, distiller, incubator, accumulators) were not examined in depth; tanks are covered by the Tank card, powered blocks by the Energy card
- **Reliquary** (`reliquary`): altar, apothecary cauldron and pedestals are short crafting steps; not examined in depth
- **Interdimensional Wireless Transmitter**: an RS2 network node; the RS Cloud Bridge already shows the network
- **Trash Cans, Item Collectors**: no state worth a card (a trash can is always empty; a collector only has a filter and a range)
- **Just Dire Things**: its generators/collectors are covered by the generic Energy/Tank cards where they expose the capabilities; no extra state examined

This page is generated from the compatibility manifest (docs/COMPAT.md in the source), so it is always up to date with the mod.

