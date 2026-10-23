package ai.ezfox.cccloud.layout;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * v1 → v2 migration: the shared fixture (src/test/resources/layout/migration_fixture.json, generated with the dashboard's
 * {@code migrateLayout}) must come out identical, the result must be a valid v2 document, and entries of unknown sections
 * survive.
 */
class LayoutMigrationTest {
    static JsonObject fixture() throws Exception {
        try (var in = LayoutMigrationTest.class.getResourceAsStream("/layout/migration_fixture.json")) {
            assertNotNull(in, "fixture missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static List<JsonObject> uplinks(JsonObject c) {
        var list = new ArrayList<JsonObject>();
        c.getAsJsonArray("uplinks").forEach(u -> list.add(u.getAsJsonObject()));
        return list;
    }

    @Test
    void matchesTheDashboardMigration() throws Exception {
        var cases = fixture().getAsJsonArray("cases");
        assertFalse(cases.isEmpty());
        int i = 0;
        for (var e : cases) {
            var c = e.getAsJsonObject();
            var v1 = c.getAsJsonObject("v1");
            assertTrue(LayoutValidator.validate(v1).ok(), "fixture v1 is valid");
            var migrated = LayoutMigration.migrate(uplinks(c), v1);
            assertEquals(c.getAsJsonObject("v2"), migrated, "case " + i++);
            var check = LayoutValidator.validate(migrated);
            assertTrue(check.ok(), String.valueOf(check.detail()));
        }
    }

    @Test
    void keepsEntriesOfUnknownSections() throws Exception {
        var c = fixture().getAsJsonArray("cases").get(0).getAsJsonObject();
        var v1 = c.getAsJsonObject("v1").deepCopy();
        v1.getAsJsonObject("cards").add("gone", JsonParser.parseString("{\"order\":3,\"span\":2,\"title\":\"Old\",\"page\":\"x\"}"));
        v1.getAsJsonObject("widgets").add("gone/w", JsonParser.parseString("{\"order\":3,\"label\":\"L\",\"hidden\":true}"));
        var migrated = LayoutMigration.migrate(uplinks(c), v1);
        assertEquals(JsonParser.parseString("{\"title\":\"Old\",\"page\":\"x\"}"), migrated.getAsJsonObject("cards").get("gone"));
        assertEquals(JsonParser.parseString("{\"label\":\"L\",\"hidden\":true}"), migrated.getAsJsonObject("widgets").get("gone/w"));
        assertTrue(LayoutValidator.validate(migrated).ok());
    }

    @Test
    void noSectionsGivesTheDefaultPage() {
        var v1 = JsonParser.parseString("{\"v\":1,\"pages\":[],\"cards\":{},\"widgets\":{}}").getAsJsonObject();
        var migrated = LayoutMigration.migrate(List.of(), v1);
        assertEquals(JsonParser.parseString("{\"v\":2,\"pages\":[{\"id\":\"main\",\"title\":\"Overview\",\"icon\":\"home\"}],\"cards\":{},\"widgets\":{}}"),
            migrated);
    }
}
