package ai.ezfox.cccloud.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CC: Tweaked is optional (docs/SPEC.md "CC: Tweaked is optional"): no source outside {@code compat/cct} uses a CC:
 * Tweaked type, so nothing outside it can make the JVM load one. Package names in string literals (crash report
 * filters) do not count; {@code compat/Cct} reaches {@code compat/cct} only behind its ModList check.
 */
class CctIsolationTest {
    private static final Path MAIN = Path.of(System.getProperty("cccloud.mainSrc", "src/main/java")).resolve("ai/ezfox/cccloud");
    private static final Pattern CC_TYPE = Pattern.compile("\\b(dan200\\.computercraft|cc\\.tweaked)\\.");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    @Test
    void noCcTypesOutsideCompatCct() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = MAIN.relativize(file).toString().replace('\\', '/');
                if (rel.startsWith("compat/cct/")) continue;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String code = STRING.matcher(lines.get(i)).replaceAll("\"\"");
                    if (CC_TYPE.matcher(code).find()) found.add(rel + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }
        assertTrue(found.isEmpty(), "CC: Tweaked types outside compat/cct:\n" + String.join("\n", found));
    }

    @Test
    void compatCctIsOnlyReachedThroughTheGuardedEntryPoints() throws IOException {
        // compat/Cct loads CctCompat behind ModList; GameTest helpers call compat/cct only behind Cct.present().
        var allowed = List.of("compat/Cct.java", "gametest/BridgeTestSupport.java", "gametest/ClaimGuardTests.java");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = MAIN.relativize(file).toString().replace('\\', '/');
                if (rel.startsWith("compat/cct/") || allowed.contains(rel)) continue;
                if (Files.readString(file).contains("ai.ezfox.cccloud.compat.cct.")) found.add(rel);
            }
        }
        assertTrue(found.isEmpty(), "compat/cct used outside its guarded entry points: " + found);
    }
}
