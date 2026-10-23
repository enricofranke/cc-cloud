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

    public record Alert(int seq, String text, String level) {
    }

    /** A craft request: immediate error, or a job id plus the result once the job is planned. */
    public record CraftTicket(@Nullable String error, @Nullable String jobId, CompletableFuture<ActionResult> result) {
    }

    private static final class Job {
        final String id;
        final String name;
        final String displayName;
        final long count;
        final String user;
        final long created;
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
        }
        if (!watchlist.isEmpty() && isConnected()) checkWatchlist();
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
        String error = null;
        var item = ItemNames.byName(name);
        if (!isConnected()) error = "offline";
        else if (item == null) error = "unknown_item";
        else if (count < 1 || count > maxCraftAmount()) error = "too_many";
        else if (!adapter.isCraftable(item)) error = "not_craftable";
        else if (jobs.stream().filter(j -> j.status.equals("queued")).count() >= MAX_PENDING_CRAFTS) error = "busy";
        if (error != null) return new CraftTicket(error, null, CompletableFuture.completedFuture(ActionResult.fail(error)));

        var job = new Job(UUID.randomUUID().toString().substring(0, 8), ItemNames.id(item), ItemNames.displayName(item), count, user, tick);
        var reply = new CompletableFuture<ActionResult>();
        job.reply = reply;
        jobs.addFirst(job);
        pruneJobs();
        CCCloud.LOGGER.info("[cccloud] {} requested {}x {} on the {} bridge (job {})", user, count, job.name, kind, job.id);
        adapter.startCraft(item, count, user, outcome -> {
            if (outcome.error() != null) {
                job.status = "failed";
                job.error = outcome.error();
                complete(job, ActionResult.fail(outcome.error()));
            } else {
                job.handle = outcome.handle();
                job.status = "running";
                complete(job, new ActionResult(null, job.id));
            }
            setChanged.run();
        });
        return new CraftTicket(null, job.id, reply);
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

    /** Rows for the jobs widget and getCraftingJobs(): newest first, own jobs plus other jobs running on the network. */
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

    public String saveWatchlist() {
        return watchlist.save();
    }

    public void loadWatchlist(String json) {
        watchlist.load(json);
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
