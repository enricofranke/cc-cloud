package ai.ezfox.cccloud.cable;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Remembers the mainframe whose ender fiber group this link joined (Link Card, owner/admin only). */
public class EnderFiberLinkBlockEntity extends BlockEntity {
    private static final Set<EnderFiberLinkBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private @Nullable UUID group;

    public EnderFiberLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENDER_FIBER_LINK_BE.get(), pos, state);
    }

    /** Loaded links (server thread). */
    public static Collection<EnderFiberLinkBlockEntity> loaded() {
        return LOADED;
    }

    public static void clearLoaded() {
        LOADED.clear();
    }

    public @Nullable UUID group() {
        return group;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        group = input.read("group", UUIDUtil.STRING_CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("group", UUIDUtil.STRING_CODEC, group);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        LOADED.add(this);
        NetworkService.markFiberDirty();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || level.isClientSide()) return;
        LOADED.remove(this);
        NetworkService.markFiberDirty();
    }

    /** Link Card: join the fiber group of the card's mainframe (owner/admin only). */
    public void bind(MainframeLink link, Player player) {
        var mainframe = CloudNetwork.mainframe(link.id());
        if (mainframe == null) {
            player.sendSystemMessage(Component.translatable("cccloud.uplink.mainframe_missing").withStyle(ChatFormatting.RED));
            return;
        }
        if (!mainframe.has(player, ai.ezfox.cccloud.access.Perm.NETWORK_LINK)) {
            player.sendSystemMessage(Component.translatable("cccloud.mainframe.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        if (!ai.ezfox.cccloud.access.Access.mayRelink(player, group, link.id())) {
            player.sendSystemMessage(Component.translatable("cccloud.link_card.linked_elsewhere").withStyle(ChatFormatting.RED));
            return;
        }
        group = link.id();
        setChanged();
        NetworkService.markFiberDirty();
        CCCloud.LOGGER.info("[cccloud] {} linked the Ender Fiber Link at {} to {}", player.getGameProfile().name(), getBlockPos(), mainframe.getDisplayName());
        player.sendSystemMessage(Component.translatable("cccloud.fiber.ender_linked", link.describe()).withStyle(ChatFormatting.GREEN));
    }

    public void showStatus(Player player) {
        player.sendSystemMessage(getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable(group == null ? "cccloud.fiber.ender_unlinked" : "cccloud.fiber.ender_group")
            .withStyle(ChatFormatting.GRAY));
    }
}
