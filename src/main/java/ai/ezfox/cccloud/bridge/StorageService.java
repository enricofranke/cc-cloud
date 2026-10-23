package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Op;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Storage card v2 on the server thread (docs/SPEC.md "Storage card v2 – protocol"): finds the bridge behind a card and
 * runs browse / craftables / fluids / item / cpus / cancel / favorite / autostock and the craft preview. Arguments are
 * validated strictly here; permissions were checked by the caller.
 */
public final class StorageService {
    public record Result(@Nullable String error, @Nullable JsonObject data) {
        static Result fail(String error) {
            return new Result(error, null);
        }

        static Result ok(JsonObject data) {
            return new Result(null, data);
        }
    }

    private StorageService() {
    }

    /** The bridge behind a card of this mainframe, or null. */
    public static @Nullable BridgeCore coreFor(UUID mainframeId, UUID uplinkId, @Nullable String widgetId) {
        var section = CloudNetwork.section(uplinkId);
        if (section == null || section.getLink() == null || !section.getLink().id().equals(mainframeId)) return null;
        if (section instanceof BridgeSection bridge) return bridge.host().bridgeCore();
        if (section instanceof CloudUplinkBlockEntity uplink) {
            BridgeHost host = widgetId != null ? uplink.bridgeForWidget(widgetId) : uplink.storageBridge();
            return host == null ? null : host.bridgeCore();
        }
        return null;
    }

    public static Result handle(UUID mainframeId, UUID uplinkId, @Nullable String widgetId, String op, JsonObject args) {
        var core = coreFor(mainframeId, uplinkId, widgetId);
        if (core == null) return Result.fail("unavailable");
        if (!core.isConnected()) return Result.fail("offline");
        return switch (op) {
            case "browse" -> browse(core, args);
            case "craftables" -> {
                if (!keys(args, Set.of("q", "offset", "limit"))) yield Result.fail("invalid");
                if (!core.trySearch()) yield Result.fail("rate_limited");
                yield Result.ok(StorageQueries.craftables(core.index(), core.favorites(), str(args, "q"), num(args, "offset", 0), num(args, "limit", 100)));
            }
            case "fluids" -> fluids(core, args);
            case "item" -> item(mainframeId, uplinkId, core, args);
            case "cpus" -> keys(args, Set.of()) ? Result.ok(cpus(core)) : Result.fail("invalid");
            case "cancel" -> {
                String jobId = str(args, "jobId");
                if (!keys(args, Set.of("jobId")) || jobId == null || jobId.length() > 64) yield Result.fail("invalid");
                yield core.cancelAny(jobId) ? Result.ok(new JsonObject()) : Result.fail("unknown_job");
            }
            case "favorite" -> {
                String name = str(args, "name");
                var on = args.get("on");
                if (!keys(args, Set.of("name", "on")) || !Op.isItemName(name) || on == null || !on.isJsonPrimitive() || !on.getAsJsonPrimitive().isBoolean()) {
                    yield Result.fail("invalid");
                }
                String error = core.setFavorite(name, on.getAsBoolean());
                yield error == null ? Result.ok(new JsonObject()) : Result.fail(error);
            }
            case "autostock" -> {
                String name = str(args, "name");
                var auto = args.get("autoCraft");
                long min = num(args, "min", -1), target = num(args, "target", -1);
                if (!keys(args, Set.of("name", "min", "target", "autoCraft")) || !Op.isItemName(name) || min < 0 || target < 0
                    || auto == null || !auto.isJsonPrimitive() || !auto.getAsJsonPrimitive().isBoolean()) yield Result.fail("invalid");
                String error = core.setAutoStock(name, min, target, auto.getAsBoolean());
                yield error == null ? Result.ok(new JsonObject()) : Result.fail(error);
            }
            default -> Result.fail("invalid");
        };
    }

    private static Result browse(BridgeCore core, JsonObject args) {
        if (!keys(args, Set.of("sort", "desc", "filter", "q", "offset", "limit"))) return Result.fail("invalid");
        StorageQueries.Sort sort;
        StorageQueries.Filter filter;
        try {
            sort = StorageQueries.Sort.valueOf(str(args, "sort") == null ? "COUNT" : str(args, "sort").toUpperCase(java.util.Locale.ROOT));
            filter = StorageQueries.Filter.valueOf(str(args, "filter") == null ? "ALL" : str(args, "filter").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.fail("invalid");
        }
        var desc = args.get("desc");
        if (desc != null && !(desc.isJsonPrimitive() && desc.getAsJsonPrimitive().isBoolean())) return Result.fail("invalid");
        String q = str(args, "q");
        if (q != null && q.length() > 64) return Result.fail("invalid");
        if (!core.trySearch()) return Result.fail("rate_limited");
        boolean descending = desc == null ? sort == StorageQueries.Sort.COUNT : desc.getAsBoolean();
        return Result.ok(StorageQueries.browse(core.index(), core.favorites(), sort, descending, filter, q, num(args, "offset", 0), num(args, "limit", 100)));
    }

    private static Result fluids(BridgeCore core, JsonObject args) {
        if (!keys(args, Set.of("offset", "limit"))) return Result.fail("invalid");
        int offset = num(args, "offset", 0), limit = Math.max(1, Math.min(StorageQueries.MAX_LIMIT, num(args, "limit", 100)));
        var all = core.listFluids(10_000);
        var rows = new JsonArray();
        for (int i = Math.min(offset, all.size()); i < Math.min(all.size(), offset + limit); i++) rows.add(Json.fromLua(all.get(i)));
        var data = new JsonObject();
        data.add("rows", rows);
        data.addProperty("total", all.size());
        data.addProperty("offset", offset);
        return Result.ok(data);
    }

    private static Result item(UUID mainframeId, UUID uplinkId, BridgeCore core, JsonObject args) {
        String name = str(args, "name");
        if (!keys(args, Set.of("name")) || !Op.isItemName(name)) return Result.fail("invalid");
        var item = ItemNames.byName(name);
        if (item == null) return Result.fail("unknown_item");
        var data = new JsonObject();
        data.addProperty("name", name);
        data.addProperty("displayName", ItemNames.displayName(item));
        data.addProperty("mod", name.substring(0, name.indexOf(':')));
        data.add("count", Json.number(core.adapter().itemCount(item)));
        data.addProperty("craftable", core.isCraftable(name));
        data.addProperty("favorite", core.favorites().contains(name));
        var watch = core.watchInfo(name);
        if (watch == null) {
            data.add("watch", com.google.gson.JsonNull.INSTANCE);
        } else {
            var w = new JsonObject();
            w.add("min", Json.number(watch.min()));
            w.add("target", Json.number(watch.target()));
            w.addProperty("autoCraft", watch.autoCraft());
            w.addProperty("state", watch.state());
            w.addProperty("failures", watch.failures());
            data.add("watch", w);
        }
        data.add("jobs", core.jobRowsFor(name));
        // long-term statistics (v0.5 F): 24 h of the count, 5-min averages (watchlist and favourite items only)
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        data.add("history", server == null ? new JsonArray() : ai.ezfox.cccloud.history.HistoryService.itemSparkline(server, mainframeId, uplinkId.toString(), name));
        return Result.ok(data);
    }

    private static JsonObject cpus(BridgeCore core) {
        var data = new JsonObject();
        var cpus = new JsonArray();
        for (var cpu : core.adapter().cpus()) {
            var json = new JsonObject();
            json.addProperty("id", cpu.id());
            json.addProperty("name", cpu.name());
            json.addProperty("coProcessors", cpu.coProcessors());
            json.add("storage", Json.number(cpu.storage()));
            json.addProperty("busy", cpu.busy());
            if (cpu.item() == null) {
                json.add("job", com.google.gson.JsonNull.INSTANCE);
            } else {
                var job = new JsonObject();
                job.addProperty("name", ItemNames.id(cpu.item()));
                job.addProperty("displayName", ItemNames.displayName(cpu.item()));
                job.add("count", Json.number(cpu.count()));
                job.add("progress", Json.number(Math.round(cpu.progress() * 100) / 100.0));
                job.add("elapsedMs", Json.number(cpu.elapsedMs()));
                json.add("job", job);
            }
            cpus.add(json);
        }
        data.add("cpus", cpus);
        var tasks = new JsonArray();
        for (var row : core.jobRows(BridgeCore.MAX_JOBS + 32)) {
            var job = row.getAsJsonObject();
            if (!"running".equals(job.get("status").getAsString()) && !"queued".equals(job.get("status").getAsString())) continue;
            var task = new JsonObject();
            task.add("id", job.get("jobId"));
            task.add("name", job.get("name"));
            task.add("displayName", job.get("displayName"));
            task.add("count", job.get("count"));
            task.add("progress", job.get("progress"));
            task.addProperty("own", job.has("user"));
            tasks.add(task);
        }
        data.add("tasks", tasks);
        return data;
    }

    /** Craft preview (craft_plan): calls back with the plan document once the calculation finished. */
    public static void preview(UUID mainframeId, UUID uplinkId, @Nullable String widgetId, String name, long count, Consumer<Result> done) {
        var core = coreFor(mainframeId, uplinkId, widgetId);
        if (core == null) {
            done.accept(Result.fail("unavailable"));
            return;
        }
        core.preview(name, count, plan -> {
            if (plan.error() != null) {
                done.accept(Result.fail(plan.error()));
                return;
            }
            var json = new JsonObject();
            json.addProperty("name", name);
            json.add("count", Json.number(count));
            json.addProperty("missing", plan.missing());
            if (plan.bytes() >= 0) json.add("bytes", Json.number(plan.bytes()));
            else json.add("bytes", com.google.gson.JsonNull.INSTANCE);
            var entries = new JsonArray();
            for (var e : plan.entries()) {
                var row = new JsonObject();
                row.addProperty("name", ItemNames.id(e.item()));
                row.addProperty("displayName", ItemNames.displayName(e.item()));
                row.add("stored", Json.number(e.stored()));
                row.add("toCraft", Json.number(e.toCraft()));
                row.add("missing", Json.number(e.missing()));
                entries.add(row);
            }
            json.add("entries", entries);
            if (plan.cpus() < 0) {
                json.add("cpu", com.google.gson.JsonNull.INSTANCE);
            } else {
                var cpu = new JsonObject();
                cpu.addProperty("available", plan.cpus());
                cpu.addProperty("enough", plan.cpus() > 0);
                json.add("cpu", cpu);
            }
            json.add("timeMs", com.google.gson.JsonNull.INSTANCE);
            done.accept(Result.ok(json));
        });
    }

    private static boolean keys(JsonObject args, Set<String> allowed) {
        for (String key : args.keySet()) if (!allowed.contains(key)) return false;
        return true;
    }

    private static @Nullable String str(JsonObject args, String key) {
        JsonElement e = args.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** A whole number in 0..Integer.MAX_VALUE, else the default (-1 marks "missing/invalid" for required values). */
    private static int num(JsonObject args, String key, int def) {
        JsonElement e = args.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return def;
        double v = e.getAsDouble();
        return v >= 0 && v <= Integer.MAX_VALUE && v == Math.rint(v) ? (int) v : def;
    }
}
