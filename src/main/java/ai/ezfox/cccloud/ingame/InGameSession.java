package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.HubSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * A dashboard client inside the game (an open Cloud Tablet): registered with the hub like a browser, so it gets the
 * same snapshot and deltas, filtered by the player's role (restricted sections and coordinates need operator). The hub calls
 * {@link #send} from any thread; the server thread drains the queue and forwards it to the player.
 */
public final class InGameSession implements HubSession {
    /** Messages queued for the player at most; a client that falls behind gets a fresh snapshot instead. */
    static final int MAX_QUEUED = 512;

    private final UUID mainframeId;
    private final AuthStore.Device device;
    private volatile Role role;
    private volatile java.util.Set<ai.ezfox.cccloud.access.Perm> perms;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private volatile boolean overflow;
    private volatile boolean closed;

    public InGameSession(UUID mainframeId, AuthStore.Device device, Role role, java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        this.mainframeId = mainframeId;
        this.device = device;
        this.role = role;
        this.perms = java.util.Set.copyOf(perms);
    }

    @Override
    public UUID mainframeId() {
        return mainframeId;
    }

    @Override
    public AuthStore.@Nullable Device device() {
        return device;
    }

    public AuthStore.Device requireDevice() {
        return device;
    }

    @Override
    public Role role() {
        return role;
    }

    @Override
    public java.util.Set<ai.ezfox.cccloud.access.Perm> perms() {
        return perms;
    }

    @Override
    public void setAccess(Role role, java.util.Set<ai.ezfox.cccloud.access.Perm> perms) {
        this.role = role;
        this.perms = java.util.Set.copyOf(perms);
    }

    @Override
    public void send(List<String> messages) {
        if (closed) return;
        for (var message : messages) {
            if (size.incrementAndGet() > MAX_QUEUED) {
                overflow = true;
                size.decrementAndGet();
                return;
            }
            queue.add(message);
        }
    }

    @Override
    public void close(int code, String reason) {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Takes the queued messages (server thread). Returns null if the queue overflowed (resync needed). */
    public @Nullable List<String> drainMessages() {
        var out = new ArrayList<String>();
        String m;
        while ((m = queue.poll()) != null) {
            size.decrementAndGet();
            out.add(m);
        }
        if (overflow) {
            overflow = false;
            return null;
        }
        return out;
    }
}
