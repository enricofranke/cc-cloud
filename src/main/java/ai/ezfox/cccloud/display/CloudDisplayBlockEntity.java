package ai.ezfox.cccloud.display;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.web.InGameClients;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One block of a Cloud Display. Forming follows CC: Tweaked's monitors ({@code MonitorBlockEntity}/{@code Expander}):
 * every block knows its index in the rectangle and the rectangle's size; the origin (index 0,0, the top-left seen
 * from the front) owns the content. The configuration (mainframe, section, interactive) is copied to every block of a
 * rectangle so it survives when a rectangle grows or splits.
 * <p>
 * The origin reads its section's snapshot from the hub at most 4 times per second and sends it with the block entity
 * update packet when it changed; the client renders it ({@code DisplayRenderer}).
 */
public class CloudDisplayBlockEntity extends BlockEntity {
    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 6;
    /** Ticks between snapshot reads (4 per second at most). */
    static final int SYNC_INTERVAL = 5;
    /** Max snapshot JSON sent to clients (a bigger section shows "too large"). */
    static final int MAX_JSON = 48 * 1024;

    private int xIndex;
    private int yIndex;
    private int width = 1;
    private int height = 1;
    private boolean removing;
    private boolean needsValidating;
    /** Loaded from a 0.9.0 save, whose indices were lost (see {@link #loadAdditional}): joins its neighbours again. */
    private boolean needsReforming;

    // Configuration (same on every block of the rectangle).
    private @Nullable UUID mainframeId;
    private String mainframeName = "";
    private String sectionId = "";
    private boolean interactive;
    /** The admin who chose what this display shows; the content stops when they lose access to the mainframe. */
    private @Nullable UUID configuredBy;

    /** Origin only: the JSON shown (server: last sent; client: last received). */
    private String content = "";
    private int contentVersion;
    private int ticks;

    public CloudDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(InGameRegistry.DISPLAY_BE.get(), pos, state);
    }

    // ---- geometry (CC monitor logic) -------------------------------------------------------------------------------

    public Direction direction() {
        return getBlockState().getValueOrElse(CloudDisplayBlock.FACING, Direction.NORTH);
    }

    public Direction orientation() {
        return getBlockState().getValueOrElse(CloudDisplayBlock.ORIENTATION, Direction.NORTH);
    }

    /** The face that shows the screen. */
    public Direction front() {
        var orientation = orientation();
        return orientation == Direction.NORTH ? direction() : orientation;
    }

    public Direction right() {
        return direction().getCounterClockWise();
    }

    public Direction down() {
        var orientation = orientation();
        if (orientation == Direction.NORTH) return Direction.UP;
        return orientation == Direction.DOWN ? direction() : direction().getOpposite();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int xIndex() {
        return xIndex;
    }

    public int yIndex() {
        return yIndex;
    }

    public boolean isOrigin() {
        return xIndex == 0 && yIndex == 0;
    }

    boolean isCompatible(CloudDisplayBlockEntity other) {
        return !other.isRemoved() && !other.removing && orientation() == other.orientation() && direction() == other.direction();
    }

    public BlockPos toWorldPos(int x, int y) {
        if (xIndex == x && yIndex == y) return getBlockPos();
        return getBlockPos().relative(right(), -xIndex + x).relative(down(), -yIndex + y);
    }

    private @Nullable CloudDisplayBlockEntity at(int x, int y) {
        if (x == xIndex && y == yIndex) return this;
        if (level == null) return null;
        var pos = toWorldPos(x, y);
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity other && isCompatible(other) ? other : null;
    }

    /** The origin block of this display (this block if it is the origin). */
    public @Nullable CloudDisplayBlockEntity origin() {
        return at(0, 0);
    }

    /** Grows the rectangle into compatible neighbours as far as possible (like CC's Expander). */
    public void expand() {
        var origin = origin();
        if (origin == null || !origin.isOrigin() || level == null) return;
        int changes = 0;
        var state = new int[]{origin.width, origin.height};
        var current = new CloudDisplayBlockEntity[]{origin};
        while (expandIn(current, state, true, false) || expandIn(current, state, true, true)
            || expandIn(current, state, false, false) || expandIn(current, state, false, true)) {
            if (++changes > MAX_WIDTH * MAX_HEIGHT + 1) {
                CCCloud.LOGGER.error("[cccloud] Cloud Display at {} grew too often; stopping", getBlockPos());
                break;
            }
        }
        if (changes > 0) current[0].resize(state[0], state[1]);
    }

    private boolean expandIn(CloudDisplayBlockEntity[] origin, int[] size, boolean xAxis, boolean positive) {
        var o = origin[0];
        int width = size[0], height = size[1];
        int offset = positive ? (xAxis ? width : height) : -1;
        var pos = xAxis ? o.getBlockPos().relative(o.right(), offset) : o.getBlockPos().relative(o.down(), offset);
        if (!(level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity other) || !o.isCompatible(other)) return false;
        if (xAxis) {
            if (other.yIndex != 0 || other.height != height) return false;
            width += other.width;
            if (width > MAX_WIDTH) return false;
        } else {
            if (other.xIndex != 0 || other.width != width) return false;
            height += other.height;
            if (height > MAX_HEIGHT) return false;
        }
        if (!positive) {
            if (!(level.getBlockEntity(other.toWorldPos(0, 0)) instanceof CloudDisplayBlockEntity otherOrigin) || !o.isCompatible(otherOrigin)) return false;
            origin[0] = otherOrigin;
        }
        size[0] = width;
        size[1] = height;
        return true;
    }

    /** Makes this block the origin of a width×height rectangle and updates every member. */
    void resize(int width, int height) {
        var pos = getBlockPos();
        Direction down = down(), right = right();
        // Keep the configuration of whichever member had one (this block first).
        CloudDisplayBlockEntity config = mainframeId != null ? this : null;
        for (int x = 0; x < width && config == null; x++) {
            for (int y = 0; y < height && config == null; y++) {
                if (level.getBlockEntity(pos.relative(right, x).relative(down, y)) instanceof CloudDisplayBlockEntity m && isCompatible(m) && m.mainframeId != null) {
                    config = m;
                }
            }
        }
        var source = config;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!(level.getBlockEntity(pos.relative(right, x).relative(down, y)) instanceof CloudDisplayBlockEntity m) || !isCompatible(m)) continue;
                m.xIndex = x;
                m.yIndex = y;
                m.width = width;
                m.height = height;
                m.needsValidating = false;
                if (source != null && m != source) m.copyConfig(source);
                if (!m.isOrigin()) m.content = "";
                m.updateBlockState();
                m.setChanged();
                m.sendUpdate();
            }
        }
        ticks = SYNC_INTERVAL; // refresh the content soon
    }

    private void copyConfig(CloudDisplayBlockEntity from) {
        mainframeId = from.mainframeId;
        mainframeName = from.mainframeName;
        sectionId = from.sectionId;
        interactive = from.interactive;
        configuredBy = from.configuredBy;
    }

    private void updateBlockState() {
        level.setBlock(getBlockPos(), getBlockState().setValue(CloudDisplayBlock.STATE,
            DisplayEdgeState.of(yIndex < height - 1, yIndex > 0, xIndex > 0, xIndex < width - 1)), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        removing = true;
        if (level != null && !level.isClientSide()) contractNeighbours();
    }

    /** Splits the rectangle around a removed block into up to four rectangles, which then expand again. */
    private void contractNeighbours() {
        if (width == 1 && height == 1) return;
        var pos = getBlockPos();
        Direction down = down(), right = right();
        var origin = toWorldPos(0, 0);
        CloudDisplayBlockEntity left = null, above = null, rightPart = null, below = null;
        if (xIndex > 0) left = tryResizeAt(pos.relative(right, -xIndex), xIndex, 1);
        if (yIndex > 0) above = tryResizeAt(origin, width, yIndex);
        if (xIndex < width - 1) rightPart = tryResizeAt(pos.relative(right, 1), width - xIndex - 1, 1);
        if (yIndex < height - 1) below = tryResizeAt(origin.relative(down, yIndex + 1), width, height - yIndex - 1);
        if (left != null) left.expand();
        if (above != null) above.expand();
        if (rightPart != null) rightPart.expand();
        if (below != null) below.expand();
    }

    private @Nullable CloudDisplayBlockEntity tryResizeAt(BlockPos pos, int width, int height) {
        if (level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity other && isCompatible(other)) {
            other.resize(width, height);
            return other;
        }
        return null;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        removing = false;
        needsValidating = true;
    }

    private boolean checkAt(int x, int y) {
        if (level == null) return false;
        var pos = toWorldPos(x, y);
        if (!level.isLoaded(pos)) return true; // unloaded parts are fine
        var other = at(x, y);
        return other != null && other.xIndex == x && other.yIndex == y && other.width == width && other.height == height;
    }

    /**
     * After loading: a malformed rectangle (e.g. a part was replaced while unloaded) becomes 1×1 and expands again. Parts
     * in chunks that are not loaded never count as malformed (a loaded chunk always has its saved block entities).
     */
    private void validate() {
        if (isOrigin() && width == 1 && height == 1) return;
        if (xIndex >= 0 && xIndex < width && width <= MAX_WIDTH && yIndex >= 0 && yIndex < height && height <= MAX_HEIGHT
            && checkAt(0, 0) && checkAt(0, height - 1) && checkAt(width - 1, 0) && checkAt(width - 1, height - 1)) {
            return;
        }
        CCCloud.LOGGER.warn("[cccloud] Cloud Display at {} was malformed; reset to 1x1", getBlockPos());
        resize(1, 1);
        expand();
    }

    // ---- configuration ---------------------------------------------------------------------------------------------

    public @Nullable UUID mainframeId() {
        return mainframeId;
    }

    public String mainframeName() {
        return mainframeName;
    }

    public String sectionId() {
        return sectionId;
    }

    public boolean interactive() {
        return interactive;
    }

    public @Nullable UUID configuredBy() {
        return configuredBy;
    }

    /** Sets the configuration on every block of the rectangle (server). */
    public void configure(@Nullable UUID mainframe, String name, String section, boolean interactive, @Nullable UUID by) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                var m = at(x, y);
                if (m == null) continue;
                m.mainframeId = mainframe;
                m.mainframeName = name;
                m.sectionId = section;
                m.interactive = interactive;
                m.configuredBy = by;
                m.setChanged();
                m.sendUpdate();
            }
        }
        var origin = origin();
        if (origin != null) origin.ticks = SYNC_INTERVAL;
    }

    // ---- content ---------------------------------------------------------------------------------------------------

    /** The JSON the display shows (origin): the section snapshot or {"error":...}. */
    public String content() {
        return content;
    }

    /** Client: increases with every new content (the renderer redraws its texture). */
    public int contentVersion() {
        return contentVersion;
    }

    public void serverTick(ServerLevel level) {
        if (needsReforming) {
            needsReforming = false;
            needsValidating = false;
            expand();
        } else if (needsValidating) {
            needsValidating = false;
            validate();
        }
        if (!isOrigin() || ++ticks < SYNC_INTERVAL) return;
        ticks = 0;
        String json = readContent();
        if (!json.equals(content)) {
            content = json;
            sendUpdate();
        }
    }

    /** The section's snapshot as JSON, or an error object; restricted (canControl-only) sections are never shown. */
    String readContent() {
        if (mainframeId == null) return error("unlinked");
        if (sectionId.isEmpty()) return error("no_section");
        // Only what the configuring player may see (at least viewer) and never restricted sections: a display is public.
        var server = level == null ? null : level.getServer();
        if (server == null || configuredBy == null
            || !ai.ezfox.cccloud.ingame.InGameAccess.has(server, configuredBy, mainframeId, ai.ezfox.cccloud.access.Perm.VIEW)) {
            return error("no_rights");
        }
        JsonObject snapshot = InGameClients.section(mainframeId, sectionId);
        if (snapshot == null) return error("missing");
        if (snapshot.has("restricted") && snapshot.get("restricted").getAsBoolean()) return error("restricted");
        snapshot.remove("t");
        String json = Json.GSON.toJson(snapshot);
        return json.length() > MAX_JSON ? error("too_large") : json;
    }

    private static String error(String code) {
        return "{\"error\":\"" + code + "\"}";
    }

    private void sendUpdate() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- persistence & sync ----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // Not "x"/"y": a chunk save writes the block position under those keys after saveAdditional (saveMetadata).
        output.putInt("index_x", xIndex);
        output.putInt("index_y", yIndex);
        output.putInt("width", width);
        output.putInt("height", height);
        output.storeNullable("mainframe", UUIDUtil.STRING_CODEC, mainframeId);
        output.putString("mainframe_name", mainframeName);
        output.putString("section", sectionId);
        output.putBoolean("interactive", interactive);
        output.storeNullable("configured_by", UUIDUtil.STRING_CODEC, configuredBy);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        width = Math.max(1, input.getIntOr("width", 1));
        height = Math.max(1, input.getIntOr("height", 1));
        int ix = input.getIntOr("index_x", Integer.MIN_VALUE);
        if (ix != Integer.MIN_VALUE) {
            xIndex = ix;
            yIndex = input.getIntOr("index_y", 0);
        } else if (width > 1 || height > 1) {
            // Saved by 0.9.0 under "x"/"y", which the chunk save overwrote with the block position: the index is lost.
            // Start as a 1×1 display and join the neighbours again on the first tick (like placing the blocks anew).
            xIndex = 0;
            yIndex = 0;
            width = 1;
            height = 1;
            needsReforming = true;
        } else {
            xIndex = 0;
            yIndex = 0;
        }
        mainframeId = input.read("mainframe", UUIDUtil.STRING_CODEC).orElse(null);
        mainframeName = input.getStringOr("mainframe_name", "");
        sectionId = input.getStringOr("section", "");
        interactive = input.getBooleanOr("interactive", false);
        configuredBy = input.read("configured_by", UUIDUtil.STRING_CODEC).orElse(configuredBy);
        // Client: the update packet carries the content too.
        String newContent = input.getStringOr("content", content);
        if (level != null && level.isClientSide() && !Objects.equals(newContent, content)) {
            content = newContent;
            contentVersion++;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("index_x", xIndex);
        tag.putInt("index_y", yIndex);
        tag.putInt("width", width);
        tag.putInt("height", height);
        if (mainframeId != null) tag.putString("mainframe", mainframeId.toString());
        tag.putString("mainframe_name", mainframeName);
        tag.putString("section", sectionId);
        tag.putBoolean("interactive", interactive);
        tag.putString("content", isOrigin() ? content : "");
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
