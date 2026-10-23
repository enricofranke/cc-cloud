package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

/** Security review L6: body limits before aggregation. */
class BodyLimitTest {
    static final String PUT = "/api/mainframes/00000000-0000-0000-0000-000000000000/computers/00000000-0000-0000-0000-000000000001/files/a.lua";

    @Test
    void declaredAndStreamedBodiesAreLimited() {
        var channel = new EmbeddedChannel(new BodyLimit());
        HttpRequest big = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/pair");
        big.headers().set(HttpHeaderNames.CONTENT_LENGTH, 100_000);
        channel.writeInbound(big);
        FullHttpResponse response = channel.readOutbound();
        assertEquals(413, response.status().code(), "declared too large");
        assertNull(channel.readInbound(), "nothing reaches the aggregator");

        var streamed = new EmbeddedChannel(new BodyLimit());
        HttpRequest chunked = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/pair");
        chunked.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        streamed.writeInbound(chunked);
        streamed.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[3000])));
        streamed.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[3000])));
        FullHttpResponse cut = streamed.readOutbound();
        assertEquals(413, cut.status().code(), "streamed over 4 KB");

        var upload = new EmbeddedChannel(new BodyLimit());
        HttpRequest put = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, PUT);
        put.headers().set(HttpHeaderNames.CONTENT_LENGTH, 100_000);
        put.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer abc");
        upload.writeInbound(put);
        assertNull(upload.readOutbound(), "a script upload with a token may be 256 KB");
        assertNotNull(upload.readInbound());

        var anonymous = new EmbeddedChannel(new BodyLimit());
        HttpRequest anon = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, PUT);
        anon.headers().set(HttpHeaderNames.CONTENT_LENGTH, 100_000);
        anonymous.writeInbound(anon);
        assertEquals(413, ((FullHttpResponse) anonymous.readOutbound()).status().code(), "without a token only 4 KB");
    }
}
