package ai.ezfox.cccloud.computer.block;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The Cloud Computer item; one dropped by a broken computer names that computer (see {@link ComputerItemData}). */
public class CloudComputerItem extends BlockItem {
    public CloudComputerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var data = stack.get(ComputerBlocks.COMPUTER_DATA.get());
        if (data == null) return;
        out.accept(Component.translatable("cccloud.computer.item.id", data.id()).withStyle(ChatFormatting.AQUA));
        if (!data.label().isEmpty()) out.accept(Component.translatable("cccloud.computer.item.label", data.label()).withStyle(ChatFormatting.GRAY));
    }
}
