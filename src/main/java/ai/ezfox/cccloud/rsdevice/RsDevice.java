package ai.ezfox.cccloud.rsdevice;

import java.util.Locale;
import java.util.UUID;

/**
 * What the dashboard knows about one linked Cloud Redstone device: identity, name/group, position and the last state.
 * The block entity is the source of truth while it is loaded; this copy is stored per mainframe ({@link RsDeviceData})
 * so the cards keep unloaded devices (last values) and survive restarts. Pure Java.
 *
 * @param level  switch: output 0–15 · lamp: emitted light 0–15 · sensor: input 0–15 · button: 15 while pressed
 * @param on     lamp: its own switch (the dashboard / in-game state, without redstone)
 * @param preset switch (analog): level used when it is switched on · lamp: brightness 1–15
 * @param pulse  button: pulse length in ticks (2–40)
 * @param follow lamp: a redstone signal lights it too (the brighter of both wins)
 */
public record RsDevice(UUID id, RsKind kind, String name, String group, String dim, int x, int y, int z,
                       boolean analog, int level, boolean on, int preset, int pulse, boolean follow, boolean pin, boolean confirm) {
    public static final int NAME_MAX = 32;
    public static final int PULSE_MIN = 2;
    public static final int PULSE_MAX = 40;
    public static final int PULSE_DEFAULT = 10;

    /** The name shown on the dashboard: the player's name, else e.g. "Lamp 3f2a". */
    public String displayName() {
        return name.isEmpty() ? kind.label + " " + id.toString().substring(0, 4) : name;
    }

    /** Group key: names are matched case-insensitively ("Halle" and "halle" are one card); "" = no group. */
    public String groupKey() {
        return group.strip().toLowerCase(Locale.ROOT);
    }

    /** Is it on (a lit lamp, a switch with output, a pressed button, a sensor that gets a signal)? */
    public boolean active() {
        return level > 0;
    }

    /** Valid name or group text: plain text, at most {@link #NAME_MAX} characters after stripping. */
    public static boolean validText(String text) {
        if (text.length() > NAME_MAX) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || c == ' ' || c == ' ') return false;
        }
        return true;
    }

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(15, level));
    }

    public static int clampPulse(int ticks) {
        return Math.max(PULSE_MIN, Math.min(PULSE_MAX, ticks));
    }
}
