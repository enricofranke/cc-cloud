package ai.ezfox.cccloud.cable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiberNetworksTest {
    @Test
    void enderLinksMergeNetworks() {
        var parent = new ArrayList<>(List.of(0, 1, 2, 3));
        FiberNetworks.union(parent, 1, 3);
        FiberNetworks.union(parent, 3, 2);
        assertEquals(FiberNetworks.find(parent, 1), FiberNetworks.find(parent, 2));
        assertEquals(1, FiberNetworks.find(parent, 3), "the smallest id names the merged network");
        assertNotEquals(FiberNetworks.find(parent, 0), FiberNetworks.find(parent, 1));
    }
}
