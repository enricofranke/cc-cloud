package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.publicapi.ApiKeys;
import ai.ezfox.cccloud.publicapi.ApiScope;
import ai.ezfox.cccloud.publicapi.ApiSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * v0.10 public API event stream: Server-Sent Events with the hub's section messages for what the key's owner may see
 * (restricted cards only with view.positions), the scope check, the per-key stream limit, and that a stream never
 * takes a browser slot.
 */
class ApiEventStreamTest {
    final UUID mainframe = UUID.randomUUID();
    final String uplink = UUID.randomUUID().toString();
    final String restricted = UUID.randomUUID().toString();
    final TestBridge bridge = new TestBridge();
    final WebInstance instance = new WebInstance(bridge, new AuthStore(System::currentTimeMillis, () -> {
    }), new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), true, "http://localhost:8787", 8787);
    final ApiKeys keys = new ApiKeys(System::currentTimeMillis, () -> {
    });

    ApiEventStreamTest() {
        instance.apiKeys = keys;
        instance.api = new ApiSettings(true, 5, 600, 600, 2048, List.of(), true, 1);
    }

    @AfterEach
    void cleanup() {
        WebHub.INSTANCE.removeMainframe(mainframe);
        instance.pinExecutor.shutdownNow();
        instance.storeExecutor.shutdownNow();
    }

    private void publish() {
        WebHub.INSTANCE.publishMainframe(mainframe, "Base", true, true);
        for (var id : new String[]{uplink, restricted}) {
            var section = new JsonObject();
            section.addProperty("uplinkId", id);
            section.addProperty("title", id.equals(uplink) ? "Reactor" : "Server");
            section.addProperty("online", true);
            if (id.equals(restricted)) section.addProperty("restricted", true);
            var widgets = new JsonArray();
            var w = new JsonObject();
            w.addProperty("id", "temp");
            w.addProperty("type", "gauge");
            w.addProperty("value", 40);
            widgets.add(w);
            section.add("widgets", widgets);
            WebHub.INSTANCE.publishUplink(mainframe, section);
        }
        WebHub.INSTANCE.flush();
    }

    private EmbeddedChannel open(String secret) {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/mainframes/" + mainframe + "/events", Unpooled.EMPTY_BUFFER);
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + secret);
        channel.writeInbound(req);
        channel.runPendingTasks();
        return channel;
    }

    private static String events(EmbeddedChannel channel) {
        var text = new StringBuilder();
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (out instanceof HttpContent content) text.append(content.content().toString(StandardCharsets.UTF_8));
        }
        return text.toString();
    }

    @Test
    void streamsTheSectionsTheOwnerMaySee() {
        publish();
        String secret = keys.create(UUID.randomUUID(), "Steve", mainframe, "ha", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of()).secret();
        var channel = open(secret);
        HttpResponse head = channel.readOutbound();
        assertEquals(200, head.status().code());
        assertTrue(head.headers().get(HttpHeaderNames.CONTENT_TYPE).startsWith("text/event-stream"));
        assertEquals("nosniff", head.headers().get("X-Content-Type-Options"));
        String first = events(channel);
        assertTrue(first.startsWith("event: snapshot\ndata: "), first);
        assertTrue(first.contains("Reactor") && !first.contains("Server"), "restricted cards only with view.positions: " + first);
        assertTrue(WebHub.INSTANCE.sessions().stream().noneMatch(HubSession::countsAsBrowser), "a stream never takes a browser slot");
        WebHub.INSTANCE.publishValue(mainframe, uplink, "temp", new JsonPrimitive(55));
        WebHub.INSTANCE.publishValue(mainframe, restricted, "temp", new JsonPrimitive(99));
        WebHub.INSTANCE.flush();
        channel.runPendingTasks();
        String next = events(channel);
        assertTrue(next.contains("event: value\ndata: {\"sectionId\":\"" + uplink + "\",\"id\":\"temp\",\"value\":55}"), next);
        assertFalse(next.contains("99"), "no values of restricted cards: " + next);
        assertEquals(List.of("stream:ha"), bridge.dispatched, "the owner's rights were checked on the server thread");
        // A second stream of the same key is over the limit (1 per key in this test).
        var second = open(secret);
        FullHttpResponse refused = second.readOutbound();
        assertEquals(429, refused.status().code());
        // Closing the stream frees the slot.
        channel.close();
        channel.runPendingTasks();
        var third = open(secret);
        HttpResponse ok = third.readOutbound();
        assertEquals(200, ok.status().code(), "the slot is free again");
        third.close();
    }

    @Test
    void positionsShowRestrictedCardsAndScopesAreChecked() {
        publish();
        bridge.streamPerms = Set.of(Perm.VIEW, Perm.VIEW_POSITIONS);
        String secret = keys.create(UUID.randomUUID(), "Steve", mainframe, "ha", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of()).secret();
        var channel = open(secret);
        channel.readOutbound();
        assertTrue(events(channel).contains("Server"), "with view.positions the restricted card is in the snapshot");
        channel.close();
        String noScope = keys.create(UUID.randomUUID(), "Steve", mainframe, "stats", EnumSet.of(ApiScope.READ_STATS), 0, List.of()).secret();
        FullHttpResponse missing = open(noScope).readOutbound();
        assertEquals(403, missing.status().code(), "read:sections is needed");
        bridge.streamPerms = null; // the owner lost the rights
        FullHttpResponse denied = open(secret).readOutbound();
        assertEquals(403, denied.status().code());
        String other = keys.create(UUID.randomUUID(), "Steve", UUID.randomUUID(), "x", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of()).secret();
        FullHttpResponse foreign = open(other).readOutbound();
        assertEquals(404, foreign.status().code(), "another mainframe does not exist for a key");
    }
}
