package ai.ezfox.cccloud.code.store;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.Limits;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The saved scripts of a mainframe: names, versions, conflicts, quotas, and what survives a save and load. */
class ScriptBookTest {
    static final UUID STEVE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID ALEX = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final Limits LIMITS = Limits.DEFAULT;

    private static Supplier<String> ids(String... ids) {
        var queue = new ArrayDeque<>(List.of(ids));
        return queue::removeFirst;
    }

    @Test
    void names() {
        assertEquals("Night mode", ScriptBook.validName("  Night mode "));
        assertNull(ScriptBook.validName(""));
        assertNull(ScriptBook.validName("   "));
        assertNull(ScriptBook.validName(null));
        assertNull(ScriptBook.validName("x".repeat(33)));
        assertEquals("x".repeat(32), ScriptBook.validName("x".repeat(32)));
        assertEquals("🦊".repeat(32), ScriptBook.validName("🦊".repeat(32)), "32 characters, not 32 UTF-16 units");
        assertNull(ScriptBook.validName("a\nb"));
        assertNull(ScriptBook.validName("a​b"), "no invisible format characters");
        assertNull(ScriptBook.validName("a b"));
        assertNull(ScriptBook.validName("all"), "'all' is the stop command's word");
        assertNull(ScriptBook.validName(" ALL "));
        assertEquals("Grüße", ScriptBook.validName("Grüße"));
    }

    @Test
    void createSaveAndConflicts() {
        var book = new ScriptBook(List.of());
        var created = book.save(null, "Night", "print(1)", null, STEVE, "Steve", 1000, LIMITS, ids("aaaa0001", "aaaa0002"));
        assertNull(created.error());
        var night = created.script();
        assertEquals("aaaa0001", night.id());
        assertEquals(1, night.version());
        assertEquals(STEVE, night.author());
        assertEquals("name_taken", book.save(null, "night", "x", null, ALEX, "Alex", 1001, LIMITS, ids("aaaa0003")).error(), "names ignore case");

        // the same source changes nothing, a new one is a new version by whoever saved it
        assertEquals(1, book.save("aaaa0001", "Night", "print(1)", 1, ALEX, "Alex", 1002, LIMITS, ids()).script().version());
        var v2 = book.save("aaaa0001", "Night", "print(2)", 1, ALEX, "Alex", 1003, LIMITS, ids()).script();
        assertEquals(2, v2.version());
        assertEquals(ALEX, v2.author());
        assertEquals("print(2)", v2.source());

        // someone else saved in between: refused, with the version that is there now
        var stale = book.save("aaaa0001", "Night", "print(3)", 1, STEVE, "Steve", 1004, LIMITS, ids());
        assertEquals("changed", stale.error());
        assertEquals("2", stale.detail());

        // only the name: a rename, no new version
        var renamed = book.save("aaaa0001", "Night mode", "print(2)", 2, STEVE, "Steve", 1005, LIMITS, ids()).script();
        assertEquals("Night mode", renamed.name());
        assertEquals(2, renamed.version());
        assertSame(renamed, book.find("NIGHT MODE"));
        assertSame(renamed, book.find("aaaa0001"));
        assertNull(book.find("Night"));

        assertEquals("unknown_script", book.save("ffff0000", "Other", "x", null, STEVE, "Steve", 1006, LIMITS, ids()).error());
        assertEquals("invalid", book.save(null, "all", "x", null, STEVE, "Steve", 1006, LIMITS, ids("aaaa0004")).error());
        assertEquals("too_large", book.save(null, "Big", "x".repeat(LIMITS.maxSource() + 1), null, STEVE, "Steve", 1006, LIMITS, ids("aaaa0005")).error());
        assertEquals("too_large", book.save(null, "Big", "ü".repeat(LIMITS.maxSource() / 2 + 1), null, STEVE, "Steve", 1006, LIMITS, ids("aaaa0005")).error(),
            "the size is in UTF-8 bytes");
    }

    @Test
    void quotaAndIdCollisions() {
        var limits = Limits.DEFAULT.withConfig(2, 8, 50, 200, 20, 32, 64, 16384, 20);
        var book = new ScriptBook(List.of());
        assertEquals("aaaa0001", book.save(null, "A", "", null, STEVE, "Steve", 1, limits, ids("aaaa0001")).script().id());
        assertEquals("aaaa0002", book.save(null, "B", "", null, STEVE, "Steve", 2, limits, ids("aaaa0001", "aaaa0002")).script().id(), "a taken id is drawn again");
        var third = book.save(null, "C", "", null, STEVE, "Steve", 3, limits, ids("aaaa0003"));
        assertEquals("too_many", third.error());
        assertEquals("2", third.detail());
        assertNotNull(book.delete("aaaa0001"));
        assertNull(book.delete("aaaa0001"));
        assertNull(book.save(null, "C", "", null, STEVE, "Steve", 4, limits, ids("aaaa0003")).error(), "room again after a delete");
    }

    @Test
    void historyIsPrunedAndRevertAddsAVersion() {
        var limits = Limits.DEFAULT.withConfig(16, 8, 50, 200, 3, 32, 64, 16384, 20);
        var book = new ScriptBook(List.of());
        book.save(null, "S", "v1", null, STEVE, "Steve", 1, limits, ids("aaaa0001"));
        for (int v = 2; v <= 5; v++) book.save("aaaa0001", "S", "v" + v, null, STEVE, "Steve", v, limits, ids());
        var s = book.get("aaaa0001");
        assertEquals(List.of(3, 4, 5), s.versions().stream().map(ScriptVersion::version).toList(), "the newest three are kept");
        assertNull(s.version(1));

        assertEquals("unknown_version", book.revert("aaaa0001", 1, ALEX, "Alex", 10, limits).error());
        var reverted = book.revert("aaaa0001", 3, ALEX, "Alex", 10, limits).script();
        assertEquals(6, reverted.version(), "a revert is a new version, numbers are never reused");
        assertEquals("v3", reverted.source());
        assertEquals("revert of v3", reverted.current().note());
        assertEquals(ALEX, reverted.author());
        assertEquals(List.of(4, 5, 6), reverted.versions().stream().map(ScriptVersion::version).toList());
    }

    @Test
    void historyIsCappedInBytes() {
        var big = new Limits(16, 8, 200_000, 50, 200, 20, 32, 64, 16384, 20, 200, 1L << 30, 16L << 20, 20, 32, 6, 5, 0.2);
        var record = new ScriptRecord("aaaa0001", "S", "lua", false, null, "", 0, List.of(new ScriptVersion(1, 0, STEVE, "Steve", "a".repeat(100_000), null)), Map.of());
        for (int v = 2; v <= 4; v++) record = ScriptBook.withVersion(record, new ScriptVersion(v, v, STEVE, "Steve", String.valueOf(v).repeat(100_000), null), big);
        assertEquals(List.of(3, 4), record.versions().stream().map(ScriptVersion::version).toList(), "at most 256 KB of history");
        var huge = ScriptBook.withVersion(record, new ScriptVersion(5, 5, STEVE, "Steve", "x".repeat(300_000), null), big);
        assertEquals(List.of(5), huge.versions().stream().map(ScriptVersion::version).toList(), "the newest always stays");
    }

    @Test
    void renameAutostartAndStore() {
        var book = new ScriptBook(List.of());
        book.save(null, "A", "", null, STEVE, "Steve", 1, LIMITS, ids("aaaa0001"));
        book.save(null, "B", "", null, STEVE, "Steve", 1, LIMITS, ids("aaaa0002"));
        assertEquals("name_taken", book.rename("aaaa0002", " a ").error());
        assertEquals("invalid", book.rename("aaaa0002", "").error());
        assertEquals("b", book.rename("aaaa0002", "b").script().name(), "a different case of its own name is fine");
        var on = book.setAutostart("aaaa0001", true, ALEX, "Alex").script();
        assertTrue(on.autostart());
        assertEquals(ALEX, on.autostartBy());
        book.setStore("aaaa0001", Map.of("count", "3"));
        assertEquals(Map.of("count", "3"), book.get("aaaa0001").store());
        assertEquals("unknown_script", book.setAutostart("ffff0000", true, ALEX, "Alex").error());
    }

    @Test
    void savedDataRoundTrip() {
        var mainframe = UUID.fromString("00000000-0000-4000-8000-00000000aaaa");
        var book = new ScriptBook(List.of());
        book.save(null, "Night mode", "-- Grüße\nprint(1)", null, STEVE, "Steve", 1000, LIMITS, ids("aaaa0001"));
        book.save("aaaa0001", "Night mode", "print(2)", 1, ALEX, "Alex", 2000, LIMITS, ids());
        book.revert("aaaa0001", 1, STEVE, "Steve", 3000, LIMITS);
        book.setAutostart("aaaa0001", true, ALEX, "Alex");
        book.setStore("aaaa0001", Map.of("k", "{\"a\":1}"));
        book.save(null, "Other", "", null, ALEX, "Alex", 4000, LIMITS, ids("aaaa0002"));
        var data = new CodeData();
        data.set(mainframe, book.scripts());
        data.set(UUID.randomUUID(), List.of());
        var back = CodeData.roundTrip(data);
        assertEquals(data.scripts(), back.scripts());
        assertEquals(1, back.scripts().size(), "a mainframe without scripts is not stored");
        var s = back.scripts(mainframe).getFirst();
        assertEquals(3, s.version());
        assertEquals("revert of v1", s.current().note());
        assertNull(s.version(2).note());
        assertTrue(s.autostart());
        assertEquals(ALEX, s.autostartBy());
    }

    @Test
    void damagedDataLoadsSafely() {
        // autostart without who switched it on cannot run with anyone's rights: it loads switched off; no versions: an empty one
        var json = com.google.gson.JsonParser.parseString("{\"id\":\"aaaa0001\",\"name\":\"S\",\"autostart\":true,\"created\":5,\"versions\":[]}");
        var s = CodeData.RECORD_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertFalse(s.autostart());
        assertEquals(1, s.versions().size());
        assertEquals("", s.source());
        assertEquals("lua", s.language());
        assertEquals(Map.of(), s.store());
    }
}
