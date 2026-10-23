# Building and testing CC: Cloud

Requirements: JDK 25 (Gradle's toolchain resolver downloads it if missing). Keep Gradle at most 3 workers on this machine:

```sh
./gradlew build --max-workers=3              # compile, unit tests, jar (build/libs/cccloud-<version>.jar)
./gradlew runGameTestServer --max-workers=3  # GameTests without AE2/RS (bridge tests are skipped by their conditions)
./gradlew runGameTestServer -PstorageMods --max-workers=3   # GameTests with AE2 + Refined Storage
./gradlew runServer -PstorageMods --max-workers=3           # dev server with both mods
./gradlew runClient -PstorageMods --max-workers=3
./gradlew runClient -PstorageMods -PiconBenchmark=/tmp/icons --max-workers=3   # renders every item icon once (needs a world in run/saves/cccloud-bench), logs time/size, quits
```

Dev runs (`runServer`, `runGameTestServer`) serve the dashboard on port **18787** (`-Dcccloud.devWebPort`), so they do not collide with a game running on the same machine; the real default stays 8787.

## Optional storage mods (Cloud Bridges)
The bridges compile against the versions in All the Mods 11. No local jars are needed; Gradle downloads them:

| Mod | Coordinates | Repository |
| --- | --- | --- |
| Applied Energistics 2 26.1.13-beta | `org.appliedenergistics:appliedenergistics2:26.1.13-beta:api` (compileOnly), full jar for `-PstorageMods` | https://maven.neoforged.net/releases |
| GuideME 26.1.15-beta (AE2 runtime dependency) | `org.appliedenergistics:guideme:26.1.15-beta` | https://maven.neoforged.net/releases |
| Refined Storage neoforge 3.2.1 | `maven.modrinth:refined-storage:P12kjgPK` | https://api.modrinth.com/maven |

Refined Storage 2 only publishes to GitHub Packages (needs a token), so the Modrinth maven is used. Modrinth addresses files by version id because the Fabric and NeoForge builds share the version number `3.2.1`; `P12kjgPK` is `refinedstorage-neoforge-3.2.1.jar` (SHA-1 `1bd09d5e51f82418d8602dec1e8fc6883beb852d`, identical to the ATM11 jar). Versions live in `gradle.properties` (`ae2_version`, `guideme_version`, `rs_modrinth_version`).

Without `-PstorageMods` the dev runs contain neither mod; that is the soft-dependency check (the server must start and all non-bridge GameTests must pass).

## Optional integration mods (v0.5 cards, server & team)
The integrations in `compat/<modid>` compile against the exact jars of All the Mods 11 0.12.0-beta. Everything comes from public Maven repositories (no `libs/` folder); the SHA-1 of every artifact was checked against the ATM11 jar. Versions live in `gradle.properties`.

| Mod (id) | Coordinates | Repository | SHA-1 = ATM11 |
| --- | --- | --- | --- |
| Powah 7.0.4-alpha (`powah`) | `maven.modrinth:powah:bymXTkIP` (+ runtime `me.shedaniel.cloth:cloth-config-neoforge:26.1.154`, GuideME) | Modrinth, maven.shedaniel.me | yes |
| Ender IO 9.0.6-alpha (`enderio`) | `maven.modrinth:enderio:SNsMXOPB` (+ compile `com.enderio:endercore:9.0.6-alpha`) | Modrinth, maven.rover656.dev | yes (Ender IO's own maven differs only in a manifest timestamp, so Modrinth is used) |
| Oritech 2.0.0-exp11 (`oritech`) | `maven.modrinth:oritech:ZgnrQ2pV` (+ `earth.terrarium.athena:athena-neoforge-26.1:4.7.3`, `maven.modrinth:geckolib:xfVfPcoC`) | Modrinth, maven.teamresourceful.com | yes |
| Energized Power 3.0.1 (`energizedpower`) | `maven.modrinth:energized-power:ZQjF9guf` | Modrinth | yes |
| Energy Meter 0.5.0 (`energymeter`) | `maven.modrinth:energymeter:qFGv9BPT` | Modrinth | yes |
| Generator Galore 1.7.0 (`generatorgalore`) | `curse.maven:generatorgalore-691049:8757688` | cursemaven.com (not on Modrinth) | yes |
| Hostile Neural Networks 7.0.0 (`hostilenetworks`) | `maven.modrinth:hostile-neural-networks:KaeR2HQK` (+ `maven.modrinth:placebo:c4uzJQYp`) | Modrinth | yes |
| Productive Bees 15.3.0 (`productivebees`) | `curse.maven:productivebees-377897:9124455` | cursemaven.com | yes |
| MFFS 5.8.39 (`mffs`) | `maven.modrinth:mffs:OHjnr54O` | Modrinth | yes |
| ME Requester 1.5.1 (`merequester`) | `maven.modrinth:merequester:asqYZIpt` (+ the full AE2 jar for compiling) | Modrinth | yes |
| spark 1.10.187 (`spark`) | `maven.modrinth:spark:HwHxykP9` (its `me.lucko.spark.api` classes are in the jar) | Modrinth | yes |
| FTB Teams 26.1.2.4 / FTB Library 26.1.2.9 / FTB Chunks 26.1.2.8 | `dev.ftb.mods:ftb-teams-neoforge`, `ftb-library-neoforge`, `ftb-chunks-neoforge` | maven.ftb.dev/releases | yes |
| Simple Backups 26.1.6 (`simplebackups`) | `de.melanx:SimpleBackups:26.1.6` | maven.melanx.de/release | yes |

- **productivelib** (Productive Bees' library) is only shipped inside the Productive Bees jar (`META-INF/jarjar`). The `extractProductiveLib` task copies it to `build/jarjar/` for compiling; at runtime FML loads it from the Productive Bees jar.
- **ExtendedAE** has no request/level-maintainer block, so there is no ExtendedAE integration (and no dependency).
- Each repository is declared with `exclusiveContent`, so it only serves its own group.

### Wave 2 (v0.8, docs/COMPAT.md)
All wave-2 artifacts come from cursemaven (`curse.maven:<slug>-<projectId>:<fileId>`), i.e. the exact CurseForge files of
ATM11 0.12.0-beta (file ids from the instance's `minecraftinstance.json`); the SHA-1 of every downloaded jar was compared
with the ATM11 jar (all identical). File ids live in `gradle.properties` (`*_curse_file`).

| Key (`-PintegrationMods=`) | Mod (version) | Extra runtime jars | Compile use |
| --- | --- | --- | --- |
| `dimstorage` | DimStorage 10.0.1 | EdivadLib 4.0.1 | none (capabilities + block ids) |
| `sophisticatedstorage` | Sophisticated Storage 1.5.121 | Sophisticated Core 1.5.13 | none |
| `functionalstorage` | Functional Storage 1.6.4 | Titanium 4.0.12 | none |
| `quarryplus` | QuarryPlus ("Additional Enchanted Miner") 26.12.160 | Scalable Cat's Force 3.9.0 (the `kotori_scala` language loader) | `compat/quarryplus` (public getters + client-sync tag) |
| `apothicspawners` | Apothic Spawners 2.0.1 | Placebo 10.0.2 | `compat/apothicspawners` (spawner stats API) |
| `gateways` | Gateways to Eternity 6.0.2 | Placebo, Apothic Attributes 3.1.0 | `compat/gateways` (GatewayEntity getters) |
| `easyvillagers` | Easy Villagers 1.1.47 | – | `compat/easyvillagers` (block entity getters) |
| `ironfurnaces` | Iron Furnaces 4.4.2 | – | `compat/ironfurnaces` |
| `cobblegengalore` | Cobblegen Galore 0.2.9 | – | `compat/cobblegengalore` |
| `productivemetalworks` | Productive Metalworks 1.17.1 | Productive Bees (+ its productivelib) | `compat/productivemetalworks` |
| `productivefarming` | Productive Farming 0.13.0 | Productive Bees | `compat/productivefarming` |
| `agritechevolved` | Agritech: Evolved 2.2.0.5 | – | `compat/agritechevolved` |
| `mysticalagriculture` | Mystical Agriculture 9.0.10 | Cucumber 9.0.6 | `compat/mysticalagriculture` (compile only; a dev run crashes in MA's creative tab) |
| `railcraft` | Railcraft Reborn 1.4.5 | – | `compat/railcraft` (boiler module API) |
| `waystones` | Waystones 26.1.2.16 | Balm 26.1.2.15, Shogi 26.1.2.12 | `compat/waystones` (WaystonesAPI) |
| `ftbquests` | FTB Quests 26.1.2.9 (`dev.ftb.mods:ftb-quests-neoforge`, maven.ftb.dev, SHA-1 identical) | FTB Teams, FTB Library | `compat/ftbquests` |
| `advancedae` | Advanced AE 26.1.7 (`advanced_ae`; jar-in-jars ae2addonlib, ae2wtlib_api) | AE2, GuideME, AE2WTLib 26.1.2-beta, GeckoLib | `compat/advancedae` (cluster API) |

```sh
./gradlew runGameTestServer -PintegrationMods -PdevWebPort=18797 --max-workers=3          # all integration mods
./gradlew runGameTestServer -PintegrationMods=powah,mffs -PdevWebPort=18797 --max-workers=3   # a subset (keys: see build.gradle)
./gradlew runServer -PintegrationMods -PdevWebPort=18797 --max-workers=3
```
Some integration mods ship GameTests of their own (Cobblegen Galore, Productive Farming); they run in the same
`runGameTestServer` and can fail for reasons outside CC: Cloud (e.g. Productive Farming's Agritech Two recipe check).
`-PdevWebPort` overrides the dev dashboard port (default 18787) so two checkouts can run tests at the same time. The integration GameTests use their own test environment `cccloud:integrations` (a separate batch), so they do not run at the same time as the load test. Without `-PintegrationMods` none of these mods is loaded, which is the soft-dependency check.

## Tests
- Unit tests (`src/test`): v0.5 card formatting/widget builder, team trust rule, restricted sections in the hub, the action guard (read-only devices, PIN widgets); token/PIN/pairing, rate limits, routing, message parsing (incl. `query`, `layout_set`, op values), widget validation for every widget type, the layout schema, the bridge index/watchlist logic, the WebHub snapshot/layout/chart behaviour, and the ROM Lua (`cloud.ui`, `cloud-demo`, `cloud-storage`) running on Cobalt, CC's Lua VM (`cc.tweaked:cobalt`, test-only), against a fake uplink that validates through the real `WidgetFactory`.
- GameTests (`data/cccloud/test_instance`): the Server card (always), and with `-PintegrationMods` the generic FE card (on HNN's loot fabricator), Powah energy cell (values + redstone mode) and reactor core, Ender IO Stirling generator (redstone control), Energy Meter (mode switch + measured flow), HNN simulation chamber, Productive Bees advanced hive (output full), MFFS projector (field on/off, PIN required), ME Requester (amount/enable), FTB Teams trust (party join/leave, config switch), FTB Chunks claims overlay and Simple Backups (backup now); mainframe placement/ownership, the load test (50 clients, 4×32 widgets, target < 0.5 ms/tick), the ROM mount contents, and with `-PstorageMods` one AE2 and one RS2 network test (creative energy cell + drive + 1k cell, creative controller + 1k storage block) that insert items and check the bridge, its peripheral and the direct-mode Storage template.
- Headless smoke test (with and without `-PstorageMods`): enable RCON for localhost in `run/server.properties` (`enable-rcon=true`, `server-ip=127.0.0.1`, a random `rcon.password`; `run/` is not committed), start `runServer` until `Done`, check the log for `[cccloud]` errors and "Dashboard web server listening", probe the WebSocket on port 18787 (a tiny auth frame gets `unauthorized`/4401, a 9 KB frame before auth gets 1009), then send `stop` over RCON (Gradle does not forward stdin) and check for "Dashboard web server stopped", exit code 0 and that ports 18787/25565/25575 are free.

### Headless test servers
- Set `pause-when-empty-seconds=-1` in `server.properties`: otherwise the server pauses without players and approvals, dashboard actions and rules wait.
- Approving from the server console or RCON works without a player: `cccloud approve <id>` / `cccloud deny <id>` (`cccloud approve` without an id suggests the waiting requests). The browser gets the role of the mainframe's owner (account codes: of that player); the audit log records "Console approved …".
- E2E only: with `-Dcccloud.devCommands=true`, the console command `cccloud test-token <mainframeId>` prints a device token of the mainframe's owner (never available to players or without the property).
