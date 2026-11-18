package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.computer.CloudComputer;
import ai.ezfox.cccloud.computer.InputEvent;
import ai.ezfox.cccloud.terminal.TerminalFrame;
import ai.ezfox.cccloud.terminal.TerminalInput;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * The in-game terminal screen of a Cloud Computer: opening it, its payloads and the live picture.
 * <p>
 * Server → client: {@link ViewPayload} with a {@code term} message ({@link TerminalFrame#diff}, the browser terminal's
 * format: only changed rows) or a {@code state} message (on/off, title, last error). Only players with the screen open
 * get them, and only when something changed (checked every tick).
 * <p>
 * Client → server: {@link InputPayload} with a JSON array of input events in the browser's format, parsed strictly by
 * {@link TerminalInput#parse} (at most {@value TerminalInput#MAX_EVENTS} events). The handler checks that this screen
 * is open (container id), the player is in range, the block is still there and the player may use it; the limits on
 * what a computer takes (paste length, rate) are in {@link CloudComputer#input}.
 */
public final class ComputerScreens {
    /** Max size of a view message (a full 164x81 frame with JSON escaping fits). */
    public static final int MAX_VIEW = 256 * 1024;
    /** Max size of an input message (32 events, a 512-character paste with escaping). */
    public static final int MAX_INPUT = 16 * 1024;

    public record ViewPayload(int containerId, String json) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "computer_view"));
        public static final StreamCodec<ByteBuf, ViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ViewPayload::containerId, ByteBufCodecs.stringUtf8(MAX_VIEW), ViewPayload::json, ViewPayload::new);

        @Override
        public Type<ViewPayload> type() {
            return TYPE;
        }
    }

    public record InputPayload(int containerId, String json) implements CustomPacketPayload {
        public static final Type<InputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "computer_input"));
        public static final StreamCodec<ByteBuf, InputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InputPayload::containerId, ByteBufCodecs.stringUtf8(MAX_INPUT), InputPayload::json, InputPayload::new);

        @Override
        public Type<InputPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client entry point; handles {@link ViewPayload} on the client. */
    public static volatile @Nullable Consumer<ViewPayload> clientViewHandler;

    private ComputerScreens() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ComputerScreens::register);
        NeoForge.EVENT_BUS.addListener(ComputerScreens::onServerTick);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, (payload, context) -> {
            var handler = clientViewHandler;
            if (handler != null) handler.accept(payload);
        });
        registrar.playToServer(InputPayload.TYPE, InputPayload.CODEC, ComputerScreens::onInput);
    }

    // ---- opening -------------------------------------------------------------------------------------------------------

    /** Right-click: opens the screen if the player may use the computer. */
    public static void open(ServerPlayer player, CloudComputerBlockEntity be) {
        if (be.blocked()) {
            var block = be.claimBlock();
            player.sendSystemMessage(block == null ? Component.translatable("cccloud.computer.no_access").withStyle(ChatFormatting.RED)
                : ai.ezfox.cccloud.claims.ClaimGuard.linkMessage(block));
            return;
        }
        if (!be.mayUse(player)) {
            player.sendSystemMessage(Component.translatable("cccloud.computer.no_access").withStyle(ChatFormatting.RED));
            return;
        }
        if (be.handle() == null) return; // starts the computer if this is its first tick
        var pos = be.getBlockPos();
        boolean advanced = be.advanced();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ComputerMenu(id, pos, advanced), be.getBlockState().getBlock().getName()),
            buf -> ComputerMenu.write(buf, pos, advanced));
        if (player.containerMenu instanceof ComputerMenu menu) sync(player, menu);
    }

    // ---- live picture --------------------------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ComputerMenu menu) sync(player, menu);
        }
    }

    /** Sends what changed since the last message to this screen. */
    static void sync(ServerPlayer player, ComputerMenu menu) {
        var be = menu.computer(player);
        var computer = be == null ? null : be.computer();
        if (computer == null) return;
        for (String json : updates(menu, computer)) PacketDistributor.sendToPlayer(player, new ViewPayload(menu.containerId, json));
    }

    /**
     * The messages a screen needs now (a {@code state} when on/off, title or error changed; a {@code term} diff when the
     * terminal changed) and remembers them as sent. Empty when nothing changed.
     */
    static java.util.List<String> updates(ComputerMenu menu, CloudComputer computer) {
        var out = new java.util.ArrayList<String>(2);
        String state = state(computer);
        if (!state.equals(menu.sentState)) {
            menu.sentState = state;
            out.add(state);
        }
        var terminal = computer.terminal();
        long changes = terminal.changes();
        if (changes == menu.sentChanges && menu.sentFrame != null) return out;
        menu.sentChanges = changes;
        var frame = terminal.frame();
        var diff = TerminalFrame.diff(menu.sentFrame, frame, "computer:" + computer.id());
        menu.sentFrame = frame;
        if (diff != null) out.add(diff.toString());
        return out;
    }

    /** The {@code state} message: on/off, title and the last error. */
    static String state(CloudComputer computer) {
        var json = new JsonObject();
        json.addProperty("t", "state");
        json.addProperty("on", computer.isOn());
        json.addProperty("id", computer.id());
        json.addProperty("title", computer.title());
        String error = computer.lastError();
        if (error != null) json.addProperty("error", error.length() > 200 ? error.substring(0, 200) : error);
        return json.toString();
    }

    // ---- input ---------------------------------------------------------------------------------------------------------

    private static void onInput(InputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ComputerMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) return;
        var be = menu.computer(player);
        var computer = be == null ? null : be.computer();
        if (computer == null) return;
        var parsed = parseInput(payload.json(), menu.advanced(), computer.terminal().width(), computer.terminal().height());
        if (parsed == null) return;
        String source = player.getUUID().toString();
        for (var event : parsed) computer.input(event, source);
    }

    /**
     * The events of one input message, or null when anything in it is wrong (the whole message is dropped, like the
     * browser's). Pure; unit tested.
     */
    public static java.util.@Nullable List<InputEvent> parseInput(String json, boolean advanced, int width, int height) {
        if (json.length() > MAX_INPUT) return null;
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return null;
        }
        var parsed = TerminalInput.parse(element, advanced, width, height);
        if (parsed.events() == null) return null;
        var out = new java.util.ArrayList<InputEvent>(parsed.events().size());
        for (var e : parsed.events()) {
            var event = InputEvent.of(e);
            if (event == null) return null;
            out.add(event);
        }
        return out;
    }
}
