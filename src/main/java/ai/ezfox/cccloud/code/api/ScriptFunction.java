package ai.ezfox.cccloud.code.api;

/**
 * A function of the script (a handler passed to {@code cloud.on}, {@code cloud.every}, …), opaque to everything but
 * the engine that made it. The runtime keeps it and hands it back to that engine to call.
 */
public interface ScriptFunction {
}
