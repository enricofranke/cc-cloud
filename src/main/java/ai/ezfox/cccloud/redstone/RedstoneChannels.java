package ai.ezfox.cccloud.redstone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Wireless redstone over the cloud network, the pure part (docs/SPEC.md "Wireless redstone over the network"): per
 * mainframe network, a receiver outputs the highest signal (0–15) of all connected transmitters on its channel. At most
 * {@link #MAX_CHANNELS} channels per network (alphabetically first ones win; the others stay at 0). An operator
 * override replaces a channel's level until it expires. Unit tested.
 */
public final class RedstoneChannels {
    public static final int MAX_CHANNELS = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,32}");

    /** A connected uplink in transmitter or receiver mode. */
    public record Member(String mainframe, String channel, boolean transmitter, int level) {
    }

    /** @param until millis when it ends, 0 = until reset */
    public record Override(int level, long until) {
        public boolean active(long now) {
            return until == 0 || now < until;
        }
    }

    /** @param signal the transmitters' max; {@code level} the effective output (the override wins) */
    public record Channel(String name, int level, int signal, int transmitters, int receivers, @Nullable Override override) {
    }

    private RedstoneChannels() {
    }

    public static boolean validName(@Nullable String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** @return mainframe → channel name → channel (sorted, at most {@link #MAX_CHANNELS}) */
    public static Map<String, Map<String, Channel>> compute(List<Member> members, Map<String, Map<String, Override>> overrides, long now) {
        var names = new HashMap<String, TreeSet<String>>();
        for (var m : members) if (validName(m.channel())) names.computeIfAbsent(m.mainframe(), k -> new TreeSet<>()).add(m.channel());
        for (var e : overrides.entrySet()) {
            for (var o : e.getValue().entrySet()) if (o.getValue().active(now)) names.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).add(o.getKey());
        }
        var result = new HashMap<String, Map<String, Channel>>();
        for (var e : names.entrySet()) {
            var kept = new TreeSet<String>();
            for (var name : e.getValue()) {
                if (kept.size() >= MAX_CHANNELS) break;
                kept.add(name);
            }
            var signal = new HashMap<String, Integer>();
            var tx = new HashMap<String, Integer>();
            var rx = new HashMap<String, Integer>();
            for (var m : members) {
                if (!m.mainframe().equals(e.getKey()) || !kept.contains(m.channel())) continue;
                if (m.transmitter()) {
                    signal.merge(m.channel(), Math.max(0, Math.min(15, m.level())), Math::max);
                    tx.merge(m.channel(), 1, Integer::sum);
                } else {
                    rx.merge(m.channel(), 1, Integer::sum);
                }
            }
            var channels = new TreeMap<String, Channel>();
            var mfOverrides = overrides.getOrDefault(e.getKey(), Map.of());
            for (var name : kept) {
                int s = signal.getOrDefault(name, 0);
                var o = mfOverrides.get(name);
                if (o != null && !o.active(now)) o = null;
                channels.put(name, new Channel(name, o != null ? Math.max(0, Math.min(15, o.level())) : s, s, tx.getOrDefault(name, 0),
                    rx.getOrDefault(name, 0), o));
            }
            result.put(e.getKey(), channels);
        }
        return result;
    }

    /** The level a receiver on {@code channel} outputs (0 if the channel was dropped). */
    public static int levelFor(Map<String, Map<String, Channel>> state, String mainframe, String channel) {
        var channels = state.get(mainframe);
        var c = channels == null ? null : channels.get(channel);
        return c == null ? 0 : c.level();
    }
}
