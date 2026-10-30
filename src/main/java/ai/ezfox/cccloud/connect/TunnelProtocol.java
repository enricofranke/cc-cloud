package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The tunnel protocol (version 1) between the mod and the CC: Cloud Connect relay. One WebSocket per server; the relay
 * side is {@code connect/src/protocol.ts} in the website repository, both are tested with the same frames.
 * <p>
 * Text frames are JSON control messages (challenge, auth, ready, error, release) and the keep-alive {@code "ping"}.
 * Binary frames carry the multiplexed streams: {@code [u8 type][u32 stream id][payload]}, big endian.
 * <ul>
 *   <li>relay → mod: {@link #HTTP_REQUEST} {@code [u32 head length][head JSON][body]}, {@link #WS_OPEN} (head JSON),
 *       {@link #WS_TEXT}, {@link #WS_BINARY}, {@link #WS_CLOSE} {@code [u16 code][reason]}, {@link #CANCEL};</li>
 *   <li>mod → relay: {@link #HTTP_HEAD} {@code {"s":status,"h":[[name,value]…]}}, {@link #HTTP_BODY}, {@link #HTTP_END},
 *       {@link #HTTP_ERROR}, {@link #WS_ACCEPTED}, {@link #WS_REJECTED} {@code {"s":status,"e":error}} and the
 *       WebSocket frames above.</li>
 * </ul>
 * The request head is {@code {"m":method,"p":path+query,"h":[[name,value]…],"ip":client IP,"host":"<name>.cccloud.dev"}}.
 * Pure Java, unit tested.
 */
public final class TunnelProtocol {
    public static final int VERSION = 1;

    public static final int HTTP_REQUEST = 0x01;
    public static final int WS_OPEN = 0x02;
    public static final int WS_TEXT = 0x03;
    public static final int WS_BINARY = 0x04;
    public static final int WS_CLOSE = 0x05;
    public static final int CANCEL = 0x06;
    public static final int HTTP_HEAD = 0x11;
    public static final int HTTP_BODY = 0x12;
    public static final int HTTP_END = 0x13;
    public static final int WS_ACCEPTED = 0x14;
    public static final int WS_REJECTED = 0x15;
    public static final int HTTP_ERROR = 0x16;

    /** Request heads larger than this are refused (the relay sends at most a few KB). */
    public static final int MAX_HEAD = 16 * 1024;

    private TunnelProtocol() {
    }

    /** The text the mod signs with its server key to prove it owns {@code name}. */
    public static String signedText(String name, String nonce, String publicKey) {
        return "cccloud-connect-v1\n" + name + "\n" + nonce + "\n" + publicKey;
    }

    /** A decoded binary frame; {@code payload} is a slice of the received buffer (valid while that is). */
    public record Frame(int type, long stream, ByteBuf payload) {
    }

    /** A request head from the relay. */
    public record Head(String method, String path, List<Map.Entry<String, String>> headers, String ip, String host) {
    }

    public static @Nullable Frame parse(ByteBuf data) {
        if (data.readableBytes() < 5) return null;
        int at = data.readerIndex();
        int type = data.getUnsignedByte(at);
        long stream = data.getUnsignedInt(at + 1);
        return new Frame(type, stream, data.slice(at + 5, data.readableBytes() - 5));
    }

    public static ByteBuf frame(ByteBufAllocator alloc, int type, long stream, @Nullable ByteBuf payload) {
        int size = payload == null ? 0 : payload.readableBytes();
        var out = alloc.buffer(5 + size);
        out.writeByte(type);
        out.writeInt((int) stream);
        if (payload != null) out.writeBytes(payload, payload.readerIndex(), size);
        return out;
    }

    public static ByteBuf frame(ByteBufAllocator alloc, int type, long stream, byte[] payload) {
        var out = alloc.buffer(5 + payload.length);
        out.writeByte(type);
        out.writeInt((int) stream);
        out.writeBytes(payload);
        return out;
    }

    public static ByteBuf jsonFrame(ByteBufAllocator alloc, int type, long stream, JsonObject json) {
        return frame(alloc, type, stream, Json.GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    public static ByteBuf closeFrame(ByteBufAllocator alloc, long stream, int code, String reason) {
        byte[] text = reason.length() > 120 ? reason.substring(0, 120).getBytes(StandardCharsets.UTF_8) : reason.getBytes(StandardCharsets.UTF_8);
        var out = alloc.buffer(7 + text.length);
        out.writeByte(WS_CLOSE);
        out.writeInt((int) stream);
        out.writeShort(code);
        out.writeBytes(text);
        return out;
    }

    /** The response head of an HTTP answer: {@code {"s":status,"h":[[name,value]…]}}. */
    public static JsonObject responseHead(int status, Iterable<Map.Entry<String, String>> headers) {
        var json = new JsonObject();
        json.addProperty("s", status);
        var list = new JsonArray();
        for (var e : headers) {
            var pair = new JsonArray();
            pair.add(e.getKey());
            pair.add(e.getValue());
            list.add(pair);
        }
        json.add("h", list);
        return json;
    }

    /** Splits an {@link #HTTP_REQUEST} payload into head and body (a slice); null if malformed. */
    public static @Nullable Request request(ByteBuf payload) {
        if (payload.readableBytes() < 4) return null;
        long len = payload.getUnsignedInt(payload.readerIndex());
        if (len > MAX_HEAD || len > payload.readableBytes() - 4) return null;
        var head = head(payload.toString(payload.readerIndex() + 4, (int) len, StandardCharsets.UTF_8));
        if (head == null) return null;
        int bodyAt = payload.readerIndex() + 4 + (int) len;
        return new Request(head, payload.slice(bodyAt, payload.writerIndex() - bodyAt));
    }

    public record Request(Head head, ByteBuf body) {
    }

    /** Parses a request head; null if anything is missing or out of shape. */
    public static @Nullable Head head(String text) {
        if (text.length() > MAX_HEAD) return null;
        try {
            var root = JsonParser.parseString(text);
            if (!root.isJsonObject()) return null;
            var o = root.getAsJsonObject();
            String method = string(o, "m");
            String path = string(o, "p");
            String ip = string(o, "ip");
            String host = string(o, "host");
            if (method == null || path == null || ip == null || host == null || !path.startsWith("/") || path.length() > 4096
                || !method.matches("[A-Z]{3,7}") || !host.matches("[a-z0-9.:-]{1,253}")) return null;
            var headers = new ArrayList<Map.Entry<String, String>>();
            if (o.get("h") instanceof JsonArray list) {
                for (var el : list) {
                    if (!(el instanceof JsonArray pair) || pair.size() != 2 || !pair.get(0).isJsonPrimitive() || !pair.get(1).isJsonPrimitive()) return null;
                    String name = pair.get(0).getAsString();
                    String value = pair.get(1).getAsString();
                    // never a header injection: names are tokens, values have no line breaks
                    if (!name.matches("[A-Za-z0-9-]{1,64}") || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.length() > 8192) return null;
                    headers.add(Map.entry(name.toLowerCase(java.util.Locale.ROOT), value));
                }
            }
            return new Head(method, path, List.copyOf(headers), ip, host);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String string(JsonObject o, String key) {
        var el = o.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    /** {@code [u16 code][reason]} of a {@link #WS_CLOSE} payload. */
    public static int closeCode(ByteBuf payload) {
        return payload.readableBytes() >= 2 ? payload.getUnsignedShort(payload.readerIndex()) : 1000;
    }

    public static String closeReason(ByteBuf payload) {
        if (payload.readableBytes() <= 2) return "";
        String r = payload.toString(payload.readerIndex() + 2, Math.min(payload.readableBytes() - 2, 123), StandardCharsets.UTF_8);
        return r.length() > 120 ? r.substring(0, 120) : r;
    }
}
