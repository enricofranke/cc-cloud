package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World SavedData ({@code data/cccloud/cloud_computers.dat}): the next free computer id and the label of each
 * computer. Server thread only. The files of a computer live in its drive folder, its on/off state in its block entity.
 */
public final class ComputerData extends SavedData {
    private static final Codec<ComputerData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("nextId", 0).forGetter(d -> d.nextId),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("labels", Map.of()).forGetter(d -> {
            var out = new HashMap<String, String>();
            d.labels.forEach((id, label) -> out.put(Integer.toString(id), label));
            return out;
        })
    ).apply(i, ComputerData::new));

    public static final SavedDataType<ComputerData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "cloud_computers"), ComputerData::new, CODEC);

    private int nextId;
    private final Map<Integer, String> labels = new HashMap<>();

    public ComputerData() {
    }

    private ComputerData(int nextId, Map<String, String> labels) {
        this.nextId = Math.max(0, nextId);
        for (var e : labels.entrySet()) {
            try {
                int id = Integer.parseInt(e.getKey());
                String label = ComputerRegistry.cleanLabel(e.getValue());
                if (id >= 0 && id < this.nextId && label != null) this.labels.put(id, label);
            } catch (NumberFormatException ignored) {
                // a hand-edited key: skip it
            }
        }
    }

    /** A new id, never handed out before in this world. */
    public synchronized int allocate() {
        if (nextId == Integer.MAX_VALUE) throw new IllegalStateException("no computer ids left");
        int id = nextId++;
        setDirty();
        return id;
    }

    /** Whether the id was handed out by {@link #allocate}. */
    public synchronized boolean known(int id) {
        return id >= 0 && id < nextId;
    }

    public synchronized @Nullable String label(int id) {
        return labels.get(id);
    }

    public synchronized void setLabel(int id, @Nullable String label) {
        String old = label == null ? labels.remove(id) : labels.put(id, label);
        if (!java.util.Objects.equals(old, label)) setDirty();
    }

    /** Test hook: what the codec makes of this data (encode, then decode). */
    public static ComputerData roundTrip(ComputerData data) {
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        return CODEC.parse(ops, CODEC.encodeStart(ops, data).getOrThrow()).getOrThrow();
    }
}
