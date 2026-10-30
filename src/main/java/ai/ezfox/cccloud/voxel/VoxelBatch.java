package ai.ezfox.cccloud.voxel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * The binary batch format of the voxel view (docs/SPEC.md "Voxel view – batch API"): many sections in one response.
 * <pre>
 *   "CCVB"  version (1 byte)  header length (u32, big endian)  header (UTF-8 JSON)  runs (bytes)
 * </pre>
 * The header is {@code {"dim","palette":[{id,color,flags,tex?,tint?}],"atlas"?,"sections":[…]}}; every section entry
 * has {@code "c":[cx,cy,cz]} and {@code "s"} (status). {@code ok} sections also have {@code "h"} (content hash, 16 hex
 * digits), {@code "n"} (exposed blocks), {@code "p"} (the section's palette as indices into the batch palette) and
 * {@code "l"} (byte length of its runs); their runs follow the header in section order. {@code same} sections (the
 * client's hash is current) carry {@code "h"} and {@code "p"} only. The runs are the unsigned varint pairs
 * {@code (run length, palette index)} of {@link VoxelSection} (index n = {@code palette[p[n-1]]}, 0 = nothing).
 * Pure Java, unit tested.
 */
public final class VoxelBatch {
    public static final byte[] MAGIC = {'C', 'C', 'V', 'B'};
    public static final int VERSION = 1;
    /** Most sections per request. */
    public static final int MAX_SECTIONS = 64;

    public enum Status {
        /** data follows */
        OK("ok"),
        /** the client's hash is current */
        SAME("same"),
        /** the chunk is not loaded (or outside the world): nothing to show now */
        EMPTY("empty"),
        OUT_OF_RANGE("out_of_range"),
        CLAIMED("claimed");

        public final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        static Status of(String wire) {
            for (var s : values()) if (s.wire.equals(wire)) return s;
            throw new IllegalArgumentException("bad status " + wire);
        }
    }

    /** One section of a response; {@code data} for OK and SAME. */
    public record Section(int cx, int cy, int cz, Status status, VoxelSection.@Nullable Encoded data) {
    }

    private VoxelBatch() {
    }

    /**
     * Writes a batch. {@code decorate} may add stage-2 texture fields to the header (palette entries and "atlas"), see
     * {@link BlockTextureService#decorate}.
     */
    public static byte[] write(String dim, List<Section> sections, @Nullable Consumer<JsonObject> decorate) {
        var palette = new JsonArray();
        var paletteIndex = new HashMap<VoxelSection.Kind, Integer>();
        var entries = new JsonArray();
        var runs = new ByteArrayOutputStream();
        for (var s : sections) {
            var e = new JsonObject();
            var c = new JsonArray();
            c.add(s.cx());
            c.add(s.cy());
            c.add(s.cz());
            e.add("c", c);
            e.addProperty("s", s.status().wire);
            var data = s.data();
            if (data != null && (s.status() == Status.OK || s.status() == Status.SAME)) {
                e.addProperty("h", data.hashHex());
                var p = new JsonArray();
                for (var kind : data.palette()) {
                    Integer i = paletteIndex.get(kind);
                    if (i == null) {
                        i = palette.size();
                        paletteIndex.put(kind, i);
                        var k = new JsonObject();
                        k.addProperty("id", kind.id());
                        k.addProperty("color", kind.color());
                        k.addProperty("flags", kind.flags());
                        palette.add(k);
                    }
                    p.add(i);
                }
                e.add("p", p);
                if (s.status() == Status.OK) {
                    e.addProperty("n", data.exposed());
                    e.addProperty("l", data.runs().length);
                    runs.writeBytes(data.runs());
                }
            }
            entries.add(e);
        }
        var header = new JsonObject();
        header.addProperty("dim", dim);
        header.add("palette", palette);
        if (decorate != null) decorate.accept(header);
        header.add("sections", entries);
        byte[] json = header.toString().getBytes(StandardCharsets.UTF_8);
        var out = new ByteArrayOutputStream(9 + json.length + runs.size());
        out.writeBytes(MAGIC);
        out.write(VERSION);
        out.write(json.length >>> 24);
        out.write(json.length >>> 16);
        out.write(json.length >>> 8);
        out.write(json.length);
        out.writeBytes(json);
        out.writeBytes(runs.toByteArray());
        return out.toByteArray();
    }

    public static byte[] gzip(byte[] data) {
        var out = new ByteArrayOutputStream(data.length / 3 + 64);
        try (var gz = new GZIPOutputStream(out, 8192)) {
            gz.write(data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** A decoded section (tests, documentation of the format): palette entries as JSON, indices per cell. */
    public record Decoded(int cx, int cy, int cz, Status status, @Nullable String hash, List<JsonObject> palette, int @Nullable [] cells, int exposed) {
    }

    /** Reads a batch (for tests and as the reference of the format). */
    public static List<Decoded> read(byte[] data) {
        if (data.length < 9 || data[0] != 'C' || data[1] != 'C' || data[2] != 'V' || data[3] != 'B') throw new IllegalArgumentException("not a voxel batch");
        if (data[4] != VERSION) throw new IllegalArgumentException("version " + data[4]);
        int len = ((data[5] & 0xFF) << 24) | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 8) | (data[8] & 0xFF);
        if (len < 0 || 9 + len > data.length) throw new IllegalArgumentException("bad header length");
        var header = JsonParser.parseString(new String(data, 9, len, StandardCharsets.UTF_8)).getAsJsonObject();
        var palette = header.getAsJsonArray("palette");
        int at = 9 + len;
        var out = new ArrayList<Decoded>();
        for (var el : header.getAsJsonArray("sections")) {
            var e = el.getAsJsonObject();
            var c = e.getAsJsonArray("c");
            var status = Status.of(e.get("s").getAsString());
            var entries = new ArrayList<JsonObject>();
            if (e.has("p")) for (var i : e.getAsJsonArray("p")) entries.add(palette.get(i.getAsInt()).getAsJsonObject());
            int[] cells = null;
            int exposed = 0;
            if (status == Status.OK) {
                int l = e.get("l").getAsInt();
                if (l < 0 || at + l > data.length) throw new IllegalArgumentException("runs out of bounds");
                cells = VoxelSection.decode(java.util.Arrays.copyOfRange(data, at, at + l));
                at += l;
                exposed = e.get("n").getAsInt();
                for (int v : cells) if (v > entries.size()) throw new IllegalArgumentException("index out of palette");
            }
            out.add(new Decoded(c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt(), status, e.has("h") ? e.get("h").getAsString() : null,
                entries, cells, exposed));
        }
        if (at != data.length) throw new IllegalArgumentException("trailing bytes");
        return out;
    }
}
