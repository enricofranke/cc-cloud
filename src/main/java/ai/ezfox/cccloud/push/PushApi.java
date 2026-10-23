package ai.ezfox.cccloud.push;

import ai.ezfox.cccloud.web.AccessApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET/POST /api/mainframes/<id>/push} (docs/SPEC.md v0.5 D "Push – web protocol"). The caller has already
 * checked {@code push.manage}. Secrets never leave the server: targets are returned masked. Pure Java, unit tested.
 */
public final class PushApi {
    public interface Hooks {
        boolean enabled();

        Set<String> allowedHosts();

        /** Queues a test message to the target; null if queued, else an error ({@code disabled}, {@code rate_limited}). */
        @Nullable String test(UUID mainframeId, PushTarget target);

        /** {@code lastSent}/{@code lastResult} of a target (memory only), or null. */
        @Nullable JsonObject status(String targetId);

        void audit(UUID mainframeId, String message);
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final PushStore store;
    private final Hooks hooks;

    public PushApi(PushStore store, Hooks hooks) {
        this.store = store;
        this.hooks = hooks;
    }

    public AccessApi.Response get(UUID mainframeId) {
        return new AccessApi.Response(200, document(mainframeId));
    }

    public JsonObject document(UUID mainframeId) {
        var json = new JsonObject();
        json.addProperty("enabled", hooks.enabled());
        var targets = new JsonArray();
        for (var t : store.targets(mainframeId)) {
            var row = t.publicJson();
            var status = hooks.status(t.id());
            row.add("lastSent", status != null && status.has("lastSent") ? status.get("lastSent") : com.google.gson.JsonNull.INSTANCE);
            row.add("lastResult", status != null && status.has("lastResult") ? status.get("lastResult") : com.google.gson.JsonNull.INSTANCE);
            targets.add(row);
        }
        json.add("targets", targets);
        json.addProperty("limit", PushStore.MAX_TARGETS);
        json.addProperty("perMinute", PushService.PER_MINUTE);
        var hosts = new JsonArray();
        hooks.allowedHosts().forEach(hosts::add);
        json.add("allowedHosts", hosts);
        return json;
    }

    /** {@code {"op":"add","target":{…}}}, {@code {"op":"update","id","target":{…}}}, {@code {"op":"delete","id"}}, {@code {"op":"test","id"}}. */
    public AccessApi.Response post(UUID mainframeId, String who, String body) {
        JsonObject obj;
        try {
            var e = JsonParser.parseString(body);
            if (!e.isJsonObject()) return AccessApi.error(400, "invalid");
            obj = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return AccessApi.error(400, "invalid");
        }
        String op = str(obj.get("op"));
        if (op == null) return AccessApi.error(400, "invalid");
        var list = new ArrayList<>(store.targets(mainframeId));
        switch (op) {
            case "add" -> {
                if (!keys(obj, Set.of("op", "target"))) return AccessApi.error(400, "invalid");
                if (list.size() >= PushStore.MAX_TARGETS) return AccessApi.error(400, "too_many");
                String id = newId(list);
                var parsed = PushTarget.parse(obj.get("target"), id, null, hooks.allowedHosts());
                if (parsed.target() == null) return AccessApi.error(400, parsed.error());
                list.add(parsed.target());
                store.set(mainframeId, list);
                hooks.audit(mainframeId, who + " added push target " + parsed.target().name() + " (" + parsed.target().type().id + ")");
            }
            case "update" -> {
                if (!keys(obj, Set.of("op", "id", "target"))) return AccessApi.error(400, "invalid");
                int index = indexOf(list, str(obj.get("id")));
                if (index < 0) return AccessApi.error(404, "unknown_target");
                var parsed = PushTarget.parse(obj.get("target"), list.get(index).id(), list.get(index), hooks.allowedHosts());
                if (parsed.target() == null) return AccessApi.error(400, parsed.error());
                list.set(index, parsed.target());
                store.set(mainframeId, list);
                hooks.audit(mainframeId, who + " changed push target " + parsed.target().name());
            }
            case "delete" -> {
                if (!keys(obj, Set.of("op", "id"))) return AccessApi.error(400, "invalid");
                int index = indexOf(list, str(obj.get("id")));
                if (index < 0) return AccessApi.error(404, "unknown_target");
                var removed = list.remove(index);
                store.set(mainframeId, list);
                hooks.audit(mainframeId, who + " removed push target " + removed.name());
            }
            case "test" -> {
                if (!keys(obj, Set.of("op", "id"))) return AccessApi.error(400, "invalid");
                int index = indexOf(list, str(obj.get("id")));
                if (index < 0) return AccessApi.error(404, "unknown_target");
                String error = hooks.test(mainframeId, list.get(index));
                if (error != null) return AccessApi.error(error.equals("rate_limited") ? 429 : 409, error);
            }
            default -> {
                return AccessApi.error(400, "invalid");
            }
        }
        return new AccessApi.Response(200, document(mainframeId));
    }

    private static int indexOf(java.util.List<PushTarget> list, @Nullable String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return -1;
    }

    private static String newId(java.util.List<PushTarget> list) {
        while (true) {
            byte[] raw = new byte[4];
            RANDOM.nextBytes(raw);
            String id = HexFormat.of().formatHex(raw);
            if (indexOf(list, id) < 0) return id;
        }
    }

    private static boolean keys(JsonObject obj, Set<String> allowed) {
        for (var k : obj.keySet()) if (!allowed.contains(k)) return false;
        return obj.keySet().containsAll(allowed);
    }

    private static @Nullable String str(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }
}
