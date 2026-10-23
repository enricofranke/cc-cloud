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
| Occultism (`occultism`) | 1.256.1 | Dimensional Storage Actuator (all four variants) | Storage | untested (needs Modonomicon, Curios, GeckoLib, CodedDefinedGUI at runtime) |
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
