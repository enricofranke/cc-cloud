package ai.ezfox.cccloud.computer.block;

import org.jspecify.annotations.Nullable;

/**
 * The hold-down shortcuts of the terminal screen: Ctrl+T terminates the running program, Ctrl+R reboots, Ctrl+S shuts
 * down, each only after the keys were held for {@link #HOLD_MS} ms (a short tap still reaches the program as a key).
 * Fires once per hold. Pure Java (GLFW key codes), unit tested.
 */
public final class HoldShortcut {
    public static final long HOLD_MS = 1000;
    public static final int KEY_T = 84;
    public static final int KEY_R = 82;
    public static final int KEY_S = 83;

    private int key = -1;
    private long since;
    private boolean fired;

    /** The control a key stands for while Ctrl is down, or null. */
    public static @Nullable String control(int key) {
        return switch (key) {
            case KEY_T -> "terminate";
            case KEY_R -> "reboot";
            case KEY_S -> "shutdown";
            default -> null;
        };
    }

    /** A key went down. Starts a hold when it is a shortcut with Ctrl (a repeat of the same key keeps the hold). */
    public void press(int key, boolean ctrl, long nowMs) {
        if (!ctrl || control(key) == null) {
            reset();
            return;
        }
        if (this.key == key) return;
        this.key = key;
        since = nowMs;
        fired = false;
    }

    /** A key went up (or Ctrl was let go): the hold ends. */
    public void release(int key) {
        if (key == this.key || key == 341 || key == 345) reset();
    }

    public void reset() {
        key = -1;
        fired = false;
    }

    /** Called every tick: the control to send now (once per hold), or null. */
    public @Nullable String poll(long nowMs) {
        if (key < 0 || fired || nowMs - since < HOLD_MS) return null;
        fired = true;
        return control(key);
    }

    /** How far the current hold is (0..1) for the progress bar, or -1 without one. */
    public float progress(long nowMs) {
        if (key < 0 || fired) return -1;
        return Math.min(1f, (nowMs - since) / (float) HOLD_MS);
    }

    public @Nullable String holding() {
        return key < 0 || fired ? null : control(key);
    }
}
