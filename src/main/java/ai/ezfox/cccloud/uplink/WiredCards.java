package ai.ezfox.cccloud.uplink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The pure rules of the wired machine cards (docs/SPEC.md "CC wired networks"), kept free of CC: Tweaked classes so they
 * are unit tested on their own: which peripherals become cards, their titles, and how a peripheral name is resolved.
 */
public final class WiredCards {
    private WiredCards() {
    }

    /** A peripheral that is a block: the computer's name for it and its position. */
    public record Candidate(String name, BlockPos pos) {
    }

    /** The machines to look at, in name order: one per position (the first name wins), none at {@code taken} positions. */
    public static List<Candidate> candidates(List<Candidate> all, Set<BlockPos> taken) {
        var sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(Candidate::name));
        var seen = new HashSet<BlockPos>();
        var out = new ArrayList<Candidate>();
        for (var c : sorted) {
            if (taken.contains(c.pos()) || !seen.add(c.pos())) continue;
            out.add(c);
        }
        return out;
    }

    /**
     * Card titles: the card's own title ("Energy Cell"); cards of one uplink with the same title get the computer's name
     * for them without the namespace ("Energy Cell (energy_cell_2)").
     *
     * @param titles card title per peripheral name, in card order
     */
    public static List<String> titles(List<Map.Entry<String, String>> titles) {
        var count = new HashMap<String, Integer>();
        for (var e : titles) count.merge(e.getValue(), 1, Integer::sum);
        var out = new ArrayList<String>();
        for (var e : titles) {
            String name = e.getKey();
            String local = name.substring(name.indexOf(':') + 1);
            String title = count.get(e.getValue()) > 1 ? e.getValue() + " (" + local + ")" : e.getValue();
            out.add(title.length() > Limits.TITLE ? title.substring(0, Limits.TITLE) : title);
        }
        return out;
    }

    /**
     * A peripheral by name: what the uplink's attachment reaches (the computer's sides, or the wired network when the
     * uplink itself sits behind a modem), else what the computer reaches through its own wired modems.
     */
    public static <P> @Nullable P reachable(String name, Function<String, @Nullable P> attached, Function<String, @Nullable P> wired) {
        var peripheral = attached.apply(name);
        return peripheral != null ? peripheral : wired.apply(name);
    }
}
