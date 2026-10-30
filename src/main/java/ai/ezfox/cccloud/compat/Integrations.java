package ai.ezfox.cccloud.compat;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point of the v0.5 integrations (docs/SPEC.md v0.5 A + B), called once from {@link CCCloud}'s constructor.
 * Every mod integration lives in its own {@code compat/<modid>} package and is only class-loaded when
 * {@link ModList} reports the mod; a failure while setting one up disables just that integration (one log line).
 */
public final class Integrations {
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, CCCloud.MODID);

    private Integrations() {
    }

    public static void init(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, IntegrationConfig.SPEC, "cccloud-integrations-server.toml");

        // A. Ready-made cards. Mod-specific providers first, the generic FE card last.
        DeviceCards.register("energy", DeviceCards.GENERIC, EnergyCard::detect);
        // Wave 3: plain fluid tanks (fluid capability only), below the Energy card.
        DeviceCards.register(ai.ezfox.cccloud.compat.card.FluidTankCard.KIND, DeviceCards.GENERIC - 1, ai.ezfox.cccloud.compat.card.FluidTankCard::detect);
        test("tank_card_generic", () -> ai.ezfox.cccloud.compat.card.CardGameTests::genericTank);
        test("energy_card_generic", () -> ai.ezfox.cccloud.compat.card.CardGameTests::genericEnergy);
        // Wave 4: the Peripheral card for every other block CC: Tweaked offers as a peripheral (uplinks and probes only).
        DeviceCards.register(ai.ezfox.cccloud.compat.cc.PeripheralCard.KIND, DeviceCards.FALLBACK, ai.ezfox.cccloud.compat.cc.PeripheralCard::detect);
        test("peripheral_card_generic", () -> ai.ezfox.cccloud.compat.cc.PeripheralGameTests::chest);
        test("peripheral_card_own_methods", () -> ai.ezfox.cccloud.compat.cc.PeripheralGameTests::ownMethods);
        optional("powah", () -> ai.ezfox.cccloud.compat.powah.PowahCompat::init);
        optional("enderio", () -> ai.ezfox.cccloud.compat.enderio.EnderIoCards::init);
        optional("oritech", () -> ai.ezfox.cccloud.compat.oritech.OritechCards::init);
        optional("energizedpower", () -> ai.ezfox.cccloud.compat.energizedpower.EnergizedPowerCards::init);
        optional("generatorgalore", () -> ai.ezfox.cccloud.compat.generatorgalore.GeneratorGaloreCards::init);
        optional("energymeter", () -> ai.ezfox.cccloud.compat.energymeter.EnergyMeterCards::init);
        optional("hostilenetworks", () -> ai.ezfox.cccloud.compat.hostilenetworks.HostileNetworksCards::init);
        optional("productivebees", () -> ai.ezfox.cccloud.compat.productivebees.ProductiveBeesCards::init);
        optional("mffs", () -> ai.ezfox.cccloud.compat.mffs.MffsCards::init);
        optional("merequester", () -> ai.ezfox.cccloud.compat.merequester.MeRequesterCards::init);

        // v0.8 wave 2 (docs/SPEC.md "v0.8"). 1. Storage controllers as Storage cards (capabilities only, no mod classes).
        for (String mod : ai.ezfox.cccloud.compat.storage.StorageControllers.mods()) {
            optional(mod, () -> () -> ai.ezfox.cccloud.compat.storage.StorageControllers.init(mod));
        }
        test("storage_dimstorage_chest", () -> ai.ezfox.cccloud.compat.storage.StorageGameTests::dimStorageChest);
        test("storage_dimstorage_tank", () -> ai.ezfox.cccloud.compat.storage.StorageGameTests::dimStorageTank);
        test("storage_sophisticated_controller", () -> ai.ezfox.cccloud.compat.storage.StorageGameTests::sophisticatedController);
        test("storage_occultism_actuator", () -> ai.ezfox.cccloud.compat.storage.StorageGameTests::occultismActuator);
        test("storage_functionalstorage_controller", () -> ai.ezfox.cccloud.compat.storage.StorageGameTests::functionalStorageController);

        // 2. Advanced AE quantum computer.
        optional("advanced_ae", () -> ai.ezfox.cccloud.compat.advancedae.AdvancedAeCards::init);

        // 3. Quarries.
        optional("quarryplus", () -> ai.ezfox.cccloud.compat.quarryplus.QuarryPlusCards::init);

        // 4. Spawners and mob farms.
        optional("apothic_spawners", () -> ai.ezfox.cccloud.compat.apothicspawners.ApothicSpawnerCards::init);
        optional("gateways", () -> ai.ezfox.cccloud.compat.gateways.GatewaysCard::init);
        optional("easy_villagers", () -> ai.ezfox.cccloud.compat.easyvillagers.EasyVillagersCards::init);

        // 5. Processing machines.
        optional("ironfurnaces", () -> ai.ezfox.cccloud.compat.ironfurnaces.IronFurnacesCards::init);
        optional("cobblegengalore", () -> ai.ezfox.cccloud.compat.cobblegengalore.CobblegenCards::init);
        optional("productivemetalworks", () -> ai.ezfox.cccloud.compat.productivemetalworks.MetalworksCards::init);
        optional("productivefarming", () -> ai.ezfox.cccloud.compat.productivefarming.FarmCards::init);
        optional("agritechevolved", () -> ai.ezfox.cccloud.compat.agritechevolved.AgritechCards::init);
        optional("mysticalagriculture", () -> ai.ezfox.cccloud.compat.mysticalagriculture.MysticalCards::init);
        optional("railcraft", () -> ai.ezfox.cccloud.compat.railcraft.RailcraftCards::init);

        // 6. Logistics. 7. Integrated Dynamics.
        optional("sfm", () -> ai.ezfox.cccloud.compat.sfm.SfmCards::init);
        optional("modularrouters", () -> ai.ezfox.cccloud.compat.modularrouters.RouterCards::init);
        optional("integrateddynamics", () -> ai.ezfox.cccloud.compat.integrateddynamics.IntegratedDynamicsCards::init);

        // 8. World & progression.
        optional("waystones", () -> ai.ezfox.cccloud.compat.waystones.WaystonesCard::init);
        optional("ftbquests", () -> ai.ezfox.cccloud.compat.ftbquests.QuestsCard::init);

        // Wave 3: more ATM11 machines.
        optional("witchery", () -> ai.ezfox.cccloud.compat.witchery.WitcheryCards::init);
        optional("forbidden_arcanus", () -> ai.ezfox.cccloud.compat.forbiddenarcanus.HephaestusForgeCards::init);
        optional("pylons", () -> ai.ezfox.cccloud.compat.pylons.PylonCards::init);
        optional("arsmagicalegacy", () -> ai.ezfox.cccloud.compat.arsmagica.EtheriumCards::init);

        // Wave 4: more ATM11 mods.
        optional("justdirethings", () -> ai.ezfox.cccloud.compat.justdirethings.JustDireCards::init);
        optional("productivetrees", () -> ai.ezfox.cccloud.compat.productivetrees.TreeMachineCards::init);
        optional("rangedpumps", () -> ai.ezfox.cccloud.compat.rangedpumps.PumpCards::init);
        optional("charginggadgets", () -> ai.ezfox.cccloud.compat.charginggadgets.ChargingStationCards::init);
        optional("selling_bin", () -> ai.ezfox.cccloud.compat.sellingbin.SellingBinCards::init);
        optional("cookingforblockheads", () -> ai.ezfox.cccloud.compat.cookingforblockheads.KitchenCards::init);
        optional("farmingforblockheads", () -> ai.ezfox.cccloud.compat.farmingforblockheads.ShippingBinCards::init);
        optional("trainingdummy", () -> ai.ezfox.cccloud.compat.trainingdummy.DummiesCard::init);
        optional("ftbessentials", () -> ai.ezfox.cccloud.compat.ftbessentials.WarpsCard::init);

        // B. Server & team.
        ai.ezfox.cccloud.compat.server.MainframeCards.register(ai.ezfox.cccloud.compat.server.ServerCard.KIND,
            () -> IntegrationConfig.get(IntegrationConfig.SERVER_CARD), m -> new ai.ezfox.cccloud.compat.server.ServerCard(m.getLevel().getServer()));
        test("server_card", () -> ai.ezfox.cccloud.compat.server.ServerCardGameTests::serverCard);
        optional("spark", () -> ai.ezfox.cccloud.compat.spark.SparkStats::init);
        TeamTrust.setEnabled(() -> IntegrationConfig.get(IntegrationConfig.FTB_TEAMS_TRUST));
        optional("ftbteams", () -> () -> {
            ai.ezfox.cccloud.compat.ftbteams.FtbTeamsSource.init();
            test("ftbteams_trust", () -> ai.ezfox.cccloud.compat.ftbteams.FtbTeamsGameTests::trust);
        });
        optional("ftbchunks", () -> ai.ezfox.cccloud.compat.ftbchunks.ClaimsCard::init);
        optional("simplebackups", () -> ai.ezfox.cccloud.compat.simplebackups.BackupsCard::init);

        // v0.8: handbook (GuideMe, data-driven: assets/cccloud/guideme_guides + guides/) and KubeJS bindings.
        optional("guideme", () -> ai.ezfox.cccloud.compat.guideme.HandbookGameTests::init);
        optional("kubejs", () -> ai.ezfox.cccloud.compat.kubejs.KubeJSGameTests::init);
        test("script_sections", () -> ai.ezfox.cccloud.api.ScriptSectionGameTests::sections);

        if (net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) TESTS.register(bus); // dev / GameTest server only
    }

    /** Whether uplinks look for device cards (config {@code deviceCards}). */
    public static boolean deviceCardsEnabled() {
        return IntegrationConfig.get(IntegrationConfig.DEVICE_CARDS);
    }

    /** Registers a GameTest function {@code cccloud:<name>} (referenced by data/cccloud/test_instance/<name>.json). */
    public static void test(String name, Supplier<Consumer<GameTestHelper>> function) {
        TESTS.register(name, function);
    }

    /** Runs a mod's setup if the mod is loaded; a broken integration is skipped with one log line. */
    private static void optional(String modId, Supplier<Runnable> setup) {
        if (!ModList.get().isLoaded(modId)) return;
        try {
            setup.get().run();
            CCCloud.LOGGER.info("[cccloud] Integration for '{}' enabled", modId);
        } catch (LinkageError | RuntimeException e) {
            CCCloud.LOGGER.warn("[cccloud] Integration for '{}' disabled: this version is not supported ({})", modId, e.toString());
        }
    }
}
