package ai.ezfox.cccloud.compat.jade;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.analyzer.DeviceInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade lines for uplinks, relays and bridges (Cloud Analyzer spec): signal, hops, route to the mainframe and why it is
 * not online. Only sent for networks where the looking player has a role (same check as the analyzer, no positions).
 * Server data and tooltip are separate providers with the same id: since Minecraft 1.21.6 Jade refuses a data provider
 * that is also a component provider (and then loads none of the plugin).
 */
enum NetworkJadeProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = Identifier.fromNamespaceAndPath(CCCloud.MODID, "network");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        // Registered for every block entity (the bridge classes only exist with AE2/RS): leave other mods' blocks at once.
        if (!CCCloud.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(accessor.getBlock()).getNamespace())) return;
        if (!(accessor.getPlayer() instanceof ServerPlayer player)) return;
        var info = DeviceInfo.of(player, accessor.getLevel(), accessor.getPosition());
        if (info == null) return;
        var net = new CompoundTag();
        net.putString("status", info.status());
        net.putDouble("signal", info.signal());
        net.putInt("hops", info.hops());
        var route = new ListTag();
        for (var hop : info.route()) route.add(StringTag.valueOf(hop));
        net.put("route", route);
        data.put("cccloud_net", net);
        // Advanced Cloud Sensor: armed, how many are inside (never who), the zone
        if (accessor.getBlockEntity() instanceof ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity device && device.sensor() != null) {
            var sensor = device.sensor();
            var tag = new CompoundTag();
            tag.putBoolean("armed", sensor.settings().armed());
            tag.putInt("inside", sensor.inside().size());
            tag.putString("zone", sensor.settings().clamped(ai.ezfox.cccloud.sensor.AdvancedSensor.maxX(), ai.ezfox.cccloud.sensor.AdvancedSensor.maxY(),
                ai.ezfox.cccloud.sensor.AdvancedSensor.maxZ()).zoneText());
            data.put("cccloud_sensor", tag);
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: the lines from the server data above. */
    enum Tooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var net = accessor.getServerData().getCompoundOrEmpty("cccloud_net");
            if (net.isEmpty()) return;
            String status = net.getStringOr("status", "offline");
            int hops = net.getIntOr("hops", -1);
            if (hops >= 0) {
                tooltip.add(Component.translatable("cccloud.analyzer.signal", Math.round(net.getDoubleOr("signal", 0) * 100),
                    ai.ezfox.cccloud.analyzer.CloudAnalyzerItem.via(hops)).withStyle(ChatFormatting.GRAY));
                MutableComponent route = Component.empty();
                var list = net.getListOrEmpty("route");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) route.append(" → ");
                    route.append(Component.translatable("cccloud.analyzer.kind." + list.getStringOr(i, "")));
                }
                tooltip.add(Component.translatable("cccloud.analyzer.route", route).withStyle(ChatFormatting.GRAY));
            }
            if (!"online".equals(status)) tooltip.add(Component.translatable("cccloud.analyzer.status." + status).withStyle(ChatFormatting.RED));
            var sensor = accessor.getServerData().getCompoundOrEmpty("cccloud_sensor");
            if (!sensor.isEmpty()) {
                tooltip.add(Component.translatable(sensor.getBooleanOr("armed", false) ? "cccloud.jade.sensor.armed" : "cccloud.jade.sensor.disarmed",
                    sensor.getIntOr("inside", 0), sensor.getStringOr("zone", "")).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
