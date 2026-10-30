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
 * clears the card. Cloud Probe: sneak + right-click a machine stores it as a probe target (this click went through
 * NeoForge's interaction event like any right-click, so claim mods can refuse it), then right-click the probe.
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
            var target = stack.get(ModRegistry.PROBE_TARGET.get());
            if (uplink.isProbe() && target != null) {
                if (uplink.setRemoteTarget(target, player)) stack.remove(ModRegistry.PROBE_TARGET.get());
                return InteractionResult.SUCCESS;
            }
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link == null) {
                player.sendSystemMessage(Component.translatable("cccloud.link_card.empty").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            uplink.bind(link, player);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player == null) return InteractionResult.FAIL;
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link == null) {
                player.sendSystemMessage(Component.translatable("cccloud.link_card.empty").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            relay.bindEnder(link, player);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof ai.ezfox.cccloud.cable.EnderFiberLinkBlockEntity fiberLink) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player == null) return InteractionResult.FAIL;
            var link = stack.get(ModRegistry.LINKED_MAINFRAME.get());
            if (link == null) {
                player.sendSystemMessage(Component.translatable("cccloud.link_card.empty").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            fiberLink.bind(link, player);
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

        // Cloud Probe target: sneak + right-click a machine (a block entity of another mod or vanilla).
        if (player != null && player.isSecondaryUseActive() && level.getBlockEntity(pos) != null
            && !ai.ezfox.cccloud.CCCloud.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return pickProbeTarget(stack, level, pos, context.getClickedFace(), player);
        }

        return InteractionResult.PASS;
    }

    /**
     * Stores the machine for a Cloud Probe. The click itself was a real interaction (NeoForge's RightClickBlock event,
     * which claim mods such as FTB Chunks or Open Parties and Claims cancel in protected areas, plus the server's
     * spawn protection check); here the same checks a block interaction needs are repeated for direct calls.
     */
    public static InteractionResult pickProbeTarget(ItemStack stack, Level level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face, Player player) {
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, stack)) {
            player.sendSystemMessage(Component.translatable("cccloud.probe.no_access").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        var target = new ai.ezfox.cccloud.item.ProbeTarget(GlobalPos.of(level.dimension(), pos.immutable()), face, player.getUUID());
        stack.set(ModRegistry.PROBE_TARGET.get(), target);
        player.sendSystemMessage(Component.translatable("cccloud.probe.target_stored", level.getBlockState(pos).getBlock().getName(), target.describe())
            .withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !(stack.has(ModRegistry.LINKED_MAINFRAME.get()) || stack.has(ModRegistry.PROBE_TARGET.get()))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.remove(ModRegistry.LINKED_MAINFRAME.get());
            stack.remove(ModRegistry.PROBE_TARGET.get());
            player.sendSystemMessage(Component.translatable("cccloud.link_card.cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistry.LINKED_MAINFRAME.get()) || stack.has(ModRegistry.PROBE_TARGET.get()) || super.isFoil(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        var target = stack.get(ModRegistry.PROBE_TARGET.get());
        if (target != null) {
            out.accept(Component.translatable("cccloud.probe.tooltip.card", target.describe()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
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
