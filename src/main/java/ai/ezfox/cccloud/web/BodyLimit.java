package ai.ezfox.cccloud.web;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;

/**
 * Body limits before aggregation (security review L6): 4 KB for every request, except a script upload that carries a
 * Bearer token (256 KB + 1 KB). Over-long bodies (declared or streamed) get 413 and the connection is closed, so nobody
 * makes the server buffer 256 KB without even claiming to be a device. Sits between the HTTP codec and the aggregator.
 */
final class BodyLimit extends ChannelInboundHandlerAdapter {
    static final int SMALL = ClientMessages.MAX_BODY;
    static final int SCRIPT = ai.ezfox.cccloud.script.ScriptFiles.MAX_FILE + 1024;
    private long limit = SMALL;
    private long seen;
    private boolean rejected;

    static long limitFor(HttpRequest request) {
        var route = Router.match(request.method(), new QueryStringDecoder(request.uri()).path());
        String auth = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        boolean bearer = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7);
        return route != null && route.id() == Router.Id.SCRIPT_PUT && bearer ? SCRIPT : SMALL;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest request) {
            limit = limitFor(request);
            seen = 0;
            rejected = false;
            long declared = HttpUtil.getContentLength(request, -1L);
            if (declared > limit) {
                reject(ctx, msg);
                return;
            }
        }
        if (rejected) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (msg instanceof HttpContent content) {
            seen += content.content().readableBytes();
            if (seen > limit) {
                reject(ctx, msg);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void reject(ChannelHandlerContext ctx, Object msg) {
        rejected = true;
        ReferenceCountUtil.release(msg);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
            Unpooled.copiedBuffer("{\"error\":\"too_large\"}", java.nio.charset.StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
