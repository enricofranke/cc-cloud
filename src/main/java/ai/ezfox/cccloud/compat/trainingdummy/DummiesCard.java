package ai.ezfox.cccloud.compat.trainingdummy;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.trainingdummy.entity.DummyEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Training Dummy (wave 4): a DPS meter. A "Training dummies" mainframe card lists the dummies within {@link #RANGE}
 * blocks of the mainframe (same dimension): damage per second over the last {@link #WINDOW_TICKS} ticks, the last hit,
 * the total of the current streak (hits with less than 5 s between them) and who hit last, plus a DPS chart of the
 * busiest dummy. The dummy mod keeps DPS only on the client, so the card counts the hits itself
 * ({@code LivingDamageEvent.Post}). Restricted like every mainframe card ({@code view.positions}).
 */
public final class DummiesCard extends MainframeCard {
    public static final String KIND = "dummies";
    public static final String HIT = "hit";
    public static final String BEST = "best";
    public static final String DPS = "dps";
    public static final String ROW = "dummy";
    public static final String EMPTY = "empty";
    static final int RANGE = 64;
    static final int ROWS = 4;
    /** DPS window and streak pause: 5 seconds. */
    static final long WINDOW_TICKS = 100;

    /** One hit on a dummy (game time, amount, attacker name or null). */
    record Hit(long tick, float amount, String by) {
    }

    /** Hits per dummy (entity UUID), pruned to the streak; written by the damage event, read by the cards. */
    static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

    static final class Streak {
        final List<Hit> hits = new ArrayList<>();
        long start = -1;
        double total;

        synchronized void add(Hit hit) {
            if (!hits.isEmpty() && hit.tick() - hits.getLast().tick() > WINDOW_TICKS) {
                hits.clear();
                total = 0;
                start = -1;
            }
            if (start < 0) start = hit.tick();
            hits.add(hit);
            total += hit.amount();
            while (hits.size() > 512) hits.removeFirst();
        }

        /** Damage per second over the last window (or the streak so far, if shorter). */
        synchronized double dps(long now) {
            if (hits.isEmpty() || now - hits.getLast().tick() > WINDOW_TICKS) return 0;
            double sum = 0;
            for (var h : hits) if (now - h.tick() <= WINDOW_TICKS) sum += h.amount();
            long span = Math.max(20, Math.min(WINDOW_TICKS, now - start));
            return sum * 20 / span;
        }

        synchronized Hit last() {
            return hits.isEmpty() ? null : hits.getLast();
        }
    }

    private static boolean listening;
    private final CloudMainframeBlockEntity mainframe;
    private int rows;
    private double best;

    DummiesCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> true, DummiesCard::new);
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, DummiesCard::onDamage);
        }
        ai.ezfox.cccloud.compat.Integrations.test("trainingdummy_dps", () -> DummiesGameTests::dps);
    }

    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof DummyEntity dummy) || dummy.level().isClientSide()) return;
        float amount = event.getInflictedDamage();
        if (!(amount > 0) || !Float.isFinite(amount)) return;
        String by = event.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().name()
            : event.getSource().getEntity() != null ? Fmt.title(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getSource().getEntity().getType()).getPath()) : "";
        STREAKS.computeIfAbsent(dummy.getUUID(), u -> new Streak()).add(new Hit(dummy.level().getGameTime(), amount, by));
        if (STREAKS.size() > 1024) STREAKS.clear(); // never grows without bound (dummies removed long ago)
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Training dummies";
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets()
            .indicator(HIT, "Being hit", "onColor", "red", "offColor", "gray")
            .label(BEST, "Best DPS")
            .history(DPS, "DPS", null, null, null, "points", 120, "interval", 1);
        if (rows == 0) w.label(EMPTY, "Dummies");
        for (int i = 1; i <= rows; i++) w.label(ROW + i, "Dummy " + i);
        return w.build();
    }

    @Override
    public int revision() {
        return rows;
    }

    static String describe(DummyEntity dummy, Streak streak, long now, double distance) {
        var last = streak == null ? null : streak.last();
        String name = dummy.hasCustomName() && dummy.getCustomName() != null ? dummy.getCustomName().getString() : "Dummy";
        if (last == null) return name + ": no hits yet – " + Math.round(distance) + " m";
        double dps = streak.dps(now);
        return String.format(Locale.ROOT, "%s: %s DPS, last %s, total %s%s – %d m", name, Fmt.si(Math.round(dps * 10) / 10.0),
            Fmt.si(Math.round(last.amount() * 10) / 10.0), Fmt.si(Math.round(streak.total)), last.by().isEmpty() ? "" : " (" + last.by() + ")", Math.round(distance));
    }

    @Override
    public void refresh(CardOutput out) {
        var level = mainframe.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        var center = mainframe.getBlockPos().getCenter();
        var dummies = new ArrayList<>(level.getEntitiesOfClass(DummyEntity.class, new AABB(center, center).inflate(RANGE), DummyEntity::isAlive));
        dummies.sort(Comparator.comparingDouble(d -> d.distanceToSqr(center)));
        double top = 0;
        boolean hit = false;
        int row = 1;
        for (var dummy : dummies) {
            var streak = STREAKS.get(dummy.getUUID());
            double dps = streak == null ? 0 : streak.dps(now);
            top = Math.max(top, dps);
            if (dps > 0) hit = true;
            if (row <= ROWS) out.text(ROW + row++, describe(dummy, streak, now, Math.sqrt(dummy.distanceToSqr(center))));
        }
        rows = Math.min(ROWS, dummies.size());
        if (rows == 0) out.text(EMPTY, "No training dummy within " + RANGE + " blocks");
        best = Math.max(best, top);
        out.bool(HIT, hit);
        out.text(BEST, Fmt.si(Math.round(best * 10) / 10.0) + " DPS");
        out.number(DPS, Math.round(top * 10) / 10.0);
    }
}
