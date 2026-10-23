package ai.ezfox.cccloud.push;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Push target API: add/update/delete/test, secrets never returned, strict bodies, limits; message formats. */
class PushApiTest {
    final UUID mf = UUID.randomUUID();
    final List<String> tests = new ArrayList<>();
    final List<String> audit = new ArrayList<>();
    final PushStore store = new PushStore(() -> { });
    final PushApi api = new PushApi(store, new PushApi.Hooks() {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Set<String> allowedHosts() {
            return Set.of();
        }

        @Override
        public @Nullable String test(UUID mainframeId, PushTarget target) {
            tests.add(target.name());
            return null;
        }

        @Override
        public @Nullable JsonObject status(String targetId) {
            return null;
        }

        @Override
        public void audit(UUID mainframeId, String message) {
            audit.add(message);
        }
    });

    static final String DISCORD = "https://discord.com/api/webhooks/1234567890/SECRETsecretSECRETsecret";

    JsonObject post(int status, String body) {
        var r = api.post(mf, "Alex (Phone)", body);
        assertEquals(status, r.status(), r.body().toString());
        return r.body();
    }

    @Test
    void lifecycleWithoutLeakingSecrets() {
        var doc = post(200, "{\"op\":\"add\",\"target\":{\"type\":\"discord\",\"name\":\"Base\",\"url\":\"" + DISCORD + "\",\"events\":[\"alerts\",\"power\"]}}");
        assertFalse(doc.toString().contains("SECRETsecret"), "the webhook secret never goes back");
        var row = doc.getAsJsonArray("targets").get(0).getAsJsonObject();
        assertEquals("https://discord.com/…cret", row.get("url").getAsString());
        assertEquals("warn", row.get("minLevel").getAsString());
        String id = row.get("id").getAsString();
        // update without url keeps the secret
        post(200, "{\"op\":\"update\",\"id\":\"" + id + "\",\"target\":{\"name\":\"Main base\",\"minLevel\":\"error\"}}");
        var t = store.targets(mf).getFirst();
        assertEquals(DISCORD, t.url());
        assertEquals("Main base", t.name());
        assertTrue(t.wants(PushTarget.Event.ALERTS, "crit") && !t.wants(PushTarget.Event.ALERTS, "warn") && !t.wants(PushTarget.Event.RULES, "info"));
        post(200, "{\"op\":\"test\",\"id\":\"" + id + "\"}");
        assertEquals(List.of("Main base"), tests);
        post(404, "{\"op\":\"test\",\"id\":\"ffffffff\"}");
        post(200, "{\"op\":\"delete\",\"id\":\"" + id + "\"}");
        assertTrue(store.targets(mf).isEmpty());
        assertEquals(3, audit.size());
    }

    @Test
    void refusesBadInputAndEnforcesTheLimit() {
        post(400, "{\"op\":\"add\",\"target\":{\"type\":\"webhook\",\"url\":\"http://example.org/x\"}}");
        post(400, "{\"op\":\"add\",\"target\":{\"type\":\"webhook\",\"url\":\"https://10.0.0.1/x\"}}");
        post(400, "{\"op\":\"add\",\"target\":{\"type\":\"webhook\",\"url\":\"https://example.org/x\",\"token\":\"t\"}}");
        post(400, "{\"op\":\"add\",\"target\":{\"type\":\"sms\",\"url\":\"https://example.org/x\"}}");
        post(400, "{\"op\":\"add\",\"target\":{\"type\":\"ntfy\",\"url\":\"https://ntfy.sh/a\",\"events\":[\"everything\"]}}");
        post(400, "{\"op\":\"add\",\"target\":{\"type\":\"ntfy\",\"url\":\"https://ntfy.sh/a\"},\"extra\":1}");
        post(400, "{\"op\":\"nuke\"}");
        post(400, "[]");
        for (int i = 0; i < PushStore.MAX_TARGETS; i++) {
            post(200, "{\"op\":\"add\",\"target\":{\"type\":\"ntfy\",\"name\":\"n" + i + "\",\"url\":\"https://ntfy.sh/topic" + i + "\",\"token\":\"tk_abc\"}}");
        }
        var doc = post(400, "{\"op\":\"add\",\"target\":{\"type\":\"ntfy\",\"url\":\"https://ntfy.sh/x\"}}");
        assertEquals("too_many", doc.get("error").getAsString());
        assertFalse(api.document(mf).toString().contains("tk_abc"), "tokens are never returned");
        assertTrue(api.document(mf).getAsJsonArray("targets").get(0).getAsJsonObject().get("hasToken").getAsBoolean());
    }

    @Test
    void storeRoundTripDropsTargetsThatAreNotAllowedAnyMore() {
        post(200, "{\"op\":\"add\",\"target\":{\"type\":\"webhook\",\"url\":\"https://hooks.example.org/x\"}}");
        var saved = store.save();
        var again = new PushStore(() -> { });
        assertEquals(0, again.load(saved, Set.of()));
        assertEquals(store.targets(mf), again.targets(mf));
        assertEquals(1, new PushStore(() -> { }).load(saved, Set.of("ntfy.sh")), "host no longer on the allowlist");
    }

    @Test
    void formats() {
        var msg = new PushMessage(mf, "Base", PushTarget.Event.ALERTS, "error", "Reactor", "@everyone *meltdown*", 42);
        var discord = new PushTarget("0a1b2c3d", PushTarget.Type.DISCORD, "d", DISCORD, "", "info", Set.of(PushTarget.Event.ALERTS), true);
        var body = JsonParser.parseString(new String(PushFormat.request(discord, msg).body(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(0, body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size(), "never pings");
        assertTrue(body.get("content").getAsString().contains("\\@everyone \\*meltdown\\*"), body.toString());
        var ntfy = new PushTarget("0a1b2c3d", PushTarget.Type.NTFY, "n", "https://ntfy.sh/x", "", "info", Set.of(PushTarget.Event.ALERTS), true);
        var evil = new PushMessage(mf, "Bäse\r\nX-Injected: 1", PushTarget.Event.ALERTS, "info", "T", "x", 0);
        String title = PushFormat.request(ntfy, evil).headers().get("Title");
        assertFalse(title.contains("\r") || title.contains("\n"), "no header injection");
        assertTrue(title.startsWith("=?UTF-8?B?"), "non-ASCII titles are RFC 2047 encoded");
        assertNull(PushFormat.request(ntfy, evil).headers().get("Authorization"));
        var hook = new PushTarget("0a1b2c3d", PushTarget.Type.WEBHOOK, "w", "https://example.org/x", "", "info", Set.of(PushTarget.Event.ALERTS), true);
        var json = JsonParser.parseString(new String(PushFormat.request(hook, msg).body(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("alerts", json.get("event").getAsString());
        assertEquals(mf.toString(), json.get("mainframeId").getAsString());
        assertEquals("https://discord.com/…cret", PushTarget.mask(DISCORD));
        assertEquals("https://ntfy.sh/…", PushTarget.mask("https://ntfy.sh/abc"));
    }
}
