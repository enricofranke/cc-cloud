package ai.ezfox.cccloud.push;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Push targets per mainframe (plain Java; persisted by {@link PushData}). At most {@link #MAX_TARGETS} per mainframe. */
public final class PushStore {
    public static final int MAX_TARGETS = 5;
    private final Map<UUID, List<PushTarget>> targets = new HashMap<>();
    private final Runnable onChange;

    public PushStore(Runnable onChange) {
        this.onChange = onChange;
    }

    public synchronized List<PushTarget> targets(UUID mainframeId) {
        return List.copyOf(targets.getOrDefault(mainframeId, List.of()));
    }

    public synchronized void set(UUID mainframeId, List<PushTarget> list) {
        if (list.isEmpty()) targets.remove(mainframeId);
        else targets.put(mainframeId, List.copyOf(list));
        onChange.run();
    }

    public synchronized void remove(UUID mainframeId) {
        if (targets.remove(mainframeId) != null) onChange.run();
    }

    /** Storage form: mainframe → target JSON (with secrets). */
    public synchronized Map<UUID, List<String>> save() {
        var out = new HashMap<UUID, List<String>>();
        for (var e : targets.entrySet()) out.put(e.getKey(), e.getValue().stream().map(t -> t.toJson().toString()).toList());
        return out;
    }

    /** Loads stored targets; entries that do not validate any more (e.g. a host no longer allowed) are dropped. */
    public synchronized int load(Map<UUID, List<String>> stored, Set<String> allowedHosts) {
        int dropped = 0;
        targets.clear();
        for (var e : stored.entrySet()) {
            var list = new ArrayList<PushTarget>();
            for (String json : e.getValue()) {
                try {
                    var obj = JsonParser.parseString(json).getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    var parsed = PushTarget.ID.matcher(id).matches() ? PushTarget.parse(obj, id, null, allowedHosts) : null;
                    if (parsed != null && parsed.target() != null && list.size() < MAX_TARGETS) list.add(parsed.target());
                    else dropped++;
                } catch (RuntimeException ex) {
                    dropped++;
                }
            }
            if (!list.isEmpty()) targets.put(e.getKey(), list);
        }
        return dropped;
    }
}
