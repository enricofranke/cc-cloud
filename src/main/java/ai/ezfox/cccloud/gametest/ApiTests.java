package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.compat.server.MainframeSection;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.publicapi.ApiKeyData;
import ai.ezfox.cccloud.publicapi.ApiKeys;
import ai.ezfox.cccloud.publicapi.ApiScope;
import ai.ezfox.cccloud.publicapi.ApiV1;
import ai.ezfox.cccloud.scene.SceneService;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.AccessApi;
import ai.ezfox.cccloud.web.Router;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonParser;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.10 public API on a real mainframe, through the server-thread handler ({@link ApiV1}; the HTTP layer – key in the
 * URL, expiry, IP list, rate limits, CORS, off by default – is unit tested in ApiHttpTest): a request needs the scope
 * and the key owner's live permission (a viewer's key with {@code action:widgets} still cannot act), restricted cards
 * only with {@code view.positions}, PIN widgets and PIN scenes are refused, a write is audited, other mainframes do not
 * exist for a key, and a banned owner or one who lost the role makes the key useless.
 */
public final class ApiTests {
    private ApiTests() {
    }

    private static AccessApi.Response call(GameTestHelper helper, ApiKeys.Key key, Router.Id route, String path, String body) {
        return ApiV1.handle(helper.getLevel().getServer(), key, route, path, Map.of(), body, "127.0.0.1");
    }

    public static void scopesAndRights(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(SceneTests.ANCHOR, Blocks.STONE);
        helper.setBlock(SceneTests.UPLINK, ai.ezfox.cccloud.ModRegistry.CLOUD_UPLINK.get().defaultBlockState()
            .setValue(ai.ezfox.cccloud.block.CloudUplinkBlock.FACING, net.minecraft.core.Direction.EAST));
        var server = helper.getLevel().getServer();
        UUID viewer = UUID.randomUUID();
        String[] uplinkId = {null};
        ApiKeys.Key[] keys = new ApiKeys.Key[4];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline() && mainframe.isEnabled(), "mainframe online"))
            .thenExecute(() -> {
                var uplink = SceneTests.uplink(helper);
                uplink.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                helper.assertTrue(uplink.beginLua(), "Lua may edit");
                try {
                    uplink.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "lamp", "Lamp", Map.of("value", false)));
                    uplink.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "vault", "Vault", Map.of("pin", true)));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                uplinkId[0] = uplink.getUplinkId().toString();
                UUID mf = mainframe.getMainframeId();
                WebAuthData.access(server).setRole(mf, viewer, "Vera", Role.VIEWER);
                var store = ApiKeyData.get(server).keys();
                keys[0] = store.create(owner.getUUID(), "Owner", mf, "read", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of()).key();
                keys[1] = store.create(owner.getUUID(), "Owner", mf, "bot", EnumSet.of(ApiScope.ACTION_WIDGETS, ApiScope.RUN_SCENES, ApiScope.READ_STATS), 0,
                    List.of()).key();
                keys[2] = store.create(viewer, "Vera", mf, "viewer bot", EnumSet.allOf(ApiScope.class), 0, List.of()).key();
                // A scene the API may run, and one that needs the PIN.
                var perms = Access.resolver(server).resolve(owner.getUUID(), mf).perms();
                SceneService.set(server, mf, JsonParser.parseString("{\"name\":\"Lamp off\",\"steps\":[{\"type\":\"widget\",\"uplinkId\":\"" + uplinkId[0]
                    + "\",\"widgetId\":\"lamp\",\"value\":false}]}"), owner.getUUID(), "Owner", perms);
                SceneService.set(server, mf, JsonParser.parseString("{\"name\":\"Vault\",\"pin\":true,\"steps\":[{\"type\":\"alert\",\"text\":\"x\"}]}"),
                    owner.getUUID(), "Owner", perms);
            })
            .thenWaitUntil(() -> helper.assertTrue("toggle".equals(WebHub.INSTANCE.widgetType(mainframe.getMainframeId(), uplinkId[0], "vault")), "widgets published"))
            .thenWaitUntil(() -> helper.assertTrue(SceneTests.uplink(helper).isOnline(), "the uplink has a route to the mainframe"))
            .thenWaitUntil(() -> helper.assertTrue(WebHub.INSTANCE.isRestricted(mainframe.getMainframeId(),
                MainframeSection.sectionId(mainframe.getMainframeId(), "server").toString()), "the restricted Server card is published"))
            .thenExecute(() -> {
                UUID mf = mainframe.getMainframeId();
                String base = "/api/v1/mainframes/" + mf;
                String serverCard = MainframeSection.sectionId(mf, "server").toString();
                // Reading: the owner's read key sees the restricted Server card, the viewer's key does not.
                var sections = call(helper, keys[0], Router.Id.API_SECTIONS, base + "/sections", "");
                helper.assertTrue(sections.status() == 200 && sections.body().toString().contains(serverCard) && sections.body().toString().contains(uplinkId[0]),
                    "owner sections: " + sections.body());
                var viewerSections = call(helper, keys[2], Router.Id.API_SECTIONS, base + "/sections", "");
                helper.assertTrue(viewerSections.status() == 200 && !viewerSections.body().toString().contains(serverCard)
                    && viewerSections.body().toString().contains(uplinkId[0]), "viewer: no restricted cards: " + viewerSections.body());
                // Scope missing: the read key cannot act, even though its owner could.
                String widget = base + "/sections/" + uplinkId[0] + "/widgets/lamp";
                helper.assertTrue(error(call(helper, keys[0], Router.Id.API_WIDGET, widget, "{\"value\":true}")).equals("missing_scope"), "no action:widgets scope");
                // Scope ∩ rights: the viewer's key has every scope, but a viewer cannot act.
                helper.assertTrue(error(call(helper, keys[2], Router.Id.API_WIDGET, widget, "{\"value\":true}")).equals("not_allowed"), "viewer key cannot act");
                helper.assertTrue(error(call(helper, keys[2], Router.Id.API_SCENE_RUN, base + "/scenes/" + sceneId(mf, "Lamp off") + "/run", "")).equals("not_allowed"),
                    "viewer key cannot run scenes");
                helper.assertTrue(!SceneTests.uplink(helper).getWidget("lamp").value().getAsBoolean(), "lamp untouched");
                // The owner's bot acts; it is audited.
                var set = call(helper, keys[1], Router.Id.API_WIDGET, widget, "{\"value\":true}");
                helper.assertTrue(set.status() == 200, "bot sets the lamp: " + set.body());
                helper.assertTrue(SceneTests.uplink(helper).getWidget("lamp").value().getAsBoolean(), "lamp on");
                helper.assertTrue(WebAuthData.access(server).audit(mf).stream().anyMatch(e -> e.text().contains("API key 'bot'")), "audited");
                helper.assertTrue(error(call(helper, keys[1], Router.Id.API_WIDGET, widget, "{\"value\":\"yes\"}")).equals("invalid_value"), "validated like a dashboard action");
                // No PIN through the API.
                var vault = call(helper, keys[1], Router.Id.API_WIDGET, base + "/sections/" + uplinkId[0] + "/widgets/vault", "{\"value\":true}");
                helper.assertTrue(vault.status() == 403 && error(vault).equals("pin_required"), "PIN widget refused: " + vault.body());
                helper.assertTrue(!SceneTests.uplink(helper).getWidget("vault").value().getAsBoolean(), "vault untouched");
                var pinScene = call(helper, keys[1], Router.Id.API_SCENE_RUN, base + "/scenes/" + sceneId(mf, "Vault") + "/run", "");
                helper.assertTrue(pinScene.status() == 403 && error(pinScene).equals("pin_required"), "PIN scene refused: " + pinScene.body());
                // A scene runs as the key's player.
                var run = call(helper, keys[1], Router.Id.API_SCENE_RUN, base + "/scenes/" + sceneId(mf, "Lamp off") + "/run", "");
                helper.assertTrue(run.status() == 200, "scene run: " + run.body());
                helper.assertTrue(!SceneTests.uplink(helper).getWidget("lamp").value().getAsBoolean(), "the scene switched the lamp off");
                helper.assertTrue("api".equals(SceneService.lastRun(mf, sceneId(mf, "Lamp off")).source()), "source api");
                helper.assertTrue(call(helper, keys[1], Router.Id.API_PRODUCTION, base + "/production", "").status() == 200, "read:stats");
                // Another mainframe does not exist for a key.
                helper.assertTrue(call(helper, keys[0], Router.Id.API_SECTIONS, "/api/v1/mainframes/" + UUID.randomUUID() + "/sections", "").status() == 404,
                    "foreign mainframe");
                // A banned owner: the key is dead; unbanned it works again.
                var name = new NameAndId(viewer, "Vera");
                server.getPlayerList().getBans().add(new UserBanListEntry(name));
                helper.assertTrue(error(call(helper, keys[2], Router.Id.API_SECTIONS, base + "/sections", "")).equals("no_access"), "banned");
                server.getPlayerList().getBans().remove(name);
                helper.assertTrue(call(helper, keys[2], Router.Id.API_SECTIONS, base + "/sections", "").status() == 200, "unbanned");
                // The owner loses the role: the key is dead.
                WebAuthData.access(server).setRole(mf, viewer, "Vera", Role.NONE);
                helper.assertTrue(error(call(helper, keys[2], Router.Id.API_SECTIONS, base + "/sections", "")).equals("no_access"), "no role, no access");
                var me = call(helper, keys[0], Router.Id.API_ME, "/api/v1/me", "");
                helper.assertTrue(me.status() == 200 && "read".equals(me.body().getAsJsonObject("key").get("name").getAsString()), "me: " + me.body());
                ApiKeyData.get(server).keys().forgetMainframe(mf);
                helper.assertTrue(ApiKeyData.get(server).keys().forMainframe(mf).isEmpty(), "keys of a broken mainframe are deleted");
            })
            .thenSucceed();
    }

    private static String sceneId(UUID mainframeId, String name) {
        for (var scene : SceneService.scenes(mainframeId)) if (scene.name().equals(name)) return scene.id();
        throw new IllegalStateException("no scene " + name);
    }

    private static String error(AccessApi.Response response) {
        return response.body().has("error") ? response.body().get("error").getAsString() : "";
    }
}
