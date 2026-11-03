package ai.ezfox.cccloud.layout;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World SavedData ({@code data/cccloud/layouts.dat}): the dashboard layout of each mainframe, as validated JSON
 * (max 64 KB each). v1 documents are migrated to v2 at server start when the mainframe's sections are known (cached
 * sections are published before the layouts). Server thread only; the {@link WebHub} keeps a copy for the Netty threads.
 */
public final class LayoutData extends SavedData {
    private static final Codec<LayoutData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("layouts", Map.of()).forGetter(d -> Map.copyOf(d.layouts))
    ).apply(i, LayoutData::new));

    public static final SavedDataType<LayoutData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(CCCloud.MODID, "layouts"), LayoutData::new, CODEC
    );

    private final Map<UUID, String> layouts = new HashMap<>();

    public LayoutData() {
    }

    private LayoutData(Map<UUID, String> layouts) {
        this.layouts.putAll(layouts);
    }

    public static LayoutData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Pushes all stored layouts into the hub (server start). Invalid entries (e.g. from an older version) are dropped. */
    public void publishAll() {
        var it = layouts.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            JsonObject layout = null;
            try {
                var result = LayoutValidator.validate(JsonParser.parseString(entry.getValue()));
                layout = result.layout();
            } catch (RuntimeException ignored) {
            }
            if (layout == null) {
                CCCloud.LOGGER.warn("[cccloud] Dropping invalid dashboard layout of mainframe {}", entry.getKey());
                it.remove();
                setDirty();
                continue;
            }
            var migrated = migrate(layout, WebHub.INSTANCE.sectionSnapshots(entry.getKey()));
            if (migrated != null) {
                layout = migrated;
                entry.setValue(Json.GSON.toJson(layout));
                setDirty();
                CCCloud.LOGGER.info("[cccloud] Migrated the dashboard layout of mainframe {} to v2", entry.getKey());
            }
            WebHub.INSTANCE.setLayout(entry.getKey(), layout);
        }
    }

    /**
     * A v1 layout migrated to v2 (docs/SPEC.md "Layout schema v2") for the given section snapshots, or null when nothing
     * is to be done: already v2, no sections known yet (the dashboard migrates on its first save then), or the result
     * would not validate (the v1 document stays, it is still accepted).
     */
    static @Nullable JsonObject migrate(JsonObject layout, java.util.List<JsonObject> sections) {
        if (layout.get("v").getAsInt() != 1 || sections.isEmpty()) return null;
        try {
            var result = LayoutValidator.validate(LayoutMigration.migrate(sections, layout));
            return result.layout();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.warn("[cccloud] Could not migrate a dashboard layout to v2", e);
            return null;
        }
    }

    /** Stores (or with null: resets) a validated layout and broadcasts it. */
    public void set(UUID mainframeId, @Nullable JsonObject layout) {
        if (layout == null) {
            layouts.remove(mainframeId);
        } else {
            layouts.put(mainframeId, Json.GSON.toJson(layout));
        }
        setDirty();
        WebHub.INSTANCE.publishLayout(mainframeId, layout);
    }

    /**
     * Sets (with null: removes) a card's own design in the mainframe's layout, as the editor saves it: bg, bgColor, css
     * and parts are replaced as a whole, so the card no longer shows its program's design ({@code dash:style}) until
     * the design is removed again. For Cloud Scripts ({@code cloud.setStyle}); {@code design} is already checked. A
     * design equal to the saved one changes nothing and does not count against the layout save limit. A mainframe
     * without a layout gets an empty v2 document; a v1 layout is migrated first. Returns null or an error code.
     */
    public static @Nullable String setCardDesign(MinecraftServer server, UUID mainframeId, String cardKey, @Nullable JsonObject design) {
        var current = WebHub.INSTANCE.layout(mainframeId);
        var layout = current != null ? current : emptyLayout();
        if (layout.get("v").getAsInt() != 2) {
            layout = migrate(layout, WebHub.INSTANCE.sectionSnapshots(mainframeId));
            if (layout == null) return "old_layout";
        }
        if (!(layout.get("cards") instanceof JsonObject cards)) return "invalid_layout";
        var card = cards.get(cardKey) instanceof JsonObject c ? c : new JsonObject();
        if (!CardStyle.putDesign(card, design)) return null;
        if (!WebHub.INSTANCE.tryLayoutSave(mainframeId)) return "rate_limited";
        if (card.isEmpty()) cards.remove(cardKey);
        else cards.add(cardKey, card);
        var result = LayoutValidator.validate(layout);
        if (result.layout() == null) return "invalid_layout";
        get(server).set(mainframeId, result.layout());
        return null;
    }

    /** An empty v2 layout: no pages, every card in its automatic place. */
    public static JsonObject emptyLayout() {
        var layout = new JsonObject();
        layout.addProperty("v", 2);
        layout.add("pages", new com.google.gson.JsonArray());
        layout.add("cards", new JsonObject());
        layout.add("widgets", new JsonObject());
        return layout;
    }

    /** Whether a mainframe has a stored layout (removal tests). */
    public boolean has(UUID mainframeId) {
        return layouts.containsKey(mainframeId);
    }

    public void remove(UUID mainframeId) {
        if (layouts.remove(mainframeId) != null) setDirty();
    }
}
