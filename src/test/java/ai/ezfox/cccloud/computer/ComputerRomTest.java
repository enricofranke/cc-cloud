package ai.ezfox.cccloud.computer;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.lua.LuaEngine;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The rom: index.txt lists exactly the files that are there, every Lua file compiles, every program has help. */
class ComputerRomTest {
    @Test
    void indexListsEveryFile() throws Exception {
        var index = ClasspathRom.class.getResource("/cccloud_rom/index.txt");
        assertNotNull(index);
        Path ROM = Path.of(index.toURI()).resolveSibling("rom");
        var onDisk = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(ROM)) {
            files.filter(Files::isRegularFile).forEach(p -> onDisk.add(ROM.relativize(p).toString().replace('\\', '/')));
        }
        assertEquals(onDisk, new TreeSet<>(ClasspathRom.standard().files()));
    }

    @Test
    void everyLuaFileCompiles() {
        var engine = new LuaEngine(ZoneId::systemDefault);
        var rom = ClasspathRom.standard();
        for (String file : rom.files()) {
            if (!file.endsWith(".lua")) continue;
            var problems = engine.check(file, new String(rom.read(file), StandardCharsets.ISO_8859_1));
            assertTrue(problems.isEmpty(), file + ": " + problems);
        }
        assertTrue(engine.check("boot", rom.boot()).isEmpty(), "boot.lua");
    }

    @Test
    void everyProgramHasHelp() {
        var rom = ClasspathRom.standard();
        for (String name : rom.list("programs")) {
            String program = name.replace(".lua", "");
            assertTrue(rom.exists("help/" + program + ".txt"), "help for " + program);
        }
    }
}
