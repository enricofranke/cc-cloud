package ai.ezfox.cccloud.compat;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.cable.CcReach;
import ai.ezfox.cccloud.cable.CcWired;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * CC: Tweaked is optional (docs/SPEC.md "CC: Tweaked is optional"). Every class that touches CC: Tweaked lives in
 * {@code compat/cct} and is only class-loaded when {@link ModList} reports the mod; the rest of the mod reaches it
 * through this class and the CC-free types it hands out ({@link ComputerHandle}, {@link CcWired}, {@link CcReach},
 * {@link ai.ezfox.cccloud.uplink.AttachedComputer}). Without CC: Tweaked every hook answers "nothing": no computers, no
 * peripherals, no CC wired networks – mainframes, uplinks, fiber, relays, cards, rules and the dashboard work as before.
 */
public final class Cct {
    public static final String MODID = "computercraft";

    private static volatile Hooks hooks = Hooks.NONE;
    private static volatile boolean present;

    private Cct() {
    }

    /** From the mod constructor, before the integrations: sets up the CC: Tweaked integration if the mod is there. */
    public static void init(IEventBus bus) {
        if (!ModList.get().isLoaded(MODID)) {
            CCCloud.LOGGER.info("[cccloud] CC: Tweaked is not installed: no computers, Lua peripherals or CC cables (everything else works)");
            return;
        }
        try {
            hooks = ai.ezfox.cccloud.compat.cct.CctCompat.init(bus);
            present = true;
            CCCloud.LOGGER.info("[cccloud] CC: Tweaked {} found: computers, Lua peripherals and CC cables enabled", version());
        } catch (LinkageError | RuntimeException e) {
            hooks = Hooks.NONE;
            CCCloud.LOGGER.error("[cccloud] This CC: Tweaked version is not supported; CC: Cloud runs without its CC: Tweaked features", e);
        }
    }

    /** Whether the CC: Tweaked integration is active (the mod is installed and its API matched). */
    public static boolean present() {
        return present;
    }

    /** The installed CC: Tweaked version, or null without it. */
    public static @Nullable String version() {
        if (!ModList.get().isLoaded(MODID)) return null;
        return ModList.get().getModContainerById(MODID).map(c -> c.getModInfo().getVersion().toString()).orElse(null);
    }

    public static Hooks hooks() {
        return hooks;
    }

    /**
     * The computer block at {@code pos} (server side): a Cloud Computer (always there, docs/SPEC.md "Cloud Computers") or
     * a CC: Tweaked computer (only with CC: Tweaked); null when there is none (or a turtle).
     */
    public static @Nullable ComputerHandle computer(Level level, BlockPos pos) {
        var own = ai.ezfox.cccloud.computer.block.ComputerBlocks.computer(level, pos);
        return own != null ? own : hooks.computer(level, pos);
    }

    /**
     * What the integration provides. The default methods are the answers without CC: Tweaked; {@code compat/cct} overrides
     * them. Only CC-free types appear here, so this interface loads without the mod.
     */
    public interface Hooks {
        Hooks NONE = new Hooks() {
        };

        default @Nullable ComputerHandle computer(Level level, BlockPos pos) {
            return null;
        }

        /** The mainframe's peripheral ({@code cloud_mainframe}) and wired membership on both halves. */
        default CcWired mainframe(CloudMainframeBlockEntity mainframe) {
            return CcWired.NONE;
        }

        /** The relay's peripheral ({@code cloud_relay}) and wired membership. */
        default CcWired relay(CloudRelayBlockEntity relay) {
            return CcWired.NONE;
        }

        /** The uplink's peripheral ({@code cloud_uplink}) and wired membership (it puts nothing on the cable). */
        default CcWired uplink(CloudUplinkBlockEntity uplink) {
            return CcWired.NONE;
        }

        /** A Cloud Bridge's peripheral ({@code me_cloud_bridge} / {@code rs_cloud_bridge}) and wired membership. */
        default CcWired bridge(BlockEntity owner, BridgeHost host, String type, Supplier<String> id) {
            return CcWired.NONE;
        }

        /** The CC wired networks an uplink's computer is on (docs/SPEC.md "CC wired networks"). */
        default CcReach reach(CloudUplinkBlockEntity uplink) {
            return CcReach.NONE;
        }

        /** Server tick: pending wired elements join their neighbours. */
        default void tick() {
        }

        /** Server stop. */
        default void clear() {
        }
    }
}
