package ai.ezfox.cccloud.code.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The language-neutral description of the {@code cloud} API ({@code /cccloud_code/api.json}): one file that the Lua
 * binding ({@link ai.ezfox.cccloud.code.lua.LuaBinding}), the dashboard editor's completion and hover docs and the docs
 * reference are all generated from. Later languages bind the same functions. {@link CloudApi} holds the Java side of
 * every function; {@code ApiSpecTest} checks that both list the same names.
 */
public record ApiSpec(String name, int version, List<Function> functions, Map<String, Type> types, List<Event> events, List<Field> fields, Lua lua) {
    public static final String RESOURCE = "/cccloud_code/api.json";

    /**
     * How a call returns: {@code SYNC} at once (reads from the snapshot, bookkeeping), {@code AWAIT} once the server
     * thread ran it (the script pauses meanwhile, without using its CPU budget), {@code YIELD} after a time or an event.
     */
    public enum Kind { SYNC, AWAIT, YIELD }

    public record Param(String name, String type, boolean optional, boolean variadic) {
    }

    public record Function(String name, String group, Kind kind, List<Param> params, List<String> returns, Map<String, String> doc, @Nullable String perm,
                           @Nullable String aliasOf, @Nullable String example) {
        /** The table path inside {@code cloud}: {@code store.get} → [store, get]. */
        public String[] path() {
            return name.split("\\.");
        }

        /** The function whose Java handler runs this one (itself, or the function it is an alias of). */
        public String target() {
            return aliasOf != null ? aliasOf : name;
        }

        public int required() {
            int n = 0;
            for (var p : params) if (!p.optional() && !p.variadic()) n++;
            return n;
        }
    }

    public record TypeField(String name, String type) {
    }

    /** A named type: {@code alias} of a basic type, or a {@code table} with fields. */
    public record Type(String kind, @Nullable String of, List<TypeField> fields) {
    }

    public record Event(String name, List<String> filter) {
    }

    public record Field(String name, String type) {
    }

    /** What the Lua sandbox offers ({@code globals}, library members) and leaves out ({@code removed}); checked by the sandbox test. */
    public record Lua(List<String> globals, Map<String, List<String>> libraries, List<String> removed) {
    }

    private static volatile @Nullable ApiSpec loaded;

    /** The descriptor shipped in the jar (parsed once). */
    public static ApiSpec get() {
        var spec = loaded;
        if (spec != null) return spec;
        try (InputStream in = ApiSpec.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing " + RESOURCE);
            spec = parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
        loaded = spec;
        return spec;
    }

    public static ApiSpec parse(JsonObject root) {
        var functions = new ArrayList<Function>();
        for (var e : root.getAsJsonArray("functions")) {
            var f = e.getAsJsonObject();
            var params = new ArrayList<Param>();
            for (var p : array(f, "params")) {
                var o = p.getAsJsonObject();
                params.add(new Param(o.get("name").getAsString(), o.get("type").getAsString(), bool(o, "optional"), bool(o, "variadic")));
            }
            var returns = new ArrayList<String>();
            for (var r : array(f, "returns")) returns.add(r.getAsJsonObject().get("type").getAsString());
            functions.add(new Function(f.get("name").getAsString(), f.get("group").getAsString(), Kind.valueOf(f.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
                List.copyOf(params), List.copyOf(returns), doc(f), str(f, "perm"), str(f, "aliasOf"), str(f, "example")));
        }
        var types = new LinkedHashMap<String, Type>();
        for (var e : root.getAsJsonObject("types").entrySet()) {
            var t = e.getValue().getAsJsonObject();
            var fields = new ArrayList<TypeField>();
            for (var f : array(t, "fields")) fields.add(new TypeField(f.getAsJsonObject().get("name").getAsString(), f.getAsJsonObject().get("type").getAsString()));
            types.put(e.getKey(), new Type(t.get("kind").getAsString(), str(t, "of"), List.copyOf(fields)));
        }
        var events = new ArrayList<Event>();
        for (var e : array(root, "events")) {
            var filter = new ArrayList<String>();
            for (var f : array(e.getAsJsonObject(), "filter")) filter.add(f.getAsString());
            events.add(new Event(e.getAsJsonObject().get("name").getAsString(), List.copyOf(filter)));
        }
        var fields = new ArrayList<Field>();
        for (var e : array(root, "fields")) fields.add(new Field(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject().get("type").getAsString()));
        var lua = root.getAsJsonObject("lua");
        var libraries = new LinkedHashMap<String, List<String>>();
        for (var e : lua.getAsJsonObject("libraries").entrySet()) libraries.put(e.getKey(), strings(e.getValue().getAsJsonArray()));
        return new ApiSpec(root.get("name").getAsString(), root.get("version").getAsInt(), List.copyOf(functions), Map.copyOf(types), List.copyOf(events),
            List.copyOf(fields), new Lua(strings(lua.getAsJsonArray("globals")), Map.copyOf(libraries), strings(lua.getAsJsonArray("removed"))));
    }

    public @Nullable Function function(String name) {
        for (var f : functions) if (f.name().equals(name)) return f;
        return null;
    }

    public @Nullable Event event(String name) {
        for (var e : events) if (e.name().equals(name)) return e;
        return null;
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.get(key) instanceof JsonArray a ? a : new JsonArray();
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    private static @Nullable String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Map<String, String> doc(JsonObject o) {
        var map = new LinkedHashMap<String, String>();
        if (o.get("doc") instanceof JsonObject d) for (var e : d.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
        return Map.copyOf(map);
    }

    private static List<String> strings(JsonArray a) {
        var list = new ArrayList<String>();
        for (var e : a) list.add(e.getAsString());
        return List.copyOf(list);
    }
}
