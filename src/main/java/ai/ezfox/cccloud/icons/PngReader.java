package ai.ezfox.cccloud.icons;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.jspecify.annotations.Nullable;

/**
 * Reads texture PNGs of any standard format into ARGB pixels, for the server-side icon renderer. Pure Java (no
 * AWT/ImageIO, which jlinked server runtimes may not have), unit tested.
 * <p>
 * Supports every PNG colour type and bit depth (grey, RGB, palette, grey + alpha, RGBA; 1–16 bits), {@code tRNS}
 * transparency and Adam7 interlacing. Ancillary chunks (gamma, colour profiles, text) are ignored; CRCs are not
 * checked (the bytes come from local mod jars). Anything malformed or too large gives {@code null}. With
 * {@code maxRows} only the top rows are decoded, which is all an animation strip's first frame needs.
 */
public final class PngReader {
    /** Largest width accepted (HD texture packs go up to 512, some mods ship 1024). */
    public static final int MAX_WIDTH = 4096;
    /** Largest number of pixels decoded (16 MB of ARGB). */
    public static final int MAX_PIXELS = 4 * 1024 * 1024;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int[][] ADAM7 = {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4}, {0, 2, 2, 4}, {1, 0, 2, 2}, {0, 1, 1, 2}};

    /** Decoded pixels: {@code argb[y * width + x]}, straight (not premultiplied) alpha. */
    public record Image(int width, int height, int[] argb) {
        public int at(int x, int y) {
            return argb[y * width + x];
        }
    }

    private PngReader() {
    }

    /** Just the dimensions from the header; null if not a PNG. */
    public static int @Nullable [] size(byte[] png) {
        return PngCodec.size(png);
    }

    public static @Nullable Image read(byte[] png) {
        return read(png, Integer.MAX_VALUE);
    }

    /**
     * @param maxRows decode at most this many rows from the top (the image's height is cut to it)
     * @return the pixels, or null if the bytes are not a PNG this reader accepts
     */
    public static @Nullable Image read(byte[] png, int maxRows) {
        try {
            return decode(png, maxRows);
        } catch (RuntimeException | DataFormatException e) {
            return null;
        }
    }

    private static @Nullable Image decode(byte[] png, int maxRows) throws DataFormatException {
        if (png.length < SIGNATURE.length + 25 || !Arrays.equals(Arrays.copyOf(png, SIGNATURE.length), SIGNATURE)) return null;
        var in = ByteBuffer.wrap(png);
        in.position(SIGNATURE.length);
        int width = 0, height = 0, depth = 0, type = -1, interlace = 0;
        byte[] palette = null, trns = null;
        var idat = new java.io.ByteArrayOutputStream(png.length);
        boolean header = false;
        while (in.remaining() >= 12) {
            int length = in.getInt();
            if (length < 0 || length > in.remaining() - 8) return null;
            var t = new byte[4];
            in.get(t);
            String chunk = new String(t, StandardCharsets.US_ASCII);
            int start = in.position();
            switch (chunk) {
                case "IHDR" -> {
                    if (length != 13) return null;
                    width = in.getInt();
                    height = in.getInt();
                    depth = in.get() & 0xFF;
                    type = in.get() & 0xFF;
                    if (in.get() != 0 || in.get() != 0) return null; // compression, filter method
                    interlace = in.get() & 0xFF;
                    header = true;
                }
                case "PLTE" -> palette = Arrays.copyOfRange(png, start, start + length);
                case "tRNS" -> trns = Arrays.copyOfRange(png, start, start + length);
                case "IDAT" -> idat.write(png, start, length);
                default -> {
                }
            }
            in.position(start + length + 4); // skip data and CRC
            if (chunk.equals("IEND")) break;
        }
        if (!header || width <= 0 || height <= 0 || width > MAX_WIDTH || interlace > 1) return null;
        int channels = switch (type) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> -1;
        };
        if (channels < 0 || !validDepth(type, depth) || (type == 3 && palette == null)) return null;
        int rows = Math.min(height, Math.max(1, maxRows));
        if ((long) width * (interlace == 1 ? height : rows) > MAX_PIXELS) return null;
        var format = new Format(type, depth, channels, palette, trns);

        var inflater = new Inflater();
        try {
            inflater.setInput(idat.toByteArray());
            var argb = new int[width * rows];
            if (interlace == 0) {
                int stride = (width * channels * depth + 7) / 8;
                var raw = inflate(inflater, rows * (stride + 1));
                if (raw == null) return null;
                unfilterRows(raw, rows, stride, format.bpp());
                for (int y = 0; y < rows; y++) format.row(raw, y * (stride + 1) + 1, width, argb, y * width);
            } else {
                var full = new int[width * height];
                for (var pass : ADAM7) {
                    int pw = (width - pass[0] + pass[2] - 1) / pass[2], ph = (height - pass[1] + pass[3] - 1) / pass[3];
                    if (pw <= 0 || ph <= 0) continue;
                    int stride = (pw * channels * depth + 7) / 8;
                    var raw = inflate(inflater, ph * (stride + 1));
                    if (raw == null) return null;
                    unfilterRows(raw, ph, stride, format.bpp());
                    var line = new int[pw];
                    for (int y = 0; y < ph; y++) {
                        format.row(raw, y * (stride + 1) + 1, pw, line, 0);
                        int fy = pass[1] + y * pass[3];
                        for (int x = 0; x < pw; x++) full[fy * width + pass[0] + x * pass[2]] = line[x];
                    }
                }
                System.arraycopy(full, 0, argb, 0, argb.length);
            }
            return new Image(width, rows, argb);
        } finally {
            inflater.end();
        }
    }

    private static boolean validDepth(int type, int depth) {
        return switch (type) {
            case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
            case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
            default -> depth == 8 || depth == 16;
        };
    }

    /** Inflates exactly {@code n} more bytes from the stream; null if it ends early. */
    private static byte @Nullable [] inflate(Inflater inflater, int n) throws DataFormatException {
        var out = new byte[n];
        int done = 0;
        while (done < n) {
            int got = inflater.inflate(out, done, n - done);
            if (got == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) return null;
            done += got;
        }
        return out;
    }

    /** Reverses the per-row filters in place (each row: one filter byte, then {@code stride} bytes). */
    private static void unfilterRows(byte[] raw, int rows, int stride, int bpp) {
        for (int y = 0; y < rows; y++) {
            int row = y * (stride + 1);
            int filter = raw[row];
            int cur = row + 1, prev = row - stride; // previous row's data (valid when y > 0)
            switch (filter) {
                case 0 -> {
                }
                case 1 -> {
                    for (int i = bpp; i < stride; i++) raw[cur + i] += raw[cur + i - bpp];
                }
                case 2 -> {
                    if (y > 0) for (int i = 0; i < stride; i++) raw[cur + i] += raw[prev + i];
                }
                case 3 -> {
                    for (int i = 0; i < stride; i++) {
                        int a = i >= bpp ? raw[cur + i - bpp] & 0xFF : 0;
                        int b = y > 0 ? raw[prev + i] & 0xFF : 0;
                        raw[cur + i] += (byte) ((a + b) >>> 1);
                    }
                }
                case 4 -> {
                    for (int i = 0; i < stride; i++) {
                        int a = i >= bpp ? raw[cur + i - bpp] & 0xFF : 0;
                        int b = y > 0 ? raw[prev + i] & 0xFF : 0;
                        int c = i >= bpp && y > 0 ? raw[prev + i - bpp] & 0xFF : 0;
                        raw[cur + i] += (byte) paeth(a, b, c);
                    }
                }
                default -> throw new IllegalArgumentException("bad filter " + filter);
            }
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    /** Colour type, bit depth and the tables needed to turn samples into ARGB. */
    private record Format(int type, int depth, int channels, byte @Nullable [] palette, byte @Nullable [] trns) {
        /** Filter step in bytes. */
        int bpp() {
            return Math.max(1, channels * depth / 8);
        }

        /** Converts one unfiltered row of {@code width} pixels starting at {@code at} into ARGB. */
        void row(byte[] raw, int at, int width, int[] out, int outAt) {
            for (int x = 0; x < width; x++) out[outAt + x] = pixel(raw, at, x);
        }

        private int sample(byte[] raw, int at, int index) {
            return switch (depth) {
                case 16 -> (raw[at + index * 2] & 0xFF) << 8 | raw[at + index * 2 + 1] & 0xFF;
                case 8 -> raw[at + index] & 0xFF;
                default -> {
                    int bit = index * depth;
                    int b = raw[at + bit / 8] & 0xFF;
                    yield (b >>> (8 - depth - bit % 8)) & ((1 << depth) - 1);
                }
            };
        }

        /** A sample scaled to 8 bits. */
        private int eight(int v) {
            return switch (depth) {
                case 16 -> v >>> 8;
                case 8 -> v;
                default -> v * 255 / ((1 << depth) - 1);
            };
        }

        private int pixel(byte[] raw, int at, int x) {
            int c = x * channels;
            switch (type) {
                case 0 -> {
                    int v = sample(raw, at, c);
                    int a = trns != null && trns.length >= 2 && v == ((trns[0] & 0xFF) << 8 | trns[1] & 0xFF) ? 0 : 255;
                    int g = eight(v);
                    return a << 24 | g << 16 | g << 8 | g;
                }
                case 2 -> {
                    int r = sample(raw, at, c), g = sample(raw, at, c + 1), b = sample(raw, at, c + 2);
                    int a = 255;
                    if (trns != null && trns.length >= 6 && r == ((trns[0] & 0xFF) << 8 | trns[1] & 0xFF)
                        && g == ((trns[2] & 0xFF) << 8 | trns[3] & 0xFF) && b == ((trns[4] & 0xFF) << 8 | trns[5] & 0xFF)) a = 0;
                    return a << 24 | eight(r) << 16 | eight(g) << 8 | eight(b);
                }
                case 3 -> {
                    int i = sample(raw, at, c);
                    if (palette == null || i * 3 + 2 >= palette.length) return 0;
                    int a = trns != null && i < trns.length ? trns[i] & 0xFF : 255;
                    return a << 24 | (palette[i * 3] & 0xFF) << 16 | (palette[i * 3 + 1] & 0xFF) << 8 | palette[i * 3 + 2] & 0xFF;
                }
                case 4 -> {
                    int g = eight(sample(raw, at, c)), a = eight(sample(raw, at, c + 1));
                    return a << 24 | g << 16 | g << 8 | g;
                }
                default -> {
                    return eight(sample(raw, at, c + 3)) << 24 | eight(sample(raw, at, c)) << 16 | eight(sample(raw, at, c + 1)) << 8
                        | eight(sample(raw, at, c + 2));
                }
            }
        }
    }
}
