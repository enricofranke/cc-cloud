package ai.ezfox.cccloud.power;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PowerStateTest {
    private long stored;

    private boolean tick(PowerState state, long usage, boolean require) {
        return state.tick(stored, usage, require, amount -> stored -= amount);
    }

    @Test
    void drainsAndRunsWhilePowered() {
        var state = new PowerState();
        stored = 100;
        assertTrue(tick(state, 40, true));
        assertEquals(60, stored);
        assertTrue(tick(state, 40, true));
        assertEquals(20, stored);
    }

    @Test
    void graceThenNoPowerThenRecovery() {
        var state = new PowerState();
        stored = 10;
        for (int i = 1; i <= PowerState.GRACE_TICKS; i++) assertTrue(tick(state, 40, true), "grace tick " + i);
        assertEquals(0, stored, "what was left is used up");
        assertFalse(tick(state, 40, true), "cut after 5 s");
        assertFalse(tick(state, 40, true));
        stored = 39;
        assertFalse(tick(state, 40, true), "not enough for one tick");
        stored = 1000;
        assertTrue(tick(state, 40, true), "back as soon as the buffer covers the usage");
        assertEquals(0, state.outageTicks());
    }

    @Test
    void shortBrownoutsDoNotFlicker() {
        var state = new PowerState();
        stored = 0;
        for (int i = 0; i < 50; i++) assertTrue(tick(state, 40, true));
        stored = 40;
        assertTrue(tick(state, 40, true));
        assertEquals(0, state.outageTicks(), "outage counter resets once covered");
    }

    @Test
    void freeWhenNotRequiredOrZeroUsage() {
        var state = new PowerState();
        stored = 0;
        for (int i = 0; i < 200; i++) assertTrue(tick(state, 40, false));
        for (int i = 0; i < 200; i++) assertTrue(tick(state, 0, true));
    }

    @Test
    void usageFormula() {
        assertEquals(40 + 5 * 3 + 2 * 4, PowerState.usage(40, 5, 2, 3, 4));
        assertEquals(0, PowerState.usage(0, 0, 0, 10, 10));
        assertEquals(40, PowerState.usage(40, -5, -2, 3, 4), "negative config values count as 0");
    }
}
