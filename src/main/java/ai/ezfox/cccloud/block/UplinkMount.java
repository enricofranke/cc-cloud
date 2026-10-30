package ai.ezfox.cccloud.block;

import com.mojang.math.OctahedralGroup;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * How an uplink sits on the face it is attached to (blockstate {@code mount}, docs/MODELS.md "Compact uplink"): the
 * 14x14 plate on faces that carry it, otherwise an 8x6 puck placed on the visible part of the face (slab sides,
 * tray-height machines such as solar panels, carpet-height blocks), or a puck on a post when the face lies further
 * back than 1 px (the top of a solar panel or slab is below the uplink's cell). Computed on the server from the
 * attached block's outline shape; the models come from tools/gen_models.py and use the same boxes as
 * {@link #northShape()}.
 */
public enum UplinkMount implements StringRepresentable {
    FULL("full", 0, 0),
    BOTTOM("bottom", 0, 0),
    LOW("low", 1, 0),
    CENTER("center", 5, 0),
    HIGH("high", 9, 0),
    TOP("top", 10, 0),
    POST4("post4", 5, 4),
    POST8("post8", 5, 8),
    POST12("post12", 5, 12),
    POST16("post16", 5, 16);

    public static final EnumProperty<UplinkMount> PROPERTY = EnumProperty.create("mount", UplinkMount.class);

    /** Flush positions, tried in this order (ties go to the one nearest the face's centre, then the earlier one). */
    private static final UplinkMount[] SIDES = {BOTTOM, LOW, CENTER, HIGH, TOP};
    private static final UplinkMount[] POSTS = {POST4, POST8, POST12, POST16};
    /** A face set back by at most this many pixels counts as flush (chests are 1 px in). */
    static final double FLUSH_PX = 1;
    /** Share of the plate area (or of its 1 px outline, e.g. a hopper rim) a flush face must cover for the plate. */
    static final double FULL_COVERAGE = 0.9;

    /** Blockstate rotation per facing (same as the models and {@link Shapes#rotateAll}). */
    private static final Map<Direction, OctahedralGroup> ROTATION = new EnumMap<>(Map.of(
        Direction.NORTH, OctahedralGroup.IDENTITY, Direction.EAST, OctahedralGroup.BLOCK_ROT_Y_90,
        Direction.SOUTH, OctahedralGroup.BLOCK_ROT_Y_180, Direction.WEST, OctahedralGroup.BLOCK_ROT_Y_270,
        Direction.UP, OctahedralGroup.BLOCK_ROT_X_270, Direction.DOWN, OctahedralGroup.BLOCK_ROT_X_90));

    private final String name;
    /** Bottom edge of the puck in model pixels. */
    private final int y;
    /** Post length in pixels (0 = flush). */
    private final int post;
    private Map<Direction, VoxelShape> shapes;

    UplinkMount(String name, int y, int post) {
        this.name = name;
        this.y = y;
        this.post = post;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean compact() {
        return this != FULL;
    }

    /** How far the post reaches back into the attached block's cell, in pixels (0 = flush). */
    public int post() {
        return post;
    }

    /**
     * Shape when attached to the block to the north (model space, +Z away from that block): the plate, the puck, or
     * the puck with its post and foot (which reach into the attached block's cell).
     */
    public VoxelShape northShape() {
        if (this == FULL) return Block.box(1, 1, 0, 15, 15, 2.25);
        VoxelShape puck = Block.box(4, y, 0, 12, y + 6, 2.25);
        if (post == 0) return puck;
        return Shapes.or(puck, Block.box(7, 7, 1 - post, 9, 9, 0), Block.box(6, 6, -post, 10, 10, 1 - post));
    }

    public VoxelShape shape(Direction facing) {
        var map = shapes;
        if (map == null) shapes = map = Shapes.rotateAll(northShape());
        return map.get(facing);
    }

    // ---- choosing the mount ------------------------------------------------------------------------------------------

    /** The mount for an uplink at {@code uplinkPos} that sticks to the block on its {@code facing} side. */
    public static UplinkMount compute(BlockGetter level, BlockPos uplinkPos, Direction facing) {
        BlockPos attachedPos = uplinkPos.relative(facing);
        return compute(level.getBlockState(attachedPos), level, attachedPos, facing);
    }

    public static UplinkMount compute(BlockState attached, BlockGetter level, BlockPos attachedPos, Direction facing) {
        if (attached.isAir()) return FULL;
        try {
            if (attached.isFaceSturdy(level, attachedPos, facing.getOpposite())) return FULL;
            return choose(modelBoxes(attached.getShape(level, attachedPos), facing));
        } catch (RuntimeException e) {
            // Another mod's shape code failed (e.g. a block entity that is not there yet): keep the plate.
            return FULL;
        }
    }

    /**
     * The attached block's outline boxes in the uplink's model space, in pixels: {@code {x0, y0, z0, x1, y1, z1}}; the
     * attached block occupies z -16..0 and its face towards the uplink lies at z 0 when it is flush.
     */
    static List<double[]> modelBoxes(VoxelShape attachedShape, Direction facing) {
        VoxelShape relative = attachedShape.move(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        VoxelShape model = Shapes.rotate(relative, ROTATION.get(facing).inverse());
        var out = new ArrayList<double[]>();
        model.forAllBoxes((x0, y0, z0, x1, y1, z1) -> out.add(new double[]{x0 * 16, y0 * 16, z0 * 16, x1 * 16, y1 * 16, z1 * 16}));
        return out;
    }

    /** Pure rule on model-space boxes (see {@link #modelBoxes}); unit tested. */
    static UplinkMount choose(List<double[]> boxes) {
        if (boxes.isEmpty()) return CENTER;
        double gap = Double.MAX_VALUE;
        for (var b : boxes) gap = Math.min(gap, depth(b));
        if (gap > FLUSH_PX) {
            for (var m : POSTS) if (m.post >= gap - 1e-6) return m;
            return POST16;
        }
        // footprint of the front layer (everything within FLUSH_PX of the nearest part) on the 16x16 pixel grid
        boolean[] cover = new boolean[256];
        for (var b : boxes) {
            if (depth(b) > gap + FLUSH_PX) continue;
            for (int py = 0; py < 16; py++) {
                if (b[1] >= py + 1 - 1e-6 || b[4] <= py + 1e-6) continue;
                for (int px = 0; px < 16; px++) if (b[0] < px + 1 - 1e-6 && b[3] > px + 1e-6) cover[py * 16 + px] = true;
            }
        }
        int plate = count(cover, 1, 1, 15, 15);
        int ring = plate - count(cover, 2, 2, 14, 14);
        if (plate >= FULL_COVERAGE * 14 * 14 || ring >= FULL_COVERAGE * (14 * 14 - 12 * 12)) return FULL;
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 256; i++) {
            if (!cover[i]) continue;
            sum += i / 16 + 0.5;
            n++;
        }
        double centre = n == 0 ? 8 : sum / n;
        UplinkMount best = CENTER;
        int bestOverlap = 0;
        double bestDistance = Double.MAX_VALUE;
        for (var m : SIDES) {
            int overlap = count(cover, 4, m.y, 12, m.y + 6);
            double distance = Math.abs(m.y + 3 - centre);
            if (overlap > bestOverlap || overlap == bestOverlap && overlap > 0 && distance < bestDistance - 1e-9) {
                best = m;
                bestOverlap = overlap;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** How far behind the uplink's cell boundary (z 0) a box ends, in pixels. */
    private static double depth(double[] box) {
        return Math.max(0, -box[5]);
    }

    private static int count(boolean[] cover, int x0, int y0, int x1, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (cover[y * 16 + x]) n++;
        return n;
    }
}
