package ai.ezfox.cccloud.computer.block;

import java.util.UUID;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Who may use a Cloud Computer in game (open its screen, type, turn it on or off, set its label), like the other
 * devices: its owner (who placed it), the members of the owner's team (FTB Teams or another team mod), and server
 * admins with {@code cccloud.admin.operate}. A computer without an owner (placed by a machine or a command) is open to
 * everyone. The claim guard comes on top (a blocked computer refuses everyone). Pure Java, unit tested.
 */
public final class ComputerAccess {
    private ComputerAccess() {
    }

    /**
     * @param sameTeam whether two players are in the same team (false without a team mod)
     */
    public static boolean mayUse(@Nullable UUID owner, UUID player, boolean admin, BiPredicate<UUID, UUID> sameTeam) {
        if (owner == null || owner.equals(player) || admin) return true;
        try {
            return sameTeam.test(owner, player);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Whether a computer item that names {@code id} may restore that computer: the id must exist, no loaded block may
     * use it already, and the player must be its owner (or it has none). Otherwise the placed block gets a new id, so an
     * edited item can never open another player's computer.
     */
    public static boolean mayRestore(boolean known, boolean loadedElsewhere, @Nullable UUID owner, UUID player) {
        return known && !loadedElsewhere && (owner == null || owner.equals(player));
    }
}
