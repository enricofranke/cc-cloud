package ai.ezfox.cccloud.code.game;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.code.world.SnapshotBuilder;
import ai.ezfox.cccloud.code.world.WorldSnapshot;
import ai.ezfox.cccloud.redstone.RedstoneService;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.scene.SceneService;
import ai.ezfox.cccloud.web.WebHub;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/**
 * Builds {@link WorldSnapshot}s of a mainframe in two steps: {@link #base} on the server thread (the mainframe's own
 * state, network devices, redstone channels, rules, scenes: small and only valid there), then {@link #snapshot} on any
 * thread (the dashboard cards, from what the hub already published, in the form for the script's rights).
 */
final class GameSnapshots {
    private GameSnapshots() {
    }

    /** The server-thread part of a snapshot. */
    record Base(long seq, long time, long gameTime, WorldSnapshot.Mainframe mainframe, List<WorldSnapshot.Device> devices, Map<String, Integer> redstone,
                List<WorldSnapshot.RuleInfo> rules, List<WorldSnapshot.SceneInfo> scenes) {
    }

    static Base base(MinecraftServer server, CloudMainframeBlockEntity mainframe, long seq) {
        var id = mainframe.getMainframeId();
        var energy = mainframe.energy();
        var info = new WorldSnapshot.Mainframe(id.toString(), mainframe.getDisplayName(), mainframe.isOnline(), mainframe.isEnabled(), energy.energy(),
            energy.capacity(), mainframe.energyUsage());
        var redstone = new LinkedHashMap<String, Integer>();
        for (var channel : RedstoneService.channels(id).values()) redstone.put(channel.name(), channel.level());
        var rules = new ArrayList<WorldSnapshot.RuleInfo>();
        for (var r : RuleService.scriptView(id)) rules.add(new WorldSnapshot.RuleInfo(r.id(), r.name(), r.enabled(), r.trigger(), r.runs(), r.lastResult()));
        var scenes = new ArrayList<WorldSnapshot.SceneInfo>();
        for (var s : SceneService.scenes(id)) {
            boolean running = SceneService.lastRun(id, s.id()) instanceof ai.ezfox.cccloud.scene.SceneRunner.Run run && !run.done();
            scenes.add(new WorldSnapshot.SceneInfo(s.id(), s.name(), SceneService.needsPin(id, s), s.confirm(), running));
        }
        var devices = SnapshotBuilder.devices(WebHub.INSTANCE.networkForTest(id, false));
        return new Base(seq, System.currentTimeMillis(), RuleService.dayTime(server, id), info, devices, redstone, rules, scenes);
    }

    /** Any thread: the cards as {@code positions} rights see them (restricted cards and private values only with view.positions). */
    static WorldSnapshot snapshot(Base base, boolean positions) {
        var source = WebHub.INSTANCE.scriptSource(java.util.UUID.fromString(base.mainframe().id()), positions);
        var cards = SnapshotBuilder.cards(source.sections().values(), source.restricted(), positions);
        return new WorldSnapshot(base.seq(), base.time(), base.gameTime(), base.mainframe(), cards, base.devices(), base.redstone(), base.rules(), base.scenes());
    }
}
