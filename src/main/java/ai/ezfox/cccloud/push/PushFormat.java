package ai.ezfox.cccloud.push;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** The HTTP request a target gets for a message (Discord, ntfy, generic webhook). Pure Java, unit tested. */
public final class PushFormat {
    public record Request(Map<String, String> headers, byte[] body) {
    }

    private PushFormat() {
    }

    public static Request request(PushTarget target, PushMessage message) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("User-Agent", "CC-Cloud (Minecraft mod)");
        return switch (target.type()) {
            case DISCORD -> {
                headers.put("Content-Type", "application/json");
                var json = new JsonObject();
                String icon = switch (PushTarget.rank(message.level())) {
                    case 2 -> "🔴 ";
                    case 1 -> "🟠 ";
                    default -> "";
                };
                String content = icon + "**" + markdown(message.title()) + "** – " + markdown(message.mainframe()) + "\n" + markdown(message.text());
                json.addProperty("content", content.length() > 1900 ? content.substring(0, 1899) + "…" : content);
                json.addProperty("username", "CC: Cloud");
                // never ping anyone, whatever the text contains
                var mentions = new JsonObject();
                mentions.add("parse", new JsonArray());
                json.add("allowed_mentions", mentions);
                yield new Request(headers, json.toString().getBytes(StandardCharsets.UTF_8));
            }
            case NTFY -> {
                headers.put("Content-Type", "text/plain; charset=utf-8");
                headers.put("Title", header(message.title() + " - " + message.mainframe()));
                headers.put("Priority", switch (PushTarget.rank(message.level())) {
                    case 2 -> "5";
                    case 1 -> "4";
                    default -> "3";
                });
                headers.put("Tags", switch (PushTarget.rank(message.level())) {
                    case 2 -> "rotating_light";
                    case 1 -> "warning";
                    default -> "computer";
                });
                if (!target.token().isEmpty()) headers.put("Authorization", "Bearer " + target.token());
                yield new Request(headers, message.text().getBytes(StandardCharsets.UTF_8));
            }
            case WEBHOOK -> {
                headers.put("Content-Type", "application/json");
                var json = new JsonObject();
                json.addProperty("source", "cccloud");
                json.addProperty("event", message.event().id);
                json.addProperty("mainframeId", message.mainframeId().toString());
                json.addProperty("mainframe", message.mainframe());
                json.addProperty("title", message.title());
                json.addProperty("text", message.text());
                json.addProperty("level", message.level());
                json.addProperty("time", message.time());
                yield new Request(headers, json.toString().getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    /** Escapes Discord markdown and neutralises mentions/links' embeds. */
    static String markdown(String s) {
        var out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if ("\\*_~`|>#[]()@<".indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    /** A safe header value: no control characters (no header injection); non-ASCII as RFC 2047 (ntfy decodes it). */
    static String header(String s) {
        var clean = new StringBuilder();
        for (char c : s.toCharArray()) clean.append(c < 0x20 || c == 0x7f ? ' ' : c);
        String v = clean.toString().strip();
        if (v.length() > 200) v = v.substring(0, 200);
        boolean ascii = v.chars().allMatch(c -> c < 0x80);
        return ascii ? v : "=?UTF-8?B?" + Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)) + "?=";
    }
}
