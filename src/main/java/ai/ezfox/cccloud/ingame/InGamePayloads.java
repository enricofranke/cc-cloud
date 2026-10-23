package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.web.ClientMessages;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Custom payloads of the in-game clients (v0.7). The tablet speaks the browser protocol inside them: the server sends
 * hub messages (snapshot/deltas) and action/query results as JSON exactly as a WebSocket would, and the client sends
 * browser-format {@code action}/{@code query} messages, parsed with {@link ClientMessages} on the server.
 */
public final class InGamePayloads {
    /** Max JSON per server → client packet (one snapshot of a big dashboard fits easily). */
    public static final int MAX_DATA = 1024 * 1024;

    /** Server → client: a JSON array of hub messages for the open tablet. */
    public record TabletData(int containerId, String messages) implements CustomPacketPayload {
        public static final Type<TabletData> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "tablet_data"));
        public static final StreamCodec<ByteBuf, TabletData> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletData::containerId, ByteBufCodecs.stringUtf8(MAX_DATA), TabletData::messages, TabletData::new);

        @Override
        public Type<TabletData> type() {
            return TYPE;
        }
    }

    /** Client → server: one browser-format message ({@code action} or {@code query}). */
    public record TabletRequest(int containerId, String message) implements CustomPacketPayload {
        public static final Type<TabletRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "tablet_request"));
        public static final StreamCodec<ByteBuf, TabletRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletRequest::containerId, ByteBufCodecs.stringUtf8(ClientMessages.MAX_FRAME), TabletRequest::message, TabletRequest::new);

        @Override
        public Type<TabletRequest> type() {
            return TYPE;
        }
    }

    /** Client → server: the "open tablet" key was pressed. */
    public record OpenTablet() implements CustomPacketPayload {
        public static final Type<OpenTablet> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "open_tablet"));
        public static final StreamCodec<ByteBuf, OpenTablet> CODEC = StreamCodec.unit(new OpenTablet());

        @Override
        public Type<OpenTablet> type() {
            return TYPE;
        }
    }

    /** Client → server: a Cloud Display screen action ({@code select <sectionId>}, {@code interactive true|false}, {@code unlink}). */
    public record DisplayAction(int containerId, String action, String arg) implements CustomPacketPayload {
        public static final Type<DisplayAction> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "display_action"));
        public static final StreamCodec<ByteBuf, DisplayAction> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DisplayAction::containerId, ByteBufCodecs.stringUtf8(32), DisplayAction::action,
            ByteBufCodecs.stringUtf8(64), DisplayAction::arg, DisplayAction::new);

        @Override
        public Type<DisplayAction> type() {
            return TYPE;
        }
    }

    /** Server → client: Cloud Analyzer overlay data ({@code {}} = nothing to show) or a route highlight. */
    public record AnalyzerData(String json) implements CustomPacketPayload {
        public static final Type<AnalyzerData> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "analyzer_data"));
        public static final StreamCodec<ByteBuf, AnalyzerData> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256 * 1024), AnalyzerData::json, AnalyzerData::new);

        @Override
        public Type<AnalyzerData> type() {
            return TYPE;
        }
    }

    /** Set by the client entry point. */
    public static volatile @Nullable Consumer<TabletData> clientTabletHandler;
    public static volatile @Nullable Consumer<AnalyzerData> clientAnalyzerHandler;

    private InGamePayloads() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(InGamePayloads::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(TabletData.TYPE, TabletData.CODEC, (payload, context) -> {
            var handler = clientTabletHandler;
            if (handler != null) handler.accept(payload);
        });
        registrar.playToClient(AnalyzerData.TYPE, AnalyzerData.CODEC, (payload, context) -> {
            var handler = clientAnalyzerHandler;
            if (handler != null) handler.accept(payload);
        });
        registrar.playToServer(TabletRequest.TYPE, TabletRequest.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ai.ezfox.cccloud.tablet.TabletService.request(player, payload);
        });
        registrar.playToServer(OpenTablet.TYPE, OpenTablet.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ai.ezfox.cccloud.tablet.TabletService.openFromKey(player);
        });
        registrar.playToServer(DisplayAction.TYPE, DisplayAction.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ai.ezfox.cccloud.display.DisplayService.onAction(player, payload);
        });
    }
}
