package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A ready-made dashboard card for one machine (docs/SPEC.md v0.5 A): an uplink attached directly to a supported block
 * publishes it with zero Lua. A card is a plain set of widgets ({@link #widgets()}), refreshed once per second, plus an
 * optional action handler for its interactive widgets.
 * <p>
 * Cards are created by a {@link DeviceCards.Provider} and only live on the server thread. Everything a card does with
 * another mod's classes happens inside its own {@code compat/<modid>} package; {@link DeviceCards} catches any
 * {@link LinkageError} or exception, drops the card and disables its provider with one log line.
 */
public abstract class DeviceCard {
    /** Template id sent in the uplink snapshot ({@code "template"}); {@code [a-z0-9_]}, e.g. "energy", "powah_reactor". */
    public abstract String kind();

    /** Card title (plain English, like the other templates; at most 48 characters). */
    public abstract String title();

    /** The widgets of the card. Called once when the card is shown; validated like Lua widgets ({@link CardWidgets}). */
    public abstract List<Widget> widgets();

    /** False once the block the card was made for is gone or replaced; the uplink then looks again. */
    public abstract boolean isValid();

    /**
     * Version of the widget set. A card whose widgets depend on the machine's configuration (e.g. ME Requester's list
     * of requests) returns a new value when it changes; the uplink then rebuilds the section from {@link #widgets()}.
     */
    public int revision() {
        return 0;
    }

    /** Reads the machine and writes the widget values. Called once per second on the server thread. */
    public abstract void refresh(CardOutput out);

    /**
     * Runs a dashboard action on an interactive widget of this card. The value has already been validated against the
     * widget definition (type, range, options, PIN), and the device has canControl.
     *
     * @return null on success, else an error code for {@code action_result} (e.g. "unsupported", "unavailable")
     */
    public @Nullable String action(String widgetId, JsonElement value, String user) {
        return "not_interactive";
    }

    /** Where a card writes its values and alerts. */
    public interface CardOutput {
        void value(String widgetId, JsonElement value);

        /** Queues an alert on the card ({@code info|warn|crit}); cards send one per transition, not every second. */
        void alert(String text, String level);

        default void number(String widgetId, double value) {
            value(widgetId, ai.ezfox.cccloud.uplink.Json.number(Double.isFinite(value) ? value : 0));
        }

        default void text(String widgetId, String text) {
            String clipped = text.length() > ai.ezfox.cccloud.uplink.Limits.STRING_VALUE
                ? text.substring(0, ai.ezfox.cccloud.uplink.Limits.STRING_VALUE) : text;
            value(widgetId, new com.google.gson.JsonPrimitive(clipped));
        }

        default void bool(String widgetId, boolean value) {
            value(widgetId, new com.google.gson.JsonPrimitive(value));
        }
    }
}
