package ai.ezfox.cccloud.ingame;

/**
 * Two-step confirmation of the tablet's controls: the first click on a control with {@code confirm} arms it, a second
 * click on the same control within {@link #WINDOW_MS} goes through. A click on anything else disarms. Pure Java, unit tested.
 */
public final class ConfirmArm {
    public static final long WINDOW_MS = 3000;

    private String armedKey = "";
    private long armedUntil;

    public boolean armed(String key, long now) {
        return key.equals(armedKey) && now < armedUntil;
    }

    /** A click on a control: true = send it, false = it is armed now and waits for the second click. */
    public boolean press(String key, boolean needsConfirm, long now) {
        if (!needsConfirm || armed(key, now)) {
            armedKey = "";
            return true;
        }
        armedKey = key;
        armedUntil = now + WINDOW_MS;
        return false;
    }
}
