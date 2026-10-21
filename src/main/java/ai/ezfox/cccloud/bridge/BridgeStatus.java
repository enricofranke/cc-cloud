package ai.ezfox.cccloud.bridge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Right-click info of a bridge block. */
final class BridgeStatus {
    private BridgeStatus() {
    }

    static void show(Player player, BridgeCore core, Component name) {
        player.sendSystemMessage(name.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("cccloud.bridge.status", core.status().displayName()));
        if (core.isConnected()) {
            var t = core.adapter().totals();
            player.sendSystemMessage(Component.translatable("cccloud.bridge.items", String.format(java.util.Locale.ROOT, "%,d", t.itemsUsed()),
                String.format(java.util.Locale.ROOT, "%,d", t.typesUsed())).withStyle(ChatFormatting.GRAY));
        }
    }
}
