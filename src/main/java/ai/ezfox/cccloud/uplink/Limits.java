package ai.ezfox.cccloud.uplink;

/** Server-side length limits for everything a Lua script can send to the dashboard. */
public final class Limits {
    public static final int TITLE = 48;
    public static final int LABEL = 48;
    public static final int STRING_VALUE = 256;
    public static final int ALERT_TEXT = 200;
    public static final int OPTS_BYTES = 1024;
    public static final int UNIT = 16;

    private Limits() {
    }
}
