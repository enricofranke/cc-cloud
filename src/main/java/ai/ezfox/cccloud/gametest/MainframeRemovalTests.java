package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.MainframeGuard;
import ai.ezfox.cccloud.block.MainframeRetention;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.item.MainframeIdentity;
import ai.ezfox.cccloud.layout.LayoutData;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.web.WebAuthData;
import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Mainframe removal (docs/SPEC.md "Mainframe removal"): the dropped item keeps the identity and restores the same
 * mainframe with its data and original owner; the misclick guard; the retention window and the purge; the recover
 * command's rights. Fake players, so these run in the {@code cccloud:ingame} batch.
 */
public final class MainframeRemovalTests {
    /** A second spot on the 5×5 floor for placing the mainframe again. */
    private static final BlockPos OTHER_FLOOR = new BlockPos(4, 0, 0);
    private static final String LAYOUT = "{\"v\":2,\"pages\":[],\"cards\":{},\"widgets\":{}}";

    private MainframeRemovalTests() {
    }

    private static BlockPos lower() {
        return InGameTestSupport.MAINFRAME_FLOOR.above();
    }

    private static void seed(GameTestHelper helper, InGameTestSupport.Setup setup, ServerPlayer friend) {
        InGameTestSupport.grant(helper, setup, friend, Role.VIEWER);
        LayoutData.get(helper.getLevel().getServer()).set(setup.id(), JsonParser.parseString(LAYOUT).getAsJsonObject());
    }

    /** Devices, accounts and layout of the mainframe are (still) there. */
    private static void assertKept(GameTestHelper helper, UUID id, ServerPlayer friend, String when) {
        var server = helper.getLevel().getServer();
        var access = WebAuthData.access(server).access(id);
        helper.assertTrue(access != null, when + ": accounts kept");
        var grant = access.grants().get(friend.getUUID());
        helper.assertTrue(grant != null && grant.role() == Role.VIEWER, when + ": the friend is still a viewer");
        helper.assertTrue(LayoutData.get(server).has(id), when + ": layout kept");
    }

    /** Places a mainframe item like a player does (the item has to be in the hand). */
    private static void place(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos floor) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.placeAt(player, stack, floor, Direction.UP);
    }

    private static @Nullable CloudMainframeBlockEntity mainframeAt(GameTestHelper helper, BlockPos lower) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(lower)) instanceof CloudMainframeBlockEntity m ? m : null;
    }

    private static ItemStack identityItem(UUID id, String owner) {
        var stack = new ItemStack(ModRegistry.CLOUD_MAINFRAME_ITEM.get());
        stack.set(ModRegistry.MAINFRAME_IDENTITY.get(), new MainframeIdentity(id, owner));
        return stack;
    }

    /**
     * Broken with drops: the item names the mainframe and its data stays. Placed again by someone else, it is the same
     * mainframe with the same owner and data, and the placer gets no rights; a second item of it is refused meanwhile.
     */
    public static void breakAndRestore(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "mfr_owner");
        var friend = InGameTestSupport.player(helper, "mfr_friend");
        var stranger = InGameTestSupport.player(helper, "mfr_stranger");
        var server = helper.getLevel().getServer();
        var id = setup.id();
        ItemStack[] dropped = {ItemStack.EMPTY};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(id) != null, "mainframe registered"))
            .thenExecute(() -> {
                seed(helper, setup, friend);
                helper.getLevel().destroyBlock(helper.absolutePos(lower()), true);
            })
            .thenWaitUntil(() -> {
                var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(lower())).inflate(3));
                var item = items.stream().map(ItemEntity::getItem).filter(s -> s.is(ModRegistry.CLOUD_MAINFRAME_ITEM.get())).findFirst();
                helper.assertTrue(item.isPresent(), "the mainframe dropped its item");
                dropped[0] = item.get().copy();
            })
            .thenExecute(() -> {
                var identity = dropped[0].get(ModRegistry.MAINFRAME_IDENTITY.get());
                helper.assertTrue(identity != null && identity.id().equals(id) && identity.ownerName().equals("mfr_owner"), "the item names the mainframe: " + identity);
                var entry = MainframeRetention.get(server).entry(id);
                helper.assertTrue(entry != null && entry.ownedBy(setup.owner().getUUID()) && entry.dropped(), "kept as removed: " + entry);
                assertKept(helper, id, friend, "after the break");
                place(helper, stranger, dropped[0].copy(), OTHER_FLOOR);
            })
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(id) != null, "the same mainframe is back"))
            .thenExecute(() -> {
                var restored = mainframeAt(helper, OTHER_FLOOR.above());
                helper.assertTrue(restored != null && restored.getMainframeId().equals(id), "same id at the new place");
                helper.assertTrue(setup.owner().getUUID().equals(restored.getOwner()), "still the original owner's, not the placer's");
                helper.assertTrue(Access.resolver(server).resolve(stranger.getUUID(), id).role() == Role.NONE, "the placer got no rights");
                helper.assertTrue(MainframeRetention.get(server).entry(id) == null, "no longer removed");
                assertKept(helper, id, friend, "after restoring");
                // Another item of the same mainframe cannot be placed while it stands.
                var copyFloor = new BlockPos(4, 0, 4);
                place(helper, setup.owner(), dropped[0].copy(), copyFloor);
                helper.assertTrue(mainframeAt(helper, copyFloor.above()) == null, "a second copy is refused");
            })
            .thenSucceed();
    }

    /**
     * Misclick guard: creative breaks only while sneaking; in survival only the owner, its admins and server admins.
     * Checked as a decision and through the real break path (ServerPlayerGameMode → BreakBlockEvent).
     */
    public static void breakGuard(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "mfg_owner");
        var stranger = InGameTestSupport.player(helper, "mfg_stranger");
        var admin = InGameTestSupport.player(helper, "mfg_admin");
        var server = helper.getLevel().getServer();
        var id = setup.id();
        var owner = setup.owner();
        var at = helper.absolutePos(lower());
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(id) != null, "mainframe registered"))
            .thenExecute(() -> {
                InGameTestSupport.grant(helper, setup, admin, Role.ADMIN);
                var mainframe = setup.mainframe();
                helper.assertTrue("cccloud.mainframe.not_yours_to_break".equals(MainframeGuard.refusal(stranger, mainframe)), "a stranger may not");
                helper.assertTrue(MainframeGuard.refusal(owner, mainframe) == null, "the owner may");
                helper.assertTrue(MainframeGuard.refusal(admin, mainframe) == null, "an admin of the mainframe may");
                stranger.setGameMode(GameType.CREATIVE);
                helper.assertTrue("cccloud.mainframe.sneak_to_break".equals(MainframeGuard.refusal(stranger, mainframe)), "creative: sneak first");
                stranger.setShiftKeyDown(true);
                helper.assertTrue(MainframeGuard.refusal(stranger, mainframe) == null, "creative + sneaking may (like any creative break)");
                stranger.setShiftKeyDown(false);
                stranger.setGameMode(GameType.SURVIVAL);

                helper.assertTrue(!stranger.gameMode.destroyBlock(at), "a stranger's survival break is cancelled");
                helper.assertTrue(mainframeAt(helper, lower()) != null, "still standing after the stranger");
                owner.setGameMode(GameType.CREATIVE);
                helper.assertTrue(!owner.gameMode.destroyBlock(at), "a creative break without sneaking is cancelled");
                helper.assertTrue(mainframeAt(helper, lower()) != null, "still standing after the misclick");
                owner.setShiftKeyDown(true);
                helper.assertTrue(owner.gameMode.destroyBlock(at), "sneaking breaks it");
                owner.setShiftKeyDown(false);
                owner.setGameMode(GameType.SURVIVAL);
                helper.assertTrue(mainframeAt(helper, lower()) == null, "broken");
                var entry = MainframeRetention.get(server).entry(id);
                helper.assertTrue(entry != null && !entry.dropped(), "creative break: kept, no item dropped: " + entry);
            })
            .thenSucceed();
    }

    /** Within the retention window everything is kept; afterwards it is deleted, and an old item is a plain mainframe. */
    public static void retentionPurge(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "mfp_owner");
        var friend = InGameTestSupport.player(helper, "mfp_friend");
        var stranger = InGameTestSupport.player(helper, "mfp_stranger");
        var server = helper.getLevel().getServer();
        var id = setup.id();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(id) != null, "mainframe registered"))
            .thenExecute(() -> {
                seed(helper, setup, friend);
                helper.getLevel().destroyBlock(helper.absolutePos(lower()), false);
                var data = MainframeRetention.get(server);
                var entry = data.entry(id);
                helper.assertTrue(entry != null, "kept as removed");
                long removedAt = entry.removedAt();
                helper.assertTrue(data.purgeExpired(server, removedAt + MainframeRetention.retentionMs() - 60_000).isEmpty(), "nothing expires early");
                assertKept(helper, id, friend, "a minute before the end");
                helper.assertTrue(data.purgeExpired(server, removedAt + MainframeRetention.retentionMs() + 1).contains(id), "purged after the window");
                helper.assertTrue(data.entry(id) == null, "entry gone");
                helper.assertTrue(WebAuthData.access(server).access(id) == null, "accounts and roles deleted");
                helper.assertTrue(!LayoutData.get(server).has(id), "layout deleted");
                place(helper, stranger, identityItem(id, "mfp_owner"), OTHER_FLOOR);
            })
            .thenWaitUntil(() -> {
                var placed = mainframeAt(helper, OTHER_FLOOR.above());
                helper.assertTrue(placed != null && CloudNetwork.mainframe(placed.getMainframeId()) == placed, "placed and registered");
                helper.assertTrue(!placed.getMainframeId().equals(id), "a purged identity is not reused");
                helper.assertTrue(stranger.getUUID().equals(placed.getOwner()), "a new mainframe of whoever placed it");
            })
            .thenSucceed();
    }

    /**
     * {@code /cccloud mainframe recover|purge}: the owner gets one item after a break that dropped none; strangers get
     * nothing; a server admin may always; purge needs the owner (or an admin).
     */
    public static void recoverCommand(GameTestHelper helper) {
        var setup = InGameTestSupport.mainframe(helper, "mfc_owner");
        var stranger = InGameTestSupport.player(helper, "mfc_stranger");
        var admin = InGameTestSupport.player(helper, "mfc_admin");
        var server = helper.getLevel().getServer();
        var id = setup.id();
        var owner = setup.owner();
        String shortId = id.toString().substring(0, 8);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(id) != null, "mainframe registered"))
            .thenExecute(() -> {
                owner.setGameMode(GameType.CREATIVE);
                owner.setShiftKeyDown(true);
                helper.assertTrue(owner.gameMode.destroyBlock(helper.absolutePos(lower())), "broken in creative");
                owner.setShiftKeyDown(false);
                owner.setGameMode(GameType.SURVIVAL);
                owner.getInventory().clearContent();

                helper.assertTrue(!run(server, stranger, "cccloud mainframe recover " + shortId), "a stranger cannot recover it");
                helper.assertTrue(run(server, owner, "cccloud mainframe recover " + shortId), "the owner can");
                helper.assertTrue(holds(owner, id), "the owner got a bound item");
                helper.assertTrue(MainframeRetention.get(server).entry(id).recovered(), "marked as recovered");
                helper.assertTrue(!run(server, owner, "cccloud mainframe recover " + shortId), "only once per removal");

                var adminName = new NameAndId(admin.getUUID(), admin.getGameProfile().name());
                // Level 2 explicitly (the GameTest server's default operator level is 0).
                server.getPlayerList().op(adminName, java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER), java.util.Optional.empty());
                try {
                    helper.assertTrue(run(server, admin, "cccloud mainframe recover " + shortId), "a server admin always can");
                    helper.assertTrue(holds(admin, id), "the admin got the item");
                } finally {
                    server.getPlayerList().deop(adminName);
                }
                helper.assertTrue(!run(server, stranger, "cccloud mainframe purge " + shortId), "a stranger cannot purge it");
                helper.assertTrue(MainframeRetention.get(server).entry(id) != null, "still kept");
                helper.assertTrue(run(server, owner, "cccloud mainframe purge " + shortId), "the owner can purge it");
                helper.assertTrue(MainframeRetention.get(server).entry(id) == null && WebAuthData.access(server).access(id) == null, "deleted now");
            })
            .thenSucceed();
    }

    private static boolean run(net.minecraft.server.MinecraftServer server, ServerPlayer player, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack()) > 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return false;
        }
    }

    private static boolean holds(ServerPlayer player, UUID id) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var identity = inventory.getItem(i).get(ModRegistry.MAINFRAME_IDENTITY.get());
            if (identity != null && identity.id().equals(id)) return true;
        }
        return false;
    }
}
