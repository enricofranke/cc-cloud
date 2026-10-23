package ai.ezfox.cccloud.redstone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedstoneChannelsTest {
    private static RedstoneChannels.Member tx(String mf, String channel, int level) {
        return new RedstoneChannels.Member(mf, channel, true, level);
    }

    private static RedstoneChannels.Member rx(String mf, String channel) {
        return new RedstoneChannels.Member(mf, channel, false, 0);
    }

    @Test
    void receiversGetTheMaxOfTheirChannel() {
        var state = RedstoneChannels.compute(List.of(tx("a", "door", 4), tx("a", "door", 11), rx("a", "door"), tx("a", "lamp", 2)), Map.of(), 0);
        var door = state.get("a").get("door");
        assertEquals(11, door.level());
        assertEquals(2, door.transmitters());
        assertEquals(1, door.receivers());
        assertEquals(11, RedstoneChannels.levelFor(state, "a", "door"));
        assertEquals(2, RedstoneChannels.levelFor(state, "a", "lamp"));
        assertEquals(0, RedstoneChannels.levelFor(state, "a", "none"));
    }

    @Test
    void networksAreSeparate() {
        var state = RedstoneChannels.compute(List.of(tx("a", "door", 15), rx("b", "door")), Map.of(), 0);
        assertEquals(15, RedstoneChannels.levelFor(state, "a", "door"));
        assertEquals(0, RedstoneChannels.levelFor(state, "b", "door"), "same name on another mainframe's network");
    }

    @Test
    void levelsAreClamped() {
        var state = RedstoneChannels.compute(List.of(tx("a", "x", 99)), Map.of(), 0);
        assertEquals(15, RedstoneChannels.levelFor(state, "a", "x"));
    }

    @Test
    void atMost64Channels() {
        var members = new ArrayList<RedstoneChannels.Member>();
        for (int i = 0; i < 70; i++) members.add(tx("a", String.format("c%02d", i), 15));
        var state = RedstoneChannels.compute(members, Map.of(), 0);
        assertEquals(RedstoneChannels.MAX_CHANNELS, state.get("a").size());
        assertEquals(15, RedstoneChannels.levelFor(state, "a", "c63"));
        assertEquals(0, RedstoneChannels.levelFor(state, "a", "c64"), "channels beyond the limit stay off");
    }

    @Test
    void overridesWinAndExpire() {
        var overrides = new HashMap<String, Map<String, RedstoneChannels.Override>>();
        overrides.put("a", Map.of("door", new RedstoneChannels.Override(3, 1000), "alarm", new RedstoneChannels.Override(15, 0)));
        var state = RedstoneChannels.compute(List.of(tx("a", "door", 12)), overrides, 500);
        assertEquals(3, state.get("a").get("door").level());
        assertEquals(12, state.get("a").get("door").signal());
        assertEquals(15, RedstoneChannels.levelFor(state, "a", "alarm"), "an override alone creates the channel");
        var later = RedstoneChannels.compute(List.of(tx("a", "door", 12)), overrides, 1000);
        assertEquals(12, later.get("a").get("door").level(), "expired");
        assertNull(later.get("a").get("door").override());
    }

    @Test
    void names() {
        assertTrue(RedstoneChannels.validName("base.door-1_A"));
        assertFalse(RedstoneChannels.validName(""));
        assertFalse(RedstoneChannels.validName("with space"));
        assertFalse(RedstoneChannels.validName("x".repeat(33)));
        assertFalse(RedstoneChannels.validName(null));
    }
}
