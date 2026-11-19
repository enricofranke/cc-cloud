package ai.ezfox.cccloud.code.wasm;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;

/**
 * The host functions a module reaches through {@code cccloud.call}, by their full name ({@code cloud.getCard}): for
 * Cloud Scripts the {@code cloud} descriptor ({@link CloudHostApi}), for Cloud Computer programs their modules
 * ({@code term.write}, {@code fs.open}, …).
 */
public interface WasmHostApi {
    /** One function: arguments as plain Java values (already parsed from JSON), returns like {@link ai.ezfox.cccloud.code.api.CloudApi}. */
    interface Function {
        /**
         * Runs the call: nothing ({@code null}), one value, several ({@code Object[]}) or a future the engine waits for.
         * A wrong call throws {@link ai.ezfox.cccloud.code.api.ApiError}.
         */
        @Nullable Object call(java.util.List<@Nullable Object> args);

        /** Whether a lone {@code null} result is a value ({@code [null]}) rather than nothing ({@code []}). */
        default boolean returnsSomething() {
            return true;
        }
    }

    /** The function with this name, or null. */
    @Nullable Function find(String name);

    /** How strings travel in memory: UTF-8 for scripts, ISO-8859-1 (one byte per char) for computer programs. */
    default Charset charset() {
        return java.nio.charset.StandardCharsets.UTF_8;
    }

    /** A line from the module's {@code trace} (AssemblyScript's debug output). */
    void trace(String text);
}
