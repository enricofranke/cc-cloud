package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.rsdevice.RsCards;
import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import ai.ezfox.cccloud.rsdevice.RsDevices;
import ai.ezfox.cccloud.rsdevice.RsPlateBlock;
import ai.ezfox.cccloud.uplink.WiredMachineSection;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemFullBlockEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * The claim guard (docs/SECURITY.md "Claims") on every device kind but the probe ({@link ProbeTests#guards} has that):
 * an uplink on a machine, a wired machine card behind a computer's modem and a Cloud Switch are placed and linked in
 * unclaimed land and work; then another team claims their chunks. All three are blocked at once: nothing is read, the
 * cards show the claim (the team's name only for viewers with {@code view.positions}), every action is refused with
 * {@code claim_blocked}, an audit line is written. They work again when the owner becomes an ally of that team, are
 * blocked again when the alliance ends, and work again when the chunks are unclaimed.
 * <p>
 * {@link #core} runs it with {@link TestClaims} (no claim mod needed; the test team only distrusts this test's
 * player); {@code compat/ftbchunks/FtbGameTests#claimGuard} runs it with real FTB Chunks claims and FTB Teams allies.
 */
public final class ClaimGuardTests {
    private ClaimGuardTests() {
    }

    /** Claims, alliances and the team name of the claiming team (TestClaims or FTB Chunks). */
    public interface Claimer {
        /** The claiming team's name as the guard reports it. */
        String team();

        void claim(ServerLevel level, Set<ChunkPos> chunks);

        void unclaim(ServerLevel level, Set<ChunkPos> chunks);

        /** The owner becomes an ally of the claiming team ({@code true}) or stops being one. */
        void ally(boolean ally);

        /** The test ends (also unclaims). */
        void cleanup(ServerLevel level, Set<ChunkPos> chunks);
    }

    /** With the GameTest claim mod: runs in the normal suite, next to other tests. */
    public static void core(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var claims = TestClaims.get();
        var team = claims.team("claim_guard_rivals", Set.of(owner.getUUID()));
        run(helper, owner, new Claimer() {
            @Override
            public String team() {
                return team.name();
            }

            @Override
            public void claim(ServerLevel level, Set<ChunkPos> chunks) {
                for (var c : chunks) claims.claim(level, new BlockPos(c.getMinBlockX(), 0, c.getMinBlockZ()), team);
            }

            @Override
            public void unclaim(ServerLevel level, Set<ChunkPos> chunks) {
                for (var c : chunks) claims.unclaim(level, new BlockPos(c.getMinBlockX(), 0, c.getMinBlockZ()));
            }

            @Override
            public void ally(boolean ally) {
                claims.ally(team, owner.getUUID(), ally);
            }

            @Override
            public void cleanup(ServerLevel level, Set<ChunkPos> chunks) {
                claims.forget(level, team);
            }
        });
    }

    private record Devices(CloudUplinkBlockEntity uplink, CloudUplinkBlockEntity computerUplink, RsDeviceBlockEntity device) {
    }

    /** The scenario, for any claim mod. {@code owner} places the mainframe and links every device. */
    public static void run(GameTestHelper helper, Player owner, Claimer claimer) {
        var level = helper.getLevel();
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(0, 0, 0));
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        var link = new MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), id);

        // 1. an uplink on a machine (a beacon with energy, GameTest capability)
        var beacon = new BlockPos(2, 1, 1);
        helper.setBlock(beacon, Blocks.BEACON);
        helper.setBlock(beacon.north(), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.SOUTH));
        // 2. a machine behind a computer's wired modem (an enchanting table with energy on top)
        var table = new BlockPos(4, 1, 4);
        var modem = table.above();
        var computer = modem.north();
        var computerUplink = computer.east();
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        helper.setBlock(computer, BuiltInRegistries.BLOCK.getValue(Identifier.parse("computercraft:computer_normal")));
        helper.setBlock(modem, BuiltInRegistries.BLOCK.getValue(Identifier.parse("computercraft:wired_modem_full")));
        helper.setBlock(computerUplink, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        // 3. a Cloud Switch on a stone block
        var support = new BlockPos(1, 1, 3);
        var switchPos = support.south();
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(switchPos, ModRegistry.CLOUD_SWITCH.get().defaultBlockState().setValue(RsPlateBlock.FACING, Direction.NORTH));

        var d = new Devices((CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(beacon.north())),
            (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(computerUplink)),
            (RsDeviceBlockEntity) level.getBlockEntity(helper.absolutePos(switchPos)));
        // the chunks of everything the devices read: the machine, the computer, the table and the switch
        var chunks = new LinkedHashSet<ChunkPos>();
        for (var p : new BlockPos[]{beacon, computer, table, switchPos}) chunks.add(ChunkPos.containing(helper.absolutePos(p)));
        String[] wiredId = {null};

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(id) == mainframe && mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                d.uplink().bind(link, owner);
                d.computerUplink().bind(link, owner);
                d.device().bind(link, owner);
                helper.assertTrue(d.uplink().getLink() != null && d.computerUplink().getLink() != null && d.device().getLink() != null, "all linked");
                if (!(level.getBlockEntity(helper.absolutePos(modem)) instanceof WiredModemFullBlockEntity full)) throw helper.assertionException("no wired modem");
                full.use(owner); // share the table with the computer's network
            })
            .thenWaitUntil(() -> {
                assertWorking(helper, d, id);
                var wired = wired(d.computerUplink());
                helper.assertTrue(wired != null, "a card for the table behind the modem");
                wiredId[0] = wired.sectionId().toString();
            })
            // another team claims the chunks
            .thenExecute(() -> claimer.claim(level, chunks))
            .thenWaitUntil(() -> assertBlocked(helper, d, id, wiredId[0], claimer.team()))
            .thenExecute(() -> {
                var audit = ai.ezfox.cccloud.web.WebAuthData.access(level.getServer()).audit(id);
                helper.assertTrue(audit.stream().anyMatch(e -> e.text().startsWith("Claim guard: ") && e.text().contains(" blocked: ")),
                    "one audit line per transition: " + audit);
            })
            // the owner becomes an ally of that team: everything works again
            .thenExecute(() -> claimer.ally(true))
            .thenWaitUntil(() -> assertWorking(helper, d, id))
            .thenExecute(() -> claimer.ally(false))
            .thenWaitUntil(() -> assertBlocked(helper, d, id, wiredId[0], claimer.team()))
            // the claims go
            .thenExecute(() -> claimer.unclaim(level, chunks))
            .thenWaitUntil(() -> assertWorking(helper, d, id))
            .thenExecute(() -> {
                helper.assertTrue(press(d.device(), id, new JsonPrimitive(true)) == null, "the switch works again");
                var audit = ai.ezfox.cccloud.web.WebAuthData.access(level.getServer()).audit(id);
                helper.assertTrue(audit.stream().anyMatch(e -> e.text().startsWith("Claim guard: ") && e.text().contains(" unblocked: ")), "unblock audited");
                claimer.cleanup(level, chunks);
                for (var p : new BlockPos[]{switchPos, support, beacon.north(), beacon, computerUplink, modem, computer, table}) {
                    level.setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            })
            .thenSucceed();
    }

    private static @Nullable WiredMachineSection wired(CloudUplinkBlockEntity uplink) {
        for (var section : uplink.wiredMachines().sections()) if (section.peripheral().startsWith("minecraft:enchanting_table")) return section;
        return null;
    }

    private static @Nullable String press(RsDeviceBlockEntity device, UUID mainframeId, JsonPrimitive value) {
        var section = RsDevices.sectionOfDevice(mainframeId, device.deviceId());
        if (section == null) return "no_card";
        var result = section.handleAction(RsCards.widgetId(device.deviceId()), value, null, "test", false).getNow(null);
        return result == null ? "pending" : result.error();
    }

    private static void assertWorking(GameTestHelper helper, Devices d, UUID id) {
        helper.assertTrue(d.uplink().isOnline() && d.uplink().claimBlock() == null, "uplink online and not blocked");
        helper.assertTrue(EnergyCard.KIND.equals(d.uplink().deviceKind()), "the machine's card: " + d.uplink().deviceKind());
        var snapshot = WebHub.INSTANCE.sectionSnapshot(id, d.uplink().getUplinkId().toString(), true);
        helper.assertTrue(snapshot != null && !snapshot.has("blocked") && !snapshot.getAsJsonArray("widgets").isEmpty(), "published with values: " + snapshot);
        var wired = wired(d.computerUplink());
        helper.assertTrue(wired != null && wired.claimBlock() == null, "wired card not blocked");
        var wiredSnapshot = WebHub.INSTANCE.sectionSnapshot(id, wired.sectionId().toString(), true);
        helper.assertTrue(wiredSnapshot != null && !wiredSnapshot.has("blocked") && !wiredSnapshot.getAsJsonArray("widgets").isEmpty(), "wired card has values");
        helper.assertTrue(d.device().claimBlock() == null && d.device().status() == CloudStatus.ONLINE, "switch online and not blocked");
        var card = RsDevices.sectionOfDevice(id, d.device().deviceId());
        helper.assertTrue(card != null && card.isOnline(), "the switch's card");
        var cardSnapshot = WebHub.INSTANCE.sectionSnapshot(id, card.sectionId().toString(), true);
        helper.assertTrue(cardSnapshot != null && !cardSnapshot.has("blockedWidgets"), "the switch's card shows it normally: " + cardSnapshot);
    }

    private static void assertBlocked(GameTestHelper helper, Devices d, UUID id, String wiredId, String team) {
        // the uplink on the machine: no card, no reads, the claim on the dashboard, actions refused
        var uplink = d.uplink();
        helper.assertTrue(uplink.claimBlock() != null && team.equals(uplink.claimBlock().team()), "uplink blocked by " + team + ": " + uplink.claimBlock());
        helper.assertTrue(uplink.readPos() == null && uplink.deviceCard() == null && uplink.deviceKind().isEmpty(), "nothing is read");
        assertBlockedSection(helper, id, uplink.getUplinkId().toString(), team);
        helper.assertTrue(ClaimGuard.ERROR.equals(error(uplink.handleAction("energy", new JsonPrimitive(1), null, "test", false).getNow(null))),
            "uplink action refused");
        // the computer's uplink (the computer is in the claim too) and the wired machine card of the table
        helper.assertTrue(d.computerUplink().claimBlock() != null, "the computer's uplink is blocked");
        var wired = wired(d.computerUplink());
        helper.assertTrue(wired != null && wired.claimBlock() != null, "the wired card stays, blocked");
        helper.assertTrue(wired.sectionId().toString().equals(wiredId), "same card");
        assertBlockedSection(helper, id, wiredId, team);
        helper.assertTrue(ClaimGuard.ERROR.equals(error(wired.handleAction("energy", new JsonPrimitive(1), null, "test", false).getNow(null))),
            "wired card action refused");
        // the switch: its widget on the card shows the claim, switching is refused
        var device = d.device();
        helper.assertTrue(device.claimBlock() != null && team.equals(device.claimBlock().team()), "switch blocked");
        var card = RsDevices.sectionOfDevice(id, device.deviceId());
        helper.assertTrue(card != null, "the switch stays on its card");
        var full = WebHub.INSTANCE.sectionSnapshot(id, card.sectionId().toString(), true);
        var publicVersion = WebHub.INSTANCE.sectionSnapshot(id, card.sectionId().toString(), false);
        String widget = RsCards.widgetId(device.deviceId());
        helper.assertTrue(full != null && full.get("blockedWidgets") instanceof JsonObject b && b.get(widget) instanceof JsonObject w
            && team.equals(w.get("team").getAsString()), "the card names the team for viewers with positions: " + full);
        helper.assertTrue(publicVersion != null && publicVersion.getAsJsonObject("blockedWidgets").getAsJsonObject(widget).get("team") == null,
            "and not for the others: " + publicVersion);
        helper.assertTrue(ClaimGuard.ERROR.equals(press(device, id, new JsonPrimitive(true))), "switching is refused");
    }

    private static void assertBlockedSection(GameTestHelper helper, UUID id, String sectionId, String team) {
        var full = WebHub.INSTANCE.sectionSnapshot(id, sectionId, true);
        helper.assertTrue(full != null && full.get("blocked") instanceof JsonObject b && "claim".equals(b.get("reason").getAsString())
            && team.equals(b.get("team").getAsString()), "the card shows the claim with the team: " + full);
        helper.assertTrue(full.getAsJsonArray("widgets").isEmpty(), "and no values");
        var publicVersion = WebHub.INSTANCE.sectionSnapshot(id, sectionId, false);
        helper.assertTrue(publicVersion != null && publicVersion.getAsJsonObject("blocked").get("team") == null,
            "viewers without view.positions do not get the team: " + publicVersion);
    }

    private static @Nullable String error(ai.ezfox.cccloud.web.@Nullable ActionResult result) {
        return result == null ? "pending" : result.error();
    }
}
