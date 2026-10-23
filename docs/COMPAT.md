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
