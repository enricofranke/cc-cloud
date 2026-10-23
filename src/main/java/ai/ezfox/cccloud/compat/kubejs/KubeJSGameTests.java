package ai.ezfox.cccloud.compat.kubejs;

import static ai.ezfox.cccloud.ingame.InGameTestSupport.*;

import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.api.CloudEvents;
import ai.ezfox.cccloud.api.ScriptSectionGameTests;
import ai.ezfox.cccloud.api.ScriptSections;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonPrimitive;
import ai.ezfox.cccloud.compat.Integrations;
import dev.latvian.mods.kubejs.event.EventGroups;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * KubeJS: the plugin is loaded (event group {@code CCCloudEvents} with action/deviceLinked/alert), and the
 * {@code CCCloud} binding accepts what Rhino hands over (JS numbers as Integer, arrays as lists, objects as maps),
 * checks {@code layout.edit} for {@code as(player)} and validates like Lua. Then a round trip with the dev server script
 * {@code src/test/kubejs/cccloud_gametest.js}: an alert reaches {@code CCCloudEvents.alert}, the script publishes a
 * section, a dashboard action runs its {@code onAction} (and may be rejected), {@code CCCloudEvents.action} fires.
 */
public final class KubeJSGameTests {
    private static final List<CloudEvents.Alert> ALERTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean listening;

    private KubeJSGameTests() {
    }

    public static void init() {
        Integrations.test("kubejs_bindings", () -> KubeJSGameTests::bindings);
    }

    public static void bindings(GameTestHelper helper) {
        var setup = mainframe(helper, "kubejs_owner");
        var viewer = player(helper, "kubejs_viewer");
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                var group = EventGroups.ALL.get().map().get("CCCloudEvents");
                helper.assertTrue(group != null, "CCCloudEvents registered");
                helper.assertTrue(group.getHandlers().keySet().containsAll(List.of("action", "deviceLinked", "alert")), "handlers: " + group.getHandlers().keySet());
                grant(helper, setup, viewer, Role.VIEWER);
                var api = CCCloudJS.INSTANCE;
                String id = setup.id().toString();
                helper.assertTrue(api.mainframes().stream().anyMatch(m -> m.id().equals(id)), "mainframe listed");
                helper.assertTrue(api.mainframesOf(viewer).stream().anyMatch(m -> m.id().equals(id)), "viewer sees it");
                helper.assertTrue(api.has(setup.owner(), id, "layout.edit") && !api.has(viewer, id, "act.buttons"), "permission check");
                Map<Object, Object> gaugeOpts = new LinkedHashMap<>();
                gaugeOpts.put("max", 1200); // Rhino: Integer
                gaugeOpts.put("unit", "K");
                Map<Object, Object> selectOpts = new LinkedHashMap<>();
                selectOpts.put("options", List.of("Eco", "Boost")); // Rhino: NativeArray is a List
                api.section(id, "farm").title("Farm").gauge("temp", "Temperature", gaugeOpts).select("mode", "Mode", selectOpts).toggle("on", "On")
                    .publish();
                api.set(id, "farm", "temp", 300); // Integer
                api.set(id, "farm", "mode", "Boost");
                helper.assertTrue(Double.valueOf(300).equals(api.get(id, "farm", "temp")) && "Boost".equals(api.get(id, "farm", "mode")), "values set");
                boolean refused;
                try {
                    api.section(id, "farm2").as(viewer).toggle("x", "X").publish();
                    refused = false;
                } catch (ScriptSections.ScriptException e) {
                    refused = true;
                }
                helper.assertTrue(refused, "as(viewer) needs layout.edit");
                helper.assertTrue(api.sections(id).equals(List.of("farm")), "only the owner's section: " + api.sections(id));
                helper.assertTrue(api.remove(id, "farm"), "removed");
                // Round trip through the dev script src/test/kubejs/cccloud_gametest.js.
                if (!listening) {
                    listening = true;
                    CloudEvents.onAlert(ALERTS::add);
                }
                ALERTS.clear();
                WebHub.INSTANCE.publishAlert(setup.id(), null, "kubejs-ping", "info");
                CloudEvents.flush();
            })
            .thenWaitUntil(() -> helper.assertTrue(ScriptSections.keys(setup.id()).contains("pong")
                && Double.valueOf(42).equals(CCCloudJS.INSTANCE.get(setup.id().toString(), "pong", "level"))
                && String.valueOf(ai.ezfox.cccloud.web.InGameClients.section(setup.id(), ScriptSections.sectionId(setup.id(), "pong").toString()))
                    .contains("\"online\":true"), "the script published its section"))
            .thenExecute(() -> {
                var section = ScriptSections.sectionId(setup.id(), "pong");
                String lamp = ScriptSectionGameTests.act(helper, setup.owner(), setup.id(), section, "lamp", new JsonPrimitive(true));
                helper.assertTrue("ok".equals(lamp), "lamp: " + lamp);
                helper.assertTrue(Double.valueOf(100).equals(CCCloudJS.INSTANCE.get(setup.id().toString(), "pong", "level")), "onAction ran in JS");
                String boost = ScriptSectionGameTests.act(helper, setup.owner(), setup.id(), section, "mode", new JsonPrimitive("Boost"));
                helper.assertTrue("no_boost".equals(boost), "JS rejected: " + boost);
                String viewerLamp = ScriptSectionGameTests.act(helper, viewer, setup.id(), section, "lamp", new JsonPrimitive(false));
                helper.assertTrue("read_only".equals(viewerLamp), "viewer: " + viewerLamp);
                CloudEvents.flush();
            })
            .thenWaitUntil(() -> helper.assertTrue(ALERTS.stream().anyMatch(a -> a.text().equals("lamp by kubejs_owner")),
                "CCCloudEvents.action reached the script, which sent an alert: " + ALERTS))
            .thenExecute(() -> ScriptSections.remove(setup.id(), "pong"))
            .thenSucceed();
    }
}
