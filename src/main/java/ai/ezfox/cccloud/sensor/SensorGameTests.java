package ai.ezfox.cccloud.sensor;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import ai.ezfox.cccloud.rsdevice.RsPlateBlock;
import ai.ezfox.cccloud.rules.RuleData;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.HubSession;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import org.jspecify.annotations.Nullable;

/**
 * GameTests of the Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"): a zombie and a villager walking in
 * (entered/left events with their types), the owner's team ignored, the filters (mode, allow and deny lists, vanished
 * players), the redstone output (also disarmed), a rule with {@code {who}}, the claim check of the zone, player names
 * hidden from viewers and displays, and the alert rate limit with its summary.
 * <p>
 * The sensor sits on the floor of the test structure ({@link #SENSOR}, facing down), so its default zone (3×3×3 in
 * front) is the 3×3 area around it, three blocks high. Fake players are not in the world's entity list; the tests hand
 * them to the scan ({@link AdvancedSensor#TEST_ENTITIES}).
 */
public final class SensorGameTests {
    static final BlockPos SENSOR = new BlockPos(0, 1, 4);
    /** Inside the default zone, on the structure's floor. */
    static final BlockPos INSIDE = new BlockPos(1, 1, 3);
    static final BlockPos INSIDE_2 = new BlockPos(0, 1, 3);
    /** Outside the zone, on the floor. */
    static final BlockPos OUTSIDE = new BlockPos(4, 1, 0);

    /** Every alert per mainframe ("level:text"), captured in front of the push service's listener. */
    private static final Map<UUID, List<String>> ALERTS = new ConcurrentHashMap<>();
    private static volatile boolean hooked;

    private SensorGameTests() {
    }

    private record Setup(ServerPlayer owner, CloudMainframeBlockEntity mainframe, RsDeviceBlockEntity device, AdvancedSensor sensor) {
        UUID id() {
            return mainframe.getMainframeId();
        }

        String card() {
            return device.deviceId().toString();
        }
    }

    private static synchronized void hookAlerts() {
        if (hooked) return;
        var previous = WebHub.onAlert;
        WebHub.onAlert = (mainframeId, source, text, level) -> {
            ALERTS.computeIfAbsent(mainframeId, k -> new CopyOnWriteArrayList<>()).add(level + ":" + text);
            if (previous != null) previous.alert(mainframeId, source, text, level);
        };
        hooked = true;
    }

    private static List<String> alerts(Setup s) {
        return ALERTS.getOrDefault(s.id(), List.of());
    }

    /** Mainframe + an Advanced Cloud Sensor on the floor, linked, owned by {@code ownerName}. */
    private static Setup setup(GameTestHelper helper, String ownerName) {
        hookAlerts();
        var owner = InGameTestSupport.player(helper, ownerName);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(SENSOR.below(), Blocks.STONE);
        helper.setBlock(SENSOR, ModRegistry.ADVANCED_CLOUD_SENSOR.get().defaultBlockState().setValue(RsPlateBlock.FACING, Direction.DOWN));
        var device = (RsDeviceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(SENSOR));
        var sensor = device.sensor();
        sensor.setOwner(owner);
        device.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
        return new Setup(owner, mainframe, device, sensor);
    }

    private static boolean ready(Setup s) {
        return s.mainframe().isOnline() && s.device().status() == CloudStatus.ONLINE && s.sensor().section() != null
            && WebHub.INSTANCE.sectionSnapshot(s.id(), s.card()) != null;
    }

    /** A mob without AI (it only moves when the test moves it); zombies get a helmet against the sun. */
    private static <E extends Mob> E mob(GameTestHelper helper, EntityType<E> type, BlockPos at) {
        var mob = helper.spawnWithNoFreeWill(type, at);
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        mob.setPersistenceRequired();
        return mob;
    }

    private static void move(GameTestHelper helper, net.minecraft.world.entity.Entity e, BlockPos to) {
        var p = helper.absolutePos(to).getBottomCenter();
        // fake players have no real connection to teleport through: place them directly
        if (e instanceof ServerPlayer) e.setPos(p.x, p.y, p.z);
        else e.teleportTo(p.x, p.y, p.z);
    }

    private static List<SensorLog.Event> entered(Setup s) {
        return s.sensor().log().events().stream().filter(SensorLog.Event::entered).toList();
    }

    private static void cleanup(GameTestHelper helper, Setup s, List<? extends net.minecraft.world.entity.Entity> entities) {
        for (var e : entities) {
            AdvancedSensor.TEST_ENTITIES.remove(e);
            if (!(e instanceof ServerPlayer)) e.discard();
        }
        AdvancedSensor.setClockForTest(s.device().deviceId(), null);
        RuleService.forget(helper.getLevel().getServer(), s.id());
        helper.setBlock(SENSOR, Blocks.AIR);
    }

    // ---- mobs --------------------------------------------------------------------------------------------------------

    /** A zombie and a villager walking in: "entered" events with their kinds and types; the zombie leaving: "left". */
    public static void mobsEnter(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = setup(helper, "sensor_mobs_owner");
        var zombie = mob(helper, EntityType.ZOMBIE, OUTSIDE);
        var villager = mob(helper, EntityType.VILLAGER, OUTSIDE.east(0).south(1));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online with its card"))
            .thenExecute(() -> {
                s.sensor().scan(level); // the baseline (nothing inside yet)
                helper.assertTrue(s.sensor().log().events().isEmpty(), "nothing reported before anything entered");
                move(helper, zombie, INSIDE);
                move(helper, villager, INSIDE_2);
            })
            .thenWaitUntil(() -> helper.assertTrue(entered(s).size() >= 2, "two entered events: " + s.sensor().log().events()))
            .thenExecute(() -> {
                var events = entered(s);
                var zombieEvent = events.stream().filter(e -> e.entry().type().equals("minecraft:zombie")).findFirst().orElse(null);
                var villagerEvent = events.stream().filter(e -> e.entry().type().equals("minecraft:villager")).findFirst().orElse(null);
                helper.assertTrue(zombieEvent != null && zombieEvent.entry().kind() == SensorKind.HOSTILE && "Zombie".equals(zombieEvent.entry().name()),
                    "the zombie is a hostile mob named Zombie: " + zombieEvent);
                helper.assertTrue(villagerEvent != null && villagerEvent.entry().kind() == SensorKind.PASSIVE && "Villager".equals(villagerEvent.entry().name()),
                    "the villager is a passive mob named Villager: " + villagerEvent);
                helper.assertTrue(s.sensor().occupied() && s.sensor().log().today(System.currentTimeMillis(), AdvancedSensor.zoneId()) == 2, "occupied, 2 today");
            })
            .thenWaitUntil(() -> {
                var card = WebHub.INSTANCE.sectionSnapshot(s.id(), s.card());
                helper.assertTrue(card != null && card.toString().contains("\"Zombie\"") && card.toString().contains("\"Villager\""),
                    "the card lists who is inside: " + card);
            })
            .thenExecute(() -> move(helper, zombie, OUTSIDE))
            .thenWaitUntil(() -> helper.assertTrue(s.sensor().log().events().stream()
                .anyMatch(e -> !e.entered() && e.entry().type().equals("minecraft:zombie")), "the zombie left"))
            .thenExecute(() -> {
                helper.assertTrue(s.sensor().inside().size() == 1, "the villager is still inside");
                cleanup(helper, s, List.of(zombie, villager));
            })
            .thenSucceed();
    }

    // ---- team and filters ----------------------------------------------------------------------------------------------

    /**
     * The owner's scoreboard team mate is ignored (a stranger is reported); the filters: players only, mobs only, hostile
     * only, the deny list, the allow list (wins over "ignore my team") and a vanished player.
     */
    public static void teamAndFilters(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = setup(helper, "sensor_team_owner");
        var scoreboard = level.getScoreboard();
        var team = scoreboard.addPlayerTeam("cccloud_sensor_t" + Integer.toHexString(helper.absolutePos(BlockPos.ZERO).hashCode()));
        scoreboard.addPlayerToTeam("sensor_team_owner", team);
        scoreboard.addPlayerToTeam("sensor_team_mate", team);
        var mate = InGameTestSupport.player(helper, "sensor_team_mate");
        var stranger = InGameTestSupport.player(helper, "sensor_stranger");
        var zombie = mob(helper, EntityType.ZOMBIE, OUTSIDE);
        var all = new ArrayList<net.minecraft.world.entity.Entity>(List.of(mate, stranger, zombie));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online"))
            .thenExecute(() -> {
                s.sensor().scan(level);
                move(helper, mate, INSIDE);
                move(helper, stranger, INSIDE_2);
                AdvancedSensor.TEST_ENTITIES.add(mate);
                AdvancedSensor.TEST_ENTITIES.add(stranger);
            })
            .thenWaitUntil(() -> helper.assertTrue(entered(s).stream().anyMatch(e -> "sensor_stranger".equals(e.entry().name())), "the stranger entered"))
            .thenExecute(() -> {
                helper.assertTrue(entered(s).stream().noneMatch(e -> "sensor_team_mate".equals(e.entry().name())), "the team mate is ignored");
                move(helper, zombie, INSIDE);
                s.sensor().scan(level);
                helper.assertTrue(names(s).equals(List.of("sensor_stranger", "Zombie")) || names(s).equals(List.of("Zombie", "sensor_stranger")),
                    "everyone (team ignored): " + names(s));
                filter(helper, s, f -> f.withMode(SensorFilter.Mode.PLAYERS));
                helper.assertTrue(names(s).equals(List.of("sensor_stranger")), "players only: " + names(s));
                filter(helper, s, f -> f.withMode(SensorFilter.Mode.MOBS));
                helper.assertTrue(names(s).equals(List.of("Zombie")), "mobs only: " + names(s));
                filter(helper, s, f -> f.withMode(SensorFilter.Mode.HOSTILE));
                helper.assertTrue(names(s).equals(List.of("Zombie")), "hostile only: " + names(s));
                filter(helper, s, f -> f.withMode(SensorFilter.Mode.PLAYERS).withDeny(List.of("SENSOR_STRANGER")));
                helper.assertTrue(names(s).isEmpty(), "deny list (any case): " + names(s));
                filter(helper, s, f -> f.withDeny(List.of()).withAllow(List.of("sensor_team_mate")));
                helper.assertTrue(names(s).equals(List.of("sensor_team_mate")), "allow list: only the listed player, even from the team: " + names(s));
                filter(helper, s, f -> f.withAllow(List.of()).withIgnoreTeam(false));
                helper.assertTrue(names(s).size() == 2, "ignore my team off: both players: " + names(s));
                stranger.addTag("vanished");
                s.sensor().scan(level);
                s.sensor().scan(level); // gone after LEAVE_AFTER_MISSED scans
                helper.assertTrue(names(s).equals(List.of("sensor_team_mate")), "a vanished player is not reported: " + names(s));
                stranger.removeTag("vanished");
                scoreboard.removePlayerTeam(team);
                cleanup(helper, s, all);
            })
            .thenSucceed();
    }

    private static List<String> names(Setup s) {
        return s.sensor().inside().stream().map(SensorEntry::label).sorted().toList();
    }

    private static void filter(GameTestHelper helper, Setup s, java.util.function.UnaryOperator<SensorFilter> change) {
        var error = s.sensor().update(s.sensor().settings().withFilter(change.apply(s.sensor().settings().filter())), null);
        helper.assertTrue(error == null, "filter applied: " + error);
        s.sensor().scan(helper.getLevel());
    }

    // ---- redstone ------------------------------------------------------------------------------------------------------

    /**
     * The plate powers like a lever while something matching is inside: a lamp next to it lights up and goes out when
     * the zone is empty again; with the output off it stays dark; disarmed the output still works but nothing is logged.
     */
    public static void redstoneOutput(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = setup(helper, "sensor_rs_owner");
        BlockPos lamp = SENSOR.east();
        helper.setBlock(lamp.below(), Blocks.STONE);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        var zombie = mob(helper, EntityType.ZOMBIE, OUTSIDE);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online"))
            .thenExecute(() -> {
                s.sensor().scan(level);
                helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                move(helper, zombie, INSIDE);
            })
            .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true))
            .thenExecute(() -> {
                helper.assertTrue(level.getBlockState(helper.absolutePos(SENSOR)).getValue(RsPlateBlock.POWERED), "the plate shows it (powered)");
                helper.assertTrue(s.device().output() == 15, "output 15");
                move(helper, zombie, OUTSIDE);
            })
            .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false))
            .thenExecute(() -> {
                helper.assertTrue(s.sensor().update(s.sensor().settings().withRedstone(false), null) == null, "output off");
                move(helper, zombie, INSIDE);
                s.sensor().scan(level);
                helper.assertTrue(s.sensor().occupied() && s.device().output() == 0, "occupied but no output");
            })
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                move(helper, zombie, OUTSIDE);
                s.sensor().scan(level);
                s.sensor().scan(level);
                helper.assertTrue(s.sensor().update(s.sensor().settings().withRedstone(true).withArmed(false), null) == null, "disarmed, output on");
                int events = s.sensor().log().events().size();
                move(helper, zombie, INSIDE);
                s.sensor().scan(level);
                helper.assertTrue(s.sensor().log().events().size() == events, "disarmed: no events");
            })
            .thenWaitUntil(() -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true))
            .thenExecute(() -> {
                helper.setBlock(lamp, Blocks.AIR);
                cleanup(helper, s, List.of(zombie));
            })
            .thenSucceed();
    }

    // ---- rules -------------------------------------------------------------------------------------------------------

    /**
     * A rule "Advanced Sensor detects a player" with the alert "{who} betritt den Reaktorraum" runs with the player's
     * name; a rule for another player and a mobs-only rule do not run for that player.
     */
    public static void ruleWithName(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var s = setup(helper, "sensor_rule_owner");
        var visitor = InGameTestSupport.player(helper, "sensor_rule_steve");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online"))
            .thenExecute(() -> {
                s.sensor().scan(level);
                for (String json : List.of(
                    "{\"name\":\"Reactor room\",\"cooldown\":5,\"when\":{\"type\":\"sensor\",\"uplinkId\":\"" + s.card() + "\",\"kind\":\"player\"},"
                        + "\"then\":[{\"type\":\"alert\",\"text\":\"{who} betritt den Reaktorraum\",\"level\":\"warn\"}]}",
                    "{\"name\":\"Only Alex\",\"cooldown\":5,\"when\":{\"type\":\"sensor\",\"uplinkId\":\"" + s.card() + "\",\"player\":\"Alex\"},"
                        + "\"then\":[{\"type\":\"alert\",\"text\":\"Alex is here\",\"level\":\"warn\"}]}",
                    "{\"name\":\"Mobs\",\"cooldown\":5,\"when\":{\"type\":\"sensor\",\"uplinkId\":\"" + s.card() + "\",\"kind\":\"mob\"},"
                        + "\"then\":[{\"type\":\"alert\",\"text\":\"a mob\",\"level\":\"warn\"}]}")) {
                    var saved = RuleService.set(server, s.id(), JsonParser.parseString(json));
                    helper.assertTrue(saved.error() == null, "rule saved: " + saved.error());
                }
            })
            .thenIdle(25) // the rule engine records where the sensor's log stands first
            .thenExecute(() -> {
                move(helper, visitor, INSIDE);
                AdvancedSensor.TEST_ENTITIES.add(visitor);
            })
            .thenWaitUntil(() -> helper.assertTrue(alerts(s).contains("warn:sensor_rule_steve betritt den Reaktorraum"),
                "the rule's alert names the player: " + alerts(s)))
            .thenIdle(25)
            .thenExecute(() -> {
                helper.assertTrue(alerts(s).stream().noneMatch(a -> a.contains("Alex is here") || a.contains("a mob")),
                    "the rules for another player and for mobs did not run: " + alerts(s));
                helper.assertTrue(RuleData.get(server).log(s.id()).stream().anyMatch(e -> e.name().equals("Reactor room") && e.result().equals("ok")),
                    "logged as a run");
                cleanup(helper, s, List.of(visitor));
            })
            .thenSucceed();
    }

    // ---- claims --------------------------------------------------------------------------------------------------------

    /**
     * A zone in a chunk another team claimed (for the owner) is refused when it is set, and a zone claimed later stops
     * the sensor from watching (nothing reported, "zone_claimed"). Both checks go through the claim guard's provider
     * ({@link ai.ezfox.cccloud.gametest.TestClaims}: a team that only distrusts this test's owner). When the claimed chunks
     * include the sensor's own block, the claim guard blocks the sensor itself: its card shows the claim instead of its
     * widgets and refuses actions with {@code claim_blocked}; it works again once the claims go.
     */
    public static void claims(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = setup(helper, "sensor_claim_owner");
        var owner = s.owner();
        var claims = ai.ezfox.cccloud.gametest.TestClaims.get();
        var team = claims.team("sensor_claim_rivals", java.util.Set.of(owner.getUUID()));
        var zombie = mob(helper, EntityType.ZOMBIE, OUTSIDE);
        boolean[] ownChunk = {false};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online"))
            .thenExecute(() -> {
                s.sensor().scan(level);
                // a bigger zone reaches into the chunks claimed by another team: refused, with the player and from the dashboard
                var zone = SensorZone.of(helper.absolutePos(SENSOR), Direction.DOWN, s.sensor().settings().withZone(9, 3, 9, SensorSettings.Placement.FRONT));
                var sensorChunk = net.minecraft.world.level.ChunkPos.containing(helper.absolutePos(SENSOR));
                for (var chunk : zone.chunks()) {
                    claims.claim(level, new BlockPos(chunk.x() << 4, 0, chunk.z() << 4), team);
                    if (chunk.x() == sensorChunk.x() && chunk.z() == sensorChunk.z()) ownChunk[0] = true;
                }
                var bigger = s.sensor().settings().withZone(9, 3, 9, SensorSettings.Placement.FRONT);
                helper.assertTrue("zone_claimed".equals(s.sensor().update(bigger, owner)), "refused in game");
                helper.assertTrue("zone_claimed".equals(s.sensor().update(bigger, null)), "refused from the dashboard (checked for the owner)");
                var card = s.sensor().section();
                var result = card.handleAction(SensorCards.ZONE_X, new JsonPrimitive(9), null, "test", false).getNow(null);
                // the sensor's own chunk claimed too: the claim guard refuses every action first
                String expected = ownChunk[0] ? ai.ezfox.cccloud.claims.ClaimGuard.ERROR : "zone_claimed";
                helper.assertTrue(result != null && expected.equals(result.error()), "the card's zone field says why: " + (result == null ? null : result.error()));
                helper.assertTrue(s.sensor().settings().sizeX() == 3, "the zone stays as it was");
                helper.assertTrue((s.device().claimBlock() != null) == ownChunk[0], "the sensor itself is blocked exactly when its own chunk is claimed");
                // the current zone is claimed now: the sensor stops watching
                move(helper, zombie, INSIDE);
                AdvancedSensor.setClockForTest(s.device().deviceId(), () -> System.currentTimeMillis() + 60_000); // the claim check is due
                s.sensor().scan(level);
                helper.assertTrue("zone_claimed".equals(s.sensor().zoneProblem()) && !s.sensor().occupied() && s.sensor().log().events().isEmpty(),
                    "claimed zone: nothing reported (" + s.sensor().zoneProblem() + ", " + s.sensor().log().events() + ")");
            })
            .thenWaitUntil(() -> {
                var card = WebHub.INSTANCE.sectionSnapshot(s.id(), s.card());
                if (ownChunk[0]) {
                    helper.assertTrue(card != null && card.has("blocked") && card.getAsJsonArray("widgets").isEmpty(), "the card shows the claim: " + card);
                    var publicCard = WebHub.INSTANCE.sectionSnapshot(s.id(), s.card(), false);
                    helper.assertTrue(!publicCard.getAsJsonObject("blocked").has("team"), "the team only for viewers with positions: " + publicCard);
                } else {
                    helper.assertTrue(card != null && card.toString().contains("zone_claimed"), "the card says so: " + card);
                }
            })
            .thenExecute(() -> claims.forget(level, team))
            .thenWaitUntil(() -> {
                helper.assertTrue(s.device().claimBlock() == null, "claims gone: the sensor works again");
                var card = WebHub.INSTANCE.sectionSnapshot(s.id(), s.card());
                helper.assertTrue(card != null && !card.has("blocked") && !card.getAsJsonArray("widgets").isEmpty(), "its card is back: " + card);
            })
            .thenExecute(() -> cleanup(helper, s, List.of(zombie)))
            .thenSucceed();
    }

    // ---- names -------------------------------------------------------------------------------------------------------

    private static final class Session implements HubSession {
        final UUID mainframe;
        final Role role;
        final List<String> received = new CopyOnWriteArrayList<>();
        final AuthStore.Device device;

        Session(UUID mainframe, Role role) {
            this.mainframe = mainframe;
            this.role = role;
            this.device = new AuthStore.Device("sensor-test-" + role.id + "-" + mainframe, mainframe, "test " + role.id, role != Role.VIEWER, 0, 0,
                UUID.randomUUID(), "tester");
        }

        @Override
        public UUID mainframeId() {
            return mainframe;
        }

        @Override
        public AuthStore.@Nullable Device device() {
            return device;
        }

        @Override
        public Role role() {
            return role;
        }

        @Override
        public boolean countsAsBrowser() {
            return false;
        }

        @Override
        public void send(List<String> messages) {
            received.addAll(messages);
        }

        @Override
        public void close(int code, String reason) {
        }

        boolean got(String text) {
            return received.stream().anyMatch(m -> m.contains(text));
        }
    }

    /**
     * Player names reach operators only: a viewer's dashboard, the public form (displays, share links) and the API
     * without {@code view.positions} get "a player" in the card and in the alert; the operator gets the name.
     */
    public static void namesHidden(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = setup(helper, "sensor_names_owner");
        var visitor = InGameTestSupport.player(helper, "sensor_names_alex");
        var operator = new Session(s.id(), Role.OPERATOR);
        var viewer = new Session(s.id(), Role.VIEWER);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online"))
            .thenExecute(() -> {
                s.sensor().scan(level);
                WebHub.INSTANCE.addSession(operator);
                WebHub.INSTANCE.addSession(viewer);
                move(helper, visitor, INSIDE);
                AdvancedSensor.TEST_ENTITIES.add(visitor);
            })
            .thenWaitUntil(() -> helper.assertTrue(operator.got("sensor_names_alex entered") && viewer.got("A player entered"),
                "the alert reached both: " + operator.received.size() + " / " + viewer.received.size()))
            .thenWaitUntil(() -> helper.assertTrue(operator.got("\"n\":\"sensor_names_alex\""), "the operator's card has the name"))
            .thenExecute(() -> {
                helper.assertTrue(viewer.received.stream().noneMatch(m -> m.toLowerCase(java.util.Locale.ROOT).contains("sensor_names_alex")),
                    "nothing the viewer got contains the name");
                helper.assertTrue(viewer.got("A player inside"), "the viewer's summary says a player");
                var display = WebHub.INSTANCE.sectionText(s.id(), s.card());
                helper.assertTrue(display != null && !display.contains("sensor_names_alex") && display.contains("A player inside"),
                    "Cloud Displays get the public form: " + display);
                var full = WebHub.INSTANCE.sectionSnapshot(s.id(), s.card(), true);
                helper.assertTrue(full != null && full.toString().contains("sensor_names_alex"), "the full form has it (operators, rules)");
                var api = WebHub.INSTANCE.sectionSnapshots(s.id(), WebHub.seesNames(ai.ezfox.cccloud.access.Perm.preset(Role.VIEWER)));
                helper.assertTrue(api.stream().noneMatch(j -> j.toString().contains("sensor_names_alex")), "a viewer's API key gets no names");
                WebHub.INSTANCE.removeSession(operator);
                WebHub.INSTANCE.removeSession(viewer);
                cleanup(helper, s, List.of(visitor));
            })
            .thenSucceed();
    }

    // ---- rate limit ----------------------------------------------------------------------------------------------------

    /**
     * Alerts: the first zombie at once, the two that follow during the cooldown as one summary ("2 Zombies entered …")
     * when it is over; a zombie walking out and in again within the per-entity cooldown gives no new event or alert.
     */
    public static void rateLimitAndSummary(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = setup(helper, "sensor_rate_owner");
        var clock = new AtomicLong(1_000_000_000L);
        AdvancedSensor.setClockForTest(s.device().deviceId(), clock::get);
        var a = mob(helper, EntityType.ZOMBIE, OUTSIDE);
        var b = mob(helper, EntityType.ZOMBIE, OUTSIDE.south());
        var c = mob(helper, EntityType.ZOMBIE, OUTSIDE.south(2));
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ready(s), "sensor online"))
            .thenExecute(() -> {
                s.device().setNameAndGroupForTest("Reactor room", "");
                s.sensor().scan(level);
                move(helper, a, INSIDE);
                s.sensor().scan(level);
            })
            .thenWaitUntil(() -> helper.assertTrue(alerts(s).contains("warn:A Zombie entered Reactor room"), "the first one at once: " + alerts(s)))
            .thenExecute(() -> {
                clock.addAndGet(1_000);
                move(helper, b, INSIDE_2);
                move(helper, c, INSIDE_2.above());
                s.sensor().scan(level);
                helper.assertTrue(alerts(s).size() == 1, "collected during the cooldown: " + alerts(s));
                helper.assertTrue(entered(s).size() == 3, "but logged: " + s.sensor().log().events());
                clock.addAndGet(AdvancedSensor.alertCooldownMs());
            })
            .thenWaitUntil(() -> helper.assertTrue(alerts(s).contains("warn:2 Zombies entered Reactor room"), "one summary: " + alerts(s)))
            .thenExecute(() -> {
                int events = s.sensor().log().events().size();
                int sent = alerts(s).size();
                move(helper, a, OUTSIDE);
                s.sensor().scan(level);
                s.sensor().scan(level);
                helper.assertTrue(s.sensor().log().events().size() == events + 1, "it left: " + s.sensor().log().events());
                clock.addAndGet(5_000); // well within the 60 s per-entity cooldown
                move(helper, a, INSIDE);
                s.sensor().scan(level);
                move(helper, a, OUTSIDE);
                s.sensor().scan(level);
                s.sensor().scan(level);
                helper.assertTrue(s.sensor().log().events().size() == events + 1, "walking in and out again: no new events");
                clock.addAndGet(AdvancedSensor.alertCooldownMs() * 2);
                helper.assertTrue(alerts(s).size() == sent, "and no alert");
                cleanup(helper, s, List.of(a, b, c));
            })
            .thenSucceed();
    }
}
