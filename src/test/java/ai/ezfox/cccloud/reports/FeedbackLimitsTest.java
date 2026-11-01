package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeedbackLimitsTest {
    @Test
    void threePerSenderInTenMinutes() {
        var limits = new FeedbackLimits();
        for (int i = 0; i < FeedbackLimits.PER_SENDER; i++) assertTrue(limits.allow("a", 1000 + i));
        assertFalse(limits.allow("a", 2000));
        assertTrue(limits.allow("b", 2000));
        assertTrue(limits.allow("a", 1000 + FeedbackLimits.SENDER_WINDOW_MS));
    }

    @Test
    void tenPerServerInAnHour() {
        var limits = new FeedbackLimits();
        long now = 0;
        for (int i = 0; i < FeedbackLimits.PER_SERVER; i++) assertTrue(limits.allow("s" + i, now += 1000));
        assertFalse(limits.allow("late", now));
        assertFalse(limits.allow("late", now + FeedbackLimits.SENDER_WINDOW_MS));
        assertTrue(limits.allow("late", 1000 + FeedbackLimits.SERVER_WINDOW_MS));
    }

    @Test
    void aRefusedMessageDoesNotCount() {
        var limits = new FeedbackLimits();
        for (int i = 0; i < FeedbackLimits.PER_SENDER; i++) limits.allow("a", 0);
        for (int i = 0; i < 100; i++) assertFalse(limits.allow("a", 1));
        for (int i = 0; i < FeedbackLimits.PER_SERVER - FeedbackLimits.PER_SENDER; i++) assertTrue(limits.allow("o" + i, 1));
    }
}
