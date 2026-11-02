package ai.ezfox.cccloud.cable;

import java.util.Set;

/**
 * What CC: Tweaked sees of one of our blocks (mainframe, relay, uplink, bridge): its peripheral and its membership in
 * CC wired networks (docs/SPEC.md "Cables", part A). Created by {@link ai.ezfox.cccloud.compat.Cct}; without CC:
 * Tweaked it is {@link #NONE} – no CC cable buses, so such blocks are linked by radio, fiber and Ender Fiber Links only.
 */
public interface CcWired {
    CcWired NONE = new CcWired() {
        @Override
        public void onLoad() {
        }

        @Override
        public void onRemoved() {
        }

        @Override
        public void refresh() {
        }

        @Override
        public Set<String> visibleNodes() {
            return Set.of();
        }

        @Override
        public Set<String> cableBuses() {
            return Set.of();
        }

        @Override
        public boolean attached() {
            return false;
        }
    };

    /** From the block entity's {@code onLoad}: connect to neighbouring wired elements on the next tick. */
    void onLoad();

    /** From the block entity's {@code setRemoved}: leave the wired network. */
    void onRemoved();

    /** Re-checks the neighbours and the exposed peripheral (e.g. after the element's id changed). */
    void refresh();

    /** Ids of the cloud nodes (mainframe, relays) on the same CC wired network, without this element. */
    Set<String> visibleNodes();

    /** The ids of the CC wired networks this element is on, as the network graph sees them ({@code cc:<node id>}). */
    Set<String> cableBuses();

    boolean attached();
}
