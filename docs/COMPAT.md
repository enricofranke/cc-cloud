# Mod compatibility (manifest)

Every optional integration of CC: Cloud, tested against **All the Mods 11 0.12.0-beta** (MC 26.1.2, NeoForge 26.1.2.109).
All integrations are soft dependencies: they load only when the mod is present, and a version that does not fit
disables just that card with one log line. Controls follow the fine-grained permissions (docs/SPEC.md): buttons and
toggles need `act.buttons`, a machine's own redstone mode (widget `redstone` / `*_redstone`) needs `act.redstone`,
mainframe cards with coordinates or private progress are restricted sections (`view.positions`).

"Tested" means a GameTest (`./gradlew runGameTestServer -PintegrationMods=<key>`, see docs/BUILD.md) places the
block and checks the card headless. "Untested" integrations compile against the exact ATM11 jar but need a multiblock,
a player or the client and are not covered by a GameTest.

The v0.5 integrations (Powah, Ender IO, Oritech, Energized Power, Generator Galore, Energy Meter, Hostile Neural
Networks, Productive Bees, MFFS, ME Requester, spark, FTB Teams, FTB Chunks, Simple Backups) are listed in
docs/BUILD.md "Optional integration mods" and docs/SPEC.md "v0.5".

## v0.8 – wave 2

### 1. Storage controllers (Storage card)
An uplink on the controller shows **Storage used** (gauge), **Total items**, **Item types**, **Slots used** and an
**item search** over the controller's whole inventory (same search widget as the Cloud Bridges; rows are never
craftable, crafting answers `not_craftable`). Read-only: no extraction. Uses only NeoForge's item/fluid capabilities
and the block ids, no mod classes (`compat/storage`). Config `cards.storageCards` (default true). Scans at most every
5 s and at most 32 768 slots.

| Mod (id) | Version | Blocks | Card | Tested |
| --- | --- | --- | --- | --- |
| Functional Storage (`functionalstorage`) | 1.6.4 | Storage Controller, Framed Storage Controller, Controller Access Points | Storage | yes (bare controller; drawers are linked with the Linking Tool only) |
| Sophisticated Storage (`sophisticatedstorage`) | 1.5.121 | Controller | Storage | yes (controller + chest) |
| Occultism (`occultism`) | 1.256.1 | Dimensional Storage Actuator (all four variants) | Storage | yes (wave 3: actuator alone, 100 iron ingots; runtime Modonomicon, Curios, GeckoLib) |
| DimStorage (`dimstorage`) | 10.0.1 | Dimensional Chest / Dimensional Tank | Storage / Tank (fill %, fluid, mB) | yes (both) |
| Integrated Terminals / Tunnels | – | – | skipped: their networks expose no aggregated inventory capability on a block (only through ID parts/variables); see item 7 | – |

### 2. Applied Energistics add-ons

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| Advanced AE (`advanced_ae`) | 26.1.7 | **Quantum Computer** card on any block of the multiblock: status (online + name / not formed / no power), free storage, co-processor threads, jobs running, up to 6 job rows ("64× Iron Ingot – 42 %, 1 min 3 s"); info alert when all jobs are done. Quantum CPU jobs also show in the ME Cloud Bridge's job list, because Advanced AE adds its CPUs to AE2's `getCpus()`. | "Cancel all jobs" (`act.buttons`, confirm) | partly: a single quantum unit block ("Not formed", cancel → `unavailable`); a formed quantum computer needs the full multiblock on a powered grid |
| ExtendedAE (`extendedae`) | 1.0.5 | skipped: no level maintainer or CPU-like block; its machines (assemblers, pattern providers) are covered by the ME bridge's job list | – | – |
| AE2 Infinity Booster / Network Analyzer | – | skipped: nothing to show (range card / client-side analyzer) | – | – |

### 3. Quarries

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| QuarryPlus, published as "Additional Enchanted Miner" (`quarryplus`) | 26.12.160 | **Quarry** / **Chunk Destroyer** cards: status (the machine's state from its public client-sync tag: building the frame, mining, removing fluids, finished, …), working indicator, energy %, area size and bottom Y (no coordinates), dig depth % (quarry: from the drill head), buffered items; warn alert once when 10 000+ mined items pile up in the internal buffer (nothing pulls them out), info alert when finished | none: QuarryPlus has no redstone or API pause | yes (quarry: no area → placed with its default area → working) |

### 4. Spawners and mob farms

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| Apothic Spawners (`apothic_spawners`) | 2.0.1 | **Spawner** card on (upgraded) spawners: mob, spawning now (player in range or "ignore players"; with Redstone Control only while powered), "needs redstone" indicator, next spawn %, delay range, mobs per spawn, max nearby, player range, the boolean upgrades | none: Redstone Control is an upgrade item applied in-game; a dashboard switch would bypass the progression | yes (pig spawner, default stats) |
| Gateways to Eternity (`gateways`) | 6.0.2 | **Gateways** mainframe card (restricted, `view.positions`): gates open within 256 blocks of the mainframe – name, wave n / total, enemies alive, lives, distance; alerts when a gate opens, completes or closes. Gates are entities, so this is a mainframe card. Config `world.gatewaysCard`. | none | yes (Emerald Grove gate opened and removed) |
| Easy Villagers (`easy_villagers`) | 1.1.47 | **Villager Breeder** (villagers, breeding / needs food), **Iron Farm** (villager, golem cycle %), **Villager Farmer** (villager, crop) with output slots used, output full indicator and a warn alert once when the output is full | none | yes (empty iron farm) |

### 5. Processing machines

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| Iron Furnaces (`ironfurnaces`) | 4.4.2 | **Furnace** card on every tier: status (smelting X / idle / no fuel / output full), smelting %, fuel left, input, output, energy; warn alert once when the output slot is full | furnace redstone mode (Ignore / Run without signal / Run with signal), select with confirm, `act.redstone` | yes (smelts raw iron, mode switch) |
| Cobblegen Galore (`cobblegengalore`) | 0.2.9 | **Block generator** card: what it makes, tier speed (×modifier per tick-rate), buffer n / 64; warn alert once when the buffer is full (nothing pulls the blocks out). The exact items/min depend on the recipe's private speed and are not shown. | none | yes (stone generator between water and lava) |
| Productive Metalworks (`productivemetalworks`) | 1.17.1 | **Foundry** card on the controller (also from an uplink on a foundry tank, drain or capacitor): formed + coil type, fuel, power (energy coil), items melting, molten metals (up to 4) | none | yes (lone controller, "Not formed") |
| Silent Gear Metalworks (`sgearmetalworks`) | 1.6.0 | skipped: adds only recipes to the Productive Metalworks foundry, no blocks of its own | – | – |
| Agritech: Evolved (`agritechevolved`) | 2.2.0.5 | **Planter** / **Advanced Planter** cards: plant, soil, growth %, energy (advanced), output full indicator + warn alert | none | yes (empty basic planter) |
| Productive Farming (`productivefarming`) | 0.13.0 | **Farm** card on the farm controller (also from an uplink on a hatch or other auxiliary part): formed, water, output slots used, output full + warn alert | none | yes (lone controller). Note: Productive Farming's own GameTest `agritech_compat_recipes_present` fails in a dev run without Agritech Two; ours passes. |
| Mystical Agriculture (`mysticalagriculture`) | 9.0.10 | **Machine** card for Essence Furnace, Seed Reprocessor, Soul Extractor, Harvester, Ore Infuser, Soulium Spawner: status, progress, energy, fuel left, upgrade tier | none | untested (retried in wave 3): any dev server with Mystical Agriculture 9.0.10 + Cucumber 9.0.6 (also with Mystical Agradditions) crashes at start. CC: Tweaked rebuilds the creative tabs on server start (`CommonHooks.onServerStarted` → `CreativeModeTabs.tryRebuildTabContents`), and MA's own tab then adds "Inferium Essence" twice (`ModCreativeModeTabs` via Cucumber's `FeatureFlagDisplayItemGenerator`). ATM11 runs the same code with its crop/tag configuration (`config/mysticalcustomization`, `cucumber-tags.json`), so it is not our bug, but a minimal test world cannot be started; the card stays compile-checked against the exact jar. |
| Mystical Automation (`mysticalautomation`) | 2.0.7 | no own card: its machines only expose energy, so the generic Energy card applies | – | – |
| Railcraft Reborn (`railcraft`) | 1.4.5 | Multiblocks are read through their master block entity, so an uplink or a Cloud Probe on **any** block of the structure shows the formed multiblock: **Steam Boiler** card on any boiler block (firebox or tank): status + °C, temperature %, water %, steam %, burning; crit alert once when the water runs low while the boiler is hot (refilling a dry hot boiler makes it explode). **Iron / Steel Tank** card on any wall, gauge or valve: status, filled %, fluid, amount. **Coke Oven** card on any brick: status (cooking X / idle / creosote tank full), progress, creosote %, input, output; warn alert once when the creosote tank is full. **Blast Furnace** card on any brick: status, progress, burning, input, fuel (+ burn time left), output, slag | none | yes (lone firebox and blast furnace brick: "Not formed"; a formed 3×3×3 coke oven and a 3×4×3 iron tank read from a corner block that is not the master: the master's output and fluid) |

### 6. Logistics

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| Super Factory Manager (`sfm`) | 4.34.0 | **Factory Manager** card: state (running / no disk / no program / program has errors), program name, average tick time, errors, warnings, first error (SFM's translation key on dedicated servers), queued redstone pulses; warn alert when the program becomes invalid | none: SFM has no on/off – taking the disk out is its off switch | yes (manager without a disk) |
| Modular Routers (`modularrouters`) | 26.1.2.2 | **Modular Router** card: status, modules, speed (tick rate, items, eco mode), buffer | redstone mode (Always / Redstone low / Redstone high / Never / Redstone pulse) – "Never" switches the router off; select with confirm, `act.redstone` | yes (empty router, switched to Never) |
| LaserIO, Pipez | – | skipped: pipes/nodes without state worth a card (no buffers, no machine status) | – | – |
| Logistics Networks (`logisticsnetworks`) | 1.13.0 | no card: it talks to CC: Tweaked natively (peripherals), so a Lua program on an uplink covers it | – | – |

### 7. Integrated Dynamics

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| Integrated Dynamics (`integrateddynamics`) | 1.36.1 | **Display Panels** card: an uplink next to a logic cable that carries Display/Light Panels shows each panel's current value with its type (one label per side; the card rebuilds when panels are added/removed). ID evaluates the variables on its own network; the card only reads the panel's display value. Variable Stores are not read (their variables are evaluated only by readers/panels). | none | untested: placing parts on a cable needs ID's part placement and a network; compiled against the exact jar and loaded in a dev server |

### 8. World & progression

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| Waystones (`waystones`) | 26.1.2.16 | **Waystones** mainframe card (restricted, `view.positions`): waystones owned by the mainframe owner or a member of the owner's team (team providers, e.g. FTB Teams), plus global waystones – name, dimension, coordinates (8 rows) and the network-map overlay in the snapshot: `"waystones":{"items":[{"name","dim","x","y","z","kind","global"?}],"truncated"}` (max 512, re-read every 10 s). Config `world.waystonesCard`. The dashboard does not draw the overlay yet. | none | yes (own waystone listed, a stranger's private one hidden) |
| FTB Quests (`ftbquests`) | 26.1.2.9 | **Quests** mainframe card (restricted, `view.positions`): the owner's FTB team – overall %, chapters done / total, quests done / total, the 5 most recent completions with chapter and age; info alert on a new completion. Config `world.questsCard`. | none | partly: card, restricted flag and quest file access; a mock player has no FTB team, so progress values are not covered |
| JourneyMap (`journeymap`) | 6.0.10 (API 2.0.0) | client plugin (`client/compat/journeymap`, found by JourneyMap's `@JourneyMapPlugin`): nodes of every network where the player has `view.positions` as temporary (non-persistent) waypoints in one JourneyMap waypoint group "CC: Cloud" (switch the group off in JourneyMap's waypoint manager to hide them all; the group and that choice are kept). Client config `journeyMapWaypoints` (default true) and `journeyMapWaypointTypes` (default `mainframe`, `ender_relay`; also `relay`, `uplink`, `bridge`). Only mainframes get JourneyMap's in-world marker and beam, everything else shows on the map only. Names: the device's card title (computer label, Lua title, machine card) or the block's name ("Cloud Mainframe", "Ender Relay"), coloured by type. The client subscribes with the `map_nodes_subscribe` payload; the server (`map/MapNodesService`) re-checks the permission every 5 s, sends `map_nodes` only when it changed, at most 512 nodes; the client filters by type. | client config | server part yes (GameTest `map_nodes_filtering`: owner gets his network, a stranger nothing, no payload without subscription; unit tests `MapNodesTest`, `MapWaypointsTest`: default types, map-only devices, names); the JourneyMap side needs a client and is untested |
| FTB Chunks | – | already done in v0.5 (Claims card + overlay); claims also feed the claim guard (devices in another team's claim are blocked, docs/SECURITY.md "Claims") | – | – |

**FTB Quests chapters (auto-install).** Besides the card, the standard jar (`cccloud-<version>.jar`) carries the two quest
chapters of `integration/ftbquests` (CC: Tweaked, 24 quests; CC: Cloud, 39 quests) and adds them to
`config/ftbquests/quests` once, on `ServerAboutToStartEvent` – before FTB Quests 26.1.2.9 reads its book on
`ServerStartedEvent`. Backup first; foreign chapters and existing texts are never replaced; ATM11's Tech group or a new
"CC: Cloud" group; nothing at all if one of our ids is already used; the CC: Tweaked chapter only when the book has no
CC: Tweaked chapter of its own. A marker (`config/cccloud/quests-installed.json`) makes it once only (a deleted chapter is
not added again) and lets later versions update only chapters and texts nobody changed (content hashes that survive
FTB Quests re-saving the book). Config `installQuests` in `cccloud-server.toml` (default true). Modpacks use the modpack
edition (`cccloud-<version>-modpack.jar`: no quest files, `installQuests` false) and the release zip
`cccloud-quests-atm11-<version>.zip`. Tested: unit tests (`compat/ftbquests/install`) and the GameTests
`ftbquests_install` (fresh book, an FTB Quests re-save then an update, a pack with its own CC: Tweaked chapter,
`installQuests = false`, modpack edition, deleted and edited chapters – each result loaded with FTB Quests' own reader)
and `ftbquests_install_live` (the server's own book was installed before FTB Quests loaded it).

### 9. In-game handbook

| Mod (id) | Version | What | Tested |
| --- | --- | --- | --- |
| GuideME (`guideme`) | 26.1.15-beta | **CC: Cloud Handbook**: a data-driven GuideMe guide (`assets/cccloud/guideme_guides/handbook.json`, pages in `assets/cccloud/guides/cccloud/handbook/`, German in `_de_de/`), generated from the dashboard docs by `tools/gen_handbook.ts` (11 pages per language, recipes as `<RecipeFor>` with a fallback text). Item: GuideMe's guide item with `guideme:guide_id = cccloud:handbook`, crafted shapeless from a book + Cloud Core (recipe only when GuideMe is loaded). No code dependency. | yes (`guideme_handbook` parses every page with GuideMe's parser; screenshots of the EN and DE guide in a dev client). The ME/RS bridge recipe boxes show GuideMe's "missing item" note when AE2 / RS are absent. |

### 10. KubeJS bindings

| Mod (id) | Version | What | Tested |
| --- | --- | --- | --- |
| KubeJS (`kubejs`) | 26.1.2-8.0.6 (Rhino 2101.2.8-build.91) | Server scripts get **`CCCloudEvents.action`** (an accepted dashboard action: mainframeId, sectionId, section, widget, value, playerName, player, device), **`CCCloudEvents.deviceLinked`** (uplink/bridge bound with a Link Card: mainframeId, deviceId, type, dimension, pos, player) and **`CCCloudEvents.alert`** (mainframeId, sectionId, text, level), and the binding **`CCCloud`**: `mainframes()`, `mainframesOf(player)`, `has(player, mainframeId, "act.buttons")`, `section(mainframeId, key).title(..).gauge/toggle/button/slider/select/buttons/number/text/label/chart/progress/indicator(..).restricted(true).as(player).onAction(ctx => …).publish()`, `set`, `get`, `alert`, `remove`, `sections`. Widgets and values are validated like Lua; dashboard actions need the same per-widget permission (`act.buttons`) and validation, then `onAction` runs (`ctx.reject("code")` refuses); `as(player)` needs `layout.edit`. | yes (`script_sections` without KubeJS, `kubejs_bindings` with a real server script) |

## Wave 3 – more ATM11 machines (survey of the ATM11 0.12.0-beta mod list)

False positives of the generic Energy and Tank cards can be excluded by pack makers: block tag `#cccloud:no_generic_card` (data pack) or the config list `cards.genericCardExcludes` in `cccloud-integrations-server.toml` (`"mod:block"` or `"mod:*"`).

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| any mod: plain fluid tanks | – | generic **Tank** card (kind `tank`, lowest priority, below the Energy card): a block entity with a fluid capability, no energy capability and at most 2 item slots – fill %, fluid, mB (up to 8 tanks summed). Covers EvilCraft's Dark Tank, Stellaris fluid tanks and similar. | none | yes (EvilCraft Dark Tank with 1 bucket of water) |
| Witchery (`witchery`) | 0.5.17 | **Altar** card on the altar core: power gauge, current / max power, range, augment rate and boost; warn alert below 10 % | none | no: the altar is a multiblock; the mod loads in the dev server (Kotlin for Forge, Curios, Modonomicon, Lithostitched) |
| Forbidden & Arcanus (`forbidden_arcanus`) | 2.8.0-beta.2 | **Hephaestus Forge** card: tier, ritual running, main item, the four essences (aureal, ectoplasm, blood, experience) as gauges and amount / limit; info alert when a ritual finishes | none | yes (lone forge: tier 1, idle, empty) |
| Pylons (`pylons`) | 6.0.1 | **Pylon** card for all five pylons: on/off, owner, range | on/off toggle (the pylon's own switch), confirm, `act.buttons` | yes (expulsion pylon toggled off and on) |
| EvilCraft (`evilcraft`) | 1.2.107 | Dark Tank via the generic Tank card; Blood Infuser / Spirit Furnace keep the generic Energy/tank cards | – | see Tank |
| Stellaris (`stellaris`) | 2.0.7 | no own card: fluid tanks via the generic Tank card, generators via the Energy card where they expose the capabilities (not verified in a test); rockets are entities | – | no |
| Theurgy (`theurgy`) | 1.116.0 | no own card in this pass: its machines (calcination oven, distiller, incubator, accumulators) were not examined in depth; tanks are covered by the Tank card, powered blocks by the Energy card | – | – |
| Ars Magica Legacy (`arsmagicalegacy`) | 1.7.0 | **Etherium** card for every block of its public `EtheriumHandler` API (obelisk, celestial prism, black aurem, altar core): gauge + amount / max per etherium type; the widgets follow the block's types | none | yes (obelisk) |
| Reliquary (`reliquary`) | 2.0.92 | skipped: altar, apothecary cauldron and pedestals are short crafting steps; not examined in depth | – | – |
| Interdimensional Wireless Transmitter | 1.0.1 | skipped: an RS2 network node; the RS Cloud Bridge already shows the network | – | – |
| Trash Cans, Item Collectors | 1.1.1a / 1.1.12a | skipped: no state worth a card (a trash can is always empty; a collector only has a filter and a range) | – | – |
| Just Dire Things | 1.6.11 | no own card: its generators/collectors are covered by the generic Energy/Tank cards where they expose the capabilities; no extra state examined | – | – |

## Follow-ups (2026-10-30)

### CC: Tweaked wired networks

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| CC: Tweaked (`computercraft`) | 1.120.0 | An uplink on a computer shows a card for every machine the computer reaches through its wired modems (networking cable, modems switched on): the card an uplink on that machine would show, same controls and permissions. Up to `wiredMachineCards` (8) per uplink, one per machine and mainframe, not for machines with their own uplink or probe. Lua bridge widgets accept bridges on the computer's cable network (`me_cloud_bridge_0`) with the uplink directly on the computer. Public CC API only (a listener node on the modem's network). | the card's own | yes (`wired_machine_cards`: an energy block behind a full wired modem; a chest gets no card) |

## Wave 4 – more ATM11 mods (2026-10-30)

Checked against the jars of All the Mods 11 for MC 26.1.2 (CurseForge files of the instance, SHA-1 compared, see docs/BUILD.md "Wave 4"). Every card is a soft dependency in `compat/<modid>`; GameTests run with `-PintegrationMods=<key>`.

| Mod (id) | Version | What | Controls | Tested |
| --- | --- | --- | --- | --- |
| any mod: CC peripherals | – | generic **Peripheral card** (kind `peripheral`, fallback below every other card) for every block CC: Tweaked offers as a peripheral and that has no card of its own: up to 6 values and up to 2 charts picked in the card's settings, see "Peripheral card" below | an optional `set…(boolean)` method as switch (`act.buttons`, confirm, PIN by default); picking values needs `layout.edit` | yes (`peripheral_card_generic`: a vanilla chest through CC's generic inventory methods; `peripheral_card_own_methods`: a test peripheral with every kind of method) |
| Just Dire Things (`justdirethings`) | 1.6.11 | **Generator** card (Simple Coal / Fuel Generator: status, output FE/t, energy, burn left and fuel or fuel tank), **Machine** card for every other redstone-controlled machine (breakers, placers, clickers, droppers, swappers, fluid collectors and placers, item collector, experience holder, paradox machine, energy transmitter: status, speed, energy and tank where present, the transmitter's network energy, the holder's experience), read-only **Sensor** card (signal out, what it senses, strong/weak). Player Accessor, Goo blocks and the Eclipse Gate get no card. | the machine's own redstone mode (Always on / without signal / with signal / one step per pulse), select with confirm, `act.redstone` | yes (coal generator: mode switch; advanced block breaker; simple sensor) |
| Productive Trees (`productivetrees`) | 26.1.2-1.3.1 | **Sawmill**, **Pollen Sifter** and **Stripper**: status, progress (sawmill, sifter), input, output, by-products (sawmill) or axe (stripper); warn alert once when the output is full. Tree farms themselves are trees in the world, not blocks with state. | none (the machines have no switch) | yes (lone sawmill) |
| Ranged Pumps (`rangedpumps`) | 2.0.0 | **Pump**: state (pumping, no energy, stopped by redstone, tank full, done), tank %, fluid and mB, energy %, scan range (no coordinates); info alert when done, warn alert when the tank is full | none: any redstone signal stops a pump, there is no mode | yes (lone pump) |
| Charging Gadgets (`charginggadgets`) | 1.16.1 | **Charging Station**: status, station energy, burn left, fuel, the item being charged and its own charge (the item's energy capability); info alert when it is charged | none | yes (station burns coal into FE) |
| wd's Selling Bin (`selling_bin`) | 1.6 | **Selling Bin** (read from its main block, also from an uplink on the other half): item to sell and what it is worth, credit (sold value not yet paid out), currency in the output. The mod keeps no sales history, so past earnings are not shown. | auto-sell toggle and a **Sell now** button (both `act.buttons`, what the bin's own buttons do) | yes (bin alone: toggle; Sell now refused while empty. A plain test world has no currency data, so no sale is run) |
| Cooking for Blockheads (`cookingforblockheads`) | 26.1.2.6 | Fridges, cabinets and counters (plain and all 16 colours) as **Storage** cards with item search; **Oven** card (status, items cooking, cooking and burn progress, input, fuel, output, energy with a heating unit; warn alert when the output is full); **Toaster** card (progress, what is inside) | the toaster's on/off (`act.buttons`; refused while it is empty) | yes (oven, toaster switched on and off, fridge as Storage card) |
| Farming for Blockheads (`farmingforblockheads`) | 26.1.2.5 | **Shipping Bin**: next shipment (value against the shipment size), goods, emeralds paid out; info alert when a shipment leaves. The Market only opens a trade screen and holds no state, so it has no card. | none | yes (bin alone, then wheat put in) |
| Training Dummy (`trainingdummy`) | 1.9.0 | **Training dummies** mainframe card (restricted, `view.positions`): a DPS meter for the dummies within 64 blocks of the mainframe – DPS over the last 5 s, last hit, total of the current streak and who hit, the best DPS, a DPS chart. Dummies are entities and the mod keeps DPS only on the client, so the card counts hits itself (`LivingDamageEvent.Post`). | none | yes (dummy spawned next to the mainframe and hit) |
| FTB Essentials (`ftbessentials`) | 26.1.2.5 | **Warps** mainframe card (restricted, `view.positions`): the server's warps (what `/listwarps` shows every player) with dimension and coordinates, nearest first, at most 8. Homes are private and never shown; nothing on the card teleports. Config `world.warpsCard`. | none | yes (warp added through FTB Essentials' warp manager) |
| The Aether II (`aether_ii`) | 26.1.2-alpha.4.1 | Network map: the Aether dimension with its name, icon and height range (y −32 to 383) in the 2D slice and 3D cut sliders; Ender Relays link to it like to any dimension | – | unit tests of names, icons and ranges; not in a dev world |
| The Undergarden (`undergarden`) | 26.1.2-0.9.8 | Network map: name, icon and height range; it has a roof, so the 2D map opens on a slice at the network's height instead of the bedrock roof (like the Nether) | – | unit tests; not in a dev world |
| All the Modium (`allthemodium`) | 26.1.2-4.2.0 | Network map: Mining Dimension, The Other and The Beyond (y −128 to 383) with names, icons and height ranges | – | unit tests; not in a dev world |
| Nullscape (`nullscape`) | 1.2.20 | no card: it reshapes the End's terrain (same dimension `minecraft:the_end`), which the map already handles | – | – |
| DeepNull Reforged (`deepnullreforged`) | 4.2.1-beta | skipped: not a dimension but a Dank Null-style item storage you carry; nothing a block card could show | – | – |
| Mystical Agradditions (`mysticalagradditions`) | 9.0.4 | skipped: adds no machine or block entity (storage blocks, cruxes, farmland, crops) | – | – |
| Item Collectors (`itemcollectors`) | 1.1.12a | skipped: no count of collected items and no on/off; only a range and a filter | – | – |
| Elevator (`elevatorid`) | 26.1-1.16.2 | skipped: there is no cabin – an elevator is a column of teleport pads, and "call to floor N" would mean teleporting a player standing near a pad from the dashboard, which CC: Cloud does not offer | – | – |
| Refined Flow Analytics (`refinedflowanalytics`) | 2.0.0 | skipped in this wave: it records RS2 throughput on the server, but only in internal classes (no API) and only while a Flow Grid, Monitor or Detector is on the network; a candidate for the RS Cloud Bridge | – | – |
| AE2 Network Analyser (`ae2netanalyser`) | 1.0.0 | skipped: items only; the data is computed for the player holding the analyser and never stored, no API | – | – |
| LaserIO (`laserio`) | 1.10.2 | skipped: no transfer counters or machine state; a node only holds its cards | – | – |
| More Red (`morered`) | 26.1.2.3 | skipped: bundled cables keep no signal to read; linking their 16 colour channels to Cloud Redstone would need a new bridge block | – | – |
| Iron Jetpacks (`ironjetpacks`) | 9.0.3 | skipped: no blocks | – | – |
| Power Armor (`power_armor`) | 1.0.7 | no own card: the compressor and the armor modification table expose energy, so the generic Energy card applies; the compressor's redstone setting has no effect in this version | – | – |
| Mining Gadgets (`mininggadgets`) | 1.19.3 | skipped: only the modification table, without energy, progress or switch | – | – |

### Peripheral card

The fallback for every mod: if CC: Tweaked offers a block as a peripheral, an uplink or probe on it shows a card.

- **Which blocks:** any block with a peripheral on the uplink's face – the block's own (`PeripheralCapability`) or CC's generic one (energy, item and fluid methods, and generic sources other mods register) – that no other card claims. Not for CC: Tweaked's and CC: Cloud's own blocks, not for wired machine cards (a chest behind a computer's modem stays without a card), and not for blocks excluded with `#cccloud:no_generic_card` / `genericCardExcludes`. Config `cards.peripheralCard`.
- **Which methods are offered:** getters – names `get…`, `is…`, `list…` and CC's `size`, `list`, `tanks` – whose Java signature (the `@LuaFunction` method on the peripheral or on CC's registered generic source) takes no Lua arguments; and setters – `set…` methods that take exactly one boolean. Methods that ask for the computer (`IComputerAccess`) or raw arguments (`IArguments`) are never offered. Dynamic peripherals of other mods only report method names, not their arguments: they are counted ("n methods do not say which arguments they take") and never offered.
- **Settings:** in the layout editor's card settings the owner picks up to 6 getters (order = order shown), up to 2 numeric ones also as a 60-minute chart, and optionally one setter as the card's switch with "Ask for the PIN" (on by default). Sent as `card_settings` (WebSocket, `layout.edit`, rate-limited like actions), validated strictly by the card, saved with the uplink together with the block id it was made for; settings for another block are dropped.
- **Safety limits:** nothing is called that the owner did not pick, and discovery never calls a method. Picked getters run on the server thread every 2 s (`cards.peripheralPollSeconds`, 1–60); a poll slower than 2 ms doubles the interval up to 30 s, all Peripheral cards together spend at most 3 ms per tick (the rest poll a second later), a method that throws pauses for 30 s and shows "error: …". Results are cut to 64 printable characters (control and formatting characters removed); tables show their size, an item or fluid detail ("64× minecraft:stone"), or – up to 3 entries – their keys and values, never walking more than 256 entries. The setter only runs on a dashboard action: `act.buttons`, confirm, the PIN unless the owner turned it off, logged with the player.

### Dimensions

The network snapshot carries `dims: {"<id>": {minY, maxY, ceiling?}}` for every dimension on the map. The dashboard names the ATM11 dimensions (Aether, Undergarden, Mining Dimension, The Other, The Beyond, Moon, Earth Orbit, Dream/Nightmare/Mirror World, Reality Marble, Spatial Storage, ...), gives each tab a pixel icon (guessed from the id for unknown ones), fits the slice and cut sliders to the dimension's height and opens dimensions with a roof on a slice. Ender Relays and Ender Fiber Links never cared about the dimension.

## Not in this wave
Nothing left from the v0.8 list.
