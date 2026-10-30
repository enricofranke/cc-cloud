package ai.ezfox.cccloud.icons;

import ai.ezfox.cccloud.CCCloud;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code assets/<namespace>/…} files on the server, for the icon renderer and the icon fallback: from the
 * loaded mods' jars (NeoForge mods ship one jar with their assets, so a dedicated server has them) and, for the
 * {@code minecraft} namespace, from the vanilla client jar if the admin provided or allowed one ({@link #vanilla}).
 * Mods win over vanilla, like resource packs on a client. Namespaces need not match mod ids, so every jar's
 * {@code assets/} folder is indexed once (in the background, at server start). Thread-safe.
 */
public final class ModAssets {
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static volatile @Nullable Map<String, List<IModFile>> index;
    private static volatile @Nullable VanillaAssets vanilla;

    private ModAssets() {
    }

    /** The vanilla source for {@code minecraft} assets (null: none, the dedicated server default). */
    public static void setVanilla(@Nullable VanillaAssets assets) {
        var old = vanilla;
        vanilla = assets;
        if (old != null && old != assets) {
            try {
                old.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static @Nullable VanillaAssets vanilla() {
        return vanilla;
    }

    /** Builds the namespace index now (it is built on first use otherwise). */
    public static Map<String, List<IModFile>> index() {
        var current = index;
        if (current != null) return current;
        synchronized (ModAssets.class) {
            if (index == null) index = build();
            return index;
        }
    }

    private static Map<String, List<IModFile>> build() {
        long start = System.nanoTime();
        var map = new HashMap<String, List<IModFile>>();
        int files = 0;
        for (var info : ModList.get().getModFiles()) {
            var file = info.getFile();
            Set<String> namespaces = new LinkedHashSet<>();
            try {
                file.getContents().visitContent("assets/", (path, resource) -> {
                    int a = path.indexOf('/'), b = a < 0 ? -1 : path.indexOf('/', a + 1);
                    if (b > a + 1) namespaces.add(path.substring(a + 1, b));
                });
            } catch (RuntimeException e) {
                CCCloud.LOGGER.debug("[cccloud] Could not list the assets of {}", file.getFileName(), e);
                continue;
            }
            files++;
            for (var ns : namespaces) map.computeIfAbsent(ns, k -> new ArrayList<>(1)).add(file);
        }
        CCCloud.LOGGER.debug("[cccloud] Indexed the assets of {} mod files ({} namespaces) in {} ms", files, map.size(), (System.nanoTime() - start) / 1_000_000);
        return map;
    }

    /** A file like {@code assets/ae2/textures/item/foo.png}; null if no mod (or vanilla source) has it, or over 4 MB. */
    public static byte @Nullable [] read(String file) {
        if (!file.startsWith("assets/") || file.contains("..") || file.contains("\\") || file.contains("//")) return null;
        int slash = file.indexOf('/', "assets/".length());
        if (slash < 0) return null;
        String namespace = file.substring("assets/".length(), slash);
        var files = index().get(namespace);
        if (files != null) {
            for (var modFile : files) {
                try (InputStream in = modFile.getContents().openFile(file)) {
                    if (in == null) continue;
                    var bytes = in.readNBytes(MAX_FILE_BYTES + 1);
                    if (bytes.length <= MAX_FILE_BYTES) return bytes;
                } catch (IOException | RuntimeException e) {
                    // a directory or an unreadable entry: try the next jar
                }
            }
        }
        var v = vanilla;
        return v != null && namespace.equals("minecraft") ? v.read(file) : null;
    }
}
