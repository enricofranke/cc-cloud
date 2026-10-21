package ai.ezfox.cccloud.uplink;

import java.util.Set;

/** The fixed icon set bundled with the dashboard (opts.icon, cloud.page icons, layout cards). See docs/SPEC.md. */
public final class Icons {
    public static final Set<String> ALL = Set.of(
        "home", "bolt", "flame", "drop", "box", "fan", "lock", "warning",
        "gear", "chart", "star", "leaf", "heart", "cpu", "shield", "pick"
    );

    private Icons() {
    }

    public static boolean isValid(Object icon) {
        return icon instanceof String s && ALL.contains(s);
    }
}
