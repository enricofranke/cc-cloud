package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.compat.team.Teams;
import ai.ezfox.cccloud.computer.CloudComputer;
import ai.ezfox.cccloud.computer.ComputerHost;
import ai.ezfox.cccloud.computer.ComputerRegistry;
import ai.ezfox.cccloud.computer.ComputerSide;
import ai.ezfox.cccloud.computer.RedstoneIo;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Holds a Cloud Computer (docs/SPEC.md "Cloud Computers"): its id and whether it was on (saved), the owner (who placed
 * it), the redstone around it ({@link RedstoneIo.Memory}: inputs read from the world here, outputs set by the program
 * and applied here on the server thread). It is the computer's {@link ComputerHost}; the {@link ComputerRegistry} owns
 * the program. Rights: {@link ComputerAccess} plus the claim guard (docs/SECURITY.md "Claims"): a computer in a claim
 * of a team its owner does not belong to is shut down and refuses everyone.
 */
public class CloudComputerBlockEntity extends BlockEntity implements ComputerHost, ClaimGuard.Guarded {
    private int computerId = -1;
    private boolean wasOn;
    private @Nullable UUID owner;
    private final RedstoneIo.Memory redstone = new RedstoneIo.Memory();
    private long appliedOutputs;
    private boolean inputsDirty = true;
    private volatile boolean stateDirty = true;
    private @Nullable CloudComputer computer;
    private ClaimGuard.@Nullable Block claimBlock;
    private @Nullable CloudComputerHandle handle;

    public CloudComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ComputerBlocks.COMPUTER_BE.get(), pos, state);
    }

    @Override
    public boolean advanced() {
        return getBlockState().getBlock() instanceof CloudComputerBlock block && block.advanced();
    }

    public int computerId() {
        return computerId;
    }

    /** The running (loaded) computer, or null on the client and before the first server tick. */
    public @Nullable CloudComputer computer() {
        return computer;
    }

    /** The dashboard's view of this computer (automatic Computer card, browser terminal, editor), server side. */
    public @Nullable CloudComputerHandle handle() {
        if (level == null || level.isClientSide() || isRemoved()) return null;
        if (computer == null) ensureComputer();
        if (computer == null) return null;
        if (handle == null) handle = new CloudComputerHandle(this);
        return handle;
    }

    // ---- life cycle ------------------------------------------------------------------------------------------------------

    /**
     * Placed by a player (or a machine: {@code player} null). An item of a broken computer restores that computer when
     * {@link ComputerAccess#mayRestore} allows it; otherwise the block gets a new id.
     */
    void placed(@Nullable Player player, @Nullable ComputerItemData data) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var server = serverLevel.getServer();
        var registry = ComputerRegistry.get(server);
        var owners = ComputerOwners.get(server);
        if (player != null) owner = player.getUUID();
        if (computer != null) {
            // already started by a tick before setPlacedBy (should not happen): keep it
            owners.set(computerId, owner);
            setChanged();
            return;
        }
        int id = -1;
        if (data != null && player != null) {
            boolean known = owners.owner(data.id()) != null || registry.label(data.id()) != null;
            if (ComputerAccess.mayRestore(known, ComputerBlocks.host(data.id()) != null, owners.owner(data.id()), player.getUUID())) id = data.id();
            else CCCloud.LOGGER.info("[cccloud] {} placed a Cloud Computer item for computer #{} they may not restore; it gets a new id",
                player.getGameProfile().name(), data.id());
        }
        computerId = id >= 0 ? id : registry.create();
        owners.set(computerId, owner);
        setChanged();
        ensureComputer();
    }

    private void ensureComputer() {
        if (computer != null || !(level instanceof ServerLevel serverLevel) || isRemoved()) return;
        var registry = ComputerRegistry.get(serverLevel.getServer());
        if (computerId < 0) {
            computerId = registry.create();
            if (owner != null) ComputerOwners.get(serverLevel.getServer()).set(computerId, owner);
            setChanged();
        } else if (ComputerBlocks.host(computerId) instanceof CloudComputerBlockEntity other && other != this) {
            // A copy of a loaded computer (pick-block with data, a structure): never share one computer between two blocks.
            computerId = registry.create();
            if (owner != null) ComputerOwners.get(serverLevel.getServer()).set(computerId, owner);
            setChanged();
        }
        try {
            computer = registry.load(computerId, this, wasOn && claimBlock == null);
        } catch (IllegalArgumentException e) {
            // an id this world never handed out (copied from another world): start fresh
            computerId = registry.create();
            if (owner != null) ComputerOwners.get(serverLevel.getServer()).set(computerId, owner);
            setChanged();
            computer = registry.load(computerId, this, false);
        }
        ComputerBlocks.setHost(computerId, this);
        stateDirty = true;
        inputsDirty = true;
        ClaimGuard.check(this);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (computer == null) ensureComputer();
        var c = computer;
        if (c == null) return;
        if (inputsDirty) readInputs();
        c.tick();
        long outputs = redstone.outputs();
        if (outputs != appliedOutputs) {
            appliedOutputs = outputs;
            updateNeighbours(serverLevel, getBlockPos(), getBlockState().getBlock());
        }
        boolean on = c.isOn();
        if (on != wasOn) {
            wasOn = on;
            setChanged();
        }
        if (stateDirty || getBlockState().getValue(CloudComputerBlock.ON) != on) {
            stateDirty = false;
            if (getBlockState().getValue(CloudComputerBlock.ON) != on) {
                serverLevel.setBlock(getBlockPos(), getBlockState().setValue(CloudComputerBlock.ON, on), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ClaimGuard.forget(this);
        if (computer != null && level instanceof ServerLevel serverLevel) {
            var registry = ComputerRegistry.get(serverLevel.getServer());
            // only when this block still hosts it (a newer block entity may have loaded the same id)
            if (ComputerBlocks.removeHost(computerId, this)) registry.unload(computerId);
        }
        computer = null;
        handle = null;
    }

    // ---- redstone ------------------------------------------------------------------------------------------------------

    /** The computer-local side for a world direction (as the computer sees it: front = where the screen looks). */
    public static ComputerSide local(Direction facing, Direction world) {
        if (world == Direction.UP) return ComputerSide.TOP;
        if (world == Direction.DOWN) return ComputerSide.BOTTOM;
        if (world == facing) return ComputerSide.FRONT;
        if (world == facing.getOpposite()) return ComputerSide.BACK;
        return world == facing.getCounterClockWise() ? ComputerSide.LEFT : ComputerSide.RIGHT;
    }

    /** The world direction of a computer-local side. */
    public static Direction world(Direction facing, ComputerSide side) {
        return switch (side) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getCounterClockWise();
            case RIGHT -> facing.getClockWise();
        };
    }

    private Direction facing() {
        return getBlockState().getValue(CloudComputerBlock.FACING);
    }

    /** Reads the redstone on all six sides into the computer's inputs (server thread; on neighbour changes). */
    void readInputs() {
        inputsDirty = false;
        if (level == null || level.isClientSide()) return;
        var facing = facing();
        for (var dir : Direction.values()) {
            var at = getBlockPos().relative(dir);
            redstone.setInput(local(facing, dir), level.isLoaded(at) ? level.getSignal(at, dir) : 0);
        }
    }

    /** The level this computer emits towards {@code world} (0 while blocked by a claim). */
    public int outputToward(Direction world) {
        if (claimBlock != null) return 0;
        return redstone.output(local(facing(), world));
    }

    static void updateNeighbours(Level level, BlockPos pos, Block block) {
        level.updateNeighborsAt(pos, block);
        for (var dir : Direction.values()) level.updateNeighborsAt(pos.relative(dir), block);
    }

    // ---- ComputerHost --------------------------------------------------------------------------------------------------

    @Override
    public @Nullable ServerLevel level() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    @Override
    public BlockPos pos() {
        return getBlockPos();
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public RedstoneIo redstone() {
        return redstone;
    }

    /** The world direction of a computer-local side (front = where the screen looks). Overrides ComputerHost#direction. */
    public @Nullable Direction direction(ComputerSide side) {
        return isRemoved() ? null : world(facing(), side);
    }

    /** Any thread: the block state and the neighbours are updated on the next server tick. */
    @Override
    public void changed() {
        stateDirty = true;
    }

    // ---- rights --------------------------------------------------------------------------------------------------------

    /** Whether a player may use this computer in game (screen, typing, power, label). */
    public boolean mayUse(ServerPlayer player) {
        if (claimBlock != null) return false;
        return ComputerAccess.mayUse(owner, player.getUUID(), Access.has(player, ServerPermissions.Node.ADMIN_OPERATE), CloudComputerBlockEntity::sameTeam);
    }

    /** Both players in the same team of the team mod (FTB Teams); false without one. */
    static boolean sameTeam(UUID a, UUID b) {
        var source = Teams.source();
        if (source == null) return false;
        var team = source.teamOf(a);
        return team.isPresent() && team.equals(source.teamOf(b));
    }

    public boolean blocked() {
        return claimBlock != null;
    }

    // ---- claim guard -----------------------------------------------------------------------------------------------------

    @Override
    public @Nullable ServerLevel guardLevel() {
        return level();
    }

    /** Its own block, once it has an owner (a computer without owner is never blocked). */
    @Override
    public @Nullable BlockPos guardPos() {
        return owner == null || isRemoved() ? null : getBlockPos();
    }

    @Override
    public List<UUID> guardOwners() {
        return owner == null ? List.of() : List.of(owner);
    }

    @Override
    public ClaimGuard.@Nullable Block claimBlock() {
        return claimBlock;
    }

    /** Blocked: the computer shuts down, its outputs go to 0 and nobody can use it until the claim allows it again. */
    @Override
    public void applyClaimBlock(ClaimGuard.@Nullable Block block) {
        claimBlock = block;
        if (block != null && computer != null) computer.shutdown();
        if (level instanceof ServerLevel serverLevel) updateNeighbours(serverLevel, getBlockPos(), getBlockState().getBlock());
    }

    @Override
    public String guardLabel() {
        return "computer #" + computerId;
    }

    @Override
    public @Nullable UUID guardMainframe() {
        return null;
    }

    @Override
    public boolean guardRemoved() {
        return isRemoved();
    }

    // ---- save / item -----------------------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        computerId = input.getIntOr("computer_id", -1);
        wasOn = input.getBooleanOr("on", false);
        owner = input.read("owner", UUIDUtil.STRING_CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("computer_id", computerId);
        output.putBoolean("on", wasOn);
        output.storeNullable("owner", UUIDUtil.STRING_CODEC, owner);
    }

    /** The item of a broken computer names it (loot table: copy_components). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (computerId < 0) return;
        String label = computer != null ? computer.label() : null;
        if (label == null && level instanceof ServerLevel serverLevel) label = ComputerRegistry.get(serverLevel.getServer()).label(computerId);
        components.set(ComputerBlocks.COMPUTER_DATA.get(), new ComputerItemData(computerId, label == null ? "" : label));
        if (label != null) components.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(label));
    }

    /** The id on an item is never trusted here: {@link #placed} checks it. */
    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
    }
}
