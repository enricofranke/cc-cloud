package ai.ezfox.cccloud.relay;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.network.graph.NetworkGraph;

/** The three relay tiers with their config values. */
public enum RelayType {
    RELAY(NetworkGraph.NodeType.RELAY),
    ADVANCED(NetworkGraph.NodeType.ADVANCED_RELAY),
    ENDER(NetworkGraph.NodeType.ENDER_RELAY);

    public final NetworkGraph.NodeType nodeType;

    RelayType(NetworkGraph.NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public int nodeRange() {
        return CloudConfig.get(switch (this) {
            case RELAY -> CloudConfig.RANGE_RELAY_NODES;
            case ADVANCED -> CloudConfig.RANGE_ADVANCED_RELAY_NODES;
            case ENDER -> CloudConfig.RANGE_ENDER_RELAY_NODES;
        });
    }

    public int deviceRange() {
        return CloudConfig.get(switch (this) {
            case RELAY -> CloudConfig.RANGE_RELAY_DEVICES;
            case ADVANCED -> CloudConfig.RANGE_ADVANCED_RELAY_DEVICES;
            case ENDER -> CloudConfig.RANGE_ENDER_RELAY_DEVICES;
        });
    }

    public int usage() {
        return CloudConfig.get(switch (this) {
            case RELAY -> CloudConfig.RELAY_FE;
            case ADVANCED -> CloudConfig.ADVANCED_RELAY_FE;
            case ENDER -> CloudConfig.ENDER_RELAY_FE;
        });
    }

    public int buffer() {
        return CloudConfig.get(switch (this) {
            case RELAY -> CloudConfig.RELAY_BUFFER_FE;
            case ADVANCED -> CloudConfig.ADVANCED_RELAY_BUFFER_FE;
            case ENDER -> CloudConfig.ENDER_RELAY_BUFFER_FE;
        });
    }
}
