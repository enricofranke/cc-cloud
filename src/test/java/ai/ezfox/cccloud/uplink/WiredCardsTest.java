package ai.ezfox.cccloud.uplink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * docs/SPEC.md "CC wired networks": which wired peripherals become cards (one per block in name order, none where
 * another uplink or probe already reads), how cards with the same title are told apart, and how Lua bridge widgets find
 * a peripheral the computer reaches through its own wired modem. The CC side is covered by GameTest
 * {@code wired_machine_cards}.
 */
class WiredCardsTest {
    private static final BlockPos A = new BlockPos(1, 64, 1);
    private static final BlockPos B = new BlockPos(2, 64, 1);
    private static final BlockPos C = new BlockPos(3, 64, 1);

    @Test
    void oneCandidatePerBlockInNameOrderWithoutTakenBlocks() {
        var all = List.of(
            new WiredCards.Candidate("powah:energy_cell_1", B),
            new WiredCards.Candidate("minecraft:furnace_0", A),
            new WiredCards.Candidate("powah:energy_cell_0", B), // the same block through a second modem
            new WiredCards.Candidate("minecraft:chest_3", C));
        var picked = WiredCards.candidates(all, Set.of());
        assertEquals(List.of("minecraft:chest_3", "minecraft:furnace_0", "powah:energy_cell_0"), picked.stream().map(WiredCards.Candidate::name).toList(),
            "sorted by name, the first name of a block wins");
        var withoutTaken = WiredCards.candidates(all, Set.of(A, C));
        assertEquals(List.of(B), withoutTaken.stream().map(WiredCards.Candidate::pos).toList(), "blocks another uplink or probe reads are skipped");
        assertTrue(WiredCards.candidates(List.of(), Set.of()).isEmpty());
    }

    @Test
    void sameTitlesGetTheComputersName() {
        var titles = WiredCards.titles(List.of(
            Map.entry("powah:energy_cell_0", "Energy Cell"),
            Map.entry("minecraft:furnace_0", "Furnace"),
            Map.entry("powah:energy_cell_2", "Energy Cell")));
        assertEquals(List.of("Energy Cell (energy_cell_0)", "Furnace", "Energy Cell (energy_cell_2)"), titles);
        var longName = WiredCards.titles(List.of(Map.entry("m:" + "x".repeat(80), "Tank"), Map.entry("m:y", "Tank")));
        assertTrue(longName.get(0).length() <= Limits.TITLE, "titles stay within the title limit");
        assertEquals(List.of("Reactor"), WiredCards.titles(List.of(Map.entry("noNamespace_0", "Reactor"))));
    }

    @Test
    void bridgeNamesResolveThroughTheComputersWiredModems() {
        Map<String, String> attached = Map.of("left", "bridge on the left side");
        Map<String, String> network = Map.of("me_cloud_bridge_0", "bridge behind the computer's modem", "left", "never asked");
        assertEquals("bridge on the left side", WiredCards.reachable("left", attached::get, network::get), "the uplink's own attachment first");
        assertEquals("bridge behind the computer's modem", WiredCards.reachable("me_cloud_bridge_0", attached::get, network::get),
            "then the computer's wired networks");
        assertNull(WiredCards.reachable("me_cloud_bridge_9", attached::get, network::get));
    }
}
