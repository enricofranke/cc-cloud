package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.ezfox.cccloud.reports.ReportPayload.Counts;
import ai.ezfox.cccloud.reports.ReportPayload.Env;
import ai.ezfox.cccloud.reports.ReportPayload.Where;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The payloads as they leave the mod, against {@code reports-contract.json}: the report service tests the same file
 * (reports/test/fixtures/mod-payloads.json in the website repository), so the two cannot drift apart unnoticed. After a
 * format change copy {@code build/minecraft-junit/reports-contract.json} over both.
 */
class ReportContractTest {
    private static final Env ENV = new Env("0.17.0", "26.1.2.100", "26.1.2", 25, "linux");

    private static JsonObject payloads() {
        var scrubber = new ReportScrubber(List.of("Steve12"), List.of("Meine Welt"));
        var failure = new IllegalStateException("Could not save Meine Welt to /home/steve/world/level.dat from 192.168.1.20 for Steve12");
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("ai.ezfox.cccloud.web.WebHub", "tick", "WebHub.java", 120),
            new StackTraceElement("ai.ezfox.cccloud.web.WebHub$Inner", "lambda$run$0", "WebHub.java", 88),
            new StackTraceElement("dan200.computercraft.shared.Foo", "bar", "Foo.java", 7),
            new StackTraceElement("net.minecraft.server.MinecraftServer", "tickServer", "MinecraftServer.java", 900),
            new StackTraceElement("com.steve12.privatemod.Thing", "run", "Thing.java", 9)});
        var wrapper = new RuntimeException("wrapped", failure);
        wrapper.setStackTrace(new StackTraceElement[0]);
        var json = new JsonObject();
        json.add("crash", ReportPayload.crash(ENV, Where.DEVICE_CARD, ReportStack.of(wrapper), scrubber));
        json.add("stats", ReportPayload.stats(ENV, new Counts(1, 12, 300), "connect", List.of("ae2", "powah", "refinedstorage")));
        json.add("feedback", ReportPayload.feedback("bug", "The map of Steve12 flickers. Grüße!", "steve#1", ENV, scrubber));
        return json;
    }

    @Test
    void payloadsMatchTheContract() throws IOException {
        var json = payloads();
        Files.writeString(Path.of("reports-contract.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n");
        try (var in = ReportContractTest.class.getResourceAsStream("/reports-contract.json")) {
            assertEquals(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)), json);
        }
    }
}
