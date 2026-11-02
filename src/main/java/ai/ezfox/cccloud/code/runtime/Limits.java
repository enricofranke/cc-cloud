package ai.ezfox.cccloud.code.runtime;

import com.google.gson.JsonObject;

/**
 * The limits of Cloud Scripts (server config {@code cloudScripts.*}; the last group is fixed). Per mainframe:
 * {@code maxScripts} saved, {@code maxRunning} at once. Per running script: CPU, timers, handlers, storage, log,
 * memory and actions. {@code memoryBytes} is the memory cap of one instance: for Lua an estimate of the reachable heap
 * (plus {@code allocBytesPerSecond}), for an engine with its own heap a hard cap set when the instance is created.
 */
public record Limits(int maxScripts, int maxRunning, int maxSource, int cpuMsPerSecond, int cpuMsPerResume, int versionsKept, int maxTimers, int maxHandlers,
                     int storageBytes, int logLinesPerSecond,
                     // fixed
                     int logKept, long allocBytesPerSecond, long memoryBytes, int actionsPerSecond, int maxTasks, int auditPerMinute, int notifyBurst,
                     double notifyPerSecond) {
    public static final Limits DEFAULT = new Limits(16, 8, 32 * 1024, 50, 200, 20, 32, 64, 16 * 1024, 20,
        200, 1L << 30, 16L << 20, 20, 32, 6, 5, 0.2);

    public Limits withConfig(int maxScripts, int maxRunning, int cpuMsPerSecond, int cpuMsPerResume, int versionsKept, int maxTimers, int maxHandlers,
                             int storageBytes, int logLinesPerSecond) {
        return new Limits(maxScripts, maxRunning, maxSource, cpuMsPerSecond, cpuMsPerResume, versionsKept, maxTimers, maxHandlers, storageBytes,
            logLinesPerSecond, logKept, allocBytesPerSecond, memoryBytes, actionsPerSecond, maxTasks, auditPerMinute, notifyBurst, notifyPerSecond);
    }

    /** The {@code limits} object of the dashboard protocol. */
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("maxScripts", maxScripts);
        json.addProperty("maxRunning", maxRunning);
        json.addProperty("maxSource", maxSource);
        json.addProperty("cpuMsPerSecond", cpuMsPerSecond);
        json.addProperty("cpuMsPerResume", cpuMsPerResume);
        json.addProperty("versionsKept", versionsKept);
        json.addProperty("maxTimers", maxTimers);
        json.addProperty("maxHandlers", maxHandlers);
        json.addProperty("storageBytes", storageBytes);
        json.addProperty("logLinesPerSecond", logLinesPerSecond);
        json.addProperty("logKept", logKept);
        json.addProperty("memoryBytes", memoryBytes);
        json.addProperty("allocBytesPerSecond", allocBytesPerSecond);
        json.addProperty("actionsPerSecond", actionsPerSecond);
        return json;
    }
}
