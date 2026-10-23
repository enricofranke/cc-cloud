package ai.ezfox.cccloud.rom;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.RegisteredFunction;
import org.squiddev.cobalt.lib.CoreLibraries;
import org.junit.jupiter.api.Test;

/**
 * Runs the Lua that ships in the CC ROM (cloud.ui, cloud-demo, cloud-storage) on CC's own VM (Cobalt) against a fake
 * uplink. Every widget call goes through the real {@link WidgetFactory}, so wrong option names or values in the
 * programs fail this test exactly like they would fail in game.
 */
class RomLuaTest {
    private static final String ROM = "data/computercraft/lua/rom/";
    private static final String[] LUA_FILES = {"modules/main/cloud/ui.lua", "programs/cloud-demo.lua", "programs/cloud-storage.lua"};

    /** The validating part of the fake uplink (the bookkeeping is in rom_test/prelude.lua). */
    private static final class FakeUplink {
        final Map<String, ai.ezfox.cccloud.uplink.Widget> widgets = new LinkedHashMap<>();
        int pages;

        Varargs create(LuaState state, Varargs args) throws LuaError {
            String method = args.arg(1).checkString();
            Object id = toJava(args.arg(2));
            Object label = toJava(args.arg(3));
            Object opts = toJava(args.arg(4));
            try {
                switch (method) {
                    case "page" -> {
                        WidgetFactory.page(id, label, (Map<?, ?>) opts);
                        pages++;
                    }
                    case "group" -> WidgetFactory.group(id, label, (Map<?, ?>) opts);
                    default -> {
                        var type = method.equals("itemSearch") ? WidgetType.ITEM_SEARCH : WidgetType.byId(method);
                        if (type == null) throw new LuaError("unknown widget method " + method);
                        if (type.isBridgeBound() && !(opts instanceof Map<?, ?> m && m.get("bridge") instanceof String)) {
                            throw new LuaError(method + " needs {bridge=\"<peripheral name>\"}");
                        }
                        var widget = WidgetFactory.create(type, WidgetFactory.checkId(id), label, (Map<?, ?>) opts);
                        if (!widgets.containsKey(widget.id()) && widgets.size() >= (pages > 0 ? 64 : 32)) throw new LuaError("too many widgets");
                        widgets.put(widget.id(), widget);
                    }
                }
            } catch (WidgetFactory.WidgetException e) {
                throw new LuaError(e.getMessage());
            }
            return ValueFactory.varargsOf();
        }

        Varargs set(LuaState state, Varargs args) throws LuaError {
            String id = args.arg(1).checkString();
            var widget = widgets.get(id);
            if (widget == null) throw new LuaError("no widget with id '" + id + "'");
            try {
                WidgetFactory.valueFor(widget, toJava(args.arg(2)));
            } catch (WidgetFactory.WidgetException e) {
                throw new LuaError(e.getMessage());
            }
            return ValueFactory.varargsOf();
        }
    }

    /** Converts like CC does: numbers become Double, tables Map. */
    static Object toJava(LuaValue value) throws LuaError {
        if (value.isNil()) return null;
        if (value.type() == org.squiddev.cobalt.Constants.TBOOLEAN) return value.toBoolean();
        if (value.isNumber() && value.type() == org.squiddev.cobalt.Constants.TNUMBER) return value.toDouble();
        if (value.isString()) return value.toString();
        if (value instanceof LuaTable table) {
            var map = new HashMap<Object, Object>();
            LuaValue key = org.squiddev.cobalt.Constants.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.first();
                if (key.isNil()) break;
                map.put(toJava(key), toJava(next.arg(2)));
            }
            return map;
        }
        return value.toString();
    }

    private static LuaFunction compile(LuaState state, InputStream in, String name, LuaValue env) throws Exception {
        return LoadState.load(state, in, name, env);
    }

    /** A ROM file from the mod's resources (on the test classpath). */
    private static InputStream file(String path) throws IOException {
        var in = RomLuaTest.class.getClassLoader().getResourceAsStream(ROM + path);
        if (in == null) throw new IOException("missing ROM file " + path);
        return in;
    }

    private static FakeUplink run(String scenario) throws Exception {
        var state = new LuaState();
        CoreLibraries.standardGlobals(state);
        var globals = state.globals();
        var fake = new FakeUplink();
        RegisteredFunction.bind(globals, new RegisteredFunction[]{
            RegisteredFunction.ofV("__create", fake::create),
            RegisteredFunction.ofV("__set", fake::set),
        });

        // Modules for require(): CC's cc.expect from the CC jar and our cloud.ui.
        var modules = new LuaTable();
        try (var expect = RomLuaTest.class.getClassLoader().getResourceAsStream("data/computercraft/lua/rom/modules/main/cc/expect.lua")) {
            assertNotNull(expect, "cc/expect.lua not on the test classpath");
            modules.rawset("cc.expect", compile(state, expect, "@cc/expect.lua", globals));
        }
        try (var ui = file("modules/main/cloud/ui.lua")) {
            modules.rawset("cloud.ui", compile(state, ui, "@cloud/ui.lua", globals));
        }
        globals.rawset("__modules", modules);
        var programs = new LuaTable();
        for (var name : new String[]{"cloud-demo", "cloud-storage"}) {
            try (var in = file("programs/" + name + ".lua")) {
                programs.rawset(name, compile(state, in, "@" + name + ".lua", globals));
            }
        }
        globals.rawset("__programs", programs);

        try (var prelude = RomLuaTest.class.getResourceAsStream("/rom_test/prelude.lua")) {
            LuaThread.runMain(state, compile(state, prelude, "@prelude.lua", globals));
        }
        try (var test = RomLuaTest.class.getResourceAsStream("/rom_test/" + scenario + ".lua")) {
            LuaThread.runMain(state, compile(state, test, "@" + scenario + ".lua", globals));
        }
        return fake;
    }

    @Test
    void romFilesCompile() throws Exception {
        var state = new LuaState();
        CoreLibraries.standardGlobals(state);
        for (var path : LUA_FILES) {
            try (var in = file(path)) {
                assertNotNull(compile(state, in, "@" + path, state.globals()), path);
            }
        }
        try (var help = file("help/cloud.txt")) {
            assertTrue(new String(help.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("cloud.itemSearch"));
        }
    }

    @Test
    void uiLibraryDispatchesActionsAndTimers() throws Exception {
        var fake = run("ui_test");
        assertTrue(fake.widgets.containsKey("temp"));
    }

    @Test
    void handlersThatSleepDoNotBlockOthers() throws Exception {
        run("ui_sleep_test");
    }

    @Test
    void cloudDemoBuildsAValidDashboard() throws Exception {
        var fake = run("demo_test");
        for (var id : new String[]{"temp", "power", "fuel", "running", "status", "enabled", "rods", "mode", "target", "note", "preset", "scram"}) {
            assertTrue(fake.widgets.containsKey(id), id);
        }
        assertEquals(0, fake.pages % 2, "two pages per run (the test starts the program four times)");
    }

    @Test
    void cloudStorageBuildsAValidDashboard() throws Exception {
        var fake = run("storage_test");
        for (var id : new String[]{"storage", "energy", "online", "items", "top", "watch", "search", "jobs"}) {
            assertTrue(fake.widgets.containsKey(id), id);
        }
    }
}
