# Building and testing CC: Cloud

Requirements: JDK 25 (Gradle's toolchain resolver downloads it if missing). Keep Gradle at most 3 workers on this machine:

```sh
./gradlew build --max-workers=3              # compile, unit tests, both jars, quest zip (see "Downloads" below)
./gradlew runGameTestServer --max-workers=3  # GameTests without AE2/RS (bridge tests are skipped by their conditions)
./gradlew runGameTestServer -PstorageMods --max-workers=3   # GameTests with AE2 + Refined Storage
./gradlew runServer -PstorageMods --max-workers=3           # dev server with both mods
./gradlew runClient -PstorageMods --max-workers=3
./gradlew runClient -PstorageMods -PiconBenchmark=/tmp/icons --max-workers=3   # renders every item icon once (needs a world in run/saves/cccloud-bench), logs time/size, quits
```

## Downloads (what `build` makes)

| File | Task | What |
| --- | --- | --- |
| `build/libs/cccloud-<version>.jar` | `jar` | standard edition: for players' own servers. Carries the FTB Quests chapters (`/cccloud_quests/`, copied from `integration/ftbquests/quests` by `processResources`) and adds them to the quest book once on server start (`installQuests`, default true). |
| `build/libs/cccloud-<version>-modpack.jar` | `modpackJar` | modpack edition: the same classes, mod id and version; no `/cccloud_quests/`, plus `/cccloud-edition.properties` (`edition=modpack`, from `src/modpack/`), so `installQuests` defaults to false and the installer does nothing. Only the mod description says "Modpack Edition". |
| `build/distributions/cccloud-quests-atm11-<version>.zip` | `questPack` | the chapters for modpacks / installing by hand: `quests/`, `install.sh`, `README.txt` (EN/DE), `LICENSE`. |

`checkEditionJars` (part of `build`) fails when the standard jar lacks the quest files or has an edition file, or when the
modpack jar contains quest files, lacks `edition=modpack`, or differs in mod id, version or display name. The release job
attaches all three files (CI, below).

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
| `sfm` | Super Factory Manager 4.34.0 | – | `compat/sfm` |
| `modularrouters` | Modular Routers 26.1.2.2 | – | `compat/modularrouters` |
| `integrateddynamics` | Integrated Dynamics 1.36.1 | Cyclops Core 1.31.3, Common Capabilities 2.11.8 | `compat/integrateddynamics` (part container + panel state) |
| `guideme` | GuideME 26.1.15-beta | – | `compat/guideme` (GameTest only: parses the handbook pages with GuideMe's own parser) |
| `kubejs` | KubeJS 26.1.2-8.0.6 + Rhino 2101.2.8-build.91 (maven.latvian.dev, SHA-1 identical) | Better Advanced Tooltips 2601.1.0-build.11 (KubeJS needs it at runtime) | `compat/kubejs` (plugin via `kubejs.plugins.txt`); also copies `src/test/kubejs/*.js` into `run/kubejs/server_scripts` for the `kubejs_bindings` GameTest |
| `journeymap` | JourneyMap 6.0.10 (client; for `runClient`) | – | its API (`journeymap-api-neoforge-2.0.0-26.1`, only inside the JourneyMap jar) is extracted by `extractJourneyMapApi`; the extracted jar's SHA-1 equals the one in ATM11's jar (`51b77c19…`). JourneyMap's maven only has snapshots of it. |
| `occultism` | Occultism 1.256.1 (jar-in-jars CodedDefinedGUI, MagicParticlesLib) | Modonomicon 2.17.1, Curios, GeckoLib | none (capabilities + block ids) |
| `witchery` | Witchery 0.5.17 | Kotlin for Forge 6.4.0, Curios, Modonomicon, Lithostitched 1.8.1 | `compat/witchery` (altar getters) |
| `forbiddenarcanus` | Forbidden & Arcanus 2.8.0-beta.2 | Valhelsia Core 1.1.6 | `compat/forbiddenarcanus` |
| `pylons` | Pylons 6.0.1 | – | `compat/pylons` |
| `evilcraft` | EvilCraft 1.2.107 | Cyclops Core | none (generic Tank card test) |
| `arsmagica` | Ars Magica Legacy 1.7.0 | GeckoLib, Patchouli | `compat/arsmagica` (`EtheriumHandler` API) |
| `advancedae` | Advanced AE 26.1.7 (`advanced_ae`; jar-in-jars ae2addonlib, ae2wtlib_api) | AE2, GuideME, AE2WTLib 26.1.2-beta, GeckoLib | `compat/advancedae` (cluster API) |
| `jade` | Jade 26.1.12 (`maven.modrinth:jade:Pr1h4azo`, the ATM11 jar, compile-only otherwise) | – | `compat/jade` (tooltip plugin). Jade checks its providers only on a physical client: use `runClient`. |

### Wave 4 (docs/COMPAT.md "Wave 4")
CurseForge files of the ATM11 26.1.2 instance (`minecraftinstance.json`) via cursemaven, FTB Essentials from maven.ftb.dev; the SHA-1 of every downloaded jar equals the instance's jar. File ids live in `gradle.properties`.

| Key (`-PintegrationMods=`) | Mod (version) | Extra runtime jars | Compile use |
| --- | --- | --- | --- |
| `justdirethings` | Just Dire Things 1.6.11 | – | `compat/justdirethings` (machine base classes, redstone mode API) |
| `productivetrees` | Productive Trees 26.1.2-1.3.1 (jar-in-jars productivelib) | – | `compat/productivetrees` (compiled against the productivelib extracted from Productive Bees) |
| `rangedpumps` | Ranged Pumps 2.0.0 | – | `compat/rangedpumps` (tank, energy, state; `getState()`/`getRange()` are package-private and read by reflection, computed if that fails) |
| `charginggadgets` | Charging Gadgets 1.16.1 | – | `compat/charginggadgets` |
| `sellingbin` | wd's Selling Bin 1.6 (jar-in-jars tiny-multiblock-lib neo-26.1-3.1) | – | `compat/sellingbin`; `extractSellingBinLibs` copies tiny-multiblock-lib to `build/jarjar/` for compiling (only `IMultiBlock.getCenter`, the same in its 4.0.0 that ATM11 resolves) |
| `cookingforblockheads` | Cooking for Blockheads 26.1.2.6 | Balm 26.1.2.15, Shogi 26.1.2.12 | `compat/cookingforblockheads` (oven, toaster); fridges, cabinets and counters by block id (`compat/storage`) |
| `farmingforblockheads` | Farming for Blockheads 26.1.2.5 | Balm, Shogi | `compat/farmingforblockheads` |
| `trainingdummy` | Training Dummy 1.9.0+mc26.1.2 | – | `compat/trainingdummy` (`DummyEntity`, `LivingDamageEvent.Post`) |
| `ftbessentials` | FTB Essentials 26.1.2.5 (`dev.ftb.mods:ftb-essentials-neoforge`, maven.ftb.dev) | FTB Library | `compat/ftbessentials` (warp manager) |

The Peripheral card needs no extra mod: `peripheral_card_generic` (a chest) and `peripheral_card_own_methods` (a test peripheral that GameTest runs register on lecterns, `-Dcccloud.gametest`) run in every `runGameTestServer`.

```sh
./gradlew runGameTestServer -PintegrationMods -PdevWebPort=18797 --max-workers=3          # all integration mods
./gradlew runGameTestServer -PintegrationMods=powah,mffs -PdevWebPort=18797 --max-workers=3   # a subset (keys: see build.gradle)
./gradlew runServer -PintegrationMods -PdevWebPort=18797 --max-workers=3
```
Some integration mods ship GameTests of their own (Cobblegen Galore, Productive Farming); they run in the same
`runGameTestServer` and can fail for reasons outside CC: Cloud (e.g. Productive Farming's Agritech Two recipe check).
`-PdevWebPort` overrides the dev dashboard port (default 18787) so two checkouts can run tests at the same time. The integration GameTests use their own test environment `cccloud:integrations` (a separate batch), so they do not run at the same time as the load test. Without `-PintegrationMods` none of these mods is loaded, which is the soft-dependency check.

## Item icons for the website and the mock

The website's recipe grids and mod cards and the dashboard mock (`pnpm mock`, `pnpm screenshots`) show real inventory icons. They are exported, never committed: vanilla textures belong to Mojang, mod textures to their authors.

```sh
./gradlew exportIcons --max-workers=3                       # -> build/item-icons/<namespace>/<path>.png + index.json
./gradlew exportIcons -PiconStores=/path/a:/path/b --max-workers=3   # also use client-rendered icon stores
```

- **Which items:** `node cloud/dev/icon-ids.ts` lists them (task `iconExportIds`): every recipe result and ingredient (a tag becomes a typical member, `iconItem` in the docs content), the docs' named items, the card icon of every mod on the "Supported mods" page (`cloud/web/src/docs/content/mods-meta.ts`) and the mock's storage items.
- **Where an icon comes from**, best first (`index.json` says which per item):
  1. `client`: a client-rendered icon store (`<world>/cccloud/icons`, e.g. after `runClient -PiconBenchmark`). `run/saves/*/cccloud/icons` is picked up automatically, `-PiconStores` adds more (`:`-separated). The game's own renderer: tints, chests, banners, GeckoLib machines. Our own items only when the store was rendered by the current mod version. Icons a dedicated server drew itself (`source: "server"` in the manifest) are skipped.
  2. `model`: drawn from the item's model JSON (`src/iconExport`, `ModelIcon`): `item/generated` layers, block-like models with the model's gui transform (30°/225°), back-face culling, a depth buffer and inventory lighting. Models and textures come from everything on the dev classpath: the vanilla client resources of the NeoForge toolchain (`build/moddev/artifacts/minecraft-patched-*-merged.jar`), the compile-only mod jars (CC: Tweaked, AE2, RS and every integration mod of `build.gradle`) and our own resources. No display needed (`java.awt.headless`), so it runs in CI.
  3. `flat`: the server fallback's texture (`IconFallback`), scaled to 32×32.
  Items with special renderers (chests, banners, beds, GeckoLib models) only come from a client store; mods that are not on the classpath (AllTheModium, All The Tweaks, Mekanism, Thermal, …) have no icon. Those keep the letter tile.
- **Who uses them:** the website build (`website/scripts/prepare.mjs` in the website repository `enricofranke/cccloud-website`, which reads this checkout from `MOD_DIR`, default `../cc_tweaked_cloud` next to it) copies the icons the docs need into `website/public/img/icons` (gitignored) and lists them in `src/generated/item-icons.json`; the mock's `/api/icon` serves `build/item-icons` before its few drawn samples. `CCCLOUD_ITEM_ICONS=<dir>` points both at another export. Without an export everything still builds: letter tiles, and our own items fall back to their flat textures.
- **CI** (the website repository's workflow checks this repository out next to it): for real icons there, JDK 25 +
  Gradle as in `build.yml`, then before the site build
  ```yaml
  - name: Item icons for the website (not committed)
    run: ./gradlew exportIcons --max-workers=3
    working-directory: cc_tweaked_cloud
  ```
  The export only reads from Gradle's cache (vanilla client jar, mod jars), so it adds a few seconds after `./gradlew build`.
- Re-take the dashboard screenshots with real icons after an export: `SHOTS='^(desktop-st|phone-s[0-9]|docs-)' pnpm --dir cloud screenshots` re-takes only the matching shots (all of them without `SHOTS`).

## Server-rendered item icons

A dedicated server draws flat items and plain cube blocks itself at start (docs/SPEC.md "Item icons (as implemented)"). `serverIconBenchmark` runs the same renderer over a modpack's `mods/` folder (jar-in-jar mods included, the jars are only read) and prints flat / cube / skipped, the CPU time, the store size and the most common reasons an item was left for game clients:

```sh
./gradlew serverIconBenchmark -PiconBenchMods=/path/to/server/mods \
    -PiconBenchVanilla=$HOME/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar \
    [-PiconBenchNoVanillaTextures] [-PiconBenchOut=/some/dir] [-PiconBenchCompare=/path/to/world/cccloud/icons] --max-workers=3
```

`-PiconBenchVanilla` adds the vanilla items (a real server registers them too) and lets the renderer read vanilla models and textures, like `downloadVanillaAssets = true`; `-PiconBenchNoVanillaTextures` keeps the vanilla items in the list but without textures, like a default dedicated server. `-PiconBenchCompare` compares with the client renders of an icon store (mean absolute difference per channel). The store is written to `-PiconBenchOut` (a temp folder by default), never into the repository. The unit tests use self-made textures only (`ServerIconRendererTest`, golden images in `src/test/resources/icons/golden`; a mismatching render is written to `build/test-goldens`).

## Art previews
`python3 tools/gen_textures.py && python3 tools/gen_models.py` regenerate textures, models, blockstates and docs/MODELS.md (with validation). Renders need Pillow + numpy (e.g. `uv run --with pillow --with numpy python …`); reference assets for comparison scenes come from `CCCLOUD_REF` (`modref/<namespace>/assets/<namespace>`: vanilla, AE2, RS, Energized Power; CC: Tweaked via `--cc`), the scenes fall back to plain stand-ins without them:
```sh
python3 preview/render_preview.py --only detail                       # renders/uplinks.png, facings.png, mainframe.png, bridges.png, relays.png
MOUNTS=1 python3 preview/render_preview.py --only none               # renders/uplink_mounts.png (plate vs compact puck on the side of a full block, slab, solar panel, carpet)
python3 preview/build_viewer.py                                      # preview/viewer.html (three.js viewer, "Mounts" view)
```

## Tests
- In-game screens: `./gradlew runClient -PstorageMods -PintegrationMods=energymeter -PscreenShots=/some/dir` joins `run/saves/cccloud-bench` (copy any world there) in a 960×600 window, builds one of every case in the air above the player (mainframe; uplinks on an Energy Meter, on a computer, with a Lua card, unlinked, behind a relay, at the edge of range, out of range; an ME bridge; a relay), opens each screen, saves `<dir>/screenshots/<case>.png` and quits. Run it before and after a screen change.
- Handbook: `node tools/gen_handbook.ts` regenerates the in-game guide (`assets/cccloud/guides/cccloud/handbook`, EN + `_de_de`) from the dashboard docs; `--check` (and `cloud/test/handbook.test.ts`) fails when the committed pages are stale. `./gradlew runClient -PintegrationMods=guideme -PguideShots=/some/dir` opens the guide on startup, saves a screenshot and quits.
- Unit tests (`src/test`): the in-game card preview (`CardPreviewTest`: compact values per widget type, bounded size, layout-hidden widgets and labels) and the screen actions (rename/dashboard link rights and arguments), the network graph incl. cables (wired/fiber links, air-hop routing, cable preference, capacity weight and fiber FE), upgrade effects, capacity cut-off, wireless redstone channels and the `rs_override` message, v0.5 card formatting/widget builder, team trust rule, restricted sections in the hub, the action guard (read-only devices, PIN widgets); token/PIN/pairing, rate limits, routing, message parsing (incl. `query`, `layout_set`, op values), widget validation for every widget type, the layout schema, the bridge index/watchlist logic, the WebHub snapshot/layout/chart behaviour, and the ROM Lua (`cloud.ui`, `cloud-demo`, `cloud-storage`) running on Cobalt, CC's Lua VM (`cc.tweaked:cobalt`, test-only), against a fake uplink that validates through the real `WidgetFactory`. `StoreScriptsTest` runs every Script Store program (`scripts/*` of [cc-cloud-scripts](https://github.com/enricofranke/cc-cloud-scripts): `-PstoreScripts=<its scripts folder>` or a checkout next to this one, `../cc-cloud-scripts`; skipped without it, CI checks it out with a read-only deploy key) the same way, with one dashboard action per control, so an option the mod refuses fails the build. `UplinkMountTest` checks the plate / compact-puck rule for uplinks (full blocks, chest, hopper rim, slab, solar panel and carpet sides, inset faces; top and bottom faces only take the plate), that every mount's hitbox equals its generated model's bounds and that the blockstates use exactly the enum's values; `MapWaypointsTest` the JourneyMap waypoint rules (default types, map-only devices, names). `CapacityTest` also covers Cloud Probes (probeCapacityUnits, `probes` FE in the breakdown), `GuiActionsTest` the probe's `clear_target` rights, `UplinkMountTest` the probe hitbox against its model. `ConnectionLimitTest` opens real sockets against the production pipeline (a browser burst, 503 just over the per-IP limit, closed far over it); `JadePluginRulesTest` reads the compiled Jade plugin classes (no data provider may also be a component provider).
- GameTests (`data/cccloud/test_instance`): always the Server card, the Cloud Probe (`probe_remote_target`, batch `cccloud:ingame`: picking a machine with the Link Card through the real interaction pipeline is refused where a cancelled RightClickBlock stands in for a claim, another player cannot use the pick, beyond `probeRange` and the probe itself are refused, the card is read through the clicked face, a removed machine clears the target), side handling (`uplink_side_fallback_hint`: a machine with energy only on its internal view still gets its card, one with energy only on top shows no card and the hint names the top; both via test-only capabilities on a beacon and an enchanting table that exist only with `-Dcccloud.gametest`), the uplink screen (`uplink_screen_card`: live card preview, the mainframe name without a second position, rights per player, renaming the card through the dashboard layout and back), the compact uplink (`uplink_mount_slab_side`: placed on the side of a slab-height sculk sensor it gets the 1..7 px puck and its hitbox, the top of a daylight detector and a plain slab refuse it, a saved state with an unknown mount (the removed `post12`) loads as the plate, the mount follows the attached block and a plate saved before the mount existed fits itself after loading), the Cloud Tablet (coverage, rights, session snapshot, actions through the tablet: operator ok, viewer read_only, invalid value, PIN required/not set), the Cloud Display (forming a 3×1 row and a 3×2 wall, splitting on break; binding needs `network.link` and the section choice `layout.edit`, restricted sections refused, content sync, in-world clicks need `act.buttons` and the switch) and the Cloud Analyzer (payload only for networks with `view.positions`, other players' networks never; placement preview equals the server's link check); with `-PintegrationMods` the generic FE card (on HNN's loot fabricator), Powah energy cell (values + redstone mode) and reactor core, Ender IO Stirling generator (redstone control), Energy Meter (mode switch + measured flow), HNN simulation chamber, Productive Bees advanced hive (output full), MFFS projector (field on/off, PIN required), ME Requester (amount/enable), FTB Teams trust (party join/leave, config switch), FTB Chunks claims overlay and Simple Backups (backup now); mainframe placement/ownership, cables (`cable_*`: devices on CC cable + wired modem route as wired with 100 % and no hop FE, cutting the cable gives no_route, radio + cable prefers the cable and falls back to radio, fiber connects only to cloud blocks and fiber, fiber capacity weight and FE share, Ender Fiber Links across dimensions), upgrades (`upgrades_effects_capacity`: range/bandwidth/efficiency/Quantum Core effects, 16-device capacity, refused 17th link, Capacity card, over_capacity after removing it; `upgrades_chunk_anchor`: off by default, 2 chunks + 240 FE/t when enabled, released when the card is removed), `wireless_redstone` (cabled transmitter → receiver, other channel stays off, dashboard channel, override), `wireless_redstone_load` (a lever that was on before the transmitter, and a simulated chunk reload: the transmitter samples its input), `display_reload` (a 4×2 Cloud Display wall across a chunk border saved like a chunk save and loaded in the other order keeps its shape; a 0.9.0 save forms again), the mainframe removal tests (`mainframe_break_restore`, `mainframe_break_guard`, `mainframe_retention_purge`, `mainframe_recover_command`, batch `cccloud:ingame`), script sections (`script_sections`: widgets and values validated like Lua, section on the dashboard, owner's action ok and seen by the handler, handler rejection, invalid value, viewer `read_only`, one `CloudEvents.Action`, values kept on republish), with `-PintegrationMods=guideme` the handbook (`guideme_handbook`: every page in EN/DE parses with GuideMe's parser, only `RecipeFor` tags with existing items, links resolve), with `-PintegrationMods=ftbquests` the quest auto-install (`ftbquests_install`: a fresh book gets both chapters in the "CC: Cloud" group, a re-save by FTB Quests is no edit so a newer release updates, a pack with its own CC: Tweaked chapter gets only CC: Cloud without links into it, `installQuests = false` and the modpack edition install nothing, a deleted chapter is not added again and an edited one not overwritten – every result loaded with FTB Quests' own `readDataFull`; `ftbquests_install_live`: the dev server's own book was installed before FTB Quests read it), and with `-PintegrationMods=kubejs` the KubeJS bindings (`kubejs_bindings`: event group registered, binding with Rhino-style inputs, `as(player)` needs `layout.edit`, round trip with the dev script: alert → script publishes a section → dashboard action → `onAction` / rejection → `CCCloudEvents.action` → script alert), the load test (50 clients, 4×32 widgets, target < 0.5 ms/tick), the ROM mount contents, and with `-PstorageMods` one AE2 and one RS2 network test (creative energy cell + drive + 1k cell, creative controller + 1k storage block) that insert items and check the bridge, its peripheral and the direct-mode Storage template. v0.10: `scenes_rights_delays_pin` (an operator's press skips a step they may not do, saving such a scene is refused, delays in ticks, PIN scenes, rules run scenes with the owner's rights), `schedules_clocks` (per-mainframe test clocks: weekday schedule, interval, in-game time, one missed run after a restart, nothing outside the window, an unloaded mainframe skips and logs once), `api_scopes_rights` (public API: scope ∩ live rights, restricted cards, PIN refusal, ban, lost role) and with `-PstorageMods` `me_bridge_production` / `rs_bridge_production` (rates, top lists, item view, permission filter and a pinned chart, with manual sample times) and `me_bridge_autostock_cpu` (auto-stock on an AE2 network without a crafting CPU: one waiting row, no failure through several cooldowns, crafts once a CPU is built). Cloud Redstone devices (`rsdevice_*`): switch output on all six faces (strong/weak, analog), button pulse length, lamp light from the dashboard and redstone, sensor input after a simulated chunk reload, group cards with All on, a sensor rule and a scene step on a lamp, `act.redstone`/PIN/rate limit/link takeover, capacity weight and FE, and devices placed by `/setblock` with their link in the block data appear on their cards without a state change (`rsdevice_setblock_card`).
- Follow-ups 2026-10-30: unit tests `MapTileDiskTest` (purged mainframe's slice folders, age sweep, links not followed), `ProductionTrackerTest` (a change of 50 000 types creates at most `trackedItemsPerStorage` entries), `ScreenNetworkTest` (probe type and target in the screen network), `WiredCardsTest` (wired machine selection, titles, name resolution); GameTests `rsdevice_setblock_card`, `probe_network_node` (node type `probe`, target only with positions, screen network), `wired_machine_cards` (a machine behind the computer's wired modem gets its card), `mainframe_retention_purge` (now also the map slices), with `-PintegrationMods=railcraft` `railcraft_multiblocks` (formed coke oven and iron tank read from a corner); web `net3d.test.ts`/`network.test.ts` (probe), `redstone-devices.test.ts` (button colours); bot `test/deploy.test.ts` (NetworkPolicy).
- Wave 4: unit test `PeripheralValuesTest` (getter/setter names, captions, result formatting and cleaning, settings check, saved settings); `PermissionWalkTest` and `ShareSessionTest` cover `card_settings`; GameTests `peripheral_card_generic` (a chest through CC's generic inventory methods: only argument-free methods offered, values and chart, an equivalent peripheral object keeps the card, settings come back for a new chest) and `peripheral_card_own_methods` (a test peripheral on lecterns: what is offered, results cut and cleaned, a main-thread task, a failing getter, methods never picked are never called, the switch with confirm and PIN), with `-PintegrationMods` `justdirethings_generator`/`_machine`/`_sensor`, `productivetrees_sawmill`, `rangedpumps_pump`, `charginggadgets_station`, `sellingbin_bin`, `cookingforblockheads_oven`/`_toaster`/`_fridge`, `farmingforblockheads_shipping_bin`, `trainingdummy_dps`, `ftbessentials_warps`; web `wave4.test.ts` (dimension names, icons, ranges, roof; `dims` and `cardSettings` normalisation; `card_settings` in the mock).
- Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"): unit tests `sensor/*Test` (filter, log, tracker and zone, alerts and summary text, card and actions), `SensorTriggerTest`, `WebHubPrivateValuesTest`, `AdvancedSensorGraphTest`; GameTests `sensor_*` (zombie and villager entering, team and filters, redstone, a rule with `{who}`, claims (through the claim guard's `TestClaims`; with the sensor's own chunk claimed the sensor is blocked), names hidden for viewers, alert rate limit); fake players are handed to the scan through `AdvancedSensor.TEST_ENTITIES` (they are not in the world's entity list) and the cooldowns use a per-sensor test clock; web `advanced-sensor.test.ts`.
- Claim guard (2026-10-30, docs/SECURITY.md "Claims"): unit tests `ClaimRuleTest` (the rule: own team, ally, foreign team, unclaimed, no owner, no operator bypass; the pass budget; team names) and `WebHubClaimTeamTest` (the team's name only for sessions with `view.positions`), web `claims.test.ts`; GameTests `claim_guard` (an uplink on a machine, a wired machine card and a Cloud Switch are blocked when another team claims their chunks, refuse actions, work again as allies and after the unclaim; with the GameTest claim mod `TestClaims`), `probe_guards` and `map_tiles` (now through the shared guard), and with `-PintegrationMods=ftbchunks` `ftbchunks_claim_guard` (the same with real FTB Chunks claims and FTB Teams allies). Real claims affect every test around them, so the FTB Chunks tests run in test environments of their own (`cccloud:claims`, `cccloud:claims_card`) and unclaim when they end: `./gradlew runGameTestServer -PintegrationMods=ftbchunks -PskipLoadTest '-PgameTests=cccloud:ftbchunks_*'`.
- Headless smoke test (with and without `-PstorageMods`): enable RCON for localhost in `run/server.properties` (`enable-rcon=true`, `server-ip=127.0.0.1`, a random `rcon.password`; `run/` is not committed), start `runServer` until `Done`, check the log for `[cccloud]` errors and "Dashboard web server listening", probe the WebSocket on port 18787 (a tiny auth frame gets `unauthorized`/4401, a 9 KB frame before auth gets 1009), then send `stop` over RCON (Gradle does not forward stdin) and check for "Dashboard web server stopped", exit code 0 and that ports 18787/25565/25575 are free.

### Headless test servers
- Set `pause-when-empty-seconds=-1` in `server.properties`: otherwise the server pauses without players and approvals, dashboard actions and rules wait.
- Approving from the server console or RCON works without a player: `cccloud approve <id>` / `cccloud deny <id>` (`cccloud approve` without an id suggests the waiting requests). The browser gets the role of the mainframe's owner (account codes: of that player); the audit log records "Console approved …".
- E2E only: with `-Dcccloud.devCommands=true`, the console command `cccloud test-token <mainframeId>` prints a device token of the mainframe's owner (never available to players or without the property).

### Releases
- Version: `mod_version` in `gradle.properties` (0.9.0); changes go into `CHANGELOG.md`.
- CI (`.github/workflows/build.yml`) on every push and pull request: web dashboard typecheck, tests and build (into `src/main/resources/cccloud_web`), then `./gradlew build` (unit tests, handbook check); the jar is kept as a workflow artifact. Pushing a tag `v*` (e.g. `git tag v0.9.0 && git push origin v0.9.0`) also creates a GitHub release with three downloads – the standard jar, the modpack jar and the quest zip ("Downloads" above; the notes start with "Which download?") – and the changelog (tags with a `-`, e.g. `v0.9.0-rc1`, become pre-releases). No CurseForge upload yet.
- The release jar contains no GameTest data (`data/cccloud/test_instance`, `test_environment`, the test structure); the test functions are only registered when NeoForge enables GameTests (development and the GameTest server). Dev helpers (`-PiconBenchmark`, `-PdemoShots`, `-PguideShots`, `-PguiScreenshot`, `-PscreenShots`, `-PtextureCheck`, `cccloud.devCommands`, `cccloud.gametest`) do nothing without their system property.

### Handbook check
`./gradlew check` (and so `build`) runs `node tools/gen_handbook.ts --check` (task `checkHandbook`, Node ≥ 24): the in-game handbook pages must match the dashboard docs in `cloud/web/src/docs/content`. After editing the docs, run `node tools/gen_handbook.ts` and commit the pages with them. `-PskipHandbookCheck` skips it locally (e.g. while someone else's docs edits are not regenerated yet); CI always runs it.

### Deterministic GameTests and the load test
- GameTests run side by side in one world. Relays placed by a test serve only that test's mainframe (`restrictForTest`), so networks of neighbouring tests never connect.
- Load-independent (checked at load average 65–85 with extra CPU burners): `runGameTestServer` binds the dashboard to a free port (`cccloud.devWebPort=0`, so parallel checkouts never collide – a failed bind kept every mainframe offline) and sets `-Dcccloud.gametest=true`, which flushes the hub on every server tick instead of only on the web server's 100 ms timer. Tests wait for explicit conditions (e.g. the Server card's first values) with budgets of 2400 ticks; nothing asserts wall-clock-derived values. The GameTest server ticks as fast as it can (thousands of ticks per second when idle) while CC runs computers in wall-clock time, so tests that wait for a CC computer (`script_upload_run`, `rules_widget_redstone`, `terminal_type_and_see`, `auto_computer_card`) call `CloudGameTests.throttleForComputers` (5 ms sleep per tick while they run). Tests that log in fake players (tablet, display, analyzer) run in their own batch `cccloud:ingame`, and tests remove mod blocks that broadcast to all players (DimStorage tank) before other mods' tests log in mock players.
- External mods' own GameTests that need mods we do not load are optional/non-blocking: Railcraft `bed_minecart_sleep`, Cobblegen Galore `atm_ancient_stone`, Productive Farming's Agritech Two check (keep `productivefarming` out of combined runs).
- The load test runs in its own batch (test environment `cccloud:loadtest`), so no other test runs at the same time. It is still timing-sensitive on a busy machine: `./gradlew runGameTestServer -PskipLoadTest --max-workers=3` runs all functional tests; run the full suite (with the load test) at quiet moments.
