package ai.ezfox.cccloud.push;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One push target of a mainframe (docs/SPEC.md v0.5 D): a Discord webhook, an ntfy topic or a generic JSON webhook.
 * The URL (and the ntfy token) are secrets: stored on the server, never sent back in full ({@link #publicJson}).
 *
 * @param minLevel the lowest alert level that is pushed ({@code info|warn|error})
 */
public record PushTarget(String id, Type type, String name, String url, String token, String minLevel, Set<Event> events, boolean enabled) {
    public static final int MAX_NAME = 32;
    public static final int MAX_URL = 512;
    public static final int MAX_TOKEN = 128;
    static final Pattern ID = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.~+/=-]{1,128}");

    public enum Type {
        DISCORD("discord"), NTFY("ntfy"), WEBHOOK("webhook");

        public final String id;

        Type(String id) {
            this.id = id;
        }

        public static @Nullable Type byId(@Nullable String id) {
            for (var t : values()) if (t.id.equals(id)) return t;
            return null;
        }
    }

    /** What a target receives. */
    public enum Event {
        /** Alerts of scripts, cards and rule alert actions (from {@code minLevel} up). */
        ALERTS("alerts"),
        /** Rule runs of rules with {@code notify}. */
        RULES("rules"),
        /** A browser waits for in-game approval. */
        APPROVALS("approvals"),
        /** The mainframe lost or regained power. */
        POWER("power");

        public final String id;

        Event(String id) {
            this.id = id;
        }

        public static @Nullable Event byId(@Nullable String id) {
            for (var e : values()) if (e.id.equals(id)) return e;
            return null;
        }
    }

    public PushTarget {
        events = Set.copyOf(events);
    }

    /** Level order: info < warn < error (Lua's {@code crit} counts as error). */
    public static int rank(@Nullable String level) {
        if (level == null) return 0;
        return switch (level) {
            case "warn" -> 1;
            case "error", "crit" -> 2;
            default -> 0;
        };
    }

    public boolean wants(Event event, String level) {
        return enabled && events.contains(event) && (event != Event.ALERTS || rank(level) >= rank(minLevel));
    }

    /** The target without its secrets: type, name, host + the URL's last 4 characters, whether a token is set. */
    public JsonObject publicJson() {
        var json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", type.id);
        json.addProperty("name", name);
        json.addProperty("url", mask(url));
        json.addProperty("hasToken", !token.isEmpty());
        json.addProperty("minLevel", minLevel);
        var ev = new JsonArray();
        for (var e : Event.values()) if (events.contains(e)) ev.add(e.id);
        json.add("events", ev);
        json.addProperty("enabled", enabled);
        return json;
    }

    /** {@code https://discord.com/…a1b2}: scheme and host, then only the last 4 characters. */
    public static String mask(String url) {
        int start = url.indexOf("://");
        int slash = start < 0 ? -1 : url.indexOf('/', start + 3);
        String base = slash < 0 ? url : url.substring(0, slash);
        if (slash < 0) return base;
        String tail = url.length() - slash > 8 ? url.substring(url.length() - 4) : "";
        return base + "/…" + tail;
    }

    /** Storage form (with secrets). */
    public JsonObject toJson() {
        var json = publicJson();
        json.addProperty("url", url);
        json.remove("hasToken");
        if (!token.isEmpty()) json.addProperty("token", token);
        return json;
    }

    public record Parsed(@Nullable PushTarget target, @Nullable String error) {
    }

    /**
     * Parses a target from the browser (strict) or from storage. With {@code previous} (an update), a missing
     * {@code url}/{@code token} keeps the stored secret; {@code token:""} removes it.
     */
    public static Parsed parse(@Nullable JsonElement element, String id, @Nullable PushTarget previous, Set<String> allowedHosts) {
        if (!(element instanceof JsonObject obj)) return new Parsed(null, "invalid");
        for (var key : obj.keySet()) {
            if (!Set.of("id", "type", "name", "url", "token", "minLevel", "events", "enabled").contains(key)) return new Parsed(null, "invalid");
        }
        Type type = Type.byId(str(obj.get("type")));
        if (type == null) type = previous == null ? null : previous.type();
        if (type == null) return new Parsed(null, "invalid");
        if (previous != null && type != previous.type() && !obj.has("url")) return new Parsed(null, "invalid"); // a new type needs a new URL
        String name = obj.has("name") ? str(obj.get("name")) : previous != null ? previous.name() : type.id;
        if (name == null || name.isBlank() || name.length() > MAX_NAME || name.chars().anyMatch(c -> c < 0x20)) return new Parsed(null, "invalid");
        String url = obj.has("url") ? str(obj.get("url")) : previous != null ? previous.url() : null;
        if (url == null || url.length() > MAX_URL) return new Parsed(null, "invalid");
        String urlError = UrlPolicy.check(type, url, allowedHosts);
        if (urlError != null) return new Parsed(null, urlError);
        // a stored token stays only while the host stays the same (L8: never send it to a new host)
        boolean sameHost = previous != null && host(previous.url()).equals(host(url));
        String token = obj.has("token") ? str(obj.get("token")) : previous != null && sameHost ? previous.token() : "";
        if (token == null || (!token.isEmpty() && (!TOKEN.matcher(token).matches() || type != Type.NTFY))) return new Parsed(null, "invalid");
        String minLevel = obj.has("minLevel") ? str(obj.get("minLevel")) : previous != null ? previous.minLevel() : "warn";
        if (minLevel == null || !Set.of("info", "warn", "error").contains(minLevel)) return new Parsed(null, "invalid");
        Set<Event> events;
        if (obj.has("events")) {
            if (!(obj.get("events") instanceof JsonArray array) || array.size() > Event.values().length) return new Parsed(null, "invalid");
            var set = EnumSet.noneOf(Event.class);
            for (var e : array) {
                var ev = Event.byId(str(e));
                if (ev == null) return new Parsed(null, "invalid");
                set.add(ev);
            }
            events = set;
        } else {
            events = previous != null ? previous.events() : EnumSet.of(Event.ALERTS, Event.RULES, Event.POWER);
        }
        boolean enabled;
        if (obj.has("enabled")) {
            if (!(obj.get("enabled") instanceof JsonPrimitive p) || !p.isBoolean()) return new Parsed(null, "invalid");
            enabled = p.getAsBoolean();
        } else {
            enabled = previous == null || previous.enabled();
        }
        return new Parsed(new PushTarget(id, type, name.strip(), url, token, minLevel, events, enabled), null);
    }

    private static String host(String url) {
        try {
            var h = new java.net.URI(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (java.net.URISyntaxException e) {
            return "";
        }
    }

    private static @Nullable String str(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
