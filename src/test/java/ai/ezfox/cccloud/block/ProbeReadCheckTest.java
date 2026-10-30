package ai.ezfox.cccloud.block;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.ProbeTarget;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/**
 * Security review 2026-10-30, Cloud Probe: the range, the dimension and the probe's own position are checked on every
 * read (not only when linking), and a picked machine expires.
 */
class ProbeReadCheckTest {
    static final BlockPos PROBE = new BlockPos(100, 64, 100);

    @Test
    void readCheck() {
        var target = PROBE.offset(5, 0, 0);
        assertNull(CloudUplinkBlockEntity.remoteProblem(PROBE, "minecraft:overworld", PROBE, "minecraft:overworld", target, 8));
        assertEquals("too_far", CloudUplinkBlockEntity.remoteProblem(PROBE, "minecraft:overworld", PROBE, "minecraft:overworld", target, 4),
            "a lower probeRange applies to probes linked before");
        assertEquals("moved", CloudUplinkBlockEntity.remoteProblem(PROBE.east(), "minecraft:overworld", PROBE, "minecraft:overworld", target, 8),
            "moved with its data: the old coordinates are not read");
        assertEquals("moved", CloudUplinkBlockEntity.remoteProblem(PROBE, "minecraft:the_nether", PROBE, "minecraft:overworld", target, 8),
            "same coordinates in another dimension are another machine");
    }

    @Test
    void pickedMachinesExpire() {
        long now = 1_800_000_000_000L;
        var at = GlobalPos.of(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.withDefaultNamespace("overworld")),
            PROBE);
        UUID player = UUID.randomUUID();
        assertTrue(new ProbeTarget(at, Direction.UP, player, now - 60_000).fresh(now));
        assertFalse(new ProbeTarget(at, Direction.UP, player, now - ProbeTarget.VALID_MS - 1).fresh(now), "older than 10 minutes");
        assertFalse(new ProbeTarget(at, Direction.UP, player, 0).fresh(now), "no pick time (old cards, made-up cards)");
        assertFalse(new ProbeTarget(at, Direction.UP, player, now + 3_600_000).fresh(now), "not from the future");
    }
}
