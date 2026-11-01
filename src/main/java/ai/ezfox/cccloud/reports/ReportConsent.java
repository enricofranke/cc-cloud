package ai.ezfox.cccloud.reports;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The consent rules of crash reports and usage stats (docs/PRIVACY.md "Consent"), as one pure function: nothing is sent
 * unless {@link #state} says {@link State#ACTIVE}.
 * <p>
 * Two things must agree. The server config ({@code [reports]}) says what this world wants, and it travels: it is copied
 * with a world and can be shipped in a modpack's {@code defaultconfigs}. So a setting of {@code on} counts only together
 * with a decision recorded on this installation ({@link ReportStore}, next to the server, outside the world), written
 * when a person answers the prompt or runs {@code /cccloud reports}. A pre-set config asks like a new one.
 */
public final class ReportConsent {
    /**
     * The version of what is sent and what the prompt says. Raise it when a channel starts to send more: every earlier
     * yes then counts as undecided and is asked again. A no stays a no.
     */
    public static final int VERSION = 1;

    /** The two channels with a switch. Feedback has none: every message is confirmed on its own. */
    private static final String PRIVACY_EN = "https://cccloud.dev/en/legal/privacy/";
    private static final String PRIVACY_DE = "https://cccloud.dev/de/legal/privacy/";

    /** The privacy policy in a Minecraft language ({@code de_de}, {@code en_us}). */
    public static String privacyUrl(String language) {
        return language.startsWith("de") ? PRIVACY_DE : PRIVACY_EN;
    }

    public enum Channel {
        CRASH("crash"), STATS("stats");

        public final String id;

        Channel(String id) {
            this.id = id;
        }

        public static @Nullable Channel byId(@Nullable String id) {
            for (var c : values()) if (c.id.equals(id)) return c;
            return null;
        }
    }

    /** The values of {@code crashReports} and {@code usageStats}. */
    public enum Setting {
        ASK, ON, OFF;

        public static @Nullable Setting parse(@Nullable Object value) {
            if (value instanceof String s) {
                for (var setting : values()) if (setting.name().toLowerCase(Locale.ROOT).equals(s)) return setting;
            }
            return null;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** ASK: undecided, nothing is sent. ACTIVE: sending is allowed. DECLINED: nothing is sent and nobody is asked. */
    public enum State {
        ASK, ACTIVE, DECLINED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One answer of the server owner, and the {@link #VERSION} it was given for. */
    public record Decision(boolean yes, int version) {
    }

    private ReportConsent() {
    }

    /**
     * @param configured    the channel's setting in the server config; an invalid value counts as {@link Setting#ASK}
     * @param configVersion {@code consentVersion} in the server config
     * @param decision      the decision recorded on this installation, null if there is none
     * @param current       {@link #VERSION}
     */
    public static State state(@Nullable Setting configured, int configVersion, @Nullable Decision decision, int current) {
        if (configured == Setting.OFF) return State.DECLINED; // withdrawing by editing the file works at once
        if (decision != null && !decision.yes()) return State.DECLINED; // after a no nobody asks again, also not in a new world
        if (configured == Setting.ON && decision != null && decision.version() >= current && configVersion >= current) return State.ACTIVE;
        return State.ASK;
    }

    /** Whether the one-time prompt is due: something is undecided and nobody put it aside for this version. */
    public static boolean prompt(State crash, State stats, int dismissedVersion, int current) {
        return (crash == State.ASK || stats == State.ASK) && dismissedVersion < current;
    }
}
