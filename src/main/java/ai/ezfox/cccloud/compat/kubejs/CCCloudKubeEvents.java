package ai.ezfox.cccloud.compat.kubejs;

import ai.ezfox.cccloud.api.CloudEvents;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * {@code CCCloudEvents.action / deviceLinked / alert} for server scripts. The core posts {@link CloudEvents} on the
 * server thread; these handlers forward them to the scripts when any script listens.
 */
public final class CCCloudKubeEvents {
    public static final EventGroup GROUP = EventGroup.of("CCCloudEvents");
    public static final EventHandler ACTION = GROUP.server("action", () -> ActionKubeEvent.class);
    public static final EventHandler DEVICE_LINKED = GROUP.server("deviceLinked", () -> DeviceLinkedKubeEvent.class);
    public static final EventHandler ALERT = GROUP.server("alert", () -> AlertKubeEvent.class);
    private static boolean hooked;

    private CCCloudKubeEvents() {
    }

    static synchronized void hook() {
        if (hooked) return;
        hooked = true;
        CloudEvents.onAction(e -> {
            if (ACTION.hasListeners()) ACTION.post(new ActionKubeEvent(e));
        });
        CloudEvents.onDeviceLinked(e -> {
            if (DEVICE_LINKED.hasListeners()) DEVICE_LINKED.post(new DeviceLinkedKubeEvent(e));
        });
        CloudEvents.onAlert(e -> {
            if (ALERT.hasListeners()) ALERT.post(new AlertKubeEvent(e));
        });
    }
}
