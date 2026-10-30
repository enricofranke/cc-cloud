package ai.ezfox.cccloud.block;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/** The plate / compact-puck rule (docs/MODELS.md "Compact uplink") and that the hitboxes match the generated models. */
class UplinkMountTest {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static UplinkMount mount(VoxelShape attached, Direction facing) {
        return UplinkMount.choose(UplinkMount.modelBoxes(attached, facing));
    }

    @Test
    void fullBlocksKeepThePlate() {
        for (var d : Direction.values()) assertEquals(UplinkMount.FULL, mount(Shapes.block(), d), d.toString());
        // a chest is 1 px smaller on every side and 14 px tall: still close enough for the plate
        var chest = Block.box(1, 0, 1, 15, 14, 15);
        for (var d : SIDES) assertEquals(UplinkMount.FULL, mount(chest, d), d.toString());
        // a hopper's top is a 2 px rim around the bowl: the plate rests on the rim like a lid
        var hopper = Shapes.or(Shapes.join(Block.box(0, 10, 0, 16, 16, 16), Block.box(2, 11, 2, 14, 16, 14), BooleanOp.ONLY_FIRST),
            Block.box(4, 4, 4, 12, 10, 12), Block.box(6, 0, 6, 10, 4, 10));
        assertEquals(UplinkMount.FULL, mount(hopper, Direction.DOWN));
    }

    @Test
    void lowBlocksGetThePuckOnTheirVisiblePart() {
        var slab = Block.box(0, 0, 0, 16, 8, 16);
        var solar = Block.box(0, 0, 0, 16, 4, 16);           // Energized Power solar panel
        var tray = Block.box(0, 0, 0, 16, 6, 16);            // daylight detector
        var carpet = Block.box(0, 0, 0, 16, 1, 16);
        var thin = Block.box(0, 0, 0, 16, 0.5, 16);          // Powah solar panel
        var topSlab = Block.box(0, 8, 0, 16, 16, 16);
        var hopperBowl = Block.box(0, 10, 0, 16, 16, 16);
        for (var d : SIDES) {
            assertEquals(UplinkMount.LOW, mount(slab, d), "slab side " + d);
            assertEquals(UplinkMount.BOTTOM, mount(solar, d), "solar panel side " + d);
            assertEquals(UplinkMount.BOTTOM, mount(tray, d), "tray side " + d);
            assertEquals(UplinkMount.BOTTOM, mount(carpet, d), "carpet side " + d);
            assertEquals(UplinkMount.BOTTOM, mount(thin, d), "0.5 px side " + d);
            assertEquals(UplinkMount.HIGH, mount(topSlab, d), "top slab side " + d);
            assertEquals(UplinkMount.TOP, mount(hopperBowl, d), "hopper bowl side " + d);
        }
        // a narrow centred face (e.g. a 4 px wide pillar flush with this side)
        assertEquals(UplinkMount.CENTER, mount(Block.box(6, 0, 0, 10, 16, 16), Direction.NORTH));
        assertEquals(UplinkMount.CENTER, mount(Shapes.empty(), Direction.NORTH));
    }

    @Test
    void insetFacesGetAPost() {
        // the uplink sits in the cell above (facing down): the face is 16 - height px below it
        assertEquals(UplinkMount.POST8, mount(Block.box(0, 0, 0, 16, 8, 16), Direction.DOWN));
        assertEquals(UplinkMount.POST12, mount(Block.box(0, 0, 0, 16, 4, 16), Direction.DOWN));
        assertEquals(UplinkMount.POST12, mount(Block.box(0, 0, 0, 16, 6, 16), Direction.DOWN));
        assertEquals(UplinkMount.POST16, mount(Block.box(0, 0, 0, 16, 1, 16), Direction.DOWN));
        // under a top slab (facing up) and the slab's flush faces
        assertEquals(UplinkMount.POST8, mount(Block.box(0, 8, 0, 16, 16, 16), Direction.UP));
        assertEquals(UplinkMount.FULL, mount(Block.box(0, 8, 0, 16, 16, 16), Direction.DOWN));
        assertEquals(UplinkMount.FULL, mount(Block.box(0, 0, 0, 16, 8, 16), Direction.UP));
        // a 12x12 machine standing 2 px back from this side
        assertEquals(UplinkMount.POST4, mount(Block.box(2, 0, 2, 14, 16, 14), Direction.EAST));
        assertEquals(4, UplinkMount.POST4.post());
    }

    @Test
    void positionAndHitboxAgreeForEveryFacing() {
        // Only the south half of the block above carries a face: the puck must end up under that half in the world.
        var southHalf = Block.box(0, 0, 8, 16, 16, 16);
        var m = mount(southHalf, Direction.UP);
        assertTrue(m == UplinkMount.HIGH || m == UplinkMount.TOP, m.toString());
        AABB box = m.shape(Direction.UP).bounds();
        assertTrue(box.minZ >= 8 / 16.0 && box.maxY == 1.0, box.toString());
        // same on a side: a block whose west face only exists in its upper half
        var upper = Block.box(0, 8, 0, 16, 16, 16);
        for (var d : SIDES) {
            AABB b = mount(upper, d).shape(d).bounds();
            assertTrue(b.minY >= 8 / 16.0, d + " " + b);
        }
    }

    @Test
    void hitboxesMatchTheGeneratedModels() throws IOException {
        for (var m : UplinkMount.values()) {
            String model = m == UplinkMount.FULL ? "uplink" : "uplink_compact_" + m.getSerializedName();
            var json = model(model);
            double[] min = {99, 99, 99}, max = {-99, -99, -99};
            for (var e : json.getAsJsonArray("elements")) {
                var from = e.getAsJsonObject().getAsJsonArray("from");
                var to = e.getAsJsonObject().getAsJsonArray("to");
                for (int i = 0; i < 3; i++) {
                    min[i] = Math.min(min[i], from.get(i).getAsDouble());
                    max[i] = Math.max(max[i], to.get(i).getAsDouble());
                }
            }
            AABB shape = m.northShape().bounds();
            assertEquals(new AABB(min[0] / 16, min[1] / 16, min[2] / 16, max[0] / 16, max[1] / 16, max[2] / 16), shape, model);
            // the post (if any) is part of the hitbox, not just its bounding box
            if (m.post() > 0) assertTrue(m.northShape().toAabbs().stream().anyMatch(b -> b.contains(new Vec3(0.5, 0.5, -m.post() / 32.0))), model);
            for (var d : Direction.values()) assertFalse(m.shape(d).isEmpty());
        }
    }

    @Test
    void blockstatesUseTheEnumValues() throws IOException {
        // a value the enum does not know would only show up as a model loading error on a client
        var expected = new java.util.TreeSet<String>();
        for (var m : UplinkMount.values()) expected.add(m.getSerializedName());
        for (var block : new String[]{"cloud_uplink", "advanced_cloud_uplink"}) {
            var seen = new java.util.TreeSet<String>();
            for (var part : json("assets/cccloud/blockstates/" + block + ".json").getAsJsonArray("multipart")) {
                seen.add(part.getAsJsonObject().getAsJsonObject("when").get("mount").getAsString());
            }
            assertEquals(expected, seen, block);
        }
    }

    private static JsonObject model(String name) throws IOException {
        return json("assets/cccloud/models/block/" + name + ".json");
    }

    private static JsonObject json(String rel) throws IOException {
        InputStream in = UplinkMountTest.class.getClassLoader().getResourceAsStream(rel);
        if (in == null) in = Files.newInputStream(Path.of("src/main/resources", rel));
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
