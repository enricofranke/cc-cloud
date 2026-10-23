package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.CloudLog;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Registry of the ready-made device cards (docs/SPEC.md v0.5 A). The integrations register a {@link Provider} during
 * mod construction, only if their mod is loaded; the uplink asks {@link #detect} for the block it sticks to.
 * <p>
 * Fail-soft: a provider or card that throws (or hits a {@link LinkageError} because another mod changed its classes)
 * is disabled for the rest of the session with one log line, and the card simply does not appear.
 * Server thread only.
 */
public final class DeviceCards {
    /** Creates a card for the block at {@code pos}, or returns null if the block is not supported. */
    @FunctionalInterface
    public interface Provider {
        /**
         * @param side the face of the block the uplink touches (use it for capability lookups)
         */
        @Nullable DeviceCard create(ServerLevel level, BlockPos pos, Direction side);
    }

    private record Entry(String name, int priority, Provider provider) {
    }

    /** Priority of mod-specific cards; the generic FE card uses {@link #GENERIC}. */
    public static final int SPECIFIC = 100;
    public static final int GENERIC = 0;

    private static final List<Entry> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final java.util.Set<String> DISABLED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DeviceCards() {
    }

    public static void register(String name, int priority, Provider provider) {
        PROVIDERS.add(new Entry(name, priority, provider));
        var sorted = new ArrayList<>(PROVIDERS);
        sorted.sort(Comparator.comparingInt(Entry::priority).reversed());
        PROVIDERS.clear();
        PROVIDERS.addAll(sorted);
    }

    /** Names of the registered providers (diagnostics, tests). */
    public static List<String> providers() {
        return PROVIDERS.stream().map(Entry::name).toList();
    }

    /** The card for the block at {@code pos}, or null. */
    public static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        for (var entry : PROVIDERS) {
            if (DISABLED.contains(entry.name())) continue;
            try {
                var card = entry.provider().create(level, pos, side);
                if (card != null) return card;
            } catch (LinkageError | RuntimeException e) {
                disable(entry.name(), e);
            }
        }
        return null;
    }

    /** Refreshes a card; false if it failed (the caller drops it). */
    public static boolean refresh(DeviceCard card, DeviceCard.CardOutput out) {
        try {
            card.refresh(out);
            return true;
        } catch (LinkageError | RuntimeException e) {
            disable(providerOf(card), e);
            return false;
        }
    }

    /** The card's widgets, or null if building them failed (the card is dropped and its provider disabled). */
    public static java.util.@Nullable List<ai.ezfox.cccloud.uplink.Widget> widgets(DeviceCard card) {
        try {
            return card.widgets();
        } catch (LinkageError | RuntimeException e) {
            disable(providerOf(card), e);
            return null;
        }
    }

    /** The card's title (failures: an empty title). */
    public static String title(DeviceCard card) {
        try {
            return card.title();
        } catch (LinkageError | RuntimeException e) {
            disable(providerOf(card), e);
            return "";
        }
    }

    /** The card's widget revision; -1 if the call failed. */
    public static int revision(DeviceCard card) {
        try {
            return card.revision();
        } catch (LinkageError | RuntimeException e) {
            disable(providerOf(card), e);
            return -1;
        }
    }

    /** True if the card's block is still there; false (drop it) if not or if the check failed. */
    public static boolean isValid(DeviceCard card) {
        try {
            return card.isValid();
        } catch (LinkageError | RuntimeException e) {
            disable(providerOf(card), e);
            return false;
        }
    }

    /** Runs an action; a failing card answers "unavailable" and is disabled. */
    public static @Nullable String action(DeviceCard card, String widgetId, JsonElement value, String user) {
        try {
            return card.action(widgetId, value, user);
        } catch (LinkageError | RuntimeException e) {
            disable(providerOf(card), e);
            return "unavailable";
        }
    }

    private static String providerOf(DeviceCard card) {
        // Cards are named after their provider by convention (kind prefix), see Integrations.
        String kind;
        try {
            kind = card.kind();
        } catch (RuntimeException | LinkageError e) {
            kind = card.getClass().getName();
        }
        for (var entry : PROVIDERS) if (kind.equals(entry.name()) || kind.startsWith(entry.name() + "_")) return entry.name();
        return kind;
    }

    private static void disable(String name, Throwable e) {
        if (DISABLED.add(name)) {
            CloudLog.LOGGER.warn("[cccloud] The '{}' dashboard card is disabled: this version of the mod is not supported ({})", name, e.toString());
            CloudLog.LOGGER.debug("[cccloud] '{}' card failure", name, e);
        }
    }

    /** Test hook: true if a provider was disabled after a failure. */
    public static boolean isDisabled(String name) {
        return DISABLED.contains(name);
    }
}
