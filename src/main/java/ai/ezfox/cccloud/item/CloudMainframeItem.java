package ai.ezfox.cccloud.item;

import ai.ezfox.cccloud.ModRegistry;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The Cloud Mainframe item; one dropped by a broken mainframe names that mainframe (see {@link MainframeIdentity}). */
public class CloudMainframeItem extends DoubleHighBlockItem {
    public CloudMainframeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistry.MAINFRAME_IDENTITY.get()) || super.isFoil(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var identity = stack.get(ModRegistry.MAINFRAME_IDENTITY.get());
        if (identity == null) return;
        out.accept(Component.translatable("cccloud.mainframe.item.identity", identity.shortId()).withStyle(ChatFormatting.AQUA));
        if (!identity.ownerName().isEmpty()) out.accept(Component.translatable("cccloud.mainframe.item.owner", identity.ownerName()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("cccloud.mainframe.item.restore_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
