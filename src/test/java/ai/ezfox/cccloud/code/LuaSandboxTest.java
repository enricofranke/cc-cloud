package ai.ezfox.cccloud.code;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.api.ApiSpec;
import ai.ezfox.cccloud.code.lua.LuaEngine;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Lua sandbox: exactly the descriptor's whitelist, no way out, bounded bulk operations, clean compile errors. */
class LuaSandboxTest {
    private final TestScripts scripts = new TestScripts();

    @AfterEach
    void close() {
        scripts.close();
    }

    /** Runs a script that logs one result line and returns that line. */
    private String eval(String source) throws InterruptedException {
        var run = scripts.run(source);
        assertTrue(run.await(5000), "script did not end: " + run.logText());
        assertNull(run.error(), () -> "error: " + run.error().getMessage() + "\n" + run.logText());
        var lines = run.log().since(0).stream().filter(l -> l.level().equals("info")).toList();
        assertFalse(lines.isEmpty(), run.logText());
        return lines.getLast().text();
    }

    private String fails(String source) throws InterruptedException {
        var run = scripts.run(source);
        assertTrue(run.await(10_000), "script did not end: " + run.logText());
        assertNotNull(run.error(), "expected an error:\n" + run.logText());
        return run.error().getMessage();
    }

    @Test
    void globalsAreExactlyTheWhitelist() throws InterruptedException {
        var spec = ApiSpec.get();
        String names = eval("local t = {} for k in pairs(_G) do t[#t + 1] = k end table.sort(t) print(table.concat(t, ','))");
        var expected = new java.util.TreeSet<>(spec.lua().globals());
        expected.addAll(spec.lua().libraries().keySet());
        expected.add("cloud");
        expected.add("print");
        assertEquals(String.join(",", expected), names);
        for (var lib : spec.lua().libraries().entrySet()) {
            String members = eval("local t = {} for k in pairs(" + lib.getKey() + ") do t[#t + 1] = k end table.sort(t) print(table.concat(t, ','))");
            assertEquals(String.join(",", new java.util.TreeSet<>(lib.getValue())), members, "members of " + lib.getKey() + " (every listed one exists)");
        }
    }

    @Test
    void removedThingsAreGone() throws InterruptedException {
        for (var name : ApiSpec.get().lua().removed()) {
            String path = name.contains(".") ? name : "_G." + name;
            assertEquals("nil", eval("print(type(" + path + "))"), name + " must be gone");
        }
        assertEquals("nil", eval("print(type(getmetatable('').__index.dump))"), "string.dump through the string metatable");
        assertEquals("true", eval("print(rawget(_G, 'java') == nil and rawget(_G, 'luajava') == nil and rawget(_G, 'jit') == nil)"));
    }

    @Test
    void noBytecode() throws InterruptedException {
        String out = eval("local f, err = load(string.char(27) .. 'LuaR\\0\\1\\4\\8\\4\\8\\0') print(tostring(f) .. ' ' .. tostring(err))");
        assertTrue(out.startsWith("nil "), out);
        assertTrue(out.contains("binary"), out);
        assertEquals("3", eval("print(load('return 1 + 2')())"), "source text still loads");
    }

    @Test
    void loadHasASizeLimit() throws InterruptedException {
        // a reader that never ends stops at the limit, before the next piece is read
        assertEquals("nil chunk too large (more than 256 KB) 263", eval("""
            local n = 0
            local f, err = load(function() n = n + 1 return ("x"):rep(1000) end)
            print(tostring(f) .. " " .. err .. " " .. n)
            """));
        assertEquals("nil chunk too large (more than 256 KB)", eval("local f, err = load(('x'):rep(300000)) print(tostring(f) .. ' ' .. err)"));
        assertEquals("nil reader function must return a string", eval("local f, err = load(function() return {} end) print(tostring(f) .. ' ' .. err)"));
        // a large allowed chunk, from a string and from a reader
        assertEquals("20000 20000", eval("""
            local src = ("n = n + 1\\n"):rep(20000)
            n = 0 load(src)() local a = n
            local i = 0
            n = 0 load(function() i = i + 1 if i <= 20000 then return "n = n + 1\\n" end end)()
            print(a .. " " .. n)
            """));
        // the other arguments pass through: name, mode and an environment; without one the globals stay
        assertEquals("5 function c:1:", eval("""
            local a = load("return x", "=c", "t", {x = 5})()
            local b = load("return type(print)")()
            local _, err = load("+", "=c")
            print(a .. " " .. b .. " " .. err:sub(1, 4))
            """));
    }

    @Test
    void scriptsDoNotShareGlobals() throws InterruptedException {
        var a = scripts.run("leak = 'secret' string.leak = 1 cloud.leak = 2 cloud.sleep(2)");
        TestScripts.until(() -> a.runtime().handlerCount() >= 0 && a.log().size() > 0, 2000, "first script started");
        assertEquals("nil nil nil", eval("print(rawget(_G, 'leak'), string.leak, cloud.leak)"));
    }

    @Test
    void boundedBulkOperations() throws InterruptedException {
        assertTrue(fails("local s = string.rep('x', 2 * 1024 * 1024)").contains("over 1 MB"));
        assertTrue(fails("local s = ('ab'):rep(600 * 1024)").contains("over 1 MB"), "through the string metatable too");
        assertTrue(fails("local t = {} for i = 1, 20 do t[i] = string.rep('x', 64 * 1024) end table.concat(t)").contains("over 1 MB"));
        assertTrue(fails("unpack({}, 1, 1e7)").contains("too many results"));
        assertTrue(fails("table.unpack({}, 1, 2^31 - 2)").contains("too many results"));
        assertTrue(fails("table.move({}, 1, 2^30, 2)").contains("too many elements"));
        // huge counts: no endless Java loop (that blocked a worker thread for good) and no overflowing size checks
        assertEquals("0 0", eval("print(#string.rep('', 2^62), #string.rep('', 1e12, ''))"));
        assertTrue(fails("string.rep('abc', 6148914691236517206)").contains("over 1 MB"), "length * n overflows");
        assertTrue(fails("string.rep('', 2^40, 'x')").contains("over 1 MB"), "only separators");
        assertTrue(fails("table.unpack({}, -2^63, 2^63 - 2048)").contains("too many results"), "j - i overflows");
        assertTrue(fails("table.move({1}, -2^63, 0, 1)").contains("too many elements"), "e - f overflows");
        assertEquals("1,2,3", eval("print(table.concat({1, 2, 3}, ','))"));
        assertEquals("3", eval("print(select('#', table.unpack({1, 2, 3})))"));
        assertEquals("1 1 2 3", eval("local t = table.move({1, 2, 3}, 1, 3, 2) print(t[1], t[2], t[3], t[4])"));
    }

    /** A script that must hit the 1 MB bound (CPU has room by default, so the bound is what stops it on any machine). */
    private void hitsOneMb(String source, String what) throws InterruptedException {
        var run = scripts.run(source);
        assertTrue(run.await(10_000), "script did not end: " + run.logText());
        assertNotNull(run.error(), "expected an error:\n" + run.logText());
        String message = run.error().getMessage();
        assertTrue(message.contains("over 1 MB"), what + ": " + message);
    }

    @Test
    void gsubAndFormatAreBounded() throws InterruptedException {
        // one call each that would build terabytes in Java, without a budget check in between
        hitsOneMb("local s = ('x'):rep(1000000) s:gsub('.', s)", "a replacement string");
        hitsOneMb("local s = ('x'):rep(1000) s:gsub('.', ('%0'):rep(2000))", "repeated captures");
        hitsOneMb("local s = ('x'):rep(1000000) s:gsub('.', function() return s end)", "a replacement function");
        hitsOneMb("local s = ('x'):rep(1000000) s:gsub('.', {x = s})", "a replacement table");
        hitsOneMb("local s = ('x'):rep(600000) string.format('%s%s', s, s)", "format %s");
        hitsOneMb("local s = ('x'):rep(300000) string.format('%q', s)", "%q can make four bytes of one");
        // big sources with few matches, and ordinary use, still work
        // (the quick bound says up to 1.4 MB, the exact count of matches lets it through)
        assertEquals("200006 1", eval("local s = ('x'):rep(200000) .. 'y' local r, n = s:gsub('y', 'zzzzzz') print(#r, n)"));
        assertEquals("h-el-lo", eval("print((('hello'):gsub('(%w)(%w)', '%1-%2')))"));
        assertEquals("HELLO world 1", eval("print(('hello world'):gsub('%a+', string.upper, 1))"));
        assertEquals("one two 2", eval("print(('1 2'):gsub('%d', {['1'] = 'one', ['2'] = 'two'}))"));
        assertEquals("x=  3 y=0.50 \"ab\"", eval("print(string.format('x=%3d y=%.2f %q', 3, 0.5, 'ab'))"));
        // a replacement function may wait (the call stays resumable)
        assertEquals("a1b1", eval("print((('ab'):gsub('%a', function(c) cloud.sleep(0.01) return c .. '1' end)))"));
        assertEquals("nil", eval("print(string.pack)"));
    }

    @Test
    void wrappedFunctionsReportTheScriptLine() throws InterruptedException {
        var run = scripts.run("local a = 1\nlocal r = string.gsub(nil, 'x', 'y')\n");
        assertTrue(run.await(5000));
        assertNotNull(run.error());
        assertEquals(2, run.error().line(), run.error().getMessage());
        assertTrue(run.error().getMessage().contains("bad argument"), run.error().getMessage());
        var format = scripts.run("\nlocal s = string.format('%d', 'x')");
        assertTrue(format.await(5000));
        assertEquals(2, format.error().line(), format.error().getMessage());
    }

    @Test
    void osIsHarmless() throws InterruptedException {
        assertEquals("number string table", eval("print(type(os.time()), type(os.date('%H:%M')), type(os.date('*t')))"));
        assertEquals("2026-11-02", eval("print(os.date('!%Y-%m-%d', 1793577600))"));
        assertEquals("nil nil nil", eval("print(os.execute, os.getenv, os.remove)"));
    }

    @Test
    void utf8RoundTrips() throws InterruptedException {
        assertEquals("Grüße 5 7", eval("print('Grüße', utf8.len('Grüße'), #'Grüße')"));
    }

    @Test
    void compileErrorsHaveLines() {
        var engine = new LuaEngine(ZoneId::systemDefault);
        var d = engine.check("Night mode", "local x = 1\nif x then\n  print(x)\n");
        assertEquals(1, d.size());
        assertEquals(4, d.getFirst().line(), d.getFirst().message());
        assertTrue(d.getFirst().message().contains("'end' expected"), d.getFirst().message());
        assertTrue(engine.check("ok", "print('fine')").isEmpty());
        var e = engine.check("x", "local = 3");
        assertEquals(1, e.getFirst().line());
    }

    @Test
    void runtimeErrorsHaveLines() throws InterruptedException {
        var run = scripts.run("local a = 1\nlocal t = nil\nt.x = a\n");
        assertTrue(run.await(5000));
        assertNotNull(run.error());
        assertEquals(3, run.error().line(), run.error().getMessage());
        assertTrue(run.error().getMessage().startsWith("test:3:"), run.error().getMessage());
        var bad = scripts.run("cloud.set('Reactor')");
        assertTrue(bad.await(5000));
        assertTrue(bad.error().getMessage().contains("bad argument #2 to 'cloud.set' (string expected, got no value)"), bad.error().getMessage());
        assertEquals(1, bad.error().line());
    }
}
