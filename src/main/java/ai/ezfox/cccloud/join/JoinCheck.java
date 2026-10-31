package ai.ezfox.cccloud.join;

import ai.ezfox.cccloud.BuildInfo;
import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.Edition;
import ai.ezfox.cccloud.connect.ConnectService;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.IpUtil;
import ai.ezfox.cccloud.web.PairingText;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Tells a player when their CC: Cloud jar is not the server's. Every test build has the same version number and nothing
 * else notices: Minecraft never downloads mods, and NeoForge only compares channels. The client names its build right
 * after joining ({@link Hello}, sent by {@code client/ClientHello}); the server compares it with its own
 * ({@link BuildCheck}). On a difference, or when no hello arrives within 10 seconds (a client from before this check), the
 * player gets a chat message with both versions and a link to the server's jar, and the server logs it. It never keeps
 * anyone out.
 */
public final class JoinCheck {
    /** Client → server: which build this client is. */
    public record Hello(String build) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "build_hello"));
        public static final StreamCodec<ByteBuf, Hello> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(BuildCheck.MAX_ID), Hello::build, Hello::new);

        @Override
        public Type<Hello> type() {
            return TYPE;
        }
    }

    private static final BuildCheck CHECK = new BuildCheck();
    private static long ticks;

    private JoinCheck() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(JoinCheck::register);
        NeoForge.EVENT_BUS.addListener(JoinCheck::onLogin);
        NeoForge.EVENT_BUS.addListener(JoinCheck::onLogout);
        NeoForge.EVENT_BUS.addListener(JoinCheck::onTick);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e ->
            CloudLog.LOGGER.info("[cccloud] CC: Cloud build {} ({})", BuildInfo.id(), Edition.current().label()));
    }

    /**
     * Optional, so that it can never refuse a connection: NeoForge drops an optional channel the other side lacks when it
     * negotiates (NetworkComponentNegotiator), while a required one disconnects that client. Old clients keep joining.
     */
    private static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(Hello.TYPE, Hello.CODEC, JoinCheck::onHello);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CHECK.joined(player.getUUID(), ticks);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHECK.left(event.getEntity().getUUID());
    }

    private static void onHello(Hello hello, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !CHECK.hello(player.getUUID())) return;
        String client = BuildCheck.clean(hello.build());
        if (BuildCheck.same(BuildInfo.id(), client)) {
            CloudLog.LOGGER.debug("[cccloud] {} has the server's CC: Cloud build {}", player.getGameProfile().name(), client);
            return;
        }
        CloudLog.LOGGER.info("[cccloud] {} joined with CC: Cloud build {}, this server runs {}", player.getGameProfile().name(), client, BuildInfo.id());
        notify(player, client);
    }

    private static void onTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % 20 != 0) return;
        for (var id : CHECK.overdue(ticks)) {
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) continue;
            CloudLog.LOGGER.info("[cccloud] {} named no CC: Cloud build within 10 seconds (a client from before the join check), this server runs {}",
                player.getGameProfile().name(), BuildInfo.id());
            notify(player, null);
        }
    }

    /** The chat message: both versions and what to do. {@code client} null: an older client or an unreadable build id. */
    private static void notify(ServerPlayer player, @Nullable String client) {
        Component theirs = client != null ? Component.literal(client) : JoinText.text(player, "cccloud.join.older");
        player.sendSystemMessage(JoinText.text(player, "cccloud.join.mismatch", theirs, BuildInfo.id()).withStyle(ChatFormatting.GOLD));
        String url = jarUrl(player);
        player.sendSystemMessage(url != null
            ? JoinText.text(player, "cccloud.join.update").withStyle(ChatFormatting.GRAY).append(downloadLink(player, url))
            : JoinText.text(player, "cccloud.join.no_link").withStyle(ChatFormatting.GRAY));
    }

    private static Component downloadLink(ServerPlayer player, String url) {
        return PairingText.openLink(url, JoinText.text(player, "cccloud.join.download"));
    }

    /** The link to the server's jar this player can open, or null (no dashboard they reach, or the download is off). */
    private static @Nullable String jarUrl(ServerPlayer player) {
        if (!CloudWebServer.jarAvailable()) return null;
        return PairingText.jarUrl(PairingText.reachableUrl(ConnectService.preferredUrl(null), CloudWebServer.publicUrl(), CloudWebServer.dashboardUrl(),
            IpUtil.isLan(player.getIpAddress())));
    }

    /** {@code /cccloud version}: the server's build (a click copies it) and, for a player, the link to its jar. */
    public static void showVersion(CommandSourceStack source) {
        var player = source.getPlayer();
        String build = BuildInfo.id();
        var line = JoinText.text(player, "cccloud.join.version", Component.literal(build).withStyle(style -> style.withColor(ChatFormatting.GOLD)
            .withClickEvent(new ClickEvent.CopyToClipboard(build))
            .withHoverEvent(new HoverEvent.ShowText(JoinText.text(player, "cccloud.join.copy")))));
        String url = player == null ? null : jarUrl(player);
        source.sendSystemMessage(url == null ? line : line.append(downloadLink(player, url)));
    }
}
