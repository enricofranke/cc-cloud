package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** v0.7: mainframe upgrades (effects, capacity, over_capacity), the Chunk Anchor card and wireless redstone. */
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
