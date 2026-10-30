package ai.ezfox.cccloud.config;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/**
 * A config builder that ends every value's comment with its {@link Reload} marker, as the last line: after the
 * description and after NeoForge's own " Default:" / " Range:" lines. Values apply live unless {@link #restart()} was
 * called right before them.
 */
public final class MarkedBuilder extends ModConfigSpec.Builder {
    private Reload next = Reload.LIVE;

    /** The next value is only read when the server starts. */
    public MarkedBuilder restart() {
        next = Reload.RESTART;
        return this;
    }

    // Every scalar value (booleans, strings, ranges, ...) ends up here, after the range lines were added.
    @Override
    public <T> ModConfigSpec.ConfigValue<T> define(List<String> path, Supplier<T> defaultSupplier, Predicate<Object> validator, Class<?> clazz) {
        mark();
        return super.define(path, defaultSupplier, validator, clazz);
    }

    // Lists do not pass through the method above.
    @Override
    public <T> ModConfigSpec.ConfigValue<List<? extends T>> defineList(List<String> path, Supplier<List<? extends T>> defaultSupplier,
                                                                     @Nullable Supplier<T> newElementSupplier, Predicate<Object> elementValidator,
                                                                     ModConfigSpec.@Nullable Range<Integer> sizeRange) {
        mark();
        return super.defineList(path, defaultSupplier, newElementSupplier, elementValidator, sizeRange);
    }

    private void mark() {
        comment(" " + next.marker);
        next = Reload.LIVE;
    }
}
