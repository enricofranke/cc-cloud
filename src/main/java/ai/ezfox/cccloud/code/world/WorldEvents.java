package ai.ezfox.cccloud.code.world;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ApiSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Events between two snapshots, and the filters of {@code cloud.on}/{@code cloud.wait}. Raw events are {@code change}
 * (a widget value), {@code online}/{@code offline} (a card or a device without a card) and {@code redstone} (a channel
 * level); {@code above}/{@code below} are changes that cross a filter's threshold. A widget or card seen for the first
 * time fires nothing: events are changes, not states.
 */
public final class WorldEvents {
    private WorldEvents() {
    }

    /** One event: {@code type} plus the {@code Event} fields of the descriptor. */
    public record Event(String type, @Nullable String card, @Nullable String title, @Nullable String widget, @Nullable Object value, @Nullable Object old,
                        @Nullable String channel, long time) {
        /** The table the handler gets. */
        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("type", type);
            if (card != null) map.put("card", card);
            if (title != null) map.put("title", title);
            if (widget != null) map.put("widget", widget);
            if (value != null) map.put("value", value);
            if (old != null) map.put("old", old);
            if (channel != null) map.put("channel", channel);
            map.put("time", time);
            return map;
        }

        Event as(String type) {
            return new Event(type, card, title, widget, value, old, channel, time);
        }
    }

    /** Every raw event from {@code before} to {@code after} (null before = the first snapshot: nothing). */
    public static List<Event> diff(@Nullable WorldSnapshot before, WorldSnapshot after) {
        var out = new ArrayList<Event>();
        if (before == null) return out;
        long time = after.time();
        var old = before.cardsById();
        var cardIds = new java.util.HashSet<String>();
        for (var card : after.cardsById().values()) {
            cardIds.add(card.id());
            var was = old.get(card.id());
            if (was == null) continue;
            if (was.online() != card.online()) out.add(new Event(card.online() ? "online" : "offline", card.id(), card.title(), null, null, null, null, time));
            for (var w : card.widgets()) {
                var prev = was.widget(w.id());
                if (prev == null || Objects.equals(prev.value(), w.value())) continue;
                out.add(new Event("change", card.id(), card.title(), w.id(), w.value(), prev.value(), null, time));
            }
        }
        // devices without a card of their own (relays, switches): online/offline under their device id
        var oldDevices = new java.util.HashMap<String, WorldSnapshot.Device>();
        for (var d : before.devices()) oldDevices.put(d.id(), d);
        for (var d : after.devices()) {
            if (cardIds.contains(d.id())) continue;
            var was = oldDevices.get(d.id());
            if (was != null && was.online() != d.online()) out.add(new Event(d.online() ? "online" : "offline", d.id(), d.name(), null, null, null, null, time));
        }
        for (var e : after.redstone().entrySet()) {
            Integer was = before.redstone().get(e.getKey());
            if (was != null && !was.equals(e.getValue())) out.add(new Event("redstone", null, null, null, e.getValue(), was, e.getKey(), time));
        }
        return out;
    }

    /** A checked filter of {@code cloud.on}/{@code cloud.wait}. */
    public record Filter(String event, @Nullable String card, @Nullable String widget, @Nullable Double value, @Nullable String channel) {
        /** The event this filter lets through for a raw event, or null (above/below come from changes). */
        public @Nullable Event match(Event e, WorldSnapshot now) {
            return switch (event) {
                case "change" -> e.type().equals("change") && cardMatches(e, now) && (widget == null || widget.equals(e.widget())) ? e : null;
                case "above", "below" -> {
                    if (!e.type().equals("change") || !cardMatches(e, now) || !Objects.equals(widget, e.widget()) || value == null) yield null;
                    Double a = number(e.old()), b = number(e.value());
                    if (a == null || b == null) yield null;
                    boolean crossed = event.equals("above") ? a <= value && b > value : a >= value && b < value;
                    yield crossed ? e.as(event) : null;
                }
                case "online", "offline" -> e.type().equals(event) && cardMatches(e, now) ? e : null;
                case "redstone" -> e.type().equals("redstone") && (channel == null || channel.equals(e.channel())) ? e : null;
                default -> null;
            };
        }

        private boolean cardMatches(Event e, WorldSnapshot now) {
            if (card == null) return true;
            if (card.equals(e.card())) return true;
            return e.title() != null && card.toLowerCase(Locale.ROOT).equals(e.title().toLowerCase(Locale.ROOT)) && firstWithTitle(now, e);
        }

        /** A title names the first card with it (like {@link WorldSnapshot#card}); a second card with the same title does not match. */
        private boolean firstWithTitle(WorldSnapshot now, Event e) {
            var c = now.card(card);
            return c == null || c.id().equals(e.card());
        }
    }

    /** Booleans count as 0/1. */
    static @Nullable Double number(@Nullable Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        return null;
    }

    /**
     * Checks an event name and its filter against the descriptor: unknown events and fields are script errors, and
     * above/below need card, widget and a numeric value.
     */
    public static Filter filter(String event, @Nullable Map<String, Object> filter, String function) {
        var spec = ApiSpec.get().event(event);
        if (spec == null) {
            var names = ApiSpec.get().events().stream().map(ApiSpec.Event::name).toList();
            throw new ApiError("cloud." + function + ": unknown event '" + event + "' (one of " + String.join(", ", names) + ")");
        }
        var f = filter == null ? Map.<String, Object>of() : filter;
        for (var key : f.keySet()) {
            if (!spec.filter().contains(key)) {
                throw new ApiError("cloud." + function + ": '" + event + "' has no filter field '" + key + "' (" + String.join(", ", spec.filter()) + ")");
            }
        }
        String card = text(f.get("card"), "card", function);
        String widget = text(f.get("widget"), "widget", function);
        String channel = text(f.get("channel"), "channel", function);
        Double value = null;
        if (f.get("value") != null) {
            if (!(f.get("value") instanceof Number n)) throw new ApiError("cloud." + function + ": filter field 'value' must be a number");
            value = n.doubleValue();
        }
        if ((event.equals("above") || event.equals("below")) && (card == null || widget == null || value == null)) {
            throw new ApiError("cloud." + function + ": '" + event + "' needs {card=, widget=, value=}");
        }
        return new Filter(event, card, widget, value, channel);
    }

    private static @Nullable String text(@Nullable Object v, String field, String function) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return Long.toString(n.longValue());
        throw new ApiError("cloud." + function + ": filter field '" + field + "' must be a string");
    }
}
