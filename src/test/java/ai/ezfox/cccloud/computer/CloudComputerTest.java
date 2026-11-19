package ai.ezfox.cccloud.computer;

import static ai.ezfox.cccloud.computer.TestComputers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Whole computers: boot, shell, programs, events, timers, files, redstone, limits and power. */
class CloudComputerTest {
    @TempDir
    Path tmp;
    private TestComputers computers;

    @BeforeEach
    void setUp() {
        computers = new TestComputers(tmp);
    }

    @AfterEach
    void tearDown() {
        computers.close();
    }

    @Test
    void bootsIntoTheShell() {
        var c = computers.create();
        c.turnOn();
        assertTrue(c.isOn());
        until(c, 15000, "the prompt", () -> shows(c, "CloudOS") && shows(c, "/>"));
        assertNull(c.lastError());
    }

    @Test
    void startupRunsAndProgramsSeeTheApis() {
        var c = computers.create();
        runStartup(c, """
            local f = fs.open("out", "w")
            f.writeLine(os.getComputerID())
            f.writeLine(textutils.serialize({1, 2, a = "x"}, {compact = true}))
            f.writeLine(textutils.serializeJSON({n = 1, list = {true, "s"}}))
            f.writeLine(tostring(colors.red) .. " " .. colors.toBlit(colors.red) .. " " .. tostring(keys.enter))
            local w, h = term.getSize()
            f.writeLine(w .. "x" .. h)
            f.writeLine(textutils.unserialize("{ 5, b = true }")[1])
            f.writeLine(tostring(textutils.unserializeJSON('{"a":[1,2,{"b":null}]}').a[2]))
            f.writeLine(tostring(textutils.unserialize("os.shutdown()")))
            f.close()
            fs.open("done", "w").close()
            """, 15000);
        String out = read(c, "out");
        String[] lines = out.split("\n");
        assertEquals(Integer.toString(c.id()), lines[0]);
        assertEquals("{1,2,a=\"x\"}", lines[1]);
        assertEquals("{\"list\":[true,\"s\"],\"n\":1}", lines[2]);
        assertEquals("16384 e 257", lines[3]);
        assertEquals("51x19", lines[4]);
        assertEquals("5", lines[5]);
        assertEquals("2", lines[6]);
        assertEquals("nil", lines[7], "unserialize has no access to the APIs");
    }

    @Test
    void shellCommandsWork() {
        var c = computers.create();
        runStartup(c, """
            shell.run("mkdir", "box")
            shell.run("cd box")
            local f = fs.open(shell.resolve("a.txt"), "w") f.write("hello") f.close()
            shell.run("cp a.txt b.txt")
            shell.run("mv b.txt c.txt")
            shell.run("rm a.txt")
            shell.run("label set Kitchen")
            shell.run("cd ..")
            local list = table.concat(fs.list("box"), ",")
            local ff = fs.open("result", "w") ff.write(list .. "|" .. tostring(os.getComputerLabel()) .. "|" .. shell.dir()) ff.close()
            fs.open("done", "w").close()
            """, 15000);
        assertEquals("c.txt|Kitchen|", read(c, "result"));
        assertEquals("Kitchen", c.label());
        assertEquals("hello", read(c, "box/c.txt"));
    }

    @Test
    void timersAndEvents() {
        var c = computers.create();
        runStartup(c, """
            local start = os.clock()
            sleep(0.2)
            local slept = os.clock() - start
            local t = os.startTimer(0.1)
            os.cancelTimer(t)
            os.queueEvent("ping", 42, "x")
            local e, a, b = os.pullEvent("ping")
            local t2 = os.startTimer(0.05)
            local _, id = os.pullEvent("timer")
            local f = fs.open("out", "w")
            f.write(("%s %s %s %s %s"):format(e, a, b, tostring(id == t2), tostring(slept >= 0.15)))
            f.close()
            fs.open("done", "w").close()
            """, 15000);
        assertEquals("ping 42 x true true", read(c, "out"));
    }

    @Test
    void inputBecomesEventsAndReadWorks() {
        var c = computers.create();
        file(c, "startup.lua", """
            write("name?")
            local name = read()
            local f = fs.open("out", "w") f.write(name) f.close()
            fs.open("done", "w").close()
            """);
        c.turnOn();
        until(c, 15000, "the question", () -> shows(c, "name?"));
        for (char ch : "Bob".toCharArray()) assertTrue(c.input(InputEvent.character(ch), "player"));
        c.input(InputEvent.key(259, false), "player"); // backspace
        c.input(InputEvent.paste("x\ny"), "player");
        c.input(InputEvent.key(257, false), "player"); // enter
        until(c, 15000, "the answer", () -> c.fs().exists("done"));
        assertEquals("Box", read(c, "out"));
    }

    @Test
    void terminateStopsAProgramButNotTheShell() {
        var c = computers.create();
        file(c, "startup.lua", "while true do os.pullEvent() end");
        c.turnOn();
        until(c, 15000, "the program", () -> shows(c, "CloudOS"));
        c.input(InputEvent.control("terminate"), "p"); // queued as an event: the program's pullEvent raises "Terminated"
        until(c, 15000, "the prompt after terminate", () -> shows(c, "Terminated") && shows(c, "/>"));
        assertTrue(c.isOn());
    }

    @Test
    void endlessLoopIsStoppedAndTheComputerTurnsOff() {
        computers.limits = new ComputerLimits(50, 150, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        var c = computers.create();
        file(c, "startup.lua", "while true do end");
        c.turnOn();
        var run = c.runtime();
        assertNotNull(run);
        until(c, 10_000, "the stop", () -> !c.isOn());
        assertNotNull(c.lastError());
        assertTrue(c.lastError().contains("too long without yielding"), c.lastError());
        assertTrue(shows(c, "too long without yielding"), screen(c));
        // 150 ms at 50 ms per second: the share pauses it in between. Counted, not timed: on a loaded machine a late
        // check can see 50 ms and more at once, so fewer pauses and less wall time are fine.
        assertTrue(run.sharePauses() >= 1, "the per-second share never paused it");
    }

    @Test
    void pcallCannotKeepAnEndlessLoopAlive() {
        computers.limits = new ComputerLimits(50, 150, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        var c = computers.create();
        file(c, "startup.lua", "while true do pcall(function() while true do end end) end");
        c.turnOn();
        until(c, 10_000, "the stop", () -> !c.isOn());
        assertTrue(c.lastError().contains("too long without yielding"), c.lastError());
    }

    @Test
    void memoryHogIsStopped() {
        computers.limits = new ComputerLimits(200, 5000, 4L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        var c = computers.create();
        file(c, "startup.lua", """
            local t = {}
            local i = 0
            while true do
              i = i + 1
              t[i] = ("x"):rep(1000) .. i
              if i % 100 == 0 then os.queueEvent("y") os.pullEvent("y") end
            end
            """);
        c.turnOn();
        until(c, 20_000, "the stop", () -> !c.isOn());
        assertTrue(c.lastError().contains("out of memory"), c.lastError());
    }

    @Test
    void eventQueueAndTimersAreBounded() {
        computers.limits = new ComputerLimits(50, 1000, 16L << 20, 1L << 30, 8, 4, 1L << 20, 32, 20);
        var c = computers.create();
        runStartup(c, """
            local timer = os.startTimer(0.5) -- fires after the queue is drained again
            for i = 1, 20 do os.queueEvent("e", i) end
            local n = 0
            while true do
              local e, id = os.pullEvent()
              if e == "timer" and id == timer then break end
              n = n + 1
            end
            local ok, err = pcall(function() for i = 1, 10 do os.startTimer(10) end end)
            local f = fs.open("out", "w") f.write(n .. " " .. tostring(ok) .. " " .. tostring(err)) f.close()
            fs.open("done", "w").close()
            """, 15000);
        String out = read(c, "out");
        assertTrue(out.startsWith("8 false"), out);
        assertTrue(out.contains("too many timers"), out);
    }

    @Test
    void redstoneReadsInputsSetsOutputsAndIsRateLimited() {
        var io = new RedstoneIo.Memory();
        io.setInput(ComputerSide.LEFT, 7);
        var c = computers.create(io);
        runStartup(c, """
            local f = fs.open("out", "w")
            f.write(redstone.getAnalogInput("left") .. " " .. tostring(rs.getInput("left")) .. " " .. table.concat(redstone.getSides(), ","))
            redstone.setOutput("top", true)
            redstone.setAnalogOutput("back", 3)
            local ok, err = pcall(function() for i = 1, 100 do redstone.setAnalogOutput("front", i % 2) end end)
            f.write(" " .. tostring(ok) .. " " .. tostring(err))
            local bad = pcall(redstone.getInput, "up")
            f.write(" " .. tostring(bad))
            f.close()
            fs.open("done", "w").close()
            """, 15000);
        String out = read(c, "out");
        assertTrue(out.startsWith("7 true bottom,top,back,front,right,left false"), out);
        assertTrue(out.contains("too many redstone changes"), out);
        assertTrue(out.endsWith(" false"), out);
        assertEquals(15, io.output(ComputerSide.TOP));
        assertEquals(3, io.output(ComputerSide.BACK));
        c.shutdown();
        assertEquals(0, io.output(ComputerSide.TOP), "outputs go off with the computer");
    }

    @Test
    void redstoneChangesWakeTheProgram() {
        var io = new RedstoneIo.Memory();
        var c = computers.create(io);
        file(c, "startup.lua", """
            os.pullEvent("redstone")
            local f = fs.open("out", "w") f.write(rs.getAnalogInput("bottom")) f.close()
            fs.open("done", "w").close()
            """);
        c.turnOn();
        until(c, 15000, "boot", () -> shows(c, "CloudOS"));
        io.setInput(ComputerSide.BOTTOM, 9);
        until(c, 15000, "the event", () -> c.fs().exists("done"));
        assertEquals("9", read(c, "out"));
    }

    @Test
    void fileHandlesAndQuotaFromPrograms() {
        computers.limits = new ComputerLimits(50, 1000, 16L << 20, 1L << 30, 256, 128, 4096, 2, 20);
        var c = computers.create();
        runStartup(c, """
            local results = {}
            local f = fs.open("a", "wb") f.write(65) f.write("BC") f.close()
            local r = fs.open("a", "rb") results[#results + 1] = r.read() .. r.read(5) r.close()
            local ap = fs.open("a", "a") ap.writeLine("D") ap.close()
            local l = fs.open("a", "r") results[#results + 1] = l.readLine() .. "/" .. tostring(l.readLine()) l.close()
            local h1 = fs.open("x1", "w")
            local h2 = fs.open("x2", "w")
            local h3, e3 = fs.open("x3", "w")
            results[#results + 1] = tostring(h3) .. ":" .. tostring(e3)
            h1.close()
            h2.close()
            local big = fs.open("big", "w")
            local ok, err = pcall(function() big.write(("z"):rep(5000)) end)
            big.close()
            results[#results + 1] = tostring(ok) .. ":" .. tostring(err)
            results[#results + 1] = tostring(pcall(fs.open("a", "r").readAll))
            local closed = fs.open("a", "r") closed.close()
            results[#results + 1] = tostring(pcall(closed.readLine))
            results[#results + 1] = tostring(select(2, fs.open("nope", "r")))
            results[#results + 1] = tostring(select(2, fs.open("/rom/x", "w")))
            local o = fs.open("out", "w") o.write(table.concat(results, "\\n")) o.close()
            fs.open("done", "w").close()
            """, 15000);
        String[] lines = read(c, "out").split("\n");
        assertEquals("65BC", lines[0]);
        assertEquals("ABCD/nil", lines[1]);
        assertTrue(lines[2].startsWith("nil:") && lines[2].contains("Too many files"), lines[2]);
        assertTrue(lines[3].startsWith("false:") && lines[3].contains("Out of space"), lines[3]);
        assertEquals("true", lines[4]);
        assertEquals("false", lines[5]);
        assertTrue(lines[6].contains("No such file"), lines[6]);
        assertTrue(lines[7].contains("Access denied"), lines[7]);
    }

    @Test
    void programsCannotReachTheHost() {
        var c = computers.create();
        runStartup(c, """
            local results = {}
            for _, name in ipairs({"io", "debug", "package", "require", "collectgarbage", "getfenv", "__native"}) do
              results[#results + 1] = name .. "=" .. type(_G[name])
            end
            results[#results + 1] = tostring(os.execute) .. tostring(os.getenv) .. tostring(os.remove)
            results[#results + 1] = tostring(load(string.char(27) .. "Lua"))
            local f = fs.open("../../../../escape", "w") f.write("x") f.close()
            results[#results + 1] = table.concat(fs.list("/"), ",")
            local o = fs.open("out", "w") o.write(table.concat(results, " ")) o.close()
            fs.open("done", "w").close()
            """, 15000);
        String out = read(c, "out");
        for (String name : new String[]{"io", "debug", "package", "require", "collectgarbage", "getfenv", "__native"}) {
            assertTrue(out.contains(name + "=nil"), name + " in " + out);
        }
        assertTrue(out.contains("nilnilnil"), out);
        assertTrue(out.contains("escape"), "the file landed on the drive: " + out);
        assertFalse(tmp.resolve("escape").toFile().exists());
    }

    @Test
    void shutdownAndRebootFromPrograms() {
        var c = computers.create();
        file(c, "startup.lua", """
            if not fs.exists("booted") then
              fs.open("booted", "w").close()
              os.reboot()
            end
            fs.open("done", "w").close()
            os.shutdown()
            """);
        c.turnOn();
        until(c, 15000, "reboot and shutdown", () -> c.fs().exists("done") && !c.isOn());
        assertNull(c.lastError());
    }

    @Test
    void inputIsRateLimitedPerSource() {
        var c = computers.create();
        c.turnOn();
        int accepted = 0;
        for (int i = 0; i < 500; i++) if (c.input(InputEvent.key(65, false), "spammer")) accepted++;
        assertTrue(accepted <= InputLimiter.BURST + 5, "accepted " + accepted);
        assertTrue(c.input(InputEvent.key(65, false), "someone-else"), "another source has its own budget");
    }

    @Test
    void pasteIsCleaned() {
        assertEquals("ab", InputEvent.cleanPaste("a\u0001b\nsecond line"));
        assertEquals(InputEvent.MAX_PASTE, InputEvent.cleanPaste("x".repeat(5000)).length());
        assertEquals("", InputEvent.cleanPaste("€"));
    }

    @Test
    void mouseNeedsAnAdvancedComputer() {
        int id = computers.registry.create();
        var host = new ComputerHost() {
            @Override
            public net.minecraft.server.level.@org.jspecify.annotations.Nullable ServerLevel level() {
                return null;
            }

            @Override
            public net.minecraft.core.BlockPos pos() {
                return net.minecraft.core.BlockPos.ZERO;
            }

            @Override
            public java.util.@org.jspecify.annotations.Nullable UUID owner() {
                return null;
            }

            @Override
            public RedstoneIo redstone() {
                return new RedstoneIo.Memory();
            }

            @Override
            public boolean advanced() {
                return false;
            }
        };
        var c = computers.registry.load(id, host, true);
        assertFalse(c.terminal().colour());
        assertFalse(c.input(InputEvent.mouse("mouse_click", 1, 1, 1), "p"));
    }

    @Test
    void labelsAndIdsSurviveARoundTrip() {
        var data = new ComputerData();
        int a = data.allocate(), b = data.allocate();
        data.setLabel(b, "Base");
        var again = ComputerData.roundTrip(data);
        assertEquals(2, again.allocate(), "ids are never handed out twice");
        assertEquals("Base", again.label(b));
        assertNull(again.label(a));
        assertEquals("abc", ComputerRegistry.cleanLabel("  a\u0000b​c "));
        assertNull(ComputerRegistry.cleanLabel("   "));
        assertEquals(ComputerRegistry.MAX_LABEL, ComputerRegistry.cleanLabel("y".repeat(100)).length());
    }

    @Test
    void unloadStopsTheProgram() {
        var c = computers.create();
        file(c, "startup.lua", "while true do sleep(0.05) end");
        c.turnOn();
        until(c, 15000, "boot", () -> shows(c, "CloudOS"));
        computers.registry.unload(c.id());
        assertFalse(c.isOn());
        assertNull(computers.registry.get(c.id()));
    }

    @Test
    void luaPromptEvaluates() {
        var c = computers.create();
        file(c, "startup.lua", "shell.run('lua')");
        c.turnOn();
        until(c, 15000, "the lua prompt", () -> shows(c, "lua>"));
        for (char ch : "1 + 2".toCharArray()) c.input(InputEvent.character(ch), "p");
        c.input(InputEvent.key(257, false), "p");
        until(c, 15000, "the result", () -> screen(c).contains("\n3\n"));
        for (char ch : "exit()".toCharArray()) c.input(InputEvent.character(ch), "p");
        c.input(InputEvent.key(257, false), "p");
        until(c, 15000, "back in the shell", () -> shows(c, "/>"));
    }

    @Test
    void editorSavesAFile() {
        var c = computers.create();
        file(c, "startup.lua", "shell.run('edit', 'notes.txt')");
        c.turnOn();
        until(c, 15000, "the editor", () -> shows(c, "Press Ctrl for the menu"));
        for (char ch : "hi".toCharArray()) c.input(InputEvent.character(ch), "p");
        c.input(InputEvent.key(257, false), "p");
        c.input(InputEvent.character('x'), "p");
        c.input(InputEvent.key(341, false), "p"); // ctrl: menu
        c.input(InputEvent.key(257, false), "p"); // Save
        until(c, 15000, "saved", () -> c.fs().exists("notes.txt") && shows(c, "Saved"));
        assertEquals("hi\nx\n", read(c, "notes.txt"));
        c.input(InputEvent.key(341, false), "p");
        c.input(InputEvent.key(262, false), "p"); // right: Exit
        c.input(InputEvent.key(257, false), "p");
        until(c, 15000, "back in the shell", () -> shows(c, "/>"));
    }
}
