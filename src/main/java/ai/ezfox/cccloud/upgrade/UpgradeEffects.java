package ai.ezfox.cccloud.upgrade;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Effective limits of a mainframe from its installed upgrades (docs/SPEC.md "Mainframe upgrades"). Pure Java, unit
 * tested. One card per type counts: the highest tier of each type wins. The Quantum Core makes devices and browsers
 * unlimited, turns the mainframe into an Ender node and sets the FE factor to ×0.25 (the lower of it and Efficiency).
 *
 * @param devices      device capacity (uplinks + bridges + relays); {@link #UNLIMITED} with a Quantum Core
 * @param browsers     concurrent dashboard browsers; {@link #UNLIMITED} with a Quantum Core
 * @param rules        rules per mainframe
 * @param historyHours history retention (hours)
 * @param rangeFactor  mainframe node range ×
 * @param feFactor     mainframe FE usage ×
 * @param enderNode    the mainframe links to its Ender Relays across any distance/dimension (Quantum Core)
 * @param chunkAnchor  a Chunk Anchor card is installed (it only works when the server enables it)
 */
public record UpgradeEffects(int devices, int browsers, int rules, int historyHours, double rangeFactor, double feFactor,
                             boolean enderNode, boolean chunkAnchor, Map<UpgradeType, UpgradeTier> tiers, boolean quantum) {
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /** Base limits without upgrades (config). */
    public record Base(int devices, int browsers, int rules, int historyHours) {
        public static final Base DEFAULT = new Base(16, 4, 8, 1);
    }

    /** One installed card. */
    public record Card(UpgradeType type, UpgradeTier tier) {
    }

    public static UpgradeEffects of(Base base, Collection<Card> cards, boolean quantum, boolean chunkAnchor) {
        var tiers = new EnumMap<UpgradeType, UpgradeTier>(UpgradeType.class);
        for (var card : cards) tiers.merge(card.type(), card.tier(), (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
        int devices = Math.max(base.devices(), pick(tiers.get(UpgradeType.CAPACITY), 48, 128, 512, 0));
        int browsers = Math.max(base.browsers(), pick(tiers.get(UpgradeType.BANDWIDTH), 8, 16, 64, 0));
        double range = pickD(tiers.get(UpgradeType.RANGE), 1.5, 2, 3, 1);
        double fe = pickD(tiers.get(UpgradeType.EFFICIENCY), 0.8, 0.65, 0.5, 1);
        var memory = tiers.get(UpgradeType.MEMORY);
        int rules = Math.max(base.rules(), pick(memory, 24, 64, 128, 0));
        int history = Math.max(base.historyHours(), pick(memory, 24, 7 * 24, 30 * 24, 0));
        if (quantum) {
            devices = UNLIMITED;
            browsers = UNLIMITED;
            fe = Math.min(fe, 0.25);
        }
        return new UpgradeEffects(devices, browsers, rules, history, range, fe, quantum, chunkAnchor, Map.copyOf(tiers), quantum);
    }

    /** The same cards on other base limits (config reload of {@code [upgrades]} base*). */
    public UpgradeEffects withBase(Base base) {
        var cards = new java.util.ArrayList<Card>();
        for (var e : tiers.entrySet()) cards.add(new Card(e.getKey(), e.getValue()));
        return of(base, cards, quantum, chunkAnchor);
    }

    public static UpgradeEffects none(Base base) {
        return of(base, java.util.List.of(), false, false);
    }

    private static int pick(UpgradeTier tier, int a, int v, int u, int none) {
        if (tier == null) return none;
        return switch (tier) {
            case ALLTHEMODIUM -> a;
            case VIBRANIUM -> v;
            case UNOBTAINIUM -> u;
        };
    }

    private static double pickD(UpgradeTier tier, double a, double v, double u, double none) {
        if (tier == null) return none;
        return switch (tier) {
            case ALLTHEMODIUM -> a;
            case VIBRANIUM -> v;
            case UNOBTAINIUM -> u;
        };
    }
}
