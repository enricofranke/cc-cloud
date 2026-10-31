package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.redstone.RedstoneMode;
import ai.ezfox.cccloud.redstone.RedstoneService;
import ai.ezfox.cccloud.upgrade.ChunkAnchor;
import ai.ezfox.cccloud.upgrade.MainframeUpgrades;
import ai.ezfox.cccloud.upgrade.UpgradeEffects;
import ai.ezfox.cccloud.upgrade.UpgradeTier;
import ai.ezfox.cccloud.upgrade.UpgradeType;
import ai.ezfox.cccloud.web.WebHub;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.7: mainframe upgrades (effects, capacity, over_capacity, installing by right-click), the Chunk Anchor card and
 * wireless redstone.
 */
public final class UpgradeTests {
    private UpgradeTests() {
    }

    private record Setup(Player owner, CloudMainframeBlockEntity mainframe, MainframeLink link, BlockPos base) {
    }

    private static Setup mainframe(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var link = new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        return new Setup(owner, mainframe, link, mainframe.getBlockPos());
    }

    private static ItemStack card(UpgradeType type, UpgradeTier tier) {
        return new ItemStack(ModRegistry.card(type, tier).get());
    }

    /** An uplink at {@code pos} on a stone block below it. */
    private static void uplink(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
    }

    private static CloudUplinkBlockEntity uplinkAt(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity uplink)) throw helper.assertionException("uplink missing at " + pos);
        return uplink;
    }

    private static void clear(ServerLevel level, List<BlockPos> positions) {
        for (var pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static BlockHitResult upperHalf(CloudMainframeBlockEntity mainframe) {
        var pos = mainframe.getBlockPos().above();
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
    }

    /** Right-click on the mainframe's upper half with {@code stack} in the main hand, through the server's interaction path. */
    private static InteractionResult click(GameTestHelper helper, ServerPlayer player, CloudMainframeBlockEntity mainframe, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, upperHalf(mainframe));
    }

    private static boolean holds(MainframeUpgrades upgrades, int slot, ItemStack like) {
        return ItemStack.isSameItem(upgrades.getItem(slot), like) && upgrades.getItem(slot).getCount() == 1;
    }

    private static int count(Player player, ItemStack like) {
        int n = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ItemStack.isSameItem(inventory.getItem(i), like)) n += inventory.getItem(i).getCount();
        }
        return n;
    }

    /**
     * Right-click with an upgrade: a card goes into the first free card slot and one item is used, the Quantum Core into
     * its own slot, a higher tier replaces the installed card of its type (which goes back to the inventory, or drops
     * when that is full), the same or a lower tier, a second core or anchor and full slots are refused without using
     * the item, and any other item still opens the screen.
     */
    public static void clickInstall(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = InGameTestSupport.mainframe(helper, "upgrade_click_owner");
        var owner = s.owner();
        var mainframe = s.mainframe();
        var upgrades = mainframe.upgrades();
        var rangeA = card(UpgradeType.RANGE, UpgradeTier.ALLTHEMODIUM);
        var rangeV = card(UpgradeType.RANGE, UpgradeTier.VIBRANIUM);
        var rangeU = card(UpgradeType.RANGE, UpgradeTier.UNOBTAINIUM);
        var core = new ItemStack(ModRegistry.QUANTUM_CORE.get());
        var anchor = new ItemStack(ModRegistry.UPGRADE_CHUNK_ANCHOR.get());

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(s.id()) != null, "mainframe registered"))
            .thenExecute(() -> {
                owner.getInventory().clearContent();
                upgrades.clearContent();
                // Empty slots: the first card slot, one of the two cards is used.
                var held = rangeA.copyWithCount(2);
                helper.assertTrue(click(helper, owner, mainframe, held).consumesAction(), "the click is handled");
                helper.assertTrue(holds(upgrades, 0, rangeA) && held.getCount() == 1, "installed into slot 0, one card used: " + held);
                helper.assertTrue(mainframe.effects().rangeFactor() == 1.5, "the effects follow: " + mainframe.effects());
                // The same tier again: refused, nothing used.
                click(helper, owner, mainframe, held);
                helper.assertTrue(upgrades.getItem(1).isEmpty() && held.getCount() == 1, "the same tier is refused");
                // A higher tier replaces it in its slot; the old card comes back.
                var better = rangeV.copy();
                click(helper, owner, mainframe, better);
                helper.assertTrue(holds(upgrades, 0, rangeV) && upgrades.getItem(1).isEmpty() && better.isEmpty(), "the higher tier replaces it in place");
                helper.assertTrue(count(owner, rangeA) == 1, "the replaced card is back in the inventory");
                // A lower tier: refused.
                var lower = rangeA.copy();
                click(helper, owner, mainframe, lower);
                helper.assertTrue(holds(upgrades, 0, rangeV) && lower.getCount() == 1, "a lower tier is refused");
                // The Quantum Core goes to its own slot, a second one is refused.
                var cores = core.copyWithCount(2);
                click(helper, owner, mainframe, cores);
                helper.assertTrue(holds(upgrades, MainframeUpgrades.CORE_SLOT, core) && upgrades.getItem(1).isEmpty() && cores.getCount() == 1,
                    "the core goes to slot 4");
                click(helper, owner, mainframe, cores);
                helper.assertTrue(cores.getCount() == 1, "a second core is refused");
                // The Chunk Anchor takes the next free card slot; a second one is refused.
                var anchors = anchor.copyWithCount(2);
                click(helper, owner, mainframe, anchors);
                click(helper, owner, mainframe, anchors);
                helper.assertTrue(holds(upgrades, 1, anchor) && upgrades.getItem(2).isEmpty() && anchors.getCount() == 1, "one anchor, the second is refused");
                // Fill the card slots; a new type finds no slot.
                click(helper, owner, mainframe, card(UpgradeType.CAPACITY, UpgradeTier.ALLTHEMODIUM));
                click(helper, owner, mainframe, card(UpgradeType.BANDWIDTH, UpgradeTier.ALLTHEMODIUM));
                helper.assertTrue(holds(upgrades, 2, card(UpgradeType.CAPACITY, UpgradeTier.ALLTHEMODIUM))
                    && holds(upgrades, 3, card(UpgradeType.BANDWIDTH, UpgradeTier.ALLTHEMODIUM)), "slots 2 and 3 filled");
                var memory = card(UpgradeType.MEMORY, UpgradeTier.ALLTHEMODIUM);
                click(helper, owner, mainframe, memory);
                helper.assertTrue(memory.getCount() == 1 && !holds(upgrades, 3, memory), "full slots refuse a new type");
                // Full slots still let a higher tier replace; with a full inventory the old card is dropped.
                for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) owner.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
                var best = rangeU.copyWithCount(2);
                click(helper, owner, mainframe, best);
                helper.assertTrue(holds(upgrades, 0, rangeU) && best.getCount() == 1, "the higher tier replaces it in full slots");
                helper.assertTrue(mainframe.effects().rangeFactor() == 3, "range ×3: " + mainframe.effects());
                var dropped = level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(4), e -> ItemStack.isSameItem(e.getItem(), rangeV));
                helper.assertTrue(dropped.size() == 1 && count(owner, rangeV) == 0, "the replaced card drops when the inventory is full");
                dropped.forEach(ItemEntity::discard);
                owner.getInventory().clearContent();
                // Any other item: the block hands the click on to the screen (useWithoutItem), the slots stay as they are.
                var stick = new ItemStack(Items.STICK);
                var hit = upperHalf(mainframe);
                helper.assertTrue(level.getBlockState(hit.getBlockPos()).useItemOn(stick, level, owner, InteractionHand.MAIN_HAND, hit)
                    instanceof InteractionResult.TryEmptyHandInteraction, "other items fall back to opening the screen");
                click(helper, owner, mainframe, stick);
                helper.assertTrue(stick.getCount() == 1 && holds(upgrades, 0, rangeU) && holds(upgrades, 1, anchor), "a stick changes nothing");
                owner.getInventory().clearContent();
                upgrades.clearContent();
            })
            .thenSucceed();
    }

    /**
     * Right-click installs follow the Upgrades tab's rule (devices.manage): a stranger, a viewer and an operator are
     * refused and keep their card, an admin of the mainframe installs; a click that a claim mod cancels changes nothing,
     * even for the owner; creative players install without using the item.
     */
    public static void clickRights(GameTestHelper helper) {
        var s = InGameTestSupport.mainframe(helper, "upgrade_rights_owner");
        var owner = s.owner();
        var mainframe = s.mainframe();
        var upgrades = mainframe.upgrades();
        var stranger = InGameTestSupport.player(helper, "upgrade_rights_stranger");
        var viewer = InGameTestSupport.player(helper, "upgrade_rights_viewer");
        var operator = InGameTestSupport.player(helper, "upgrade_rights_operator");
        var admin = InGameTestSupport.player(helper, "upgrade_rights_admin");
        var capacity = card(UpgradeType.CAPACITY, UpgradeTier.ALLTHEMODIUM);
        var bandwidth = card(UpgradeType.BANDWIDTH, UpgradeTier.ALLTHEMODIUM);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(s.id()) != null, "mainframe registered"))
            .thenExecute(() -> {
                upgrades.clearContent();
                InGameTestSupport.grant(helper, s, viewer, Role.VIEWER);
                InGameTestSupport.grant(helper, s, operator, Role.OPERATOR);
                InGameTestSupport.grant(helper, s, admin, Role.ADMIN);
                for (var player : List.of(stranger, viewer, operator)) {
                    var held = capacity.copy();
                    click(helper, player, mainframe, held);
                    helper.assertTrue(held.getCount() == 1 && upgrades.isEmpty(), player.getGameProfile().name() + " may not change the upgrades");
                    helper.assertTrue(!mainframe.mayChangeUpgrades(player), "the same rule as the Upgrades tab");
                }
                var held = capacity.copy();
                click(helper, admin, mainframe, held);
                helper.assertTrue(held.isEmpty() && holds(upgrades, 0, capacity), "an admin of the mainframe installs");
                // A claim mod cancels the right-click (FTB Chunks in another team's claim): nothing changes, even for the owner.
                var upper = upperHalf(mainframe).getBlockPos();
                ProbeTests.DENIED.add(upper);
                var claimed = bandwidth.copy();
                var result = click(helper, owner, mainframe, claimed);
                ProbeTests.DENIED.remove(upper);
                helper.assertTrue(!result.consumesAction() && claimed.getCount() == 1 && upgrades.getItem(1).isEmpty(), "a cancelled click installs nothing");
                // Creative: installed, the stack keeps its count.
                owner.setGameMode(GameType.CREATIVE);
                var creative = bandwidth.copy();
                click(helper, owner, mainframe, creative);
                owner.setGameMode(GameType.SURVIVAL);
                helper.assertTrue(holds(upgrades, 1, bandwidth) && creative.getCount() == 1, "creative installs without using the item");
                owner.getInventory().clearContent();
                upgrades.clearContent();
            })
            .thenSucceed();
    }

    /**
     * Effects of the cards (range, bandwidth, efficiency, Quantum Core), and the capacity: 16 devices fit, the 17th link
     * is refused, a Capacity card lets it in, removing the card puts the most recently linked device over capacity.
     */
    public static void effectsAndCapacity(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var upgrades = s.mainframe().upgrades();
        var positions = new ArrayList<BlockPos>();
        // 17 uplinks in a row 40 blocks above the mainframe (radio range 96).
        for (int i = 0; i < 17; i++) {
            var pos = s.base().above(40).east(i - 8);
            uplink(level, pos);
            positions.add(pos);
        }
        var far = s.base().above(200);
        uplink(level, far);
        positions.add(far);
        var id = s.mainframe().getMainframeId();

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                // Range ×3: an uplink 200 blocks away links directly (96 × 3 = 288).
                var farUplink = uplinkAt(helper, level, far);
                farUplink.bind(s.link(), s.owner());
                helper.assertTrue(farUplink.getLink() == null, "200 blocks is out of the base range");
                upgrades.setItem(0, card(UpgradeType.RANGE, UpgradeTier.UNOBTAINIUM));
                upgrades.setItem(1, card(UpgradeType.BANDWIDTH, UpgradeTier.ALLTHEMODIUM));
                upgrades.setItem(2, card(UpgradeType.EFFICIENCY, UpgradeTier.UNOBTAINIUM));
                upgrades.setItem(3, card(UpgradeType.RANGE, UpgradeTier.ALLTHEMODIUM));
                var e = s.mainframe().effects();
                helper.assertTrue(e.rangeFactor() == 3, "highest range tier wins: " + e);
                helper.assertTrue(WebHub.browserLimit.applyAsInt(id) == 8, "bandwidth card: 8 browsers");
                helper.assertTrue(!MainframeUpgrades.allowed(MainframeUpgrades.CORE_SLOT, card(UpgradeType.CAPACITY, UpgradeTier.VIBRANIUM)),
                    "the core slot only takes the Quantum Core");
                helper.assertTrue(!MainframeUpgrades.allowed(0, new ItemStack(ModRegistry.QUANTUM_CORE.get())), "card slots do not take the core");
            })
            .thenIdle(2)
            .thenExecute(() -> {
                var farUplink = uplinkAt(helper, level, far);
                farUplink.bind(s.link(), s.owner());
                helper.assertTrue(farUplink.getLink() != null, "range upgrade extends the mainframe's reach");
                var power = s.mainframe().powerBreakdown();
                helper.assertTrue(power.get("efficiency") < 0 && power.get("total") < power.get("base") + power.get("uplinks") + power.get("clients")
                    + power.get("hops"), "efficiency lowers the usage: " + power);
                // Back to 16 devices: the far uplink plus 15 of the row.
                upgrades.setItem(0, ItemStack.EMPTY);
                upgrades.setItem(3, ItemStack.EMPTY);
                for (int i = 0; i < 15; i++) uplinkAt(helper, level, positions.get(i)).bind(s.link(), s.owner());
            })
            .thenWaitUntil(() -> helper.assertTrue(NetworkService.counts(id).devices() == 15, "15 devices routed (far one out of range again): "
                + NetworkService.counts(id)))
            .thenExecute(() -> {
                uplinkAt(helper, level, positions.get(15)).bind(s.link(), s.owner());
                var last = uplinkAt(helper, level, positions.get(16));
                last.bind(s.link(), s.owner());
                helper.assertTrue(last.getLink() == null, "the 17th link is refused (capacity 16 incl. the far uplink)");
                upgrades.setItem(0, card(UpgradeType.CAPACITY, UpgradeTier.ALLTHEMODIUM));
                helper.assertTrue(s.mainframe().effects().devices() == 48, "capacity card: 48");
                last.bind(s.link(), s.owner());
                helper.assertTrue(last.getLink() != null, "with the card it fits");
            })
            .thenIdle(2)
            .thenExecute(() -> upgrades.setItem(0, ItemStack.EMPTY))
            .thenWaitUntil(() -> {
                var lastId = uplinkAt(helper, level, positions.get(16)).getUplinkId();
                var overNow = new ArrayList<Integer>();
                var states = new StringBuilder();
                for (int i = 0; i < 18; i++) {
                    var u = uplinkAt(helper, level, positions.get(i));
                    var r = NetworkService.route(u.getUplinkId());
                    states.append(i).append(u.getLink() == null ? "U" : r == null ? "?" : r.connected() ? "C" : "N").append(' ');
                    if (i < 17 && NetworkService.overCapacity(u.getUplinkId())) overNow.add(i);
                }
                helper.assertTrue(NetworkService.overCapacity(lastId), "the most recently linked device goes over_capacity: states=" + states + " over=" + overNow
                    + " counts=" + NetworkService.counts(id) + " limit=" + s.mainframe().effects().devices() + " used=" + NetworkService.capacityUsed(id));
                helper.assertTrue("over_capacity".equals(uplinkAt(helper, level, positions.get(16)).routeState()), "uplink shows over_capacity");
                helper.assertTrue(!NetworkService.overCapacity(uplinkAt(helper, level, positions.get(0)).getUplinkId()), "older devices keep working");
                var json = WebHub.INSTANCE.networkForTest(id, true);
                helper.assertTrue(json != null && json.toString().contains("\"over_capacity\""), "map shows over_capacity");
            })
            .thenExecute(() -> {
                upgrades.setItem(MainframeUpgrades.CORE_SLOT, new ItemStack(ModRegistry.QUANTUM_CORE.get()));
                var e = s.mainframe().effects();
                helper.assertTrue(e.devices() == UpgradeEffects.UNLIMITED && e.enderNode() && e.feFactor() == 0.25, "quantum core: " + e);
            })
            .thenWaitUntil(() -> helper.assertTrue(!NetworkService.overCapacity(uplinkAt(helper, level, positions.get(16)).getUplinkId()),
                "capacity is back"))
            .thenExecute(() -> {
                upgrades.clearContent();
                clear(level, positions);
            })
            .thenSucceed();
    }

    /**
     * Chunk Anchor card: does nothing while the server config is off; with {@code chunkAnchor=true} it force-loads the
     * mainframe's chunk and its linked devices' chunks, costs FE, and releases them when the card is removed.
     */
    public static void chunkAnchor(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var id = s.mainframe().getMainframeId();
        // Another chunk, but between the rows of the GameTest grid: 40 blocks east alone is exactly 4 test slots over,
        // on top of that test's mainframe.
        var remote = s.base().east(40).south(6).above(2);
        uplink(level, remote);
        boolean before = CloudConfig.get(CloudConfig.CHUNK_ANCHOR);

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                uplinkAt(helper, level, remote).bind(s.link(), s.owner());
                CloudConfig.CHUNK_ANCHOR.set(false);
                s.mainframe().upgrades().setItem(0, new ItemStack(ModRegistry.UPGRADE_CHUNK_ANCHOR.get()));
            })
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(ChunkAnchor.chunks(id) == 0, "disabled by default: nothing is anchored");
                CloudConfig.CHUNK_ANCHOR.set(true);
                s.mainframe().upgrades().setChanged();
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(ChunkAnchor.chunks(id) == 2, "mainframe chunk + the uplink's chunk: " + ChunkAnchor.active(id));
                helper.assertTrue(ChunkAnchor.isAnchored(level, remote.getX(), remote.getZ()), "device chunk anchored");
                long anchor = s.mainframe().powerBreakdown().getOrDefault("anchor", 0L);
                helper.assertTrue(anchor == 200 + 2 * 20, "anchor costs 200 + 20 per chunk: " + anchor);
            })
            .thenExecute(() -> s.mainframe().upgrades().setItem(0, ItemStack.EMPTY))
            .thenWaitUntil(() -> {
                helper.assertTrue(ChunkAnchor.chunks(id) == 0, "card removed → released");
                helper.assertTrue(!s.mainframe().powerBreakdown().containsKey("anchor"), "no anchor cost");
            })
            .thenExecute(() -> {
                CloudConfig.CHUNK_ANCHOR.set(before);
                clear(level, List.of(remote));
            })
            .thenSucceed();
    }

    /**
     * Wireless redstone: a transmitter cabled 110 blocks away (CC cable, out of radio range) and a receiver near the
     * mainframe on the same channel; the receiver follows the transmitter's input, a receiver on another channel stays
     * off, the dashboard gets the channel, and an operator override wins until it is cleared.
     */
    public static void wirelessRedstone(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var id = s.mainframe().getMainframeId();
        var placed = new ArrayList<BlockPos>();
        var cable = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("computercraft", "cable"));
        var cableState = cable.defaultBlockState();
        if (cable.getStateDefinition().getProperty("cable") instanceof BooleanProperty p) cableState = cableState.setValue(p, true);
        for (int y = 2; y <= 110; y++) {
            level.setBlock(s.base().above(y), cableState, Block.UPDATE_ALL);
            placed.add(s.base().above(y));
        }
        var tx = s.base().above(111);
        level.setBlock(tx, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
        placed.add(tx);
        var lever = tx.east();
        var rx = s.base().west(4).above(2);
        uplink(level, rx);
        var other = s.base().west(6).above(2);
        uplink(level, other);
        var probe = rx.north();
        placed.addAll(List.of(lever, rx, rx.below(), other, other.below()));

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(uplinkAt(helper, level, tx).wired().visibleNodes().contains(id.toString()), "transmitter is cabled"))
            .thenExecute(() -> {
                for (var pos : List.of(tx, rx, other)) uplinkAt(helper, level, pos).bind(s.link(), s.owner());
                uplinkAt(helper, level, tx).setRedstone(RedstoneMode.TRANSMIT, "door");
                uplinkAt(helper, level, rx).setRedstone(RedstoneMode.RECEIVE, "door");
                uplinkAt(helper, level, other).setRedstone(RedstoneMode.RECEIVE, "lamp");
            })
            .thenWaitUntil(() -> helper.assertTrue(NetworkService.route(uplinkAt(helper, level, tx).getUplinkId()) != null
                && NetworkService.route(uplinkAt(helper, level, tx).getUplinkId()).wired(), "transmitter routed by cable"))
            .thenExecute(() -> level.setBlock(lever, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))
            .thenWaitUntil(() -> {
                helper.assertTrue(uplinkAt(helper, level, tx).redstoneInput() == 15, "transmitter reads its input");
                helper.assertTrue(uplinkAt(helper, level, rx).redstoneOutput() == 15, "receiver follows over the network");
                helper.assertTrue(level.getSignal(rx, Direction.SOUTH) == 15 || level.getBestNeighborSignal(probe) == 15, "real redstone signal");
                helper.assertTrue(uplinkAt(helper, level, other).redstoneOutput() == 0, "other channel stays off");
                var json = WebHub.INSTANCE.redstoneForTest(id);
                helper.assertTrue(json != null && json.toString().contains("\"name\":\"door\"") && json.toString().contains("\"level\":15"),
                    "dashboard channel: " + json);
            })
            .thenExecute(() -> {
                helper.assertTrue(RedstoneService.override(id, "door", 4, null) == null, "override accepted");
            })
            .thenWaitUntil(() -> helper.assertTrue(uplinkAt(helper, level, rx).redstoneOutput() == 4, "override wins"))
            .thenExecute(() -> {
                RedstoneService.override(id, "door", null, null);
                level.setBlock(lever, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> helper.assertTrue(uplinkAt(helper, level, rx).redstoneOutput() == 0, "input gone → receiver off"))
            .thenExecute(() -> {
                for (var pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }

    /**
     * A transmitter whose input was already on before it existed (lever placed first) and after its chunk reloads: the
     * input is not saved and a lever that stays on sends no neighbour update, so the transmitter has to sample it on
     * load. The reload is simulated like a chunk unload/load of the block entity (saved tag → new block entity, onLoad).
     */
    public static void wirelessRedstoneAfterLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var id = s.mainframe().getMainframeId();
        // Inside the test's own 5×5 floor (the mainframe stands in the middle), so neighbouring tests are never touched.
        var tx = s.base().west(2).north(2).above(1);
        var rx = s.base().east(2).south(2).above(1);
        var lever = tx.south();
        java.util.function.IntSupplier channel = () -> {
            var c = RedstoneService.channels(id).get("gate");
            return c == null ? -1 : c.level();
        };
        // Fresh placement order: the lever is on before the transmitter is placed.
        level.setBlock(lever.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,
            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true), Block.UPDATE_ALL);
        uplink(level, tx);
        uplink(level, rx);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(id) != null, "mainframe registered"))
            .thenExecute(() -> {
                for (var pos : List.of(tx, rx)) {
                    uplinkAt(helper, level, pos).bind(s.link(), s.owner());
                    helper.assertTrue(uplinkAt(helper, level, pos).getLink() != null, "linked: " + pos);
                }
                uplinkAt(helper, level, tx).setRedstone(RedstoneMode.TRANSMIT, "gate");
                uplinkAt(helper, level, rx).setRedstone(RedstoneMode.RECEIVE, "gate");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(channel.getAsInt() == 15, "channel follows the lever that was on first: " + channel.getAsInt());
                helper.assertTrue(uplinkAt(helper, level, rx).redstoneOutput() == 15, "receiver on");
            })
            .thenExecute(() -> {
                // Chunk unload: the block entity is saved and dropped, the lever stays on.
                var old = uplinkAt(helper, level, tx);
                var tag = old.saveWithFullMetadata(level.registryAccess());
                old.onChunkUnloaded();
                level.removeBlockEntity(tx);
                // Chunk load: a new block entity from the saved data (input 0 until it samples), onLoad on the next tick.
                var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(tx, level.getBlockState(tx), tag, level.registryAccess());
                helper.assertTrue(loaded instanceof CloudUplinkBlockEntity u && u.redstoneInput() == 0 && u.redstoneMode() == RedstoneMode.TRANSMIT,
                    "reloaded transmitter starts without input");
                level.setBlockEntity(loaded);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(uplinkAt(helper, level, tx).redstoneInput() == 15, "transmitter sampled its input after loading");
                helper.assertTrue(channel.getAsInt() == 15, "channel after the reload: " + channel.getAsInt());
                helper.assertTrue(uplinkAt(helper, level, rx).redstoneOutput() == 15, "receiver still on");
            })
            .thenExecute(() -> level.setBlock(lever, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
            .thenWaitUntil(() -> helper.assertTrue(uplinkAt(helper, level, rx).redstoneOutput() == 0, "lever gone → receiver off"))
            .thenExecute(() -> clear(level, List.of(tx, rx, lever)))
            .thenSucceed();
    }
}
