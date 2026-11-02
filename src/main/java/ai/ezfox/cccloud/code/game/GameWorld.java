package ai.ezfox.cccloud.code.game;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.code.world.ScriptIdentity;
import ai.ezfox.cccloud.code.world.ScriptWorld;
import ai.ezfox.cccloud.code.world.WorldAction;
import ai.ezfox.cccloud.code.world.WorldSnapshot;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.push.PushService;
import ai.ezfox.cccloud.push.PushTarget;
import ai.ezfox.cccloud.rules.Rule;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.scene.SceneService;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonPrimitive;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The game as Cloud Scripts see it ({@link ScriptWorld}). Script workers only queue here; {@link #tick} runs the queue
 * on the server thread (at most {@link #ACTIONS_PER_TICK} actions per tick for all scripts together), and every action
 * is checked there again exactly like a press on the dashboard: the script's live rights (its author's rights, capped by
 * whoever started it), the widget's permission, the PIN (scripts never have one), the confirmation, the mainframe being
 * online. Then the same code path as rules and scenes runs it.
 */
final class GameWorld implements ScriptWorld {
    /** Script actions per server tick, all mainframes together. */
    static final int ACTIONS_PER_TICK = 64;
    /** Actions waiting for the server thread; beyond this an action is refused right away ({@code busy}). */
    static final int MAX_QUEUED = 2048;

    private record Pending(ScriptIdentity who, WorldAction action, CompletableFuture<@Nullable Object[]> future) {
    }

    private final ConcurrentLinkedQueue<Pending> actions = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final MinecraftServer server;

    GameWorld(MinecraftServer server) {
        this.server = server;
    }

    /** Runs something on the next server tick (any thread). */
    void later(Runnable task) {
        tasks.add(task);
    }

    @Override
    public CompletableFuture<@Nullable Object[]> act(ScriptIdentity who, WorldAction action) {
        var future = new CompletableFuture<@Nullable Object[]>();
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            future.complete(fail("busy"));
            return future;
        }
        actions.add(new Pending(who, action, future));
        return future;
    }

    @Override
    public boolean notify(ScriptIdentity who, Channel channel, String text, String level, @Nullable String title) {
        later(() -> {
            var mainframe = CloudNetwork.mainframe(who.mainframeId());
            if (mainframe == null || !mainframe.isOnline()) return;
            String heading = title != null && !title.isBlank() ? title : who.scriptName();
            if (channel != Channel.PUSH) WebHub.INSTANCE.publishToast(who.mainframeId(), heading + ": " + text, level);
            if (channel != Channel.TOAST) PushService.notify(who.mainframeId(), mainframe.getDisplayName(), PushTarget.Event.ALERTS, level, heading, text);
        });
        return true;
    }

    @Override
    public void audit(ScriptIdentity who, String text) {
        if (server.isSameThread()) WebAuthData.access(server).audit(who.mainframeId(), text);
        else later(() -> WebAuthData.access(server).audit(who.mainframeId(), text));
    }

    /** Server thread: queued tasks, then up to {@link #ACTIONS_PER_TICK} actions. */
    void tick() {
        Runnable task;
        int n = 0;
        while (n++ < 4096 && (task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                CCCloud.LOGGER.error("[cccloud] Cloud Scripts task failed", e);
            }
        }
        for (int i = 0; i < ACTIONS_PER_TICK; i++) {
            var pending = actions.poll();
            if (pending == null) break;
            queued.decrementAndGet();
            @Nullable Object[] result;
            try {
                result = run(pending.who(), pending.action());
            } catch (RuntimeException e) {
                CCCloud.LOGGER.error("[cccloud] Cloud Script action {} failed", pending.action().describe(), e);
                result = fail("internal_error");
            }
            pending.future().complete(result);
        }
    }

    /** Server stopping: everything still waiting is refused. */
    void drain() {
        Pending pending;
        while ((pending = actions.poll()) != null) pending.future().complete(fail("offline"));
        queued.set(0);
        tasks.clear();
    }

    static Object[] ok() {
        return new Object[]{true};
    }

    static Object[] fail(String error) {
        return new Object[]{false, error};
    }

    /** One action on the server thread with the same checks as a dashboard press. */
    @Nullable Object[] run(ScriptIdentity who, WorldAction action) {
        UUID id = who.mainframeId();
        var mainframe = CloudNetwork.mainframe(id);
        if (mainframe == null || mainframe.isRemoved() || !mainframe.isOnline()) return fail("offline");
        if (!CodeService.isRunning(who)) return fail("stopped");
        Set<Perm> perms = CodeService.rights(server, who);
        if (!perms.contains(Perm.VIEW)) return fail("not_allowed");
        return switch (action) {
            case WorldAction.Press p -> widget(mainframe, perms, who, p.cardId(), p.widgetId(), new JsonPrimitive(true), p.confirmed());
            case WorldAction.Set s -> widget(mainframe, perms, who, s.cardId(), s.widgetId(), primitive(s.value()), s.confirmed());
            case WorldAction.SetRedstone r -> {
                if (!perms.contains(Perm.ACT_REDSTONE)) yield fail("not_allowed");
                if (hidden(id, r.cardId(), perms)) yield fail("unknown_card");
                yield result(RuleService.runAction(mainframe, new Rule.RedstoneAction(r.cardId(), r.side(), r.level()), who.user(), false));
            }
            case WorldAction.Craft c -> {
                if (!perms.contains(Perm.STORAGE_CRAFT)) yield fail("not_allowed");
                if (hidden(id, c.cardId(), perms)) yield fail("unknown_card");
                yield result(RuleService.runAction(mainframe, new Rule.CraftAction(c.cardId(), c.item(), c.amount()), who.user(), false));
            }
            case WorldAction.SetEnabled e -> {
                if (!perms.contains(Perm.POWER_TOGGLE)) yield fail("not_allowed");
                mainframe.setEnabled(e.enabled());
                yield ok();
            }
            case WorldAction.RunScene r -> {
                if (!perms.contains(Perm.ACT_BUTTONS)) yield fail("not_allowed");
                var scene = SceneService.scene(id, r.sceneId());
                if (scene == null) yield fail("unknown_scene");
                if (SceneService.needsPin(id, scene)) yield fail("pin_required");
                if (scene.confirm() && !r.confirmed()) yield fail("confirm_required");
                // the steps are checked against the author's live rights, capped by what this script may do now
                var caller = new SceneService.Caller(who.author(), who.authorName(), who.user(), "script", null, perms);
                yield result(SceneService.start(mainframe, r.sceneId(), caller, false));
            }
            case WorldAction.SetRuleEnabled r -> {
                if (!perms.contains(Perm.RULES_TOGGLE)) yield fail("not_allowed");
                yield result(RuleService.setEnabled(server, id, r.ruleId(), r.enabled()));
            }
            case WorldAction.RunRule r -> {
                if (!perms.contains(Perm.RULES_TOGGLE)) yield fail("not_allowed");
                yield result(RuleService.runNow(server, id, r.ruleId()));
            }
            case WorldAction.ItemCount c -> {
                if (!perms.contains(Perm.STORAGE_SEARCH) || hidden(id, c.cardId(), perms)) yield new Object[]{null};
                yield new Object[]{RuleService.itemCount(id, c.cardId(), c.item())};
            }
        };
    }

    private static boolean hidden(UUID mainframeId, String cardId, Set<Perm> perms) {
        return !WebHub.seesRestricted(perms) && WebHub.INSTANCE.isRestricted(mainframeId, cardId);
    }

    private @Nullable Object[] widget(ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity mainframe, Set<Perm> perms, ScriptIdentity who, String cardId,
                                      String widgetId, JsonPrimitive value, boolean confirmed) {
        UUID id = mainframe.getMainframeId();
        if (cardId.equals(WorldSnapshot.MAINFRAME_CARD)) {
            // the mainframe pseudo card: only its dashboard switch can be set
            if (!widgetId.equals("enabled") || !value.isBoolean()) return fail("not_interactive");
            if (!perms.contains(Perm.POWER_TOGGLE)) return fail("not_allowed");
            mainframe.setEnabled(value.getAsBoolean());
            return ok();
        }
        String uplinkId = ai.ezfox.cccloud.rsdevice.RsDevices.sectionOf(id, cardId, widgetId);
        if (hidden(id, uplinkId, perms)) return fail("unknown_card");
        if (SceneService.isScenesSection(id, uplinkId)) return fail("not_interactive"); // cloud.runScene
        if (WebHub.INSTANCE.widgetType(id, uplinkId, widgetId) == null) return fail("unknown_widget");
        if (!perms.contains(WebHub.INSTANCE.actionPerm(id, uplinkId, widgetId))) return fail("not_allowed");
        if (WebHub.INSTANCE.widgetNeedsPin(id, uplinkId, widgetId)) return fail("pin_required");
        if (WebHub.INSTANCE.widgetConfirms(id, uplinkId, widgetId) && !confirmed) return fail("confirm_required");
        return result(RuleService.runAction(mainframe, new Rule.WidgetAction(cardId, widgetId, value), who.user(), false));
    }

    private static JsonPrimitive primitive(Object value) {
        return switch (value) {
            case Boolean b -> new JsonPrimitive(b);
            case Double d when d == Math.rint(d) && Math.abs(d) < 0x1p53 -> new JsonPrimitive(d.longValue());
            case Number n -> new JsonPrimitive(n);
            default -> new JsonPrimitive(String.valueOf(value));
        };
    }

    private static Object[] result(@Nullable String error) {
        if (error == null) return ok();
        return fail(error.equals("unknown_uplink") ? "unknown_card" : error);
    }
}
