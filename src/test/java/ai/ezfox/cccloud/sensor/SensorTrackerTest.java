package ai.ezfox.cccloud.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Presence tracking (baseline, leaving after missed scans, the per-entity cooldown, bounds) and the zone geometry. */
class SensorTrackerTest {
    static final long COOLDOWN = 60_000;
    static final UUID A = UUID.nameUUIDFromBytes("a".getBytes());
    static final UUID B = UUID.nameUUIDFromBytes("b".getBytes());
    static final SensorEntry ZOMBIE = new SensorEntry(SensorKind.HOSTILE, "minecraft:zombie", "Zombie");

    static List<SensorTracker.Seen> seen(UUID... ids) {
        var list = new ArrayList<SensorTracker.Seen>();
        for (var id : ids) list.add(new SensorTracker.Seen(id, ZOMBIE));
        return list;
    }

    @Test
    void baselineThenEnteredAndLeftAfterTwoMissedScans() {
        var t = new SensorTracker();
        assertTrue(t.scan(seen(A), 0, COOLDOWN).isEmpty(), "the first scan only records who is there");
        assertTrue(t.occupied());
        assertEquals(1, t.scan(seen(A, B), 1_000, COOLDOWN).size(), "B entered");
        assertTrue(t.scan(seen(A), 2_000, COOLDOWN).isEmpty(), "missing once: still inside (edge flicker)");
        var left = t.scan(seen(A), 3_000, COOLDOWN);
        assertEquals(1, left.size());
        assertFalse(left.getFirst().entered(), "missing twice: left");
        assertTrue(t.scan(seen(), 4_000, COOLDOWN).isEmpty());
        assertTrue(t.scan(seen(), 5_000, COOLDOWN).isEmpty(), "A was part of the baseline: it leaves silently");
        assertFalse(t.occupied());
    }

    @Test
    void cooldownPerEntityMakesComingBackSilent() {
        var t = new SensorTracker();
        t.scan(seen(), 0, COOLDOWN);
        assertEquals(1, t.scan(seen(A), 1_000, COOLDOWN).size(), "entered");
        t.scan(seen(), 2_000, COOLDOWN);
        assertEquals(1, t.scan(seen(), 3_000, COOLDOWN).size(), "left");
        assertTrue(t.scan(seen(A), 10_000, COOLDOWN).isEmpty(), "back within the cooldown: silent");
        assertTrue(t.occupied(), "but inside");
        t.scan(seen(), 11_000, COOLDOWN);
        assertTrue(t.scan(seen(), 12_000, COOLDOWN).isEmpty(), "and leaves silently");
        assertEquals(1, t.scan(seen(A), 70_000, COOLDOWN).size(), "after the cooldown it is reported again");
        var noCooldown = new SensorTracker();
        noCooldown.scan(seen(), 0, 0);
        noCooldown.scan(seen(A), 1, 0);
        noCooldown.scan(seen(), 2, 0);
        noCooldown.scan(seen(), 3, 0);
        assertEquals(1, noCooldown.scan(seen(A), 4, 0).size(), "cooldown 0: every time");
    }

    @Test
    void rebaseAndBounds() {
        var t = new SensorTracker();
        t.scan(seen(), 0, COOLDOWN);
        t.scan(seen(A), 1, COOLDOWN);
        t.rebase();
        assertFalse(t.occupied());
        assertTrue(t.scan(seen(A, B), 2, COOLDOWN).isEmpty(), "after a new setting: a new baseline");
        var many = new ArrayList<SensorTracker.Seen>();
        for (int i = 0; i < SensorTracker.MAX_INSIDE + 20; i++) many.add(new SensorTracker.Seen(UUID.randomUUID(), ZOMBIE));
        var big = new SensorTracker();
        big.scan(seen(), 0, COOLDOWN);
        assertEquals(SensorTracker.MAX_INSIDE, big.scan(many, 1, COOLDOWN).size(), "at most " + SensorTracker.MAX_INSIDE + " tracked");
        assertEquals(SensorTracker.MAX_INSIDE, big.inside().size());
        for (int round = 0; round < 6; round++) {
            var fresh = new ArrayList<SensorTracker.Seen>();
            for (int i = 0; i < SensorTracker.MAX_INSIDE; i++) fresh.add(new SensorTracker.Seen(UUID.randomUUID(), ZOMBIE));
            big.scan(List.of(), 10 + round * 3, COOLDOWN);
            big.scan(List.of(), 11 + round * 3, COOLDOWN);
            big.scan(fresh, 12 + round * 3, COOLDOWN);
        }
        assertTrue(big.remembered() <= SensorTracker.MAX_REMEMBERED, "the cooldown memory is bounded: " + big.remembered());
    }

    @Test
    void zoneInFrontAndAround() {
        var pos = new BlockPos(10, 64, 10);
        var s = SensorSettings.DEFAULT; // 3×3×3 in front
        var floor = SensorZone.of(pos, Direction.DOWN, s);
        assertEquals(new BlockPos(9, 64, 9), floor.min(), "on the floor: the zone goes up from the sensor");
        assertEquals(new BlockPos(11, 66, 11), floor.max());
        var ceiling = SensorZone.of(pos, Direction.UP, s);
        assertEquals(new BlockPos(9, 62, 9), ceiling.min(), "under a ceiling: down");
        var wall = SensorZone.of(pos, Direction.SOUTH, s.withZone(5, 3, 7, SensorSettings.Placement.FRONT));
        assertEquals(new BlockPos(8, 63, 4), wall.min(), "on a wall to the south it looks north (−Z), 7 deep");
        assertEquals(new BlockPos(12, 65, 10), wall.max());
        var east = SensorZone.of(pos, Direction.WEST, s.withZone(4, 2, 2, SensorSettings.Placement.FRONT));
        assertEquals(new BlockPos(10, 64, 10), east.min(), "looking east (+X); even sizes: the extra block on the positive side");
        assertEquals(new BlockPos(13, 65, 11), east.max());
        var around = SensorZone.of(pos, Direction.SOUTH, s.withZone(9, 5, 9, SensorSettings.Placement.AROUND));
        assertEquals(new BlockPos(6, 62, 6), around.min());
        assertEquals(new BlockPos(14, 66, 14), around.max());
        assertEquals(9, around.sizeX());
        assertEquals(1, floor.chunks().size());
        assertEquals(4, SensorZone.of(new BlockPos(16, 64, 16), Direction.DOWN, s).chunks().size(), "a zone across a chunk corner: 4 chunks");
        assertEquals(new BlockPos(9, 64, 9), floor.closestTo(new BlockPos(0, 0, 0)));
        assertEquals(8, floor.corners().size());
    }

    @Test
    void settingsClampAndPersist() {
        var s = SensorSettings.DEFAULT.withZone(30, 30, 30, SensorSettings.Placement.AROUND).withArmed(false).withAlert(SensorSettings.Alert.CRIT)
            .withFilter(SensorFilter.DEFAULT.withMode(SensorFilter.Mode.HOSTILE).withAllow(List.of("Steve")).withDeny(List.of("Alex")));
        var c = s.clamped(9, 5, 9);
        assertEquals(List.of(9, 5, 9), List.of(c.sizeX(), c.sizeY(), c.sizeZ()), "the server's maximum");
        assertEquals("9×5×9 around", c.zoneText());
        assertEquals(c, SensorSettings.fromJson(c.toJson()));
        assertEquals(SensorSettings.DEFAULT, SensorSettings.fromJson(null));
        var json = c.toJson();
        json.addProperty("x", 999);
        json.addProperty("mode", "dragons");
        json.add("allow", com.google.gson.JsonParser.parseString("[\"ok\", \"no way\", 5]"));
        var read = SensorSettings.fromJson(json);
        assertEquals(SensorSettings.HARD_MAX_XZ, read.sizeX(), "bounded");
        assertEquals(SensorFilter.Mode.ALL, read.filter().mode(), "unknown mode: the default");
        assertEquals(List.of("ok"), read.filter().allow(), "only valid names");
        assertFalse(SensorSettings.DEFAULT.withArmed(false).withRedstone(false).scans(), "disarmed without output: no scans");
    }
}
