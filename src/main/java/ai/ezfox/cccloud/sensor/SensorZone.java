package ai.ezfox.cccloud.sensor;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * The zone of an Advanced Cloud Sensor in world blocks (docs/SPEC.md "Advanced Cloud Sensor"). Sizes are along the
 * world axes (X × Y × Z). {@code in front}: the zone starts at the sensor's own block and reaches {@code size} blocks
 * out of the face the sensor looks out of (away from the block it sits on), centred on the other two axes; {@code
 * around}: centred on the sensor. Even sizes put the extra block on the positive side. Unit tested.
 *
 * @param min lowest corner (inclusive)
 * @param max highest corner (inclusive)
 */
public record SensorZone(BlockPos min, BlockPos max) {
    /**
     * @param facing the direction from the sensor towards the block it sits on (the plate's {@code facing})
     */
    public static SensorZone of(BlockPos pos, Direction facing, SensorSettings s) {
        int[] size = {s.sizeX(), s.sizeY(), s.sizeZ()};
        int[] at = {pos.getX(), pos.getY(), pos.getZ()};
        int[] lo = new int[3];
        int[] hi = new int[3];
        var front = facing.getOpposite();
        int frontAxis = front.getAxis().ordinal(); // X = 0, Y = 1, Z = 2
        for (int a = 0; a < 3; a++) {
            int n = Math.max(1, size[a]);
            if (s.placement() == SensorSettings.Placement.FRONT && a == frontAxis) {
                if (front.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    lo[a] = at[a];
                    hi[a] = at[a] + n - 1;
                } else {
                    lo[a] = at[a] - n + 1;
                    hi[a] = at[a];
                }
            } else {
                lo[a] = at[a] - (n - 1) / 2;
                hi[a] = lo[a] + n - 1;
            }
        }
        return new SensorZone(new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2]));
    }

    /** The box an entity query uses (block corners, so an entity whose hitbox touches a zone block counts). */
    public AABB box() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    /** The chunks the zone touches (claims are checked per chunk). */
    public List<ChunkPos> chunks() {
        var list = new ArrayList<ChunkPos>();
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) list.add(new ChunkPos(cx, cz));
        }
        return list;
    }

    /** The point of the zone closest to {@code p} (spawn protection is a square around the spawn point). */
    public BlockPos closestTo(BlockPos p) {
        return new BlockPos(Math.max(min.getX(), Math.min(max.getX(), p.getX())), Math.max(min.getY(), Math.min(max.getY(), p.getY())),
            Math.max(min.getZ(), Math.min(max.getZ(), p.getZ())));
    }

    /** The eight corners (the world border is checked at each). */
    public List<BlockPos> corners() {
        var list = new ArrayList<BlockPos>(8);
        for (int x : new int[]{min.getX(), max.getX()}) {
            for (int y : new int[]{min.getY(), max.getY()}) {
                for (int z : new int[]{min.getZ(), max.getZ()}) list.add(new BlockPos(x, y, z));
            }
        }
        return list;
    }
}
