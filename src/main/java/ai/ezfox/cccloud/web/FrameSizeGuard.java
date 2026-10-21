package ai.ezfox.cccloud.web;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;

/**
 * Sits in front of Netty's WebSocket frame decoder and reads the raw frame headers (RFC 6455 §5.2) without consuming
 * anything. If a frame – or a fragmented message – announces more payload than the current limit, the connection is
 * closed (1009) before the payload is buffered.
 * <p>
 * The limit starts at {@link #PRE_AUTH_LIMIT} (the auth message is tiny) and is raised to
 * {@link ClientMessages#MAX_LAYOUT_FRAME} once the session is authenticated; {@link ClientMessages} then still limits
 * every message except {@code layout_set} to 8 KB.
 */
final class FrameSizeGuard extends ChannelInboundHandlerAdapter {
    static final int PRE_AUTH_LIMIT = 2 * 1024;

    private volatile int limit = PRE_AUTH_LIMIT;
    private final byte[] header = new byte[14];
    private int headerRead;
    private long payloadLeft;
    private long messageSize;
    private boolean closed;

    void raiseLimit(int limit) {
        this.limit = limit;
    }

    int limit() {
        return limit;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (closed) {
            io.netty.util.ReferenceCountUtil.release(msg);
            return;
        }
        if (msg instanceof ByteBuf buf && !inspect(buf)) {
            closed = true;
            buf.release();
            ctx.channel().writeAndFlush(new CloseWebSocketFrame(1009, "message too big")).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        super.channelRead(ctx, msg);
    }

    /** Walks all frame headers in the buffer. Returns false if a frame exceeds the limit. */
    private boolean inspect(ByteBuf buf) {
        int i = buf.readerIndex();
        int end = buf.writerIndex();
        while (i < end) {
            if (payloadLeft > 0) {
                long skip = Math.min(payloadLeft, end - i);
                payloadLeft -= skip;
                i += (int) skip;
                continue;
            }
            header[headerRead++] = buf.getByte(i++);
            int needed = headerLength();
            if (needed < 0 || headerRead < needed) continue;
            // Header complete.
            int opcode = header[0] & 0x0F;
            long length = payloadLength();
            headerRead = 0;
            if (length < 0 || length > limit) return false;
            if (opcode < 0x8) {
                // Data frame: 0 = continuation, 1 = text, 2 = binary. Count fragmented messages as a whole.
                messageSize = opcode == 0 ? messageSize + length : length;
                if (messageSize > limit) return false;
            }
            payloadLeft = length;
        }
        return true;
    }

    /** Total header length once enough bytes are known, else -1. */
    private int headerLength() {
        if (headerRead < 2) return -1;
        int len7 = header[1] & 0x7F;
        int mask = (header[1] & 0x80) != 0 ? 4 : 0;
        return 2 + (len7 == 126 ? 2 : len7 == 127 ? 8 : 0) + mask;
    }

    private long payloadLength() {
        int len7 = header[1] & 0x7F;
        if (len7 < 126) return len7;
        if (len7 == 126) return ((header[2] & 0xFFL) << 8) | (header[3] & 0xFFL);
        long value = 0;
        for (int k = 2; k < 10; k++) value = (value << 8) | (header[k] & 0xFFL);
        return value; // negative if the top bit is set → rejected
    }
}
