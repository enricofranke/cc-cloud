package ai.ezfox.cccloud.join;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The logic of the join check ({@link JoinCheck}): which players still owe their build id, and whether a build id is this
 * server's. A player is checked once per join: the first hello counts, later ones are ignored, and a player who stays
 * silent for {@link #HELLO_TICKS} counts as a client from before the check. Pure Java, unit tested; server thread only.
 */
public final class BuildCheck {
    /** How long a client has to say its build after joining: 10 seconds of game time. */
    public static final int HELLO_TICKS = 200;
    /** The longest build id on the wire (a real one is about 35 characters). */
    public static final int MAX_ID = 128;
    private static final Pattern ID = Pattern.compile("[0-9A-Za-z.+_-]{1," + MAX_ID + "}");

    /** When each waiting player joined (server ticks). */
    private final Map<UUID, Long> waiting = new HashMap<>();

    public void joined(UUID player, long tick) {
        waiting.put(player, tick);
    }

    /** A hello arrived: true if this player was waiting for one (it is the first). */
    public boolean hello(UUID player) {
        return waiting.remove(player) != null;
    }

    public void left(UUID player) {
        waiting.remove(player);
    }

    /** The players whose time ran out at {@code tick}; they stop waiting. */
    public List<UUID> overdue(long tick) {
        var list = new ArrayList<UUID>();
        for (var it = waiting.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (tick - e.getValue() < HELLO_TICKS) continue;
            list.add(e.getKey());
            it.remove();
        }
        return list;
    }

    /** A build id from a client as it may go into chat and the log, or null if it is not one (a modified client). */
    public static @Nullable String clean(@Nullable String text) {
        return text != null && ID.matcher(text).matches() ? text : null;
    }

    /** Equal ids are the same build; anything else, an unknown id too, differs. */
    public static boolean same(String server, @Nullable String client) {
        return server.equals(client);
    }
}
