package ai.ezfox.cccloud.item;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click a mainframe to store it, right-click an uplink to bind the uplink to the stored mainframe.
 * Sneak + right-click a mainframe revokes all dashboard tokens of that mainframe; sneak + right-click in the air
 * clears the card.
 */
public class CloudLinkCardItem extends Item {
    public CloudLinkCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (state.getBlock() instanceof CloudMainframeBlock) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            if (mainframe == null || player == null) return InteractionResult.FAIL;
            if (player.isSecondaryUseActive()) {
                mainframe.revokeTokens(player);
                return InteractionResult.SUCCESS;
            }
            var link = new MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
            stack.set(ModRegistry.LINKED_MAINFRAME.get(), link);
            player.sendSystemMessage(Component.translatable("cccloud.link_card.stored", link.describe()).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity uplink) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player == null) return InteractionResult.FAIL;
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link == null) {
                player.sendSystemMessage(Component.translatable("cccloud.link_card.empty").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            uplink.bind(link, player);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof ai.ezfox.cccloud.bridge.BridgeHost bridge) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player == null) return InteractionResult.FAIL;
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link == null) {
                player.sendSystemMessage(Component.translatable("cccloud.link_card.empty").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            bridge.bridgeSection().bind(link, player);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !stack.has(ModRegistry.LINKED_MAINFRAME.get())) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            stack.remove(ModRegistry.LINKED_MAINFRAME.get());
            player.sendSystemMessage(Component.translatable("cccloud.link_card.cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistry.LINKED_MAINFRAME.get()) || super.isFoil(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
        if (link == null) {
            out.accept(Component.translatable("cccloud.link_card.tooltip.empty").withStyle(ChatFormatting.GRAY));
            out.accept(Component.translatable("cccloud.link_card.tooltip.hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            out.accept(Component.translatable("cccloud.link_card.tooltip.linked", link.describe()).withStyle(ChatFormatting.AQUA));
            out.accept(Component.literal(link.pos().dimension().identifier().toString()).withStyle(ChatFormatting.DARK_GRAY));
            out.accept(Component.translatable("cccloud.link_card.tooltip.bind_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
