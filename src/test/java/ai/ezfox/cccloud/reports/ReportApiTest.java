package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.reports.ReportApi.Caller;
import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportApiTest {
    private static final Caller OWNER = new Caller(true, true, "owner");
    private static final Caller PLAYER = new Caller(false, true, "player");
    private static final Caller READ_ONLY_OWNER = new Caller(true, false, "owner");

    private final List<String> calls = new ArrayList<>();
    private boolean promptDue = true;
    private boolean limited;

    private final ReportApi.Hooks hooks = new ReportApi.Hooks() {
        @Override
        public State state(Channel channel) {
            return State.ASK;
        }

        @Override
        public boolean promptDue() {
            return promptDue;
        }

        @Override
        public void decide(Channel channel, boolean yes) {
            calls.add("decide " + channel.id + " " + yes);
        }

        @Override
        public void dismissPrompt() {
            calls.add("dismiss");
        }

        @Override
        public JsonObject preview() {
            calls.add("preview");
            return new JsonObject();
        }

        @Override
        public @Nullable JsonObject feedbackPayload(String category, String text, @Nullable String contact, boolean technical) {
            calls.add("payload " + category + " " + technical);
            if (text.equals("nothing")) return null;
            var json = new JsonObject();
            json.addProperty("text", text);
            return json;
        }

        @Override
        public boolean sendFeedback(String sender, JsonObject payload) {
            calls.add("send " + sender);
            return !limited;
        }
    };

    @Test
    void theOwnerSeesThePrompt() {
        var body = ReportApi.get(OWNER, hooks).body();
        assertTrue(body.get("canDecide").getAsBoolean());
        assertTrue(body.get("prompt").getAsBoolean());
        assertEquals("ask", body.getAsJsonObject("channels").get("crash").getAsString());
    }

    @Test
    void othersSeeNeitherPromptNorState() {
        var body = ReportApi.get(PLAYER, hooks).body();
        assertFalse(body.get("canDecide").getAsBoolean());
        assertFalse(body.get("prompt").getAsBoolean());
        assertFalse(body.has("channels"));
        assertTrue(body.get("feedback").getAsBoolean());
    }

    @Test
    void aReadOnlyBrowserCannotDecide() {
        var body = ReportApi.get(READ_ONLY_OWNER, hooks).body();
        assertFalse(body.get("canDecide").getAsBoolean());
        assertFalse(body.get("feedback").getAsBoolean());
        assertEquals(403, ReportApi.post(READ_ONLY_OWNER, "{\"channel\":\"crash\",\"decision\":\"on\"}", hooks).status());
    }

    @Test
    void onlyTheOwnerDecides() {
        assertEquals(403, ReportApi.post(PLAYER, "{\"channel\":\"crash\",\"decision\":\"on\"}", hooks).status());
        assertEquals(403, ReportApi.post(PLAYER, "{\"dismiss\":true}", hooks).status());
        assertEquals(List.of(), calls);
    }

    @Test
    void onlyTheOwnerSeesThePreview() {
        assertEquals(403, ReportApi.preview(PLAYER, hooks).status());
        assertEquals(List.of(), calls);
        assertEquals(200, ReportApi.preview(OWNER, hooks).status());
    }

    @Test
    void decisions() {
        assertEquals(200, ReportApi.post(OWNER, "{\"channel\":\"crash\",\"decision\":\"on\"}", hooks).status());
        assertEquals(200, ReportApi.post(OWNER, "{\"channel\":\"stats\",\"decision\":\"off\"}", hooks).status());
        assertEquals(List.of("decide crash true", "decide stats false"), calls);
    }

    @Test
    void dismissing() {
        assertEquals(200, ReportApi.post(OWNER, "{\"dismiss\":true}", hooks).status());
        assertEquals(List.of("dismiss"), calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "[]", "null", "{}", "{\"dismiss\":false}", "{\"dismiss\":true,\"x\":1}", "{\"channel\":\"crash\"}",
        "{\"channel\":\"crash\",\"decision\":\"maybe\"}", "{\"channel\":\"feedback\",\"decision\":\"on\"}", "{\"channel\":1,\"decision\":\"on\"}",
        "{\"channel\":\"crash\",\"decision\":\"on\",\"x\":1}"})
    void strictDecisionBodies(String body) {
        assertEquals(400, ReportApi.post(OWNER, body, hooks).status());
        assertEquals(List.of(), calls);
    }

    @Test
    void feedbackFirstShowsThePayload() {
        var response = ReportApi.feedback(PLAYER, "{\"category\":\"bug\",\"text\":\"hello\"}", hooks);
        assertEquals(200, response.status());
        assertEquals("hello", response.body().getAsJsonObject("payload").get("text").getAsString());
        assertEquals(List.of("payload bug false"), calls);
    }

    @Test
    void feedbackSendsOnlyWhenConfirmed() {
        var response = ReportApi.feedback(PLAYER, "{\"category\":\"idea\",\"text\":\"hello\",\"technical\":true,\"confirm\":true}", hooks);
        assertEquals(200, response.status());
        assertTrue(response.body().get("sent").getAsBoolean());
        assertEquals(List.of("payload idea true", "send player"), calls);
    }

    @Test
    void confirmFalseDoesNotSend() {
        ReportApi.feedback(PLAYER, "{\"category\":\"idea\",\"text\":\"hello\",\"confirm\":false}", hooks);
        assertFalse(calls.stream().anyMatch(c -> c.startsWith("send")));
    }

    @Test
    void feedbackLimit() {
        limited = true;
        var response = ReportApi.feedback(PLAYER, "{\"category\":\"bug\",\"text\":\"hello\",\"confirm\":true}", hooks);
        assertEquals(429, response.status());
        assertNotNull(response.body());
    }

    @Test
    void aReadOnlyBrowserSendsNoFeedback() {
        assertEquals(403, ReportApi.feedback(READ_ONLY_OWNER, "{\"category\":\"bug\",\"text\":\"hello\"}", hooks).status());
    }

    @Test
    void emptyAfterScrubbing() {
        assertEquals(400, ReportApi.feedback(PLAYER, "{\"category\":\"bug\",\"text\":\"nothing\"}", hooks).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "{}", "{\"text\":\"x\"}", "{\"category\":\"bug\"}", "{\"category\":\"rant\",\"text\":\"x\"}", "{\"category\":\"bug\",\"text\":\"  \"}",
        "{\"category\":\"bug\",\"text\":1}", "{\"category\":\"bug\",\"text\":\"x\",\"name\":\"Steve\"}", "{\"category\":\"bug\",\"text\":\"x\",\"technical\":\"yes\"}",
        "{\"category\":\"bug\",\"text\":\"x\",\"confirm\":1}", "{\"category\":\"bug\",\"text\":\"x\",\"contact\":1}"})
    void strictFeedbackBodies(String body) {
        assertEquals(400, ReportApi.feedback(PLAYER, body, hooks).status());
        assertFalse(calls.stream().anyMatch(c -> c.startsWith("send")));
    }

    @Test
    void lengthLimits() {
        var json = new JsonObject();
        json.addProperty("category", "bug");
        json.addProperty("text", "x".repeat(ReportPayload.MAX_FEEDBACK_TEXT + 1));
        var response = ReportApi.feedback(PLAYER, json.toString(), hooks);
        assertEquals(400, response.status());
        assertEquals("too_long", response.body().get("error").getAsString());
        json.addProperty("text", "x");
        json.addProperty("contact", "x".repeat(ReportPayload.MAX_CONTACT + 1));
        assertEquals("too_long", ReportApi.feedback(PLAYER, json.toString(), hooks).body().get("error").getAsString());
    }
}
