package ai.ezfox.cccloud.block.entity;

import ai.ezfox.cccloud.block.CloudStatus;
import net.minecraft.util.Util;

/**
 * Client-only animation state kept on a block entity: integrates a rotation angle with a speed that eases towards a
 * target depending on the status (online = full speed, connecting = slow, offline = stop).
 */
public final class SpinAnimation {
    private float angle;
    private float speed;
    private long last;

    /** @param fullSpeed degrees per second when online. Returns the angle in degrees. */
    public float advance(CloudStatus status, float fullSpeed) {
        return advance(status, fullSpeed, 0.3f);
    }

    /**
     * @param fullSpeed         degrees per second when online
     * @param connectingFactor  speed factor while connecting (relative to online)
     */
    public float advance(CloudStatus status, float fullSpeed, float connectingFactor) {
        long now = Util.getMillis();
        float dt = last == 0 ? 0 : Math.min(0.25f, (now - last) / 1000f);
        last = now;
        float target = switch (status) {
            case ONLINE -> fullSpeed;
            case CONNECTING -> fullSpeed * connectingFactor;
            case OFFLINE -> 0;
        };
        speed += (target - speed) * Math.min(1f, dt * 1.5f);
        angle = (angle + speed * dt) % 360f;
        return angle;
    }

    /** Fan: ~1 revolution per second when online. */
    public float advanceFan(CloudStatus status) {
        return advance(status, 360f);
    }
}
