package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import org.junit.jupiter.api.Test;

/** Texture PNGs in every format mods ship (written here by ImageIO from synthetic images, no game assets). */
class PngReaderTest {
    private static byte[] png(BufferedImage image, boolean interlaced) throws IOException {
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var out = new ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            var param = writer.getDefaultWriteParam();
            if (interlaced) param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static int pattern(int x, int y) {
        return 0xFF000000 | (x * 13 & 0xFF) << 16 | (y * 29 & 0xFF) << 8 | ((x ^ y) * 7 & 0xFF);
    }

    @Test
    void readsRgbaRgbAndOurOwnEncoder() throws IOException {
        var argb = new BufferedImage(19, 7, BufferedImage.TYPE_INT_ARGB);
        var rgb = new BufferedImage(19, 7, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 19; x++) {
                argb.setRGB(x, y, x == 3 ? 0x00000000 : (pattern(x, y) & 0x00FFFFFF) | (x * 12) << 24);
                rgb.setRGB(x, y, pattern(x, y));
            }
        }
        for (boolean interlaced : new boolean[]{false, true}) {
            var a = PngReader.read(png(argb, interlaced));
            var b = PngReader.read(png(rgb, interlaced));
            assertNotNull(a);
            assertNotNull(b);
            assertEquals(19, a.width());
            assertEquals(7, a.height());
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 19; x++) {
                    if (x != 3) assertEquals(argb.getRGB(x, y), a.at(x, y), "rgba " + x + "," + y + " interlaced=" + interlaced);
                    assertEquals(rgb.getRGB(x, y), b.at(x, y), "rgb " + x + "," + y);
                }
            }
            assertEquals(0, a.at(3, 0) >>> 24);
        }
        var px = new int[5 * 4];
        for (int i = 0; i < px.length; i++) px[i] = pattern(i, i * 3);
        assertArrayEquals(px, PngReader.read(PngCodec.encode(5, 4, px)).argb());
    }

    @Test
    void readsPalettesWithTransparencyAtEveryBitDepth() throws IOException {
        for (int bits : new int[]{1, 2, 4, 8}) {
            int colors = 1 << bits;
            var r = new byte[colors];
            var g = new byte[colors];
            var b = new byte[colors];
            var a = new byte[colors];
            for (int i = 0; i < colors; i++) {
                r[i] = (byte) (i * 255 / (colors - 1));
                g[i] = (byte) (255 - i * 255 / (colors - 1));
                b[i] = (byte) 0x40;
                a[i] = (byte) (i == 0 ? 0 : 255);
            }
            var model = new IndexColorModel(bits, colors, r, g, b, a);
            var image = bits == 8 ? new BufferedImage(10, 6, BufferedImage.TYPE_BYTE_INDEXED, model)
                : new BufferedImage(10, 6, BufferedImage.TYPE_BYTE_BINARY, model);
            for (int y = 0; y < 6; y++) for (int x = 0; x < 10; x++) image.getRaster().setSample(x, y, 0, (x + y) % colors);
            var read = PngReader.read(png(image, false));
            assertNotNull(read, bits + " bit");
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 10; x++) {
                    int i = (x + y) % colors;
                    int expected = i == 0 ? 0 : 0xFF000000 | (r[i] & 0xFF) << 16 | (g[i] & 0xFF) << 8 | 0x40;
                    if (i == 0) assertEquals(0, read.at(x, y) >>> 24, bits + " bit transparent index");
                    else assertEquals(expected, read.at(x, y), bits + " bit at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void readsGreyAndSixteenBit() throws IOException {
        var grey = new BufferedImage(8, 3, BufferedImage.TYPE_BYTE_GRAY);
        var grey16 = new BufferedImage(8, 3, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 8; x++) {
                grey.getRaster().setSample(x, y, 0, x * 30 + y);
                grey16.getRaster().setSample(x, y, 0, (x * 30 + y) * 257);
            }
        }
        var a = PngReader.read(png(grey, false));
        var b = PngReader.read(png(grey16, true));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 8; x++) {
                int v = x * 30 + y;
                assertEquals(0xFF000000 | v << 16 | v << 8 | v, a.at(x, y));
                assertEquals(0xFF000000 | v << 16 | v << 8 | v, b.at(x, y));
            }
        }
    }

    @Test
    void decodesOnlyTheRowsAsked() throws IOException {
        var strip = new BufferedImage(4, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 4; x++) strip.setRGB(x, y, y < 4 ? 0xFFFF0000 : 0xFF0000FF);
        var first = PngReader.read(png(strip, false), 4);
        assertEquals(4, first.height());
        for (int c : first.argb()) assertEquals(0xFFFF0000, c);
    }

    @Test
    void rejectsGarbageAndHugeImages() {
        assertNull(PngReader.read(new byte[0]));
        assertNull(PngReader.read("not a png at all, really not".getBytes()));
        var valid = PngCodec.encode(4, 4, new int[16]);
        assertNull(PngReader.read(java.util.Arrays.copyOf(valid, valid.length - 20)), "truncated");
        var huge = valid.clone();
        // IHDR width = 100000 (CRCs are not checked, the size limit is)
        huge[16] = 0;
        huge[17] = 1;
        huge[18] = (byte) 0x86;
        huge[19] = (byte) 0xA0;
        assertNull(PngReader.read(huge));
    }
}
