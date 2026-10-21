package ai.ezfox.cccloud.uplink;

/** Server-side length limits for everything a Lua script can send to the dashboard. */
public final class Limits {
    public static final int TITLE = 48;
    public static final int LABEL = 48;
    public static final int STRING_VALUE = 256;
    public static final int ALERT_TEXT = 200;
    public static final int OPTS_BYTES = 1024;
    public static final int UNIT = 16;

    // v0.2
    /** Length of one select/buttons option. */
    public static final int OPTION = 32;
    /** Number of options of a select/buttons widget. */
    public static final int MAX_OPTIONS = 16;
    /** Pages and groups one uplink may declare with cloud.page()/cloud.group(). */
    public static final int MAX_PAGES = 8;
    public static final int MAX_GROUPS = 16;
    /** Chart history (ring buffer) size, per chart. */
    public static final int CHART_POINTS_MIN = 10;
    public static final int CHART_POINTS_MAX = 300;
    public static final int CHART_POINTS_DEFAULT = 60;
    /** Chart sample interval in seconds. */
    public static final double CHART_INTERVAL_MIN = 0.5;
    public static final double CHART_INTERVAL_MAX = 3600;
    /** Layout ordering range (opts.order and the browser layout). */
    public static final int ORDER_MIN = 0;
    public static final int ORDER_MAX = 10_000;
    /** Entries in cloud.setMany(). */
    public static final int SET_MANY = 64;
    /** Item search query length. */
    public static final int QUERY = 64;
    /** Rows of one query_result. */
    public static final int QUERY_ROWS = 50;
    /** Watchlist entries per bridge. */
    public static final int WATCHLIST = 32;
    /** Number range accepted for number widgets without explicit bounds. */
    public static final double NUMBER_DEFAULT_MIN = -2_147_483_648d;
    public static final double NUMBER_DEFAULT_MAX = 2_147_483_647d;

    private Limits() {
    }
}
