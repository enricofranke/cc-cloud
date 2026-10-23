package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.script.ComputerTarget;
import ai.ezfox.cccloud.script.ScriptFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Script import (docs/SPEC.md): list/read/write/delete files of the computer behind an uplink card and run an uploaded
 * file. Admin role, PIN (checked before, off the server thread), audit log and a chat note to the owner. Server thread.
 */
public final class ScriptApi {
    private ScriptApi() {
    }

    public static AccessApi.Response handle(MinecraftServer server, GameBridge.ScriptRequest request) {
        var device = request.device();
        var role = Access.resolver(server).deviceRole(device, request.mainframeId());
        if (role == Role.NONE) return AccessApi.error(403, "no_access");
        String denied = AccessGate.check(role, AccessGate.Kind.MANAGE);
        if (denied != null) return AccessApi.error(403, denied);
        var mainframe = CloudNetwork.mainframe(request.mainframeId());
        if (mainframe == null || !mainframe.isOnline()) return AccessApi.error(409, "offline");
        var section = CloudNetwork.section(request.uplinkId());
        if (!(section instanceof CloudUplinkBlockEntity uplink) || section.getLink() == null || !section.getLink().id().equals(request.mainframeId())) {
            return AccessApi.error(404, "unknown_computer");
        }
        var target = ComputerTarget.of(uplink);
        if (target == null) return AccessApi.error(404, "unknown_computer");
        var store = target.store(server);
        try {
            return switch (request.route()) {
                case SCRIPT_LIST -> list(target, store);
                case SCRIPT_GET -> read(store, request.file());
                case SCRIPT_PUT -> write(server, request, target, store, mainframe.getDisplayName());
                case SCRIPT_DELETE -> delete(server, request, target, store);
                case SCRIPT_RUN -> run(server, request, target, store);
                default -> AccessApi.error(404, "not_found");
            };
        } catch (IOException e) {
            CloudLog.LOGGER.warn("[cccloud] Script import on computer #{} failed: {}", target.id(), e.toString());
            return AccessApi.error(500, "io_error");
        }
    }

    private static AccessApi.Response list(ComputerTarget target, ScriptFiles.Store store) throws IOException {
        var json = new JsonObject();
        var computer = new JsonObject();
        computer.addProperty("id", target.id());
        if (target.label() != null) computer.addProperty("label", target.label());
        computer.addProperty("on", target.isOn());
        computer.addProperty("advanced", target.advanced());
        json.add("computer", computer);
        json.addProperty("capacity", store.capacity());
        json.addProperty("used", Math.max(0, store.capacity() - store.remaining()));
        var files = new JsonArray();
        for (var e : ScriptFiles.list(store)) {
            var f = new JsonObject();
            f.addProperty("path", e.path());
            f.addProperty("size", e.size());
            f.addProperty("dir", e.dir());
            files.add(f);
        }
        json.add("files", files);
        return new AccessApi.Response(200, json);
    }

    private static AccessApi.Response read(ScriptFiles.Store store, String file) throws IOException {
        String path = ScriptFiles.validate(file);
        if (path == null) return AccessApi.error(400, "invalid_path");
        byte[] data = ScriptFiles.read(store, path);
        if (data == null) return AccessApi.error(404, "not_found");
        return new AccessApi.Response(200, new JsonObject(), data);
    }

    private static AccessApi.Response write(MinecraftServer server, GameBridge.ScriptRequest request, ComputerTarget target, ScriptFiles.Store store,
                                            String mainframeName) throws IOException {
        String path = ScriptFiles.validate(request.file());
        if (path == null) return AccessApi.error(400, "invalid_path");
        // Text only: the body must be valid UTF-8 (Lua sources); binary uploads are refused.
        var decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(request.body()));
        } catch (java.nio.charset.CharacterCodingException e) {
            return AccessApi.error(400, "not_text");
        }
        boolean overwrite = "true".equals(request.query().get("overwrite"));
        var result = ScriptFiles.write(store, path, request.body(), overwrite);
        if (result.error() != null) return AccessApi.error(result.status(), result.error());
        boolean startup = "true".equals(request.query().get("startup"));
        if (startup && !path.equals("startup.lua")) {
            var s = ScriptFiles.write(store, "startup.lua", ScriptFiles.startupFor(path), true);
            if (s.error() != null) return AccessApi.error(s.status(), s.error());
        }
        audit(server, request, target, "uploaded " + path + (startup ? " (startup)" : ""), mainframeName);
        var json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("path", path);
        json.addProperty("size", result.size());
        return new AccessApi.Response(200, json);
    }

    private static AccessApi.Response delete(MinecraftServer server, GameBridge.ScriptRequest request, ComputerTarget target, ScriptFiles.Store store) throws IOException {
        String path = ScriptFiles.validate(request.file());
        if (path == null) return AccessApi.error(400, "invalid_path");
        var result = ScriptFiles.delete(store, path);
        if (result.error() != null) return AccessApi.error(result.status(), result.error());
        audit(server, request, target, "deleted " + path, null);
        var json = new JsonObject();
        json.addProperty("ok", true);
        return new AccessApi.Response(200, json);
    }

    private static AccessApi.Response run(MinecraftServer server, GameBridge.ScriptRequest request, ComputerTarget target, ScriptFiles.Store store) throws IOException {
        JsonObject body;
        try {
            var e = JsonParser.parseString(new String(request.body(), StandardCharsets.UTF_8));
            body = e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            body = null;
        }
        if (body == null || request.body().length > ClientMessages.MAX_BODY) return AccessApi.error(400, "invalid");
        for (String key : body.keySet()) if (!key.equals("path") && !key.equals("mode")) return AccessApi.error(400, "invalid");
        String path = body.get("path") != null && body.get("path").isJsonPrimitive() ? ScriptFiles.validate(body.get("path").getAsString()) : null;
        if (path == null) return AccessApi.error(400, "invalid_path");
        if (!store.exists(path) || store.isDirectory(path)) return AccessApi.error(404, "not_found");
        String mode = body.get("mode") != null && body.get("mode").isJsonPrimitive() ? body.get("mode").getAsString() : "auto";
        if (mode.equals("auto")) {
            if (!target.advanced()) return AccessApi.error(409, "needs_reboot");
            // The background runner tab only exists on computers that asked for it: the first run writes .cloud/runner
            // and restarts the computer (the autorun helper then runs the file and starts the runner); later runs are events.
            mode = store.exists(ScriptFiles.RESERVED + "/runner") ? "event" : "reboot";
        }
        switch (mode) {
            case "event" -> target.queueRun(path);
            case "reboot" -> {
                if (target.advanced() && !store.exists(ScriptFiles.RESERVED + "/runner")) {
                    var runner = ScriptFiles.write(store, ScriptFiles.RESERVED + "/runner", "on demand".getBytes(StandardCharsets.UTF_8), true);
                    if (runner.error() != null) return AccessApi.error(runner.status(), runner.error());
                }
                // The ROM autorun helper runs .cloud/run_next once after boot.
                var marker = ScriptFiles.write(store, ScriptFiles.RESERVED + "/run_next", path.getBytes(StandardCharsets.UTF_8), true);
                if (marker.error() != null) return AccessApi.error(marker.status(), marker.error());
                target.reboot();
            }
            default -> {
                return AccessApi.error(400, "invalid");
            }
        }
        audit(server, request, target, "ran " + path + " (" + mode + ")", null);
        var json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("started", mode);
        return new AccessApi.Response(200, json);
    }

    private static void audit(MinecraftServer server, GameBridge.ScriptRequest request, ComputerTarget target, String what, @org.jspecify.annotations.Nullable String mainframeName) {
        var device = request.device();
        String text = device.name() + " (" + device.playerName() + ") " + what + " on " + target.title();
        WebAuthData.access(server).audit(request.mainframeId(), text);
        CloudLog.LOGGER.info("[cccloud] Script import: {} from {}", text, request.ip());
        var access = WebAuthData.access(server).access(request.mainframeId());
        if (access == null || access.owner() == null) return;
        var owner = server.getPlayerList().getPlayer(access.owner());
        if (owner != null) owner.sendSystemMessage(Component.translatable("cccloud.script.note", device.name(), device.playerName(), what, target.title())
            .withStyle(ChatFormatting.GRAY));
    }
}
