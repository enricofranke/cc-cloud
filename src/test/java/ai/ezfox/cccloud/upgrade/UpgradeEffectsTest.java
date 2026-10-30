package ai.ezfox.cccloud.upgrade;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class UpgradeEffectsTest {
    private static final UpgradeEffects.Base BASE = UpgradeEffects.Base.DEFAULT;

    private static UpgradeEffects.Card card(UpgradeType type, UpgradeTier tier) {
        return new UpgradeEffects.Card(type, tier);
    }

    @Test
    void rebaseKeepsTheCards() {
        // config reload of [upgrades] base*: the published effects follow the new base limits, the cards still count
        var cards = UpgradeEffects.of(BASE, List.of(card(UpgradeType.BANDWIDTH, UpgradeTier.ALLTHEMODIUM), card(UpgradeType.RANGE, UpgradeTier.VIBRANIUM)),
            false, true);
        var raised = cards.withBase(new UpgradeEffects.Base(100, 20, 30, 48));
        assertEquals(100, raised.devices());
        assertEquals(20, raised.browsers(), "base above the card's 8");
        assertEquals(30, raised.rules());
        assertEquals(48, raised.historyHours());
        assertEquals(2, raised.rangeFactor());
        assertTrue(raised.chunkAnchor());
        var lowered = cards.withBase(new UpgradeEffects.Base(2, 1, 1, 1));
        assertEquals(8, lowered.browsers(), "the Bandwidth card still gives 8");
        assertEquals(2, lowered.devices());
        var quantum = UpgradeEffects.of(BASE, List.of(), true, false).withBase(new UpgradeEffects.Base(1, 1, 1, 1));
        assertEquals(UpgradeEffects.UNLIMITED, quantum.devices());
        assertEquals(UpgradeEffects.none(BASE), UpgradeEffects.none(new UpgradeEffects.Base(5, 5, 5, 5)).withBase(BASE));
    }

    @Test
    void baseWithoutCards() {
        var e = UpgradeEffects.none(BASE);
        assertEquals(16, e.devices());
        assertEquals(4, e.browsers());
        assertEquals(8, e.rules());
        assertEquals(1, e.historyHours());
        assertEquals(1, e.rangeFactor());
        assertEquals(1, e.feFactor());
        assertFalse(e.enderNode() || e.chunkAnchor() || e.quantum());
    }

    @Test
    void tableValues() {
        var e = UpgradeEffects.of(BASE, List.of(card(UpgradeType.CAPACITY, UpgradeTier.VIBRANIUM), card(UpgradeType.BANDWIDTH, UpgradeTier.UNOBTAINIUM),
            card(UpgradeType.RANGE, UpgradeTier.ALLTHEMODIUM), card(UpgradeType.EFFICIENCY, UpgradeTier.VIBRANIUM)), false, false);
        assertEquals(128, e.devices());
        assertEquals(64, e.browsers());
        assertEquals(1.5, e.rangeFactor());
        assertEquals(0.65, e.feFactor());
        var memory = UpgradeEffects.of(BASE, List.of(card(UpgradeType.MEMORY, UpgradeTier.UNOBTAINIUM)), false, false);
        assertEquals(128, memory.rules());
        assertEquals(30 * 24, memory.historyHours());
        assertEquals(24, UpgradeEffects.of(BASE, List.of(card(UpgradeType.MEMORY, UpgradeTier.ALLTHEMODIUM)), false, false).historyHours());
    }

    @Test
    void highestTierOfATypeWins() {
        var e = UpgradeEffects.of(BASE, List.of(card(UpgradeType.CAPACITY, UpgradeTier.UNOBTAINIUM), card(UpgradeType.CAPACITY, UpgradeTier.ALLTHEMODIUM)),
            false, false);
        assertEquals(512, e.devices(), "cards of one type do not add up");
        assertEquals(UpgradeTier.UNOBTAINIUM, e.tiers().get(UpgradeType.CAPACITY));
    }

    @Test
    void quantumCore() {
        var e = UpgradeEffects.of(BASE, List.of(card(UpgradeType.EFFICIENCY, UpgradeTier.UNOBTAINIUM)), true, false);
        assertEquals(UpgradeEffects.UNLIMITED, e.devices());
        assertEquals(UpgradeEffects.UNLIMITED, e.browsers());
        assertEquals(0.25, e.feFactor());
        assertTrue(e.enderNode());
    }

    @Test
    void basesNeverShrink() {
        var big = new UpgradeEffects.Base(1000, 100, 100, 48);
        var e = UpgradeEffects.of(big, List.of(card(UpgradeType.CAPACITY, UpgradeTier.ALLTHEMODIUM), card(UpgradeType.MEMORY, UpgradeTier.ALLTHEMODIUM)),
            false, true);
        assertEquals(1000, e.devices(), "a card never lowers a configured base");
        assertEquals(100, e.rules());
        assertEquals(48, e.historyHours());
        assertTrue(e.chunkAnchor());
    }
}
