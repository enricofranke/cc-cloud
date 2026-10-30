package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.WiredMachineSection;
import ai.ezfox.cccloud.web.AccessGate;
import ai.ezfox.cccloud.web.WebHub;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemFullBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * CC wired networks (docs/SPEC.md "CC wired networks"): an uplink on a computer shows the card of a machine the
 * computer reaches through its wired modem, published like a device card (same permissions), and the computer's name
 * for a peripheral resolves for Lua bridge widgets. A chest (no card) gets none; a machine with an uplink of its own
 * keeps that one; switching the modem's peripheral sharing off removes the card. The machine is a GameTest-only
 * enchanting table with energy on its top face ({@link ProbeTests#registerTestCapabilities}), read by CC through the
 * modem on top of it.
 */
public final class WiredMachineTests {
    private WiredMachineTests() {
    }

    private static @Nullable WiredMachineSection card(CloudUplinkBlockEntity uplink) {
        for (var section : uplink.wiredMachines().sections()) if (section.peripheral().startsWith("minecraft:enchanting_table")) return section;
        return null;
    }

    public static void wiredMachineCards(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(0, 0, 0));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        var link = new MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), id);

        var table = new BlockPos(4, 1, 4);
        var modem = table.above();
        var computer = modem.north();
        var uplinkPos = computer.east();
        var chest = modem.west();
        var tableUplink = table.east();
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(computer, BuiltInRegistries.BLOCK.getValue(Identifier.parse("computercraft:computer_normal")));
        helper.setBlock(modem, BuiltInRegistries.BLOCK.getValue(Identifier.parse("computercraft:wired_modem_full")));
        helper.setBlock(uplinkPos, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        var uplink = (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(uplinkPos));
        String[] section = {null};

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(id) == mainframe && mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                uplink.bind(link, owner);
                helper.assertTrue(uplink.getLink() != null, "uplink linked");
                // share the peripherals next to the modem with the network (a right-click on the modem)
                if (!(level.getBlockEntity(helper.absolutePos(modem)) instanceof WiredModemFullBlockEntity full)) throw helper.assertionException("no wired modem");
                full.use(owner);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(uplink.isOnline(), "uplink online");
                helper.assertTrue(uplink.wiredMachines().reach().modems().contains(helper.absolutePos(modem)), "the computer's modem is followed");
                var card = card(uplink);
                helper.assertTrue(card != null, "a card for the machine behind the modem: " + uplink.wiredMachines().sections().size());
                helper.assertTrue(card.card().kind().equals(EnergyCard.KIND), "the same card an uplink on the machine would show: " + card.card().kind());
                section[0] = card.sectionId().toString();
                var snapshot = WebHub.INSTANCE.sectionSnapshot(id, section[0]);
                helper.assertTrue(snapshot != null && "direct".equals(snapshot.get("mode").getAsString()) && EnergyCard.KIND.equals(snapshot.get("template").getAsString())
                    && "wired".equals(snapshot.get("source").getAsString()), "published like a device card: " + snapshot);
                helper.assertTrue("Enchanting Table".equals(snapshot.get("title").getAsString()), "the card's title: " + snapshot.get("title"));
                for (var w : snapshot.getAsJsonArray("widgets")) {
                    var widget = w.getAsJsonObject();
                    String wid = widget.get("id").getAsString();
                    var need = WebHub.INSTANCE.actionPerm(id, section[0], wid);
                    helper.assertTrue(need == AccessGate.actionPerm(widget.get("type").getAsString(), "direct", wid, EnergyCard.KIND),
                        "permissions like a device card: " + wid + " " + need);
                }
                helper.assertTrue(uplink.wiredMachines().peripheral(card.peripheral()) != null, "the computer's name resolves (Lua bridge widgets)");
                helper.assertTrue(uplink.wiredMachines().sections().size() == 1, "nothing for the chest (no card)");
                helper.assertTrue(uplink.wiredMachines().reach().peripherals().keySet().stream().anyMatch(n -> n.startsWith("minecraft:chest")),
                    "the chest is on the network, just without a card");
            })
            .thenExecute(() -> {
                // an uplink of its own on the machine: that one keeps the machine
                helper.setBlock(tableUplink, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
                var own = (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(tableUplink));
                own.bind(link, owner);
                helper.assertTrue(own.getLink() != null, "second uplink linked");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(card(uplink) == null, "a machine with its own uplink gets no second card");
                helper.assertTrue(WebHub.INSTANCE.sectionSnapshot(id, section[0]) == null, "removed from the dashboard");
            })
            .thenExecute(() -> helper.setBlock(tableUplink, Blocks.AIR))
            .thenWaitUntil(() -> helper.assertTrue(card(uplink) != null && WebHub.INSTANCE.sectionSnapshot(id, section[0]) != null, "back without the other uplink"))
            .thenExecute(() -> ((WiredModemFullBlockEntity) level.getBlockEntity(helper.absolutePos(modem))).use(owner))
            .thenWaitUntil(() -> {
                helper.assertTrue(card(uplink) == null && WebHub.INSTANCE.sectionSnapshot(id, section[0]) == null, "sharing off: the card goes");
            })
            .thenExecute(() -> {
                helper.setBlock(uplinkPos, Blocks.AIR);
                helper.setBlock(modem, Blocks.AIR);
                helper.setBlock(computer, Blocks.AIR);
            })
            .thenSucceed();
    }
}
