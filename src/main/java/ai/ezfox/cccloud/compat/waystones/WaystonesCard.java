package ai.ezfox.cccloud.compat.waystones;

import ai.ezfox.cccloud.access.TeamProviders;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.TeamTrust;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.blay09.mods.waystones.api.WaystonesAPI;
import org.jspecify.annotations.Nullable;

/**
 * Waystones (docs/SPEC.md v0.8 item 8): a "Waystones" mainframe card with the waystones of the mainframe owner and of
 * the owner's team (team members via the team providers), plus global waystones. The list is also overlay data for
 * the network map: the snapshot carries {@code "waystones":{"items":[{"name","dim","x","y","z","kind","global"?}],
 * "truncated"}}. Restricted section ({@code view.positions}): it carries coordinates. Re-read every 10 s; the snapshot
 * is re-sent only when the list changes.
 */
public final class WaystonesCard extends MainframeCard {
    public static final String KIND = "waystones";
    public static final String COUNT = "count";
    public static final String ROW = "waystone";
    /** Shown instead of the rows while there is no waystone. */
    public static final String EMPTY = "empty";
    static final int ROWS = 8;
    /** Waystones in the overlay at most. */
    public static final int MAX_ITEMS = 512;

    record Item(String name, String dim, int x, int y, int z, String kind, boolean global) {
    }

    private final CloudMainframeBlockEntity mainframe;
    private List<Item> items = List.of();
    private boolean truncated;
    private int revision;
    private int refreshes;

    WaystonesCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.WAYSTONES_CARD), WaystonesCard::new);
        ai.ezfox.cccloud.compat.Integrations.test("waystones_card", () -> WaystonesGameTests::card);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Waystones";
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets().label(COUNT, "Waystones", "icon", "home");
        int rows = Math.min(ROWS, items.size());
        if (rows == 0) w.label(EMPTY, "Waystones", "span", 2);
        for (int i = 1; i <= rows; i++) w.label(ROW + i, "Waystone " + i, "span", 2);
        return w.build();
    }

    @Override
    public int revision() {
        return revision;
    }

    /** True if the waystone belongs to the owner or a member of the owner's team. */
    private boolean ours(Waystone waystone, @Nullable UUID owner, @Nullable String provider, @Nullable String teamId) {
        var wsOwner = waystone.getOwnerUid().orElse(null);
        if (wsOwner == null || owner == null) return false;
        if (wsOwner.equals(owner)) return true;
        if (provider == null || teamId == null) return false;
        var p = TeamProviders.get(provider);
        try {
            return p != null && p.rankOf(teamId, wsOwner) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    void reload() {
        var level = mainframe.getLevel();
        if (level == null || level.getServer() == null) return;
        var owner = mainframe.getOwner();
        var binding = TeamTrust.ownerTeam(owner);
        var list = new ArrayList<Item>();
        for (var waystone : WaystonesAPI.getAllWaystones(level.getServer())) {
            if (!waystone.isValid() || waystone.isTransient()) continue;
            boolean global = waystone.getVisibility() == WaystoneVisibility.GLOBAL;
            if (!global && !ours(waystone, owner, binding == null ? null : binding.provider(), binding == null ? null : binding.teamId())) continue;
            var pos = waystone.getPos();
            String name = waystone.hasName() ? waystone.getName().getString() : "Unnamed";
            list.add(new Item(name, waystone.getDimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(),
                waystone.getWaystoneKind().getPath(), global));
        }
        list.sort(Comparator.comparing(Item::dim).thenComparing(Item::name));
        truncated = list.size() > MAX_ITEMS;
        items = truncated ? List.copyOf(list.subList(0, MAX_ITEMS)) : List.copyOf(list);
        revision = items.hashCode() & 0x7fffffff;
    }

    @Override
    public void refresh(CardOutput out) {
        if (refreshes++ % 10 == 0) reload();
        out.text(COUNT, items.size() + (truncated ? "+" : ""));
        if (items.isEmpty()) out.text(EMPTY, "No waystones yet");
        for (int i = 1; i <= Math.min(ROWS, items.size()); i++) {
            var it = items.get(i - 1);
            out.text(ROW + i, it.name() + (it.global() ? " (global)" : "") + " – " + it.dim().substring(it.dim().indexOf(':') + 1)
                + " " + it.x() + ", " + it.y() + ", " + it.z());
        }
    }

    @Override
    public void snapshotExtras(JsonObject snapshot) {
        var json = new JsonObject();
        var array = new JsonArray(items.size());
        for (var it : items) {
            var o = new JsonObject();
            o.addProperty("name", it.name());
            o.addProperty("dim", it.dim());
            o.addProperty("x", it.x());
            o.addProperty("y", it.y());
            o.addProperty("z", it.z());
            o.addProperty("kind", it.kind());
            if (it.global()) o.addProperty("global", true);
            array.add(o);
        }
        json.add("items", array);
        json.addProperty("truncated", truncated);
        snapshot.add("waystones", json);
    }

    /** Test hook. */
    List<Item> items() {
        return items;
    }
}
