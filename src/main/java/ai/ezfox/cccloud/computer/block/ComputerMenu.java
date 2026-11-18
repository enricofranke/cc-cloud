package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.terminal.TerminalFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The slot-less menu of an open Cloud Computer screen. The server uses it to know who looks at which computer (only
 * those players get terminal updates) and remembers what it sent last, so only changes go out.
 */
public class ComputerMenu extends AbstractContainerMenu {
    /** Max distance (blocks) between the player and the computer while the screen is open. */
    public static final double RANGE = 8.0;

    private final BlockPos pos;
    private final boolean advanced;
    /** Server side: what this screen has. */
    @Nullable TerminalFrame sentFrame;
    long sentChanges = -1;
    @Nullable String sentState;
    /** Client side: the terminal picture and the last {@code state} message. */
    public final TerminalMirror mirror = new TerminalMirror();
    public com.google.gson.JsonObject state = new com.google.gson.JsonObject();

    /** Client side: applies a view message (a {@code term} diff or a {@code state}). */
    public void applyView(String json) {
        com.google.gson.JsonObject msg;
        try {
            msg = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        if (msg.get("t") instanceof com.google.gson.JsonPrimitive t && "state".equals(t.getAsString())) state = msg;
        else mirror.apply(msg);
    }

    public ComputerMenu(int id, BlockPos pos, boolean advanced) {
        super(ComputerBlocks.COMPUTER_MENU.get(), id);
        this.pos = pos.immutable();
        this.advanced = advanced;
    }

    public ComputerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), buf.readBoolean());
    }

    static void write(RegistryFriendlyByteBuf buf, BlockPos pos, boolean advanced) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(advanced);
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean advanced() {
        return advanced;
    }

    /** Server side: the computer block behind the screen, or null when it is gone. */
    @Nullable CloudComputerBlockEntity computer(Player player) {
        return player.level().isLoaded(pos) && player.level().getBlockEntity(pos) instanceof CloudComputerBlockEntity be && !be.isRemoved() ? be : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** In range, block still there, and (server) the player may still use it: a lost right or a claim closes the screen. */
    @Override
    public boolean stillValid(Player player) {
        if (player.distanceToSqr(pos.getCenter()) > RANGE * RANGE) return false;
        var be = computer(player);
        if (be == null) return false;
        return !(player instanceof ServerPlayer serverPlayer) || be.mayUse(serverPlayer);
    }
}
