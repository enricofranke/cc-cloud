package ai.ezfox.cccloud.icons;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Dev tool behind {@code ./gradlew exportIcons} (docs/BUILD.md "Item icons for the website and the mock"): writes a
 * 32×32 inventory-style PNG for every item id in a list to {@code <out>/<namespace>/<path>.png}, plus
 * {@code <out>/index.json} (id → where the icon came from). The website build and the dashboard mock pick them up.
 * <p>
 * Sources, best first:
 * <ol>
 *   <li>{@code client}: a client-rendered icon store ({@code <world>/cccloud/icons}, e.g. from
 *       {@code runClient -PiconBenchmark}); the game's own renderer, so tints, special models and all. Our own items
 *       only when rendered by the current mod version.</li>
 *   <li>{@code model}: {@link ModelIcon} draws the item from its model JSON (layers, or the block model with the gui
 *       transform and lighting).</li>
 *   <li>{@code flat}: the server fallback's texture ({@link IconFallback}), scaled to 32×32.</li>
 * </ol>
 * Models and textures are read from every jar and directory on this program's classpath (the vanilla client
 * resources, the mod jars of the dev classpath and our own resources). The output is not meant to be committed:
 * vanilla and third-party textures belong to Mojang and the mod authors.
 * <pre>
 *   IconExport --ids ids.txt --out build/item-icons --mod-version 0.9.0 [--store dir]… [--stores-under run/saves]
 * </pre>
 */
public final class IconExport {
    private IconExport() {
    }

    public static void main(String[] args) throws IOException {
        Path ids = null, out = null;
        String modVersion = "";
        var stores = new ArrayList<Path>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ids" -> ids = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--mod-version" -> modVersion = args[++i];
                case "--store" -> {
                    for (var s : args[++i].split(File.pathSeparator)) if (!s.isBlank()) stores.add(Path.of(s.trim()));
                }
                case "--stores-under" -> {
                    // every <world>/cccloud/icons below a saves directory
                    var saves = Path.of(args[++i]);
                    if (Files.isDirectory(saves)) {
                        try (var worlds = Files.list(saves)) {
                            worlds.map(w -> w.resolve("cccloud/icons")).filter(d -> Files.isRegularFile(d.resolve("manifest.json"))).sorted().forEach(stores::add);
                        }
                    }
                }
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (ids == null || out == null) throw new IllegalArgumentException("usage: --ids <file> --out <dir> [--mod-version v] [--store dir] [--stores-under dir]");

        var wanted = Files.readAllLines(ids, StandardCharsets.UTF_8).stream().map(String::trim)
            .filter(l -> !l.isEmpty() && !l.startsWith("#")).distinct().sorted().toList();
        long start = System.nanoTime();
        try (var assets = Assets.fromClasspath(System.getProperty("java.class.path"))) {
            var openStores = new ArrayList<IconStore>();
            for (var dir : stores) openStores.add(new IconStore(dir));
            clean(out);
            var model = new ModelIcon(assets::read);
            var index = new TreeMap<String, String>();
            var missing = new ArrayList<String>();
            var counts = new TreeMap<String, Integer>();
            for (var id : wanted) {
                if (!IconStore.isValidId(id)) {
                    System.err.println("[exportIcons] skipping invalid id " + id);
                    continue;
                }
                var split = IconFallback.split(id);
                String source = null;
                byte[] png = null;
                for (var store : openStores) {
                    if (split[0].equals("cccloud") && !store.isCurrent(id, "cccloud@" + modVersion)) continue; // our art changes
                    png = store.get(id);
                    if (png != null && PngCodec.isValid(png, PngCodec.ICON_SIZE, PngCodec.ICON_SIZE)) {
                        source = "client";
                        break;
                    }
                    png = null;
                }
                if (png == null) {
                    int[] px = model.render(split[0], split[1]);
                    if (px != null) {
                        png = PngCodec.encode(ModelIcon.SIZE, ModelIcon.SIZE, px);
                        source = "model";
                    }
                }
                if (png == null && !model.isSpecial(split[0], split[1])) {
                    png = flat(IconFallback.resolve(split[0], split[1], assets::read));
                    if (png != null) source = "flat";
                }
                if (png == null) {
                    missing.add(id);
                    continue;
                }
                var file = out.resolve(split[0]).resolve(split[1] + ".png");
                Files.createDirectories(file.getParent());
                Files.write(file, png);
                index.put(id, source);
                counts.merge(source, 1, Integer::sum);
            }
            for (var store : openStores) store.close();

            var json = new JsonObject();
            json.addProperty("generated", Instant.now().toString());
            json.addProperty("note", "Generated by ./gradlew exportIcons. Contains Mojang and third-party mod textures: do not commit or redistribute.");
            var icons = new JsonObject();
            index.forEach(icons::addProperty);
            json.add("icons", icons);
            var miss = new JsonArray();
            missing.forEach(miss::add);
            json.add("missing", miss);
            Files.writeString(out.resolve("index.json"), new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
            System.out.printf("[exportIcons] %d of %d icons in %s (%s), %d missing%s, %d ms%n", index.size(), wanted.size(), out, counts,
                missing.size(), missing.isEmpty() ? "" : ": " + String.join(", ", missing), (System.nanoTime() - start) / 1_000_000);
        }
    }

    /** Removes the PNGs and the index of an earlier run (nothing else). */
    private static void clean(Path out) throws IOException {
        if (!Files.isDirectory(out)) {
            Files.createDirectories(out);
            return;
        }
        try (var files = Files.walk(out)) {
            for (var f : files.sorted(Comparator.reverseOrder()).toList()) {
                String name = f.getFileName().toString();
                if (Files.isRegularFile(f) && (name.endsWith(".png") || name.equals("index.json"))) Files.delete(f);
                else if (Files.isDirectory(f) && !f.equals(out)) {
                    try (var rest = Files.list(f)) {
                        if (rest.findAny().isEmpty()) Files.delete(f);
                    }
                }
            }
        }
    }

    /** A texture PNG (any square size) scaled to 32×32 with nearest neighbour. */
    private static byte @Nullable [] flat(byte @Nullable [] png) {
        if (png == null) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) return null;
            int w = image.getWidth(), h = Math.min(image.getHeight(), w);
            var px = new int[ModelIcon.SIZE * ModelIcon.SIZE];
            for (int y = 0; y < ModelIcon.SIZE; y++) {
                for (int x = 0; x < ModelIcon.SIZE; x++) px[y * ModelIcon.SIZE + x] = image.getRGB(x * w / ModelIcon.SIZE, y * h / ModelIcon.SIZE);
            }
            return PngCodec.encode(ModelIcon.SIZE, ModelIcon.SIZE, px);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Every {@code assets/…} file of the classpath: directories first (our resources), then the Minecraft jar, then
     * the other jars in classpath order; the first one that has a path wins.
     */
    static final class Assets implements AutoCloseable {
        private final Map<String, Object> index = new HashMap<>();
        private final List<ZipFile> zips = new ArrayList<>();

        static Assets fromClasspath(String classpath) throws IOException {
            var dirs = new ArrayList<Path>();
            var minecraft = new ArrayList<Path>();
            var jars = new ArrayList<Path>();
            for (var entry : classpath.split(File.pathSeparator)) {
                if (entry.isBlank()) continue;
                var p = Path.of(entry);
                if (Files.isDirectory(p)) dirs.add(p);
                else if (Files.isRegularFile(p) && entry.endsWith(".jar")) {
                    String name = p.getFileName().toString();
                    (name.startsWith("minecraft") || name.contains("client") ? minecraft : jars).add(p);
                }
            }
            var assets = new Assets();
            for (var d : dirs) assets.addDir(d);
            for (var j : minecraft) assets.addJar(j);
            for (var j : jars) assets.addJar(j);
            System.out.printf("[exportIcons] %d asset files from %d directories and %d jars%n", assets.index.size(), dirs.size(), minecraft.size() + jars.size());
            return assets;
        }

        private void addDir(Path dir) throws IOException {
            var root = dir.resolve("assets");
            if (!Files.isDirectory(root)) return;
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile).forEach(f -> index.putIfAbsent(dir.relativize(f).toString().replace(File.separatorChar, '/'), f));
            }
        }

        private void addJar(Path jar) throws IOException {
            ZipFile zip;
            try {
                zip = new ZipFile(jar.toFile());
            } catch (IOException e) {
                return;
            }
            boolean used = false;
            for (var entries = zip.entries(); entries.hasMoreElements(); ) {
                var e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("assets/")) continue;
                if (index.putIfAbsent(e.getName(), new Object[]{zip, e}) == null) used = true;
            }
            if (used) zips.add(zip);
            else zip.close();
        }

        byte @Nullable [] read(String path) {
            var hit = index.get(path);
            try {
                if (hit instanceof Path p) return Files.readAllBytes(p);
                if (hit instanceof Object[] z) {
                    try (InputStream in = ((ZipFile) z[0]).getInputStream((java.util.zip.ZipEntry) z[1])) {
                        return in.readNBytes(4 * 1024 * 1024);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            for (var z : zips) z.close();
        }
    }
}
