package ai.ezfox.cccloud.code.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a script can read about its mainframe at one moment: an immutable copy made from what the dashboard shows
 * (cards and widgets), the network, the wireless redstone channels, rules and scenes. Scripts never touch the world
 * themselves: they read the newest snapshot (made a few times per second off the server thread, see
 * {@code CodeService}) and their actions go to the server thread as {@link WorldAction}s. A snapshot is made for one
 * set of rights: cards the author may not see are not in it.
 */
public record WorldSnapshot(long seq, long time, long gameTime, Mainframe mainframe, List<Card> cards, List<Device> devices, Map<String, Integer> redstone,
                            List<RuleInfo> rules, List<SceneInfo> scenes) {
    /** The pseudo card of the mainframe itself ({@code cloud.getValue("mainframe", "percent")}). */
    public static final String MAINFRAME_CARD = "mainframe";

    public record Mainframe(String id, String name, boolean online, boolean enabled, long energy, long capacity, long usage) {
        public double percent() {
            return capacity <= 0 ? 0 : Math.round(1000.0 * energy / capacity) / 10.0;
        }
    }

    /**
     * A dashboard card. {@code kind}: {@code lua} for program cards, else the template ({@code storage}, {@code redstone},
     * device kinds), {@code auto} for automatic computer cards, {@code mainframe} for the pseudo card.
     */
    public record Card(String id, String title, boolean online, String kind, List<Widget> widgets, boolean blocked) {
        public @Nullable Widget widget(String id) {
            for (var w : widgets) if (w.id().equals(id)) return w;
            return null;
        }
    }

    /** {@code value}: null, Boolean, Double, String, or a List/Map for structured widgets (lists, jobs). */
    public record Widget(String id, String type, @Nullable String label, @Nullable Object value, @Nullable String unit, @Nullable Double min, @Nullable Double max,
                         boolean pin, boolean confirm) {
    }

    public record Device(String id, String type, String name, String status) {
        public boolean online() {
            return "online".equals(status);
        }
    }

    public record RuleInfo(String id, String name, boolean enabled, String trigger, long runs, @Nullable String lastResult) {
    }

    public record SceneInfo(String id, String name, boolean needsPin, boolean confirm, boolean running) {
    }

    public WorldSnapshot {
        cards = List.copyOf(cards);
        devices = List.copyOf(devices);
        redstone = Map.copyOf(redstone);
        rules = List.copyOf(rules);
        scenes = List.copyOf(scenes);
    }

    /** The mainframe as a card with the widgets energy, capacity, percent, usage and enabled. */
    public Card mainframeCard() {
        var m = mainframe;
        var widgets = new ArrayList<Widget>(5);
        widgets.add(new Widget("energy", "number", "Energy", (double) m.energy(), "FE", 0.0, (double) m.capacity(), false, false));
        widgets.add(new Widget("capacity", "number", "Capacity", (double) m.capacity(), "FE", null, null, false, false));
        widgets.add(new Widget("percent", "gauge", "Energy", m.percent(), "%", 0.0, 100.0, false, false));
        widgets.add(new Widget("usage", "number", "Usage", (double) m.usage(), "FE/t", null, null, false, false));
        widgets.add(new Widget("enabled", "toggle", "Dashboard", m.enabled(), null, null, null, false, false));
        return new Card(MAINFRAME_CARD, m.name(), m.online(), "mainframe", widgets, false);
    }

    /** A card by id, by title (case-insensitive, the first match) or {@code "mainframe"}; null if there is none. */
    public @Nullable Card card(String ref) {
        if (ref.equals(MAINFRAME_CARD)) return mainframeCard();
        for (var c : cards) if (c.id().equals(ref)) return c;
        String lower = ref.toLowerCase(Locale.ROOT);
        for (var c : cards) if (c.title().toLowerCase(Locale.ROOT).equals(lower)) return c;
        return null;
    }

    public @Nullable Device device(String ref) {
        for (var d : devices) if (d.id().equals(ref)) return d;
        String lower = ref.toLowerCase(Locale.ROOT);
        for (var d : devices) if (d.name().toLowerCase(Locale.ROOT).equals(lower)) return d;
        return null;
    }

    public @Nullable RuleInfo rule(String ref) {
        for (var r : rules) if (r.id().equals(ref)) return r;
        String lower = ref.toLowerCase(Locale.ROOT);
        for (var r : rules) if (r.name().toLowerCase(Locale.ROOT).equals(lower)) return r;
        return null;
    }

    public @Nullable SceneInfo scene(String ref) {
        for (var s : scenes) if (s.id().equals(ref)) return s;
        String lower = ref.toLowerCase(Locale.ROOT);
        for (var s : scenes) if (s.name().toLowerCase(Locale.ROOT).equals(lower)) return s;
        return null;
    }

    /** Every card including the mainframe pseudo card, id → card (for event detection). */
    public Map<String, Card> cardsById() {
        var map = new LinkedHashMap<String, Card>();
        for (var c : cards) map.put(c.id(), c);
        map.put(MAINFRAME_CARD, mainframeCard());
        return map;
    }

    /** A snapshot of nothing (before the first real one arrives, e.g. in unit tests). */
    public static WorldSnapshot empty(String mainframeId, String name) {
        return new WorldSnapshot(0, 0, 0, new Mainframe(mainframeId, name, false, false, 0, 0, 0), List.of(), List.of(), Map.of(), List.of(), List.of());
    }
}
