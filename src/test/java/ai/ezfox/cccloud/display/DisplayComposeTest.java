package ai.ezfox.cccloud.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Custom cards and pages as one synthetic section: what belongs on them, order, restricted sections, views, clicks. */
class DisplayComposeTest {
    private static final String REACTOR = "11111111-1111-1111-1111-111111111111";
    private static final String SERVER = "22222222-2222-2222-2222-222222222222";

    private static JsonObject json(String text) {
        return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject();
    }

    private static Map<String, JsonObject> sections() {
        var map = new LinkedHashMap<String, JsonObject>();
        map.put(REACTOR, json("{'uplinkId':'" + REACTOR + "','title':'Reactor','online':true,'widgets':["
            + "{'id':'temp','type':'gauge','label':'Temp','opts':{'unit':'C','max':100},'value':40},"
            + "{'id':'hist','type':'chart','label':'Heat','history':[1,2,3]},"
            + "{'id':'run','type':'toggle','label':'Run','value':true},"
            + "{'id':'stop','type':'button','label':'Stop','opts':{'group':'ctl'}}"
            + "],'groups':[{'id':'ctl','title':'Controls','page':'ops'}],'blockedWidgets':{'run':{'kind':'claim'}}}"));
        map.put(SERVER, json("{'uplinkId':'" + SERVER + "','title':'Server','online':false,'restricted':true,'widgets':["
            + "{'id':'tps','type':'label','label':'TPS','value':'20'}]}"));
        return map;
    }

    private static final JsonObject LAYOUT = json("{'v':2,'pages':[{'id':'main','title':'Base'},{'id':'ops','title':'Operations'}],"
        + "'cards':{'c:wall':{'title':'Wall','page':'main','x':0,'y':5,'w':6,'h':4},'c:empty':{'page':'ops'},'" + SERVER + "':{'page':'ops','y':9}},"
        + "'widgets':{'" + REACTOR + "/temp':{'card':'c:wall','x':0,'y':1,'w':6,'h':2,'label':'Core'},"
        + "'" + SERVER + "/tps':{'card':'c:wall','x':0,'y':0,'w':6,'h':2},"
        + "'" + REACTOR + "/hist':{'hidden':true},"
        + "'v:big':{'card':'c:wall','src':'" + REACTOR + "/temp','as':'label','x':6,'y':1,'w':6,'h':2}}}");

    private static List<String> ids(JsonObject section) {
        var ids = new ArrayList<String>();
        for (var w : section.getAsJsonArray("widgets")) ids.add(w.getAsJsonObject().get("id").getAsString());
        return ids;
    }

    private static JsonObject widget(JsonObject section, String id) {
        for (var w : section.getAsJsonArray("widgets")) if (w.getAsJsonObject().get("id").getAsString().equals(id)) return w.getAsJsonObject();
        throw new AssertionError("no widget " + id);
    }

    @Test
    void targets() {
        assertTrue(DisplayCompose.validTarget(""));
        assertTrue(DisplayCompose.validTarget(REACTOR));
        assertTrue(DisplayCompose.validTarget("c:wall"));
        assertTrue(DisplayCompose.validTarget("p:main"));
        assertFalse(DisplayCompose.validTarget("c:"));
        assertFalse(DisplayCompose.validTarget("x:wall"));
        assertFalse(DisplayCompose.validTarget("c:bad key"));
        assertTrue(DisplayCompose.isComposite("c:wall") && DisplayCompose.isComposite("p:main") && !DisplayCompose.isComposite(REACTOR));
    }

    @Test
    void customCardFollowsTheLayout() {
        var result = DisplayCompose.compose("c:wall", LAYOUT, sections(), id -> true);
        var section = result.section();
        assertNotNull(section);
        assertEquals("Wall", section.get("title").getAsString());
        assertEquals(List.of(SERVER + "/tps", REACTOR + "/temp", "v:big"), ids(section), "by row, then column");
        assertEquals("Core", widget(section, REACTOR + "/temp").get("label").getAsString(), "the label from the layout");
        assertEquals(REACTOR + "/temp", widget(section, "v:big").get("src").getAsString(), "clicks go to the published widget");
        assertEquals("label", widget(section, "v:big").get("type").getAsString());
        assertEquals("40 C", widget(section, "v:big").get("value").getAsString(), "a view of a number as a label keeps the unit");
        assertFalse(section.get("online").getAsBoolean(), "offline as soon as one of its sections is");
    }

    @Test
    void restrictedWidgetsStayOffUnlessAllowed() {
        var result = DisplayCompose.compose("c:wall", LAYOUT, sections(), id -> !id.equals(SERVER));
        assertEquals(1, result.filtered());
        assertEquals(List.of(REACTOR + "/temp", "v:big"), ids(result.section()));
        assertTrue(result.section().get("online").getAsBoolean(), "only what is shown counts");

        var layout = json("{'v':2,'pages':[],'cards':{'c:only':{}},'widgets':{'" + SERVER + "/tps':{'card':'c:only'}}}");
        var only = DisplayCompose.compose("c:only", layout, sections(), id -> !id.equals(SERVER));
        assertEquals(1, only.filtered());
        assertTrue(only.section().getAsJsonArray("widgets").isEmpty());
    }

    @Test
    void aViewOfARestrictedSectionIsFilteredToo() {
        var layout = json("{'v':2,'pages':[],'cards':{'c:v':{}},'widgets':{'v:a':{'card':'c:v','src':'" + SERVER + "/tps','as':'label'}}}");
        assertEquals(1, DisplayCompose.compose("c:v", layout, sections(), id -> !id.equals(SERVER)).filtered());
        assertEquals(1, DisplayCompose.compose("c:v", layout, sections(), id -> true).section().getAsJsonArray("widgets").size());
    }

    @Test
    void pageHasHeadingsAndKeepsOutHiddenAndOtherPages() {
        var page = DisplayCompose.compose("p:ops", LAYOUT, sections(), id -> true).section();
        assertEquals("Operations", page.get("title").getAsString());
        assertEquals(List.of("h:" + REACTOR + "/ctl", REACTOR + "/stop"), ids(page).subList(0, 2), "the group's page, its card title as a heading");
        assertEquals("Controls", widget(page, "h:" + REACTOR + "/ctl").get("label").getAsString());
        var main = DisplayCompose.compose("p:main", LAYOUT, sections(), id -> true).section();
        assertTrue(ids(main).contains("c:wall") == false && ids(main).contains(REACTOR + "/run"), "ungrouped widgets stay on the first page");
        assertFalse(ids(main).contains(REACTOR + "/hist"), "hidden widgets are left out");
    }

    @Test
    void naturalOrderWithoutALayoutIsReadoutsFirst() {
        var page = DisplayCompose.compose("p:main", null, sections(), id -> !id.equals(SERVER)).section();
        assertEquals("Overview", page.get("title").getAsString());
        assertTrue(ids(page).indexOf(REACTOR + "/temp") < ids(page).indexOf(REACTOR + "/run"), "gauges before toggles");
    }

    @Test
    void blockedWidgetsFollowTheirKey() {
        var page = DisplayCompose.compose("p:main", LAYOUT, sections(), id -> true).section();
        assertTrue(page.getAsJsonObject("blockedWidgets").has(REACTOR + "/run"));
    }

    @Test
    void unknownTargetsAndInputStaysUntouched() {
        assertNull(DisplayCompose.compose("c:nope", LAYOUT, sections(), id -> true).section());
        assertNull(DisplayCompose.compose("p:nope", LAYOUT, sections(), id -> true).section());
        assertFalse(DisplayCompose.exists("c:wall", null, sections()), "no layout, no custom cards");
        var input = sections();
        String before = input.get(REACTOR).toString();
        DisplayCompose.compose("c:wall", LAYOUT, input, id -> true);
        assertEquals(before, input.get(REACTOR).toString(), "the hub's objects are shared");
    }

    @Test
    void pickerRows() {
        var rows = DisplayCompose.targets(LAYOUT, sections(), Set.of(SERVER));
        var byId = new LinkedHashMap<String, JsonObject>();
        for (var r : rows) byId.put(r.getAsJsonObject().get("uplinkId").getAsString(), r.getAsJsonObject());
        assertEquals(Set.of("c:wall", "c:empty", "p:main", "p:ops"), byId.keySet());
        assertTrue(byId.get("c:wall").get("restricted").getAsBoolean(), "it holds a widget of a restricted section");
        assertEquals("New card", byId.get("c:empty").get("title").getAsString());
        assertEquals("page", byId.get("p:ops").get("kind").getAsString());
    }

    @Test
    void headingsTakeARowOfTheirOwn() {
        var page = DisplayCompose.compose("p:ops", LAYOUT, sections(), id -> true).section();
        var layout = DisplayLayout.layout(page, 4, 3);
        var heading = layout.boxes().getFirst();
        assertEquals("heading", heading.type());
        assertEquals(4 * DisplayLayout.PX_PER_BLOCK - 2 * DisplayLayout.BORDER, heading.w());
        assertFalse(heading.clickable());
    }
}
