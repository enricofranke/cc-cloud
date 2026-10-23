package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import ai.ezfox.cccloud.access.TeamProvider;
import ai.ezfox.cccloud.access.TeamProviders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The v0.6 HTTP API (docs/SPEC.md "v0.6 web protocol"): {@code /api/mainframes}, {@code /api/devices},
 * {@code /api/mainframes/<id>/access} and {@code /api/mainframes/<id>/shares}. Plain Java with injected game hooks so
 * it is unit tested without a server; in the mod it runs on the server thread. Bodies are strict: unknown fields,
 * wrong types and out-of-range values are rejected with {@code 400 invalid}.
 */
public final class AccessApi {
    public record Response(int status, JsonObject body) {
    }

    public record Known(UUID uuid, String name) {
    }

    /** Name ↔ UUID without blocking network lookups (online players and names the mod already knows). */
    public interface Players {
        @Nullable Known byName(String name);

        String name(UUID uuid);
    }

    /** Effects outside the stores (sessions, hub info). */
    public interface Hooks {
        /** Roles of a mainframe changed: re-check its open sessions now (role push / close). */
        void accessChanged(UUID mainframeId);

        void closeDevice(String tokenHash);

        void closeShare(String shareId, String reason);

        int shareViewers(String shareId);

        WebHub.@Nullable MainframeInfo info(UUID mainframeId);

        /** Every mainframe id the server knows (for account tokens). */
        Set<UUID> mainframes();

        void log(String message);
    }

    private static final Set<String> ROLE_IDS = Set.of("viewer", "operator", "admin");

    private final AuthStore auth;
    private final AccessResolver resolver;
    private final AccessStore store;
    private final Players players;
    private final Hooks hooks;
    private final Function<String, @Nullable TeamProvider> teams;
    private final java.util.function.Supplier<List<TeamProvider>> enabledTeams;

    public AccessApi(AuthStore auth, AccessResolver resolver, Players players, Hooks hooks) {
        this(auth, resolver, players, hooks, TeamProviders::get, TeamProviders::enabled);
    }

    public AccessApi(AuthStore auth, AccessResolver resolver, Players players, Hooks hooks, Function<String, @Nullable TeamProvider> teams,
                     java.util.function.Supplier<List<TeamProvider>> enabledTeams) {
        this.auth = auth;
        this.resolver = resolver;
        this.store = resolver.store();
        this.players = players;
        this.hooks = hooks;
        this.teams = teams;
        this.enabledTeams = enabledTeams;
    }

    /**
     * @param devices the valid devices of the request's Bearer tokens (at least one)
     */
    public Response handle(Router.Id route, List<AuthStore.Device> devices, String path, String body) {
        var device = devices.getFirst();
        return switch (route) {
            case PAIR_STATUS -> pairInfo(device);
            case MAINFRAMES -> mainframes(devices);
            case DEVICES -> devices(device);
            case DEVICE_DELETE -> deleteDevice(device, path.substring(path.lastIndexOf('/') + 1));
            case ACCESS_GET, ACCESS_POST, SHARES_GET, SHARES_POST, SHARE_DELETE -> mainframeApi(route, device, path, body);
            default -> error(404, "not_found");
        };
    }

    // ---- /api/mainframes ---------------------------------------------------------------------------------------------

    private Response mainframes(List<AuthStore.Device> devices) {
        var roles = new LinkedHashMap<UUID, Role>();
        AuthStore.Device account = null;
        for (var device : devices) {
            Set<UUID> candidates = device.mainframeId() != null ? Set.of(device.mainframeId()) : allMainframes();
            if (device.isAccount() && account == null) account = device;
            for (var id : candidates) {
                var role = resolver.deviceRole(device, id);
                if (role == Role.NONE) continue;
                roles.merge(id, role, Role::max);
            }
        }
        var list = new JsonArray();
        for (var e : roles.entrySet()) {
            var info = hooks.info(e.getKey());
            var json = new JsonObject();
            json.addProperty("id", e.getKey().toString());
            json.addProperty("name", info != null ? info.name() : "Mainframe");
            json.addProperty("online", info != null && info.online());
            json.addProperty("enabled", info != null && info.enabled());
            json.addProperty("role", e.getValue().id);
            json.addProperty("canControl", e.getValue().canControl());
            if (devices.size() == 1 && !devices.getFirst().isAccount()) json.addProperty("device", devices.getFirst().name());
            list.add(json);
        }
        var body = new JsonObject();
        body.add("mainframes", list);
        if (account != null) {
            var acc = new JsonObject();
            acc.addProperty("player", account.playerName());
            acc.addProperty("uuid", account.player().toString());
            body.add("account", acc);
        } else {
            body.add("account", JsonNull.INSTANCE);
        }
        return new Response(200, body);
    }

    /** Fields of an approved pairing (merged with status + token by the HTTP layer). */
    private Response pairInfo(AuthStore.Device device) {
        var json = new JsonObject();
        if (device.isAccount()) {
            var account = new JsonObject();
            account.addProperty("player", device.playerName());
            account.addProperty("uuid", device.player().toString());
            json.add("account", account);
            var list = new JsonArray();
            for (var id : allMainframes()) {
                var role = resolver.deviceRole(device, id);
                if (role == Role.NONE) continue;
                var info = hooks.info(id);
                var row = new JsonObject();
                row.addProperty("id", id.toString());
                row.addProperty("name", info != null ? info.name() : "Mainframe");
                row.addProperty("role", role.id);
                list.add(row);
            }
            json.add("mainframes", list);
        } else {
            var role = resolver.deviceRole(device, device.mainframeId());
            var info = hooks.info(device.mainframeId());
            json.addProperty("mainframeId", device.mainframeId().toString());
            json.addProperty("name", info != null ? info.name() : "Mainframe");
            json.addProperty("device", device.name());
            json.addProperty("canControl", role.canControl());
            json.addProperty("role", role.id);
        }
        return new Response(200, json);
    }

    private Set<UUID> allMainframes() {
        var set = new LinkedHashSet<>(store.mainframes());
        set.addAll(hooks.mainframes());
        return set;
    }

    // ---- /api/devices ------------------------------------------------------------------------------------------------

    private Response devices(AuthStore.Device current) {
        // Account tokens see every browser of their player; a mainframe-limited token only itself.
        var list = current.isAccount() ? auth.devicesOf(current.player()) : List.of(current);
        var array = new JsonArray();
        for (var d : list) {
            var json = new JsonObject();
            json.addProperty("id", d.id());
            json.addProperty("name", d.name());
            json.addProperty("type", d.type());
            json.addProperty("lastIp", d.lastIp());
            json.addProperty("lastSeen", d.lastUsed());
            json.addProperty("created", d.created());
            json.addProperty("current", d.tokenHash().equals(current.tokenHash()));
            if (d.mainframeId() == null) {
                json.add("mainframes", JsonNull.INSTANCE);
            } else {
                var ids = new JsonArray();
                ids.add(d.mainframeId().toString());
                json.add("mainframes", ids);
            }
            array.add(json);
        }
        var body = new JsonObject();
        body.add("devices", array);
        return new Response(200, body);
    }

    private Response deleteDevice(AuthStore.Device current, String id) {
        var removed = auth.revokeDevice(id, d -> d.tokenHash().equals(current.tokenHash()) || (current.isAccount() && d.player().equals(current.player())));
        if (removed == null) return error(404, "not_found");
        hooks.closeDevice(removed.tokenHash());
        hooks.log(current.playerName() + " revoked the browser '" + removed.name() + "' (web)");
        return ok();
    }

    // ---- /api/mainframes/<id>/(access|shares) -----------------------------------------------------------------------

    private Response mainframeApi(Router.Id route, AuthStore.Device device, String path, String body) {
        String[] parts = path.split("/");
        // /api/mainframes/<id>/<what>[/<shareId>]
        UUID mainframeId = ClientMessages.parseUuid(parts.length > 3 ? parts[3] : null);
        if (mainframeId == null) return error(404, "not_found");
        var resolution = resolver.resolveDevice(device, mainframeId);
        if (resolution.role() == Role.NONE) return error(403, "no_access");
        String denied = AccessGate.check(resolution.role(), AccessGate.Kind.MANAGE);
        if (denied != null) return error(403, denied);
        if (resolution.source() == AccessResolver.Source.SERVER_ADMIN) {
            store.audit(mainframeId, "Access settings opened by admin " + device.playerName());
        }
        return switch (route) {
            case ACCESS_GET -> new Response(200, accessDoc(mainframeId, device, resolution.role()));
            case ACCESS_POST -> accessPost(mainframeId, device, resolution.role(), body);
            case SHARES_GET -> new Response(200, shares(mainframeId));
            case SHARES_POST -> createShare(mainframeId, device, body);
            case SHARE_DELETE -> deleteShare(mainframeId, device, parts[parts.length - 1]);
            default -> error(404, "not_found");
        };
    }

    JsonObject accessDoc(UUID mainframeId, AuthStore.Device device, Role yourRole) {
        var doc = new JsonObject();
        var you = new JsonObject();
        you.addProperty("player", device.playerName());
        you.addProperty("role", yourRole.id);
        doc.add("you", you);
        var access = store.access(mainframeId);
        var rows = new LinkedHashMap<UUID, JsonObject>();
        if (access != null && access.owner() != null) {
            String name = access.ownerName().isEmpty() ? players.name(access.owner()) : access.ownerName();
            rows.put(access.owner(), playerRow(access.owner(), name, Role.OWNER, "owner", null));
        }
        if (access != null) {
            for (var e : access.grants().entrySet()) {
                if (rows.containsKey(e.getKey())) continue;
                String name = e.getValue().name().isEmpty() ? players.name(e.getKey()) : e.getValue().name();
                rows.put(e.getKey(), playerRow(e.getKey(), name, e.getValue().role(), "direct", null));
            }
        }
        JsonElement teamJson = JsonNull.INSTANCE;
        if (access != null && access.team() != null) {
            var binding = access.team();
            var provider = teams.apply(binding.provider());
            var team = provider == null ? null : provider.team(binding.teamId());
            var t = new JsonObject();
            t.addProperty("provider", binding.provider());
            t.addProperty("id", binding.teamId());
            t.addProperty("name", team != null ? team.name() : binding.teamId());
            var mapping = new JsonObject();
            for (var m : binding.mapping().entrySet()) mapping.addProperty(m.getKey(), m.getValue().id);
            t.add("mapping", mapping);
            teamJson = t;
            if (provider != null) {
                for (var member : provider.members(binding.teamId())) {
                    var role = binding.mapping().getOrDefault(member.rank(), Role.NONE);
                    if (role == Role.NONE) continue;
                    var existing = rows.get(member.uuid());
                    if (existing != null && Role.parse(existing.get("role").getAsString()).rank >= role.rank) continue;
                    rows.put(member.uuid(), playerRow(member.uuid(), member.name(), role, "team", member.rank()));
                }
            }
        }
        var list = new ArrayList<>(rows.values());
        list.sort((a, b) -> {
            int r = Role.parse(b.get("role").getAsString()).rank - Role.parse(a.get("role").getAsString()).rank;
            return r != 0 ? r : a.get("player").getAsString().compareToIgnoreCase(b.get("player").getAsString());
        });
        var array = new JsonArray();
        list.forEach(array::add);
        doc.add("players", array);
        doc.add("team", teamJson);
        // Teams the caller may assign: their own teams (server admins with cccloud.admin.manage: also the current one).
        var teamsArray = new JsonArray();
        for (var provider : enabledTeams.get()) {
            var seen = new LinkedHashSet<String>();
            for (var team : provider.teamsOf(device.player())) {
                if (!seen.add(team.id())) continue;
                teamsArray.add(teamRow(provider, team));
            }
            if (access != null && access.team() != null && access.team().provider().equals(provider.id()) && !seen.contains(access.team().teamId())) {
                var current = provider.team(access.team().teamId());
                if (current != null) teamsArray.add(teamRow(provider, current));
            }
        }
        doc.add("teams", teamsArray);
        var providers = new JsonArray();
        for (var provider : enabledTeams.get()) providers.add(provider.id());
        doc.add("providers", providers);
        return doc;
    }

    private static JsonObject playerRow(UUID uuid, String name, Role role, String source, @Nullable String rank) {
        var row = new JsonObject();
        row.addProperty("uuid", uuid.toString());
        row.addProperty("player", name);
        row.addProperty("role", role.id);
        row.addProperty("source", source);
        if (rank != null) row.addProperty("teamRank", rank);
        return row;
    }

    private static JsonObject teamRow(TeamProvider provider, TeamProvider.Team team) {
        var row = new JsonObject();
        row.addProperty("provider", provider.id());
        row.addProperty("id", team.id());
        row.addProperty("name", team.name());
        var ranks = new JsonArray();
        provider.ranks().forEach(ranks::add);
        row.add("ranks", ranks);
        return row;
    }

    private Response accessPost(UUID mainframeId, AuthStore.Device device, Role yourRole, String body) {
        var obj = parse(body);
        if (obj == null) return error(400, "invalid");
        String op = string(obj, "op");
        if (op == null) return error(400, "invalid");
        // Owners and moderators (cccloud.admin.manage) may change everyone; admins only players below them.
        boolean moderator = yourRole == Role.OWNER || resolver.permissions().has(device.player(), ServerPermissions.Node.ADMIN_MANAGE);
        String who = device.playerName();
        switch (op) {
            case "set_role", "remove" -> {
                if (!onlyKeys(obj, op.equals("set_role") ? Set.of("op", "player", "role") : Set.of("op", "player"))) return error(400, "invalid");
                String playerArg = string(obj, "player");
                if (playerArg == null || playerArg.isEmpty() || playerArg.length() > 36) return error(400, "invalid");
                Role role = Role.NONE;
                if (op.equals("set_role")) {
                    String roleArg = string(obj, "role");
                    if (roleArg == null || !ROLE_IDS.contains(roleArg)) return error(400, "invalid");
                    role = Role.parse(roleArg);
                }
                var target = resolvePlayer(playerArg);
                if (target == null) return error(400, "unknown_player");
                var access = store.access(mainframeId);
                if (access != null && target.uuid().equals(access.owner())) return error(400, "invalid");
                var currentGrant = access == null ? null : access.grants().get(target.uuid());
                if (!moderator && ((currentGrant != null && currentGrant.role().rank >= yourRole.rank) || role.rank > yourRole.rank)) {
                    return error(403, "not_allowed");
                }
                if (op.equals("remove") && currentGrant == null) return new Response(200, accessDoc(mainframeId, device, yourRole));
                String err = store.setRole(mainframeId, target.uuid(), target.name(), role);
                if (err != null) return error(400, err.equals("too_many") ? "too_many" : "invalid");
                store.audit(mainframeId, who + (role == Role.NONE ? " removed " + target.name() : " set " + target.name() + " to " + role.id));
            }
            case "set_team" -> {
                if (!onlyKeys(obj, Set.of("op", "provider", "teamId"))) return error(400, "invalid");
                var teamIdElement = obj.get("teamId");
                if (teamIdElement == null) return error(400, "invalid");
                if (teamIdElement.isJsonNull()) {
                    store.setTeam(mainframeId, null);
                    store.audit(mainframeId, who + " removed the team assignment");
                } else {
                    String providerId = string(obj, "provider");
                    String teamId = string(obj, "teamId");
                    if (providerId == null || teamId == null || teamId.isEmpty() || teamId.length() > 64) return error(400, "invalid");
                    var provider = teams.apply(providerId);
                    var team = provider == null ? null : provider.team(teamId);
                    if (team == null) return error(400, "invalid");
                    // Only teams the caller belongs to (moderators: any team).
                    if (!moderator && provider.rankOf(teamId, device.player()) == null) return error(403, "not_allowed");
                    store.setTeam(mainframeId, new MainframeAccess.TeamBinding(providerId, teamId, defaultMapping(provider)));
                    store.audit(mainframeId, who + " assigned the team " + team.name() + " (" + providerId + ")");
                }
            }
            case "set_mapping" -> {
                if (!onlyKeys(obj, Set.of("op", "rank", "role"))) return error(400, "invalid");
                String rank = string(obj, "rank");
                String roleArg = string(obj, "role");
                Role role = roleArg == null ? null : Role.parse(roleArg);
                if (rank == null || role == null || role == Role.OWNER) return error(400, "invalid");
                if (!moderator && role.rank > yourRole.rank) return error(403, "not_allowed");
                if (!store.setMapping(mainframeId, rank, role)) return error(400, "invalid");
                store.audit(mainframeId, who + " mapped the team rank " + rank + " to " + role.id);
            }
            default -> {
                return error(400, "invalid");
            }
        }
        hooks.accessChanged(mainframeId);
        hooks.log(who + " changed the access of " + mainframeId + ": " + op);
        // The caller's own role may have changed (e.g. removed themselves).
        var now = resolver.deviceRole(device, mainframeId);
        return new Response(200, accessDoc(mainframeId, device, now));
    }

    private static Map<String, Role> defaultMapping(TeamProvider provider) {
        var mapping = new LinkedHashMap<String, Role>();
        var defaults = provider.defaultMapping();
        for (String rank : provider.ranks()) mapping.put(rank, defaults.getOrDefault(rank, Role.NONE));
        return mapping;
    }

    private @Nullable Known resolvePlayer(String arg) {
        var uuid = ClientMessages.parseUuid(arg);
        if (uuid != null) {
            String name = players.name(uuid);
            return name.isEmpty() ? null : new Known(uuid, name);
        }
        if (!arg.matches("[A-Za-z0-9_]{1,16}")) return null;
        return players.byName(arg);
    }

    // ---- shares ------------------------------------------------------------------------------------------------------

    private JsonObject shares(UUID mainframeId) {
        var array = new JsonArray();
        for (var share : store.sharesOf(mainframeId)) {
            var json = new JsonObject();
            json.addProperty("id", share.id());
            json.addProperty("label", share.label());
            json.addProperty("createdBy", share.createdBy());
            json.addProperty("created", share.created());
            if (share.expires() == 0) json.add("expires", JsonNull.INSTANCE);
            else json.addProperty("expires", share.expires());
            json.addProperty("maxViewers", share.maxViewers());
            json.addProperty("viewers", hooks.shareViewers(share.id()));
            var pages = new JsonArray();
            share.pages().forEach(pages::add);
            json.add("pages", pages);
            var cards = new JsonArray();
            share.cards().forEach(cards::add);
            json.add("cards", cards);
            json.addProperty("hideNumbers", share.hideNumbers());
            array.add(json);
        }
        var body = new JsonObject();
        body.add("shares", array);
        return body;
    }

    private Response createShare(UUID mainframeId, AuthStore.Device device, String body) {
        var obj = parse(body);
        if (obj == null || !onlyKeys(obj, Set.of("label", "pages", "cards", "expiresIn", "maxViewers", "hideNumbers"))) return error(400, "invalid");
        String label = obj.has("label") ? string(obj, "label") : "";
        if (label == null) return error(400, "invalid");
        label = AuthStore.sanitizeName(label.isBlank() ? "Shared view" : label);
        var pages = stringList(obj.get("pages"));
        var cards = stringList(obj.get("cards"));
        if (pages == null || cards == null) return error(400, "invalid");
        Long expiresIn;
        var exp = obj.get("expiresIn");
        if (exp == null || exp.isJsonNull()) expiresIn = null;
        else if (exp.isJsonPrimitive() && exp.getAsJsonPrimitive().isNumber() && exp.getAsDouble() == Math.rint(exp.getAsDouble())) expiresIn = exp.getAsLong();
        else return error(400, "invalid");
        var max = obj.get("maxViewers");
        if (max == null || !max.isJsonPrimitive() || !max.getAsJsonPrimitive().isNumber() || max.getAsDouble() != Math.rint(max.getAsDouble())) return error(400, "invalid");
        int maxViewers = (int) Math.max(-1, Math.min(1000, max.getAsDouble()));
        var hide = obj.get("hideNumbers");
        if (hide != null && !(hide.isJsonPrimitive() && hide.getAsJsonPrimitive().isBoolean())) return error(400, "invalid");
        boolean hideNumbers = hide != null && hide.getAsBoolean();
        String invalid = store.validateShare(mainframeId, label, expiresIn, maxViewers, pages, cards);
        if (invalid != null) return error(400, invalid);
        var created = store.createShare(mainframeId, label, device.playerName(), expiresIn, maxViewers, pages, cards, hideNumbers);
        store.audit(mainframeId, device.playerName() + " created the share link '" + label + "'");
        hooks.log(device.playerName() + " created share link " + created.share().id() + " for " + mainframeId);
        var json = new JsonObject();
        json.addProperty("id", created.share().id());
        json.addProperty("secret", created.secret());
        json.addProperty("url", "/s/" + created.share().id() + "#" + created.secret());
        return new Response(200, json);
    }

    private Response deleteShare(UUID mainframeId, AuthStore.Device device, String shareId) {
        var removed = store.revokeShare(mainframeId, shareId);
        if (removed == null) return error(404, "not_found");
        hooks.closeShare(removed.id(), "share_revoked");
        store.audit(mainframeId, device.playerName() + " revoked the share link '" + removed.label() + "'");
        return ok();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private static @Nullable JsonObject parse(String body) {
        if (body.length() > ClientMessages.MAX_BODY) return null;
        try {
            var element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean onlyKeys(JsonObject obj, Set<String> allowed) {
        for (String key : obj.keySet()) if (!allowed.contains(key)) return false;
        return true;
    }

    private static @Nullable String string(JsonObject obj, String key) {
        var e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** A JSON array of strings (absent = empty), or null if it is anything else. */
    private static @Nullable List<String> stringList(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return List.of();
        if (!e.isJsonArray() || e.getAsJsonArray().size() > 256) return null;
        var list = new ArrayList<String>();
        for (var item : e.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return null;
            if (!list.contains(item.getAsString())) list.add(item.getAsString());
        }
        return list;
    }

    private static Response ok() {
        var json = new JsonObject();
        json.addProperty("ok", true);
        return new Response(200, json);
    }

    static Response error(int status, String code) {
        var json = new JsonObject();
        json.addProperty("error", code);
        return new Response(status, json);
    }
}
