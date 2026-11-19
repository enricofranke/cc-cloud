package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * What a computer sees of the blocks next to it: for every side with a supported machine the same ready-made card the
 * dashboard shows (the device card readers, {@link DeviceCards}), read once a second on the server thread and handed
 * to the program as a snapshot. The program reads values and calls the card's interactive widgets; a call is checked
 * on the server thread like a dashboard press ({@link ActionValidator}: interactive widgets only, PIN widgets
 * refused) and its answer comes back as a {@code peripheral_result} event.
 *
 * <p>Claims: a machine in a chunk claimed by a team the computer's owner is not in or allied with reads as blocked (no
 * values, every call refused with {@link ClaimGuard#ERROR}), checked on every refresh and before every call.
 */
final class ComputerPeripherals {
    static final int REFRESH_TICKS = 20;
    static final int MAX_PENDING = 16;
    static final int CALLS_PER_TICK = 8;

    /** A side's machine as the program sees it. Immutable. */
    record View(String kind, String title, boolean blocked, List<Object> widgets, Map<String, Object> values) {
    }

    private record Call(int id, ComputerSide side, String widget, @Nullable Object value) {
    }

    private static final class Slot {
        final DeviceCard card;
        final Map<String, Widget> widgets = new LinkedHashMap<>();
        final Map<String, JsonElement> values = new LinkedHashMap<>();
        boolean blocked;

        Slot(DeviceCard card) {
            this.card = card;
        }
    }

    private final EnumMap<ComputerSide, Slot> slots = new EnumMap<>(ComputerSide.class);
    private final ConcurrentLinkedQueue<Call> calls = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private volatile Map<ComputerSide, View> views = Map.of();
    private int ticks;

    // ---- program side (any thread) -------------------------------------------------------------------------------------

    Map<ComputerSide, View> views() {
        return views;
    }

    /** Queues a call of a card widget; returns its id (the result comes as an event). */
    int call(ComputerSide side, String widget, @Nullable Object value) {
        if (!views.containsKey(side)) throw new ApiError("no peripheral on side '" + side.id() + "'");
        if (pending.incrementAndGet() > MAX_PENDING) {
            pending.decrementAndGet();
            throw new ApiError("too many peripheral calls at once");
        }
        int id = nextId.getAndUpdate(i -> i == Integer.MAX_VALUE ? 1 : i + 1);
        calls.add(new Call(id, side, widget, value));
        return id;
    }

    // ---- server side ---------------------------------------------------------------------------------------------------

    /**
     * Server tick: runs waiting calls (answers through {@code events}), refreshes the cards once a second and reports
     * machines that came or went ({@code peripheral}/{@code peripheral_detach} events).
     */
    void tick(ComputerHost host, boolean on, Events events) {
        ServerLevel level = host.level();
        if (level == null) return;
        for (int i = 0; i < CALLS_PER_TICK; i++) {
            var call = calls.poll();
            if (call == null) break;
            pending.decrementAndGet();
            String error = run(level, host, call);
            events.queue("peripheral_result", (long) call.id(), error == null, error);
        }
        if (++ticks % REFRESH_TICKS != 1) return;
        var before = views.keySet();
        var next = new EnumMap<ComputerSide, View>(ComputerSide.class);
        for (var side : ComputerSide.all()) {
            var dir = host.direction(side);
            if (dir == null) continue;
            BlockPos pos = host.pos().relative(dir);
            var slot = slots.get(side);
            if (slot != null && !DeviceCards.isValid(slot.card)) slot = null;
            if (slot == null) {
                var card = DeviceCards.detect(level, pos, dir.getOpposite());
                if (card == null) {
                    slots.remove(side);
                    continue;
                }
                slot = new Slot(card);
                var widgets = DeviceCards.widgets(card);
                if (widgets == null) {
                    slots.remove(side);
                    continue;
                }
                for (var w : widgets) slot.widgets.put(w.id(), w);
                slots.put(side, slot);
            }
            slot.blocked = blocked(level, pos, host.owner());
            if (!slot.blocked) {
                var s = slot;
                boolean ok = DeviceCards.refresh(s.card, new DeviceCard.CardOutput() {
                    @Override
                    public void value(String widgetId, JsonElement value) {
                        s.values.put(widgetId, value);
                    }

                    @Override
                    public void alert(String text, String level) {
                        // alerts are for the dashboard
                    }
                });
                if (!ok) {
                    slots.remove(side);
                    continue;
                }
            }
            next.put(side, view(slot));
        }
        views = Map.copyOf(next);
        if (!on) return;
        for (var side : next.keySet()) if (!before.contains(side)) events.queue("peripheral", side.id());
        for (var side : before) if (!next.containsKey(side)) events.queue("peripheral_detach", side.id());
    }

    /** Forgets the cards (the computer turned off or was unloaded). */
    void clear() {
        slots.clear();
        views = Map.of();
        calls.clear();
        pending.set(0);
        ticks = 0;
    }

    private @Nullable String run(ServerLevel level, ComputerHost host, Call call) {
        var slot = slots.get(call.side());
        var dir = host.direction(call.side());
        if (slot == null || dir == null || !DeviceCards.isValid(slot.card)) return "unavailable";
        if (blocked(level, host.pos().relative(dir), host.owner())) return ClaimGuard.ERROR;
        var widget = slot.widgets.get(call.widget());
        var result = ActionValidator.validate(widget, json(call.value()), null, false);
        if (!result.ok()) return result.error();
        return DeviceCards.action(slot.card, call.widget(), result.value(), "computer");
    }

    private static boolean blocked(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        return ClaimGuard.blockedAt(level, pos, owner == null ? List.of() : List.of(owner));
    }

    private static View view(Slot slot) {
        var widgets = new ArrayList<Object>();
        for (var w : slot.widgets.values()) {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", w.id());
            row.put("type", w.type().name().toLowerCase(java.util.Locale.ROOT));
            if (w.label() != null) row.put("label", w.label());
            row.put("interactive", w.type().isInteractive());
            widgets.add(row);
        }
        var values = new LinkedHashMap<String, Object>();
        if (!slot.blocked) {
            for (var e : slot.values.entrySet()) {
                var v = java(e.getValue(), 0);
                if (v != null) values.put(e.getKey(), v);
            }
        }
        return new View(slot.card.kind(), DeviceCards.title(slot.card), slot.blocked, List.copyOf(widgets), values);
    }

    /** A card value as plain Java for the program (depth and size bounded). */
    static @Nullable Object java(@Nullable JsonElement e, int depth) {
        if (e == null || e.isJsonNull() || depth > 8) return null;
        if (e instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return d == Math.rint(d) && Math.abs(d) < 0x1p53 ? (Object) (long) d : (Object) d;
            }
            return p.getAsString();
        }
        if (e instanceof JsonArray a) {
            var list = new ArrayList<Object>();
            for (int i = 0; i < Math.min(a.size(), 256); i++) {
                var v = java(a.get(i), depth + 1);
                list.add(v == null ? false : v);
            }
            return list;
        }
        var map = new LinkedHashMap<String, Object>();
        int n = 0;
        for (var entry : ((JsonObject) e).entrySet()) {
            if (++n > 256) break;
            var v = java(entry.getValue(), depth + 1);
            if (v != null) map.put(entry.getKey(), v);
        }
        return map;
    }

    private static @Nullable JsonElement json(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> new JsonPrimitive(b);
            case Number n -> new JsonPrimitive(n);
            case String s -> new JsonPrimitive(s);
            default -> null;
        };
    }

    /** Where answers and attach events go. */
    interface Events {
        void queue(String name, Object... args);
    }
}
