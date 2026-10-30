package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Turns a mainframe's Cloud Redstone devices into dashboard cards (docs/SPEC.md "Cloud Redstone devices"): devices
 * with the same group share one card (title = the group name), devices without a group go to one automatic card
 * "Redstone" per mainframe. A card holds at most {@link #MAX_DEVICES_PER_CARD} devices; more continue in "Halle (2)".
 * Cards with two or more switches/lamps get "All on" / "All off". Widgets are built through {@link WidgetFactory}
 * (the validation a Lua script gets). Pure Java, unit tested.
 * <p>
 * Widget ids: {@code d_<12 hex of the device id>} for the main control (toggle, slider, button, indicator), plus
 * {@code …_b} (lamp brightness) and {@code …_l} (sensor level). They stay the same when a device moves to another card,
 * so rules and scenes can follow it ({@link RsDevices#sectionOf}).
 */
public final class RsCards {
    public static final String DEFAULT_TITLE = "Redstone";
    public static final int MAX_DEVICES_PER_CARD = 32;
    public static final String ALL_ON = "all_on";
    public static final String ALL_OFF = "all_off";
    public static final String PREFIX = "d_";
    public static final String BRIGHTNESS = "_b";
    public static final String LEVEL = "_l";
    /** History of lamp light and sensor level (long-term statistics, limited by the mainframe's retention). */
    public static final String HISTORY = "24h";

    /** One card: {@code groupKey} ("" = the automatic card) and {@code part} (0 = first card of the group). */
    public record Card(String groupKey, int part, String title, List<Widget> widgets, List<UUID> devices) {
    }

    public enum Control { MAIN, BRIGHTNESS, LEVEL, ALL_ON, ALL_OFF }

    /** What a widget id addresses: a device (by its widget key) or the card's "All on" / "All off". */
    public record Target(@Nullable String deviceKey, Control control) {
    }

    private RsCards() {
    }

    /** {@code d_} + the first 12 hex digits of the device id. */
    public static String widgetId(UUID id) {
        return PREFIX + id.toString().replace("-", "").substring(0, 12);
    }

    /** The target of a widget id, or null if it is not one of ours. */
    public static @Nullable Target target(String widgetId) {
        if (widgetId.equals(ALL_ON)) return new Target(null, Control.ALL_ON);
        if (widgetId.equals(ALL_OFF)) return new Target(null, Control.ALL_OFF);
        if (!widgetId.startsWith(PREFIX)) return null;
        String rest = widgetId.substring(PREFIX.length());
        Control control = Control.MAIN;
        if (rest.endsWith(BRIGHTNESS)) control = Control.BRIGHTNESS;
        else if (rest.endsWith(LEVEL)) control = Control.LEVEL;
        if (control != Control.MAIN) rest = rest.substring(0, rest.length() - 2);
        if (!rest.matches("[0-9a-f]{12}")) return null;
        return new Target(PREFIX + rest, control);
    }

    /** Stable section id of a card (layouts, bookmarks and share links survive restarts). */
    public static UUID sectionId(UUID mainframeId, String groupKey, int part) {
        return UUID.nameUUIDFromBytes(("cccloud:redstone:" + mainframeId + ":" + part + ":" + groupKey).getBytes(StandardCharsets.UTF_8));
    }

    /** Cards in a stable order: the automatic "Redstone" card first, then the groups by name. */
    public static List<Card> build(Collection<RsDevice> devices) {
        var byGroup = new TreeMap<String, List<RsDevice>>();
        for (var device : devices) byGroup.computeIfAbsent(device.groupKey(), k -> new ArrayList<>()).add(device);
        var cards = new ArrayList<Card>();
        for (var entry : byGroup.entrySet()) {
            var list = entry.getValue();
            list.sort(Comparator.comparing((RsDevice d) -> d.displayName().toLowerCase(Locale.ROOT)).thenComparing(d -> d.id().toString()));
            String title = entry.getKey().isEmpty() ? DEFAULT_TITLE : list.getFirst().group().strip();
            for (int part = 0; part * MAX_DEVICES_PER_CARD < list.size(); part++) {
                var chunk = list.subList(part * MAX_DEVICES_PER_CARD, Math.min(list.size(), (part + 1) * MAX_DEVICES_PER_CARD));
                cards.add(card(entry.getKey(), part, part == 0 ? title : title + " (" + (part + 1) + ")", chunk));
            }
        }
        return cards;
    }

    private static Card card(String groupKey, int part, String title, List<RsDevice> devices) {
        var widgets = new ArrayList<Widget>();
        long switchable = devices.stream().filter(d -> d.kind().switchable()).count();
        if (switchable >= 2) {
            // the buttons ask for what the strictest device asks for
            boolean pin = devices.stream().anyMatch(d -> d.kind().switchable() && d.pin());
            boolean confirm = devices.stream().anyMatch(d -> d.kind().switchable() && d.confirm());
            widgets.add(widget(WidgetType.BUTTON, ALL_ON, "All on", opts("color", "green", "order", 0, "pin", pin ? true : null, "confirm", confirm ? true : null)));
            widgets.add(widget(WidgetType.BUTTON, ALL_OFF, "All off", opts("color", "gray", "order", 1, "pin", pin ? true : null, "confirm", confirm ? true : null)));
        }
        var ids = new ArrayList<UUID>();
        int order = 10;
        for (var device : devices) {
            widgets.addAll(widgets(device, order));
            ids.add(device.id());
            order += 2;
        }
        return new Card(groupKey, part, title, List.copyOf(widgets), List.copyOf(ids));
    }

    /** The widgets of one device (validated like Lua widgets). */
    static List<Widget> widgets(RsDevice d, int order) {
        String id = widgetId(d.id());
        String name = d.displayName();
        Boolean pin = d.pin() ? true : null;
        Boolean confirm = d.confirm() ? true : null;
        return switch (d.kind()) {
            case SWITCH -> d.analog()
                ? List.of(widget(WidgetType.SLIDER, id, name, opts("min", 0, "max", 15, "step", 1, "value", d.level(), "pin", pin, "icon", d.kind().icon,
                    "order", order)))
                : List.of(widget(WidgetType.TOGGLE, id, name, opts("value", d.active(), "pin", pin, "confirm", confirm, "icon", d.kind().icon, "order", order)));
            case BUTTON -> List.of(widget(WidgetType.BUTTON, id, name, opts("color", "cyan", "pin", pin, "confirm", confirm, "icon", d.kind().icon,
                "order", order)));
            case LAMP -> List.of(
                widget(WidgetType.TOGGLE, id, name, opts("value", d.active(), "pin", pin, "confirm", confirm, "icon", d.kind().icon, "order", order)),
                widget(WidgetType.SLIDER, id + BRIGHTNESS, name + " brightness", opts("min", 0, "max", 15, "step", 1, "value", d.level(), "pin", pin,
                    "history", HISTORY, "icon", d.kind().icon, "order", order + 1)));
            case SENSOR -> List.of(
                widget(WidgetType.INDICATOR, id, name, opts("value", d.active(), "onColor", "green", "icon", d.kind().icon, "order", order)),
                widget(WidgetType.GAUGE, id + LEVEL, name + " level", opts("min", 0, "max", 15, "value", d.level(), "history", HISTORY,
                    "icon", d.kind().icon, "order", order + 1)));
        };
    }

    private static Widget widget(WidgetType type, String id, String label, Map<String, Object> opts) {
        try {
            return WidgetFactory.create(type, id, label, opts);
        } catch (WidgetFactory.WidgetException e) {
            throw new IllegalStateException("invalid redstone widget " + id + ": " + e.getMessage(), e);
        }
    }

    /** Key/value pairs; null values are left out. */
    private static Map<String, Object> opts(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) if (pairs[i + 1] != null) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
