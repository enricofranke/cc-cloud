package ai.ezfox.cccloud.join;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildCheckTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void equalIdsAreTheSameBuild() {
        assertTrue(BuildCheck.same("1.0.0-beta.2-0de7b2c3", "1.0.0-beta.2-0de7b2c3"));
        assertFalse(BuildCheck.same("1.0.0-beta.2-0de7b2c3", "1.0.0-beta.2-81ab0536"), "same version, other commit");
        assertFalse(BuildCheck.same("1.0.0-beta.2-0de7b2c3", "1.0.0-beta.2-0de7b2c3-dirty"), "the same commit with local changes");
        assertFalse(BuildCheck.same("1.0.0-beta.2+dev5-0de7b2c3", "1.0.0-beta.2+dev4-0de7b2c3"), "another test build");
        assertFalse(BuildCheck.same("1.0.0-beta.2-0de7b2c3", null), "an unreadable id");
    }

    @Test
    void aHelloEndsTheWait() {
        var check = new BuildCheck();
        check.joined(A, 100);
        assertTrue(check.hello(A));
        assertEquals(List.of(), check.overdue(100 + BuildCheck.HELLO_TICKS), "it said its build in time");
    }

    @Test
    void aPlayerWhoStaysSilentIsOverdueOnce() {
        var check = new BuildCheck();
        check.joined(A, 100);
        assertEquals(List.of(), check.overdue(100 + BuildCheck.HELLO_TICKS - 1), "still within the 10 seconds");
        assertEquals(List.of(A), check.overdue(100 + BuildCheck.HELLO_TICKS));
        assertEquals(List.of(), check.overdue(100 + 2 * BuildCheck.HELLO_TICKS), "told once");
    }

    @Test
    void eachPlayerHasTheirOwnTime() {
        var check = new BuildCheck();
        check.joined(A, 0);
        check.joined(B, 50);
        assertEquals(List.of(A), check.overdue(BuildCheck.HELLO_TICKS));
        assertEquals(List.of(B), check.overdue(50 + BuildCheck.HELLO_TICKS));
    }

    @Test
    void onlyTheFirstHelloCounts() {
        var check = new BuildCheck();
        check.joined(A, 0);
        assertTrue(check.hello(A));
        assertFalse(check.hello(A), "a client cannot repeat the message by repeating the hello");
        assertFalse(check.hello(B), "not waiting: joined never happened");
    }

    @Test
    void aLateHelloDoesNotCountAgain() {
        var check = new BuildCheck();
        check.joined(A, 0);
        assertEquals(List.of(A), check.overdue(BuildCheck.HELLO_TICKS));
        assertFalse(check.hello(A), "already told");
    }

    @Test
    void leavingEndsTheWaitAndRejoiningStartsANewOne() {
        var check = new BuildCheck();
        check.joined(A, 0);
        check.left(A);
        assertEquals(List.of(), check.overdue(1000));
        check.joined(A, 1000);
        assertEquals(List.of(), check.overdue(1000 + BuildCheck.HELLO_TICKS - 1));
        assertEquals(List.of(A), check.overdue(1000 + BuildCheck.HELLO_TICKS));
    }

    @Test
    void onlyPlainIdsReachChatAndTheLog() {
        assertEquals("1.0.0-beta.2+dev5-0de7b2c3-dirty", BuildCheck.clean("1.0.0-beta.2+dev5-0de7b2c3-dirty"));
        for (var bad : new String[]{null, "", "a b", "a\nfake log line", "§cred", "1.0\u0000", "x".repeat(BuildCheck.MAX_ID + 1)}) {
            assertNull(BuildCheck.clean(bad), String.valueOf(bad));
        }
        assertEquals("x".repeat(BuildCheck.MAX_ID), BuildCheck.clean("x".repeat(BuildCheck.MAX_ID)));
    }
}
