package ai.ezfox.cccloud.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.core.Direction;

/** Applies the same rotation the blockstates use (see docs/MODELS.md), around the block centre. */
final class Orientation {
    private static final Map<Direction, Transformation> UPLINK = new EnumMap<>(Direction.class);
    private static final Map<Direction, Transformation> MAINFRAME = new EnumMap<>(Direction.class);

    static {
        UPLINK.put(Direction.NORTH, of(Quadrant.R0, Quadrant.R0));
        UPLINK.put(Direction.SOUTH, of(Quadrant.R0, Quadrant.R180));
        UPLINK.put(Direction.EAST, of(Quadrant.R0, Quadrant.R90));
        UPLINK.put(Direction.WEST, of(Quadrant.R0, Quadrant.R270));
        UPLINK.put(Direction.UP, of(Quadrant.R270, Quadrant.R0));
        UPLINK.put(Direction.DOWN, of(Quadrant.R90, Quadrant.R0));
        MAINFRAME.put(Direction.NORTH, of(Quadrant.R0, Quadrant.R0));
        MAINFRAME.put(Direction.EAST, of(Quadrant.R0, Quadrant.R90));
        MAINFRAME.put(Direction.SOUTH, of(Quadrant.R0, Quadrant.R180));
        MAINFRAME.put(Direction.WEST, of(Quadrant.R0, Quadrant.R270));
    }

    private Orientation() {
    }

    private static Transformation of(Quadrant x, Quadrant y) {
        return BlockModelRotation.get(Quadrant.fromXYZAngles(x, y, Quadrant.R0)).transformation();
    }

    static void applyUplink(PoseStack pose, Direction facing) {
        apply(pose, UPLINK.getOrDefault(facing, Transformation.IDENTITY));
    }

    static void applyMainframe(PoseStack pose, Direction facing) {
        apply(pose, MAINFRAME.getOrDefault(facing, Transformation.IDENTITY));
    }

    private static void apply(PoseStack pose, Transformation transformation) {
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(transformation);
        pose.translate(-0.5f, -0.5f, -0.5f);
    }
}
