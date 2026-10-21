package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WebSocket frame size limits: 2 KB before authentication, 64 KB (+1 KB) only afterwards. */
class FrameSizeGuardTest {
    /** A masked client→server text frame (RFC 6455) with the given payload size. */
    static ByteBuf frame(int opcode, boolean fin, int size) {
        var buf = Unpooled.buffer();
        buf.writeByte((fin ? 0x80 : 0) | opcode);
        if (size < 126) {
            buf.writeByte(0x80 | size);
        } else if (size <= 0xFFFF) {
            buf.writeByte(0x80 | 126);
            buf.writeShort(size);
        } else {
            buf.writeByte(0x80 | 127);
            buf.writeLong(size);
        }
        byte[] mask = {1, 2, 3, 4};
        buf.writeBytes(mask);
        for (int i = 0; i < size; i++) buf.writeByte('a' ^ mask[i % 4]);
        return buf;
    }

    @Test
    void nineKilobyteFrameBeforeAuthClosesTheConnection() {
        // Full path: HTTP upgrade through the real HttpHandler, then a raw 9 KB WebSocket frame.
        var instance = RouteAuthTest.instance(true);
        // Same handlers and names as WebInstance#initPipeline (minus the connection limiter and TLS).
        var channel = new EmbeddedChannel();
        channel.pipeline()
            .addLast("idle", new io.netty.handler.timeout.IdleStateHandler(10, 0, 0))
            .addLast("http", new HttpServerCodec(4096, 8192, 8192))
            .addLast("aggregator", new HttpObjectAggregator(ClientMessages.MAX_BODY))
            .addLast("handler", new HttpHandler(instance));
        String upgrade = "GET /ws/client HTTP/1.1\r\nHost: localhost:8787\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n";
        channel.writeInbound(Unpooled.copiedBuffer(upgrade, StandardCharsets.US_ASCII));
        channel.runPendingTasks();
        ByteBuf response = channel.readOutbound();
        assertNotNull(response, "handshake response");
        assertTrue(response.toString(StandardCharsets.US_ASCII).startsWith("HTTP/1.1 101"), response.toString(StandardCharsets.US_ASCII));
        assertNotNull(channel.pipeline().get("ws-size-guard"), "guard installed in front of the frame decoder: " + channel.pipeline().names() + " open=" + channel.isOpen());
        assertEquals(List.of("ws-size-guard", "wsdecoder"), channel.pipeline().names().stream()
            .filter(n -> n.equals("ws-size-guard") || n.equals("wsdecoder")).toList(), "guard before decoder");

        channel.writeInbound(frame(1, true, 9 * 1024));
        channel.runPendingTasks();
        assertFalse(channel.isOpen(), "a 9 KB frame before authentication must close the connection");
    }

    @Test
    void smallFramesPassBeforeAuth() {
        var guard = new FrameSizeGuard();
        var channel = new EmbeddedChannel(guard);
        channel.writeInbound(frame(1, true, 100));
        assertTrue(channel.isOpen());
        ByteBuf passed = channel.readInbound();
        assertEquals(2 + 4 + 100, passed.readableBytes(), "bytes are passed through untouched");
        passed.release();
    }

    @Test
    void limitIsRaisedOnlyAfterAuth() {
        var guard = new FrameSizeGuard();
        var channel = new EmbeddedChannel(guard);
        assertEquals(FrameSizeGuard.PRE_AUTH_LIMIT, guard.limit());
        guard.raiseLimit(ClientMessages.MAX_LAYOUT_FRAME);
        channel.writeInbound(frame(1, true, 60 * 1024));
        assertTrue(channel.isOpen(), "a layout-sized frame is fine after auth");
        ((ByteBuf) channel.readInbound()).release();
        channel.writeInbound(frame(1, true, ClientMessages.MAX_LAYOUT_FRAME + 1));
        assertFalse(channel.isOpen(), "but never more than 64 KB + 1 KB");
        assertInstanceOf(CloseWebSocketFrame.class, channel.readOutbound());
    }

    @Test
    void headersSplitAcrossBuffersAndFragmentedMessagesAreCounted() {
        var channel = new EmbeddedChannel(new FrameSizeGuard());
        // Two frames in one buffer, the second header split over two writes.
        var whole = Unpooled.wrappedBuffer(frame(1, false, 1000), frame(0, false, 500));
        channel.writeInbound(whole);
        var next = frame(0, true, 1000); // message total 2500 > 2 KB
        channel.writeInbound(next.readRetainedSlice(1));
        assertTrue(channel.isOpen(), "header incomplete, nothing decided yet");
        channel.writeInbound(next);
        assertFalse(channel.isOpen(), "fragments add up per message");
    }

    @Test
    void controlFramesDoNotResetTheMessageSize() {
        var channel = new EmbeddedChannel(new FrameSizeGuard());
        channel.writeInbound(frame(1, false, 1500));
        channel.writeInbound(frame(0x9, true, 10)); // ping in between
        channel.writeInbound(frame(0, true, 1000));
        assertFalse(channel.isOpen());
    }

    @Test
    void sixtyFourBitLengthsWithTheTopBitSetAreRejected() {
        var channel = new EmbeddedChannel(new FrameSizeGuard());
        var buf = Unpooled.buffer();
        buf.writeByte(0x81).writeByte(0x80 | 127).writeLong(Long.MIN_VALUE).writeInt(0);
        channel.writeInbound(buf);
        assertFalse(channel.isOpen());
    }
}
