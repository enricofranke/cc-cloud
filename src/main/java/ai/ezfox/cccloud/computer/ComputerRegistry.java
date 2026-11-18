package ai.ezfox.cccloud.computer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The Cloud Computers of a world: ids and labels ({@link ComputerData}) and the computers that are loaded right now.
 *
 * <p>Block entity contract (server thread): on placement {@link #create()} gives the id; the block entity saves the
 * id and whether the computer was on. When it loads it calls {@link #load(int, ComputerHost, boolean)}, every server
 * tick {@link CloudComputer#tick()}, and when it is removed or its chunk unloads {@link #unload(int)} (which stops
 * the program). Breaking the block keeps id, label and files (the item carries the id).
 */
public final class ComputerRegistry {
    public static final int MAX_LABEL = 32;
    /** The folder under the world save that holds one folder per computer id. */
    public static final String FOLDER = "cccloud/computers";
    private static final Map<MinecraftServer, ComputerRegistry> BY_SERVER = new ConcurrentHashMap<>();

    private final ComputerData data;
    private final Path root;
    private final Map<Integer, CloudComputer> loaded = new ConcurrentHashMap<>();

    /** A registry over this data with drive folders under {@code root} (tests; the game uses {@link #get}). */
    public ComputerRegistry(ComputerData data, Path root) {
        this.data = data;
        this.root = root;
    }

    public static ComputerRegistry get(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, s -> new ComputerRegistry(s.getDataStorage().computeIfAbsent(ComputerData.TYPE),
            s.getWorldPath(LevelResource.ROOT).resolve(FOLDER)));
    }

    /** Server stopping: every computer stops, the registry is forgotten. */
    public static void stop(MinecraftServer server) {
        var registry = BY_SERVER.remove(server);
        if (registry != null) registry.unloadAll();
    }

    /** A new computer id. */
    public int create() {
        return data.allocate();
    }

    /**
     * The live computer for {@code id} with this host (a second load of a loaded id replaces its host, e.g. a block
     * entity that was recreated). {@code on}: boot it if it is off (the saved state).
     */
    public CloudComputer load(int id, ComputerHost host, boolean on) {
        if (!data.known(id)) throw new IllegalArgumentException("unknown computer id " + id);
        var computer = loaded.compute(id, (k, old) -> {
            if (old instanceof Computer c) {
                c.setHost(host);
                return c;
            }
            return new Computer(this, id, host);
        });
        if (on) computer.turnOn();
        return computer;
    }

    /** The loaded computer with this id, or null. */
    public @Nullable CloudComputer get(int id) {
        return loaded.get(id);
    }

    public List<CloudComputer> loaded() {
        return new ArrayList<>(loaded.values());
    }

    /** Stops and forgets a loaded computer; its id, label and files stay. */
    public void unload(int id) {
        var computer = loaded.remove(id);
        if (computer instanceof Computer c) c.close();
    }

    public void unloadAll() {
        for (int id : new ArrayList<>(loaded.keySet())) unload(id);
    }

    public @Nullable String label(int id) {
        return data.label(id);
    }

    public void setLabel(int id, @Nullable String label) {
        data.setLabel(id, cleanLabel(label));
    }

    /** The drive folder of a computer. */
    public Path folder(int id) {
        return root.resolve(Integer.toString(id));
    }

    /** A label without control characters, trimmed to {@link #MAX_LABEL}; null for empty. */
    public static @Nullable String cleanLabel(@Nullable String label) {
        if (label == null) return null;
        var out = new StringBuilder();
        label.codePoints().filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT).forEach(out::appendCodePoint);
        String s = out.toString().strip();
        if (s.isEmpty()) return null;
        return s.codePointCount(0, s.length()) > MAX_LABEL ? s.substring(0, s.offsetByCodePoints(0, MAX_LABEL)) : s;
    }
}
