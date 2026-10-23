package ai.ezfox.cccloud.api;

import static ai.ezfox.cccloud.ingame.InGameTestSupport.*;

import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.InGameClients;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Script sections (the KubeJS API's core): widgets validated like Lua, values validated like {@code cloud.set}, the
 * section shows up on the dashboard, and dashboard actions pass the per-widget permission (viewer read-only), the
 * action validation and the script's handler (which may reject), then a {@link CloudEvents.Action} is posted.
 */
public final class ScriptSectionGameTests {
    private static final List<CloudEvents.Action> EVENTS = new CopyOnWriteArrayList<>();
    private static boolean listening;

    private ScriptSectionGameTests() {
    }

    private static @Nullable String error(Runnable r) {
        try {
            r.run();
            return null;
        } catch (ScriptSections.ScriptException e) {
            return e.getMessage();
        }
    }

    public static @Nullable String act(GameTestHelper helper, ServerPlayer player, UUID mainframeId, UUID sectionId, String widget, JsonElement value) {
        var device = InGameClients.device(player, mainframeId, "gametest");
        var action = new ClientMessages.Action("t", sectionId, widget, value, null, null);
        ActionResult result = InGameClients.action(helper.getLevel().getServer(), device, action, false).getNow(null);
        return result == null ? "pending" : result.ok() ? "ok" : result.error();
    }

    public static void sections(GameTestHelper helper) {
        if (!listening) {
            listening = true;
            CloudEvents.onAction(EVENTS::add);
        }
        EVENTS.clear();
        var setup = mainframe(helper, "script_owner");
        var viewer = player(helper, "script_viewer");
        var handled = new ArrayList<String>();
        UUID[] section = new UUID[1];
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                grant(helper, setup, viewer, Role.VIEWER);
                var id = setup.id();
                helper.assertTrue(error(() -> ScriptSections.publish(id, "Bad Key", "x", List.of(), false, null)) != null, "key checked");
                helper.assertTrue(error(() -> ScriptSections.publish(id, "s", "x",
                    List.of(new ScriptSections.WidgetSpec("item_search", "a", "A", null)), false, null)) != null, "bridge-only types refused");
                helper.assertTrue(error(() -> ScriptSections.publish(id, "s", "x",
                    List.of(new ScriptSections.WidgetSpec("gauge", "bad id!", "A", null)), false, null)) != null, "widget ids checked like Lua");
                helper.assertTrue(error(() -> ScriptSections.publish(id, "s", "x",
                    List.of(new ScriptSections.WidgetSpec("gauge", "a", "A", Map.of("colour", "red"))), false, null)) != null, "unknown options refused");
                section[0] = ScriptSections.publish(id, "reactor", "Reactor", List.of(
                    new ScriptSections.WidgetSpec("gauge", "temp", "Temperature", Map.of("max", 1200.0, "unit", "K")),
                    new ScriptSections.WidgetSpec("toggle", "pumps", "Coolant pumps", null),
                    new ScriptSections.WidgetSpec("select", "mode", "Mode", Map.of("options", Map.of(1.0, "Eco", 2.0, "Boost"))),
                    new ScriptSections.WidgetSpec("button", "scram", "SCRAM", null),
                    new ScriptSections.WidgetSpec("label", "note", "Note", null)), false, ctx -> {
                        handled.add(ctx.widgetId() + "=" + ctx.value());
                        return ctx.widgetId().equals("scram") ? "busy" : null;
                    });
                ScriptSections.set(id, "reactor", "temp", 812.0);
                ScriptSections.set(id, "reactor", "note", "all fine");
                helper.assertTrue(error(() -> ScriptSections.set(id, "reactor", "pumps", "yes")) != null, "values checked like cloud.set");
                helper.assertTrue(error(() -> ScriptSections.set(id, "reactor", "mode", "Turbo")) != null, "select value must be an option");
                helper.assertTrue(error(() -> ScriptSections.alert(id, "reactor", "x", "panic")) != null, "alert level checked");
            })
            .thenWaitUntil(() -> {
                var snapshot = InGameClients.section(setup.id(), section[0].toString());
                helper.assertTrue(snapshot != null && snapshot.toString().contains("\"Reactor\"") && snapshot.toString().contains("812"),
                    "section with its value on the dashboard: " + snapshot);
            })
            .thenExecute(() -> {
                var id = setup.id();
                helper.assertTrue("ok".equals(act(helper, setup.owner(), id, section[0], "pumps", new JsonPrimitive(true))), "owner switches the pumps");
                helper.assertTrue(Boolean.TRUE.equals(ScriptSections.value(id, "reactor", "pumps").getAsBoolean()), "toggle keeps the accepted value");
                helper.assertTrue("busy".equals(act(helper, setup.owner(), id, section[0], "scram", new JsonPrimitive(true))), "handler may reject");
                helper.assertTrue("invalid_value".equals(act(helper, setup.owner(), id, section[0], "mode", new JsonPrimitive("Turbo"))),
                    "action validation before the handler");
                String viewerResult = act(helper, viewer, id, section[0], "pumps", new JsonPrimitive(false));
                helper.assertTrue("read_only".equals(viewerResult), "viewers lack act.buttons: " + viewerResult);
                helper.assertTrue(handled.equals(List.of("pumps=true", "scram=true")), "handler saw only valid, permitted actions: " + handled);
                CloudEvents.flush();
                var mine = EVENTS.stream().filter(e -> e.mainframeId().equals(id)).toList(); // other tests run at the same time
                helper.assertTrue(mine.size() == 1 && mine.getFirst().widgetId().equals("pumps")
                    && mine.getFirst().playerName().equals("script_owner"), "one action event for the accepted action: " + mine);
                // Republishing keeps values of widgets with the same id and type.
                ScriptSections.publish(id, "reactor", "Reactor 2", List.of(new ScriptSections.WidgetSpec("toggle", "pumps", "Pumps", null)), false, null);
                helper.assertTrue(ScriptSections.value(id, "reactor", "pumps").getAsBoolean(), "value kept on republish");
                helper.assertTrue(ScriptSections.remove(id, "reactor"), "removed");
                helper.assertTrue(ScriptSections.keys(id).isEmpty(), "no sections left");
            })
            .thenSucceed();
    }
}
