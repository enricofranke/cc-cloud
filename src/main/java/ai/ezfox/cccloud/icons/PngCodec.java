package ai.ezfox.cccloud.icons;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jspecify.annotations.Nullable;

/**
 * A minimal PNG encoder and a strict validator for item icons. Pure Java (no AWT/ImageIO, which jlinked game runtimes
 * may not have), unit tested.
 * <p>
 * Icons are always non-interlaced 8-bit RGBA. The validator only accepts exactly that: signature, IHDR (exact size),
 * one or more IDAT chunks, IEND; every CRC must match and the image data must inflate to exactly the expected size
 * with valid filter bytes. Ancillary chunks are rejected, so uploaded icons cannot smuggle anything along.
 */
public final class PngCodec {
    public static final int ICON_SIZE = 32;
    public static final int MAX_BYTES = 16 * 1024;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngCodec() {
    }

    /** Encodes ARGB pixels (row-major, top to bottom) as an RGBA PNG. */
    public static byte[] encode(int width, int height, int[] argb) {
        if (argb.length != width * height) throw new IllegalArgumentException("pixel count");
        // Filter type 1 (Sub) per row: good compression for icons with flat areas.
        var raw = new byte[height * (1 + width * 4)];
        int o = 0;
        for (int y = 0; y < height; y++) {
            raw[o++] = 1;
            int prevR = 0, prevG = 0, prevB = 0, prevA = 0;
            for (int x = 0; x < width; x++) {
                int p = argb[y * width + x];
                int a = p >>> 24, r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                if (a == 0) r = g = b = 0; // canonical transparent pixels compress better
                raw[o++] = (byte) (r - prevR);
                raw[o++] = (byte) (g - prevG);
                raw[o++] = (byte) (b - prevB);
                raw[o++] = (byte) (a - prevA);
                prevR = r;
                prevG = g;
                prevB = b;
                prevA = a;
            }
        }
        var deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        var compressed = new ByteArrayOutputStream(raw.length / 4);
        var buf = new byte[8192];
        while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf));
        deflater.end();

        var out = new ByteArrayOutputStream(compressed.size() + 64);
        out.writeBytes(SIGNATURE);
        var ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        var t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(t);
        out.writeBytes(data);
        var crc = new CRC32();
        crc.update(t);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    /** True if the bytes are a valid width×height 8-bit RGBA PNG within {@link #MAX_BYTES}. */
    public static boolean isValid(byte[] png, int width, int height) {
        return decode(png, width, height) != null;
    }

    /** Decodes a PNG produced by {@link #encode} (or any PNG passing the same strict checks); null if invalid. */
    public static int @Nullable [] decode(byte[] png, int width, int height) {
        if (png.length > MAX_BYTES || png.length < SIGNATURE.length + 12 * 3) return null;
        if (!Arrays.equals(Arrays.copyOf(png, SIGNATURE.length), SIGNATURE)) return null;
        var in = ByteBuffer.wrap(png);
        in.position(SIGNATURE.length);
        var idat = new ByteArrayOutputStream();
        boolean header = false, end = false;
        while (in.remaining() >= 12) {
            int length = in.getInt();
            if (length < 0 || length > in.remaining() - 8) return null;
            var typeBytes = new byte[4];
            in.get(typeBytes);
            var data = new byte[length];
            in.get(data);
            int crcValue = in.getInt();
            var crc = new CRC32();
            crc.update(typeBytes);
            crc.update(data);
            if ((int) crc.getValue() != crcValue) return null;
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            switch (type) {
                case "IHDR" -> {
                    if (header || length != 13) return null;
                    var h = ByteBuffer.wrap(data);
                    if (h.getInt() != width || h.getInt() != height) return null;
                    if (h.get() != 8 || h.get() != 6 || h.get() != 0 || h.get() != 0 || h.get() != 0) return null;
                    header = true;
                }
                case "IDAT" -> {
                    if (!header) return null;
                    idat.writeBytes(data);
                }
                case "IEND" -> {
                    if (!header || length != 0) return null;
                    end = true;
                }
                default -> {
                    return null;
                }
            }
            if (end) break;
        }
        if (!end || in.hasRemaining()) return null;

        int stride = width * 4;
        var raw = new byte[height * (stride + 1)];
        var inflater = new Inflater();
        try {
            inflater.setInput(idat.toByteArray());
            int n = inflater.inflate(raw);
            if (n != raw.length || !inflater.finished()) return null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        var pixels = new int[width * height];
        var prev = new byte[stride];
        var cur = new byte[stride];
        for (int y = 0; y < height; y++) {
            int filter = raw[y * (stride + 1)];
            System.arraycopy(raw, y * (stride + 1) + 1, cur, 0, stride);
            for (int i = 0; i < stride; i++) {
                int a = i >= 4 ? cur[i - 4] & 0xFF : 0;
                int b = prev[i] & 0xFF;
                int c = i >= 4 ? prev[i - 4] & 0xFF : 0;
                int x = cur[i] & 0xFF;
                int v = switch (filter) {
                    case 0 -> x;
                    case 1 -> x + a;
                    case 2 -> x + b;
                    case 3 -> x + ((a + b) >> 1);
                    case 4 -> x + paeth(a, b, c);
                    default -> -1;
                };
                if (v < 0) return null;
                cur[i] = (byte) v;
            }
            for (int px = 0; px < width; px++) {
                int r = cur[px * 4] & 0xFF, g = cur[px * 4 + 1] & 0xFF, bl = cur[px * 4 + 2] & 0xFF, al = cur[px * 4 + 3] & 0xFF;
                pixels[y * width + px] = al << 24 | r << 16 | g << 8 | bl;
            }
            var t = prev;
            prev = cur;
            cur = t;
        }
        return pixels;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    /** Just the dimensions of a PNG (for fallback textures of unknown size); null if not a PNG. */
    public static int @Nullable [] size(byte[] png) {
        if (png.length < 24 || !Arrays.equals(Arrays.copyOf(png, SIGNATURE.length), SIGNATURE)) return null;
        var b = ByteBuffer.wrap(png, 16, 8);
        return new int[]{b.getInt(), b.getInt()};
    }
}
