package ai.ezfox.cccloud.reports;

import ai.ezfox.cccloud.CloudLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Builds the three kinds of payload field by field: what is not named here is not sent (docs/PRIVACY.md lists every
 * field, and the report service refuses every other field). Building fails closed: anything that throws, and anything
 * {@link #verified} does not accept, means there is no report. Pure Java.
 */
public final class ReportPayload {
    /** The payload format (the {@code v} field); the service refuses versions it does not know. */
    public static final int FORMAT = 1;
    public static final int MAX_FEEDBACK_TEXT = 1500, MAX_CONTACT = 120;

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z.+_-]{1,48}");
    private static final Pattern CODE_NAME = Pattern.compile("[\\p{L}\\p{N}_$.<>]{1,200}");
    private static final Pattern FRAME = Pattern.compile("[\\p{L}\\p{N}_$.<>]{1,240}(?::\\d{1,7})?");
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Set<String> OS = Set.of("windows", "macos", "linux", "other");
    private static final Set<String> INTERNET = Set.of("off", "http", "connect");
    private static final Set<String> CATEGORIES = Set.of("bug", "idea", "other");

    /** Where a failure was seen. Fixed names: nothing about the failure's content goes into this field. */
    public enum Where {
        CRASH_FILE("crash_file"), DEVICE_CARD("device_card"), WEB("web"), WEBSOCKET("websocket"), EXAMPLE("example");

        public final String id;

        Where(String id) {
            this.id = id;
        }
    }

    /** The versions and the platform. {@code os} is one of windows, macos, linux, other. */
    public record Env(String mod, String neoforge, String minecraft, int java, String os) {
        JsonObject json() {
            var json = new JsonObject();
            json.addProperty("mod", version(mod));
            json.addProperty("neoforge", version(neoforge));
            json.addProperty("mc", version(minecraft));
            json.addProperty("java", java);
            json.addProperty("os", OS.contains(os) ? os : "other");
            return json;
        }

        private static String version(String value) {
            return VERSION.matcher(value).matches() ? value : "unknown";
        }
    }

    /** A stats snapshot before bucketing. */
    public record Counts(int mainframes, int uplinks, int cards) {
    }

    private ReportPayload() {
    }

    /** The operating system's family from {@code os.name}. */
    public static String osFamily(@Nullable String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "macos";
        if (n.contains("linux")) return "linux";
        return "other";
    }

    /** A count as a coarse range: the exact size of a base never leaves the server. */
    public static String bucket(int n) {
        if (n <= 0) return "0";
        if (n == 1) return "1";
        if (n <= 5) return "2-5";
        if (n <= 20) return "6-20";
        if (n <= 100) return "21-100";
        if (n <= 500) return "101-500";
        return "500+";
    }

    /** Runs a builder and swallows every failure, stack overflows in a pattern included: no payload, no report. */
    public static @Nullable JsonObject failClosed(Supplier<@Nullable JsonObject> build) {
        try {
            return build.get();
        } catch (Throwable t) { // NOSONAR: an Error here must not escape into the game either
            CloudLog.LOGGER.debug("[cccloud] Report dropped: {}", t.getClass().getName());
            return null;
        }
    }

    /** A crash or error report, or null when the failure is not this mod's or the result is not clean. */
    public static @Nullable JsonObject crash(Env env, Where where, ReportStack stack, ReportScrubber scrubber) {
        return failClosed(() -> {
            if (!stack.ours()) return null;
            var json = new JsonObject();
            json.addProperty("v", FORMAT);
            json.addProperty("fingerprint", stack.fingerprint());
            json.addProperty("where", where.id);
            json.add("env", env.json());
            var causes = new JsonArray();
            for (var cause : stack.causes()) {
                var c = new JsonObject();
                c.addProperty("class", scrubber.className(cause.cls()));
                if (cause.message() != null) {
                    String message = scrubber.message(cause.message());
                    if (!message.isEmpty()) c.addProperty("message", message);
                }
                var frames = new JsonArray();
                for (var frame : cause.frames()) frames.add(frame.own() ? frame.text() : scrubber.className(frame.text()));
                c.add("frames", frames);
                causes.add(c);
            }
            json.add("causes", causes);
            return verified(json, scrubber);
        });
    }

    /** Usage stats. {@code integrations}: mod ids from the mod's own list ({@code Integrations}), never the mod list. */
    public static @Nullable JsonObject stats(Env env, Counts counts, String internetAccess, Collection<String> integrations) {
        return failClosed(() -> {
            var json = new JsonObject();
            json.addProperty("v", FORMAT);
            json.add("env", env.json());
            json.addProperty("mainframes", bucket(counts.mainframes()));
            json.addProperty("uplinks", bucket(counts.uplinks()));
            json.addProperty("cards", bucket(counts.cards()));
            json.addProperty("internetAccess", INTERNET.contains(internetAccess) ? internetAccess : "off");
            var mods = new JsonArray();
            for (String id : new TreeSet<>(integrations)) {
                if (MOD_ID.matcher(id).matches()) mods.add(id);
            }
            json.add("integrations", mods);
            return json;
        });
    }

    /**
     * A feedback message.
     *
     * @param contact   what the writer chose to give for an answer; it is the one field that is not scrubbed
     * @param technical the versions block, only when the writer ticked the box
     */
    public static @Nullable JsonObject feedback(String category, String text, @Nullable String contact, @Nullable Env technical, ReportScrubber scrubber) {
        return failClosed(() -> {
            if (!CATEGORIES.contains(category)) return null;
            String clean = ReportScrubber.cut(scrubber.text(text), MAX_FEEDBACK_TEXT);
            if (clean.isEmpty()) return null;
            var json = new JsonObject();
            json.addProperty("v", FORMAT);
            json.addProperty("category", category);
            json.addProperty("text", clean);
            String reachable = contact == null ? "" : contact.strip();
            if (!reachable.isEmpty()) json.addProperty("contact", ReportScrubber.cut(reachable, MAX_CONTACT));
            if (technical != null) json.add("env", technical.json());
            return verified(json, scrubber);
        });
    }

    /** The last gate: the finished payload's free text must come out of the scrubber unchanged. Otherwise null. */
    private static @Nullable JsonObject verified(JsonObject json, ReportScrubber scrubber) {
        if (json.has("text") && !scrubber.clean(json.get("text").getAsString())) return null;
        if (!json.has("causes")) return json;
        for (JsonElement element : json.getAsJsonArray("causes")) {
            var cause = element.getAsJsonObject();
            if (!CODE_NAME.matcher(cause.get("class").getAsString()).matches()) return null;
            if (cause.has("message") && !scrubber.clean(cause.get("message").getAsString())) return null;
            for (JsonElement frame : cause.getAsJsonArray("frames")) {
                if (!FRAME.matcher(frame.getAsString()).matches()) return null;
            }
        }
        return json;
    }

    /** The payload's text as it is sent. */
    public static byte[] bytes(JsonObject payload) {
        return payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
