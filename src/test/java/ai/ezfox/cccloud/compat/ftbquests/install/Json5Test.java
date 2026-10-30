package ai.ezfox.cccloud.compat.ftbquests.install;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The JSON5 reader/editor of the quest installer: FTB Quests' files, in-place edits, format-independent hashes. */
class Json5Test {
    @Test
    void parsesFtbQuestsAndHandWrittenJson5() {
        var root = Json5.parse("""
            // a comment
            {
              id: "4A19A9D21CB02379", /* inline */
              'single': 'it\\'s',
              "quoted.key": [1, +2, -3.5, .5, 0x1F, 1e3, Infinity, NaN,],
              flag: true, none: null,
              text: "line\\nbreak \\u00e4 \\
            continued",
            }
            """);
        assertEquals("4A19A9D21CB02379", root.stringOf("id"));
        assertEquals("it's", root.stringOf("single"));
        assertEquals(8, root.get("quoted.key").items.size());
        assertEquals("line\nbreak ä continued", root.stringOf("text"));
        assertEquals(Json5.Kind.BOOLEAN, root.get("flag").kind);
        assertEquals(Json5.Kind.NULL, root.get("none").kind);
        assertEquals("[1,2,-3.5,0.5,31,1000,Infinity,NaN]", Json5.canonical(root.get("quoted.key")));
    }

    @Test
    void rejectsBrokenInputWithoutCrashing() {
        for (String bad : List.of("{", "{a: }", "[1 2]", "{a: \"x}", "{a: 1} x", "{a: 'x\n'}", "/* open", "{a: 0x}")) {
            assertThrows(Json5.ParseException.class, () -> Json5.parse(bad), bad);
        }
        assertThrows(Json5.ParseException.class, () -> Json5.parse("[".repeat(10_000)));
    }

    @Test
    void canonicalFormIgnoresLayoutButNotContent() {
        var a = Json5.parse("{ x: 1.0, y: \"a\", list: [1, 2], nested: { b: 1, a: 2 } }");
        var b = Json5.parse("{\n  \"nested\": {a: 2.00, b: 1},\n  list: [1,2,],\n  y: 'a',\n  x: 1,\n}");
        assertEquals(Json5.canonical(a), Json5.canonical(b));
        assertNotEquals(Json5.canonical(a), Json5.canonical(Json5.parse("{ x: 1, y: \"a\", list: [2, 1], nested: { b: 1, a: 2 } }")));
        assertEquals(Json5.canonical(a, Set.of("x")), Json5.canonical(Json5.parse("{ x: 9, y: \"a\", list: [1, 2], nested: { b: 1, a: 2 } }"), Set.of("x")));
        assertEquals("0", Json5.number("-0.0"));
        assertEquals("487623848494439020", Json5.number("487623848494439020"));
    }

    @Test
    void insertsMembersInTheFilesOwnLayout() {
        String src = "{\n  \"a\": \"1\",\n  \"b\": [\n    \"x\",\n  ]\n}\n";
        String out = Json5.insert(src, Json5.parse(src), List.of(new Json5.Entry("c.d", "2"), new Json5.Entry("e", List.of("l1", "l2"))));
        assertEquals("{\n  \"a\": \"1\",\n  \"b\": [\n    \"x\",\n  ],\n  \"c.d\": \"2\",\n  e: [\n    \"l1\",\n    \"l2\",\n  ],\n}\n", out);
        assertEquals(List.of("a", "b", "c.d", "e"), Json5.parse(out).members.stream().map(Json5.Member::key).toList());
        // empty objects, one-line objects, CRLF files
        assertEquals("{\n  x: 1,\n}", Json5.insert("{}", Json5.parse("{}"), List.of(new Json5.Entry("x", new Json5.RawNumber("1")))));
        assertEquals("{ a: 1,\n  b: 2,\n}", Json5.insert("{ a: 1 }", Json5.parse("{ a: 1 }"), List.of(new Json5.Entry("b", new Json5.RawNumber("2")))));
        String crlf = "{\r\n  a: 1,\r\n}\r\n";
        assertEquals("{\r\n  a: 1,\r\n  b: [\r\n    \"x\",\r\n  ],\r\n}\r\n",
            Json5.insert(crlf, Json5.parse(crlf), List.of(new Json5.Entry("b", List.of("x")))));
        // an item in a nested array
        String groups = "{\n  chapter_groups: [\n    {\n      id: \"1111111111111111\",\n    },\n  ],\n}\n";
        Json5.Node arr = Json5.parse(groups).get("chapter_groups");
        String g = Json5.insert(groups, arr, List.of(new Json5.Entry(null, Map.of("id", "2222222222222222"))));
        assertEquals("{\n  chapter_groups: [\n    {\n      id: \"1111111111111111\",\n    },\n    {\n      id: \"2222222222222222\",\n    },\n  ],\n}\n", g);
    }

    @Test
    void removesItemsAndReplacesValues() {
        String src = "{\n  deps: [\n    \"A\",\n    \"B\",\n    \"C\",\n  ],\n  inline: [\"x\", \"y\"],\n  n: 1,\n}\n";
        var root = Json5.parse(src);
        String out = Json5.removeItems(src, List.of(root.get("deps").items.get(1), root.get("inline").items.get(0)));
        assertEquals("{\n  deps: [\n    \"A\",\n    \"C\",\n  ],\n  inline: [\"y\"],\n  n: 1,\n}\n", out);
        assertEquals("{\n  deps: [\n    \"A\",\n    \"C\",\n  ],\n  inline: [\"y\"],\n  n: 2,\n}\n",
            Json5.replace(out, Json5.parse(out).get("n"), "2"));
    }

    @Test
    void writesStringsLikeFtbQuests() {
        assertEquals("\"a \\\"b\\\" \\\\ ä\\n\"", Json5.quote("a \"b\" \\ ä\n"));
        assertEquals("id", Json5.key("id"));
        assertEquals("\"quest.1.title\"", Json5.key("quest.1.title"));
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", "x");
        map.put("l", List.of());
        assertEquals("{\n  id: \"x\",\n  l: [\n  ],\n}", Json5.format(map, "", "\n"));
    }
}
