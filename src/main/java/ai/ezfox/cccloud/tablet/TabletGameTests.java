package ai.ezfox.cccloud.tablet;

import static ai.ezfox.cccloud.ingame.InGameTestSupport.*;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.ingame.InGameRange;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import ai.ezfox.cccloud.network.NetworkService;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cloud Tablet: coverage (inside the mainframe's device range, not 300 blocks away; the advanced tablet anywhere),
 * rights (strangers cannot open it), the session gets the snapshot, and actions through the tablet go through the
 * browsers' validation: operators switch a toggle, viewers get read_only, PIN widgets need the PIN, and moving out
 * of coverage closes the tablet.
 */
public final class TabletGameTests {
    /** Data sent to fake players (they have no connection), per player. */
    static final Map<UUID, List<String>> CAPTURED = new ConcurrentHashMap<>();

    private TabletGameTests() {
    }

    static void capture() {
        TabletService.sender = (player, data) -> {
            if (player instanceof FakePlayer) CAPTURED.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(data.messages());
            else PacketDistributor.sendToPlayer(player, data);
        };
    }

    /** Opens a tablet for a fake player (menu assigned directly; a fake player cannot open screens). */
    static TabletMenu open(GameTestHelper helper, ServerPlayer player, UUID mainframeId, ItemStack tablet, int containerId) {
        player.getInventory().setItem(0, tablet);
        var menu = new TabletMenu(containerId, player.blockPosition(), "Mainframe", tablet.getItem() == InGameRegistry.ADVANCED_CLOUD_TABLET.get(), true);
        player.containerMenu = menu;
        TabletService.attach(player, menu, mainframeId, menu.isAdvanced());
        return menu;
    }

    static boolean received(ServerPlayer player, String needle) {
        var list = CAPTURED.get(player.getUUID());
        if (list == null) return false;
        synchronized (list) {
            return list.stream().anyMatch(m -> m.contains(needle));
        }
    }

    static String result(ServerPlayer player, String reqId) {
        var list = CAPTURED.get(player.getUUID());
        if (list == null) return null;
        synchronized (list) {
            for (var batch : list) {
                for (var element : JsonParser.parseString(batch).getAsJsonArray()) {
                    var m = element.getAsJsonObject();
                    if (m.has("reqId") && reqId.equals(m.get("reqId").getAsString())) return m.has("error") ? m.get("error").getAsString() : "ok";
                }
            }
        }
        return null;
    }

    public static void range(GameTestHelper helper) {
        capture();
        var setup = mainframe(helper, "tablet_range_owner");
        var stranger = player(helper, "tablet_range_stranger");
        var level = helper.getLevel();
        var tablet = new ItemStack(InGameRegistry.CLOUD_TABLET.get());
        tablet.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
        var near = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 1)).getCenter();
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertTrue(NetworkService.result(setup.id()) != null, "network topology computed");
                helper.assertTrue(InGameRange.covered(setup.id(), level, near), "covered next to the mainframe");
                // the Server card has its first values (refreshed once per second of game time)
                var card = ai.ezfox.cccloud.compat.server.MainframeCards.section(setup.id(), ai.ezfox.cccloud.compat.server.ServerCard.KIND);
                helper.assertTrue(card != null && card.values().get(ai.ezfox.cccloud.compat.server.ServerCard.TPS).isJsonPrimitive(), "server card refreshed");
                var published = ai.ezfox.cccloud.web.WebHub.INSTANCE.widgetValue(setup.id(), card.sectionId().toString(), ai.ezfox.cccloud.compat.server.ServerCard.TPS);
                helper.assertTrue(published != null && published.isJsonPrimitive(), "server card values published to the hub");
            })
            .thenExecute(() -> {
                helper.assertTrue(!InGameRange.covered(setup.id(), level, near.add(300, 0, 0)), "not covered 300 blocks away");
                helper.assertTrue(TabletService.check(setup.owner(), setup.id(), false) == null, "owner may use the tablet here");
                helper.assertTrue("no_rights".equals(TabletService.check(stranger, setup.id(), false)), "strangers may not");
                setup.owner().setPos(near.x + 300, near.y, near.z);
                helper.assertTrue("out_of_range".equals(TabletService.check(setup.owner(), setup.id(), false)), "normal tablet out of coverage");
                helper.assertTrue(TabletService.check(setup.owner(), setup.id(), true) == null, "the advanced tablet works anywhere");
                setup.owner().setPos(near.x, near.y, near.z);
                open(helper, setup.owner(), setup.id(), tablet, 77);
            })
            .thenWaitUntil(() -> helper.assertTrue(received(setup.owner(), "\"t\":\"snapshot\""), "the tablet session gets the dashboard snapshot"))
            .thenWaitUntil(() -> {
                var model = new ai.ezfox.cccloud.ingame.DashboardModel();
                var list = CAPTURED.get(setup.owner().getUUID());
                synchronized (list) {
                    for (var batch : list) model.apply(batch);
                }
                var server = model.uplinks().values().stream().filter(u -> "Server".equals(u.get("title").getAsString())).findFirst().orElse(null);
                helper.assertTrue(server != null, "the owner (operator and up) sees the restricted Server card");
                var tps = server.getAsJsonArray("widgets").get(0).getAsJsonObject();
                helper.assertTrue(tps.has("value") && tps.get("value").isJsonPrimitive(), "live values reach the tablet: " + tps);
            })
            .thenExecute(() -> setup.owner().setPos(near.x + 300, near.y, near.z))
            .thenWaitUntil(() -> helper.assertTrue(TabletService.session(setup.owner()) == null, "leaving the coverage closes the tablet"))
            .thenSucceed();
    }

    public static void actions(GameTestHelper helper) {
        capture();
        var setup = mainframe(helper, "tablet_actions_owner");
        var viewer = player(helper, "tablet_actions_viewer");
        placeUplink(helper);
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                bindAndFill(helper, setup);
                grant(helper, setup, viewer, Role.VIEWER);
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).isOnline(), "uplink online"))
            .thenWaitUntil(() -> helper.assertTrue(InGameRange.covered(setup.id(), helper.getLevel(), setup.owner().position()), "coverage"))
            .thenExecute(() -> {
                var tablet = new ItemStack(InGameRegistry.CLOUD_TABLET.get());
                tablet.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                open(helper, setup.owner(), setup.id(), tablet, 78);
                open(helper, viewer, setup.id(), tablet.copy(), 79);
            })
            .thenWaitUntil(() -> helper.assertTrue(received(setup.owner(), "\"lamp\"") && received(viewer, "\"lamp\""), "both see the toggles"))
            .thenExecute(() -> {
                String id = uplink(helper).getUplinkId().toString();
                TabletService.request(setup.owner(), new InGamePayloads.TabletRequest(78,
                    "{\"t\":\"action\",\"reqId\":\"o1\",\"uplinkId\":\"" + id + "\",\"id\":\"lamp\",\"value\":true}"));
                TabletService.request(viewer, new InGamePayloads.TabletRequest(79,
                    "{\"t\":\"action\",\"reqId\":\"v1\",\"uplinkId\":\"" + id + "\",\"id\":\"lamp\",\"value\":false}"));
                TabletService.request(setup.owner(), new InGamePayloads.TabletRequest(78,
                    "{\"t\":\"action\",\"reqId\":\"o2\",\"uplinkId\":\"" + id + "\",\"id\":\"secret\",\"value\":true}"));
                TabletService.request(setup.owner(), new InGamePayloads.TabletRequest(78,
                    "{\"t\":\"action\",\"reqId\":\"o3\",\"uplinkId\":\"" + id + "\",\"id\":\"lamp\",\"value\":\"yes\"}"));
                TabletService.request(setup.owner(), new InGamePayloads.TabletRequest(78,
                    "{\"t\":\"action\",\"reqId\":\"o4\",\"uplinkId\":\"" + id + "\",\"id\":\"secret\",\"value\":true,\"pin\":\"1234\"}"));
            })
            .thenWaitUntil(() -> {
                helper.assertTrue("ok".equals(result(setup.owner(), "o1")), "owner switches the lamp: " + result(setup.owner(), "o1"));
                helper.assertTrue(uplink(helper).getWidget("lamp").value().getAsBoolean(), "the uplink's toggle is on");
                helper.assertTrue("read_only".equals(result(viewer, "v1")), "viewers are read-only: " + result(viewer, "v1"));
                helper.assertTrue("pin_required".equals(result(setup.owner(), "o2")), "PIN widget without PIN: " + result(setup.owner(), "o2"));
                helper.assertTrue("invalid_value".equals(result(setup.owner(), "o3")), "same validation as browsers: " + result(setup.owner(), "o3"));
                helper.assertTrue("pin_not_set".equals(result(setup.owner(), "o4")), "PIN checked like for browsers: " + result(setup.owner(), "o4"));
                helper.assertTrue(!uplink(helper).getWidget("secret").value().getAsBoolean(), "the PIN toggle stayed off");
            })
            .thenSucceed();
    }
}
