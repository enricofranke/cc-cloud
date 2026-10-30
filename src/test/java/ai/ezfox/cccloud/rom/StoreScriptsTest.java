package ai.ezfox.cccloud.rom;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.function.LuaFunction;

/**
 * Runs every Script Store program ({@code scripts/<id>} of enricofranke/cc-cloud-scripts) on Cobalt against the fake
 * uplink of {@link RomLuaTest}, so each widget, page and group declaration and every value it sets goes through the
 * mod's real {@code WidgetFactory}. An option the mod does not accept (like {@code icon} on a group) fails here instead
 * of on a player's computer. Each program also gets one dashboard action per interactive widget and a few timer rounds;
 * handler errors fail too.
 */
class StoreScriptsTest {
    /** Set by build.gradle when the registry is checked out (-PstoreScripts or ../cc-cloud-scripts). */
    private static final String SCRIPTS = System.getProperty("cccloud.storeScripts");

    @TestFactory
    Stream<DynamicTest> everyStoreScriptBuildsAValidDashboard() throws Exception {
        assumeTrue(SCRIPTS != null, "no Script Store registry: check out enricofranke/cc-cloud-scripts next to this repository or pass -PstoreScripts=<its scripts folder>");
        var scripts = Path.of(SCRIPTS);
        assertTrue(Files.isDirectory(scripts), "store scripts not found at " + scripts.toAbsolutePath());
        var tests = new ArrayList<DynamicTest>();
        try (var dirs = Files.list(scripts)) {
            for (var dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                tests.add(DynamicTest.dynamicTest(dir.getFileName().toString(), () -> runScript(dir)));
            }
        }
        assertFalse(tests.isEmpty(), "no store scripts");
        return tests.stream();
    }

    private static void runScript(Path dir) throws Exception {
        var manifest = JsonParser.parseString(Files.readString(dir.resolve("manifest.json"))).getAsJsonObject();
        String entry = manifest.get("entry").getAsString();
        var files = new ArrayList<String>();
        for (var f : manifest.getAsJsonArray("files")) files.add(f.getAsString());

        var fake = new RomLuaTest.FakeUplink();
        var state = RomLuaTest.prepare(fake);
        RomLuaTest.runScenario(state, "store_prelude");
        var globals = state.globals();
        // Other Lua files of a multi-file script are require()-able by their name, like next to the program in CC.
        var modules = (LuaTable) globals.rawget("__modules");
        for (String file : files) {
            if (file.equals(entry) || !file.endsWith(".lua")) continue;
            modules.rawset(file.substring(0, file.length() - 4).replace('/', '.'), compile(state, dir.resolve(file)));
        }
        globals.rawset("__store_program", compile(state, dir.resolve(entry)));
        RomLuaTest.runScenario(state, "store_test");
        assertFalse(fake.widgets.isEmpty(), dir + " declared no widgets");
    }

    private static LuaFunction compile(LuaState state, Path file) throws Exception {
        return RomLuaTest.compile(state, new ByteArrayInputStream(Files.readAllBytes(file)), "@" + file.getFileName(), state.globals());
    }
}
