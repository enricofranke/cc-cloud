package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.TokenBucket;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Mod-independent logic of a Cloud Bridge, owned by the bridge block entity: cached item index and search, limits,
 * the watchlist, crafting requests and the list of recent jobs. Server thread only (Lua bridge methods are
 * {@code mainThread = true}, dashboard requests arrive through {@code server.execute}).
 */
public final class BridgeCore {
    /** Ticks an item index snapshot stays valid. */
    public static final int INDEX_TTL = 10;
    /** Craft requests that are being planned at the same time, per bridge. */
    public static final int MAX_PENDING_CRAFTS = 4;
    /** How long a dashboard craft request waits for the plan before it is answered with "queued" (ticks). */
    public static final int CRAFT_REPLY_TIMEOUT = 100;
    /** Recent jobs kept per bridge. */
    public static final int MAX_JOBS = 16;
    /** Job rows shown on the dashboard. */
    public static final int JOB_ROWS = 8;
    /** A craft that cannot run yet for lack of items: an order, it waits for them (never fails, never stuck). */
    public static final String NOT_ENOUGH = "not_enough_items";
    /** Orders kept waiting per bridge; one more fails. Saved with the bridge. */
    public static final int MAX_ORDERS = 8;
    /** How often a waiting order looks at its missing items (ticks). */
    public static final int ORDER_RETRY = 600;
    /** A waiting order is planned again at least this often, even if its missing items did not change (ticks). */
    public static final int ORDER_FULL_RETRY = 6000;
    /** Rows of what is missing in a job row. */
    public static final int MISSING_ROWS = 8;
    /** A "how many are possible" search gives up after this long (ticks). */
    public static final int MAX_SEARCH_TICKS = 400;
    private static final int SLOW_INTERVAL = 20;

    private final StorageAdapter adapter;
    private final String kind;
    private final Runnable setChanged;
    private final Watchlist watchlist = new Watchlist();
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    /** Searches/listings per second per bridge (Lua + dashboard together). */
    private final TokenBucket searchBudget = new TokenBucket(20, 20);
    private ItemIndex index = ItemIndex.EMPTY;
    private long indexTick = Long.MIN_VALUE;
    private long tick;
    private int alertSeq;
    private final ArrayDeque<Alert> alerts = new ArrayDeque<>();
    /** GameTests: moves the auto-stock clock forward (cooldowns are wall-clock time). */
    private long clockOffsetForTest;
    /** Claim guard: the bridge's section is blocked; auto-stock and the watchlist alerts pause. */
    private volatile boolean claimPaused;
    /** Items of jobs that finished {@code done} since the last {@link #takeCrafted()} (v0.10 production statistics). */
    private final Map<String, Long> crafted = new HashMap<>();
    /** Network jobs (terminals) that are being watched for stalls: id, item and amount → the watch. */
    private final Map<String, StuckWatch> networkWatches = new HashMap<>();
    private int stuckSeq;
    private final ArrayDeque<StuckEvent> stuckEvents = new ArrayDeque<>();

    public record Alert(int seq, String text, String level) {
    }

    /** A job that was reported as stuck: what the rule trigger "craft stuck" sees. */
    public record StuckEvent(int seq, String displayName, long count) {
    }

    /**
     * How many of a craft the current items allow.
     *
     * @param max   an amount known to work, null when there is no reliable number
     * @param exact {@code max} is the maximum, not only an amount that works
     */
    public record MaxResult(@Nullable Long max, boolean exact) {
    }

    /** A craft request: immediate error, or a job id plus the result once the job is planned. */
    public record CraftTicket(@Nullable String error, @Nullable String jobId, CompletableFuture<ActionResult> result) {
    }

    private static final class Job {
        final String id;
        final String name;
        final String displayName;
        /** Changes only when a waiting auto-stock job is planned again with a new amount. */
        long count;
        final String user;
        long created;
        /** Alert when finished or failed (storage v2 "notify me"). */
        boolean notify;
        /** dashboard, lua, autostock or rule. */
        String source = "dashboard";
        String status = "queued";
        double progress;
        @Nullable String error;
        @Nullable Object handle;
        @Nullable CompletableFuture<ActionResult> reply;
        final StuckWatch watch;
        /** What the mod last said the job waits for. */
        @Nullable String reason;
        /** Kept until it can start: it could not be planned for lack of items (never for auto-stock, see {@link Watchlist}). */
        boolean order;
        /** A plan is being calculated. */
        boolean planning;
        long lastPlan;
        long nextPlan;
        /** Stored amount of the missing items at the last plan: nothing changed, nothing to plan. */
        long missingHave;
        boolean noticed;
        List<StorageAdapter.PlanEntry> missing = List.of();

        Job(String id, String name, String displayName, long count, String user, long created) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.count = count;
            this.user = user;
            this.created = created;
            this.watch = new StuckWatch(created);
        }

        boolean finished() {
            return status.equals("done") || status.equals("failed") || status.equals("cancelled");
        }

        boolean stuck() {
            return !finished() && watch.stuck();
        }
    }

    /**
     * @param kind "me" or "rs" (used in titles and logs)
     */
    public BridgeCore(StorageAdapter adapter, String kind, Runnable setChanged) {
        this.adapter = adapter;
        this.kind = kind;
        this.setChanged = setChanged;
    }

    public String kind() {
        return kind;
    }

    public StorageAdapter adapter() {
        return adapter;
    }

    public CloudStatus status() {
        return adapter.status();
    }

    public boolean isConnected() {
        return adapter.status() == CloudStatus.ONLINE;
    }

    // ---- tick ------------------------------------------------------------------------------------------------------

    public void tick() {
        tick++;
        try {
            adapter.tick();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Error while polling the {} bridge", kind, e);
        }
        for (var job : jobs) {
            if (job.reply != null && job.status.equals("queued") && tick - job.created >= CRAFT_REPLY_TIMEOUT) {
                // Still planning (big crafting tree): answer the dashboard now, the jobs widget shows the outcome.
                job.reply.complete(new ActionResult(null, job.id));
                job.reply = null;
            }
        }
        if (tick % SLOW_INTERVAL == 0) slowTick();
    }

    private void slowTick() {
        long stuckLimit = claimPaused ? 0 : CloudConfig.get(CloudConfig.CRAFT_STUCK_SECONDS) * 20L;
        for (var job : jobs) {
            if (job.finished()) continue;
            if (job.handle != null) {
                var state = adapter.jobState(job.handle);
                job.status = state.status();
                job.progress = Math.max(0, Math.min(1, state.progress()));
                job.reason = state.reason();
                if (job.reason != null) job.missing = List.of();
                if (job.status.equals("done") && crafted.size() < 1024) crafted.merge(job.name, job.count, Long::sum);
                if (job.notify && job.finished()) {
                    if (job.status.equals("done")) addAlert("Crafted " + job.count + "× " + job.displayName, "info");
                    else addAlert("Crafting " + job.status + ": " + job.count + "× " + job.displayName, "warn");
                }
                if (job.finished()) continue;
            }
            if (waitingForItems(job)) job.watch.started(tick); // an order may wait forever: that is not a hanging job
            else if (job.watch.update(tick, job.progress, stuckLimit)) reportStuck(job.displayName, job.count, stuckReason(job));
        }
        watchNetworkJobs(stuckLimit);
        if (isConnected() && !claimPaused) retryOrders();
        if (!watchlist.isEmpty() && isConnected() && !claimPaused) {
            // Auto-stock (level maintainer): server side, no computer needed.
            var log = watchlist.autoStock(System.currentTimeMillis() + clockOffsetForTest, CloudConfig.get(CloudConfig.AUTO_STOCK_BATCH), new Watchlist.Crafter() {
                @Override
                public boolean craftable(String name) {
                    return isCraftable(name);
                }

                @Override
                public String start(String name, long count) {
                    var ticket = autoStockCraft(name, count);
                    return ticket.error() != null ? "error:" + ticket.error() : ticket.jobId();
                }

                @Override
                public @Nullable String status(String jobId) {
                    for (var job : jobs) if (job.id.equals(jobId)) return job.status;
                    return null;
                }

                @Override
                public @Nullable String error(String jobId) {
                    for (var job : jobs) if (job.id.equals(jobId)) return job.error;
                    return null;
                }

                @Override
                public void discard(String jobId) {
                    jobs.removeIf(job -> job.id.equals(jobId) && job.status.equals(Watchlist.WAITING) && !job.order);
                }
            });
            for (String line : log) CCCloud.LOGGER.info("[cccloud] Auto-stock on the {} bridge: {}", kind, line);
            if (!log.isEmpty()) setChanged.run();
        }
        // Waiting rows of entries that were removed or switched off.
        if (jobs.stream().anyMatch(job -> job.status.equals(Watchlist.WAITING) && !job.order)) {
            var followed = watchlist.jobIds();
            jobs.removeIf(job -> job.status.equals(Watchlist.WAITING) && !job.order && !followed.contains(job.id));
        }
        if (!watchlist.isEmpty() && isConnected() && !claimPaused) checkWatchlist();
    }

    /** The craft cannot go on for lack of items: an order waiting for them, or a task the mod holds back for them. */
    private static boolean waitingForItems(Job job) {
        return NOT_ENOUGH.equals(job.reason) || job.order && job.status.equals(Watchlist.WAITING) && NOT_ENOUGH.equals(job.error);
    }

    /**
     * AE2 and RS2 both refuse a plan with missing items, so a waiting order is planned again here, one per slow pass and
     * only when one of its missing items changed (or after {@link #ORDER_FULL_RETRY}: what is missing can be something
     * that only shows in a new plan), so that waiting orders cost nothing while nothing happens.
     */
    private void retryOrders() {
        if (planningCount() >= MAX_PENDING_CRAFTS) return;
        for (var it = jobs.descendingIterator(); it.hasNext(); ) {
            var job = it.next();
            if (!job.order || !job.status.equals(Watchlist.WAITING) || job.planning || tick < job.nextPlan) continue;
            job.nextPlan = tick + ORDER_RETRY;
            if (!job.missing.isEmpty() && missingHave(job) == job.missingHave && tick - job.lastPlan < ORDER_FULL_RETRY) continue;
            var item = ItemNames.byName(job.name);
            if (item == null) continue;
            plan(job, item);
            return;
        }
    }

    private long missingHave(Job job) {
        long sum = 0;
        for (var entry : job.missing) sum += adapter.itemCount(entry.item());
        return sum;
    }

    private long planningCount() {
        return jobs.stream().filter(j -> j.planning).count();
    }

    private static JsonArray missingRows(List<StorageAdapter.PlanEntry> missing) {
        var array = new JsonArray();
        for (var entry : missing) {
            if (array.size() >= MISSING_ROWS) break;
            var row = new JsonObject();
            row.addProperty("name", ItemNames.id(entry.item()));
            row.addProperty("displayName", ItemNames.displayName(entry.item()));
            row.add("have", Json.number(entry.stored()));
            row.add("need", Json.number(entry.stored() + entry.missing()));
            array.add(row);
        }
        return array;
    }

    private static String missingText(List<StorageAdapter.PlanEntry> missing) {
        var text = new StringBuilder();
        for (var entry : missing.subList(0, Math.min(3, missing.size()))) {
            text.append(text.isEmpty() ? "" : ", ").append(entry.missing()).append("× ").append(ItemNames.displayName(entry.item()));
        }
        return missing.size() > 3 ? text + ", …" : text.toString();
    }

    /** Why a job is stuck: offline, what the mod says, what kept it from starting, else {@code no_progress}. */
    private String stuckReason(Job job) {
        if (!isConnected()) return "offline";
        if (job.reason != null) return job.reason;
        if (job.handle != null) return "no_progress";
        return job.error != null ? job.error : "planning";
    }

    /** One alert and one rule event per job; the job rows show the state for as long as it lasts. */
    private void reportStuck(String displayName, long count, String reason) {
        addAlert("Crafting stuck: " + count + "× " + displayName + " (" + StuckWatch.text(reason) + ")", "warn");
        stuckEvents.addLast(new StuckEvent(++stuckSeq, displayName, count));
        while (stuckEvents.size() > 16) stuckEvents.removeFirst();
    }

    /** Crafts started elsewhere on the network (terminals) get stuck too; they only show, a retry is not ours to make. */
    private void watchNetworkJobs(long stuckLimit) {
        if (!isConnected()) return;
        var seen = new java.util.HashSet<String>();
        for (var job : adapter.networkJobs(ownHandles())) {
            String key = networkKey(job);
            seen.add(key);
            var watch = networkWatches.computeIfAbsent(key, k -> new StuckWatch(tick));
            double progress = Math.max(0, Math.min(1, job.progress()));
            if (watch.update(tick, progress, stuckLimit)) reportStuck(ItemNames.displayName(job.item()), job.count(), job.reason() != null ? job.reason() : "no_progress");
        }
        networkWatches.keySet().retainAll(seen);
    }

    private static String networkKey(StorageAdapter.NetworkJob job) {
        return job.id() + "|" + ItemNames.id(job.item()) + "|" + job.count();
    }

    private List<Object> ownHandles() {
        var handles = new ArrayList<Object>();
        for (var job : jobs) if (job.handle != null) handles.add(job.handle);
        return handles;
    }

    /** Stuck reports newer than {@code seq}, oldest first (rule trigger "craft stuck"). */
    public List<StuckEvent> stuckSince(int seq) {
        var list = new ArrayList<StuckEvent>();
        for (var event : stuckEvents) if (event.seq() > seq) list.add(event);
        return list;
    }

    public int stuckSeq() {
        return stuckSeq;
    }

    /** Claim guard (docs/SECURITY.md "Claims"): pauses auto-stock and the watchlist while the bridge is blocked. */
    public void setClaimPaused(boolean paused) {
        claimPaused = paused;
    }

    /** Refreshes the watchlist counts and turns every new "below min" crossing into an alert. */
    private void checkWatchlist() {
        var crossings = watchlist.check(name -> {
            var item = ItemNames.byName(name);
            return item == null ? 0 : adapter.itemCount(item);
        });
        for (var c : crossings) {
            var item = ItemNames.byName(c.name());
            String display = item == null ? c.name() : ItemNames.displayName(item);
            addAlert(display + " is low: " + c.count() + " / " + c.min(), "warn");
        }
    }

    private void addAlert(String text, String level) {
        alerts.addLast(new Alert(++alertSeq, text.length() > 200 ? text.substring(0, 200) : text, level));
        while (alerts.size() > 16) alerts.removeFirst();
    }

    /** Alerts (watchlist crossings) newer than {@code seq}; uplinks remember the last seq they forwarded. */
    public List<Alert> alertsSince(int seq) {
        var list = new ArrayList<Alert>();
        for (var alert : alerts) if (alert.seq() > seq) list.add(alert);
        return list;
    }

    public int alertSeq() {
        return alertSeq;
    }

    /** Items crafted by finished bridge jobs since the last call (name → count), then forgets them. */
    public Map<String, Long> takeCrafted() {
        if (crafted.isEmpty()) return Map.of();
        var copy = Map.copyOf(crafted);
        crafted.clear();
        return copy;
    }

    /** Running crafting on the network now (own jobs and others): {@code [{name, displayName, count, progress}]}. */
    public JsonArray runningTasks(int limit) {
        var array = new JsonArray();
        for (var e : jobRows(limit)) {
            var row = e.getAsJsonObject();
            String status = row.get("status").getAsString();
            if (!status.equals("running") && !status.equals("queued") && !status.equals("stuck")) continue;
            var task = new JsonObject();
            task.add("name", row.get("name"));
            task.add("displayName", row.get("displayName"));
            task.add("count", row.get("count"));
            task.add("progress", row.get("progress"));
            array.add(task);
        }
        return array;
    }

    // ---- items -----------------------------------------------------------------------------------------------------

    /** Consumes one unit of the per-bridge search budget (20/s). */
    public boolean trySearch() {
        return searchBudget.tryAcquire();
    }

    public ItemIndex index() {
        if (indexTick != Long.MIN_VALUE && tick - indexTick < INDEX_TTL) return index;
        indexTick = tick;
        if (!isConnected()) return index = ItemIndex.EMPTY;
        var counts = new HashMap<Item, Long>();
        boolean[] truncated = {false};
        adapter.forEachItem((item, count) -> {
            if (count <= 0) return;
            if (counts.size() >= ItemIndex.MAX_ENTRIES && !counts.containsKey(item)) {
                truncated[0] = true;
                return;
            }
            counts.merge(item, count, Long::sum);
        });
        var craftable = new java.util.HashSet<Item>();
        adapter.forEachCraftable(item -> {
            if (counts.size() + craftable.size() < ItemIndex.MAX_ENTRIES || counts.containsKey(item)) craftable.add(item);
            else truncated[0] = true;
        });
        var rows = new ArrayList<ItemRow>(counts.size() + craftable.size());
        for (var e : counts.entrySet()) {
            rows.add(new ItemRow(ItemNames.id(e.getKey()), ItemNames.displayName(e.getKey()), e.getValue(), craftable.contains(e.getKey())));
        }
        for (var item : craftable) {
            if (!counts.containsKey(item)) rows.add(new ItemRow(ItemNames.id(item), ItemNames.displayName(item), 0, true));
        }
        return index = new ItemIndex(rows, truncated[0]);
    }

    public @Nullable ItemRow getItem(String name) {
        var item = ItemNames.byName(name);
        if (item == null || !isConnected()) return null;
        long count = adapter.itemCount(item);
        boolean craftable = adapter.isCraftable(item);
        if (count <= 0 && !craftable) return null;
        return new ItemRow(ItemNames.id(item), ItemNames.displayName(item), count, craftable);
    }

    public boolean isCraftable(String name) {
        var item = ItemNames.byName(name);
        return item != null && isConnected() && adapter.isCraftable(item);
    }

    public JsonObject storage() {
        var json = new JsonObject();
        if (!isConnected()) return json;
        var t = adapter.totals();
        json.add("itemsUsed", Json.number(t.itemsUsed()));
        if (t.itemsTotal() != null) json.add("itemsTotal", Json.number(t.itemsTotal()));
        json.add("typesUsed", Json.number(t.typesUsed()));
        if (t.typesTotal() != null) json.add("typesTotal", Json.number(t.typesTotal()));
        json.add("fluidsUsed", Json.number(t.fluidsUsed()));
        if (t.fluidsTotal() != null) json.add("fluidsTotal", Json.number(t.fluidsTotal()));
        return json;
    }

    public List<Map<String, Object>> listFluids(int limit) {
        var list = new ArrayList<Map<String, Object>>();
        if (!isConnected()) return list;
        adapter.forEachFluid((name, displayName, amount) -> {
            if (list.size() >= limit) return;
            var row = new LinkedHashMap<String, Object>();
            row.put("name", name);
            row.put("displayName", displayName);
            row.put("amount", amount);
            list.add(row);
        });
        return list;
    }

    // ---- crafting --------------------------------------------------------------------------------------------------

    public static int maxCraftAmount() {
        return CloudConfig.get(CloudConfig.MAX_CRAFT_AMOUNT);
    }

    /** Validates and starts a craft request. Must be called on the server thread. */
    public CraftTicket craft(String name, long count, String user) {
        return craft(name, count, user, false, user.startsWith("rule:") ? "rule" : "dashboard");
    }

    /**
     * Starts a craft request.
     *
     * @param notify alert when the job finishes or fails
     * @param source dashboard, lua, autostock or rule (shown in the job rows)
     */
    public CraftTicket craft(String name, long count, String user, boolean notify, String source) {
        String error = craftError(name, count, null);
        if (error != null) return new CraftTicket(error, null, CompletableFuture.completedFuture(ActionResult.fail(error)));

        var item = ItemNames.byName(name);
        // A script or rule asking again for what it already ordered follows the same order.
        if (source.equals("lua") || source.equals("rule")) {
            for (var job : jobs) {
                if (job.order && job.status.equals(Watchlist.WAITING) && job.name.equals(ItemNames.id(item)) && job.source.equals(source)) {
                    return new CraftTicket(null, job.id, CompletableFuture.completedFuture(new ActionResult(null, job.id, missingRows(job.missing))));
                }
            }
        }
        var job = new Job(UUID.randomUUID().toString().substring(0, 8), ItemNames.id(item), ItemNames.displayName(item), count, user, tick);
        job.notify = notify;
        job.source = source;
        var reply = new CompletableFuture<ActionResult>();
        job.reply = reply;
        jobs.addFirst(job);
        pruneJobs();
        CCCloud.LOGGER.info("[cccloud] {} requested {}x {} on the {} bridge (job {})", user, count, job.name, kind, job.id);
        plan(job, item);
        return new CraftTicket(null, job.id, reply);
    }

    /** Why a craft cannot be requested right now, or null; {@code replaced} is a job about to go away (it does not count as planning). */
    private @Nullable String craftError(String name, long count, @Nullable Job replaced) {
        var item = ItemNames.byName(name);
        if (!isConnected()) return "offline";
        if (item == null) return "unknown_item";
        if (count < 1 || count > maxCraftAmount()) return "too_many";
        if (!adapter.isCraftable(item)) return "not_craftable";
        if (jobs.stream().filter(j -> j != replaced && j.planning).count() >= MAX_PENDING_CRAFTS) return "busy";
        return null;
    }

    /**
     * Retry of a stuck craft: cancels the job and requests the same craft again, with the same "notify me". Only jobs
     * started from the dashboard: a Lua program or a rule follows its own job and may ask again by itself, and auto-stock
     * plans its next request when a job is cancelled. Nothing is cancelled if the new request would be refused.
     */
    public CraftTicket retryCraft(String jobId, String user) {
        for (var job : jobs) {
            if (!job.id.equals(jobId)) continue;
            String error = !job.stuck() || waitingForItems(job) ? "not_stuck" : !job.source.equals("dashboard") ? "not_retryable" : craftError(job.name, job.count, job);
            if (error == null && !cancelCraft(jobId)) error = "unknown_job";
            if (error != null) return new CraftTicket(error, null, CompletableFuture.completedFuture(ActionResult.fail(error)));
            CCCloud.LOGGER.info("[cccloud] {} retries the stuck job {} on the {} bridge", user, jobId, kind);
            return craft(job.name, job.count, user, job.notify, "dashboard");
        }
        return new CraftTicket("unknown_job", null, CompletableFuture.completedFuture(ActionResult.fail("unknown_job")));
    }

    /**
     * Auto-stock request. Without a free crafting CPU the job waits instead of failing (one row per item, no failure
     * counted); the next request of that item plans the same job again. Same for every bridge: AE2 reports a missing
     * CPU before planning ({@link StorageAdapter#cpuAvailable}) or when submitting the plan ({@code no_cpu}); RS2 has no
     * CPUs and never waits.
     */
    CraftTicket autoStockCraft(String name, long count) {
        Job waiting = null;
        for (var job : jobs) {
            if (job.status.equals(Watchlist.WAITING) && !job.order && job.name.equals(name)) {
                waiting = job;
                break;
            }
        }
        if (waiting == null) return craft(name, count, "autostock", false, "autostock");
        var item = ItemNames.byName(name);
        String error = null;
        if (!isConnected()) error = "offline";
        else if (item == null || !adapter.isCraftable(item)) error = "not_craftable";
        else if (count < 1 || count > maxCraftAmount()) error = "too_many";
        if (error != null) {
            jobs.remove(waiting);
            return new CraftTicket(error, null, CompletableFuture.completedFuture(ActionResult.fail(error)));
        }
        if (planningCount() >= MAX_PENDING_CRAFTS) {
            return new CraftTicket("busy", null, CompletableFuture.completedFuture(ActionResult.fail("busy")));
        }
        waiting.count = count;
        waiting.created = tick;
        plan(waiting, item);
        return new CraftTicket(null, waiting.id, CompletableFuture.completedFuture(new ActionResult(null, waiting.id)));
    }

    /** Plans and starts a queued job (or lets an auto-stock job wait when no crafting CPU is free). */
    private void plan(Job job, Item item) {
        boolean autoStock = job.source.equals("autostock");
        if (autoStock && !adapter.cpuAvailable()) {
            waitForCpu(job);
            return;
        }
        // a waiting order keeps its row while it looks again
        if (!job.order) {
            job.status = "queued";
            job.error = null;
        }
        job.planning = true;
        job.lastPlan = tick;
        adapter.startCraft(item, job.count, job.user, outcome -> {
            job.planning = false;
            if (job.finished()) {
                // Cancelled while it was still planning: it must not start after all.
                if (outcome.handle() != null) adapter.cancel(outcome.handle());
                return;
            }
            if (autoStock && "no_cpu".equals(outcome.error())) {
                waitForCpu(job);
            } else if (outcome.error() != null && (job.order || !autoStock && NOT_ENOUGH.equals(outcome.error()) && orderCount() < MAX_ORDERS)) {
                waitForItems(job, outcome);
            } else if (outcome.error() != null) {
                job.status = "failed";
                job.error = outcome.error();
                job.missing = outcome.missing();
                complete(job, ActionResult.fail(outcome.error()));
                if (job.notify) addAlert("Crafting failed: " + job.count + "× " + job.displayName + " (" + outcome.error() + ")", "warn");
            } else {
                job.handle = outcome.handle();
                job.status = "running";
                job.error = null;
                job.missing = List.of();
                job.order = false;
                job.watch.started(tick);
                complete(job, new ActionResult(null, job.id));
            }
            setChanged.run();
        });
    }

    private long orderCount() {
        return jobs.stream().filter(j -> j.order).count();
    }

    /**
     * Nothing could be planned for lack of items: the order is taken anyway and waits (dashboard, Lua, rules alike), with
     * one notice. Once it waits, any other plan error (no CPU right now, a pattern gone for a moment) only keeps it waiting.
     */
    private void waitForItems(Job job, StorageAdapter.CraftOutcome outcome) {
        job.order = true;
        job.status = Watchlist.WAITING;
        job.error = outcome.error();
        if (!outcome.missing().isEmpty() || NOT_ENOUGH.equals(outcome.error())) job.missing = outcome.missing();
        job.missingHave = missingHave(job);
        job.nextPlan = tick + ORDER_RETRY;
        complete(job, new ActionResult(null, job.id, missingRows(job.missing)));
        if (!job.noticed && NOT_ENOUGH.equals(job.error)) {
            job.noticed = true;
            addAlert("Waiting for items: " + job.count + "× " + job.displayName + (job.missing.isEmpty() ? "" : " (missing " + missingText(job.missing) + ")"), "info");
        }
    }

    private void waitForCpu(Job job) {
        job.status = Watchlist.WAITING;
        job.error = "no_cpu";
        complete(job, ActionResult.fail("no_cpu"));
    }

    private static void complete(Job job, ActionResult result) {
        if (job.reply != null) {
            job.reply.complete(result);
            job.reply = null;
        }
    }

    private void pruneJobs() {
        while (jobs.size() > MAX_JOBS) {
            // Drop the oldest finished job; if none is finished, the oldest one.
            Job victim = null;
            for (var it = jobs.descendingIterator(); it.hasNext(); ) {
                var job = it.next();
                if (job.finished()) {
                    victim = job;
                    break;
                }
            }
            if (victim == null) {
                for (var it = jobs.descendingIterator(); it.hasNext() && victim == null; ) {
                    var job = it.next();
                    if (!job.order) victim = job;
                }
            }
            if (victim == null) victim = jobs.peekLast();
            jobs.remove(victim);
            if (victim.reply != null) victim.reply.complete(ActionResult.fail("busy"));
        }
    }

    public boolean cancelCraft(String jobId) {
        for (var job : jobs) {
            if (!job.id.equals(jobId) || job.finished()) continue;
            if (job.handle != null && !adapter.cancel(job.handle)) return false;
            job.status = "cancelled";
            complete(job, ActionResult.fail("cancelled"));
            if (job.order) setChanged.run();
            return true;
        }
        return false;
    }

    /**
     * Rows for the jobs widget and getCraftingJobs(): newest first, own jobs plus other jobs running on the network. An
     * auto-stock job without a free CPU is {@code waiting} (error {@code no_cpu}). A job without progress for
     * {@code craftStuckSeconds} is {@code stuck}, whatever it was doing; {@code error} then holds the reason, and
     * {@code retryable} says whether the dashboard offers Retry ({@link #retryCraft}). An order without its items is
     * never stuck: {@code waiting} with {@code not_enough_items} and {@code missing} (also on a failed row).
     */
    public JsonArray jobRows(int limit) {
        var array = new JsonArray();
        for (var job : jobs) {
            if (array.size() >= limit) break;
            var row = new JsonObject();
            row.addProperty("jobId", job.id);
            row.addProperty("name", job.name);
            row.addProperty("displayName", job.displayName);
            row.add("count", Json.number(job.count));
            boolean items = waitingForItems(job);
            boolean stuck = !items && job.stuck();
            row.addProperty("status", items ? Watchlist.WAITING : stuck ? "stuck" : job.status);
            row.add("progress", Json.number(Math.round((job.status.equals("done") ? 1 : job.progress) * 100) / 100.0));
            String error = items ? NOT_ENOUGH : stuck ? stuckReason(job) : job.error;
            if (error != null) row.addProperty("error", error);
            if (stuck) row.addProperty("retryable", job.source.equals("dashboard"));
            if (!job.missing.isEmpty()) row.add("missing", missingRows(job.missing));
            row.addProperty("user", job.user);
            row.addProperty("notify", job.notify);
            row.addProperty("source", job.source);
            array.add(row);
        }
        if (isConnected() && array.size() < limit) {
            for (var job : adapter.networkJobs(ownHandles())) {
                if (array.size() >= limit) break;
                var row = new JsonObject();
                row.addProperty("jobId", job.id());
                row.addProperty("name", ItemNames.id(job.item()));
                row.addProperty("displayName", ItemNames.displayName(job.item()));
                row.add("count", Json.number(job.count()));
                var watch = networkWatches.get(networkKey(job));
                boolean stuck = watch != null && watch.stuck();
                row.addProperty("status", stuck ? "stuck" : "running");
                row.add("progress", Json.number(Math.round(Math.max(0, Math.min(1, job.progress())) * 100) / 100.0));
                if (stuck) {
                    row.addProperty("error", job.reason() != null ? job.reason() : "no_progress");
                    row.addProperty("retryable", false);
                }
                array.add(row);
            }
        }
        return array;
    }

    // ---- watchlist -------------------------------------------------------------------------------------------------

    public @Nullable String watchOp(Op op) {
        String error = watchlist.apply(op, name -> ItemNames.byName(name) != null);
        if (error == null) {
            // Refresh the counts right away so the dashboard shows the new row with its amount.
            // An entry added (or its min raised) while already below min alerts right away.
            if (isConnected()) checkWatchlist();
            setChanged.run();
        }
        return error;
    }

    public JsonArray watchRows() {
        return watchlist.rows(name -> {
            var item = ItemNames.byName(name);
            return item == null ? name : ItemNames.displayName(item);
        });
    }

    /** Watchlist (with auto-stock) and favorites, as stored in the bridge block entity. */
    public String saveWatchlist() {
        var json = new JsonObject();
        json.add("watch", com.google.gson.JsonParser.parseString(watchlist.save()));
        var fav = new JsonArray();
        favorites.forEach(fav::add);
        json.add("fav", fav);
        var orders = new JsonArray();
        for (var job : jobs) {
            if (!job.order || !job.status.equals(Watchlist.WAITING)) continue;
            var order = new JsonObject();
            order.addProperty("id", job.id);
            order.addProperty("name", job.name);
            order.add("count", Json.number(job.count));
            order.addProperty("user", job.user);
            order.addProperty("notify", job.notify);
            order.addProperty("source", job.source);
            orders.add(order);
        }
        if (!orders.isEmpty()) json.add("orders", orders);
        return Json.GSON.toJson(json);
    }

    public void loadWatchlist(String json) {
        watchlist.load(json);
        favorites.clear();
        try {
            var parsed = com.google.gson.JsonParser.parseString(json);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().get("fav") instanceof JsonArray fav) {
                for (var e : fav) if (favorites.size() < MAX_FAVORITES && Op.isItemName(e.getAsString())) favorites.add(e.getAsString());
            }
            if (parsed.isJsonObject() && parsed.getAsJsonObject().get("orders") instanceof JsonArray orders) loadOrders(orders);
        } catch (RuntimeException ignored) {
        }
    }

    /** Orders saved with the bridge come back waiting: they are planned again when the bridge ticks. */
    private void loadOrders(JsonArray orders) {
        for (var e : orders) {
            if (orderCount() >= MAX_ORDERS) return;
            var order = e.getAsJsonObject();
            var item = ItemNames.byName(order.get("name").getAsString());
            if (item == null) continue;
            var job = new Job(order.get("id").getAsString(), ItemNames.id(item), ItemNames.displayName(item), order.get("count").getAsLong(), order.get("user").getAsString(), tick);
            job.order = true;
            job.noticed = true;
            job.status = Watchlist.WAITING;
            job.error = NOT_ENOUGH;
            job.notify = order.get("notify").getAsBoolean();
            job.source = order.get("source").getAsString();
            jobs.addLast(job);
        }
    }

    // ---- storage v2 ------------------------------------------------------------------------------------------------

    public static final int MAX_FAVORITES = 64;
    private final java.util.LinkedHashSet<String> favorites = new java.util.LinkedHashSet<>();

    /** v0.5 F: items with long-term count statistics: the watchlist and the favourites (at most 64). */
    public java.util.List<String> trackedItems() {
        var names = new java.util.LinkedHashSet<String>(watchlist.names());
        names.addAll(favorites());
        return names.stream().limit(64).toList();
    }

    public java.util.Set<String> favorites() {
        return java.util.Collections.unmodifiableSet(favorites);
    }

    public @Nullable String setFavorite(String name, boolean on) {
        if (ItemNames.byName(name) == null) return "unknown_item";
        if (on && !favorites.contains(name) && favorites.size() >= MAX_FAVORITES) return "too_many";
        boolean changed = on ? favorites.add(name) : favorites.remove(name);
        if (changed) setChanged.run();
        return null;
    }

    public @Nullable String setAutoStock(String name, long min, long target, boolean autoCraft) {
        String error = watchlist.setAutoStock(name, min, target, autoCraft, n -> ItemNames.byName(n) != null);
        if (error == null) {
            if (isConnected()) checkWatchlist();
            setChanged.run();
        }
        return error;
    }

    /** GameTest hook: lets the auto-stock cooldowns pass without waiting. */
    public void advanceClockForTest(long ms) {
        clockOffsetForTest += ms;
    }

    public Watchlist.@Nullable Info watchInfo(String name) {
        return watchlist.info(name);
    }

    /** Jobs of one item (newest first) for the item details. */
    public JsonArray jobRowsFor(String name) {
        var array = new JsonArray();
        for (var row : jobRows(MAX_JOBS)) if (row.getAsJsonObject().get("name").getAsString().equals(name)) array.add(row);
        return array;
    }

    /** Pending previews per bridge (each runs a crafting calculation). */
    private int previews;
    private boolean maxSearching;

    /** Plans a craft without starting it (storage v2 craft_preview). Server thread; done runs on the server thread. */
    public void preview(String name, long count, java.util.function.Consumer<StorageAdapter.Plan> done) {
        var item = ItemNames.byName(name);
        if (!isConnected()) done.accept(StorageAdapter.Plan.failed("offline"));
        else if (item == null) done.accept(StorageAdapter.Plan.failed("unknown_item"));
        else if (count < 1 || count > maxCraftAmount()) done.accept(StorageAdapter.Plan.failed("too_many"));
        else if (!adapter.isCraftable(item)) done.accept(StorageAdapter.Plan.failed("not_craftable"));
        else if (previews >= MAX_PENDING_CRAFTS) done.accept(StorageAdapter.Plan.failed("busy"));
        else {
            previews++;
            adapter.preview(item, count, plan -> {
                previews--;
                done.accept(plan);
            });
        }
    }

    /**
     * How many of a craft are possible with the items there are now, by simulating amounts below {@code count} (which
     * was just planned with something missing); see {@link MaxSearch}. Each step is the same asynchronous calculation as
     * a preview and counts against the same limit, one search at a time per bridge. Whatever goes wrong gives no number
     * rather than a guess. Server thread; done runs on the server thread.
     */
    public void maxCraftable(String name, long count, java.util.function.Consumer<MaxResult> done) {
        var item = ItemNames.byName(name);
        if (item == null || !isConnected() || count < 1 || count > maxCraftAmount() || maxSearching) {
            done.accept(new MaxResult(null, false));
            return;
        }
        maxSearching = true;
        maxStep(item, new MaxSearch(count), tick, done);
    }

    private void maxStep(Item item, MaxSearch search, long started, java.util.function.Consumer<MaxResult> done) {
        long amount = search.next();
        boolean late = tick - started > MAX_SEARCH_TICKS;
        if (amount < 0 || late || previews >= MAX_PENDING_CRAFTS || !isConnected()) {
            maxSearching = false;
            boolean finished = amount < 0;
            done.accept(search.best() > 0 || finished && search.exact() ? new MaxResult(search.best(), finished && search.exact()) : new MaxResult(null, false));
            return;
        }
        previews++;
        adapter.preview(item, amount, plan -> {
            previews--;
            if (plan.error() != null) {
                maxSearching = false;
                done.accept(new MaxResult(null, false));
                return;
            }
            search.result(amount, !plan.missing());
            maxStep(item, search, started, done);
        });
    }

    /** GameTest hook: lets the stuck time pass without waiting. */
    public void advanceTicksForTest(long ticks) {
        tick += ticks;
    }

    /** Cancels a bridge job or a network job / CPU from {@code cpus}. */
    public boolean cancelAny(String jobId) {
        if (cancelCraft(jobId)) return true;
        return isConnected() && adapter.cancelNetworkJob(jobId);
    }

    // ---- direct mode (Storage template) ---------------------------------------------------------------------------

    /** Values for the template widgets: storage %, energy %, item total, types, energy usage. */
    public Map<String, Object> stats() {
        var map = new LinkedHashMap<String, Object>();
        if (!isConnected()) return map;
        var t = adapter.totals();
        if (t.itemsTotal() != null && t.itemsTotal() > 0) map.put("storage", Math.round(1000.0 * t.itemsUsed() / t.itemsTotal()) / 10.0);
        double max = adapter.maxEnergy();
        if (max > 0) map.put("energy", Math.round(1000.0 * adapter.energy() / max) / 10.0);
        map.put("items", String.format(java.util.Locale.ROOT, "%,d", t.itemsUsed()));
        map.put("types", String.format(java.util.Locale.ROOT, "%,d", t.typesUsed()));
        double usage = adapter.energyUsage();
        map.put("usage", Double.isNaN(usage) ? "n/a" : String.format(java.util.Locale.ROOT, "%.1f %s/t", usage, adapter.energyUnit()));
        return map;
    }
}
