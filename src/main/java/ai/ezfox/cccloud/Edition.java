package ai.ezfox.cccloud;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Which download this jar is. Both are built from the same sources with the same mod id and version:
 * <ul>
 *   <li>{@link #STANDARD} ({@code cccloud-<version>.jar}): for players who drop the mod onto their own server; it carries
 *   the FTB Quests chapters and adds them to the quest book once ({@code installQuests}, default true).</li>
 *   <li>{@link #MODPACK} ({@code cccloud-<version>-modpack.jar}, task {@code modpackJar}): for modpacks, which keep control
 *   of their quest book; no quest files inside, {@code installQuests} defaults to false and the installer does nothing.</li>
 * </ul>
 * The modpack jar contains {@code /cccloud-edition.properties} with {@code edition=modpack}; without it (the standard
 * jar, dev runs) the edition is standard.
 */
public enum Edition {
    STANDARD,
    MODPACK;

    public static final String RESOURCE = "/cccloud-edition.properties";
    private static volatile Edition current;

    public static Edition current() {
        Edition e = current;
        if (e == null) {
            try (InputStream in = Edition.class.getResourceAsStream(RESOURCE)) {
                e = read(in);
            } catch (IOException ex) {
                CloudLog.LOGGER.warn("[cccloud] Could not read {}: {}", RESOURCE, ex.toString());
                e = STANDARD;
            }
            current = e;
        }
        return e;
    }

    /** The edition named by a {@code cccloud-edition.properties} stream (null = no file = standard). */
    public static Edition read(InputStream in) throws IOException {
        if (in == null) return STANDARD;
        var p = new Properties();
        p.load(in);
        return "modpack".equalsIgnoreCase(p.getProperty("edition", "").trim()) ? MODPACK : STANDARD;
    }

    /** Whether this edition adds the FTB Quests chapters by itself (the default of {@code installQuests}). */
    public boolean installsQuests() {
        return this == STANDARD;
    }

    public String label() {
        return this == MODPACK ? "modpack edition" : "standard edition";
    }
}
