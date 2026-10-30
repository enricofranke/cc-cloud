package ai.ezfox.cccloud.icons;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Stand-ins for the vanilla parent models that mod models build on ({@code minecraft:item/generated},
 * {@code minecraft:block/cube_all}, …). A dedicated server has no vanilla models (the server jar ships no assets), but
 * about two thirds of all modded item models in a big pack only name one of these as their parent and bring their
 * own textures. Only the structure is described here – which face shows which texture variable, the inventory view,
 * mirrored or rotated faces – written for this renderer; no Mojang file is copied. When the real model is readable
 * (from {@code vanillaAssetsJar} or the downloaded client jar) it wins.
 */
final class BuiltinModels {
    private static final Map<String, String> MODELS = new HashMap<>();

    static {
        put("item/generated", "{'parent':'builtin/generated','gui_light':'front'}");
        put("item/handheld", "{'parent':'item/generated'}");
        put("item/handheld_rod", "{'parent':'item/handheld'}");
        put("item/handheld_mace", "{'parent':'item/handheld'}");
        put("item/template_music_disc", "{'parent':'item/generated'}");
        // The inventory view of blocks: 30° down, turned 225°, 0.625 of a slot; lit from the side.
        put("block/block", "{'gui_light':'side','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[0.625,0.625,0.625]}}}");
        put("block/cube", "{'parent':'block/block','elements':[" + cube("#down", "#up", "#north", "#south", "#west", "#east", "") + "]}");
        put("block/cube_all", textures("block/cube", "#all", "#all", "#all", "#all", "#all", "#all"));
        put("block/cube_all_inner_faces", "{'parent':'block/cube_all'}"); // the inner faces are never seen in the inventory
        put("block/cube_bottom_top", textures("block/cube", "#bottom", "#top", "#side", "#side", "#side", "#side"));
        put("block/cube_bottom_top_inner_faces", "{'parent':'block/cube_bottom_top'}");
        put("block/cube_column", textures("block/cube", "#end", "#end", "#side", "#side", "#side", "#side"));
        put("block/cube_top", textures("block/cube", "#side", "#top", "#side", "#side", "#side", "#side"));
        put("block/orientable_with_bottom", textures("block/cube", "#bottom", "#top", "#front", "#side", "#side", "#side"));
        put("block/orientable", "{'parent':'block/orientable_with_bottom','textures':{'bottom':'#top'}}");
        put("block/orientable_vertical", textures("block/cube", "#side", "#front", "#side", "#side", "#side", "#side"));
        put("block/cube_column_horizontal", "{'parent':'block/block','textures':{'particle':'#side','down':'#end','up':'#end','north':'#side',"
            + "'east':'#side','south':'#side','west':'#side'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{"
            + "'down':{'texture':'#down'},'up':{'texture':'#up','rotation':180},'north':{'texture':'#north'},'south':{'texture':'#south'},"
            + "'west':{'texture':'#west'},'east':{'texture':'#east'}}}]}");
        put("block/cube_directional", "{'parent':'block/block','elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{"
            + "'down':{'texture':'#down','rotation':180},'up':{'texture':'#up'},'north':{'texture':'#north'},'south':{'texture':'#south'},"
            + "'west':{'texture':'#west','rotation':270},'east':{'texture':'#east','rotation':90}}}]}");
        // Mirrored cubes have no parent in vanilla, so they keep the untransformed (front) view.
        put("block/cube_mirrored", "{'elements':[" + cube("#down", "#up", "#north", "#south", "#west", "#east", ",'uv':[16,0,0,16]") + "]}");
        put("block/cube_mirrored_all", textures("block/cube_mirrored", "#all", "#all", "#all", "#all", "#all", "#all"));
        put("block/cube_column_mirrored", textures("block/cube_mirrored", "#end", "#end", "#side", "#side", "#side", "#side"));
        put("block/cube_north_west_mirrored", "{'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{'down':{'texture':'#down'},"
            + "'up':{'texture':'#up'},'north':{'texture':'#north','uv':[16,0,0,16]},'south':{'texture':'#south'},"
            + "'west':{'texture':'#west','uv':[16,0,0,16]},'east':{'texture':'#east'}}}]}");
        put("block/cube_north_west_mirrored_all", textures("block/cube_north_west_mirrored", "#all", "#all", "#all", "#all", "#all", "#all"));
        put("block/leaves", "{'parent':'block/block','elements':[" + cube("#all", "#all", "#all", "#all", "#all", "#all", ",'tintindex':0") + "]}");
        put("block/template_glazed_terracotta", "{'parent':'block/cube','elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{"
            + "'down':{'texture':'#pattern'},'up':{'texture':'#pattern'},'north':{'texture':'#pattern','rotation':90},"
            + "'south':{'texture':'#pattern','rotation':270},'west':{'texture':'#pattern'},'east':{'texture':'#pattern','rotation':180}}}]}");
    }

    private BuiltinModels() {
    }

    private static void put(String path, String json) {
        MODELS.put(path, json.replace('\'', '"'));
    }

    private static String cube(String down, String up, String north, String south, String west, String east, String extra) {
        return "{'from':[0,0,0],'to':[16,16,16],'faces':{'down':{'texture':'" + down + "'" + extra + "},'up':{'texture':'" + up + "'" + extra
            + "},'north':{'texture':'" + north + "'" + extra + "},'south':{'texture':'" + south + "'" + extra + "},'west':{'texture':'" + west
            + "'" + extra + "},'east':{'texture':'" + east + "'" + extra + "}}}";
    }

    private static String textures(String parent, String down, String up, String north, String south, String west, String east) {
        return "{'parent':'" + parent + "','textures':{'down':'" + down + "','up':'" + up + "','north':'" + north + "','south':'" + south
            + "','west':'" + west + "','east':'" + east + "'}}";
    }

    /** The stand-in for {@code minecraft:<path>} as JSON bytes, or null if there is none. */
    static byte @Nullable [] get(String path) {
        var json = MODELS.get(path);
        return json == null ? null : json.getBytes(StandardCharsets.UTF_8);
    }

    static boolean has(String path) {
        return MODELS.containsKey(path);
    }
}
