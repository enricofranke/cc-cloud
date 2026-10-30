package ai.ezfox.cccloud.web.internet;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The text of {@code /cccloud internet}, rendered with the real English and German language files. */
class InternetAdviceTest {
    private static JsonObject lang(String name) {
        var in = InternetAdviceTest.class.getResourceAsStream("/assets/cccloud/lang/" + name + ".json");
        assertNotNull(in, name);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static final JsonObject EN = lang("en_us");
    private static final JsonObject DE = lang("de_de");

    /** Like Minecraft's translatable components: %s and %1$s placeholders. */
    static String render(JsonObject lang, List<InternetAdvice.Line> lines) {
        return lines.stream().map(l -> {
            assertTrue(lang.has(l.key()), "missing translation " + l.key());
            assertTrue(DE.has(l.key()) && EN.has(l.key()), "both languages: " + l.key());
            return String.format(lang.get(l.key()).getAsString(), l.args().toArray());
        }).collect(Collectors.joining("\n"));
    }

    private static InternetAdvice.Facts facts(InternetMode configured, InternetMode running, String domain, String publicIp, List<String> withoutPin) {
        return new InternetAdvice.Facts(configured, running, domain, 8787, "192.168.1.20", publicIp, withoutPin, 24, false);
    }

    @Test
    void switchingToHttpSaysWhatToDo() {
        var lines = InternetAdvice.switched(facts(InternetMode.HTTP, InternetMode.OFF, "", "203.0.113.5", List.of()));
        String text = render(EN, lines);
        assertTrue(text.contains("plain HTTP – INSECURE"), text);
        assertTrue(text.contains("Not active yet"), "the running mode is not the configured one: " + text);
        assertTrue(text.contains("1. Open from anywhere: http://203.0.113.5:8787"), text);
        assertTrue(text.contains("asked at https://api.ipify.org): 203.0.113.5"), text);
        assertTrue(text.contains("forward TCP port 8787 to 192.168.1.20 port 8787"), text);
        assertTrue(text.contains("phone with Wi-Fi switched off"), "the self-test without third-party port checkers: " + text);
        assertTrue(text.contains("can read logins and the PIN"), text);
        assertTrue(text.contains("unused for 24 h"), text);
        assertEquals(InternetAdvice.Style.LINK, lines.stream().filter(l -> l.key().endsWith("step.open")).findFirst().orElseThrow().style());
        assertTrue(render(DE, lines).contains("Leite im Router den TCP-Port 8787 an 192.168.1.20 Port 8787 weiter"), render(DE, lines));

        // applied live (the usual case): no such line
        assertFalse(render(EN, InternetAdvice.switched(facts(InternetMode.HTTP, InternetMode.HTTP, "", "203.0.113.5", List.of()))).contains("Not active"));
    }

    @Test
    void aDomainReplacesTheIpAndAnUnknownIpIsAPlaceholder() {
        String named = render(EN, InternetAdvice.switched(facts(InternetMode.HTTP, InternetMode.OFF, "mybase.duckdns.org", null, List.of())));
        assertTrue(named.contains("http://mybase.duckdns.org:8787"), named);
        assertFalse(named.contains("public IP"), "no lookup with a domain: " + named);
        String unknown = render(EN, InternetAdvice.switched(facts(InternetMode.HTTP, InternetMode.OFF, "", null, List.of())));
        assertTrue(unknown.contains("http://<public-ip>:8787"), unknown);
        assertTrue(unknown.contains("could not be found"), unknown);
    }

    @Test
    void refusedWithoutAPin() {
        String text = render(EN, InternetAdvice.refusedNoPin(List.of("Base (1 64 2)", "Alex/1a2b3c4d"), 3));
        assertTrue(text.startsWith("Refused: plain HTTP needs a dashboard PIN on every mainframe"), text);
        assertTrue(text.contains("Without a PIN: Base (1 64 2), Alex/1a2b3c4d."), text);
        assertTrue(text.contains("Set a PIN: /cccloud pin set <4-8 digits>"), text);
        assertTrue(render(EN, InternetAdvice.refusedNoPin(List.of(), 0)).contains("place a Cloud Mainframe and set its PIN first"));
        assertEquals("a, b, c, d, e +2", InternetAdvice.names(List.of("a", "b", "c", "d", "e", "f", "g")));
    }

    @Test
    void offAndStatus() {
        String off = render(EN, InternetAdvice.switched(facts(InternetMode.OFF, InternetMode.HTTP, "", null, List.of())));
        assertTrue(off.contains("Internet access: off"), off);
        assertTrue(off.contains("Not active yet"), off);
        var legacy = new InternetAdvice.Facts(InternetMode.OFF, InternetMode.OFF, "", 8787, null, null, List.of(), 24, true);
        assertTrue(render(EN, InternetAdvice.switched(legacy)).contains("webAllowInternet = true is still set"));

        String status = render(EN, InternetAdvice.status(facts(InternetMode.HTTP, InternetMode.HTTP, "", null, List.of("Base (1 64 2)")),
            "http://203.0.113.5:8787"));
        assertTrue(status.contains("Internet access: http"), status);
        assertTrue(status.contains("Link from the internet: http://203.0.113.5:8787"), status);
        assertTrue(status.contains("Without a PIN (only watching from the internet, no control): Base (1 64 2)"), status);
        String pending = render(EN, InternetAdvice.status(facts(InternetMode.HTTP, InternetMode.OFF, "", null, List.of()), ""));
        assertTrue(pending.contains("Configured: http – not active yet"), pending);
        assertTrue(pending.contains("Only the local network"), pending);
    }

    @Test
    void publicIpAnswers() {
        assertEquals("203.0.113.5", PublicIp.parse("203.0.113.5\n"));
        assertNull(PublicIp.parse("192.168.1.1"), "never a LAN address");
        assertNull(PublicIp.parse("<html>"));
        assertNull(PublicIp.parse("999.1.1.1"));
        assertNull(PublicIp.parse("0.1.2.3"));
        assertNull(PublicIp.parse("239.1.2.3"));
        assertNull(PublicIp.parse(null));
    }
}
