package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The computer rom shipped in the mod jar under {@code cccloud_rom/}: {@code index.txt} lists every file (one path per
 * line), which are loaded once into memory. {@code boot.lua} at the top is the boot code and not part of the visible
 * rom folder.
 */
public final class ClasspathRom implements ComputerFs.Rom {
    public static final String BASE = "cccloud_rom/";
    public static final String BOOT = "boot.lua";
    private static volatile @Nullable ClasspathRom standard;

    private final Map<String, byte[]> files = new HashMap<>();
    private final Map<String, List<String>> dirs = new HashMap<>();
    private final String boot;

    private ClasspathRom(ClassLoader loader) throws IOException {
        byte[] index = load(loader, "index.txt");
        if (index == null) throw new IOException("cccloud_rom/index.txt missing");
        var children = new HashMap<String, Set<String>>();
        children.put("", new HashSet<>());
        for (String line : new String(index, StandardCharsets.UTF_8).split("\n")) {
            String path = line.strip();
            if (path.isEmpty() || path.startsWith("#")) continue;
            if (!ComputerFs.normalize(path).equals(path)) throw new IOException("bad rom path " + path);
            byte[] data = load(loader, "rom/" + path);
            if (data == null) throw new IOException("rom file missing: " + path);
            files.put(path, data);
            String p = path;
            while (true) {
                int slash = p.lastIndexOf('/');
                String parent = slash < 0 ? "" : p.substring(0, slash);
                children.computeIfAbsent(parent, k -> new HashSet<>()).add(p.substring(slash + 1));
                if (parent.isEmpty()) break;
                p = parent;
            }
        }
        for (var e : children.entrySet()) {
            var names = new ArrayList<>(e.getValue());
            Collections.sort(names);
            dirs.put(e.getKey(), List.copyOf(names));
        }
        byte[] b = load(loader, BOOT);
        if (b == null) throw new IOException("cccloud_rom/boot.lua missing");
        boot = new String(b, StandardCharsets.ISO_8859_1);
    }

    /** The mod's rom, loaded on first use. */
    public static ClasspathRom standard() {
        var rom = standard;
        if (rom == null) {
            synchronized (ClasspathRom.class) {
                rom = standard;
                if (rom == null) {
                    try {
                        rom = new ClasspathRom(ClasspathRom.class.getClassLoader());
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot load the computer rom: " + e.getMessage(), e);
                    }
                    standard = rom;
                }
            }
        }
        return rom;
    }

    /** The boot code (bytes as ISO-8859-1 characters). */
    public String boot() {
        return boot;
    }

    private static byte @Nullable [] load(ClassLoader loader, String path) throws IOException {
        try (InputStream in = loader.getResourceAsStream(BASE + path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    @Override
    public boolean exists(String path) {
        return files.containsKey(path) || dirs.containsKey(path);
    }

    @Override
    public boolean isDir(String path) {
        return dirs.containsKey(path);
    }

    @Override
    public List<String> list(String dir) {
        var names = dirs.get(dir);
        if (names == null) throw new ApiError("/rom/" + dir + ": Not a directory");
        return names;
    }

    @Override
    public byte[] read(String path) {
        byte[] data = files.get(path);
        if (data == null) throw new ApiError("/rom/" + path + ": No such file");
        return data.clone();
    }

    /** Every file path of the rom (tests). */
    public Set<String> files() {
        return Collections.unmodifiableSet(files.keySet());
    }
}
