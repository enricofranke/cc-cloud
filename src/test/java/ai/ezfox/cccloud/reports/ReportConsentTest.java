package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.Decision;
import ai.ezfox.cccloud.reports.ReportConsent.Setting;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import org.junit.jupiter.api.Test;

class ReportConsentTest {
    private static final Decision YES = new Decision(true, 1);
    private static final Decision NO = new Decision(false, 1);

    private static State state(Setting setting, int configVersion, Decision decision, int current) {
        return ReportConsent.state(setting, configVersion, decision, current);
    }

    @Test
    void nothingDecidedAsks() {
        assertEquals(State.ASK, state(Setting.ASK, 0, null, 1));
    }

    @Test
    void anUnreadableSettingAsks() {
        assertEquals(State.ASK, state(null, 1, YES, 1));
    }

    @Test
    void onlyBothHalvesTogetherAreAYes() {
        assertEquals(State.ACTIVE, state(Setting.ON, 1, YES, 1));
    }

    @Test
    void aFileAloneIsNoYes() {
        // a modpack's defaultconfigs or a copied world cannot agree for the owner
        assertEquals(State.ASK, state(Setting.ON, 1, null, 1));
    }

    @Test
    void aDecisionAloneIsNoYes() {
        assertEquals(State.ASK, state(Setting.ASK, 1, YES, 1));
    }

    @Test
    void offInTheFileStopsAtOnce() {
        assertEquals(State.DECLINED, state(Setting.OFF, 1, YES, 1));
    }

    @Test
    void aStoredNoHoldsAgainstTheFile() {
        assertEquals(State.DECLINED, state(Setting.ON, 1, NO, 1));
        assertEquals(State.DECLINED, state(Setting.ASK, 0, NO, 1));
    }

    @Test
    void aNewVersionAsksAgainAfterAYes() {
        assertEquals(State.ASK, state(Setting.ON, 1, YES, 2));
        assertEquals(State.ASK, state(Setting.ON, 2, YES, 2));
        assertEquals(State.ASK, state(Setting.ON, 1, new Decision(true, 2), 2));
        assertEquals(State.ACTIVE, state(Setting.ON, 2, new Decision(true, 2), 2));
    }

    @Test
    void aNewVersionDoesNotReopenANo() {
        assertEquals(State.DECLINED, state(Setting.OFF, 1, NO, 2));
        assertEquals(State.DECLINED, state(Setting.ASK, 1, NO, 2));
    }

    @Test
    void thePromptComesOnce() {
        assertTrue(ReportConsent.prompt(State.ASK, State.ASK, 0, 1));
        assertTrue(ReportConsent.prompt(State.ACTIVE, State.ASK, 0, 1));
        assertTrue(ReportConsent.prompt(State.ASK, State.DECLINED, 0, 1));
        assertFalse(ReportConsent.prompt(State.ASK, State.ASK, 1, 1));
    }

    @Test
    void afterAnAnswerThePromptStaysAway() {
        assertFalse(ReportConsent.prompt(State.ACTIVE, State.DECLINED, 0, 1));
        assertFalse(ReportConsent.prompt(State.DECLINED, State.DECLINED, 0, 1));
    }

    @Test
    void aNewVersionBringsThePromptBack() {
        assertTrue(ReportConsent.prompt(State.ASK, State.DECLINED, 1, 2));
    }

    @Test
    void settingsParseStrictly() {
        assertEquals(Setting.ASK, Setting.parse("ask"));
        assertEquals(Setting.ON, Setting.parse("on"));
        assertEquals(Setting.OFF, Setting.parse("off"));
        assertNull(Setting.parse("ON"));
        assertNull(Setting.parse("yes"));
        assertNull(Setting.parse(1));
        assertNull(Setting.parse(null));
    }

    @Test
    void channelsById() {
        assertEquals(Channel.CRASH, Channel.byId("crash"));
        assertEquals(Channel.STATS, Channel.byId("stats"));
        assertNull(Channel.byId("feedback"));
        assertNull(Channel.byId(null));
    }

    @Test
    void privacyLinkFollowsTheLanguage() {
        assertEquals("https://cccloud.dev/de/legal/privacy/", ReportConsent.privacyUrl("de_de"));
        assertEquals("https://cccloud.dev/en/legal/privacy/", ReportConsent.privacyUrl("en_us"));
        assertEquals("https://cccloud.dev/en/legal/privacy/", ReportConsent.privacyUrl("fr_fr"));
    }
}
