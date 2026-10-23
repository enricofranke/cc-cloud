package ai.ezfox.cccloud.compat.apothicspawners;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import dev.shadowsoffire.apothic_spawners.block.ApothSpawnerTile;
import dev.shadowsoffire.apothic_spawners.stats.SpawnerStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Apothic Spawners (docs/SPEC.md v0.8 item 4): the upgraded spawner's mob, time to the next spawn, its stats (delay,
 * count, max nearby, player range, the boolean upgrades) and whether it is active right now (a player in range, or
 * "ignore players"; with the Redstone Control upgrade only while powered). No controls: the Redstone Control upgrade
 * is an item the player applies in-game – switching it from the dashboard would bypass the progression.
 */
public final class ApothicSpawnerCards {
    public static final String MOB = "mob";
    public static final String ACTIVE = "active";
    public static final String NEXT = "next";
    public static final String DELAY = "delay";
    public static final String COUNT = "count";
    public static final String NEARBY = "nearby";
    public static final String RANGE = "range";
    public static final String REDSTONE_CONTROL = "redstone_control";
    public static final String UPGRADES = "upgrades";

    private ApothicSpawnerCards() {
    }

    public static void init() {
        DeviceCards.register("apothicspawners", DeviceCards.SPECIFIC, ApothicSpawnerCards::detect);
        Integrations.test("apothicspawners_spawner", () -> ApothicSpawnersGameTests::spawner);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ApothSpawnerTile tile ? new Spawner(level, pos, side, tile) : null;
    }

    /** Display name of an entity type ("minecraft:zombie" → "Zombie"). */
    static String mobName(String id) {
        var type = EntityType.byString(id);
        if (type.isEmpty()) return id.isEmpty() ? "Empty" : id;
        String name = type.get().getDescription().getString();
        String key = type.get().getDescriptionId();
        if (name.equals(key)) name = Fmt.title(EntityType.getKey(type.get()).getPath());
        return name;
    }

    static final class Spawner extends BlockCard<ApothSpawnerTile> {
        Spawner(ServerLevel level, BlockPos pos, Direction side, ApothSpawnerTile tile) {
            super(level, pos, side, tile);
        }

        @Override
        public String kind() {
            return "apothicspawners_spawner";
        }

        @Override
        public String title() {
            return "Spawner";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(MOB, "Mob", "span", 2)
                .indicator(ACTIVE, "Spawning", "onColor", "green", "offColor", "gray")
                .indicator(REDSTONE_CONTROL, "Needs redstone", "onColor", "yellow", "offColor", "gray")
                .progress(NEXT, "Next spawn", 0, 100, "%")
                .label(DELAY, "Spawn delay")
                .label(COUNT, "Mobs per spawn")
                .label(NEARBY, "Max nearby mobs")
                .label(RANGE, "Player range")
                .label(UPGRADES, "Upgrades", "span", 2)
                .build();
        }

        /** The vanilla spawner data through its public save method ("Delay", "SpawnData"). */
        private CompoundTag spawnerTag() {
            try (var reporter = new ProblemReporter.ScopedCollector(be.problemPath(), CloudLog.LOGGER)) {
                var out = TagValueOutput.createWithContext(reporter, level.registryAccess());
                be.getSpawner().save(out);
                return out.buildResult();
            }
        }

        @Override
        public void refresh(CardOutput out) {
            var tag = spawnerTag();
            String id = tag.getCompound("SpawnData").flatMap(d -> d.getCompound("entity")).flatMap(e -> e.getString("id")).orElse("");
            int delay = tag.getShortOr("Delay", (short) 0);
            int min = SpawnerStats.MIN_DELAY.getValue(be);
            int max = SpawnerStats.MAX_DELAY.getValue(be);
            int range = SpawnerStats.REQ_PLAYER_RANGE.getValue(be);
            boolean redstone = SpawnerStats.REDSTONE_CONTROL.getValue(be);
            boolean ignorePlayers = SpawnerStats.IGNORE_PLAYERS.getValue(be);
            boolean players = ignorePlayers || level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, range);
            boolean active = !id.isEmpty() && players && (!redstone || level.hasNeighborSignal(pos));
            out.text(MOB, mobName(id));
            out.bool(ACTIVE, active);
            out.bool(REDSTONE_CONTROL, redstone);
            out.number(NEXT, max > 0 ? Math.round(Math.max(0, Math.min(100, (1 - (double) delay / max) * 100))) : 0);
            out.text(DELAY, min + "–" + max + " ticks");
            out.text(COUNT, String.valueOf(SpawnerStats.SPAWN_COUNT.getValue(be)));
            out.text(NEARBY, String.valueOf(SpawnerStats.MAX_NEARBY_ENTITIES.getValue(be)));
            out.text(RANGE, ignorePlayers ? "Ignores players" : range + " blocks");
            var upgrades = new StringBuilder();
            if (SpawnerStats.IGNORE_CONDITIONS.getValue(be)) upgrades.append("Ignore conditions, ");
            if (SpawnerStats.IGNORE_LIGHT.getValue(be)) upgrades.append("Ignore light, ");
            if (SpawnerStats.NO_AI.getValue(be)) upgrades.append("No AI, ");
            if (SpawnerStats.SILENT.getValue(be)) upgrades.append("Silent, ");
            if (SpawnerStats.YOUTHFUL.getValue(be)) upgrades.append("Youthful, ");
            if (SpawnerStats.BURNING.getValue(be)) upgrades.append("Burning, ");
            int echoing = SpawnerStats.ECHOING.getValue(be);
            if (echoing > 0) upgrades.append("Echoing ").append(echoing).append(", ");
            out.text(UPGRADES, upgrades.isEmpty() ? "None" : upgrades.substring(0, upgrades.length() - 2));
        }
    }
}
