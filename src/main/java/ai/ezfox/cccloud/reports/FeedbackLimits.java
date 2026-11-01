package ai.ezfox.cccloud.reports;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * How much feedback one server may send: every message is confirmed by a person, but the mod's name on a public server
 * must not be a way to flood the service. Memory only; a restart starts over and the service has its own limit.
 */
public final class FeedbackLimits {
    public static final int PER_SENDER = 3;
    public static final long SENDER_WINDOW_MS = 10 * 60_000L;
    public static final int PER_SERVER = 10;
    public static final long SERVER_WINDOW_MS = 60 * 60_000L;
    private static final int MAX_SENDERS = 1024;

    private final Map<String, ArrayDeque<Long>> senders = new HashMap<>();
    private final ArrayDeque<Long> server = new ArrayDeque<>();

    /** Counts one message of this sender at {@code now}, or refuses it (the refused one does not count). */
    public synchronized boolean allow(String sender, long now) {
        expire(server, now - SERVER_WINDOW_MS);
        if (server.size() >= PER_SERVER) return false;
        senders.values().removeIf(times -> {
            expire(times, now - SENDER_WINDOW_MS);
            return times.isEmpty();
        });
        var times = senders.get(sender);
        if (times != null && times.size() >= PER_SENDER) return false;
        if (times == null && senders.size() >= MAX_SENDERS) return false;
        senders.computeIfAbsent(sender, k -> new ArrayDeque<>()).add(now);
        server.add(now);
        return true;
    }

    private static void expire(ArrayDeque<Long> times, long before) {
        while (!times.isEmpty() && times.peekFirst() <= before) times.removeFirst();
    }
}
