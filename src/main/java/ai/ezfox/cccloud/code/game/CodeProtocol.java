package ai.ezfox.cccloud.code.game;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.code.engine.Diagnostic;
import ai.ezfox.cccloud.code.runtime.ScriptLog;
import ai.ezfox.cccloud.code.store.ScriptBook;
import ai.ezfox.cccloud.code.store.ScriptRecord;
import ai.ezfox.cccloud.code.web.CodeViewer;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.GameBridge.CodeReply;
import ai.ezfox.cccloud.web.WebAuthData;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;

/**
 * The dashboard's {@code {"t":"code"}} requests (docs/SPEC.md "Cloud Scripts – dashboard protocol"), on the server
 * thread. The session checked {@code code.edit}; it is checked here again with the device's live rights. Compiling
 * ({@code check}, {@code save}) runs on a script worker and the save itself back on the server thread.
 */
public final class CodeProtocol {
    /** Log lines a {@code logs} request returns. */
    static final int LOG_LINES = 200;

    private CodeProtocol() {
    }

    public static CompletableFuture<CodeReply> handle(MinecraftServer server, AuthStore.Device device, UUID mainframeId, ClientMessages.CodeCommand c,
                                                      CodeViewer viewer) {
        if (c.error() != null) return done(CodeReply.fail(c.error()));
        if (!Access.resolver(server).resolveDevice(device, mainframeId).perms().contains(Perm.CODE_EDIT)) return done(CodeReply.fail("not_allowed"));
        if (CodeService.server() != server) return done(CodeReply.fail("offline"));
        String player = device.playerName();
        var book = CodeService.book(mainframeId);
        ScriptRecord record = c.id() == null ? null : book.get(c.id());
        if (c.id() != null && record == null && !c.op().equals("unwatch")) return done(CodeReply.fail("unknown_script"));
        var limits = CodeService.limits();
        return switch (c.op()) {
            case "list" -> {
                CodeService.listen(mainframeId, viewer);
                var data = new JsonObject();
                data.add("scripts", CodeService.list(mainframeId));
                data.add("limits", limits.toJson());
                data.addProperty("enabled", CodeService.enabled());
                yield done(CodeReply.ok(data));
            }
            case "get" -> {
                var script = CodeService.summary(mainframeId, record, limits);
                script.addProperty("source", record.source());
                var data = new JsonObject();
                data.add("script", script);
                yield done(CodeReply.ok(data));
            }
            case "check" -> {
                if (!CodeService.enabled()) yield done(CodeReply.fail("disabled"));
                if (c.source().getBytes(StandardCharsets.UTF_8).length > limits.maxSource()) yield done(CodeReply.fail("too_large", String.valueOf(limits.maxSource())));
                String name = c.name() != null && ScriptBook.validName(c.name()) != null ? ScriptBook.validName(c.name()) : "script";
                yield CodeService.compile(name, c.source()).handle((diagnostics, failure) -> {
                    if (failure != null) return CodeReply.fail("offline");
                    var data = new JsonObject();
                    data.add("diagnostics", diagnostics(diagnostics));
                    return CodeReply.ok(data);
                });
            }
            case "save" -> save(server, device, mainframeId, c, record);
            case "delete" -> {
                var slot = CodeService.slot(mainframeId, record.id());
                if (slot != null) {
                    if (slot.running()) CodeService.stop(slot, "deleted");
                    CodeService.removeSlot(slot);
                }
                book.delete(record.id());
                CodeService.changed(mainframeId);
                audit(server, mainframeId, player + " deleted Cloud Script \"" + record.name() + "\"");
                yield done(CodeReply.ok(new JsonObject()));
            }
            case "start" -> {
                var refusal = CodeService.start(server, mainframeId, record.id(), device.player(), player);
                if (refusal != null) yield done(CodeReply.fail(refusal.error(), refusal.detail()));
                audit(server, mainframeId, player + " started Cloud Script \"" + record.name() + "\" (v" + record.version() + ", acting as " + record.authorName() + ")");
                yield done(CodeReply.ok(new JsonObject()));
            }
            case "stop" -> {
                var slot = CodeService.slot(mainframeId, record.id());
                if (slot == null || !slot.running()) yield done(CodeReply.fail("not_running"));
                CodeService.stop(slot, "stopped");
                slot.log.system("system", "stopped by " + player);
                audit(server, mainframeId, player + " stopped Cloud Script \"" + record.name() + "\"");
                yield done(CodeReply.ok(new JsonObject()));
            }
            case "restart" -> {
                if (!CodeService.enabled()) yield done(CodeReply.fail("disabled"));
                var slot = CodeService.slot(mainframeId, record.id());
                if (slot != null && slot.running()) {
                    CodeService.stop(slot, "stopped");
                    slot.log.system("system", "restarted by " + player);
                }
                var refusal = CodeService.start(server, mainframeId, record.id(), device.player(), player);
                if (refusal != null) yield done(CodeReply.fail(refusal.error(), refusal.detail()));
                audit(server, mainframeId, player + " restarted Cloud Script \"" + record.name() + "\" (v" + record.version() + ", acting as " + record.authorName() + ")");
                yield done(CodeReply.ok(new JsonObject()));
            }
            case "autostart" -> {
                boolean on = Boolean.TRUE.equals(c.enabled());
                book.setAutostart(record.id(), on, device.player(), player);
                CodeService.changed(mainframeId);
                if (on != record.autostart()) {
                    audit(server, mainframeId, player + " switched autostart of Cloud Script \"" + record.name() + "\" " + (on ? "on" : "off"));
                }
                yield done(CodeReply.ok(new JsonObject()));
            }
            case "rename" -> {
                var result = book.rename(record.id(), c.name());
                if (result.error() != null) yield done(CodeReply.fail(result.error(), result.detail()));
                CodeService.changed(mainframeId);
                if (!result.script().name().equals(record.name())) {
                    audit(server, mainframeId, player + " renamed Cloud Script \"" + record.name() + "\" to \"" + result.script().name() + "\"");
                }
                yield done(CodeReply.ok(new JsonObject()));
            }
            case "versions" -> {
                var array = new JsonArray();
                var versions = record.versions();
                for (int i = versions.size() - 1; i >= 0; i--) {
                    var v = versions.get(i);
                    var o = new JsonObject();
                    o.addProperty("version", v.version());
                    o.addProperty("time", v.time());
                    o.addProperty("author", v.authorName());
                    o.addProperty("size", v.size());
                    if (v.note() != null) o.addProperty("note", v.note());
                    else o.add("note", JsonNull.INSTANCE);
                    array.add(o);
                }
                var data = new JsonObject();
                data.add("versions", array);
                yield done(CodeReply.ok(data));
            }
            case "version" -> {
                var v = record.version(c.version());
                if (v == null) yield done(CodeReply.fail("unknown_version"));
                var data = new JsonObject();
                data.addProperty("source", v.source());
                data.addProperty("version", v.version());
                data.addProperty("time", v.time());
                data.addProperty("author", v.authorName());
                yield done(CodeReply.ok(data));
            }
            case "revert" -> {
                var result = book.revert(record.id(), c.version(), device.player(), player, System.currentTimeMillis(), limits);
                if (result.error() != null) yield done(CodeReply.fail(result.error(), result.detail()));
                CodeService.changed(mainframeId);
                audit(server, mainframeId, player + " reverted Cloud Script \"" + record.name() + "\" to v" + c.version());
                var data = new JsonObject();
                data.addProperty("version", result.script().version());
                yield done(CodeReply.ok(data));
            }
            case "logs" -> {
                var slot = CodeService.slot(mainframeId, record, limits);
                CodeService.watch(slot, viewer);
                var lines = slot.log.since(0);
                if (lines.size() > LOG_LINES) lines = lines.subList(lines.size() - LOG_LINES, lines.size());
                var data = new JsonObject();
                data.add("lines", ScriptLog.toJson(lines));
                yield done(CodeReply.ok(data));
            }
            case "unwatch" -> {
                var slot = c.id() == null ? null : CodeService.slot(mainframeId, c.id());
                if (slot != null) CodeService.unwatch(slot, viewer);
                yield done(CodeReply.ok(new JsonObject()));
            }
            default -> done(CodeReply.fail("invalid"));
        };
    }

    /** Compile on a worker, then save on the server thread (diagnostics come back with the new version). */
    private static CompletableFuture<CodeReply> save(MinecraftServer server, AuthStore.Device device, UUID mainframeId, ClientMessages.CodeCommand c,
                                                     @org.jspecify.annotations.Nullable ScriptRecord before) {
        var limits = CodeService.limits();
        String name = ScriptBook.validName(c.name());
        if (name == null) return done(CodeReply.fail("invalid", "name"));
        if (c.source().getBytes(StandardCharsets.UTF_8).length > limits.maxSource()) return done(CodeReply.fail("too_large", String.valueOf(limits.maxSource())));
        if (before != null && c.baseVersion() != null && c.baseVersion() != before.version()) {
            return done(CodeReply.fail("changed", String.valueOf(before.version())));
        }
        if (before == null && CodeService.book(mainframeId).size() >= limits.maxScripts()) return done(CodeReply.fail("too_many", String.valueOf(limits.maxScripts())));
        CompletableFuture<List<Diagnostic>> compiled = CodeService.enabled() ? CodeService.compile(name, c.source())
            : CompletableFuture.completedFuture(List.of());
        return compiled.exceptionally(e -> List.of()).thenApplyAsync(diagnostics -> {
            if (CodeService.server() != server) return CodeReply.fail("offline");
            var book = CodeService.book(mainframeId);
            var result = book.save(before == null ? null : before.id(), name, c.source(), c.baseVersion(), device.player(), device.playerName(),
                System.currentTimeMillis(), limits, CodeService::newId);
            if (result.error() != null) return CodeReply.fail(result.error(), result.detail());
            var record = result.script();
            var slot = CodeService.slot(mainframeId, record, limits);
            slot.checkedVersion = record.version();
            slot.diagnostics = List.copyOf(diagnostics);
            CodeService.changed(mainframeId);
            if (before == null) audit(server, mainframeId, device.playerName() + " created Cloud Script \"" + record.name() + "\"");
            var data = new JsonObject();
            data.addProperty("id", record.id());
            data.addProperty("version", record.version());
            data.add("diagnostics", diagnostics(diagnostics));
            return CodeReply.ok(data);
        }, server);
    }

    static JsonArray diagnostics(List<Diagnostic> list) {
        var array = new JsonArray();
        for (var d : list) {
            var o = new JsonObject();
            o.addProperty("line", d.line());
            if (d.column() != null) o.addProperty("column", d.column());
            else o.add("column", JsonNull.INSTANCE);
            o.addProperty("message", d.message());
            o.addProperty("severity", d.severity());
            array.add(o);
        }
        return array;
    }

    private static void audit(MinecraftServer server, UUID mainframeId, String text) {
        WebAuthData.access(server).audit(mainframeId, text);
    }

    private static CompletableFuture<CodeReply> done(CodeReply reply) {
        return CompletableFuture.completedFuture(reply);
    }
}
