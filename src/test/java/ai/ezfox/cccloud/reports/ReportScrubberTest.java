package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportScrubberTest {
    private static final ReportScrubber SERVER = new ReportScrubber(
        List.of("Steve12", "xX_Dragon_Xx", "enrico"),
        List.of("Meine Welt", "mybase.example.org", "Lager Nord", "fabians-server"));

    /** What goes in, the parts that must be gone, the token that must stand in for them. */
    static Stream<Arguments> personal() {
        return Stream.of(
            // Windows paths
            a("Failed at C:\\Users\\Max Mustermann\\AppData\\Roaming\\.minecraft\\saves\\x\\level.dat", "<path>", "Max", "Mustermann", "AppData", "level.dat"),
            a("C:/Us" + "ers/anna/Desktop/mods/x.jar", "<path>", "anna", "Desktop"),
            a("D:\\Games\\Minecraft Server\\world\\region\\r.0.0.mca was locked", "<path>", "Games", "r.0.0"),
            a("E:\\mc\\logs\\latest.log", "<path>", "latest"),
            a("C:\\Documents and Settings\\Bob\\mc\\x.txt", "<path>", "Bob"),
            a("\\\\NAS01\\share\\minecraft\\world", "<path>", "NAS01", "share"),
            a("%APPDATA%\\.minecraft\\config", "<path>", "APPDATA"),
            a("%USERPROFILE%\\Desktop\\server", "<path>", "USERPROFILE", "Desktop"),
            a("Path c%3A%5CUsers%5Canna%5Cworld was bad", "<path>", "anna"),
            // macOS paths
            a("/Us" + "ers/steve/Library/Application Support/minecraft/saves/x/level.dat", "<path>", "enrico", "Library", "Application"),
            a("/Volumes/Backup Disk/server/world", "<path>", "Backup", "Disk"),
            a("/private/var/fol" + "ders/ab/cd/T/session.lock", "<path>", "folders", "session"),
            // Linux paths
            a("/home/steve/server/world/level.dat", "<path>", "steve", "server"),
            a("/root/mc/server.properties", "<path>", "root", "server.properties"),
            a("/var/lib/minecraft/world/region/r.0.0.mca", "<path>", "minecraft", "region"),
            a("/opt/mc/run.sh failed", "<path>", "opt", "run.sh"),
            a("/srv/minecraft/atm11", "<path>", "atm11"),
            a("/mnt/data/mc/world", "<path>", "data"),
            a("~/mc/server/level.dat", "<path>", "server", "level"),
            a("$HOME/mc/world", "<path>", "HOME"),
            a("${HOME}/mc/world", "<path>", "HOME"),
            // relative paths and files
            a("could not read saves/backup/world2/level.dat", "<path>", "backup", "world2"),
            a("config/cccloud-server.toml is bad", "<path>", "cccloud-server"),
            a("crash-reports/crash-2026-11-01_12.00.00-server.txt", "<path>", "crash-2026"),
            a("..\\..\\secret\\notes.txt", "<path>", "notes"),
            a("Reading level.dat failed", "<file>", "level.dat"),
            a("startup.lua: attempt to index nil", "<file>", "startup.lua"),
            a("Meine_Basis.lua stopped", "<file>", "Meine_Basis"),
            a("server.properties not found", "<file>", "server.properties"),
            // IPv4
            a("Connection from 192.168.1.20 refused", "<ip>", "192.168"),
            a("10.0.0.1:25565 timed out", "<ip>", "10.0.0.1", "25565"),
            a("peer /203.0.113.7:43210", "<ip>", "203.0.113.7"),
            a("127.0.0.1", "<ip>", "127.0.0.1"),
            a("ip=8.8.8.8;port=1", "<ip>", "8.8.8.8"),
            // IPv6
            a("Listening on 2001:db8::1", "<ip>", "2001:db8"),
            a("[2001:db8::2]:25565 closed", "<ip>", "2001:db8", "25565"),
            a("fe80::1%en0 is gone", "<ip>", "fe80", "en0"),
            a("from ::1 only", "<ip>", "::1"),
            a("::ffff:192.168.0.1", "<ip>", "192.168", "ffff"),
            a("2001:0db8:85a3:0000:0000:8a2e:0370:7334", "<ip>", "85a3", "7334"),
            a("MAC 00:1A:2B:3C:4D:5E", "<ip>", "00:1A", "5E"),
            // UUIDs
            a("Player 069a79f4-44e9-4726-a5be-fca90e38aaf5 left", "<uuid>", "069a79f4", "fca90e38aaf5"),
            a("Owner 069A79F4-44E9-4726-A5BE-FCA90E38AAF5", "<uuid>", "069A79F4"),
            a("id 069a79f444e94726a5befca90e38aaf5", "<uuid>", "069a79f4"),
            // e-mail addresses
            a("mail max.mustermann@example.com please", "<email>", "max.mustermann", "example.com"),
            a("anna+mc@web.de", "<email>", "anna", "web.de"),
            a("jürgen.müller@posteo.de", "<email>", "jürgen", "posteo"),
            a("mailto:leon@example.org", "<url>", "leon"),
            // known names
            a("Steve12 was kicked", "<player>", "Steve12"),
            a("STEVE12 was kicked", "<player>", "STEVE12"),
            a("<xX_Dragon_Xx> hello", "<player>", "xX_Dragon_Xx"),
            a("owner=enrico;", "<player>", "enrico"),
            a("World Meine Welt could not load", "<name>", "Meine Welt"),
            a("WORLD MEINE WELT", "<name>", "MEINE"),
            a("mainframe lager nord is offline", "<name>", "lager"),
            a("Connect name fabians-server is taken", "<name>", "fabians"),
            // tokens and secrets
            a("Authorization: Bearer abc.def.ghi", "<secret>", "abc.def"),
            a("Bearer abcdef0123456789", "<token>", "abcdef012345"),
            a("Basic dXNlcjpwYXNz", "<token>", "dXNlcjpwYXNz"),
            a("key cck_abcdefghijklmnop123456", "<token>", "cck_abc"),
            a("ghp_16C7e42F292c6912E7710c838347Ae178B4a", "<token>", "ghp_16"),
            a("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdefSIGNATURE", "<token>", "eyJhbG"),
            a("token=abc123", "<secret>", "abc123"),
            a("password: hunter2", "<secret>", "hunter2"),
            a("\"pin\":\"1234\"", "<secret>", "1234"),
            a("my pin is 4711", "<secret>", "4711"),
            a("api_key=AKIA1234", "<secret>", "AKIA1234"),
            a("Cookie: session=abcdef", "<secret>", "abcdef"),
            a("pairing code ABCD-EFGH", "<token>", "ABCD-EFGH"),
            a("hash 0123456789abcdef0123", "<token>", "0123456789ab"),
            a("blob Zm9vYmFyMTIzNDU2Nzg5MEFCQ0RFRkdISUpLTE1O", "<token>", "Zm9vYmFy"),
            // URLs and hosts
            a("GET https://mybase.duckdns.org:8443/api?token=abc failed", "<url>", "duckdns", "8443", "abc"),
            a("http://192.168.1.2:8080/", "<url>", "192.168"),
            a("wss://relay.example.net/tunnel", "<url>", "relay", "tunnel"),
            a("www.example.com/foo", "<url>", "example"),
            a("jar:file:/home/steve/mods/x.jar!/y", "<url>", "steve"),
            a("file:///C:/Us" + "ers/anna/x.txt", "<url>", "anna"),
            a("connect to mc.hypixel.net failed", "<host>", "hypixel"),
            a("play.example.net:25565 unreachable", "<host>", "example", "25565"),
            a("nas.local is down", "<host>", "nas"),
            a("mcserver:25565 refused", "<host>", "mcserver"),
            a("mybase.example.org is down", "<name>", "mybase"),
            // coordinates
            a("BlockPos{x=100, y=64, z=-200}", "<coords>", "100", "-200"),
            a("MutableBlockPos{x=1, y=2, z=3} moved", "<coords>", "x=1"),
            a("at [100, 64, -200]", "<coords>", "100"),
            a("at (100, 64, -200)", "<coords>", "100"),
            a("teleport to 100 64 -200 now", "<coords>", "100", "-200"),
            a("x=1,y=2,z=3", "<coords>", "x=1"),
            a("x: 1.5, y: 64, z: -3.2", "<coords>", "1.5"),
            a("Vec3(1.5, 2.5, 3.5)", "<coords>", "1.5"),
            a("ChunkPos[3, -4]", "<coords>", "-4"),
            a("chunk 3 -4 unloaded", "<coords>", "chunk 3"),
            a("region [12, -7]", "<coords>", "-7"),
            // names of things in the world
            a("Item minecraft:diamond_sword is missing", "<id>", "diamond_sword"),
            a("ae2:controller broke", "<id>", "controller"),
            a("1234567890 tokens", "<number>", "1234567890"),
            // chat colors must not hide anything
            a("§cSteve12§r left", "<player>", "Steve12", "§"));
    }

    private static Arguments a(String input, String token, String... gone) {
        return Arguments.of(input, token, List.of(gone));
    }

    @ParameterizedTest
    @MethodSource("personal")
    void removesPersonalData(String input, String token, List<String> gone) {
        String out = SERVER.text(input);
        assertTrue(out.contains(token), () -> "expected " + token + " in: " + out);
        for (String part : gone) assertFalse(out.contains(part), () -> part + " survived in: " + out);
    }

    @ParameterizedTest
    @MethodSource("personal")
    void scrubbingTwiceChangesNothing(String input) {
        String once = SERVER.text(input);
        assertEquals(once, SERVER.text(once));
        assertTrue(SERVER.clean(once), () -> "not clean: " + once);
    }

    @ParameterizedTest
    @MethodSource("personal")
    void isNotCleanBefore(String input) {
        assertFalse(SERVER.clean(input), input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Index 5 out of bounds for length 3",
        "Not ready",
        "Cannot read the array length because \"this.values\" is null",
        "Cannot invoke \"net.minecraft.world.level.Level.getBlockState()\" because \"this.level\" is null",
        "class ai.ezfox.cccloud.web.Foo cannot be cast to class ai.ezfox.cccloud.web.Bar",
        "on/off and and/or stay words",
        "Ticking block entity",
        "HttpHandler.java:120",
        "version 26.1.2 of neoforge",
        "2 of 3 cards",
        "<url> and <path> stay what they are",
        "attempt to compare number with nil",
        "Timeout after 5000 ms",
    })
    void leavesTechnicalTextReadable(String text) {
        assertEquals(text, SERVER.text(text));
        assertTrue(SERVER.clean(text));
    }

    @Test
    void namesAreWholeWords() {
        assertEquals("Steve123 is someone else", SERVER.text("Steve123 is someone else"));
    }

    @Test
    void shortLiteralsAreIgnored() {
        var scrubber = new ReportScrubber(List.of(), List.of("a", "ab", " ", "abc"));
        assertEquals("a ab <name>", scrubber.text("a ab abc"));
    }

    @Test
    void longestLiteralWins() {
        var scrubber = new ReportScrubber(List.of(), List.of("my world", "my world backup"));
        assertEquals("open <name> now", scrubber.text("open my world backup now"));
    }

    @Test
    void literalsSurviveCaseFoldingThatChangesLength() {
        var scrubber = new ReportScrubber(List.of(), List.of("Welt"));
        String out = scrubber.text("İİİ WELT");
        assertFalse(out.toLowerCase(Locale.ROOT).contains("welt"), out);
    }

    @Test
    void multiLineTextKeepsItsLinesButMessagesDoNot() {
        assertEquals("one\ntwo", ReportScrubber.EMPTY.text("one\ntwo"));
        assertEquals("one two", ReportScrubber.EMPTY.message("one\ntwo"));
    }

    @Test
    void controlCharactersGo() {
        assertEquals("a b", ReportScrubber.EMPTY.text("a\u0000b".replace("\u0000", "\u0007 ")));
        assertFalse(ReportScrubber.EMPTY.text("a\u202Eb").contains("\u202E"));
    }

    @Test
    void messagesAreCutWithoutHalfTokens() {
        String message = "x".repeat(ReportScrubber.MAX_MESSAGE - 3) + " " + "/home/steve/world/a";
        String out = ReportScrubber.EMPTY.message(message);
        assertTrue(out.length() <= ReportScrubber.MAX_MESSAGE + 1);
        assertFalse(out.contains("<pa"), out);
        assertTrue(out.endsWith("…"));
    }

    @Test
    void onlyTheStartOfAHugeTextIsRead() {
        String out = ReportScrubber.EMPTY.text("a ".repeat(100_000));
        assertTrue(out.length() <= ReportScrubber.MAX_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "/", "/a", "a/", "1 ", "1,", "<", ":", "::", "a:", "a.", "x=1,", "C:\\", "\\\\", "%", "§", "@", "a@", "[", "{", "aB3", "0123456789abcdef", "é/", "a@a.", "a.a@", "C:\\a \\", "/é ", "a.de ", "a-", "$", "~/"})
    void hostileInputStaysFast(String unit) {
        String input = unit.repeat(ReportScrubber.MAX_INPUT / unit.length() + 1);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> SERVER.text(input));
    }

    @Test
    void classNamesKeepTheirShape() {
        assertEquals("com.<player>.Mod", SERVER.className("com.steve12.Mod"));
        assertEquals("ai.ezfox.cccloud.web.HttpHandler", SERVER.className("ai.ezfox.cccloud.web.HttpHandler"));
    }

    @Test
    void cutKeepsShortTexts() {
        assertEquals("abc", ReportScrubber.cut("abc", 3));
        assertEquals("ab…", ReportScrubber.cut("abcd", 2));
    }

    @Test
    void ipv6CheckIsStrict() {
        assertTrue(ReportScrubber.isIpv6("::1"));
        assertTrue(ReportScrubber.isIpv6("2001:db8::1"));
        assertTrue(ReportScrubber.isIpv6("1:2:3:4:5:6:7:8"));
        assertTrue(ReportScrubber.isIpv6("::ffff:1.2.3.4"));
        assertFalse(ReportScrubber.isIpv6("::"));
        assertFalse(ReportScrubber.isIpv6("1:2:3"));
        assertFalse(ReportScrubber.isIpv6("1::2::3"));
        assertFalse(ReportScrubber.isIpv6("12345::1"));
        assertFalse(ReportScrubber.isIpv6("abc:def"));
        assertFalse(ReportScrubber.isIpv6("java.lang:type"));
    }

    @Test
    void anEmptyServerStillRecognisesShapes() {
        assertNotEquals("/home/steve/x", ReportScrubber.EMPTY.text("/home/steve/x"));
        assertEquals("Steve12", ReportScrubber.EMPTY.text("Steve12"));
    }
}
