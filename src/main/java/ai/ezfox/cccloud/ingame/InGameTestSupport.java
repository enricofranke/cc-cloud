package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.WebAuthData;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Shared GameTest setup of the tablet and display tests: server-side fake players (no connection, so no mod sends them
 * login packets), an owned and powered mainframe, and a linked uplink with two Lua-style toggles ({@code lamp}, and
 * {@code secret} with a PIN).
 */
public final class InGameTestSupport {
    public static final BlockPos MAINFRAME_FLOOR = new BlockPos(0, 0, 4);
    public static final BlockPos ANCHOR = new BlockPos(4, 1, 2);
    public static final BlockPos UPLINK = new BlockPos(3, 1, 2);

    public record Setup(ServerPlayer owner, CloudMainframeBlockEntity mainframe) {
        public UUID id() {
            return mainframe.getMainframeId();
        }

        public MainframeLink link() {
            return new MainframeLink(GlobalPos.of(mainframe.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        }
    }

    private InGameTestSupport() {
    }

    public static ServerPlayer player(GameTestHelper helper, String name) {
        var id = UUID.nameUUIDFromBytes(("cccloud-test:" + name).getBytes(StandardCharsets.UTF_8));
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, name));
        var pos = helper.absolutePos(new BlockPos(2, 1, 1)).getCenter();
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    public static Setup mainframe(GameTestHelper helper, String ownerName) {
        var owner = player(helper, ownerName);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, MAINFRAME_FLOOR);
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        return new Setup(owner, mainframe);
    }

    public static void grant(GameTestHelper helper, Setup setup, ServerPlayer player, Role role) {
        WebAuthData.access(helper.getLevel().getServer()).setRole(setup.id(), player.getUUID(), player.getGameProfile().name(), role);
    }

    /** An uplink (stuck to a stone block) with the toggles {@code lamp} and {@code secret} (PIN). */
    public static void placeUplink(GameTestHelper helper) {
        helper.setBlock(ANCHOR, Blocks.STONE);
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.EAST));
    }

    public static CloudUplinkBlockEntity uplink(GameTestHelper helper) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK)) instanceof CloudUplinkBlockEntity uplink)) {
            throw helper.assertionException("uplink missing");
        }
        return uplink;
    }

    public static void bindAndFill(GameTestHelper helper, Setup setup) {
        var uplink = uplink(helper);
        uplink.bind(setup.link(), setup.owner());
        helper.assertTrue(uplink.getLink() != null, "uplink linked");
        try {
            uplink.beginLua();
            uplink.setTitle("Lights");
            uplink.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "lamp", "Lamp", null));
            uplink.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "secret", "Secret", Map.of("pin", true)));
            uplink.putWidget(WidgetFactory.create(WidgetType.LABEL, "note", "Hello from the cloud", null));
        } catch (WidgetFactory.WidgetException e) {
            throw helper.assertionException(e.getMessage());
        }
    }
}
