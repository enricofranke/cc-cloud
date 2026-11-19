package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.game.CodeService;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
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
    private final Environment env;
    private final Map<Integer, CloudComputer> loaded = new ConcurrentHashMap<>();

    /**
     * What computers run on: the engine, the shared script threads (null while none run, e.g. Lua is unavailable), the
     * limits and the rom.
     */
    public interface Environment {
        ScriptEngine engine();

        @Nullable ScriptScheduler scheduler();

        /** Why programs cannot run on this server, or null. */
        default @Nullable String problem() {
            return null;
        }

        ComputerLimits limits();

        default ComputerFs.Rom rom() {
            return ClasspathRom.standard();
        }

        default String boot() {
            return ClasspathRom.standard().boot();
        }

        /** The engine of WebAssembly programs started from the shell ({@code foo.wasm}), or null if there is none. */
        default @Nullable ScriptEngine wasm() {
            return null;
        }
    }

    /** The game's environment: Lua and the threads of Cloud Scripts, limits from the config. */
    public static final Environment GAME = new Environment() {
        @Override
        public ScriptEngine engine() {
            return CodeService.lua();
        }

        @Override
        public @Nullable ScriptScheduler scheduler() {
            return CodeService.scheduler();
        }

        @Override
        public @Nullable String problem() {
            return CodeService.vmProblem();
        }

        @Override
        public ScriptEngine wasm() {
            return CodeService.wasm();
        }

        @Override
        public ComputerLimits limits() {
            return ComputerLimits.DEFAULT;
        }
    };

    /** A registry over this data with drive folders under {@code root} (tests; the game uses {@link #get}). */
    public ComputerRegistry(ComputerData data, Path root, Environment env) {
        this.data = data;
        this.root = root;
        this.env = env;
    }

    public static ComputerRegistry get(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, s -> new ComputerRegistry(s.getDataStorage().computeIfAbsent(ComputerData.TYPE),
            s.getWorldPath(LevelResource.ROOT).resolve(FOLDER), GAME));
    }

    Environment env() {
        return env;
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
