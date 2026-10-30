package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.CCCloud;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Small per-player screen settings of this client ({@code config/cccloud-screens.properties}), e.g. whether the
 * "Advanced" part of the uplink screen is open. Keys are {@code <name>.<player uuid>}; read once, written on change.
 */
public final class ScreenPrefs {
    private static final String FILE = "cccloud-screens.properties";
    private static Properties props;

    private ScreenPrefs() {
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static synchronized Properties props() {
        if (props == null) {
            props = new Properties();
            var path = path();
            if (Files.isRegularFile(path)) {
                try (Reader in = Files.newBufferedReader(path)) {
                    props.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    CCCloud.LOGGER.warn("[cccloud] Could not read {}", path, e);
                }
            }
        }
        return props;
    }

    private static String key(String name) {
        var player = Minecraft.getInstance().player;
        UUID id = player != null ? player.getUUID() : new UUID(0, 0);
        return name + "." + id;
    }

    public static boolean flag(String name, boolean fallback) {
        String value = props().getProperty(key(name));
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public static synchronized void setFlag(String name, boolean value) {
        var p = props();
        String k = key(name);
        if (String.valueOf(value).equals(p.getProperty(k))) return;
        p.setProperty(k, String.valueOf(value));
        try (Writer out = Files.newBufferedWriter(path())) {
            p.store(out, "CC: Cloud screen settings (per player)");
        } catch (IOException e) {
            CCCloud.LOGGER.warn("[cccloud] Could not write {}", path(), e);
        }
    }
}
