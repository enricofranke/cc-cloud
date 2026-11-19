package ai.ezfox.cccloud.code.runtime;

import com.google.gson.JsonObject;

/**
 * The limits of Cloud Scripts (server config {@code cloudScripts.*}; the last group is fixed). Per mainframe:
 * {@code maxScripts} saved, {@code maxRunning} at once. Per running script: CPU, timers, handlers, storage, log,
 * memory and actions. {@code memoryBytes} is the memory cap of one instance: for Lua an estimate of the reachable heap
 * (plus {@code allocBytesPerSecond}), for an engine with its own heap a hard cap set when the instance is created.
 * WebAssembly scripts: {@code maxWasmBytes} for the module, {@code maxWasmSource} for the source text stored with it
 * (shown in the dashboard), {@code hostCallsPerSecond} and {@code hostCallBytes} for the calls into the host (JSON in and
 * out).
 */
public record Limits(int maxScripts, int maxRunning, int maxSource, int cpuMsPerSecond, int cpuMsPerResume, int versionsKept, int maxTimers, int maxHandlers,
                     int storageBytes, int logLinesPerSecond, int maxWasmBytes, int maxWasmSource,
                     // fixed
                     int logKept, long allocBytesPerSecond, long memoryBytes, int actionsPerSecond, int maxTasks, int auditPerMinute, int notifyBurst,
                     double notifyPerSecond, int hostCallsPerSecond, int hostCallBytes) {
    public static final Limits DEFAULT = new Limits(16, 8, 32 * 1024, 50, 200, 20, 32, 64, 16 * 1024, 20, 128 * 1024, 32 * 1024,
        200, 1L << 30, 16L << 20, 20, 32, 6, 5, 0.2, 2000, 64 * 1024);

    public Limits withConfig(int maxScripts, int maxRunning, int cpuMsPerSecond, int cpuMsPerResume, int versionsKept, int maxTimers, int maxHandlers,
                             int storageBytes, int logLinesPerSecond, int maxWasmBytes, int maxWasmSource) {
        return new Limits(maxScripts, maxRunning, maxSource, cpuMsPerSecond, cpuMsPerResume, versionsKept, maxTimers, maxHandlers, storageBytes,
            logLinesPerSecond, maxWasmBytes, maxWasmSource, logKept, allocBytesPerSecond, memoryBytes, actionsPerSecond, maxTasks, auditPerMinute, notifyBurst,
            notifyPerSecond, hostCallsPerSecond, hostCallBytes);
    }

    /**
     * The longest stored source of a language in UTF-8 bytes: {@code maxSource} for Lua; for WebAssembly the stored form
     * (the module in base64 and its source text in a small JSON envelope, see {@code WasmSource}), whose parts are checked
     * against {@code maxWasmBytes} and {@code maxWasmSource} when it is read.
     */
    public int maxSource(String language) {
        if (!language.equals("wasm")) return maxSource;
        long bound = (maxWasmBytes + 2L) / 3 * 4 + 6L * maxWasmSource + 1024;
        return (int) Math.min(Integer.MAX_VALUE, bound);
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
        json.addProperty("maxWasmBytes", maxWasmBytes);
        json.addProperty("maxWasmSource", maxWasmSource);
        json.addProperty("logKept", logKept);
        json.addProperty("memoryBytes", memoryBytes);
        json.addProperty("allocBytesPerSecond", allocBytesPerSecond);
        json.addProperty("actionsPerSecond", actionsPerSecond);
        return json;
    }
}
