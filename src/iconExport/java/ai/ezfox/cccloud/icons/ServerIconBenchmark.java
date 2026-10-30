package ai.ezfox.cccloud.icons;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;

/**
 * Dev tool behind {@code ./gradlew serverIconBenchmark} (docs/BUILD.md): runs {@link ServerIconRenderer} over every
 * item definition in a folder of mod jars (a modpack's {@code mods/}, jar-in-jar mods included) the way a dedicated
 * server would, on one thread, and reports how many icons are flat / cube / skipped, the time and the store size.
 * Reads the jars only; the store goes to {@code --out} (default: a temp folder), never into the repository.
 * <pre>
 *   ServerIconBenchmark --mods <dir> [--vanilla <client jar>] [--no-vanilla-textures] [--out <dir>] [--compare <client icon store>]
 * </pre>
 * {@code --vanilla} adds the vanilla items to the item list (a real server registers them too) and, unless
 * {@code --no-vanilla-textures} is given, lets the renderer read vanilla models and textures (like
 * {@code downloadVanillaAssets = true}). {@code --compare} reports how close the server renders are to the client
 * renders of an existing icon store (mean absolute difference per channel, 0–255).
 */
public final class ServerIconBenchmark {
    private ServerIconBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        Path mods = null, vanillaJar = null, out = null, compare = null;
        boolean vanillaTextures = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mods" -> mods = Path.of(args[++i]);
                case "--vanilla" -> vanillaJar = Path.of(args[++i]);
                case "--no-vanilla-textures" -> vanillaTextures = false;
                case "--out" -> out = Path.of(args[++i]);
                case "--compare" -> compare = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (mods == null) throw new IllegalArgumentException("usage: --mods <dir> [--vanilla <jar>] [--no-vanilla-textures] [--out <dir>] [--compare <store>]");
        if (out == null) out = Files.createTempDirectory("cccloud-icon-bench");

        var temp = Files.createTempDirectory("cccloud-icon-bench-jars");
        try (var sources = new Sources()) {
            var jars = new ArrayList<Path>();
            try (var list = Files.list(mods)) {
                list.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().forEach(jars::add);
            }
            for (var jar : jars) sources.addJar(jar, temp, 0);
            int modJars = sources.zips.size();
            ZipFile vanilla = vanillaJar != null ? new ZipFile(vanillaJar.toFile()) : null;
            System.out.printf("[serverIconBenchmark] %d jars (%d with jar-in-jar), %d asset files%n", jars.size(), modJars, sources.index.size());

            // Item list: every item definition of the mods, plus vanilla's when the client jar is given.
            var ids = new TreeSet<String>();
            for (var path : sources.index.keySet()) addItem(ids, path);
            if (vanilla != null) {
                for (var e = vanilla.entries(); e.hasMoreElements(); ) addItem(ids, e.nextElement().getName());
            }
            VanillaAssets vanillaAssets = vanilla != null && vanillaTextures ? VanillaAssets.open(vanillaJar) : null;
            java.util.function.Function<String, byte @Nullable []> read = path -> {
                byte[] b = sources.read(path);
                if (b == null && vanillaAssets != null && path.startsWith("assets/minecraft/")) b = vanillaAssets.read(path);
                return b;
            };

            var counts = new TreeMap<String, Integer>();
            var reasons = new HashMap<String, Integer>();
            var renderer = new ServerIconRenderer(read);
            var deflater = new java.util.zip.Deflater(6); // as ServerIconPass does
            Files.createDirectories(out);
            long pngBytes = 0;
            double diffSum = 0;
            int diffCount = 0;
            var clientStore = compare != null ? new IconStore(compare) : null;
            var threads = java.lang.management.ManagementFactory.getThreadMXBean();
            long start = System.nanoTime(), cpuStart = threads.getCurrentThreadCpuTime();
            long renderNanos = 0, encodeNanos = 0;
            try (var store = new IconStore(out)) {
                store.clear();
                for (var id : ids) {
                    int colon = id.indexOf(':');
                    long t0 = System.nanoTime();
                    var result = renderer.render(id.substring(0, colon), id.substring(colon + 1));
                    renderNanos += System.nanoTime() - t0;
                    counts.merge(result.kind().name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
                    if (result.argb() == null) {
                        reasons.merge(reasonKey(result.detail()), 1, Integer::sum);
                        continue;
                    }
                    long t1 = System.nanoTime();
                    byte[] png = result.png(deflater);
                    encodeNanos += System.nanoTime() - t1;
                    pngBytes += png.length;
                    if (!IconStore.isValidId(id)) continue;
                    store.putServer(id, "bench@1", png);
                    if (clientStore != null && clientStore.source(id) == IconStore.Source.CLIENT) {
                        var client = PngCodec.decode(clientStore.get(id), 32, 32);
                        if (client != null) {
                            diffSum += meanDiff(result.argb(), client);
                            diffCount++;
                        }
                    }
                }
                long renderMs = (System.nanoTime() - start) / 1_000_000, cpuMs = (threads.getCurrentThreadCpuTime() - cpuStart) / 1_000_000;
                store.flush();
                long storeBytes = 0;
                try (var files = Files.list(out)) {
                    for (var f : files.toList()) storeBytes += Files.size(f);
                }
                System.out.printf("[serverIconBenchmark] %d items: %s in %d ms (%.2f ms per item, one thread%s)%n", ids.size(), counts, renderMs,
                    renderMs / (double) Math.max(1, ids.size()), vanilla == null ? ", no vanilla items" : vanillaTextures ? ", vanilla textures" : ", no vanilla textures");
                System.out.printf("[serverIconBenchmark] CPU time %d ms (models, textures and drawing %d ms wall, PNG encoding %d ms wall)%n", cpuMs,
                    renderNanos / 1_000_000, encodeNanos / 1_000_000);
                System.out.printf("[serverIconBenchmark] PNGs %.1f MB, store on disk %.1f MB (%s)%n", pngBytes / 1048576.0, storeBytes / 1048576.0, out);
                if (diffCount > 0) System.out.printf("[serverIconBenchmark] vs client renders: %d icons, mean abs difference %.1f per channel%n", diffCount, diffSum / diffCount);
                System.out.println("[serverIconBenchmark] most common reasons for skipping:");
                reasons.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(25)
                    .forEach(e -> System.out.printf("  %6d  %s%n", e.getValue(), e.getKey()));
            } finally {
                deflater.end();
                if (clientStore != null) clientStore.close();
                if (vanillaAssets != null) vanillaAssets.close();
                if (vanilla != null) vanilla.close();
            }
        } finally {
            try (var files = Files.walk(temp)) {
                for (var f : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
            }
        }
    }

    private static void addItem(java.util.Set<String> ids, String path) {
        // assets/<ns>/items/<path>.json
        if (!path.startsWith("assets/") || !path.endsWith(".json")) return;
        var parts = path.split("/", 4);
        if (parts.length < 4 || !parts[2].equals("items")) return;
        ids.add(parts[1] + ":" + parts[3].substring(0, parts[3].length() - ".json".length()));
    }

    /** Groups reasons: "missing texture ae2:item/x" → "missing texture (ae2)", models keep their id (vanilla parents). */
    private static String reasonKey(String detail) {
        if (detail.startsWith("missing texture ")) return "missing texture (" + IconFallback.split(detail.substring(16))[0] + ")";
        if (detail.startsWith("missing model ") && !detail.startsWith("missing model minecraft:")) return "missing model (mod)";
        if (detail.startsWith("not a plain cube")) return "not a plain cube";
        if (detail.startsWith("error:")) return "error";
        return detail;
    }

    private static double meanDiff(int[] a, int[] b) {
        long sum = 0;
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            if ((a[i] >>> 24) == 0 && (b[i] >>> 24) == 0) continue;
            for (int s = 0; s < 32; s += 8) sum += Math.abs(((a[i] >>> s) & 0xFF) - ((b[i] >>> s) & 0xFF));
            n += 4;
        }
        return n == 0 ? 0 : sum / (double) n;
    }

    /** Every {@code assets/} file of the jars, first jar wins; jar-in-jar mods are extracted to a temp folder. */
    private static final class Sources implements AutoCloseable {
        final Map<String, ZipFile> index = new HashMap<>();
        final List<ZipFile> zips = new ArrayList<>();

        void addJar(Path jar, Path temp, int depth) throws IOException {
            ZipFile zip;
            try {
                zip = new ZipFile(jar.toFile());
            } catch (IOException e) {
                return;
            }
            zips.add(zip);
            var nested = new ArrayList<ZipEntry>();
            for (var e = zip.entries(); e.hasMoreElements(); ) {
                var entry = e.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (name.startsWith("assets/")) index.putIfAbsent(name, zip);
                else if (depth < 2 && name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) nested.add(entry);
            }
            for (var entry : nested) {
                var file = temp.resolve(zips.size() + "-" + Path.of(entry.getName()).getFileName());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, file);
                }
                addJar(file, temp, depth + 1);
            }
        }

        byte @Nullable [] read(String path) {
            var zip = index.get(path);
            if (zip == null) return null;
            try (var in = zip.getInputStream(zip.getEntry(path))) {
                return in.readNBytes(ModAssets.MAX_FILE_BYTES);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void close() throws IOException {
            for (var z : zips) z.close();
        }
    }
}
