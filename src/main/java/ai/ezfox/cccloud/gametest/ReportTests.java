package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.command.ReportCommand;
import ai.ezfox.cccloud.gui.GuiNetwork;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.reports.ReportCapture;
import ai.ezfox.cccloud.reports.ReportConsent;
import ai.ezfox.cccloud.reports.ReportPayload;
import ai.ezfox.cccloud.reports.ReportService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * Reports in a running server, with a transport that records instead of sending: without the owner's yes nothing leaves
 * and no worker thread even starts, not with a failure, a due stats day, a crash file or {@code on} written into the
 * config; after {@code /cccloud reports on} the failure goes out clean, after {@code off} nothing again, and feedback
 * waits for its [Send]. Same batch as {@code internet_mode_switch} (it writes the config); everything is restored.
 */
public final class ReportTests {
    private static final int SETTLE_TICKS = 30;

    private ReportTests() {
    }

    private static Throwable failure(String message) {
        var e = new IllegalStateException(message);
        e.setStackTrace(new StackTraceElement[] {new StackTraceElement("ai.ezfox.cccloud.gametest.ReportTests", "failure" + message.length(), "ReportTests.java", 1)});
        return e;
    }

    private static boolean workerRuns() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals("cccloud-reports"));
    }

    private static Path crashFolder() throws IOException {
        var folder = Files.createTempDirectory("cccloud-crash-reports");
        var file = folder.resolve("crash-new.txt");
        Files.writeString(file, "Description: x\n\njava.lang.Error: boom\n\tat ai.ezfox.cccloud.Foo.bar(Foo.java:1)\n");
        return folder;
    }

    public static void noNetworkWithoutConsent(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "report_owner");
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        List<String> sent = new CopyOnWriteArrayList<>();
        var owner = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
        var admin = setup.owner().createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
        String crashBefore = CloudConfig.get(CloudConfig.CRASH_REPORTS);
        String statsBefore = CloudConfig.get(CloudConfig.USAGE_STATS);
        int versionBefore = CloudConfig.get(CloudConfig.CONSENT_VERSION);
        Runnable restore = () -> {
            CloudConfig.CRASH_REPORTS.set(crashBefore);
            CloudConfig.USAGE_STATS.set(statsBefore);
            CloudConfig.CONSENT_VERSION.set(versionBefore);
            CloudConfig.SPEC.save();
            ReportService.endForTest(server);
        };
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(setup.id()) != null, "mainframe registered"))
            .thenExecute(guarded(restore, () -> {
                CloudConfig.CRASH_REPORTS.set("ask");
                CloudConfig.USAGE_STATS.set("ask");
                CloudConfig.CONSENT_VERSION.set(0);
                ReportService.startForTest(server, (channel, body) -> sent.add(channel + " " + new String(body, java.nio.charset.StandardCharsets.UTF_8)));
                try {
                    helper.assertTrue(run(dispatcher, "cccloud reports status", admin) < 0, "op level 2 does not decide");
                    helper.assertTrue(run(dispatcher, "cccloud reports status", owner) == 1, "status for the owner");
                    helper.assertTrue(run(dispatcher, "cccloud reports show", owner) == 1, "the preview works without consent");
                    ReportCapture.failure(ReportPayload.Where.WEB, failure("first"));
                    ReportService.scanForTest(crashFolder());
                    ReportService.statsForTest();
                    // a file alone is no yes
                    CloudConfig.CRASH_REPORTS.set("on");
                    CloudConfig.USAGE_STATS.set("on");
                    CloudConfig.CONSENT_VERSION.set(ReportConsent.VERSION);
                    ReportCapture.failure(ReportPayload.Where.WEB, failure("second"));
                    ReportService.statsForTest();
                    helper.assertTrue(ReportService.state(ReportConsent.Channel.CRASH) == ReportConsent.State.ASK, "the config alone stays undecided");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }))
            .thenIdle(SETTLE_TICKS)
            .thenExecute(guarded(restore, () -> {
                helper.assertTrue(sent.isEmpty(), "nothing without consent: " + sent);
                helper.assertFalse(workerRuns(), "no thread without consent");
                helper.assertTrue("not_owner".equals(GuiNetwork.opsForTest(setup.owner(), setup.mainframe()).reports("crash:on")), "the screen asks the server too");
                helper.assertTrue(run(dispatcher, "cccloud reports on crash", owner) == 1, "yes to crash reports");
                ReportCapture.failure(ReportPayload.Where.WEB, failure("Steve at /home/steve/world said hello from 10.0.0.1"));
            }))
            .thenWaitUntil(() -> helper.assertTrue(sent.size() == 1, "one report: " + sent))
            .thenExecute(guarded(restore, () -> {
                String report = sent.getFirst();
                helper.assertTrue(report.startsWith("crash {"), report);
                for (String gone : List.of("/home/steve", "10.0.0.1")) helper.assertFalse(report.contains(gone), gone + " left in " + report);
                ReportService.statsForTest();
                helper.assertTrue(run(dispatcher, "cccloud reports off", owner) == 1, "withdrawn");
                ReportCapture.failure(ReportPayload.Where.WEB, failure("after the no"));
                ReportService.statsForTest();
                helper.assertTrue(run(dispatcher, "cccloud feedback the map is great", owner) == 1, "feedback is prepared");
            }))
            .thenIdle(SETTLE_TICKS)
            .thenExecute(guarded(restore, () -> {
                helper.assertTrue(sent.size() == 1, "nothing after the no, and feedback waits: " + sent);
                String id = ReportCommand.newestForTest();
                helper.assertTrue(id != null, "a message waits");
                helper.assertTrue(run(dispatcher, "cccloud feedback-send 00000000", owner) == 0, "an unknown id sends nothing");
                helper.assertTrue(run(dispatcher, "cccloud feedback-send " + id, owner) == 1, "confirmed");
                helper.assertTrue(run(dispatcher, "cccloud feedback-send " + id, owner) == 0, "a second click sends nothing");
            }))
            .thenWaitUntil(() -> helper.assertTrue(sent.size() == 2 && sent.getLast().startsWith("feedback {"), "one message: " + sent))
            .thenExecute(restore)
            .thenSucceed();
    }

    /** A failed assertion ends the sequence: the config is restored on that way out too. */
    private static Runnable guarded(Runnable restore, Runnable step) {
        return () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                restore.run();
                throw e;
            }
        };
    }

    private static int run(CommandDispatcher<CommandSourceStack> dispatcher, String command, CommandSourceStack source) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }
}
