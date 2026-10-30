package ai.ezfox.cccloud.compat.ftbquests.install;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The JSON5 that FTB Quests reads and writes (quest book files), small enough to keep the source text: every node knows
 * where it starts and ends, so the installer edits pack files in place (append a member, replace a value, append or remove
 * an array item) and never re-formats what it did not change. {@link #canonical} gives a format-independent form for
 * content hashes: key order, whitespace, comments, quotes and number spelling ({@code 1.0} = {@code 1}) do not count,
 * because FTB Quests re-writes its files in its own layout whenever it saves the book.
 */
public final class Json5 {
    private static final int MAX_DEPTH = 256;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private Json5() {
    }

    public enum Kind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    /** A parsed value; {@code start}/{@code end} are offsets into the source ({@code end} exclusive). */
    public static final class Node {
        public final Kind kind;
        public final int start;
        int end;
        /** String value, raw number text or {@code true}/{@code false}/{@code null}. */
        public final String text;
        public final List<Member> members;
        public final List<Node> items;

        Node(Kind kind, int start, String text) {
            this.kind = kind;
            this.start = start;
            this.text = text;
            this.members = kind == Kind.OBJECT ? new ArrayList<>() : List.of();
            this.items = kind == Kind.ARRAY ? new ArrayList<>() : List.of();
        }

        public int end() {
            return end;
        }

        /** The member's value (the last one if a key repeats, as in JSON5), or null. */
        public Node get(String key) {
            Node found = null;
            for (Member m : members) if (m.key().equals(key)) found = m.value();
            return found;
        }

        public Member member(String key) {
            Member found = null;
            for (Member m : members) if (m.key().equals(key)) found = m;
            return found;
        }

        /** The string value, or null when this is not a string. */
        public String string() {
            return kind == Kind.STRING ? text : null;
        }

        public String stringOf(String key) {
            Node n = get(key);
            return n == null ? null : n.string();
        }
    }

    /** {@code start} is where the key begins. */
    public record Member(String key, int start, Node value) {
    }

    public static final class ParseException extends RuntimeException {
        ParseException(String message, int pos) {
            super(message + " at offset " + pos);
        }
    }

    // ------------------------------------------------------------------------------------------------ parsing

    public static Node parse(String src) {
        var p = new Parser(src);
        p.skip();
        Node root = p.value(0);
        p.skip();
        if (p.i < src.length()) throw new ParseException("unexpected text after the value", p.i);
        return root;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c) || c == '﻿' || c == ' ' || c == ' ' || c == ' ') {
                    i++;
                } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    while (i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
                } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                    int close = s.indexOf("*/", i + 2);
                    if (close < 0) throw new ParseException("unclosed comment", i);
                    i = close + 2;
                } else {
                    return;
                }
            }
        }

        Node value(int depth) {
            if (depth > MAX_DEPTH) throw new ParseException("nested too deeply", i);
            if (i >= s.length()) throw new ParseException("value expected", i);
            char c = s.charAt(i);
            if (c == '{') return object(depth);
            if (c == '[') return array(depth);
            if (c == '"' || c == '\'') {
                int start = i;
                var n = new Node(Kind.STRING, start, string());
                n.end = i;
                return n;
            }
            if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')) return number();
            String word = identifier();
            Node n = switch (word) {
                case "true", "false" -> new Node(Kind.BOOLEAN, i - word.length(), word);
                case "null" -> new Node(Kind.NULL, i - word.length(), word);
                case "Infinity", "NaN" -> new Node(Kind.NUMBER, i - word.length(), word);
                default -> throw new ParseException("unexpected '" + (word.isEmpty() ? String.valueOf(c) : word) + "'", i - word.length());
            };
            n.end = i;
            return n;
        }

        Node object(int depth) {
            var n = new Node(Kind.OBJECT, i, null);
            i++;
            skip();
            while (true) {
                if (i >= s.length()) throw new ParseException("unclosed object", n.start);
                if (s.charAt(i) == '}') break;
                int keyStart = i;
                String key = s.charAt(i) == '"' || s.charAt(i) == '\'' ? string() : identifier();
                if (key.isEmpty() && keyStart == i) throw new ParseException("key expected", i);
                skip();
                expect(':');
                skip();
                n.members.add(new Member(key, keyStart, value(depth + 1)));
                skip();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    skip();
                } else if (i >= s.length() || s.charAt(i) != '}') {
                    throw new ParseException("',' or '}' expected", i);
                }
            }
            i++;
            n.end = i;
            return n;
        }

        Node array(int depth) {
            var n = new Node(Kind.ARRAY, i, null);
            i++;
            skip();
            while (true) {
                if (i >= s.length()) throw new ParseException("unclosed array", n.start);
                if (s.charAt(i) == ']') break;
                n.items.add(value(depth + 1));
                skip();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    skip();
                } else if (i >= s.length() || s.charAt(i) != ']') {
                    throw new ParseException("',' or ']' expected", i);
                }
            }
            i++;
            n.end = i;
            return n;
        }

        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw new ParseException("'" + c + "' expected", i);
            i++;
        }

        String identifier() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '$') i++;
                else break;
            }
            return s.substring(start, i);
        }

        String string() {
            char quote = s.charAt(i++);
            var out = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw new ParseException("unclosed string", i);
                char c = s.charAt(i++);
                if (c == quote) return out.toString();
                if (c == '\n' || c == '\r') throw new ParseException("line break in string", i - 1);
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (i >= s.length()) throw new ParseException("unclosed string", i);
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'v' -> out.append('\u000B');
                    case '0' -> out.append('\0');
                    case 'u' -> out.append((char) hex(4));
                    case 'x' -> out.append((char) hex(2));
                    case '\r' -> {
                        if (i < s.length() && s.charAt(i) == '\n') i++;
                    }
                    case '\n', ' ', ' ' -> {
                    }
                    default -> out.append(e);
                }
            }
        }

        int hex(int digits) {
            if (i + digits > s.length()) throw new ParseException("bad escape", i);
            try {
                int v = Integer.parseInt(s.substring(i, i + digits), 16);
                i += digits;
                return v;
            } catch (NumberFormatException e) {
                throw new ParseException("bad escape", i);
            }
        }

        Node number() {
            int start = i;
            if (s.charAt(i) == '+' || s.charAt(i) == '-') i++;
            if (s.startsWith("Infinity", i)) {
                i += 8;
            } else if (s.startsWith("NaN", i)) {
                i += 3;
            } else if (s.startsWith("0x", i) || s.startsWith("0X", i)) {
                i += 2;
                int digits = i;
                while (i < s.length() && Character.digit(s.charAt(i), 16) >= 0) i++;
                if (i == digits) throw new ParseException("bad number", start);
            } else {
                int digits = 0;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                    digits++;
                }
                if (i < s.length() && s.charAt(i) == '.') {
                    i++;
                    while (i < s.length() && Character.isDigit(s.charAt(i))) {
                        i++;
                        digits++;
                    }
                }
                if (digits == 0) throw new ParseException("bad number", start);
                if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    i++;
                    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                    int exp = i;
                    while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                    if (i == exp) throw new ParseException("bad number", start);
                }
            }
            var n = new Node(Kind.NUMBER, start, s.substring(start, i));
            n.end = i;
            return n;
        }
    }

    // ------------------------------------------------------------------------------------------------ canonical form

    /** A format-independent JSON text of the value: sorted keys, no whitespace, numbers in plain decimal form. */
    public static String canonical(Node node) {
        return canonical(node, Set.of());
    }

    /** Like {@link #canonical(Node)}, without the root object's members named in {@code skipRootKeys}. */
    public static String canonical(Node node, Set<String> skipRootKeys) {
        Object value = plain(node);
        if (!skipRootKeys.isEmpty() && value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map);
            copy.keySet().removeAll(skipRootKeys);
            value = copy;
        }
        return canonicalPlain(value);
    }

    /** The canonical form of plain values ({@link #plain}): sorted keys, no whitespace, plain decimal numbers. */
    public static String canonicalPlain(Object value) {
        var out = new StringBuilder();
        writePlain(value, out);
        return out.toString();
    }

    private static void writePlain(Object v, StringBuilder out) {
        if (v instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var e : map.entrySet().stream().sorted(Comparator.comparing(e -> String.valueOf(e.getKey()))).toList()) {
                if (!first) out.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), out);
                out.append(':');
                writePlain(e.getValue(), out);
            }
            out.append('}');
        } else if (v instanceof List<?> list) {
            out.append('[');
            for (int k = 0; k < list.size(); k++) {
                if (k > 0) out.append(',');
                writePlain(list.get(k), out);
            }
            out.append(']');
        } else if (v instanceof String str) {
            quote(str, out);
        } else if (v instanceof RawNumber || v instanceof Number) {
            out.append(number(v.toString()));
        } else {
            out.append(v);
        }
    }

    static String number(String raw) {
        String t = raw.startsWith("+") ? raw.substring(1) : raw;
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) return new BigInteger(t.substring(2), 16).toString();
            if (t.startsWith("-0x") || t.startsWith("-0X")) return new BigInteger(t.substring(3), 16).negate().toString();
            BigDecimal d = new BigDecimal(t);
            return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return t; // Infinity, NaN
        }
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** SHA-256 of the canonical form. */
    public static String hash(Node node, Set<String> skipRootKeys) {
        return sha256(canonical(node, skipRootKeys));
    }

    // ------------------------------------------------------------------------------------------------ writing

    /** A JSON string literal in FTB Quests' style (double quotes; non-ASCII text stays as it is). */
    public static String quote(String s) {
        var out = new StringBuilder();
        quote(s, out);
        return out.toString();
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    /** A key as FTB Quests writes it: bare when it is a plain identifier, quoted otherwise. */
    public static String key(String key) {
        return IDENTIFIER.matcher(key).matches() ? key : quote(key);
    }

    /**
     * Formats a value in FTB Quests' layout (two spaces per level, one member or item per line, trailing commas).
     * Values: {@link Map} (insertion order), {@link List}, {@link String}, {@link Boolean}, {@link Number} or
     * {@link RawNumber}; {@link #plain} turns a parsed node into these.
     */
    public static String format(Object value, String indent, String nl) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{" + nl + indent + "}";
            var out = new StringBuilder("{").append(nl);
            for (var e : map.entrySet()) {
                out.append(indent).append("  ").append(key(String.valueOf(e.getKey()))).append(": ")
                    .append(format(e.getValue(), indent + "  ", nl)).append(',').append(nl);
            }
            return out.append(indent).append('}').toString();
        }
        if (value instanceof List<?> list) {
            var out = new StringBuilder("[").append(nl);
            for (Object o : list) out.append(indent).append("  ").append(format(o, indent + "  ", nl)).append(',').append(nl);
            return out.append(indent).append(']').toString();
        }
        if (value instanceof String s) return quote(s);
        return String.valueOf(value);
    }

    /** The node as plain Java values (Map, List, String, Boolean, raw number text as a Number-like string, null). */
    public static Object plain(Node n) {
        return switch (n.kind) {
            case OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Member m : n.members) map.put(m.key(), plain(m.value()));
                yield map;
            }
            case ARRAY -> {
                List<Object> list = new ArrayList<>();
                for (Node item : n.items) list.add(plain(item));
                yield list;
            }
            case STRING -> n.text;
            case NUMBER -> new RawNumber(n.text);
            case BOOLEAN -> Boolean.valueOf(n.text);
            case NULL -> new RawNumber("null");
        };
    }

    /** A number (or null) written back exactly as it was read. */
    public record RawNumber(String raw) {
        @Override
        public String toString() {
            return raw;
        }
    }

    // ------------------------------------------------------------------------------------------------ editing

    /** The line break style of a text (CRLF if it uses any, else LF). */
    public static String newline(String src) {
        return src.contains("\r\n") ? "\r\n" : "\n";
    }

    /** The whitespace in front of {@code pos} on its line, or null if something else stands before it on that line. */
    static String indentBefore(String src, int pos) {
        int k = pos;
        while (k > 0 && (src.charAt(k - 1) == ' ' || src.charAt(k - 1) == '\t')) k--;
        if (k == 0 || src.charAt(k - 1) == '\n' || src.charAt(k - 1) == '\r') return src.substring(k, pos);
        return null;
    }

    private static int lineStart(String src, int pos) {
        int k = pos;
        while (k > 0 && src.charAt(k - 1) != '\n' && src.charAt(k - 1) != '\r') k--;
        return k;
    }

    /** Whether a comma follows {@code from} before {@code to} (skipping whitespace and comments). */
    private static boolean commaBetween(String src, int from, int to) {
        var p = new Parser(src);
        p.i = from;
        p.skip();
        return p.i < to && src.charAt(p.i) == ',';
    }

    /** An entry to insert: a member ({@code key} set) of an object, or an item ({@code key} null) of an array. */
    public record Entry(String key, Object value) {
    }

    /**
     * Inserts entries before the closing bracket of an object or array node, one per line with a trailing comma, in the
     * indentation and line break style of the existing entries (values formatted by {@link #format}).
     */
    public static String insert(String src, Node container, List<Entry> entries) {
        if (entries.isEmpty()) return src;
        String nl = newline(src);
        int close = container.end - 1;
        List<Integer> starts = new ArrayList<>();
        int lastEnd = -1;
        if (container.kind == Kind.OBJECT) {
            for (Member m : container.members) starts.add(m.start());
            if (!container.members.isEmpty()) lastEnd = container.members.getLast().value().end;
        } else {
            for (Node item : container.items) starts.add(item.start);
            if (!container.items.isEmpty()) lastEnd = container.items.getLast().end;
        }
        String closeIndent = indentBefore(src, close);
        String outer = closeIndent != null ? closeIndent : lineIndent(src, container.start);
        String indent = starts.isEmpty() ? null : indentBefore(src, starts.getFirst());
        if (indent == null) indent = outer + "  ";
        var block = new StringBuilder();
        for (Entry e : entries) {
            block.append(indent);
            if (e.key() != null) block.append(key(e.key())).append(": ");
            block.append(format(e.value(), indent, nl)).append(',').append(nl);
        }
        boolean comma = lastEnd >= 0 && !commaBetween(src, lastEnd, close);
        int at;
        String insertion;
        if (closeIndent != null) {
            at = lineStart(src, close);
            insertion = block.toString();
        } else {
            at = close;
            while (at > Math.max(container.start + 1, lastEnd) && Character.isWhitespace(src.charAt(at - 1))) at--;
            insertion = nl + block + outer;
        }
        var out = new StringBuilder();
        if (lastEnd >= 0) out.append(src, 0, lastEnd).append(comma ? "," : "").append(src, lastEnd, at);
        else out.append(src, 0, at);
        out.append(insertion).append(src, closeIndent != null ? at : close, src.length());
        return out.toString();
    }

    private static String lineIndent(String src, int pos) {
        int start = lineStart(src, pos);
        int k = start;
        while (k < src.length() && (src.charAt(k) == ' ' || src.charAt(k) == '\t')) k++;
        return src.substring(start, k);
    }

    /** Replaces a value node's text. */
    public static String replace(String src, Node value, String text) {
        return src.substring(0, value.start) + text + src.substring(value.end);
    }

    /**
     * Removes array items (with their comma, and their whole line when they stand alone on it). Items must belong to
     * the same parsed source; they may come from different arrays.
     */
    public static String removeItems(String src, List<Node> items) {
        var sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt((Node n) -> n.start).reversed());
        String out = src;
        for (Node item : sorted) {
            int from = item.start;
            int to = item.end;
            var p = new Parser(out);
            p.i = to;
            p.skip();
            boolean comma = p.i < out.length() && out.charAt(p.i) == ',';
            if (comma) to = p.i + 1;
            boolean wholeLine = false;
            if (indentBefore(out, from) != null) {
                int lineEnd = to;
                while (lineEnd < out.length() && (out.charAt(lineEnd) == ' ' || out.charAt(lineEnd) == '\t')) lineEnd++;
                if (lineEnd < out.length() && out.charAt(lineEnd) == '\r') lineEnd++;
                if (lineEnd < out.length() && out.charAt(lineEnd) == '\n') lineEnd++;
                if (lineEnd == out.length() || out.charAt(lineEnd - 1) == '\n') {
                    from = lineStart(out, from);
                    to = lineEnd;
                    wholeLine = true;
                }
            }
            if (!wholeLine && comma) while (to < out.length() && (out.charAt(to) == ' ' || out.charAt(to) == '\t')) to++;
            out = out.substring(0, from) + out.substring(to);
        }
        return out;
    }
}
