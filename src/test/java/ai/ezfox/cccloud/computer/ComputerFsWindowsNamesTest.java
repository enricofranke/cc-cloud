package ai.ezfox.cccloud.computer;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.api.ApiError;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Names Windows treats as devices are no file names on a computer's drive (worlds on Windows hosts stay intact). */
class ComputerFsWindowsNamesTest {
    @Test
    void windowsDeviceNamesAreRefused() {
        for (String bad : List.of("con", "CON", "prn", "aux", "nul", "NUL.txt", "nul.tar.gz", "com1", "COM9.log", "com0", "lpt1", "LPT9", "lpt0",
            "con .txt", "a/nul", "conin$", "CONOUT$", "com¹", "lpt³.x")) {
            assertThrows(ApiError.class, () -> ComputerFs.normalize(bad), bad);
            assertTrue(ComputerFs.windowsDevice(bad.substring(bad.lastIndexOf('/') + 1)), bad);
        }
        for (String good : List.of("console", "nul2", "com10", "lpt", "conx.txt", "my.con", "aux_", "nulls", "com", "a/console/b")) {
            assertEquals(good, ComputerFs.normalize(good), good);
        }
        assertThrows(ApiError.class, () -> ComputerFs.normalize("name."), "trailing dot");
        assertThrows(ApiError.class, () -> ComputerFs.normalize("name "), "trailing space");
    }
}
