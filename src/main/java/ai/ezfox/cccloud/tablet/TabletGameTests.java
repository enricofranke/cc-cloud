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

    /** Every kind of button a tablet can press: Lua buttons (with and without confirm), a scene, "All on" of a redstone card. */
    public static void buttons(GameTestHelper helper) {
        capture();
        var setup = mainframe(helper, "tablet_buttons_owner");
        placeUplink(helper);
        var level = helper.getLevel();
        var lamps = new net.minecraft.core.BlockPos[]{helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6)), helper.absolutePos(new net.minecraft.core.BlockPos(7, 1, 6))};
        for (var pos : lamps) level.setBlock(pos, ModRegistry.CLOUD_LAMP.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        var scene = new String[1];
        var card = ai.ezfox.cccloud.rsdevice.RsCards.sectionId(setup.id(), "halle", 0);
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                bindAndFill(helper, setup);
                try {
                    uplink(helper).putWidget(ai.ezfox.cccloud.uplink.WidgetFactory.create(ai.ezfox.cccloud.uplink.WidgetType.BUTTON, "go", "Go", null));
                    uplink(helper).putWidget(ai.ezfox.cccloud.uplink.WidgetFactory.create(ai.ezfox.cccloud.uplink.WidgetType.BUTTON, "scram", "Scram", Map.of("confirm", true)));
                } catch (ai.ezfox.cccloud.uplink.WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                for (var pos : lamps) {
                    var lamp = (ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity) level.getBlockEntity(pos);
                    lamp.bind(setup.link(), setup.owner());
                    ai.ezfox.cccloud.rsdevice.RsDevices.rename(lamp, "Lamp", "Halle");
                }
                var perms = ai.ezfox.cccloud.ingame.InGameAccess.resolve(setup.owner(), setup.id()).perms();
                var saved = ai.ezfox.cccloud.scene.SceneService.set(level.getServer(), setup.id(), JsonParser.parseString(
                    "{\"name\":\"Hello\",\"steps\":[{\"type\":\"alert\",\"text\":\"Scene ran\",\"level\":\"info\"}]}"), setup.owner().getUUID(), "owner", perms);
                helper.assertTrue(saved.error() == null, "scene saved: " + saved.error());
                scene[0] = ai.ezfox.cccloud.scene.SceneService.WIDGET_PREFIX + saved.id();
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).isOnline(), "uplink online"))
            .thenWaitUntil(() -> helper.assertTrue(InGameRange.covered(setup.id(), level, setup.owner().position()), "coverage"))
            .thenWaitUntil(() -> {
                helper.assertTrue(ai.ezfox.cccloud.web.WebHub.INSTANCE.sectionSnapshot(setup.id(), card.toString()) != null, "redstone card published");
                for (var pos : lamps) {
                    helper.assertTrue(((ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity) level.getBlockEntity(pos)).status() == ai.ezfox.cccloud.block.CloudStatus.ONLINE, "lamp online");
                }
            })
            .thenExecute(() -> {
                var tablet = new ItemStack(InGameRegistry.CLOUD_TABLET.get());
                tablet.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                open(helper, setup.owner(), setup.id(), tablet, 80);
            })
            .thenWaitUntil(() -> helper.assertTrue(received(setup.owner(), "\"scram\""), "the tablet sees the buttons"))
            .thenExecute(() -> {
                String id = uplink(helper).getUplinkId().toString();
                String scenes = ai.ezfox.cccloud.scene.SceneService.sectionId(setup.id()).toString();
                press(setup, 80, "b1", id, "go");
                press(setup, 80, "b2", id, "scram");
                press(setup, 80, "b3", scenes, scene[0]);
                press(setup, 80, "b4", card.toString(), ai.ezfox.cccloud.rsdevice.RsCards.ALL_ON);
            })
            .thenWaitUntil(() -> {
                for (var req : List.of("b1", "b2", "b3", "b4")) helper.assertTrue("ok".equals(result(setup.owner(), req)), req + ": " + result(setup.owner(), req));
                for (var pos : lamps) helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15, "All on reached the lamp");
            })
            .thenSucceed();
    }

    private static void press(Setup setup, int containerId, String reqId, String uplinkId, String widgetId) {
        TabletService.request(setup.owner(), new InGamePayloads.TabletRequest(containerId,
            "{\"t\":\"action\",\"reqId\":\"" + reqId + "\",\"uplinkId\":\"" + uplinkId + "\",\"id\":\"" + widgetId + "\",\"value\":true}"));
    }

    private static final Map<UUID, List<InGamePayloads.TabletAlert>> TOASTS = new ConcurrentHashMap<>();
    private static final java.util.Set<ServerPlayer> ONLINE = ConcurrentHashMap.newKeySet();

    private static void captureToasts(ServerPlayer... players) {
        ONLINE.addAll(List.of(players));
        TabletAlerts.players = server -> {
            var list = new ArrayList<>(server.getPlayerList().getPlayers());
            list.addAll(ONLINE);
            return list;
        };
        TabletAlerts.sender = (player, alert) -> {
            if (player instanceof FakePlayer) TOASTS.computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(alert);
            else PacketDistributor.sendToPlayer(player, alert);
        };
    }

    private static boolean toast(ServerPlayer player, String text) {
        return TOASTS.getOrDefault(player.getUUID(), List.of()).stream().anyMatch(a -> a.text().equals(text));
    }

    /**
     * Alerts published before the tablet opens are in its snapshot, filtered like the deltas (a viewer gets no restricted
     * section's alert and only the public form of a private one); one published while it is open arrives once as a delta.
     */
    public static void alertHistory(GameTestHelper helper) {
        capture();
        var setup = mainframe(helper, "tablet_alerts_owner");
        var viewer = player(helper, "tablet_alerts_viewer");
        var hub = ai.ezfox.cccloud.web.WebHub.INSTANCE;
        var restricted = new String[1];
        helper.startSequence()
            .thenExecute(() -> grant(helper, setup, viewer, Role.VIEWER))
            .thenWaitUntil(() -> {
                var card = ai.ezfox.cccloud.compat.server.MainframeCards.section(setup.id(), ai.ezfox.cccloud.compat.server.ServerCard.KIND);
                helper.assertTrue(card != null && hub.isRestricted(setup.id(), card.sectionId().toString()), "restricted Server card published");
                restricted[0] = card.sectionId().toString();
            })
            .thenExecute(() -> {
                hub.publishAlert(setup.id(), null, "Before the tablet opened", "warn");
                hub.publishAlert(setup.id(), restricted[0], "Server secret", "warn");
                hub.publishAlert(setup.id(), null, "Steve entered", "info", "A player entered");
            })
            .thenIdle(5)
            .thenExecute(() -> {
                var tablet = new ItemStack(InGameRegistry.ADVANCED_CLOUD_TABLET.get());
                tablet.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                open(helper, setup.owner(), setup.id(), tablet, 81);
                open(helper, viewer, setup.id(), tablet.copy(), 82);
            })
            .thenWaitUntil(() -> helper.assertTrue(received(setup.owner(), "\"t\":\"snapshot\"") && received(viewer, "\"t\":\"snapshot\""), "snapshots"))
            .thenExecute(() -> {
                helper.assertTrue(received(setup.owner(), "Before the tablet opened") && received(viewer, "Before the tablet opened"), "the history reaches the tablets");
                helper.assertTrue(received(setup.owner(), "Server secret") && received(setup.owner(), "Steve entered"), "the owner gets everything");
                helper.assertTrue(!received(viewer, "Server secret"), "no restricted text for viewers");
                helper.assertTrue(!received(viewer, "Steve entered") && received(viewer, "A player entered"), "viewers get the public form");
                hub.publishAlert(setup.id(), null, "While it is open", "crit");
            })
            .thenWaitUntil(() -> helper.assertTrue(received(setup.owner(), "While it is open"), "alerts land in an open tablet"))
            .thenExecute(() -> {
                var model = new ai.ezfox.cccloud.ingame.DashboardModel();
                var list = CAPTURED.get(setup.owner().getUUID());
                synchronized (list) {
                    for (var batch : list) model.apply(batch);
                }
                var alerts = JsonParser.parseString(model.toJson()).getAsJsonObject().getAsJsonArray("alerts");
                helper.assertTrue(alerts.size() == 4, "each alert once, nothing doubled: " + alerts);
                helper.assertTrue("While it is open".equals(alerts.get(0).getAsJsonObject().get("text").getAsString()), "newest first");
            })
            .thenSucceed();
    }

    /**
     * Toasts: an advanced tablet in the inventory with the screen closed gets its mainframe's alerts, filtered like the
     * dashboard; a normal tablet and a tablet of another mainframe do not; the switch stops them; bursts are capped.
     */
    public static void alertToasts(GameTestHelper helper) {
        var setup = mainframe(helper, "tablet_toast_owner");
        var viewer = player(helper, "tablet_toast_viewer");
        var normal = player(helper, "tablet_toast_normal");
        var stranger = player(helper, "tablet_toast_stranger");
        captureToasts(setup.owner(), viewer, normal, stranger);
        capture();
        var hub = ai.ezfox.cccloud.web.WebHub.INSTANCE;
        var restricted = new String[1];
        var ownerTablet = new ItemStack(InGameRegistry.ADVANCED_CLOUD_TABLET.get());
        helper.startSequence()
            .thenExecute(() -> {
                grant(helper, setup, viewer, Role.VIEWER);
                grant(helper, setup, normal, Role.VIEWER);
                ownerTablet.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                setup.owner().getInventory().setItem(9, ownerTablet);
                viewer.getInventory().setItem(9, ownerTablet.copy());
                stranger.getInventory().setItem(9, ownerTablet.copy());
                var plain = new ItemStack(InGameRegistry.CLOUD_TABLET.get());
                plain.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                normal.getInventory().setItem(9, plain);
            })
            .thenWaitUntil(() -> {
                var card = ai.ezfox.cccloud.compat.server.MainframeCards.section(setup.id(), ai.ezfox.cccloud.compat.server.ServerCard.KIND);
                helper.assertTrue(card != null && hub.isRestricted(setup.id(), card.sectionId().toString()), "restricted Server card published");
                restricted[0] = card.sectionId().toString();
            })
            .thenExecute(() -> {
                hub.publishAlert(setup.id(), null, "Reactor hot", "warn");
                hub.publishAlert(setup.id(), restricted[0], "Server secret", "warn");
                hub.publishAlert(setup.id(), null, "Steve entered", "info", "A player entered");
            })
            .thenWaitUntil(() -> helper.assertTrue(toast(setup.owner(), "Reactor hot") && toast(viewer, "Reactor hot"), "toast with the screen closed"))
            .thenWaitUntil(() -> helper.assertTrue(toast(setup.owner(), "Server secret") && toast(setup.owner(), "Steve entered"), "the owner gets all three"))
            .thenWaitUntil(() -> helper.assertTrue(toast(viewer, "A player entered"), "viewers get the public form"))
            .thenExecute(() -> {
                helper.assertTrue(!toast(viewer, "Server secret") && !toast(viewer, "Steve entered"), "nothing restricted for viewers");
                helper.assertTrue(!TOASTS.containsKey(normal.getUUID()), "no toast for a normal tablet");
                helper.assertTrue(!TOASTS.containsKey(stranger.getUUID()), "no toast without a role");
                TOASTS.clear();
                // viewer: a burst stays capped (one token was used above, tokens refill slowly)
                for (int i = 0; i < 20; i++) hub.publishAlert(setup.id(), null, "Burst " + i, "info");
                ownerTablet.set(ModRegistry.TABLET_ALERTS_OFF.get(), true);
            })
            .thenIdle(3)
            .thenExecute(() -> {
                helper.assertTrue(TOASTS.getOrDefault(viewer.getUUID(), List.of()).size() <= ai.ezfox.cccloud.ingame.AlertThrottle.BURST, "burst capped: " + TOASTS.get(viewer.getUUID()));
                helper.assertTrue(!TOASTS.containsKey(setup.owner().getUUID()), "switched off: no toast");
                TabletAlerts.clock = () -> System.nanoTime() + 60_000_000_000L; // tokens are back
            })
            .thenWaitUntil(() -> helper.assertTrue(TOASTS.getOrDefault(viewer.getUUID(), List.of()).stream().anyMatch(a -> a.text().isEmpty() && a.more() > 0),
                "held-back alerts are summed up: " + TOASTS.get(viewer.getUUID())))
            .thenExecute(() -> {
                TabletAlerts.clock = System::nanoTime;
                ONLINE.removeAll(List.of(setup.owner(), viewer, normal, stranger));
            })
            .thenSucceed();
    }

    /**
     * Renaming a mainframe: in its screen (layout.edit, viewers refused) and from the dashboard's bridge; the name reaches
     * the hub and an open tablet live, the network map, a display, the audit log, survives a save and comes back with a
     * removed mainframe; "" gives the default name back.
     */
    public static void rename(GameTestHelper helper) {
        capture();
        var setup = mainframe(helper, "tablet_rename_owner");
        var viewer = player(helper, "tablet_rename_viewer");
        var hub = ai.ezfox.cccloud.web.WebHub.INSTANCE;
        var level = helper.getLevel();
        var server = level.getServer();
        var displayPos = helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6));
        var defaultName = setup.mainframe().getDisplayName();
        var ops = new ai.ezfox.cccloud.gui.GuiActions.MainframeOps[1];
        helper.startSequence()
            .thenExecute(() -> {
                grant(helper, setup, viewer, Role.VIEWER);
                ops[0] = ai.ezfox.cccloud.gui.GuiNetwork.opsForTest(setup.owner(), setup.mainframe());
                level.setBlock(displayPos, InGameRegistry.CLOUD_DISPLAY.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> helper.assertTrue(hub.info(setup.id()) != null && InGameRange.covered(setup.id(), level, setup.owner().position()), "published"))
            .thenExecute(() -> {
                var display = (ai.ezfox.cccloud.display.CloudDisplayBlockEntity) level.getBlockEntity(displayPos);
                display.configure(setup.id(), defaultName, "", false, false, setup.owner().getUUID());
                var tablet = new ItemStack(InGameRegistry.CLOUD_TABLET.get());
                tablet.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                open(helper, setup.owner(), setup.id(), tablet, 83);
                var viewerPerms = ai.ezfox.cccloud.ingame.InGameAccess.resolve(viewer, setup.id()).perms();
                var ownerPerms = ai.ezfox.cccloud.ingame.InGameAccess.resolve(setup.owner(), setup.id()).perms();
                helper.assertTrue("not_owner".equals(ai.ezfox.cccloud.gui.GuiActions.mainframe("rename", "Mine", true, viewerPerms, ops[0])), "viewers may not rename");
                helper.assertTrue("invalid_name".equals(ai.ezfox.cccloud.gui.GuiActions.mainframe("rename", "x".repeat(33), true, ownerPerms, ops[0])), "too long");
                helper.assertTrue("invalid_name".equals(ai.ezfox.cccloud.gui.GuiActions.mainframe("rename", "§cRed", true, ownerPerms, ops[0])), "no formatting");
                helper.assertTrue(setup.mainframe().getDisplayName().equals(defaultName), "nothing changed yet");
                helper.assertTrue(ai.ezfox.cccloud.gui.GuiActions.mainframe("rename", "  North Base ", true, ownerPerms, ops[0]) == null, "the owner may");
                helper.assertTrue("North Base".equals(setup.mainframe().getDisplayName()), "renamed: " + setup.mainframe().getDisplayName());
            })
            .thenWaitUntil(() -> {
                helper.assertTrue("North Base".equals(hub.info(setup.id()).name()), "the hub knows the name");
                helper.assertTrue(received(setup.owner(), "\"name\":\"North Base\""), "an open tablet gets it live");
                var network = hub.networkForTest(setup.id(), true);
                helper.assertTrue(network != null && network.toString().contains("North Base"), "the network map follows: " + network);
                var display = (ai.ezfox.cccloud.display.CloudDisplayBlockEntity) level.getBlockEntity(displayPos);
                helper.assertTrue("North Base".equals(display.mainframeName()), "the display follows: " + display.mainframeName());
            })
            .thenExecute(() -> {
                var audit = ai.ezfox.cccloud.web.WebAuthData.access(server).auditMap().get(setup.id());
                helper.assertTrue(audit != null && audit.toString().contains("renamed the mainframe"), "audit log: " + audit);
                var device = ai.ezfox.cccloud.web.InGameClients.device(viewer, setup.id(), "tablet");
                helper.assertTrue("read_only".equals(ai.ezfox.cccloud.web.InGameClients.renameMainframe(server, device, "Mine")), "the bridge checks layout.edit");
                var tag = setup.mainframe().saveWithFullMetadata(level.registryAccess());
                helper.assertTrue("North Base".equals(tag.getStringOr("custom_name", "")), "saved with the block");
                var owner = ai.ezfox.cccloud.web.InGameClients.device(setup.owner(), setup.id(), "tablet");
                helper.assertTrue(ai.ezfox.cccloud.web.InGameClients.renameMainframe(server, owner, "") == null, "dashboard path, empty name");
                helper.assertTrue(defaultName.equals(setup.mainframe().getDisplayName()), "back to the default name");
                helper.assertTrue(ai.ezfox.cccloud.web.InGameClients.renameMainframe(server, owner, "South Base") == null, "dashboard path");
            })
            .thenWaitUntil(() -> helper.assertTrue("South Base".equals(hub.info(setup.id()).name()), "hub follows the dashboard path"))
            .thenExecute(() -> {
                level.setBlock(setup.mainframe().getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                var retention = ai.ezfox.cccloud.block.MainframeRetention.get(server);
                var entry = retention.entry(setup.id());
                helper.assertTrue(entry != null && "South Base".equals(entry.name()), "a removed mainframe keeps its name for restoring: " + entry);
                helper.assertTrue("South Base".equals(ai.ezfox.cccloud.block.MainframeRetention.name(entry)), "and is listed under it");
                ai.ezfox.cccloud.block.MainframeRetention.purge(server, setup.id());
            })
            .thenSucceed();
    }
}
