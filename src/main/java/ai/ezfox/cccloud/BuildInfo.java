package ai.ezfox.cccloud;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/**
 * Which build this jar is: {@code <mod version>-<git short hash>}, with {@code -dirty} when it was built with uncommitted
 * changes. The version alone does not tell test builds apart, so the join check ({@code join/}), {@code /cccloud version}
 * and the startup log use this. The build writes it into {@code /cccloud-build.properties} (standard and modpack jar: the
 * same id); {@link #UNKNOWN} when the file is missing.
 */
public final class BuildInfo {
    public static final String RESOURCE = "/cccloud-build.properties";
    public static final String UNKNOWN = "unknown";
    private static volatile @Nullable String id;

    private BuildInfo() {
    }

    public static String id() {
        String value = id;
        if (value == null) {
            try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
                value = read(in);
            } catch (IOException e) {
                CloudLog.LOGGER.warn("[cccloud] Could not read {}: {}", RESOURCE, e.toString());
                value = UNKNOWN;
            }
            id = value;
        }
        return value;
    }

    /** The build id named by a {@code cccloud-build.properties} stream (null = no file). */
    public static String read(@Nullable InputStream in) throws IOException {
        if (in == null) return UNKNOWN;
        var p = new Properties();
        p.load(in);
        String build = p.getProperty("build", "").trim();
        return build.isEmpty() ? UNKNOWN : build;
    }
}
