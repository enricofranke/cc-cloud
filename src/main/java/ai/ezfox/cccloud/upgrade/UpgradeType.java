package ai.ezfox.cccloud.upgrade;

import java.util.Locale;

/** The five upgrade card types (docs/SPEC.md "Mainframe upgrades"). */
public enum UpgradeType {
    CAPACITY, BANDWIDTH, RANGE, EFFICIENCY, MEMORY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
