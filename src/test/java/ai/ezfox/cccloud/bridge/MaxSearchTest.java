package ai.ezfox.cccloud.bridge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The amount the current items allow, found by bisecting simulated plans. */
class MaxSearchTest {
    private static MaxSearch run(long asked, long realMax) {
        var search = new MaxSearch(asked);
        for (long amount = search.next(); amount != -1; amount = search.next()) search.result(amount, amount <= realMax);
        return search;
    }

    @Test
    void findsTheExactMaximum() {
        var search = run(100, 37);
        assertEquals(37, search.best());
        assertTrue(search.exact());
    }

    @Test
    void nothingPossibleIsZero() {
        var search = run(64, 0);
        assertEquals(0, search.best());
        assertTrue(search.exact());
    }

    @Test
    void oneLessThanAskedIsFound() {
        assertEquals(63, run(64, 63).best());
    }

    @Test
    void anAmountOfOneNeedsNoPlan() {
        assertEquals(-1, new MaxSearch(1).next());
    }

    @Test
    void hugeAmountsStopAtTheStepLimitWithATestedLowerBound() {
        var search = run(1_000_000, 123_456);
        assertFalse(search.exact(), "8 steps do not settle a million");
        assertTrue(search.best() > 0 && search.best() <= 123_456, "only an amount that worked: " + search.best());
    }

    @Test
    void theWorkIsBounded() {
        var search = new MaxSearch(1_000_000);
        int plans = 0;
        for (long amount = search.next(); amount != -1; amount = search.next()) {
            plans++;
            search.result(amount, false);
        }
        assertEquals(MaxSearch.MAX_STEPS, plans);
    }
}
