# All the Mods 11: coverage

Every jar in the mods folder of the All the Mods 11 instance (MC 26.1.2), checked on 2026-10-31. The evidence is the jars themselves: mod ids from `META-INF/neoforge.mods.toml`, block entities and capabilities from the class files, the real API with `javap -p`. Cards are soft dependencies, nothing loads if the mod is absent. Names are the ones in the jars.

"Generic" is a claim about the jar (it has block entities and registers capabilities). The Energy, Tank and Peripheral cards pick those up without mod code; only some were tried in a game (docs/COMPAT.md says which). "Generic" also means nothing was written for that mod.

| Status | Jars |
| --- | --- |
| Supported: a card, bridge or plugin of its own | 69 |
| Generic: capability or peripheral card | 18 |
| Tech batch: being done by someone else | 18 |
| Batch in progress: real candidates, no card yet | 5 |
| Nothing meaningful | 179 |
| Total | 289 |

## Supported: a card, bridge or plugin of its own

| Mod | Jar | Why |
| --- | --- | --- |
| Advanced AE | `AdvancedAE-26.1.7.jar` | Quantum Computer card |
| Agritech: Evolved | `agritechevolved-2.2.0.5+neoforge-mc26.1.2.jar` | Planter cards |
| AllTheModium | `allthemodium-26.1.2-4.2.0.jar` | network map: its three dimensions |
| Apothic Enchanting | `ApothicEnchanting-26.1.2-2.1.0.jar` | Library card: stored enchantments, levels, points (wave 5) |
| Apothic Spawners | `ApothicSpawners-26.1.2-2.0.1.jar` | Spawner card |
| Applied Energistics 2 | `appliedenergistics2-26.1.13-beta.jar` | ME Cloud Bridge and storage views |
| Applied Thaumaturge | `appliedthaumaturge-1.0.0.jar` | Arcane Assembler and ME Essentia cards (wave 5) |
| Ars Magica: Legacy | `arsmagicalegacy-26.1.2-1.7.0.jar` | Etherium card |
| CC: Cloud | `cccloud-1.0.0-beta.2+dev7.jar` | this mod |
| CC: Tweaked | `cc-tweaked-26.1.2-forge-1.120.0.jar` | what CC: Cloud is built on: computers, wired machine cards, Lua bridges |
| Charging Gadgets | `charginggadgets-1.16.1.jar` | Charging Station card |
| Cobblegen Galore | `cobblegengalore-26.1.2-0.2.9.jar` | Block generator card |
| Cooking for Blockheads | `cookingforblockheads-neoforge-26.1.2-26.1.2.6.jar` | Oven and Toaster cards, kitchen storage as Storage cards |
| Curios API | `curios-neoforge-15.0.0+26.1.2.jar` | the Cloud Tablet works in a Curios slot |
| DimStorage | `DimStorage-26.1.2-10.0.1.jar` | Storage and Tank cards |
| Easy Villagers | `easy-villagers-neoforge-1.1.47+26.1.2.jar` | Breeder, Iron Farm and more |
| Ender IO | `enderio-9.0.6-alpha.jar` | capacitor bank and machine cards |
| Energized Power | `energizedpower-3.0.1+26.1.x-neoforge.jar` | machine and battery cards |
| Energy Meter | `energymeter-neoforge-26.1.2-0.5.0.jar` | flow, total, 60-minute chart |
| EvilCraft | `evilcraft-26.1.2-neoforge-1.2.107.jar` | Dark Tank through the Tank card, tested; Blood Infuser and Spirit Furnace through the generic cards |
| Farming for Blockheads | `farmingforblockheads-neoforge-26.1.2-26.1.2.5.jar` | Shipping Bin card |
| Forbidden Arcanus | `forbidden_arcanus-26.1.2-2.8.0-beta.2.jar` | Hephaestus Forge card |
| FTB Chunks | `ftb-chunks-neoforge-26.1.2.8.jar` | Claims mainframe card, claims on the network map |
| FTB Essentials | `ftb-essentials-neoforge-26.1.2.5.jar` | Warps mainframe card |
| FTB Quests | `ftb-quests-neoforge-26.1.2.9.jar` | Quests mainframe card |
| FTB Ranks | `ftb-ranks-neoforge-26.1.2.4.jar` | Ranks mainframe card: ranks, power, online counts (wave 5) |
| FTB Teams | `ftb-teams-neoforge-26.1.2.4.jar` | team trust for roles, team relay |
| Functional Storage | `functionalstorage-26.1.2-1.6.4.jar` | controllers as Storage cards |
| Gateways To Eternity | `GatewaysToEternity-26.1.2-6.0.2.jar` | Gateways mainframe card |
| Generator Galore | `generatorgalore-26.1.2-1.7.0.jar` | Generator card |
| GuideME | `guideme-26.1.15-beta.jar` | the in-game handbook |
| Hostile Neural Networks | `HostileNeuralNetworks-26.1.2-7.0.0.jar` | Simulation Chamber card |
| Ice And Fire Community Edition | `iceandfire-2.2-beta.4.jar` | Dragon Forge card (wave 5) |
| IntegratedDynamics | `integrateddynamics-26.1.2-neoforge-1.36.1.jar` | Display Panels card |
| Iron Furnaces | `ironfurnaces-neoforge-26.1.2-4.4.2.jar` | Furnace card |
| Jade | `Jade-mc26.1-NeoForge-26.1.12.jar` | tooltip plugin: card state in the Jade overlay |
| Journeymap | `journeymap-neoforge-26.1.2-6.0.10.jar` | client plugin: nodes as waypoints |
| Just Dire Things | `justdirethings-1.6.11.jar` | Generator, Machine and Sensor cards |
| KubeJS | `kubejs-neoforge-26.1.2-8.0.6.jar` | script bindings |
| ME Requester | `merequester-neoforge-26.1.2-1.5.1.jar` | Requester card |
| Modular Force Field System | `mffs-5.8.39.jar` | Force Field Projector card |
| Modular Routers | `modular-routers-26.1.2.2.jar` | Router card |
| Mystical Agriculture | `MysticalAgriculture-26.1.2-9.0.10.jar` | Machine card |
| Neo Vitae | `neovitae-26.1.2-1.1.38.jar` | Ara Vitae, Master Ritual Stone, Hellfire Forge, Anima cards (wave 5) |
| Occultism | `occultism-26.1.2-neoforge-1.256.1.jar` | Dimensional Storage Actuator as Storage card; other blocks generic |
| Oritech | `oritech-2.0.0-exp11.jar` | energy storage and generator cards |
| Powah | `Powah-7.0.4-alpha.jar` | energy cell, generator and reactor cards |
| Productive Bees | `productivebees-26.1.2-15.3.0.jar` | hive and centrifuge cards |
| Productive Farming | `productivefarming-26.1.2-0.13.0.jar` | Farm card |
| Productive Metalworks | `productivemetalworks-26.1.2-1.17.1.jar` | Foundry card |
| Productive Trees | `productivetrees-26.1.2-1.3.1.jar` | Sawmill, Pollen Sifter, Stripper cards |
| Pylons | `pylons-26.1-6.0.1.jar` | Pylon card with on/off |
| QuarryPlus | `AdditionalEnchantedMiner-26.1.2-neoforge-26.12.160.jar` | Quarry and Chunk Destroyer cards |
| Railcraft Reborn | `railcraft-reborn-26.1.2-1.4.5.jar` | multiblock cards through the master block |
| Ranged Pumps | `rangedpumps-2.0.0.jar` | Pump card |
| Refined Storage | `refinedstorage-neoforge-3.2.1.jar` | RS Cloud Bridge and storage views |
| Silent Gear | `silent-gear-26.1.2-neoforge-4.2.3.jar` | Machine card (alloy makers, press, grader, salvager), Starlight Charger card (wave 5) |
| Simple Backups | `SimpleBackups-26.1.6.jar` | Backups mainframe card |
| Sophisticated Backpacks | `sophisticatedbackpacks-26.1.2-3.26.11.2198.jar` | placed backpacks as Storage cards with search (wave 5) |
| Sophisticated Storage | `sophisticatedstorage-26.1.2-1.5.121.2180.jar` | controller as Storage card |
| spark | `spark-1.10.187-neoforge.jar` | TPS, MSPT and CPU on the Server card |
| Super Factory Manager (SFM) | `Super Factory Manager (SFM)-MC26.1.2-4.34.0.jar` | Factory Manager card |
| Thaumaturge | `thaumaturge-26.1.2-NeoForge-1.0.2.jar` | Essentia, Aura Node, Runic Matrix, Crucible cards (wave 5) |
| The Aether II | `aether_ii-26.1.2-alpha.4.1-neoforge.jar` | network map: name, icon, height range |
| The Undergarden | `The_Undergarden-26.1.2-0.9.8.jar` | network map: name, icon, height range; blocks use the generic cards |
| Training Dummy | `trainingdummy-1.9.0+mc26.1.2.jar` | Training dummies mainframe card |
| Waystones | `waystones-neoforge-26.1.2-26.1.2.16.jar` | Waystones mainframe card and map overlay |
| wd's Selling Bin | `selling_bin-1.6-NEOFORGE-26.1.2.jar` | Selling Bin card |
| Witchery | `witchery-0.5.17+mc26.1.2.jar` | Altar card |

## Generic: capability or peripheral card

| Mod | Jar | Why |
| --- | --- | --- |
| Apotheosis | `Apotheosis-26.1.2-9.1.0.jar` | Gem Case exposes an item capability, so the Peripheral card lists it; the reforging, augmenting and salvaging tables are crafting screens without state |
| Aquaculture 2 | `Aquaculture-26.1.1-2.9.2.jar` | Tackle Box and Neptune's Bounty expose an item capability, Peripheral card; the rest is fish and gear |
| Auroral | `auroral-26.1.2-1.5.9.jar` | item capability on its few blocks, Peripheral card; winter content |
| Chisel | `chisel-26.1.2.19.jar` | Auto Chisel exposes an item capability, Peripheral card; the rest is decoration |
| DeepNull Reforged | `deepnull-reforged-4.2.1-beta.jar` | the Dock registers item, fluid and energy block capabilities: generic cards |
| Easy Piglins | `easy-piglins-neoforge-1.1.7+26.1.2.jar` | piglin blocks expose energy, fluid and item capabilities, generic cards; not examined further |
| ExtendedAE | `ExtendedAE-26.1-1.0.5-neoforge.jar` | machines expose energy, fluid or item capabilities; the network itself is on the ME Cloud Bridge |
| FramedBlocks | `FramedBlocks-11.4.0.jar` | decoration; framed chests expose an item capability, Peripheral card |
| LogisticsNetworks | `logisticsnetworks-26.1.2-1.13.0.jar` | talks to CC: Tweaked itself: Lua on an uplink, Peripheral card |
| Macaw's Furniture | `mcw-furniture-3.4.2-mc26.1neoforge.jar` | storage furniture exposes an item capability, Peripheral card; otherwise decoration |
| Mystical Automation | `MysticalAutomation-26.1.2-2.0.7.jar` | machines expose energy and items: Energy and Peripheral cards |
| Power Armor | `power_armor-1.0.7.jar` | compressor and modification table expose energy: Energy card |
| Refined Types | `refined-types-26.1.2-1.0.2.jar` | Network Energizer exposes energy: Energy card; its stored types are on the RS network |
| MrCrayfish's Furniture Mod: Refurbished | `refurbished_furniture-neoforge-26.1.2-1.0.23.jar` | fridges, crates and basins expose item or fluid capabilities, Peripheral and Tank cards; otherwise decoration |
| Reliquary Reincarnations | `reliquary-26.1.2-2.0.92.1563.jar` | apothecary and altar are short crafting steps, item and fluid capabilities through the generic cards |
| BBL Routers | `routers-26.1.2-2.14.2.jar` | importers and exporters expose energy, fluid and item capabilities, generic cards |
| Stellaris | `stellaris-26.1.2-neoforge-2.0.7.jar` | fluid tanks via Tank card, generators via Energy card; rockets are entities |
| Theurgy | `theurgy-26.1.2-neoforge-1.116.0.jar` | tanks via Tank card, powered blocks via Energy card; machines not examined in depth |

## Tech batch: being done by someone else

| Mod | Jar | Why |
| --- | --- | --- |
| AEInfinityBooster | `aeinfinitybooster-neoforge-26.1.2-1.0.0.57.jar` | in the tech batch |
| Building Gadgets 2 | `buildinggadgets2-1.4.6.jar` | in the tech batch |
| Cable Tiers | `cabletiers-neoforge-26.1.2-1.0.3.jar` | in the tech batch |
| Classic Pipes | `classicpipes-neoforge-26.1.2-1.1.7.jar` | in the tech batch |
| Enderthing | `Enderthing-26.1.2-1.10.2.jar` | in the tech batch |
| Extra Disks | `ExtraDisks-26.1.1.jar` | in the tech batch |
| IntegratedCrafting | `integratedcrafting-26.1.2-neoforge-1.7.1-830.jar` | in the tech batch |
| IntegratedTerminals | `integratedterminals-26.1.2-neoforge-1.10.0.jar` | in the tech batch |
| IntegratedTunnels | `integratedtunnels-26.1.2-neoforge-1.13.1.jar` | in the tech batch |
| Interdimensional Wireless Transmitter | `interdimensionalwirelesstransmitter-neoforge-26.1.2-1.0.1.jar` | in the tech batch |
| Laser Bridges | `laserbridges-26.1.2-neoforge-6.2.jar` | in the tech batch |
| Mining Gadgets | `mininggadgets-1.19.3.jar` | in the tech batch |
| Pipez | `pipez-neoforge-1.2.31+26.1.2.jar` | in the tech batch |
| Simple Magnets | `simplemagnets-1.1.12-neoforge-mc26.1.jar` | in the tech batch |
| BBL Strainers | `strainers-26.1.2-6.13.0.jar` | in the tech batch |
| Trash Cans | `trashcans-1.1.1a-neoforge-mc26.1.jar` | in the tech batch |
| Universal Grid | `universalgrid-neoforge-26.1.2-1.0.2.jar` | in the tech batch |
| Utilitarian | `utilitarian-26.1.2-0.22.0.jar` | in the tech batch |

## Batch in progress: real candidates, no card yet

| Mod | Jar | Why |
| --- | --- | --- |
| Mahou Tsukai | `mahoutsukai-26.1.2-v1.36.36.jar` | candidate: a restricted mana card for the owner, mana is a player attachment; the 70 block entities were not sorted through |
| Refined Flow Analytics | `refinedflowanalytics-neoforge-26.1.2-2.0.0.jar` | candidate: the Flow Monitor keeps inflow and outflow history in private fields; it would need its own card, outside this batch |
| Roots Classic | `RootsClassic-26.1.2-1.6.7.jar` | candidate: altar ritual and imbuer progress are public getters; no card yet |
| Spice of Life: Carrot Edition | `solcarrot-26.1-1.19.0.jar` | candidate: a restricted food-variety card; `FoodList` needs the player object, so only while the owner is online |
| Starcatcher | `starcatcher-3.3.8.1-NEOFORGE-26.1.2.jar` | candidate: a restricted card with the owner's catches; its tackle box already exposes an item capability, Peripheral card |

## Nothing meaningful

| Mod | Jar | Why |
| --- | --- | --- |
| AE2 Import Export Card | `ae2importexportcard-26.1.2-2.3.1.jar` | items only, nothing placed that keeps state |
| AE2NetworkAnalyzer | `AE2NetworkAnalyzer-26.1-1.0.0-neoforge.jar` | no block entity: an item that shows network data to the player holding it |
| AE2WTLib | `ae2wtlib-26.1.2-beta.jar` | items only, nothing placed that keeps state |
| AllTheCompressed | `allthecompressed-26.1.2-5.0.1.jar` | decoration |
| AllTheOres | `alltheores-4.0.4.jar` | worldgen or structures, no block state to read |
| AllTheTweaks | `allthetweaks-26.1.2-3.1.1.jar` | pack recipes and a few items |
| Apothic Attributes | `ApothicAttributes-26.1.2-3.1.0.jar` | library |
| AppleSkin | `appleskin-neoforge-mc26.1-3.0.9.jar` | client side only |
| Applied Construction Sticks | `appliedsticks-26.1.2-2.0.4.jar` | items only, nothing placed that keeps state |
| Architectury | `architectury-neoforge-20.1.17.jar` | library |
| Artifacts | `artifacts-neoforge-15.1.3.jar` | items only, nothing placed that keeps state |
| Athena | `athena-4.7.3.jar` | library |
| Atlas API | `atlas_api-26.1.2-1.2.0.jar` | library |
| AttributeFix | `AttributeFix-neoforge-MC26.1.2-26.1.2.3.jar` | performance or fix, no game content |
| Balm | `balm-neoforge-26.1.2-26.1.2.15.jar` | library |
| Baubley Heart Canisters | `baubley-heart-canisters-26.1.2-1.7.3.jar` | items only, nothing placed that keeps state |
| BBL Core | `bblcore-26.1.2-12.6.12.jar` | library |
| Better Advanced Tooltips | `better-advanced-tooltips-2601.1.0-build.11.jar` | client side only |
| Better Advancements | `BetterAdvancements-NeoForge-26.1.2-0.6.0.76.jar` | client side only |
| Better Compatibility Checker | `better-compatability-checker-neoforge-26.1.2.1.jar` | performance or fix, no game content |
| Camol | `camol-26.1.2-0.4.1.jar` | camouflage blocks: decoration |
| Clean Swing | `cleanswing-1.9-26.1.jar` | client side only |
| Clickable Advancements mod | `clickadv-26.1-3.9.jar` | client side only |
| Cloth Config v26.1 API | `cloth-config-26.1.154.jar` | library |
| Clumps | `Clumps-neoforge-26.1.2-26.1.2.1.jar` | performance or fix, no game content |
| Comforts | `comforts-neoforge-15.0.0+26.1.2.jar` | sleeping bags and hammocks, used like beds |
| CommonCapabilities | `commoncapabilities-26.1.2-neoforge-2.11.8-377.jar` | library |
| Configured | `configured-neoforge-26.1.2-2.7.5.jar` | client side only |
| CTM | `ctm-26.1.2.11.jar` | library |
| Construction Sticks | `ConstructionSticks-26.1.2-3.2.1.jar` | items only, nothing placed that keeps state |
| Controlling | `Controlling-neoforge-26.1.2-26.1.2.7.jar` | client side only |
| Crafting on a Stick | `crafting_on_a_stick-neoforge-26.1-1.1.jar` | items only, nothing placed that keeps state |
| Crafting Tweaks | `craftingtweaks-neoforge-26.1.2-26.1.2.3.jar` | client side only |
| Crash Assistant | `CrashAssistant-neoforge-26.1-1.11.12.jar` | client side only |
| Crash Utilities | `crashutilities-10.0.1.jar` | performance or fix, no game content |
| Cristel Lib | `cristellib-neoforge-26.1.2-3.1.13.jar` | library |
| Croptopia | `croptopia-neoforge-26.1.2-4.3.2.jar` | crops and food, no machines |
| Crystalix | `crystalix-4.1.0+26.1.2.jar` | decoration |
| Cucumber Library | `Cucumber-26.1.2-9.0.6.jar` | library |
| Cupboard mod | `cupboard-26.1-4.1.jar` | library |
| Cyclops Core | `cyclopscore-26.1.2-neoforge-1.31.3.jar` | library |
| Default Options | `defaultoptions-neoforge-26.1.2-26.1.2.2.jar` | client side only |
| Deimos | `deimos-26.1.2-neoforge-2.7.jar` | library |
| Drippy Loading Screen | `drippyloadingscreen_neoforge_3.1.5_MC_26.1.2.jar` | client side only |
| Dungeon Crawl | `DungeonCrawl-NeoForge-26.1-2.3.17.jar` | worldgen or structures, no block state to read |
| Dyenamics | `dyenamics-26.1.2-3.5.0.jar` | decoration |
| EdivadLib | `EdivadLib-26.1.2-4.0.1.jar` | library |
| ElevatorMod | `elevatorid-neoforge-26.1-1.16.2.jar` | no cabin, a column of teleport pads; calling a floor would teleport a player from the dashboard |
| EnchantmentDescriptions | `EnchantmentDescriptions-neoforge-MC26.1.2-26.1.2.6.jar` | client side only |
| Ender IO - Endergy | `enderio_endergy-9.0.1-alpha.jar` | items only, nothing placed that keeps state |
| EpheroLib | `epherolib-neoforge-26.1-1.3.0.jar` | library |
| Epitaphs | `epitaphs-2.4.0+26.1.2.jar` | graves: a block with a text |
| Euphoria Patcher | `EuphoriaPatcher-1.10.5-r5.9.3-neoforge.jar` | client side only |
| ExoConfig | `ExoConfig-26.1.2-neoforge-0.3.5.jar` | library |
| Explorer's Compass | `ExplorersCompass-26.1-3.3.0-neoforge.jar` | items only, nothing placed that keeps state |
| Extreme Sound Muffler | `ExtremeSoundMuffler-4.04_NeoForge-26.1.jar` | client side only |
| Fancy Tab Sections | `fancytabsections-6.2-NEOFORGE-26.1.2.jar` | library |
| FancyMenu | `fancymenu_neoforge_3.9.14_MC_26.1.2.jar` | client side only |
| FastFurnace | `FastFurnace-26.1.2-10.0.0.jar` | performance or fix, no game content |
| Fast Suite | `FastSuite-26.1.2-7.0.0.jar` | performance or fix, no game content |
| Fast Workbench | `FastWorkbench-26.1.2-10.0.0.jar` | performance or fix, no game content |
| Ferrite Core | `ferritecore-9.0.0-neoforge.jar` | performance or fix, no game content |
| Fireproof Boats | `fireproofboats-26.1.2-1.0.4.jar` | items only, nothing placed that keeps state |
| FlickerFix | `flickerfix-26.1-7.0.0.jar` | performance or fix, no game content |
| Forge Config API Port | `ForgeConfigAPIPort-v26.1.5-mc26.1.x-NeoForge.jar` | library |
| Framework | `framework-neoforge-26.1.2-0.13.23.jar` | library |
| FTB Filter System | `ftb-filter-system-neoforge-26.1.2.2.jar` | an item filter other mods read; nothing runs on its own |
| FTB Library | `ftb-library-neoforge-26.1.2.9.jar` | library |
| FTB Ultimine | `ftb-ultimine-neoforge-26.1.2.5.jar` | a client-held key and a per-player toggle, no server state worth a card |
| FTB XMod Compat | `ftb-xmod-compat-neoforge-26.1.2.4.jar` | glue between FTB mods |
| GeckoLib 5 | `geckolib-neoforge-26.1.2-5.5.2.jar` | library |
| GeOre | `GeOre-26.1.2-8.1.0.jar` | worldgen or structures, no block state to read |
| Get It Together, Drops! | `getittogetherdrops-neoforge-26.1.1-1.5.jar` | client side only |
| Glodium | `Glodium-26.1-1.2-neoforge.jar` | library |
| Hellish Trials | `HellishTrials-neoforge-1.0.7.jar` | worldgen or structures, no block state to read |
| imfast | `imfast-NEOFORGE-26.1.2-1.0.3.jar` | performance or fix, no game content |
| ImmediatelyFast | `ImmediatelyFast-NeoForge-1.15.3+26.1.jar` | performance or fix, no game content |
| In World Recipes | `inworldrecipes-26.1.2-2.6.1.jar` | recipes |
| Inventory Essentials | `inventoryessentials-neoforge-26.1.2-26.1.2.7.jar` | client side only |
| Iris | `iris-neoforge-1.11.4+mc26.1.2.jar` | client side only |
| IrisSearch | `IrisSearch-1.8.3-neoforge.jar` | client side only |
| Iron Jetpacks | `IronJetpacks-26.1.2-9.0.3.jar` | items only, nothing placed that keeps state |
| Iron's Arms 'n Artifice | `irons_artifice-26.1.2-1.1.0.jar` | items only, nothing placed that keeps state |
| Iron's Gems 'n Jewelry | `irons_jewelry-26.1.2-2.0.2.1.jar` | items only, nothing placed that keeps state |
| Iron's Lib | `irons_lib-26.1.2-2.2.0.jar` | library |
| Item Collectors | `itemcollectors-1.1.12a-neoforge-mc26.1.jar` | no inventory, no counter, no on/off: only a range and a filter |
| Just Enough Items | `jei-26.1.2-neoforge-29.47.0.109.jar` | client side only |
| Just Enough Stuff | `jeistuff-26.1.2-1.3.0.jar` | client side only |
| Jonn's Trophies | `trophymanager-26.1.2-3.0.0.jar` | a trophy is a mounted mob or item, nothing changes |
| Jumpy Boats | `jumpboat-26.1.2-1.0.6.jar` | one movement tweak |
| Jupiter | `jupiter-2.4.3-26.1.2-universal.jar` | library |
| Just Enough Archaeology | `jearchaeology-26.1.2-1.3.0.jar` | client side only |
| Just Enough Professions (JEP) | `JustEnoughProfessions-neoforge-26.1.2-11.0.2.jar` | client side only |
| Just Enough Recipe Sharing (JERS) | `justenoughrecipesharing-neoforge-26.1.2-1.1.2.jar` | client side only |
| Just Zoom | `justzoom_neoforge_2.1.4_MC_1.21.11.jar` | client side only |
| KeyBind Bundles | `keybindbundles-2.0.0.jar` | client side only |
| KeybindsPurger | `KeybindsPurger-1.4.0-neoforge-26.1.2.jar` | client side only |
| Konkrete | `konkrete_neoforge_1.10.1_MC_26.1.1.jar` | library |
| Kotlin for Forge | `kotlinforforge-6.4.0-all.jar` | library |
| LaserIO | `laserio-1.10.2.jar` | a node holds cached card settings and has no throughput counter |
| wd's Tooltips Library | `libtooltips-2.3-NEOFORGE-26.1.2.jar` | library |
| Lithostitched | `lithostitched-1.8.1-neoforge-26.1.jar` | library |
| Lootr | `lootr-neoforge-26.1.2-1.23.38.123.jar` | contents are per player and only exist once a player opens the chest; showing them would spoil the loot |
| Luminax | `luminax-2.0.1+26.1.2.jar` | decoration |
| Macaw's Bridges | `mcw-bridges-3.1.2-mc26.1neoforge.jar` | decoration |
| Macaw's Doors | `mcw-doors-1.1.5-mc26.1neoforge.jar` | decoration |
| Macaw's Fences and Walls | `mcw-fences-1.2.1-mc26.1neoforge.jar` | decoration |
| Macaw's Lights and Lamps | `mcw-lights-1.1.5-mc26.1neoforge.jar` | decoration |
| Macaw's Paths and Pavings | `mcw-paths-1.1.1-mc26.1neoforge.jar` | decoration |
| Macaw's Roofs | `mcw-roofs-2.3.2-mc26.1neoforge.jar` | decoration |
| Macaw's Stairs and Balconies | `mcw-stairs-1.0.2-mc26.1neoforge.jar` | decoration |
| Macaw's Trapdoors | `mcw-trapdoors-1.1.5-mc26.1neoforge.jar` | decoration |
| Macaw's Windows | `mcw-windows-2.4.2-mc26.1neoforge.jar` | decoration |
| MaxHealthFix | `MaxHealthFix-neoforge-MC26.1.2-26.1.2.3.jar` | performance or fix, no game content |
| Measurements | `Measurements-neoforge-26.1-4.0.0.jar` | items only, nothing placed that keeps state |
| Melody | `melody_neoforge_1.0.16_MC_26.1.1.jar` | library |
| Mezz Config | `mezz_config-26.1.2-neoforge-0.6.8-standalone.jar` | library |
| ModernFix | `modernfix-neoforge-5.27.24+mc26.1.2.jar` | performance or fix, no game content |
| Modonomicon | `modonomicon-26.1.2-neoforge-2.17.1.jar` | library |
| More Dragon Eggs | `moredragoneggs-6.0.jar` | one world rule |
| More Overlays Updated | `moreoverlays-1.24.4-mc26.1.2-neoforge.jar` | client side only |
| More Red | `morered-26.1.2.3.jar` | logic gates, wires and item tubes: state is the redstone signal itself or items in transit, nothing stored to report |
| Mouse Tweaks | `MouseTweaks-neoforge-mc26.1-2.31.jar` | client side only |
| Mystical Agradditions | `MysticalAgradditions-26.1.2-9.0.4.jar` | no machine or block entity |
| Mystical Agriculture Tiered Crystals | `matc-1.8.0.jar` | items only, nothing placed that keeps state |
| Mystical Customization | `MysticalCustomization-26.1.2-7.0.0.jar` | a pack maker's data tool |
| Nature's Compass | `NaturesCompass-26.1-3.3.0-neoforge.jar` | items only, nothing placed that keeps state |
| NetherPortalFix | `netherportalfix-neoforge-26.1-26.1.0.1.jar` | performance or fix, no game content |
| No Chat Reports | `NoChatReports-NEOFORGE-26.1-v2.19.0.jar` | performance or fix, no game content |
| No More Craters | `nomorecraters-1.0.0.jar` | performance or fix, no game content |
| Nullscape | `Nullscape_26.1.2_v1.2.20.jar` | worldgen or structures, no block state to read |
| Oracle Index | `oracle_index-neoforge-2.0.0.jar` | client side only |
| Patchouli | `patchouli-neoforge-26.1-94.jar` | library |
| Pipe Connector | `pipe_connector-neoforge-0.6.8.jar` | items only, nothing placed that keeps state |
| Placebo | `Placebo-26.1.2-10.0.2.jar` | library |
| Porting Dead Libs | `portingdeadlibs-1.1.14-26.1.jar` | library |
| Potentials | `potentials-neoforge-26.1.2-0.11.1.jar` | library |
| PotionsMaster | `potionsmaster-26.1.2.100-2.0.0.jar` | the mortar is a plain block without a block entity; the rest is potions |
| PrickleMC | `PrickleMC-neoforge-MC26.1.2-26.1.2.6.jar` | library |
| Refined Construction Sticks | `refinedsticks-26.1.2-2.0.7.jar` | items only, nothing placed that keeps state |
| Regions Unexplored | `regions-unexplored-0.7.0+beta2-neoforge.jar` | worldgen or structures, no block state to read |
| Rhino | `rhino-2101.2.8-build.91.jar` | library |
| Refined Storage - Curios Integration | `refinedstorage-curios-integration-2.0.1.jar` | integration glue |
| Refined Fluid Substitution | `refinedfluidsubstitution-neoforge-26.1.2-1.0.1.jar` | adds a pattern type |
| Refined Storage - JEI Integration | `refinedstorage-jei-integration-neoforge-2.0.1.jar` | integration glue |
| Refined Storage - Quartz Arsenal | `refinedstorage-quartz-arsenal-neoforge-2.0.6.jar` | items only, nothing placed that keeps state |
| Scalable Cat's Force | `ScalableCatsForce-NeoForge-6.1.1-mc26.1.2-3.9.0-all.jar` | library |
| Searchables | `Searchables-neoforge-26.1.2-1.0.2.jar` | library |
| Shogi | `shogi-neoforge-26.1.2-26.1.2.12.jar` | library |
| Silent Gear Metalworks | `sgearmetalworks-26.1.2-1.6.0.jar` | recipes and materials for the Productive Metalworks foundry; the classes are datagen, no block entity of its own |
| Silent's Gems | `silentgems-26.1.2-neoforge-5.1.4.jar` | gems and teleporters; a teleporter only holds a destination, no power or progress to watch |
| Silent Lib | `silent-lib-26.1.2-neoforge-11.2.0.jar` | library |
| Sky Aesthetics | `sky_aesthetics-neoforge-2.1.4.jar` | client side only |
| Sodium | `sodium-neoforge-0.9.2+mc26.1.2.jar` | performance or fix, no game content |
| Sodium Extra | `sodium-extra-neoforge-0.9.4+mc26.1.2.jar` | performance or fix, no game content |
| Sophisticated Core | `sophisticatedcore-26.1.2-1.5.13.2403.jar` | library |
| Sophisticated Storage In Motion | `sophisticatedstorageinmotion-26.1.2-0.10.37.378.jar` | no block entity: storage on moving entities, nothing for an uplink to sit on |
| Spider Overhaul | `SpiderOverhaul-0.0.6-NeoForge-v26.1.jar` | mobs |
| Step Crafter | `stepcrafter-neoforge-26.1.2-1.0.3.jar` | an RS addon without a capability of its own; its crafting shows on the RS network |
| SuperMartijn642's Config Library | `supermartijn642configlib-1.1.8-neoforge-mc26.1.jar` | library |
| SuperMartijn642's Core Lib | `supermartijn642corelib-1.1.25-neoforge-mc26.1.jar` | library |
| Tectonic | `tectonic-3.0.28-neoforge-26.1.jar` | worldgen or structures, no block state to read |
| Terralith | `Terralith_26.1_v2.6.1_Neoforge.jar` | worldgen or structures, no block state to read |
| Theurgy KubeJS | `theurgy_kubejs-26.1.2-neoforge-1.6.0.jar` | script support |
| Titanium | `titanium-26.1.2-4.0.12.jar` | library |
| Toast Control | `ToastControl-26.1.2-10.0.0.jar` | client side only |
| Tool Belt | `ToolBelt-26.1.2-2.9.5.jar` | items only, nothing placed that keeps state |
| Towns and Towers | `t_and_t-fabric-neoforge-1.13.11.jar` | worldgen or structures, no block state to read |
| Transmog | `transmog-neoforge-1.8.0+26.1.jar` | decoration |
| TrashSlot | `trashslot-neoforge-26.1.2-26.1.2.4.jar` | client side only |
| Underground Villages Mod | `UndergroundVillages-neoforge-26.1.2-11.0.0.jar` | worldgen or structures, no block state to read |
| Underwater Village | `underwater_village-1.0.0 Neoforge 26.1.2.jar` | worldgen or structures, no block state to read |
| Uranus | `uranus-3.0-alpha.1.jar` | library |
| Valhelsia Core | `valhelsia_core-neoforge-26.1.2-1.1.6.jar` | library |
| Villages&Pillages | `villagesandpillages-neoforge-2.0.0+mc26.1.2.jar` | worldgen or structures, no block state to read |
| When Dungeons Arise | `DungeonsArise-26.1.2-2.1.71-release.jar` | worldgen or structures, no block state to read |
| Wither Skeleton Tweaks | `WitherSkeletonTweaks-26.1.2-11.0.1.jar` | mob tweak |
| Wits | `wits-1.4.3+26.1-neoforge.jar` | client side only |
| Yeetus Experimentus | `yeetusexperimentus-neoforge-99.0.0.jar` | performance or fix, no game content |
