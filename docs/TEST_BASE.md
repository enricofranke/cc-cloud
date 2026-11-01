# Test base: what was really run

World `showcase-dev11`, jar `cccloud-1.0.0-beta.2+dev12` (branch base-fixes). Every line below comes from `node tools/showcase/dashboard.mjs verify` in the website-connect repo (RCON plus the dashboard websocket). It ran on a local server without a real client, so nothing here was seen on screen.

## Core base

| Check | Result |
|---|---|
| reactor running and producing | pass |
| main cell rises from the reactor | pass |
| reactor card matches block data | pass |
| ME stock visible | pass |
| ME fluids visible | pass |
| RS stock visible | pass |
| several CPU sizes visible | pass |
| scene from the dashboard changes the real lamps | pass |
| lamp group from the dashboard switches real lamps | pass |
| rule fires from the hall switch and moves the lamp group | pass |
| rule alert in the alert list | pass |
| multi-step craft with a furnace step finishes and the items arrive | pass |
| CPU status visible while the job runs | pass |
| assembler chain with a subcraft finishes | pass |
| not enough items shows what is missing and how many are craftable | pass |
| order anyway leaves a waiting job | pass |
| stuck craft is marked | pass |
| stuck craft raises an alert | pass |
| farm and mob data moves | pass |
| relay route works | pass |
| Nether outpost online | pass |
| Cloud Probe reads the generator in the wall | pass |
| Cloud Display shows a card | pass |
| Lua canvas program runs and redraws | pass |
| train runs the ring | pass |
| train data reaches the dashboard | pass |

Not in the ATM11 pack: Create, Mekanism, Industrial Foregoing. So there are no Create trains, Mekanism Joules or chemical tanks to test. The railway uses Railcraft Reborn (creative locomotive, tank and chest cars) on vanilla rail, because strap iron track was too slow. There is no train card; train data comes from detector rails and CC computers at six stations.

## Mod street

51 buildings, 158 machines placed, 154 with data on the dashboard, 14 measured running during a 75 s window. Only 5 buildings had a machine running in that window: generatorgalore, mysticalagriculture, ironfurnaces, energizedpower, enderio. A machine with data but no movement is listed as idle, not as done. The other buildings are built and report data but sit idle, mostly because they have no recipe input or fuel. Machines fed by the fuel phase can also finish and stop before the window starts.

Real chains: ironfurnaces and energizedpower turn raw iron into ingots in the output chest. The enderio chain delivers nothing to the chest: raw iron enters but the output is not pulled out by the hopper. Railcraft: iron tank formed, coke oven turned 64 coal into 32,000 mB creosote, blast furnace has the right shape but its forming was not confirmed.

| Mod | Machines | Data | Running | Chain | Idle / no data |
|---|---|---|---|---|---|
| powah | 8 | 8 | 0 |  | energy_cell_nitro, furnator_nitro, magmator_nitro, thermo_generator_nitro |
| generatorgalore | 6 | 6 | 1 |  | gold_generator, magmatic_generator, emerald_generator, netherite_generator |
| energymeter | 1 | 1 | 0 |  | meter |
| pylons | 4 | 4 | 0 |  | expulsion_pylon, infusion_pylon, harvester_pylon, interdiction_pylon |
| oritech | 8 | 8 | 0 |  | basic_generator, bio_generator, lava_generator, fuel_generator |
| productivemetalworks | 4 | 4 | 0 |  | white_foundry_controller, white_foundry_tank, white_foundry_capacitor, orange_foundry_controller |
| cobblegengalore | 4 | 4 | 0 |  | block_gen_stone, block_gen_copper, block_gen_iron, block_gen_gold |
| quarryplus | 3 | 3 | 0 |  | quarry, adv_quarry, debug_storage |
| sfm | 4 | 4 | 0 |  | manager, buffer, water_tank, tunnelled_manager |
| modularrouters | 1 | 1 | 0 |  | modular_router |
| justdirethings | 4 | 4 | 0 |  | itemcollector, generatort1, sensort1, experienceholder |
| charginggadgets | 1 | 1 | 0 |  | charging_station |
| classicpipes | 1 | 1 | 0 |  | oak_fluid_pipe |
| rangedpumps | 1 | 1 | 0 |  | pump |
| mffs | 2 | 2 | 0 |  | projector, coercion_deriver |
| integrateddynamics | 2 | 2 | 0 |  | energy_battery, drying_basin |
| stellaris | 2 | 2 | 0 |  | solar_panel, fluid_tank_t1 |
| power_armor | 1 | 1 | 0 |  | compressor |
| arsmagicalegacy | 4 | 3 | 0 |  | obelisk, celestial_prism, black_aurem / altar_core |
| witchery | 2 | 2 | 0 |  | altar, cauldron |
| theurgy | 2 | 1 | 0 |  | mercury_flux_emitter / liquefaction_cauldron |
| thaumaturge | 2 | 2 | 0 |  | crucible, vis_generator |
| forbidden_arcanus | 1 | 1 | 0 |  | hephaestus_forge |
| evilcraft | 1 | 0 | 0 |  |  / blood_stain |
| neovitae | 2 | 2 | 0 |  | ara_vitae, blood_battery |
| mysticalagriculture | 4 | 4 | 1 |  | furnace, seed_reprocessor, soul_extractor |
| mysticalautomation | 1 | 1 | 0 |  | infuser |
| productivetrees | 4 | 4 | 0 |  | stripper, advanced_alder_beehive, sawmill, pollen_sifter |
| productivebees | 4 | 4 | 0 |  | centrifuge, honey_generator, dragon_egg_hive, powered_centrifuge |
| productivefarming | 2 | 2 | 0 |  | farm_controller, watering_trough |
| agritechevolved | 4 | 4 | 0 |  | basic_acacia_planter, advanced_planter, biomass_burner, basic_bamboo_planter |
| farmingforblockheads | 1 | 1 | 0 |  | shipping_bin |
| easy_villagers | 3 | 3 | 0 |  | farmer, breeder, iron_farm |
| hostilenetworks | 2 | 2 | 0 |  | sim_chamber, loot_fabricator |
| selling_bin | 1 | 1 | 0 |  | selling_bin |
| cookingforblockheads | 4 | 4 | 0 |  | toaster, milk_jar, blue_oven, blue_fridge |
| ae2 | 3 | 3 | 0 |  | inscriber, sky_stone_tank, me_cloud_bridge |
| advanced_ae | 4 | 4 | 0 |  | quantum_unit, reaction_chamber, quantum_core, quantum_storage_128 |
| extendedae | 1 | 1 | 0 |  | crystal_assembler |
| merequester | 1 | 1 | 0 |  | requester |
| refinedstorage | 2 | 2 | 0 |  | white_controller, rs_cloud_bridge |
| cabletiers | 1 | 1 | 0 |  | elite_interface |
| functionalstorage | 3 | 3 | 0 |  | fluid_1, storage_controller, framed_storage_controller |
| sophisticatedstorage | 1 | 1 | 0 |  | controller |
| dimstorage | 2 | 2 | 0 |  | dimensional_chest, dimensional_tank |
| trashcans | 2 | 2 | 0 |  | liquid_trash_can, energy_trash_can |
| railcraft | 5 | 5 | 0 |  | solid_fueled_firebox, fluid_fueled_firebox, coke_oven_bricks, white_iron_tank_wall |
| occultism | 4 | 3 | 0 |  | storage_controller, storage_controller_stabilized, storage_controller_dark / dimensional_storage_actuator |
| ironfurnaces | 8 | 8 | 5 | moved | netherite_furnace, allthemodium_furnace, heater |
| energizedpower | 10 | 10 | 4 | moved | crusher, compressor, metal_press, induction_smelter |
| enderio | 10 | 10 | 3 | not moved | alloy_smelter, painting_machine, slice_and_splice, soul_binder |

## Energy and fluids against the capability

125 machines with an energy or fluid capability: 72 pass, 3 fail, 50 with a capability the card does not show. Each machine was compared twice, before and after changing the amount, with the block read on both sides of the dashboard snapshot. Tolerance is the display rounding. Net flow was compared against the stored amount over 10 s on the machines with a flow label.

No units other than FE and mB appeared. Sided: sfm:tunnelled_manager (up only) and energymeter:meter (west differs). Multi-tank: quarryplus:debug_storage, oritech:steam_engine, advanced_ae:reaction_chamber.

| Machine | Block | Result | Expected energy | Shown energy | Expected fluid | Shown fluid | After change: expected | After change: shown |
|---|---|---|---|---|---|---|---|---|
| s_powah_0 | powah:energy_cell_nitro | fail | [35000000, 2000000000] | 33.0 M / 2.00 G FE |  |  | [49000000, 2000000000] | 47.0 M / 2.00 G FE |
| s_powah_1 | powah:furnator_nitro | pass | [40000000, 40000000] | 40.0 M / 40.0 M FE |  |  | [40000000, 40000000] | 40.0 M / 40.0 M FE |
| s_powah_2 | powah:magmator_nitro | not shown | [1290000, 40000000] | 1.29 M / 40.0 M FE | [[, 0, 4000]] | not shown | [1550000, 40000000] | 1.55 M / 40.0 M FE |
| s_powah_3 | powah:thermo_generator_nitro | not shown | [320000, 40000000] | 320 k / 40.0 M FE | [[minecraft:water, 800, 4000]] | not shown | [480000, 40000000] | 480 k / 40.0 M FE |
| s_powah_6 | powah:energy_hopper_nitro | pass | [1972000000, 2000000000] | 1.97 G / 2.00 G FE |  |  | [1958000000, 2000000000] | 1.96 G / 2.00 G FE |
| s_powah_7 | powah:energy_discharger_nitro | pass | [800000, 2000000] | 800 k / 2.00 M FE |  |  | [1200000, 2000000] | 1.20 M / 2.00 M FE |
| s_generatorgalore_0 | generatorgalore:gold_generator | pass | [18000, 30000] | 18.0 k / 30.0 k FE |  |  | [13920, 30000] | 15.5 k / 30.0 k FE |
| s_generatorgalore_1 | generatorgalore:magmatic_generator | not shown | [493800, 500000] | 496 k / 500 k FE | [[minecraft:lava, 4624, 10000]] | not shown | [401200, 500000] | 404 k / 500 k FE |
| s_generatorgalore_2 | generatorgalore:emerald_generator | pass | [455360, 500000] | 455 k / 500 k FE |  |  | [355360, 500000] | 355 k / 500 k FE |
| s_generatorgalore_3 | generatorgalore:netherite_generator | pass | [910720, 1000000] | 911 k / 1.00 M FE |  |  | [726080, 1000000] | 743 k / 1.00 M FE |
| s_generatorgalore_4 | generatorgalore:halitosis_generator | pass | [360320, 9800000] | 367 k / 9.80 M FE |  |  | [7680, 9800000] | 16.0 k / 9.80 M FE |
| s_generatorgalore_5 | generatorgalore:netherstar_generator | pass | [9800000, 9800000] | 9.80 M / 9.80 M FE |  |  | [8085520, 9800000] | 8.33 M / 9.80 M FE |
| s_energymeter_0 | energymeter:meter | pass |  |  |  |  |  |  |
| s_oritech_0 | oritech:basic_generator | pass | [100000, 100000] | 100 k / 100 k FE |  |  | [100000, 100000] | 100 k / 100 k FE |
| s_oritech_1 | oritech:bio_generator | pass | [0, 250000] | 0 / 250 k FE |  |  | [0, 250000] | 0 / 250 k FE |
| s_oritech_2 | oritech:lava_generator | not shown | [0, 250000] | 0 / 250 k FE | [[minecraft:lava, 2560, 4000]] | not shown | [0, 250000] | 0 / 250 k FE |
| s_oritech_3 | oritech:fuel_generator | not shown | [0, 1000000] | 0 / 1.00 M FE | [[minecraft:lava, 2560, 4000]] | not shown | [0, 1000000] | 0 / 1.00 M FE |
| s_oritech_4 | oritech:steam_engine | not shown | [0, 250000] | 0 / 250 k FE | [[, 0, 8000], [, 0, 8000]] | not shown | [0, 250000] | 0 / 250 k FE |
| s_oritech_5 | oritech:large_storage | pass | [20000000, 20000000] | 20.0 M / 20.0 M FE |  |  | [20000000, 20000000] | 20.0 M / 20.0 M FE |
| s_oritech_6 | oritech:portable_energy_storage | pass | [1000000, 1000000] | 1.00 M / 1.00 M FE |  |  | [1000000, 1000000] | 1.00 M / 1.00 M FE |
| s_oritech_7 | oritech:energy_pipe_connection | fail | [25000, 25000] | 15.0 k / 25.0 k FE |  |  | [25000, 25000] | 10.0 k / 25.0 k FE |
| s_productivemetalworks_1 | productivemetalworks:white_foundry_tank | pass |  |  | [[minecraft:water, 2560, 4000]] | [2.56 k / 4.00 k mB] | [[minecraft:water, 2048, 4000]] | [2.05 k / 4.00 k mB] |
| s_productivemetalworks_2 | productivemetalworks:white_foundry_capacitor | pass | [40000, 40000] | 40.0 k / 40.0 k FE |  |  | [40000, 40000] | 40.0 k / 40.0 k FE |
| s_quarryplus_0 | quarryplus:quarry | not shown | [10000, 10000] | not shown |  |  | [10000, 10000] | not shown |
| s_quarryplus_1 | quarryplus:adv_quarry | not shown | [50000, 50000] | not shown |  |  | [50000, 50000] | not shown |
| s_quarryplus_2 | quarryplus:debug_storage | pass |  |  | [[minecraft:water, 2560, 9223372036854776000], [minecraft:lava, 1000, 9223372036854776000], [, 0, 9223372036854776000]] | [3.56 k / 9.22 E mB] | [[minecraft:water, 2048, 9223372036854776000], [minecraft:lava, 2000, 9223372036854776000], [, 0, 9223372036854776000]] | [4.05 k / 9.22 E mB] |
| s_sfm_1 | sfm:buffer | pass | [429496729, 2147483647] | 429 M / 2.15 G FE |  |  | [858993458, 2147483647] | 859 M / 2.15 G FE |
| s_sfm_2 | sfm:water_tank | pass |  |  | [[minecraft:water, 0, 0]] | [0 / 0 mB] | [[minecraft:water, 0, 0]] | [0 / 0 mB] |
| s_sfm_3 | sfm:tunnelled_manager | not shown | [1996000000, 2000000000] | not shown |  |  | [1994000000, 2000000000] | not shown |
| s_modularrouters_0 | modularrouters:modular_router | pass |  |  |  |  |  |  |
| s_justdirethings_1 | justdirethings:generatort1 | not shown | [890580, 1000000] | not shown |  |  | [701860, 1000000] | not shown |
| s_justdirethings_3 | justdirethings:experienceholder | not shown |  |  | [[, 0, 2147483647]] | not shown | [[, 0, 2147483647]] | not shown |
| s_charginggadgets_0 | charginggadgets:charging_station | not shown | [1000000, 1000000] | not shown |  |  | [1000000, 1000000] | not shown |
| s_classicpipes_0 | classicpipes:oak_fluid_pipe | pass |  |  | [[, 0, 1000]] | [0 / 1.00 k mB] | [[, 0, 1000]] | [0 / 1.00 k mB] |
| s_rangedpumps_0 | rangedpumps:pump | not shown | [32000, 32000] | not shown | [[, 0, 32000]] | not shown | [32000, 32000] | not shown |
| s_mffs_0 | mffs:projector | pass |  |  | [[, 0, 500000]] | [0 / 500 k mB] | [[, 0, 500000]] | [0 / 500 k mB] |
| s_mffs_1 | mffs:coercion_deriver | not shown | [0, 1500000] | 0 / 1.50 M FE | [[, 0, 30000]] | not shown | [0, 1500000] | 0 / 1.50 M FE |
| s_integrateddynamics_0 | integrateddynamics:energy_battery | pass | [1000000, 1000000] | 1.00 M / 1.00 M FE |  |  | [1000000, 1000000] | 1.00 M / 1.00 M FE |
| s_integrateddynamics_1 | integrateddynamics:drying_basin | pass |  |  | [[minecraft:water, 640, 1000]] | [640 / 1.00 k mB] | [[minecraft:water, 512, 1000]] | [512 / 1.00 k mB] |
| s_stellaris_0 | stellaris:solar_panel | pass | [12523, 12800] | 12.6 k / 12.8 k FE |  |  | [10151, 12800] | 10.2 k / 12.8 k FE |
| s_stellaris_1 | stellaris:fluid_tank_t1 | pass |  |  | [[minecraft:water, 2560, 5000]] | [2.56 k / 5.00 k mB] | [[minecraft:water, 2048, 5000]] | [2.05 k / 5.00 k mB] |
| s_power_armor_0 | power_armor:compressor | pass | [16000, 16000] | 16.0 k / 16.0 k FE |  |  | [16000, 16000] | 16.0 k / 16.0 k FE |
| s_witchery_1 | witchery:cauldron | pass |  |  | [[minecraft:water, 640, 1000]] | [640 / 1.00 k mB] | [[minecraft:water, 512, 1000]] | [512 / 1.00 k mB] |
| s_theurgy_1 | theurgy:mercury_flux_emitter | pass | [1000, 1000] | 1.00 k / 1.00 k FE |  |  | [1000, 1000] | 1.00 k / 1.00 k FE |
| s_thaumaturge_0 | thaumaturge:crucible | pass |  |  | [[minecraft:water, 640, 1000]] | [640 / 1000 mB] | [[minecraft:water, 512, 1000]] | [512 / 1000 mB] |
| s_thaumaturge_1 | thaumaturge:vis_generator | pass | [1000, 1000] | 1.00 k / 1.00 k FE |  |  | [1000, 1000] | 1.00 k / 1.00 k FE |
| s_neovitae_0 | neovitae:ara_vitae | pass |  |  | [[, 0, 10000], [, 0, 1000], [, 0, 1000]] | [0 / 10.0 k mB] | [[, 0, 10000], [, 0, 1000], [, 0, 1000]] | [0 / 10.0 k mB] |
| s_neovitae_1 | neovitae:blood_battery | pass | [10000000, 10000000] | 10.0 M / 10.0 M FE |  |  | [10000000, 10000000] | 10.0 M / 10.0 M FE |
| s_mysticalagriculture_0 | mysticalagriculture:furnace | not shown | [80000, 80000] | not shown |  |  | [80000, 80000] | not shown |
| s_mysticalagriculture_1 | mysticalagriculture:seed_reprocessor | not shown | [80000, 80000] | not shown |  |  | [80000, 80000] | not shown |
| s_mysticalagriculture_2 | mysticalagriculture:soul_extractor | not shown | [80000, 80000] | not shown |  |  | [80000, 80000] | not shown |
| s_mysticalagriculture_3 | mysticalagriculture:harvester | not shown | [80000, 80000] | not shown |  |  | [80000, 80000] | not shown |
| s_mysticalautomation_0 | mysticalautomation:infuser | pass | [80000, 80000] | 80.0 k / 80.0 k FE |  |  | [80000, 80000] | 80.0 k / 80.0 k FE |
| s_productivebees_0 | productivebees:centrifuge | pass |  |  | [[minecraft:water, 2560, 10000]] | [2560 / 10000 mB] | [[minecraft:water, 2048, 10000]] | [2048 / 10000 mB] |
| s_productivebees_1 | productivebees:honey_generator | not shown | [100000, 100000] | 100 k / 100 k FE | [[, 0, 10000]] | not shown | [100000, 100000] | 100 k / 100 k FE |
| s_productivebees_3 | productivebees:powered_centrifuge | not shown | [10000, 10000] | not shown | [[minecraft:water, 800, 10000]] | [800 / 10000 mB] | [10000, 10000] | not shown |
| s_productivefarming_0 | productivefarming:farm_controller | pass |  |  | [[minecraft:water, 2560, 10000]] | [2.56 k / 10.0 k mB] | [[minecraft:water, 2048, 10000]] | [2.05 k / 10.0 k mB] |
| s_productivefarming_1 | productivefarming:watering_trough | pass |  |  | [[minecraft:water, 2560, 10000]] | [2.56 k / 10.0 k mB] | [[minecraft:water, 2048, 10000]] | [2.05 k / 10.0 k mB] |
| s_agritechevolved_1 | agritechevolved:advanced_planter | not shown | [100000, 100000] | not shown |  |  | [100000, 100000] | not shown |
| s_agritechevolved_2 | agritechevolved:biomass_burner | pass | [0, 100000] | 0 / 100 k FE |  |  | [0, 100000] | 0 / 100 k FE |
| s_hostilenetworks_0 | hostilenetworks:sim_chamber | not shown | [2000000, 2000000] | not shown |  |  | [2000000, 2000000] | not shown |
| s_hostilenetworks_1 | hostilenetworks:loot_fabricator | pass | [1000000, 1000000] | 1.00 M / 1.00 M FE |  |  | [1000000, 1000000] | 1.00 M / 1.00 M FE |
| s_cookingforblockheads_1 | cookingforblockheads:milk_jar | pass |  |  | [[, 0, 32000]] | [0 / 32.0 k mB] | [[, 0, 32000]] | [0 / 32.0 k mB] |
| s_cookingforblockheads_2 | cookingforblockheads:blue_oven | not shown | [10000, 10000] | not shown |  |  | [10000, 10000] | not shown |
| s_ae2_0 | ae2:inscriber | pass | [3200, 3200] | 3.20 k / 3.20 k FE |  |  | [3200, 3200] | 3.20 k / 3.20 k FE |
| s_ae2_1 | ae2:sky_stone_tank | pass |  |  | [[minecraft:water, 2560, 16000]] | [2.56 k / 16.0 k mB] | [[minecraft:water, 2048, 16000]] | [2.05 k / 16.0 k mB] |
| s_advanced_ae_1 | advanced_ae:reaction_chamber | not shown | [1000000, 1000000] | 1.00 M / 1.00 M FE | [[, 0, 16000], [minecraft:water, 800, 16000]] | not shown | [1000000, 1000000] | 1.00 M / 1.00 M FE |
| s_extendedae_0 | extendedae:crystal_assembler | not shown | [40000, 40000] | 40.0 k / 40.0 k FE | [[minecraft:water, 1000, 16000]] | not shown | [40000, 40000] | 40.0 k / 40.0 k FE |
| s_refinedstorage_0 | refinedstorage:white_controller | pass | [1000, 1000] | 1.00 k / 1.00 k FE |  |  | [1000, 1000] | 1.00 k / 1.00 k FE |
| s_cabletiers_0 | cabletiers:elite_interface | not shown | [900000, 16000] | 900 k / 16.0 k FE | [[, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0], [, 0, 0]] | not shown | [900000, 16000] | 900 k / 16.0 k FE |
| s_functionalstorage_0 | functionalstorage:fluid_1 | pass |  |  | [[minecraft:water, 2560, 32000]] | [2.56 k / 32.0 k mB] | [[minecraft:water, 2048, 32000]] | [2.05 k / 32.0 k mB] |
| s_dimstorage_1 | dimstorage:dimensional_tank | pass |  |  | [[minecraft:water, 2560, 16000]] | [2.56 k / 16.0 k mB] | [[minecraft:water, 2048, 16000]] | [2.05 k / 16.0 k mB] |
| s_trashcans_0 | trashcans:liquid_trash_can | not shown |  |  | [[, 0, 9223372036854776000]] | not shown | [[, 0, 9223372036854776000]] | not shown |
| s_trashcans_1 | trashcans:energy_trash_can | not shown | [0, 9223372036854776000] | not shown |  |  | [0, 9223372036854776000] | not shown |
| s_railcraft_coke_oven | railcraft:coke_oven_bricks | not shown |  |  | [[railcraft:creosote, 20480, 64000]] | not shown | [[railcraft:creosote, 16384, 64000]] | not shown |
| s_ironfurnaces_0 | ironfurnaces:iron_furnace | not shown | [80000, 80000] | not shown |  |  | [80000, 80000] | not shown |
| s_ironfurnaces_1 | ironfurnaces:gold_furnace | not shown | [200000, 200000] | not shown |  |  | [200000, 200000] | not shown |
| s_ironfurnaces_2 | ironfurnaces:diamond_furnace | not shown | [1000000, 1000000] | not shown |  |  | [1000000, 1000000] | not shown |
| s_ironfurnaces_3 | ironfurnaces:emerald_furnace | not shown | [1000000, 1000000] | not shown |  |  | [1000000, 1000000] | not shown |
| s_ironfurnaces_4 | ironfurnaces:netherite_furnace | not shown | [1000000, 1000000] | not shown |  |  | [1000000, 1000000] | not shown |
| s_ironfurnaces_5 | ironfurnaces:allthemodium_furnace | not shown | [1000000, 1000000] | not shown |  |  | [1000000, 1000000] | not shown |
| s_ironfurnaces_6 | ironfurnaces:heater | pass | [1000000, 1000000] | 1.00 M / 1.00 M FE |  |  | [1000000, 1000000] | 1.00 M / 1.00 M FE |
| s_ironfurnaces_chain0 | ironfurnaces:diamond_furnace | not shown | [0, 1000000] | not shown |  |  | [0, 1000000] | not shown |
| s_energizedpower_0 | energizedpower:pulverizer | pass | [2048, 2048] | 2.05 k / 2.05 k FE |  |  | [2048, 2048] | 2.05 k / 2.05 k FE |
| s_energizedpower_1 | energizedpower:crusher | pass | [2048, 2048] | 2.05 k / 2.05 k FE |  |  | [2048, 2048] | 2.05 k / 2.05 k FE |
| s_energizedpower_2 | energizedpower:sawmill | pass | [2048, 2048] | 2.05 k / 2.05 k FE |  |  | [2048, 2048] | 2.05 k / 2.05 k FE |
| s_energizedpower_3 | energizedpower:compressor | pass | [2048, 2048] | 2.05 k / 2.05 k FE |  |  | [2048, 2048] | 2.05 k / 2.05 k FE |
| s_energizedpower_4 | energizedpower:metal_press | pass | [2048, 2048] | 2.05 k / 2.05 k FE |  |  | [2048, 2048] | 2.05 k / 2.05 k FE |
| s_energizedpower_5 | energizedpower:induction_smelter | pass | [8192, 8192] | 8.19 k / 8.19 k FE |  |  | [8192, 8192] | 8.19 k / 8.19 k FE |
| s_energizedpower_6 | energizedpower:charger | pass | [8192, 8192] | 8.19 k / 8.19 k FE |  |  | [8192, 8192] | 8.19 k / 8.19 k FE |
| s_energizedpower_7 | energizedpower:plant_growth_chamber | not shown | [4096, 4096] | 4.10 k / 4.10 k FE | [[minecraft:water, 800, 8000]] | not shown | [4096, 4096] | 4.10 k / 4.10 k FE |
| s_energizedpower_chain0 | energizedpower:pulverizer | pass | [2048, 2048] | 2.04 k / 2.05 k FE |  |  | [2048, 2048] | 2.04 k / 2.05 k FE |
| s_energizedpower_chain1 | energizedpower:powered_furnace | not shown | [2048, 2048] | 2.05 k / 2.05 k FE | [[, 0, 8000]] | not shown | [2048, 2048] | 2.05 k / 2.05 k FE |
| s_enderio_0 | enderio:alloy_smelter | pass | [64000, 64000] | 64.0 k / 64.0 k FE |  |  | [64000, 64000] | 64.0 k / 64.0 k FE |
| s_enderio_1 | enderio:sag_mill | pass | [64000, 64000] | 64.0 k / 64.0 k FE |  |  | [64000, 64000] | 64.0 k / 64.0 k FE |
| s_enderio_2 | enderio:stirling_generator | fail | [64000, 64000] | 64.0 k / 64.0 k FE |  |  | [53560, 64000] | 55.6 k / 64.0 k FE |
| s_enderio_3 | enderio:painting_machine | pass | [64000, 64000] | 64.0 k / 64.0 k FE |  |  | [64000, 64000] | 64.0 k / 64.0 k FE |
| s_enderio_4 | enderio:slice_and_splice | pass |  |  |  |  |  |  |
| s_enderio_5 | enderio:soul_binder | not shown | [128000, 128000] | 128 k / 128 k FE | [[, 0, 10000]] | not shown | [128000, 128000] | 128 k / 128 k FE |
| s_enderio_6 | enderio:crafter | pass | [64000, 64000] | 64.0 k / 64.0 k FE |  |  | [64000, 64000] | 64.0 k / 64.0 k FE |
| s_enderio_7 | enderio:powered_spawner | pass | [100000, 100000] | 100 k / 100 k FE |  |  | [100000, 100000] | 100 k / 100 k FE |
| s_enderio_chain0 | enderio:sag_mill | pass |  |  |  |  |  |  |
| s_enderio_chain1 | enderio:alloy_smelter | pass |  |  |  |  |  |  |
| energymeter | energymeter:meter | pass |  |  |  |  |  |  |
| hauptspeicher | powah:energy_cell_nitro | pass | [844525156, 2000000000] | 845 M / 2.00 G FE |  |  | [850114056, 2000000000] | 851 M / 2.00 G FE |
| reactor | powah:reactor_blazing | not shown |  |  | [[minecraft:water, 640, 1000]] | not shown | [[minecraft:water, 512, 1000]] | not shown |
| stirling | enderio:stirling_generator | pass | [80, 256000] | 80 / 256 k FE |  |  | [80, 256000] | 80 / 256 k FE |
| coal_engine | energizedpower:coal_engine | pass | [0, 2048] | 0 / 2.05 k FE |  |  | [0, 2048] | 0 / 2.05 k FE |
| iron_gen | generatorgalore:iron_generator | pass | [0, 10000] | 0 / 10.0 k FE |  |  | [0, 10000] | 0 / 10.0 k FE |
| gold_gen | generatorgalore:gold_generator | pass | [0, 30000] | 0 / 30.0 k FE |  |  | [0, 30000] | 0 / 30.0 k FE |
| furnator | powah:furnator_niotic | pass | [0, 2000000] | 0 / 2.00 M FE |  |  | [0, 2000000] | 0 / 2.00 M FE |
| diamond_gen | generatorgalore:diamond_generator | pass | [0, 100000] | 0 / 100 k FE |  |  | [0, 100000] | 0 / 100 k FE |
| thermo | powah:thermo_generator_niotic | not shown | [400, 2000000] | 400 / 2.00 M FE | [[minecraft:water, 62660, 4000]] | not shown | [400, 2000000] | 400 / 2.00 M FE |
| ep_solar | energizedpower:solar_panel_5 | pass | [0, 327680] | 0 / 328 k FE |  |  | [0, 327680] | 0 / 328 k FE |
| eio_solar | enderio:energetic_photovoltaic_module | pass | [0, 4000] | 0 / 4.00 k FE |  |  | [0, 4000] | 0 / 4.00 k FE |
| iron_furnace | ironfurnaces:diamond_furnace | not shown | [0, 1000000] | not shown |  |  | [0, 1000000] | not shown |
| ma_harvester | mysticalagriculture:harvester | not shown | [80000, 80000] | not shown |  |  | [80000, 80000] | not shown |
| centrifuge | productivebees:powered_centrifuge | not shown | [10000, 10000] | not shown | [[minecraft:water, 800, 10000]] | [800 / 10000 mB] | [10000, 10000] | not shown |
| hnn_0 | hostilenetworks:sim_chamber | not shown | [2000000, 2000000] | not shown |  |  | [2000000, 2000000] | not shown |
| hnn_1 | hostilenetworks:sim_chamber | not shown | [2000000, 2000000] | not shown |  |  | [2000000, 2000000] | not shown |
| hnn_2 | hostilenetworks:sim_chamber | not shown | [2000000, 2000000] | not shown |  |  | [2000000, 2000000] | not shown |
| hnn_3 | hostilenetworks:sim_chamber | not shown | [2000000, 2000000] | not shown |  |  | [2000000, 2000000] | not shown |
| nether_thermo | powah:thermo_generator_niotic | not shown | [400, 2000000] | 400 / 2.00 M FE | [[minecraft:water, 62662, 4000]] | not shown | [400, 2000000] | 400 / 2.00 M FE |
| probe_netherite | generatorgalore:netherite_generator | pass | [0, 1000000] | 0 / 1.00 M FE |  |  | [0, 1000000] | 0 / 1.00 M FE |
| glass_furnace | ironfurnaces:diamond_furnace | not shown | [0, 1000000] | not shown |  |  | [0, 1000000] | not shown |

### Fails

- s_powah_0 (powah:energy_cell_nitro): the dashboard stays exactly 2.0 M FE below the capability and NBT, and it does follow changes (33.0 M shown at 35 M, 47.0 M at 49 M). The card and the capability read the same `Energy.getStored`, so the cause is not found. Open.
- s_oritech_7 (oritech:energy_pipe_connection): the capability always reports 25,000/25,000 while the card follows Oritech's internal storage down to 10.0 k after drains. The two sources disagree. Not fixed.
- s_enderio_2 (enderio:stirling_generator): stored amount matches; the flow label shows 0 FE/t while the stored amount rises by about 10 FE/t. Open.

### Mod bugs fixed on this branch

- Fluids tab rows were null (Json.fromLua on the map).
- Tank card capacity overflowed to -2 mB on tanks reporting Long.MAX_VALUE.
- Tank card named only the first fluid while summing all tanks; it now names each fluid.
- New dev commands `cccloud dev caps` and `cccloud dev drain`.
