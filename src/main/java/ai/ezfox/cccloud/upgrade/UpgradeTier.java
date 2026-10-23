package ai.ezfox.cccloud.upgrade;

import java.util.Locale;

/** Card tiers in ATM progression order; a higher tier of the same type replaces a lower one. */
public enum UpgradeTier {
    ALLTHEMODIUM, VIBRANIUM, UNOBTAINIUM;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
