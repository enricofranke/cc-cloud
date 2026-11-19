package ai.ezfox.cccloud.code.wasm;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.ScriptFunction;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

/**
 * Values between a WebAssembly script and the host, as JSON text: the script's arguments become plain Java values
 * ({@code null}, Boolean, Long for whole numbers written without fraction or exponent, Double, String,
 * {@code Map<String, Object>}, {@code List<Object>}, and {@code {"$fn": id}} a handler of the script), the host's
 * results become JSON. Anything malformed, too deep or too large is an {@link ApiError} for the script, never an
 * exception in the server.
 */
final class WasmJson {
    static final int MAX_DEPTH = 64;
    private static final String FUNCTION = "$fn";

    private WasmJson() {
    }

    /** Parses one JSON value; {@code functions} turns a handler id into the engine's function object. */
    static @Nullable Object parse(String text, IntFunction<ScriptFunction> functions) {
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(com.google.gson.Strictness.STRICT);
            Object value = read(reader, functions, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new ApiError("malformed JSON: text after the value");
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new ApiError("malformed JSON from the script: " + clip(String.valueOf(e.getMessage())));
        }
    }

    private static @Nullable Object read(JsonReader reader, IntFunction<ScriptFunction> functions, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new ApiError("JSON nested deeper than " + MAX_DEPTH);
        return switch (reader.peek()) {
            case NULL -> {
                reader.nextNull();
                yield null;
            }
            case BOOLEAN -> reader.nextBoolean();
            case STRING -> reader.nextString();
            case NUMBER -> number(reader.nextString());
            case BEGIN_ARRAY -> {
                var list = new ArrayList<@Nullable Object>();
                reader.beginArray();
                while (reader.hasNext()) list.add(read(reader, functions, depth + 1));
                reader.endArray();
                yield list;
            }
            case BEGIN_OBJECT -> {
                var map = new LinkedHashMap<String, @Nullable Object>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    map.put(key, read(reader, functions, depth + 1));
                }
                reader.endObject();
                if (map.size() == 1 && map.get(FUNCTION) instanceof Long id && id >= 0 && id <= Integer.MAX_VALUE) yield functions.apply(id.intValue());
                yield map;
            }
            default -> throw new ApiError("malformed JSON from the script");
        };
    }

    private static Object number(String text) {
        boolean whole = !text.isEmpty() && text.length() <= 18;
        for (int i = 0; i < text.length() && whole; i++) {
            char c = text.charAt(i);
            whole = c >= '0' && c <= '9' || i == 0 && c == '-' && text.length() > 1;
        }
        if (whole) return Long.parseLong(text);
        double d = Double.parseDouble(text);
        if (!Double.isFinite(d)) throw new ApiError("number out of range");
        return d;
    }

    /** Java → JSON: maps, lists, arrays (several results), strings, numbers, booleans, null; anything else as its text. */
    static String write(@Nullable Object value) {
        var out = new StringWriter();
        try (var writer = new JsonWriter(out)) {
            writer.setSerializeNulls(true);
            write(writer, value, 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    private static void write(JsonWriter writer, @Nullable Object value, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            writer.nullValue();
            return;
        }
        switch (value) {
            case null -> writer.nullValue();
            case Boolean b -> writer.value(b);
            case Long l -> writer.value(l);
            case Integer i -> writer.value(i);
            case Number n -> {
                double d = n.doubleValue();
                if (Double.isFinite(d)) writer.value(d);
                else writer.nullValue();
            }
            case String s -> writer.value(s);
            case Map<?, ?> m -> {
                writer.beginObject();
                for (var e : m.entrySet()) {
                    writer.name(String.valueOf(e.getKey()));
                    write(writer, e.getValue(), depth + 1);
                }
                writer.endObject();
            }
            case List<?> l -> {
                writer.beginArray();
                for (var e : l) write(writer, e, depth + 1);
                writer.endArray();
            }
            case Object[] a -> {
                writer.beginArray();
                for (var e : a) write(writer, e, depth + 1);
                writer.endArray();
            }
            default -> writer.value(String.valueOf(value));
        }
    }

    /** The results of a host function as a JSON array: none, one value, or several ({@code Object[]}). */
    static String results(@Nullable Object result, boolean returnsSomething) {
        if (result instanceof Object[] several) return write(several);
        if (result == null && !returnsSomething) return "[]";
        return write(new Object[]{result});
    }

    private static String clip(String s) {
        return s.length() > 120 ? s.substring(0, 119) + "…" : s;
    }

    static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }
}
