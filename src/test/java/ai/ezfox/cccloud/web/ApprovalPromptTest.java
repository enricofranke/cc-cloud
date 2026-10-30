package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A pairing request reaches each player's chat once (closing the mainframe screen does not repeat it). */
class ApprovalPromptTest {
    @Test
    void oncePerPlayer() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        String id = "pending-" + UUID.randomUUID();
        assertFalse(ApprovalPrompt.alreadySent(id, alice));
        ApprovalPrompt.markSent(id, 10_000, alice, 1_000);
        assertTrue(ApprovalPrompt.alreadySent(id, alice), "Alice has it in chat");
        assertFalse(ApprovalPrompt.alreadySent(id, bob), "Bob saw it only on the screen: he still gets it");
        assertFalse(ApprovalPrompt.alreadySent("pending-other", alice), "another request is new");
    }

    @Test
    void expiredRequestsAreForgotten() {
        UUID alice = UUID.randomUUID();
        String old = "pending-" + UUID.randomUUID(), fresh = "pending-" + UUID.randomUUID();
        ApprovalPrompt.markSent(old, 5_000, alice, 1_000);
        ApprovalPrompt.markSent(fresh, 60_000, alice, 6_000);
        assertFalse(ApprovalPrompt.alreadySent(old, alice), "expired entries are dropped");
        assertTrue(ApprovalPrompt.alreadySent(fresh, alice));
    }
}
