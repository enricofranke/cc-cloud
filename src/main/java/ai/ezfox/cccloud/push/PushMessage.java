package ai.ezfox.cccloud.push;

import java.util.UUID;

/** One notification: what happened on which mainframe. */
public record PushMessage(UUID mainframeId, String mainframe, PushTarget.Event event, String level, String title, String text, long time) {
    public static final int MAX_TEXT = 1000;

    public PushMessage {
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT - 1) + "…";
        if (title.length() > 120) title = title.substring(0, 119) + "…";
    }
}
