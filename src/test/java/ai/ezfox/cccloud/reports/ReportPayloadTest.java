package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.reports.ReportPayload.Counts;
import ai.ezfox.cccloud.reports.ReportPayload.Env;
import ai.ezfox.cccloud.reports.ReportPayload.Where;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportPayloadTest {
    private static final Env ENV = new Env("0.17.0", "26.1.2.100", "26.1.2", 25, "linux");
    private static final ReportScrubber SCRUBBER = new ReportScrubber(List.of("Steve12"), List.of("Meine Welt"));

    private static ReportStack stack(String message) {
        var e = new IllegalStateException(message);
        e.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("ai.ezfox.cccloud.web.WebHub", "tick", "WebHub.java", 5),
            new StackTraceElement("com.steve12.privatemod.Thing", "run", "Thing.java", 9)});
        return ReportStack.of(e);
    }

    @Test
    void aCrashHasExactlyTheListedFields() {
        var json = ReportPayload.crash(ENV, Where.WEB, stack("boom"), SCRUBBER);
        assertNotNull(json);
        assertEquals(Set.of("v", "fingerprint", "where", "env", "causes"), json.keySet());
        assertEquals(Set.of("mod", "neoforge", "mc", "java", "os"), json.getAsJsonObject("env").keySet());
        var cause = json.getAsJsonArray("causes").get(0).getAsJsonObject();
        assertEquals(Set.of("class", "message", "frames"), cause.keySet());
        assertEquals("web", json.get("where").getAsString());
    }

    @Test
    void aCrashIsScrubbed() {
        var json = ReportPayload.crash(ENV, Where.WEB, stack("Steve12 in Meine Welt at /home/steve/x from 10.0.0.1"), SCRUBBER);
        assertNotNull(json);
        String text = json.toString();
        for (String gone : List.of("Steve12", "steve12", "Meine Welt", "/home", "10.0.0.1", "privatemod.Thing.run")) assertFalse(text.contains(gone), gone + " in " + text);
        assertTrue(text.contains("ai.ezfox.cccloud.web.WebHub.tick:5"));
    }

    @Test
    void aForeignCrashIsNoReport() {
        var e = new IllegalStateException("x");
        e.setStackTrace(new StackTraceElement[] {new StackTraceElement("org.example.Other", "run", "Other.java", 1)});
        assertNull(ReportPayload.crash(ENV, Where.WEB, ReportStack.of(e), SCRUBBER));
    }

    @Test
    void anEmptyMessageIsLeftOut() {
        var json = ReportPayload.crash(ENV, Where.WEB, stack("   "), SCRUBBER);
        assertNotNull(json);
        assertFalse(json.getAsJsonArray("causes").get(0).getAsJsonObject().has("message"));
    }

    @Test
    void aMissingMessageIsLeftOut() {
        var json = ReportPayload.crash(ENV, Where.WEB, stack(null), SCRUBBER);
        assertNotNull(json);
        assertFalse(json.getAsJsonArray("causes").get(0).getAsJsonObject().has("message"));
    }

    @Test
    void anythingThatThrowsMeansNoReport() {
        assertNull(ReportPayload.crash(ENV, Where.WEB, null, SCRUBBER));
        assertNull(ReportPayload.crash(ENV, Where.WEB, stack("x"), null));
        assertNull(ReportPayload.crash(null, Where.WEB, stack("x"), SCRUBBER));
        assertNull(ReportPayload.feedback("bug", "x", null, null, null));
        assertNull(ReportPayload.stats(null, new Counts(1, 1, 1), "off", List.of()));
    }

    @Test
    void errorsAreSwallowedToo() {
        assertNull(ReportPayload.failClosed(() -> {
            throw new StackOverflowError();
        }));
        assertNull(ReportPayload.failClosed(() -> {
            throw new OutOfMemoryError();
        }));
    }

    @Test
    void badClassNamesDropTheReport() {
        var e = new IllegalStateException("x") {
        };
        e.setStackTrace(new StackTraceElement[] {new StackTraceElement("ai.ezfox.cccloud.Foo", "bar", "Foo.java", 1)});
        var stack = ReportStack.parse("Description: x\n\ncom.example.Bad: y\n\tat ai.ezfox.cccloud.Foo.bar(Foo.java:1)\n");
        assertNotNull(stack);
        assertNotNull(ReportPayload.crash(ENV, Where.WEB, stack, SCRUBBER));
        // a frame the format does not allow never reaches the service
        var hostile = new RuntimeException("x");
        hostile.setStackTrace(new StackTraceElement[] {new StackTraceElement("ai.ezfox.cccloud.Foo", "b r", "Foo.java", 1)});
        assertNull(ReportPayload.crash(ENV, Where.WEB, ReportStack.of(hostile), SCRUBBER));
    }

    @Test
    void statsHaveExactlyTheListedFields() {
        var json = ReportPayload.stats(ENV, new Counts(1, 12, 300), "connect", List.of("ae2", "powah"));
        assertNotNull(json);
        assertEquals(Set.of("v", "env", "mainframes", "uplinks", "cards", "internetAccess", "integrations"), json.keySet());
        assertEquals("1", json.get("mainframes").getAsString());
        assertEquals("6-20", json.get("uplinks").getAsString());
        assertEquals("101-500", json.get("cards").getAsString());
        assertEquals("connect", json.get("internetAccess").getAsString());
    }

    @Test
    void statsIgnoreWhatIsNotAModId() {
        var json = ReportPayload.stats(ENV, new Counts(0, 0, 0), "weird", List.of("powah", "Steve's Pack", "/home/x", "ae2", "ae2"));
        assertNotNull(json);
        assertEquals("[\"ae2\",\"powah\"]", json.getAsJsonArray("integrations").toString());
        assertEquals("off", json.get("internetAccess").getAsString());
    }

    @Test
    void unusualVersionsBecomeUnknown() {
        var json = ReportPayload.stats(new Env("/home/steve/dev", "x y", "26.1.2", 25, "beos"), new Counts(0, 0, 0), "off", List.of());
        assertNotNull(json);
        var env = json.getAsJsonObject("env");
        assertEquals("unknown", env.get("mod").getAsString());
        assertEquals("unknown", env.get("neoforge").getAsString());
        assertEquals("other", env.get("os").getAsString());
    }

    @Test
    void feedbackHasOnlyWhatWasGiven() {
        var bare = ReportPayload.feedback("idea", "more cards", "  ", null, SCRUBBER);
        assertNotNull(bare);
        assertEquals(Set.of("v", "category", "text"), bare.keySet());
        var full = ReportPayload.feedback("bug", "more cards", "me@example.org", ENV, SCRUBBER);
        assertNotNull(full);
        assertEquals(Set.of("v", "category", "text", "contact", "env"), full.keySet());
        assertEquals("me@example.org", full.get("contact").getAsString());
    }

    @Test
    void feedbackTextIsScrubbed() {
        var json = ReportPayload.feedback("bug", "Steve12 at /home/steve said 10.0.0.1", null, null, SCRUBBER);
        assertNotNull(json);
        assertEquals("<player> at <path>", json.get("text").getAsString().substring(0, 18));
        assertFalse(json.toString().contains("10.0.0.1"));
    }

    @Test
    void feedbackNeedsAKnownCategoryAndText() {
        assertNull(ReportPayload.feedback("rant", "x", null, null, SCRUBBER));
        assertNull(ReportPayload.feedback("bug", "   ", null, null, SCRUBBER));
    }

    @Test
    void feedbackIsCut() {
        var json = ReportPayload.feedback("bug", "word ".repeat(1000), "x".repeat(500), null, SCRUBBER);
        assertNotNull(json);
        assertTrue(json.get("text").getAsString().length() <= ReportPayload.MAX_FEEDBACK_TEXT + 1);
        assertTrue(json.get("contact").getAsString().length() <= ReportPayload.MAX_CONTACT + 1);
    }

    @Test
    void bucketsAreCoarse() {
        assertEquals("0", ReportPayload.bucket(-1));
        assertEquals("0", ReportPayload.bucket(0));
        assertEquals("1", ReportPayload.bucket(1));
        assertEquals("2-5", ReportPayload.bucket(2));
        assertEquals("2-5", ReportPayload.bucket(5));
        assertEquals("6-20", ReportPayload.bucket(6));
        assertEquals("6-20", ReportPayload.bucket(20));
        assertEquals("21-100", ReportPayload.bucket(21));
        assertEquals("21-100", ReportPayload.bucket(100));
        assertEquals("101-500", ReportPayload.bucket(101));
        assertEquals("101-500", ReportPayload.bucket(500));
        assertEquals("500+", ReportPayload.bucket(501));
    }

    @Test
    void operatingSystemFamilies() {
        assertEquals("windows", ReportPayload.osFamily("Windows 11"));
        assertEquals("macos", ReportPayload.osFamily("Mac OS X"));
        assertEquals("linux", ReportPayload.osFamily("Linux"));
        assertEquals("other", ReportPayload.osFamily("FreeBSD"));
        assertEquals("other", ReportPayload.osFamily(null));
    }

    @Test
    void bytesAreTheJson() {
        var json = ReportPayload.feedback("bug", "Grüße", null, null, SCRUBBER);
        assertNotNull(json);
        assertEquals(json.toString(), new String(ReportPayload.bytes(json), StandardCharsets.UTF_8));
    }
}
