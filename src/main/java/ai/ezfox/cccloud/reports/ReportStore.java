package ai.ezfox.cccloud.reports;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.Decision;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the report system remembers: {@code <server folder>/cccloud/reports/state.json}, next to the server and not in
 * the world. It holds the owner's decisions, the version a prompt was put aside for, the day stats were last sent and
 * the stack fingerprints sent today. No personal data, no report text, no id; a broken or missing file means "nobody
 * decided". Thread-safe.
 */
public final class ReportStore {
    private static final Gson GSON = new Gson();
    /** Fingerprints remembered at once; more than a day's worth of reports never gets here. */
    private static final int MAX_FINGERPRINTS = 256;

    private final @Nullable Path file;
    private final Map<Channel, Decision> decisions = new EnumMap<>(Channel.class);
    private final Map<String, String> sent = new HashMap<>();
    private int dismissedVersion;
    private String statsDay = "";
    private long crashesAfter;

    /** @param file null: in memory only */
    public ReportStore(@Nullable Path file) {
        this.file = file;
        if (file != null) load(file);
    }

    private void load(Path path) {
        try {
            if (!Files.isRegularFile(path)) return;
            var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            dismissedVersion = json.has("dismissedVersion") ? json.get("dismissedVersion").getAsInt() : 0;
            statsDay = json.has("statsDay") ? json.get("statsDay").getAsString() : "";
            crashesAfter = json.has("crashesAfter") ? json.get("crashesAfter").getAsLong() : 0;
            if (json.has("decisions")) {
                var all = json.getAsJsonObject("decisions");
                for (var channel : Channel.values()) {
                    if (!all.has(channel.id)) continue;
                    var d = all.getAsJsonObject(channel.id);
                    decisions.put(channel, new Decision(d.get("yes").getAsBoolean(), d.get("version").getAsInt()));
                }
            }
            if (json.has("sent")) {
                for (var e : json.getAsJsonObject("sent").entrySet()) sent.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            // no state means undecided, which is the safe side
            CloudLog.LOGGER.warn("[cccloud] Ignoring {}: {}", path.getFileName(), e.toString());
            decisions.clear();
            sent.clear();
        }
    }

    public synchronized @Nullable Decision decision(Channel channel) {
        return decisions.get(channel);
    }

    /** Records the owner's answer; a yes to crash reports covers crashes from this moment on, never older ones. */
    public synchronized void decide(Channel channel, boolean yes, int version, long now) {
        decisions.put(channel, new Decision(yes, version));
        if (yes && channel == Channel.CRASH) crashesAfter = now;
        save();
    }

    public synchronized int dismissedVersion() {
        return dismissedVersion;
    }

    public synchronized void dismiss(int version) {
        if (dismissedVersion >= version) return;
        dismissedVersion = version;
        save();
    }

    public synchronized boolean statsSentOn(String day) {
        return day.equals(statsDay);
    }

    /** True once per UTC day: this call claims the day, also if the send fails afterwards. */
    public synchronized boolean claimStatsDay(String day) {
        if (day.equals(statsDay)) return false;
        statsDay = day;
        save();
        return true;
    }

    /** True if this fingerprint was not sent yet on that UTC day; claims it. */
    public synchronized boolean claimFingerprint(String fingerprint, String day) {
        sent.values().removeIf(d -> !d.equals(day));
        if (sent.containsKey(fingerprint) || sent.size() >= MAX_FINGERPRINTS) return false;
        sent.put(fingerprint, day);
        save();
        return true;
    }

    /** Crash files older than this are not reported: they are from before the consent or were scanned already. */
    public synchronized long crashesAfter() {
        return crashesAfter;
    }

    public synchronized void crashesAfter(long time) {
        crashesAfter = time;
        save();
    }

    private void save() {
        if (file == null) return;
        var json = new JsonObject();
        json.addProperty("dismissedVersion", dismissedVersion);
        json.addProperty("statsDay", statsDay);
        json.addProperty("crashesAfter", crashesAfter);
        var all = new JsonObject();
        decisions.forEach((channel, decision) -> {
            var d = new JsonObject();
            d.addProperty("yes", decision.yes());
            d.addProperty("version", decision.version());
            all.add(channel.id, d);
        });
        json.add("decisions", all);
        var days = new JsonObject();
        sent.forEach(days::addProperty);
        json.add("sent", days);
        try {
            Files.createDirectories(file.getParent());
            var temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.warn("[cccloud] Could not save {}: {}", file.getFileName(), e.toString());
        }
    }
}
