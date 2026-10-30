package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The voxel batch route: strict body, the section budget (counts sections, not requests), gzip negotiation. */
class VoxelBatchRouteTest {
    final UUID mainframe = UUID.randomUUID();
    final UUID owner = UUID.randomUUID();
    final TestBridge bridge = new TestBridge();
    final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    final WebInstance instance = new WebInstance(bridge, auth, null, List.of(), true, "http://localhost:8787", 8787);
    final String path = "/api/mainframes/" + mainframe + "/voxels/minecraft:overworld";

    @AfterEach
    void cleanup() {
        instance.pinExecutor.shutdownNow();
        instance.storeExecutor.shutdownNow();
    }

    String token() {
        var pending = auth.pair(auth.currentCode(mainframe, owner).code(), "Laptop", "127.0.0.1");
        return auth.approve(pending.id(), owner, "Owner", true).token();
    }

    FullHttpResponse post(String token, String body, boolean gzip) throws InterruptedException {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path, Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
        if (gzip) req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip, deflate, br");
        channel.writeInbound(req);
        for (int i = 0; i < 200; i++) {
            channel.runPendingTasks();
            FullHttpResponse response = channel.readOutbound();
            if (response != null) return response;
            Thread.sleep(10);
        }
        throw new AssertionError("no response");
    }

    static String sections(int n, int x0) {
        var sb = new StringBuilder("{\"sections\":[");
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "" : ",").append("[").append(x0 + i).append(",-4,0]");
        return sb.append("]}").toString();
    }

    @Test
    void strictBody() {
        assertNotNull(HttpHandler.parseVoxelBatch("{\"sections\":[[1,-4,2],[1,-3,2,\"0123456789abcdef\"]]}"));
        var parsed = HttpHandler.parseVoxelBatch("{\"sections\":[[1,-4,2],[1,-3,2,\"0123456789abcdef\"]]}");
        assertNull(parsed.have().get(0));
        assertEquals("0123456789abcdef", parsed.have().get(1));
        assertArrayEquals(new int[]{1, -3, 2}, parsed.sections().get(1));
        for (String bad : List.of("", "[]", "{}", "{\"sections\":[]}", "{\"sections\":[[1,2]]}", "{\"sections\":[[1,2,3,4]]}",
            "{\"sections\":[[1.5,2,3]]}", "{\"sections\":[[1,2,3,\"XYZ\"]]}", "{\"sections\":[[1,2,3]],\"x\":1}", "{\"sections\":[[1,999,3]]}",
            "{\"sections\":[[1,2,3],[1,2,3]]}", "{\"sections\":[[\"1\",2,3]]}", sections(65, 0))) {
            assertNull(HttpHandler.parseVoxelBatch(bad), bad);
        }
        assertNotNull(HttpHandler.parseVoxelBatch(sections(64, 0)), "64 sections fit");
        String full = sections(64, -1_999_000).replace(",-4,0]", ",-4,0,\"0123456789abcdef\"]");
        assertNotNull(HttpHandler.parseVoxelBatch(full));
        assertTrue(full.length() < ClientMessages.MAX_BODY, "a full batch with hashes stays below the 4 KB body limit: " + full.length());
    }

    @Test
    void budgetCountsSections() throws Exception {
        String token = token();
        var bad = post(token, "{\"sections\":[]}", true);
        assertEquals(400, bad.status().code());
        // a whole base in batches of 64 within the budget: never 429
        int batches = HttpHandler.VOXEL_BUDGET / 64;
        for (int i = 0; i < batches; i++) {
            var ok = post(token, sections(64, i * 64), i % 2 == 0);
            assertEquals(200, ok.status().code(), "batch " + i);
            assertEquals(i % 2 == 0 ? "gzip" : null, ok.headers().get(HttpHeaderNames.CONTENT_ENCODING), "gzip only when accepted");
            assertEquals("application/octet-stream", ok.headers().get(HttpHeaderNames.CONTENT_TYPE));
        }
        assertEquals(batches, bridge.dispatched.stream().filter(d -> d.equals("voxels:64")).count());
        // the budget is spent: the next batch waits (Retry-After in seconds), a smaller one fits sooner
        var limited = post(token, sections(64, 99_000), true);
        assertEquals(429, limited.status().code());
        var body = JsonParser.parseString(limited.content().toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("rate_limited", body.get("error").getAsString());
        assertTrue(body.get("retryAfter").getAsInt() >= 1, body.toString());
    }

    @Test
    void tokenBucketTakesManyAtOnce() {
        long[] now = {0};
        var bucket = new TokenBucket(10, 5, () -> now[0]);
        assertTrue(bucket.tryAcquire(10));
        assertFalse(bucket.tryAcquire(1));
        assertEquals(2.0, bucket.secondsUntil(10), 1e-9);
        now[0] = 1_000_000_000L;
        assertFalse(bucket.tryAcquire(6), "all or nothing");
        assertTrue(bucket.tryAcquire(5));
        assertEquals(2.0, bucket.secondsUntil(50), 1e-9, "never more than a full bucket");
    }
}
