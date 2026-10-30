package ai.ezfox.cccloud.sensor;

import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side: the Advanced Cloud Sensors loaded on this client, with the zone their server sent (block update tag),
 * so the zone can be drawn while a sensor or a Link Card is held or a sensor's screen is open. Only client block
 * entities are added; this class has no client-only imports.
 */
public final class SensorZones {
    public static final Set<RsDeviceBlockEntity> CLIENT = ConcurrentHashMap.newKeySet();

    private SensorZones() {
    }
}
