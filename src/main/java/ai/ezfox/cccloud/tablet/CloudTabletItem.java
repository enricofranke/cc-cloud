package ai.ezfox.cccloud.tablet;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.item.MainframeLink;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Cloud Tablet / Advanced Cloud Tablet (v0.7): the dashboard in-game. Right-click a mainframe to link the tablet
 * (owner, trusted or operator), right-click in the air (or the "open tablet" key) to open it. The normal tablet works
 * inside the network's coverage (mainframe or relay device range, same dimension); the advanced one anywhere.
 */
public class CloudTabletItem extends Item {
    private final boolean advanced;

    public CloudTabletItem(boolean advanced, Properties properties) {
        super(properties);
        this.advanced = advanced;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CloudMainframeBlock)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
        if (mainframe == null || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        if (!InGameAccess.canView(player, mainframe.getMainframeId())) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        var link = new MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        stack.set(ModRegistry.LINKED_MAINFRAME.get(), link);
        player.sendSystemMessage(Component.translatable("cccloud.tablet.linked", link.describe()).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) TabletService.open(serverPlayer, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
        tooltip.accept(link == null ? Component.translatable("cccloud.tablet.unlinked").withStyle(ChatFormatting.GRAY)
            : Component.translatable("cccloud.tablet.linked_to", link.describe()).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable(advanced ? "cccloud.tablet.range_advanced" : "cccloud.tablet.range").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
