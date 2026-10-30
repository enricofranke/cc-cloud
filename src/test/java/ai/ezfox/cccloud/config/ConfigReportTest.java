package ai.ezfox.cccloud.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The reload report: what changed, what was applied live, what needs a restart, and what failed. */
class ConfigReportTest {
    @Test
    void separatesLiveRestartAndFailed() {
        var report = new ConfigReport("command", List.of(
            new ConfigReport.Change("api.apiRequestsPerMinute", "60", "30", Reload.LIVE, true),
            new ConfigReport.Change("web.webPort", "8787", "80", Reload.LIVE, false),
            new ConfigReport.Change("example.startOnly", "1", "2", Reload.RESTART, true)),
            List.of("Public API: new settings in use"), List.of("[web] Could not listen on *:80 (Permission denied). The dashboard keeps running on *:8787"));
        assertEquals(List.of("api.apiRequestsPerMinute"), report.liveKeys());
        assertEquals(List.of("web.webPort"), report.failedKeys());
        assertEquals(List.of("example.startOnly"), report.restartKeys());
        var text = String.join("\n", report.lines());
        assertTrue(text.contains("3 value(s) changed"), text);
        assertTrue(text.contains("Applied live: api.apiRequestsPerMinute\n"), text);
        assertTrue(text.contains("Needs a restart: example.startOnly"), text);
        assertTrue(text.contains("Not applied (the previous values still hold, retried by the next reload): web.webPort"), text);
        assertTrue(text.contains("Error: [web] Could not listen on *:80"), text);
    }

    @Test
    void noChanges() {
        assertEquals(List.of("CC: Cloud config: no changed values."), ConfigReport.none("file").lines());
        var renewed = new ConfigReport("command", List.of(), List.of("Dashboard web server: same address, new rules for new connections"), List.of());
        assertEquals(2, renewed.lines().size(), "a renewed certificate is reported without a changed value");
    }
}
