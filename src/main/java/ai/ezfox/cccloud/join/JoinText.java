package ai.ezfox.cccloud.join;

import ai.ezfox.cccloud.CloudLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The join check's texts. They go to the very clients that may not know them: a client from before this feature has
 * none of these keys in its language file and would show the bare key. So every text carries a fallback, picked here on
 * the server from the mod's own language files by the language the client reports; a client with the keys uses its own.
 */
final class JoinText {
    private static final String PREFIX = "cccloud.join.";
    private static final String DEFAULT = "en_us";
    private static final Map<String, Map<String, String>> FILES = new ConcurrentHashMap<>();

    private JoinText() {
    }

    /** {@code key} with the fallback for this player's language (the console reads English). */
    static MutableComponent text(@Nullable ServerPlayer player, String key, Object... args) {
        String language = player == null ? DEFAULT : player.clientInformation().language();
        return Component.translatableWithFallback(key, fallback(language, key), args);
    }

    /** The text in the player's language, English if the mod has no such language (de_at reads de_de). */
    static @Nullable String fallback(String language, String key) {
        String wanted = language.toLowerCase(Locale.ROOT);
        String text = texts(wanted).get(key);
        if (text == null) text = texts(wanted.startsWith("de_") ? "de_de" : DEFAULT).get(key);
        return text != null ? text : texts(DEFAULT).get(key);
    }

    private static Map<String, String> texts(String language) {
        if (!language.matches("[a-z0-9_]{1,16}")) return Map.of();
        return FILES.computeIfAbsent(language, JoinText::load);
    }

    private static Map<String, String> load(String language) {
        try (InputStream in = JoinText.class.getResourceAsStream("/assets/cccloud/lang/" + language + ".json")) {
            return in == null ? Map.of() : read(in);
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.warn("[cccloud] Could not read the {} texts: {}", language, e.toString());
            return Map.of();
        }
    }

    /** The join check's entries of a language file. */
    static Map<String, String> read(InputStream in) throws IOException {
        var texts = new HashMap<String, String>();
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var e : json.entrySet()) if (e.getKey().startsWith(PREFIX)) texts.put(e.getKey(), e.getValue().getAsString());
        }
        return Map.copyOf(texts);
    }
}
