package ai.ezfox.cccloud.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which mainframe a command means when the player does not look at one. */
class TargetPickerTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void picks() {
        assertNull(TargetPicker.pick(List.of(), null), "none");
        assertEquals(a, TargetPicker.pick(List.of(new TargetPicker.Candidate(a, 500)), null), "the only one, however far");
        assertEquals(a, TargetPicker.pick(List.of(new TargetPicker.Candidate(a, -1)), null), "the only one, other dimension");
        assertEquals(b, TargetPicker.pick(List.of(new TargetPicker.Candidate(a, 100), new TargetPicker.Candidate(b, 20)), null), "the only one within 32 blocks");
        assertNull(TargetPicker.pick(List.of(new TargetPicker.Candidate(a, 10), new TargetPicker.Candidate(b, 20)), null), "two nearby → list");
        assertNull(TargetPicker.pick(List.of(new TargetPicker.Candidate(a, 100), new TargetPicker.Candidate(b, 200)), null), "two far → list");
        assertEquals(a, TargetPicker.pick(List.of(new TargetPicker.Candidate(a, 10), new TargetPicker.Candidate(b, 20)), a), "selected wins");
        assertNull(TargetPicker.pick(List.of(new TargetPicker.Candidate(a, 10), new TargetPicker.Candidate(b, 20)), c), "a stale selection is ignored");
        var sorted = TargetPicker.sorted(List.of(new TargetPicker.Candidate(a, -1), new TargetPicker.Candidate(b, 50), new TargetPicker.Candidate(c, 5)));
        assertEquals(List.of(c, b, a), sorted.stream().map(TargetPicker.Candidate::id).toList(), "nearest first, other dimensions last");
    }
}
