package ai.ezfox.cccloud.item;

import ai.ezfox.cccloud.CloudConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Cloud Probe item: the tooltip says what it does and how to point it at a machine. */
public class CloudProbeItem extends BlockItem {
    public CloudProbeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        int range = CloudConfig.SPEC.isLoaded() ? CloudConfig.get(CloudConfig.PROBE_RANGE) : CloudConfig.PROBE_RANGE.getDefault();
        out.accept(Component.translatable("cccloud.probe.tooltip", range).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("cccloud.probe.tooltip.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
