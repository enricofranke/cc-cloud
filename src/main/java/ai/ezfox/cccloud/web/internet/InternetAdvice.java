package ai.ezfox.cccloud.web.internet;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The text of {@code /cccloud internet}: what changed, the link to open, the port to forward and how to test it. Pure
 * Java (lines of translation keys and arguments, turned into chat components by the command), unit tested.
 */
public final class InternetAdvice {
    /** How a line looks in chat. */
    public enum Style { TITLE, OK, WARN, ERROR, INFO, STEP, LINK, COMMAND }

    /** One chat line: a translation key ({@code cccloud.internet.*}) and its arguments. */
    public record Line(Style style, String key, List<Object> args) {
        public Line(Style style, String key, Object... args) {
            this(style, key, List.of(args));
        }
    }

    /**
     * What the command knows.
     *
     * @param configured    the mode now in the config
     * @param running       the mode the running dashboard uses (differs until the restart)
     * @param domain        {@code internetDomain}, or "" for the public IP
     * @param port          the dashboard's port
     * @param lanIp         this machine's LAN address (for the port forwarding), or null
     * @param publicIp      the looked-up public IP, or null (not found / lookups off)
     * @param withoutPin    owners of mainframes without a PIN, with their count ("Steve (2)"): those mainframes can only
     *                      be watched from the internet in plain-HTTP mode
     * @param idleHours     {@code publicTokenIdleHours}
     * @param legacyOpen    {@code internetAccess = off} but {@code webAllowInternet = true} (an older setup stays open)
     */
    public record Facts(InternetMode configured, InternetMode running, String domain, int port, @Nullable String lanIp, @Nullable String publicIp,
                        List<String> withoutPin, long idleHours, boolean legacyOpen) {
        public Facts {
            withoutPin = List.copyOf(withoutPin);
        }
    }

    /** At most this many mainframes are named in one line. */
    static final int MAX_NAMES = 5;

    private InternetAdvice() {
    }

    /** The internet link for plain HTTP: the domain, else the public IP, else a placeholder the player fills in. */
    public static String httpUrl(Facts f) {
        String host = !f.domain().isBlank() ? f.domain() : f.publicIp() != null ? f.publicIp() : "<public-ip>";
        return InternetSettings.url("http", host, f.port());
    }

    /** After {@code /cccloud internet <mode>} was written to the config. */
    public static List<Line> switched(Facts f) {
        var lines = new ArrayList<Line>();
        switch (f.configured()) {
            case OFF -> {
                lines.add(new Line(Style.TITLE, "cccloud.internet.off.title"));
                if (f.running() != InternetMode.OFF) lines.add(new Line(Style.INFO, "cccloud.internet.restart"));
                if (f.legacyOpen()) lines.add(new Line(Style.WARN, "cccloud.internet.off.legacy"));
            }
            case HTTP -> {
                lines.add(new Line(Style.WARN, "cccloud.internet.http.title"));
                if (f.running() != InternetMode.HTTP) lines.add(new Line(Style.INFO, "cccloud.internet.restart"));
                lines.addAll(httpSteps(f));
                lines.add(new Line(Style.COMMAND, "cccloud.internet.back_off", "/cccloud internet off"));
            }
        }
        return lines;
    }

    /** The three steps of plain-HTTP mode: link, port forwarding, test from outside. */
    static List<Line> httpSteps(Facts f) {
        var lines = new ArrayList<Line>();
        lines.add(new Line(Style.LINK, "cccloud.internet.step.open", httpUrl(f)));
        if (f.domain().isBlank()) {
            lines.add(f.publicIp() != null ? new Line(Style.INFO, "cccloud.internet.public_ip", f.publicIp(), PublicIp.SERVICES.getFirst())
                : new Line(Style.WARN, "cccloud.internet.public_ip_unknown"));
        }
        String port = String.valueOf(f.port());
        lines.add(new Line(Style.STEP, "cccloud.internet.step.forward", port, f.lanIp() != null ? f.lanIp() : "<this-pc>"));
        lines.add(new Line(Style.STEP, "cccloud.internet.step.test"));
        lines.add(new Line(Style.WARN, "cccloud.internet.http.warning", String.valueOf(f.idleHours())));
        if (!f.withoutPin().isEmpty()) {
            lines.add(new Line(Style.INFO, "cccloud.internet.http.without_pin", names(f.withoutPin())));
            lines.add(new Line(Style.COMMAND, "cccloud.internet.http.set_pin", "/cccloud pin set "));
        }
        return lines;
    }

    /** {@code /cccloud internet} / {@code status}. */
    public static List<Line> status(Facts f, String runningUrl) {
        var lines = new ArrayList<Line>();
        lines.add(new Line(Style.TITLE, "cccloud.internet.status.title", f.running().id()));
        if (f.configured() != f.running()) lines.add(new Line(Style.INFO, "cccloud.internet.status.pending", f.configured().id()));
        switch (f.running()) {
            case OFF -> lines.add(new Line(f.legacyOpen() ? Style.WARN : Style.OK, f.legacyOpen() ? "cccloud.internet.status.legacy" : "cccloud.internet.status.lan"));
            case HTTP -> {
                lines.add(new Line(Style.LINK, "cccloud.internet.status.link", runningUrl.isBlank() ? httpUrl(f) : runningUrl));
                lines.add(new Line(Style.WARN, "cccloud.internet.http.warning", String.valueOf(f.idleHours())));
                // only with internetRequirePin: the PIN-less mainframes (view-only from the internet), else no PIN topic
                if (!f.withoutPin().isEmpty()) lines.add(new Line(Style.INFO, "cccloud.internet.http.without_pin", names(f.withoutPin())));
            }
        }
        lines.add(new Line(Style.COMMAND, "cccloud.internet.status.modes", "/cccloud internet http", "/cccloud internet off"));
        return lines;
    }

    /** Up to {@link #MAX_NAMES} names, then "+N". */
    static String names(List<String> names) {
        if (names.size() <= MAX_NAMES) return String.join(", ", names);
        return String.join(", ", names.subList(0, MAX_NAMES)) + " +" + (names.size() - MAX_NAMES);
    }
}
