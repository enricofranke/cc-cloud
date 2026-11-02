package ai.ezfox.cccloud.compat.cct;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.bridge.BridgeRegistry;
import ai.ezfox.cccloud.cable.CcReach;
import ai.ezfox.cccloud.cable.CcWired;
import ai.ezfox.cccloud.compat.Cct;
import ai.ezfox.cccloud.compat.ComputerHandle;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.cct.peripheral.BridgePeripheral;
import ai.ezfox.cccloud.compat.cct.peripheral.MainframePeripheral;
import ai.ezfox.cccloud.compat.cct.peripheral.RelayPeripheral;
import ai.ezfox.cccloud.compat.cct.peripheral.UplinkPeripheral;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

/**
 * The CC: Tweaked integration (docs/SPEC.md "CC: Tweaked is optional"). Like every class in {@code compat/cct}, it is
 * only class-loaded through {@link Cct#init} when CC: Tweaked is installed: the peripherals ({@code cloud_mainframe},
 * {@code cloud_relay}, {@code cloud_uplink}, the bridges), CC wired network membership, the automatic Computer card's
 * computers, the Peripheral card and the CC-only GameTests.
 */
public final class CctCompat implements Cct.Hooks {
    private CctCompat() {
    }

    public static Cct.Hooks init(IEventBus bus) {
        bus.addListener(CctCompat::registerCapabilities);
        // Wave 4: the Peripheral card for every other block CC: Tweaked offers as a peripheral (uplinks and probes only).
        DeviceCards.register(PeripheralCard.KIND, DeviceCards.FALLBACK, PeripheralCard::detect);
        // GameTests that need CC: Tweaked's classes (their test instances load only with CC: Tweaked).
        Integrations.test("peripheral_card_generic", () -> PeripheralGameTests::chest);
        Integrations.test("peripheral_card_own_methods", () -> PeripheralGameTests::ownMethods);
        Integrations.test("wired_machine_cards", () -> WiredMachineTests::wiredMachineCards);
        Integrations.test("rom_contents", () -> CctGameTests::romContents);
        // GameTest runs only: a test peripheral on lecterns for the Peripheral card (PeripheralGameTests).
        if (Boolean.getBoolean("cccloud.gametest")) bus.addListener(PeripheralGameTests::registerTestCapabilities);
        return new CctCompat();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.UPLINK_BE.get(), (uplink, side) -> peripheral(uplink.wired()));
        // The mainframe is a peripheral on both halves; the block entity lives in the lower half.
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : peripheral(mainframe.wired());
        }, ModRegistry.CLOUD_MAINFRAME.get());
        // Cables: mainframe (both halves), relays, uplinks and bridges are CC wired elements; relays are peripherals too.
        event.registerBlockEntity(WiredElementCapability.get(), ModRegistry.UPLINK_BE.get(), (uplink, side) -> element(uplink.wired()));
        event.registerBlockEntity(WiredElementCapability.get(), ModRegistry.RELAY_BE.get(), (relay, side) -> element(relay.wired()));
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.RELAY_BE.get(), (relay, side) -> peripheral(relay.wired()));
        event.registerBlock(WiredElementCapability.get(), (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : element(mainframe.wired());
        }, ModRegistry.CLOUD_MAINFRAME.get());
        // The ME / RS Cloud Bridges (registered by compat/ae2 and compat/rs when those mods are there).
        for (var type : BridgeRegistry.blockEntityTypes()) bridge(event, type.get());
    }

    private static <T extends BlockEntity> void bridge(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(PeripheralCapability.get(), type, (be, side) -> be instanceof BridgeHost host ? peripheral(host.cloudWired()) : null);
        event.registerBlockEntity(WiredElementCapability.get(), type, (be, side) -> be instanceof BridgeHost host ? element(host.cloudWired()) : null);
    }

    private static @Nullable IPeripheral peripheral(@Nullable CcWired wired) {
        return wired instanceof CloudWired w ? w.peripheral() : null;
    }

    private static @Nullable WiredElement element(@Nullable CcWired wired) {
        return wired instanceof CloudWired w ? w : null;
    }

    // ---- Cct.Hooks ---------------------------------------------------------------------------------------------------

    @Override
    public @Nullable ComputerHandle computer(Level level, BlockPos pos) {
        return CcComputers.find(level, pos);
    }

    @Override
    public CcWired mainframe(CloudMainframeBlockEntity mainframe) {
        return new CloudWired(mainframe, () -> mainframe.getMainframeId().toString(), true, new MainframePeripheral(mainframe), true,
            () -> List.of(mainframe.getBlockPos(), mainframe.getBlockPos().above()));
    }

    @Override
    public CcWired relay(CloudRelayBlockEntity relay) {
        return CloudWired.single(relay, () -> relay.relayId().toString(), true, new RelayPeripheral(relay), true);
    }

    @Override
    public CcWired uplink(CloudUplinkBlockEntity uplink) {
        // a member only: it puts no peripheral on the cable (no remote control of uplinks over cables)
        return CloudWired.single(uplink, () -> uplink.getUplinkId().toString(), false, new UplinkPeripheral(uplink), false);
    }

    @Override
    public CcWired bridge(BlockEntity owner, BridgeHost host, String type, Supplier<String> id) {
        return CloudWired.single(owner, id, false, new BridgePeripheral(type, host), true);
    }

    @Override
    public CcReach reach(CloudUplinkBlockEntity uplink) {
        return new WiredReach(new WiredReach.Supplier() {
            @Override
            public @Nullable Level level() {
                return uplink.getLevel();
            }

            @Override
            public BlockPos pos() {
                return uplink.getBlockPos();
            }

            @Override
            public String id() {
                return uplink.getUplinkId().toString();
            }
        });
    }

    @Override
    public void tick() {
        CloudWired.tick();
    }

    @Override
    public void clear() {
        CloudWired.clear();
    }
}
