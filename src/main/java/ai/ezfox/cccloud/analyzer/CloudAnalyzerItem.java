package ai.ezfox.cccloud.analyzer;

import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Cloud Analyzer (handheld): while held, the client shows range bubbles, links and the placement preview (data from
 * {@link AnalyzerService}). Right-click a cloud block: a summary in chat (status, signal, hops, route, power);
 * sneak + right-click: its route to the mainframe is highlighted for 10 s. Only for networks where the player has a role.
 */
public class CloudAnalyzerItem extends Item {
    public static final int HIGHLIGHT_MS = 10_000;

    public CloudAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return context.getLevel().isClientSide() && isCloudBlock(context) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!isCloudBlock(context)) return InteractionResult.PASS;
        var info = DeviceInfo.of(player, context.getLevel(), context.getClickedPos());
        if (info == null) {
            player.sendSystemMessage(Component.translatable("cccloud.analyzer.no_info").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            // The route highlight shows positions: view.positions.
            if (!InGameAccess.has(player, info.mainframeId(), ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS)) {
                player.sendSystemMessage(Component.translatable("cccloud.analyzer.no_positions").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            var json = new JsonObject();
            var route = new JsonArray();
            for (var p : info.routePositions()) {
                var point = new JsonArray();
                point.add(p.x);
                point.add(p.y);
                point.add(p.z);
                route.add(point);
            }
            json.add("route", route);
            json.addProperty("ms", HIGHLIGHT_MS);
            AnalyzerService.sender.accept(player, new InGamePayloads.AnalyzerData(Json.GSON.toJson(json)));
            player.sendOverlayMessage(Component.translatable("cccloud.analyzer.route_shown"));
            return InteractionResult.SUCCESS;
        }
        for (var line : summary(info)) player.sendSystemMessage(line);
        return InteractionResult.SUCCESS;
    }

    private static boolean isCloudBlock(UseOnContext context) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(context.getLevel().getBlockState(context.getClickedPos()).getBlock());
        return ai.ezfox.cccloud.CCCloud.MODID.equals(id.getNamespace());
    }

    /** Chat lines for a device (also the hover text basis). */
    public static java.util.List<MutableComponent> summary(DeviceInfo info) {
        var lines = new java.util.ArrayList<MutableComponent>();
        var status = Component.translatable("cccloud.analyzer.status." + info.status())
            .withStyle(info.status().equals("online") ? ChatFormatting.GREEN : ChatFormatting.RED);
        lines.add(Component.translatable("cccloud.analyzer.header", Component.translatable("cccloud.analyzer.kind." + info.kind()), status)
            .withStyle(ChatFormatting.AQUA));
        if (info.hops() >= 0) {
            lines.add(Component.translatable("cccloud.analyzer.signal", Math.round(info.signal() * 100), info.hops()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("cccloud.analyzer.route", route(info)).withStyle(ChatFormatting.GRAY));
        }
        if (info.capacity() > 0) {
            lines.add(Component.translatable("cccloud.analyzer.power", String.format("%,d", info.energy()), String.format("%,d", info.capacity()))
                .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** "Mainframe → Cloud Relay → Cloud Uplink" from the route's type ids. */
    public static MutableComponent route(DeviceInfo info) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < info.route().size(); i++) {
            if (i > 0) text.append(" → ");
            text.append(Component.translatable("cccloud.analyzer.kind." + info.route().get(i)));
        }
        return text;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("cccloud.analyzer.tooltip").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("cccloud.analyzer.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
