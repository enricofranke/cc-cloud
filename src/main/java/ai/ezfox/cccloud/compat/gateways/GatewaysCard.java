package ai.ezfox.cccloud.compat.gateways;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.gateways.gate.normal.NormalGateway;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.AABB;

/**
 * Gateways to Eternity (docs/SPEC.md v0.8 item 4): a "Gateways" mainframe card with the gates open within
 * {@link #RANGE} blocks of the mainframe (same dimension): name, wave n / total, enemies alive, lives left and distance.
 * Alerts when a gate opens, completes or fails. Restricted section (needs {@code view.positions}), because it tells
 * where fights happen. Gateways are entities, not blocks, so this is a mainframe card instead of a device card.
 */
public final class GatewaysCard extends MainframeCard {
    public static final String KIND = "gateways";
    public static final String ACTIVE = "active";
    public static final String COUNT = "count";
    public static final String GATE = "gate";
    static final int ROWS = 6;
    static final int RANGE = 256;

    private record Seen(String name, boolean completed) {
    }

    private final CloudMainframeBlockEntity mainframe;
    private Map<UUID, Seen> seen = new HashMap<>();
    private boolean first = true;

    GatewaysCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.GATEWAYS_CARD), GatewaysCard::new);
        ai.ezfox.cccloud.compat.Integrations.test("gateways_card", () -> GatewaysGameTests::card);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Gateways";
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets()
            .indicator(ACTIVE, "Gate open", "onColor", "red", "offColor", "gray")
            .label(COUNT, "Open gates");
        for (int i = 1; i <= ROWS; i++) w.label(GATE + i, "Gate " + i, "span", 2);
        return w.build();
    }

    /** Readable gate name (dedicated servers have no translations of other mods). */
    static String name(GatewayEntity gate) {
        String name = gate.getName().getString();
        if (name.contains(".") && !name.contains(" ")) {
            String last = name.substring(name.lastIndexOf('.') + 1);
            name = Fmt.title(last.replace('/', '_'));
        }
        return name;
    }

    static String describe(GatewayEntity gate, double distance) {
        String waves = gate.getGateway() instanceof NormalGateway normal ? gate.getWave() + 1 + " / " + normal.getNumWaves() : String.valueOf(gate.getWave() + 1);
        String state = gate.isCompleted() ? "completed" : gate.isWaveActive() ? "wave " + waves + ", " + gate.getActiveEnemies() + " enemies" : "wave " + waves + " starting";
        return name(gate) + ": " + state + ", " + gate.getRemainingLives() + " lives – " + Math.round(distance) + " m";
    }

    @Override
    public void refresh(CardOutput out) {
        var level = mainframe.getLevel();
        if (level == null) return;
        var center = mainframe.getBlockPos().getCenter();
        var gates = new ArrayList<>(level.getEntitiesOfClass(GatewayEntity.class, new AABB(center, center).inflate(RANGE), GatewayEntity::isAlive));
        gates.sort(Comparator.comparingDouble(g -> g.distanceToSqr(center)));
        var now = new HashMap<UUID, Seen>();
        int row = 1;
        for (var gate : gates) {
            now.put(gate.getUUID(), new Seen(name(gate), gate.isCompleted()));
            if (!first && !seen.containsKey(gate.getUUID())) out.alert("Gateway opened: " + name(gate), "info");
            if (row <= ROWS) out.text(GATE + row++, describe(gate, Math.sqrt(gate.distanceToSqr(center))));
        }
        for (; row <= ROWS; row++) out.text(GATE + row, "–");
        for (var e : seen.entrySet()) {
            if (now.containsKey(e.getKey())) continue;
            if (e.getValue().completed()) out.alert("Gateway completed: " + e.getValue().name(), "info");
            else out.alert("Gateway closed: " + e.getValue().name(), "warn");
        }
        seen = now;
        first = false;
        out.bool(ACTIVE, !gates.isEmpty());
        out.text(COUNT, String.valueOf(gates.size()));
    }
}
