package ai.ezfox.cccloud.web;

import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * permessage-deflate (RFC 7692) for the dashboard WebSocket (docs/SPEC.md "Scaling"): a big base's snapshot and the
 * batched value deltas are JSON with a lot of repetition and shrink 5-10x. Only messages of at least
 * {@link #MIN_BYTES} are compressed. Incoming compressed frames are still checked by {@link FrameSizeGuard} (compressed
 * size) and inflate to at most {@link ClientMessages#MAX_LAYOUT_FRAME} (no decompression bombs).
 */
final class WebSocketCompression {
    static final int MIN_BYTES = 512;
    static final int LEVEL = 6;

    private WebSocketCompression() {
    }

    private static final WebSocketExtensionFilterProvider FILTERS = new WebSocketExtensionFilterProvider() {
        @Override
        public WebSocketExtensionFilter encoderFilter() {
            return frame -> frame.content().readableBytes() < MIN_BYTES;
        }

        @Override
        public WebSocketExtensionFilter decoderFilter() {
            return WebSocketExtensionFilter.NEVER_SKIP;
        }
    };

    /** A new handler per connection: answers a permessage-deflate offer in the upgrade response and installs the codec. */
    static WebSocketServerExtensionHandler handler() {
        return new WebSocketServerExtensionHandler(new PerMessageDeflateServerExtensionHandshaker(LEVEL, false,
            PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE, false, false, FILTERS, ClientMessages.MAX_LAYOUT_FRAME));
    }

    /** True if the client's Sec-WebSocket-Extensions header offers permessage-deflate. */
    static boolean offered(@Nullable String header) {
        return header != null && header.toLowerCase(Locale.ROOT).contains("permessage-deflate");
    }
}
