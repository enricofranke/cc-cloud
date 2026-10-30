package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Security review 2026-10-30: before authentication a WebSocket message is at most 2 KB. With permessage-deflate the
 * raw-frame guard ({@link FrameSizeGuard}) only sees the compressed size, so a small compressed frame could carry an
 * inflated message of up to the 257 KB frame limit, which was then parsed before authentication. The session now
 * refuses inflated messages over the pre-auth limit as well.
 */
class PreAuthSizeTest {
    private final UUID mainframe = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    private String token(AuthStore store) {
        var code = store.currentCode(mainframe, owner);
        var pending = store.pair(AuthStore.formatCode(code.code()), "Phone", "127.0.0.1");
        return store.approve(pending.id(), owner, "Owner", true).token();
    }

    private static EmbeddedChannel session(WebInstance instance) {
        var handshaker = new WebSocketServerHandshaker13("ws://localhost/ws/client", null, WebSocketDecoderConfig.newBuilder().build());
        return new EmbeddedChannel(new WebSocketSession(instance, handshaker, "127.0.0.1", null));
    }

    private static @Nullable Integer closeCode(EmbeddedChannel channel) {
        Object out;
        while ((out = channel.readOutbound()) != null) {
            if (out instanceof CloseWebSocketFrame close) return close.statusCode();
        }
        return null;
    }

    private static int deflatedSize(String text) {
        var deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(text.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        var out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.size();
    }

    @Test
    void anInflatedMessageOverTwoKilobytesIsRefusedBeforeAuth() {
        var store = new AuthStore(System::currentTimeMillis, () -> {
        });
        var instance = new WebInstance(new TestBridge(), store, null, List.of(), true, "http://localhost:8787", 8787);
        String token = token(store);
        // A valid auth message padded with 200 KB of whitespace: well under 2 KB on the wire when compressed.
        String padded = "{\"t\":\"auth\",\"token\":\"" + token + "\"" + " ".repeat(200_000) + "}";
        assertTrue(deflatedSize(padded) < FrameSizeGuard.PRE_AUTH_LIMIT, "passes the raw-frame guard compressed: " + deflatedSize(padded));

        var big = session(instance);
        big.writeInbound(new TextWebSocketFrame(padded));
        big.runPendingTasks();
        assertEquals(1009, closeCode(big), "refused, not parsed");
        // 6 KB is under the 8 KB message limit: before the fix this logged in although the pre-auth limit is 2 KB.
        var medium = session(instance);
        medium.writeInbound(new TextWebSocketFrame("{\"t\":\"auth\",\"token\":\"" + token + "\"" + " ".repeat(6_000) + "}"));
        medium.runPendingTasks();
        assertEquals(1009, closeCode(medium));

        // The same auth message without padding still works (no close).
        var small = session(instance);
        small.writeInbound(new TextWebSocketFrame("{\"t\":\"auth\",\"token\":\"" + token + "\"}"));
        small.runPendingTasks();
        assertNull(closeCode(small));
        WebHub.INSTANCE.removeMainframe(mainframe);
    }
}
