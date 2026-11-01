package ai.ezfox.cccloud.reports;

import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import ai.ezfox.cccloud.web.AccessApi;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The dashboard's side of reports (docs/PRIVACY.md): {@code GET .../reports} the state, {@code GET .../reports/preview}
 * the exact payloads, {@code POST .../reports} a decision, {@code POST .../feedback} one feedback message. The routes
 * need a role on the mainframe in the URL ({@code view}); decisions belong to the server owner alone. Strict bodies.
 * Pure Java, unit tested; runs on the server thread.
 */
public final class ReportApi {
    /** Who is asking. {@code owner}: op level 4 or the singleplayer host. {@code canWrite}: not a read-only browser. */
    public record Caller(boolean owner, boolean canWrite, String sender) {
    }

    public interface Hooks {
        State state(Channel channel);

        boolean promptDue();

        void decide(Channel channel, boolean yes);

        void dismissPrompt();

        /** {@code {"crash":{"example":true,"payload":{…}},"stats":{"payload":{…}}}}. */
        JsonObject preview();

        /** The payload exactly as it would go out, or null when the text is empty after scrubbing or was dropped. */
        @Nullable JsonObject feedbackPayload(String category, String text, @Nullable String contact, boolean technical);

        /** Sends a payload from {@link #feedbackPayload}; false when the limits refuse it. */
        boolean sendFeedback(String sender, JsonObject payload);
    }

    private static final Set<String> CATEGORIES = Set.of("bug", "idea", "other");

    private ReportApi() {
    }

    public static AccessApi.Response get(Caller caller, Hooks hooks) {
        return new AccessApi.Response(200, document(caller, hooks));
    }

    private static JsonObject document(Caller caller, Hooks hooks) {
        boolean decides = caller.owner() && caller.canWrite();
        var json = new JsonObject();
        json.addProperty("version", ReportConsent.VERSION);
        json.addProperty("canDecide", decides);
        json.addProperty("prompt", decides && hooks.promptDue());
        // what a server does with crash reports is the owner's business; everybody else only sees feedback
        if (decides) {
            var channels = new JsonObject();
            for (var channel : Channel.values()) channels.addProperty(channel.id, hooks.state(channel).id());
            json.add("channels", channels);
        }
        json.addProperty("feedback", caller.canWrite());
        return json;
    }

    public static AccessApi.Response preview(Caller caller, Hooks hooks) {
        if (!caller.owner() || !caller.canWrite()) return AccessApi.error(403, "not_owner");
        return new AccessApi.Response(200, hooks.preview());
    }

    /** {@code {"channel":"crash|stats","decision":"on|off"}} or {@code {"dismiss":true}}. */
    public static AccessApi.Response post(Caller caller, String body, Hooks hooks) {
        if (!caller.owner() || !caller.canWrite()) return AccessApi.error(403, "not_owner");
        var json = parse(body);
        if (json == null) return AccessApi.error(400, "invalid");
        if (json.has("dismiss")) {
            if (json.size() != 1 || !bool(json.get("dismiss"))) return AccessApi.error(400, "invalid");
            hooks.dismissPrompt();
            return get(caller, hooks);
        }
        if (json.size() != 2) return AccessApi.error(400, "invalid");
        var channel = Channel.byId(string(json.get("channel")));
        String decision = string(json.get("decision"));
        if (channel == null || !("on".equals(decision) || "off".equals(decision))) return AccessApi.error(400, "invalid");
        hooks.decide(channel, decision.equals("on"));
        return get(caller, hooks);
    }

    /**
     * {@code {"category","text","contact"?,"technical"?,"confirm"?}}. Without {@code confirm} it only answers
     * {@code {"payload":…}}, the exact message; the dashboard shows that and sends again with {@code confirm:true}.
     */
    public static AccessApi.Response feedback(Caller caller, String body, Hooks hooks) {
        if (!caller.canWrite()) return AccessApi.error(403, "not_allowed");
        var json = parse(body);
        if (json == null || !Set.of("category", "text", "contact", "technical", "confirm").containsAll(json.keySet())) return AccessApi.error(400, "invalid");
        String category = string(json.get("category"));
        String text = string(json.get("text"));
        String contact = json.has("contact") ? string(json.get("contact")) : "";
        if (category == null || !CATEGORIES.contains(category) || text == null || text.isBlank() || contact == null) return AccessApi.error(400, "invalid");
        if (text.length() > ReportPayload.MAX_FEEDBACK_TEXT || contact.length() > ReportPayload.MAX_CONTACT) return AccessApi.error(400, "too_long");
        if (json.has("technical") && !isBoolean(json.get("technical")) || json.has("confirm") && !isBoolean(json.get("confirm"))) return AccessApi.error(400, "invalid");
        var payload = hooks.feedbackPayload(category, text, contact, json.has("technical") && bool(json.get("technical")));
        if (payload == null) return AccessApi.error(400, "empty");
        var answer = new JsonObject();
        if (!json.has("confirm") || !bool(json.get("confirm"))) {
            answer.add("payload", payload);
            return new AccessApi.Response(200, answer);
        }
        if (!hooks.sendFeedback(caller.sender(), payload)) return AccessApi.error(429, "rate_limited");
        answer.addProperty("sent", true);
        return new AccessApi.Response(200, answer);
    }

    private static @Nullable JsonObject parse(String body) {
        try {
            var element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String string(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    private static boolean isBoolean(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    private static boolean bool(JsonElement element) {
        return isBoolean(element) && element.getAsBoolean();
    }
}
