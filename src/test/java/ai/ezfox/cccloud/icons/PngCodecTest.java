package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class PngCodecTest {
    static int[] pattern() {
        var pixels = new int[32 * 32];
        var random = new Random(42);
        for (int i = 0; i < pixels.length; i++) {
            int x = i % 32, y = i / 32;
            pixels[i] = (x > 4 && x < 28 && y > 4 && y < 28) ? 0xFF000000 | random.nextInt(0xFFFFFF) : 0; // transparent border
        }
        return pixels;
    }

    @Test
    void roundTrip() {
        var pixels = pattern();
        var png = PngCodec.encode(32, 32, pixels);
        assertTrue(png.length < PngCodec.MAX_BYTES, "size " + png.length);
        assertArrayEquals(pixels, PngCodec.decode(png, 32, 32));
        assertTrue(PngCodec.isValid(png, 32, 32));
        assertArrayEquals(new int[]{32, 32}, PngCodec.size(png));
        var flat = new int[32 * 32];
        Arrays.fill(flat, 0xFF3366CC);
        assertTrue(PngCodec.encode(32, 32, flat).length < 200, "flat icons compress well");
    }

    @Test
    void rejectsAnythingElse() {
        var png = PngCodec.encode(32, 32, pattern());
        assertFalse(PngCodec.isValid(png, 16, 16), "wrong size");
        assertFalse(PngCodec.isValid(new byte[0], 32, 32));
        assertFalse(PngCodec.isValid("GIF89a not a png at all, padded to some length....".getBytes(), 32, 32));
        var flipped = png.clone();
        flipped[png.length / 2] ^= 0x55;
        assertFalse(PngCodec.isValid(flipped, 32, 32), "CRC mismatch");
        var truncated = Arrays.copyOf(png, png.length - 5);
        assertFalse(PngCodec.isValid(truncated, 32, 32), "truncated");
        var trailing = Arrays.copyOf(png, png.length + 4);
        assertFalse(PngCodec.isValid(trailing, 32, 32), "trailing data");
        var huge = new byte[PngCodec.MAX_BYTES + 1];
        System.arraycopy(png, 0, huge, 0, png.length);
        assertFalse(PngCodec.isValid(huge, 32, 32), "over the size cap");
    }

    @Test
    void rejectsExtraChunks() {
        var png = PngCodec.encode(32, 32, pattern());
        // Insert a tEXt chunk with a valid CRC after IHDR (8 + 25 bytes).
        var data = "Comment\0hello".getBytes();
        var type = "tEXt".getBytes();
        var crc = new CRC32();
        crc.update(type);
        crc.update(data);
        var chunk = ByteBuffer.allocate(12 + data.length).putInt(data.length).put(type).put(data).putInt((int) crc.getValue()).array();
        var withText = new byte[png.length + chunk.length];
        System.arraycopy(png, 0, withText, 0, 33);
        System.arraycopy(chunk, 0, withText, 33, chunk.length);
        System.arraycopy(png, 33, withText, 33 + chunk.length, png.length - 33);
        assertFalse(PngCodec.isValid(withText, 32, 32), "ancillary chunks are rejected");
    }
}
