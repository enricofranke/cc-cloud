package ai.ezfox.cccloud;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildInfoTest {
    private static String read(String text) throws IOException {
        return BuildInfo.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsTheBuildId() throws IOException {
        assertEquals("1.0.0-beta.2+dev5-0de7b2c3-dirty", read("# comment\nbuild=1.0.0-beta.2+dev5-0de7b2c3-dirty\n"));
    }

    @Test
    void withoutTheFileOrAValueItIsUnknown() throws IOException {
        assertEquals(BuildInfo.UNKNOWN, BuildInfo.read(null));
        assertEquals(BuildInfo.UNKNOWN, read("build=\n"));
        assertEquals(BuildInfo.UNKNOWN, read("other=1\n"));
    }

    @Test
    void theTestRunHasTheBuildResource() {
        // generateModMetadata writes it into the main resources of every run, also dev and test runs
        assertTrue(BuildInfo.id().matches("[0-9A-Za-z.+_-]+-[0-9a-f]{7,40}(-dirty)?|[0-9A-Za-z.+_-]+-nogit(-dirty)?"), BuildInfo.id());
    }
}
