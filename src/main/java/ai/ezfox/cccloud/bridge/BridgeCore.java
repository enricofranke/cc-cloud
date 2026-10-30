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

    public record Alert(int seq, String text, String level) {
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

        Job(String id, String name, String displayName, long count, String user, long created) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.count = count;
            this.user = user;
            this.created = created;
        }

        boolean finished() {
            return status.equals("done") || status.equals("failed") || status.equals("cancelled");
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
        for (var job : jobs) {
            if (job.handle == null || job.finished()) continue;
            var state = adapter.jobState(job.handle);
            job.status = state.status();
            job.progress = Math.max(0, Math.min(1, state.progress()));
            if (job.status.equals("done") && crafted.size() < 1024) crafted.merge(job.name, job.count, Long::sum);
            if (job.notify && job.finished()) {
                if (job.status.equals("done")) addAlert("Crafted " + job.count + "× " + job.displayName, "info");
                else addAlert("Crafting " + job.status + ": " + job.count + "× " + job.displayName, "warn");
            }
        }
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
                    jobs.removeIf(job -> job.id.equals(jobId) && job.status.equals(Watchlist.WAITING));
                }
            });
            for (String line : log) CCCloud.LOGGER.info("[cccloud] Auto-stock on the {} bridge: {}", kind, line);
            if (!log.isEmpty()) setChanged.run();
        }
        // Waiting rows of entries that were removed or switched off.
        if (jobs.stream().anyMatch(job -> job.status.equals(Watchlist.WAITING))) {
            var followed = watchlist.jobIds();
            jobs.removeIf(job -> job.status.equals(Watchlist.WAITING) && !followed.contains(job.id));
        }
        if (!watchlist.isEmpty() && isConnected() && !claimPaused) checkWatchlist();
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
            if (!status.equals("running") && !status.equals("queued")) continue;
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
        String error = null;
        var item = ItemNames.byName(name);
        if (!isConnected()) error = "offline";
        else if (item == null) error = "unknown_item";
        else if (count < 1 || count > maxCraftAmount()) error = "too_many";
        else if (!adapter.isCraftable(item)) error = "not_craftable";
        else if (jobs.stream().filter(j -> j.status.equals("queued")).count() >= MAX_PENDING_CRAFTS) error = "busy";
        if (error != null) return new CraftTicket(error, null, CompletableFuture.completedFuture(ActionResult.fail(error)));

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

    /**
     * Auto-stock request. Without a free crafting CPU the job waits instead of failing (one row per item, no failure
     * counted); the next request of that item plans the same job again. Same for every bridge: AE2 reports a missing
     * CPU before planning ({@link StorageAdapter#cpuAvailable}) or when submitting the plan ({@code no_cpu}); RS2 has no
     * CPUs and never waits.
     */
    CraftTicket autoStockCraft(String name, long count) {
        Job waiting = null;
        for (var job : jobs) {
            if (job.status.equals(Watchlist.WAITING) && job.name.equals(name)) {
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
        if (jobs.stream().filter(j -> j.status.equals("queued")).count() >= MAX_PENDING_CRAFTS) {
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
        job.status = "queued";
        job.error = null;
        adapter.startCraft(item, job.count, job.user, outcome -> {
            if (autoStock && "no_cpu".equals(outcome.error())) {
                waitForCpu(job);
            } else if (outcome.error() != null) {
                job.status = "failed";
                job.error = outcome.error();
                complete(job, ActionResult.fail(outcome.error()));
                if (job.notify) addAlert("Crafting failed: " + job.count + "× " + job.displayName + " (" + outcome.error() + ")", "warn");
            } else {
                job.handle = outcome.handle();
                job.status = "running";
                complete(job, new ActionResult(null, job.id));
            }
            setChanged.run();
        });
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
            return true;
        }
        return false;
    }

    /**
     * Rows for the jobs widget and getCraftingJobs(): newest first, own jobs plus other jobs running on the network. An
     * auto-stock job without a free CPU is {@code waiting} (error {@code no_cpu}).
     */
    public JsonArray jobRows(int limit) {
        var array = new JsonArray();
        var handles = new ArrayList<Object>();
        for (var job : jobs) {
            if (job.handle != null) handles.add(job.handle);
            if (array.size() >= limit) continue;
            var row = new JsonObject();
            row.addProperty("jobId", job.id);
            row.addProperty("name", job.name);
            row.addProperty("displayName", job.displayName);
            row.add("count", Json.number(job.count));
            row.addProperty("status", job.status);
            row.add("progress", Json.number(Math.round((job.status.equals("done") ? 1 : job.progress) * 100) / 100.0));
            if (job.error != null) row.addProperty("error", job.error);
            row.addProperty("user", job.user);
            row.addProperty("notify", job.notify);
            row.addProperty("source", job.source);
            array.add(row);
        }
        if (isConnected() && array.size() < limit) {
            for (var job : adapter.networkJobs(handles)) {
                if (array.size() >= limit) break;
                var row = new JsonObject();
                row.addProperty("jobId", job.id());
                row.addProperty("name", ItemNames.id(job.item()));
                row.addProperty("displayName", ItemNames.displayName(job.item()));
                row.add("count", Json.number(job.count()));
                row.addProperty("status", "running");
                row.add("progress", Json.number(Math.round(Math.max(0, Math.min(1, job.progress())) * 100) / 100.0));
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
        } catch (RuntimeException ignored) {
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
