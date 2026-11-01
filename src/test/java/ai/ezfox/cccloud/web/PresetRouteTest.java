package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.script.PresetStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Store presets over HTTP: only with {@code layout.edit}, and the store is never touched before that is checked. */
class PresetRouteTest {
    final UUID mainframe = UUID.randomUUID();
    final UUID owner = UUID.randomUUID();
    final List<String> asked = new ArrayList<>();
    final TestBridge bridge = new TestBridge() {
        @Override
        public PresetStore.Result storePresets(@Nullable String id) {
            asked.add(String.valueOf(id));
            var body = new JsonObject();
            body.addProperty("from", "store");
            return new PresetStore.Result(200, body);
        }
    };
    final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    final WebInstance instance = new WebInstance(bridge, auth, null, List.of(), true, "http://localhost:8787", 8787);
    final String base = "/api/mainframes/" + mainframe + "/presets";

    @AfterEach
    void cleanup() {
        instance.pinExecutor.shutdownNow();
        instance.storeExecutor.shutdownNow();
    }

    String token() {
        var pending = auth.pair(auth.currentCode(mainframe, owner).code(), "Laptop", "127.0.0.1");
        return auth.approve(pending.id(), owner, "Owner", true).token();
    }

    FullHttpResponse get(String path, String token) throws InterruptedException {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path, Unpooled.EMPTY_BUFFER);
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
        channel.writeInbound(req);
        for (int i = 0; i < 200; i++) {
            channel.runPendingTasks();
            FullHttpResponse response = channel.readOutbound();
            if (response != null) return response;
            Thread.sleep(10);
        }
        throw new AssertionError("no response for " + path);
    }

    static JsonObject body(FullHttpResponse response) {
        return JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void anEditorGetsTheList() throws Exception {
        bridge.roles = (d, m) -> Role.ADMIN;
        var response = get(base, token());
        assertEquals(200, response.status().code());
        assertEquals("store", body(response).get("from").getAsString());
        assertEquals(List.of("null"), asked);
    }

    @Test
    void anEditorGetsOnePreset() throws Exception {
        bridge.roles = (d, m) -> Role.ADMIN;
        assertEquals(200, get(base + "/door-panel", token()).status().code());
        assertEquals(List.of("door-panel"), asked);
    }

    @Test
    void withoutLayoutEditTheStoreIsNotEvenAsked() throws Exception {
        bridge.roles = (d, m) -> Role.VIEWER;
        bridge.perms = (d, m) -> EnumSet.of(Perm.VIEW);
        String token = token();
        assertEquals(403, get(base, token).status().code());
        assertEquals(403, get(base + "/door-panel", token).status().code());
        assertTrue(asked.isEmpty());
    }

    @Test
    void everyOtherPermissionDoesNotHelp() throws Exception {
        bridge.roles = (d, m) -> Role.ADMIN;
        var all = EnumSet.copyOf(Perm.all());
        all.remove(Perm.LAYOUT_EDIT);
        bridge.perms = (d, m) -> Set.copyOf(all);
        assertEquals(403, get(base, token()).status().code());
        assertTrue(asked.isEmpty());
    }

    @Test
    void anotherMainframeIsNoShortcut() throws Exception {
        bridge.roles = (d, m) -> m.equals(mainframe) ? Role.ADMIN : Role.NONE;
        assertEquals(403, get("/api/mainframes/" + UUID.randomUUID() + "/presets", token()).status().code());
        assertTrue(asked.isEmpty());
    }

    @Test
    void noTokenNoAnswer() throws Exception {
        assertEquals(401, get(base, "not-a-token").status().code());
        assertTrue(asked.isEmpty());
    }

    @Test
    void oddIdsDoNotReachTheStore() throws Exception {
        bridge.roles = (d, m) -> Role.ADMIN;
        assertEquals(404, get(base + "/..%2F..%2Fsecret", token()).status().code());
        assertTrue(asked.isEmpty());
    }
}
