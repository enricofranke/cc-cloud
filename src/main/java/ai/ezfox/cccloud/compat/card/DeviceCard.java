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

    /**
     * Answers an item search on an {@code item_search} widget of this card (v0.8 storage cards). Server thread; the web
     * layer has already checked the device's rights and rate limit.
     *
     * @return the rows, or null if the card has no searchable storage (the uplink answers {@code unknown_widget})
     */
    public ai.ezfox.cccloud.web.@Nullable QueryResult query(String widgetId, String query) {
        return null;
    }

    /**
     * Settings the owner chose in the dashboard's card settings (wave 4, the Peripheral card), saved with the uplink and
     * given back through {@link #applySettings} when the card is shown again. Null if the card has none.
     */
    public com.google.gson.@Nullable JsonObject settings() {
        return null;
    }

    /**
     * What the dashboard's card settings offer (uplink snapshot key {@code cardSettings}), or null if the card has no
     * settings. Any thread: built from state that is fixed once the card exists.
     */
    public com.google.gson.@Nullable JsonObject settingsInfo() {
        return null;
    }

    /**
     * Applies settings: strictly validated when they come from the dashboard ({@code saved} false: an error code such
     * as {@code invalid_settings} or {@code unknown_method}), leniently when they were saved with the uplink (whatever
     * no longer fits is dropped). The caller rebuilds the card's widgets afterwards.
     *
     * @return null on success, else the error code
     */
    public @Nullable String applySettings(com.google.gson.JsonObject settings, boolean saved) {
        return "unsupported";
    }

    /**
     * Restricted details of the machine, read on demand ({@code card_detail}, needs {@code view.positions}) and never
     * part of a card's values: e.g. a Factory Manager's program, which tells the base's layout. Server thread.
     *
     * @return the details, or null if the card has none
     */
    public com.google.gson.@Nullable JsonObject detail() {
        return null;
    }

    /**
     * Checks or writes the machine's program ({@code card_write}, op {@code check|save|undo}). Server thread; the web
     * layer has checked rights, PIN and claims.
     */
    public ai.ezfox.cccloud.web.GameBridge.StorageResult write(String op, @Nullable String text, boolean force) {
        return ai.ezfox.cccloud.web.GameBridge.StorageResult.fail("unsupported");
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
