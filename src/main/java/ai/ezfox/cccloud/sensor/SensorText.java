package ai.ezfox.cccloud.sensor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain English for what an Advanced Cloud Sensor saw (docs/SPEC.md "Advanced Cloud Sensor"): "Steve", "Steve and
 * Alex", "3 Zombies and a Skeleton", "2 players". Players come first (by name, or counted as "a player" / "2 players"
 * in the public form), then the other entities grouped by name with their count. Used for alerts, push messages, the
 * rules' {@code {who}} and the card's summary line. Pure Java, unit tested.
 */
public final class SensorText {
    /** Groups named before "and N more". */
    public static final int MAX_PARTS = 4;
    private static final Map<String, String> IRREGULAR = Map.ofEntries(
        Map.entry("sheep", "sheep"), Map.entry("fish", "fish"), Map.entry("cod", "cod"), Map.entry("salmon", "salmon"),
        Map.entry("pufferfish", "pufferfish"), Map.entry("tropical fish", "tropical fish"), Map.entry("silverfish", "silverfish"),
        Map.entry("wolf", "wolves"), Map.entry("axolotl", "axolotls"), Map.entry("trader llama", "trader llamas"),
        Map.entry("mooshroom", "mooshrooms"), Map.entry("goat", "goats"), Map.entry("glass", "glass"));

    private SensorText() {
    }

    /**
     * Who: players first, then the others grouped by name.
     *
     * @param names true = player names (operators, push), false = the public form ("a player")
     */
    public static String who(List<SensorEntry> entries, boolean names) {
        return who(entries, names, 0);
    }

    /** As above; {@code more} entities entered beyond the listed ones ("… and 12 more"). */
    public static String who(List<SensorEntry> entries, boolean names, int more) {
        var players = new ArrayList<String>();
        int anonymous = 0;
        var others = new LinkedHashMap<String, Integer>();
        for (var e : entries) {
            if (e.kind() == SensorKind.PLAYER) {
                if (names && e.named()) {
                    if (players.stream().noneMatch(p -> p.equalsIgnoreCase(e.name()))) players.add(e.name());
                } else {
                    anonymous++;
                }
            } else {
                others.merge(e.label(), 1, Integer::sum);
            }
        }
        // parts with the number of entities each stands for (to count what "N more" leaves out)
        var parts = new ArrayList<String>(players);
        var counts = new ArrayList<Integer>();
        for (int i = 0; i < players.size(); i++) counts.add(1);
        if (anonymous > 0) {
            parts.add(anonymous == 1 ? SensorEntry.A_PLAYER : anonymous + " players");
            counts.add(anonymous);
        }
        for (var e : others.entrySet()) {
            parts.add(e.getValue() == 1 ? article(e.getKey()) + " " + e.getKey() : e.getValue() + " " + plural(e.getKey()));
            counts.add(e.getValue());
        }
        if (parts.isEmpty() && more <= 0) return "nobody";
        int extra = Math.max(0, more);
        if (parts.size() + (extra > 0 ? 1 : 0) > MAX_PARTS) {
            for (int i = MAX_PARTS - 1; i < counts.size(); i++) extra += counts.get(i);
            parts = new ArrayList<>(parts.subList(0, MAX_PARTS - 1));
        }
        if (extra > 0) parts.add(extra + " more");
        if (parts.size() == 1) return parts.getFirst();
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    /** "Steve entered Reactor room" / "A Zombie entered Reactor room" (a sentence; player names keep their case). */
    public static String entered(List<SensorEntry> entries, boolean names, String where) {
        return entered(entries, names, 0, where);
    }

    public static String entered(List<SensorEntry> entries, boolean names, int more, String where) {
        return sentence(entries, names, who(entries, names, more)) + " entered " + where;
    }

    /**
     * {@code who} at the start of a sentence: upper case first letter ("A player", "A Zombie"), except when it starts
     * with a player's name, which keeps its case ("sensor_bob and a Zombie").
     */
    public static String sentence(List<SensorEntry> entries, boolean names, String who) {
        boolean startsWithName = names && entries.stream().anyMatch(e -> e.kind() == SensorKind.PLAYER && e.named());
        return startsWithName ? who : capitalize(who);
    }

    public static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    static String article(String word) {
        if (word.isEmpty()) return "a";
        char c = Character.toLowerCase(word.charAt(0));
        return "aeiou".indexOf(c) >= 0 ? "an" : "a";
    }

    /** English plural of an entity or item name ("Zombie" → "Zombies", "Enderman" → "Endermen", "Wolf" → "Wolves"). */
    public static String plural(String word) {
        if (word.isEmpty()) return word;
        // only the last word changes ("Iron Golem" → "Iron Golems")
        int space = word.lastIndexOf(' ');
        String head = space < 0 ? "" : word.substring(0, space + 1);
        String last = space < 0 ? word : word.substring(space + 1);
        String lower = word.toLowerCase(Locale.ROOT);
        String irregular = IRREGULAR.get(lower);
        if (irregular == null) irregular = IRREGULAR.get(last.toLowerCase(Locale.ROOT));
        if (irregular != null) return head + matchCase(last, irregular.substring(irregular.lastIndexOf(' ') + 1));
        String l = last.toLowerCase(Locale.ROOT);
        String p;
        if (l.endsWith("man")) p = last.substring(0, last.length() - 3) + "men";
        else if (l.endsWith("y") && l.length() > 1 && "aeiou".indexOf(l.charAt(l.length() - 2)) < 0) p = last.substring(0, last.length() - 1) + "ies";
        else if (l.endsWith("s") || l.endsWith("x") || l.endsWith("z") || l.endsWith("ch") || l.endsWith("sh")) p = last + "es";
        else p = last + "s";
        return head + p;
    }

    private static String matchCase(String original, String replacement) {
        if (!original.isEmpty() && Character.isUpperCase(original.charAt(0))) return capitalize(replacement);
        return replacement;
    }
}
