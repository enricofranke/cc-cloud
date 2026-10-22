package ai.ezfox.cccloud.bridge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Implemented by the ME/RS bridge block entities (compat packages). The core only talks to this interface. */
public interface BridgeHost {
    BridgeCore bridgeCore();

    /** The bridge's own dashboard section (when linked with the Cloud Link Card). */
    BridgeSection bridgeSection();

    /** Called every server tick by {@link CloudBridgeBlock}'s ticker. */
    void bridgeTick();

    /** Called when a player places the block (e.g. for AE2 node ownership). */
    default void onPlacedBy(Player player) {
    }

    default BlockEntity asBlockEntity() {
        return (BlockEntity) this;
    }
}
