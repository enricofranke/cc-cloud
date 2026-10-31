package ai.ezfox.cccloud.display;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The linked Cloud Displays of each mainframe, for the dashboard: an origin block registers itself while it ticks and
 * when it is configured, and leaves when it is broken, unlinked or stops being the origin. A display whose chunk
 * unloads stays listed as unloaded (last known size and configuration) until the chunk returns or the server stops: it
 * can't be configured then, and a display whose chunk never loaded since the server started isn't listed at all.
 * Server thread only.
 */
public final class DisplayRegistry {
    /** What the list shows of a display, also while it is unloaded. */
    public record Info(UUID id, String dim, BlockPos pos, int width, int height, String target, boolean interactive, boolean allowRestricted) {
    }

    private static final class Entry {
        @Nullable CloudDisplayBlockEntity live;
        Info last;

        Entry(CloudDisplayBlockEntity live, Info last) {
            this.live = live;
            this.last = last;
        }
    }

    /** A row of {@link #list}: {@code live} is null while the display is unloaded. */
    public record Row(Info info, @Nullable CloudDisplayBlockEntity live) {
    }

    private static final Map<UUID, Map<UUID, Entry>> DISPLAYS = new HashMap<>();

    private DisplayRegistry() {
    }

    /** The stable, opaque id of a display (the dashboard gets it even without {@code view.positions}). */
    static UUID id(String dim, BlockPos pos) {
        return UUID.nameUUIDFromBytes(("cccloud:display:" + dim + ":" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    static void track(@Nullable UUID mainframe, CloudDisplayBlockEntity origin, Info info) {
        if (mainframe == null) return;
        var entries = DISPLAYS.computeIfAbsent(mainframe, m -> new LinkedHashMap<>());
        var entry = entries.get(info.id());
        if (entry == null) {
            entries.put(info.id(), new Entry(origin, info));
        } else {
            entry.live = origin;
            entry.last = info;
        }
    }

    /** It left this mainframe: broken, unlinked or not the origin any more. */
    static void forget(@Nullable UUID mainframe, UUID id) {
        var entries = mainframe == null ? null : DISPLAYS.get(mainframe);
        if (entries == null) return;
        entries.remove(id);
        if (entries.isEmpty()) DISPLAYS.remove(mainframe);
    }

    /** Its chunk unloaded. */
    static void unloaded(@Nullable UUID mainframe, UUID id, CloudDisplayBlockEntity block) {
        var entries = mainframe == null ? null : DISPLAYS.get(mainframe);
        var entry = entries == null ? null : entries.get(id);
        if (entry != null && entry.live == block) entry.live = null;
    }

    public static List<Row> list(UUID mainframe) {
        var rows = new ArrayList<Row>();
        var entries = DISPLAYS.get(mainframe);
        if (entries != null) for (var entry : entries.values()) rows.add(new Row(entry.last, entry.live));
        return rows;
    }

    public static @Nullable Row find(UUID mainframe, UUID id) {
        var entries = DISPLAYS.get(mainframe);
        var entry = entries == null ? null : entries.get(id);
        return entry == null ? null : new Row(entry.last, entry.live);
    }

    public static void clear() {
        DISPLAYS.clear();
    }
}
