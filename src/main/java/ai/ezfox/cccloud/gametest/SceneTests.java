package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.scene.SceneService;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.10 scenes on a real mainframe: every step is checked with the presser's live rights (an operator's scene press
 * skips the dashboard step instead of escalating; saving such a scene is refused), delays are counted in server ticks,
 * a scene on a PIN widget needs the PIN, a rule runs a scene with its owner's rights (refused once the owner lost them)
 * and never a scene that needs the PIN.
 */
public final class SceneTests {
    static final BlockPos ANCHOR = new BlockPos(5, 1, 4);
    static final BlockPos UPLINK = new BlockPos(4, 1, 4);

    private SceneTests() {
    }

    /** A synthetic in-game device of a player (no ServerPlayer needed; rights come from the AccessResolver). */
    static AuthStore.Device device(UUID player, String name, UUID mainframeId) {
        long now = System.currentTimeMillis();
        return new AuthStore.Device(InGameClients.DEVICE_PREFIX + player, mainframeId, name + " (tablet)", true, now, now, player, name, "in-game", "");
    }

    public static void rightsDelaysPin(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(ANCHOR, Blocks.STONE);
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.EAST));
        var server = helper.getLevel().getServer();
        UUID operator = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        String[] uplinkId = {null};
        String[] night = {null};
        String[] vault = {null};
        long[] pressedAt = {0};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && mainframe.isEnabled(), "mainframe online"))
            .thenExecute(() -> {
                var uplink = uplink(helper);
                uplink.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                helper.assertTrue(uplink.beginLua(), "Lua may edit");
                try {
                    uplink.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "lamp", "Lamp", Map.of("value", true)));
                    uplink.putWidget(WidgetFactory.create(WidgetType.SLIDER, "rods", "Rods", Map.of("min", 0, "max", 10, "step", 1, "value", 0)));
                    uplink.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "vault", "Vault", Map.of("pin", true)));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                uplinkId[0] = uplink.getUplinkId().toString();
                var access = WebAuthData.access(server);
                access.setRole(mainframe.getMainframeId(), operator, "Opal", Role.OPERATOR);
                access.setRole(mainframe.getMainframeId(), admin, "Adam", Role.ADMIN);
            })
            .thenWaitUntil(() -> helper.assertTrue("toggle".equals(WebHub.INSTANCE.widgetType(mainframe.getMainframeId(), uplinkId[0], "vault")), "widgets published"))
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).isOnline(), "the uplink has a route to the mainframe"))
            .thenExecute(() -> {
                UUID mf = mainframe.getMainframeId();
                var ownerPerms = Access.resolver(server).resolve(owner.getUUID(), mf).perms();
                var saved = SceneService.set(server, mf, JsonParser.parseString("{\"name\":\"Night mode\",\"steps\":["
                    + "{\"type\":\"widget\",\"uplinkId\":\"" + uplinkId[0] + "\",\"widgetId\":\"lamp\",\"value\":false},"
                    + "{\"type\":\"delay\",\"seconds\":1},"
                    + "{\"type\":\"widget\",\"uplinkId\":\"" + uplinkId[0] + "\",\"widgetId\":\"rods\",\"value\":3},"
                    + "{\"type\":\"dashboard\",\"enabled\":false},"
                    + "{\"type\":\"alert\",\"text\":\"Good night\",\"level\":\"info\"}]}"), owner.getUUID(), "Owner", ownerPerms);
                helper.assertTrue(saved.error() == null, "owner saves the scene: " + saved.error() + " " + saved.detail());
                night[0] = saved.id();
                var pin = SceneService.set(server, mf, JsonParser.parseString("{\"name\":\"Open vault\",\"steps\":[{\"type\":\"widget\",\"uplinkId\":\""
                    + uplinkId[0] + "\",\"widgetId\":\"vault\",\"value\":true}]}"), owner.getUUID(), "Owner", ownerPerms);
                helper.assertTrue(pin.error() == null, "PIN scene saved: " + pin.error());
                vault[0] = pin.id();
                helper.assertTrue(SceneService.needsPin(mf, SceneService.scene(mf, vault[0])), "a step on a PIN widget makes the scene ask for the PIN");

                // No escalation when saving: an operator who may edit rules still cannot put a power.toggle step in a scene.
                WebAuthData.access(server).setOverrides(mf, operator, new MainframeAccess.Overrides(Set.of(Perm.RULES_EDIT), Set.of()));
                var operatorPerms = Access.resolver(server).resolve(operator, mf).perms();
                var refused = SceneService.set(server, mf, JsonParser.parseString("{\"name\":\"Lights out\",\"steps\":[{\"type\":\"dashboard\",\"enabled\":false}]}"),
                    operator, "Opal", operatorPerms);
                helper.assertTrue("not_allowed".equals(refused.error()) && "step 1: power.toggle".equals(refused.detail()),
                    "the saver needs every step's permission: " + refused.error() + " " + refused.detail());
            })
            .thenWaitUntil(() -> helper.assertTrue(SceneService.section(mainframe.getMainframeId()) != null, "the Scenes section is published"))
            .thenExecute(() -> {
                UUID mf = mainframe.getMainframeId();
                var section = SceneService.section(mf);
                var snapshot = WebHub.INSTANCE.sectionSnapshot(mf, section.sectionId().toString());
                helper.assertTrue(snapshot != null && "scenes".equals(snapshot.get("template").getAsString()), "Scenes card on the dashboard: " + snapshot);
                helper.assertTrue(WebHub.INSTANCE.widgetNeedsPin(mf, section.sectionId().toString(), "s_" + vault[0]), "the vault button asks for the PIN");
                // The operator presses Night mode: lamp off right away, rods after the delay, the dashboard step is refused.
                var caller = SceneService.Caller.of(device(operator, "Opal", mf));
                var result = section.press("s_" + night[0], new JsonPrimitive(true), caller, false);
                helper.assertTrue(result.ok(), "started: " + result.error());
                pressedAt[0] = helper.getTick();
                helper.assertTrue(!uplink(helper).getWidget("lamp").value().getAsBoolean(), "steps before the first delay run in the press tick");
                helper.assertTrue(uplink(helper).getWidget("rods").value().getAsDouble() == 0, "the delay holds the next step");
                var again = section.press("s_" + night[0], new JsonPrimitive(true), caller, false);
                helper.assertTrue("busy".equals(again.error()), "the same scene never runs twice at once: " + again.error());
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).getWidget("rods").value().getAsDouble() == 3, "rods set after the delay"))
            .thenExecute(() -> {
                UUID mf = mainframe.getMainframeId();
                helper.assertTrue(helper.getTick() - pressedAt[0] >= 20, "1 s delay = 20 server ticks, waited " + (helper.getTick() - pressedAt[0]));
                var run = SceneService.lastRun(mf, night[0]);
                helper.assertTrue(run != null && run.done(), "run finished");
                var results = run.results();
                helper.assertTrue("ok".equals(results[0]) && "ok".equals(results[1]) && "ok".equals(results[2]) && "not_allowed".equals(results[3])
                    && "ok".equals(results[4]), "per-step results: " + String.join(",", java.util.Arrays.stream(results).map(String::valueOf).toList()));
                helper.assertTrue(mainframe.isEnabled(), "the operator could not switch the dashboard off through a scene");
                helper.assertTrue("tablet".equals(run.source()), "source: " + run.source());
                var doc = SceneService.document(server, mf);
                helper.assertTrue(doc.getAsJsonArray("log").size() == 1 && "not_allowed".equals(doc.getAsJsonArray("log").get(0).getAsJsonObject().get("result").getAsString()),
                    "logged with the first error: " + doc.get("log"));

                // PIN: refused without, runs with a verified PIN.
                var section = SceneService.section(mf);
                var caller = SceneService.Caller.of(device(operator, "Opal", mf));
                var noPin = section.press("s_" + vault[0], new JsonPrimitive(true), caller, false);
                helper.assertTrue("pin_required".equals(noPin.error()), "no PIN: " + noPin.error());
                helper.assertTrue(!uplink(helper).getWidget("vault").value().getAsBoolean(), "vault still closed");
                var withPin = section.press("s_" + vault[0], new JsonPrimitive(true), caller, true);
                helper.assertTrue(withPin.ok(), "with the PIN: " + withPin.error());
                helper.assertTrue(uplink(helper).getWidget("vault").value().getAsBoolean(), "vault opened");

                // Rules: never a scene that needs the PIN; a scene runs with its owner's rights.
                var pinRule = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"vault\",\"when\":{\"type\":\"interval\",\"minutes\":60},"
                    + "\"then\":[{\"type\":\"scene\",\"sceneId\":\"" + vault[0] + "\"}]}"));
                helper.assertTrue("pin_widget".equals(pinRule.error()), "a rule cannot run a PIN scene: " + pinRule.error());
                var adminPerms = Access.resolver(server).resolve(admin, mf).perms();
                var adminScene = SceneService.set(server, mf, JsonParser.parseString("{\"name\":\"Lamp on\",\"steps\":[{\"type\":\"widget\",\"uplinkId\":\""
                    + uplinkId[0] + "\",\"widgetId\":\"lamp\",\"value\":true}]}"), admin, "Adam", adminPerms);
                helper.assertTrue(adminScene.error() == null, "admin saves a scene: " + adminScene.error());
                var rule = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"lamp\",\"when\":{\"type\":\"interval\",\"minutes\":60},"
                    + "\"then\":[{\"type\":\"scene\",\"sceneId\":\"" + adminScene.id() + "\"}]}"));
                helper.assertTrue(rule.error() == null, "rule with a scene action: " + rule.error());
                helper.assertTrue(RuleService.runNow(server, mf, rule.id()) == null, "the rule runs the scene");
                helper.assertTrue(uplink(helper).getWidget("lamp").value().getAsBoolean(), "lamp on through the rule (owner Adam's rights)");
                helper.assertTrue("rule".equals(SceneService.lastRun(mf, adminScene.id()).source()), "source rule");
                // The scene's owner loses their role: the rule's run of it is refused.
                WebAuthData.access(server).setRole(mf, admin, "Adam", Role.NONE);
                uplink(helper).setValue("lamp", new JsonPrimitive(false));
                helper.assertTrue("not_allowed".equals(RuleService.runNow(server, mf, rule.id())), "no rights, no run");
                helper.assertTrue(!uplink(helper).getWidget("lamp").value().getAsBoolean(), "lamp stays off");
                RuleService.forget(server, mf);
            })
            .thenSucceed();
    }

    static CloudUplinkBlockEntity uplink(GameTestHelper helper) {
        return (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
    }
}
