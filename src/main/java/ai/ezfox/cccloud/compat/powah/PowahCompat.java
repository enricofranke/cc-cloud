package ai.ezfox.cccloud.compat.powah;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.DeviceCards;

/** Powah integration setup. Only class-loaded when {@code powah} is present. */
public final class PowahCompat {
    private PowahCompat() {
    }

    public static void init() {
        DeviceCards.register("powah", DeviceCards.SPECIFIC, PowahCards::detect);
        Integrations.test("powah_energy_cell", () -> PowahGameTests::energyCell);
        Integrations.test("powah_reactor", () -> PowahGameTests::reactor);
    }
}
