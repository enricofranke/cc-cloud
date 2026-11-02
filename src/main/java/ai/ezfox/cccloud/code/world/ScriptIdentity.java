package ai.ezfox.cccloud.code.world;

import java.util.UUID;

/**
 * Who a running script is and whose rights it uses: the author (the player who saved the running version) and the
 * starter (who pressed Run, or who switched autostart on). It acts with the author's live rights, capped by the
 * starter's, and never with more.
 */
public record ScriptIdentity(UUID mainframeId, String scriptId, String scriptName, UUID author, String authorName, UUID starter, String starterName) {
    /** {@code script Night mode (by Steve)}: how the audit log and the dashboard name what a script did. */
    public String label() {
        return "script " + scriptName + " (by " + authorName + ")";
    }

    /** The user string of dashboard actions ({@code script:Night mode}). */
    public String user() {
        return "script:" + scriptName;
    }
}
