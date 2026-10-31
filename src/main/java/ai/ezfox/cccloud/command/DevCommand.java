package ai.ezfox.cccloud.command;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.analyzer.AnalyzerService;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.FluidTankCard;
import ai.ezfox.cccloud.compat.card.ItemStorageCard;
import ai.ezfox.cccloud.compat.ftbchunks.DevClaims;
import ai.ezfox.cccloud.display.CloudDisplayBlockEntity;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGameSession;
import ai.ezfox.cccloud.map.MapNodesService;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Test world helpers for {@code tools/e2e} ({@code -Dcccloud.devCommands=true} only, console and RCON only): what a
 * player would do by hand on a server nobody can join. Never registered on a normal server.
 * <ul>
 *   <li>{@code namespaces}, {@code catalog <pos> <namespace> <skip,skip>}: places a mod's block entity blocks at {@code pos} for
 *       a moment and answers which ready-made card an uplink would show.</li>
 *   <li>{@code fe|fluid|item <pos> ...}: fills a machine through its capabilities.</li>
 *   <li>{@code uuid|party|ally|claim|unclaim ...}: scripted players ({@link #player}) with FTB Teams and FTB Chunks.</li>
 *   <li>{@code own|role|token ...}: a mainframe's owner, a player's role on a mainframe, a device token as if the player had paired a browser.</li>
 *   <li>{@code analyzer|mapnodes|tablet ...}: what the player's Cloud Analyzer, map mod and Cloud Tablet would get, as JSON.</li>
 *   <li>{@code display <pos>}: what a Cloud Display shows.</li>
 * </ul>
 */
public final class DevCommand {
    /** The block the catalog is at (server folder). */
    static final String CATALOG_FILE = "e2e-catalog-progress.txt";
    private static final Direction[] SIDES = Direction.values();

    private DevCommand() {
    }

    /** The console and RCON, never a player or a command block. */
    static boolean allowed(CommandSourceStack src) {
        return Boolean.getBoolean("cccloud.devCommands") && src.getEntity() == null
            && (src.source == src.getServer() || src.source instanceof RconConsoleSource);
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("dev").requires(DevCommand::allowed)
            .then(Commands.literal("namespaces").executes(DevCommand::namespaces))
            .then(Commands.literal("catalog").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("namespace", StringArgumentType.word())
                    .then(Commands.argument("skip", StringArgumentType.greedyString()).executes(DevCommand::catalog)))))
            .then(Commands.literal("fe").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(DevCommand::fe))))
            .then(Commands.literal("fluid").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("id", IdentifierArgument.id())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(DevCommand::fluid)))))
            .then(Commands.literal("item").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("id", IdentifierArgument.id())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(DevCommand::item)))))
            .then(Commands.literal("uuid").then(Commands.argument("player", StringArgumentType.word()).executes(DevCommand::uuid)))
            .then(Commands.literal("party").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("name", StringArgumentType.word()).executes(DevCommand::party))))
            .then(Commands.literal("ally").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("other", StringArgumentType.word())
                    .then(Commands.argument("on", BoolArgumentType.bool()).executes(DevCommand::ally)))))
            .then(Commands.literal("claim").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> claim(c, true)))))
            .then(Commands.literal("unclaim").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> claim(c, false)))))
            .then(Commands.literal("own").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("player", StringArgumentType.word()).executes(DevCommand::own))))
            .then(Commands.literal("role").then(Commands.argument("mainframe", StringArgumentType.word())
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("role", StringArgumentType.word()).executes(DevCommand::role)))))
            .then(Commands.literal("token").then(Commands.argument("mainframe", StringArgumentType.word())
                .then(Commands.argument("player", StringArgumentType.word()).executes(DevCommand::token))))
            .then(Commands.literal("analyzer").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(DevCommand::analyzer))))
            .then(Commands.literal("mapnodes").then(Commands.argument("player", StringArgumentType.word()).executes(DevCommand::mapNodes)))
            .then(Commands.literal("tablet").then(Commands.argument("mainframe", StringArgumentType.word())
                .then(Commands.argument("player", StringArgumentType.word()).executes(DevCommand::tablet))))
            .then(Commands.literal("display").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(DevCommand::display)));
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text));
    }

    // ---- catalog -----------------------------------------------------------------------------------------------------

    /** The namespaces with block entity blocks, so the catalog can be asked mod by mod (a broken block stops one mod only). */
    private static int namespaces(CommandContext<CommandSourceStack> c) {
        var counts = new java.util.TreeMap<String, Integer>();
        for (var block : BuiltInRegistries.BLOCK) {
            if (block instanceof EntityBlock) counts.merge(BuiltInRegistries.BLOCK.getKey(block).getNamespace(), 1, Integer::sum);
        }
        var out = new JsonObject();
        counts.forEach(out::addProperty);
        reply(c.getSource(), "json " + out);
        return 1;
    }

    private static int catalog(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var source = c.getSource();
        var level = source.getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        var namespace = StringArgumentType.getString(c, "namespace");
        var skip = java.util.Set.of(StringArgumentType.getString(c, "skip").split(","));
        var rows = new JsonArray();
        int tried = 0;
        var progress = source.getServer().getServerDirectory().resolve(CATALOG_FILE);
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!(block instanceof EntityBlock) || !id.getNamespace().equals(namespace) || skip.contains(id.getPath())) continue;
            tried++;
            try {
                Files.writeString(progress, id.toString(), StandardCharsets.UTF_8); // what to skip when a block hangs the server
            } catch (IOException e) {
                CloudLog.LOGGER.warn("[cccloud] Dev catalog: {}", e.toString());
            }
            var row = probe(level, pos, block);
            if (row != null) rows.add(row);
        }
        var out = new JsonObject();
        out.addProperty("tried", tried);
        out.add("blocks", rows);
        reply(source, "json " + out);
        return rows.size();
    }

    /** One line for the block if an uplink on any of its faces would show a card, else null. */
    private static @Nullable JsonObject probe(ServerLevel level, BlockPos pos, Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block).toString();
        var flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        try {
            level.setBlock(pos, block.defaultBlockState(), flags);
            if (level.getBlockEntity(pos) == null) return null;
            String kind = null;
            String title = "";
            int widgets = 0;
            var sides = new JsonArray();
            for (var side : SIDES) {
                var card = DeviceCards.detect(level, pos, side);
                if (card == null) continue;
                sides.add(side.getName());
                if (kind == null) {
                    kind = card.kind();
                    title = DeviceCards.title(card);
                    var list = DeviceCards.widgets(card);
                    widgets = list == null ? -1 : list.size();
                }
            }
            if (kind == null) return null;
            var row = new JsonObject();
            row.addProperty("block", id);
            row.addProperty("kind", kind);
            row.addProperty("title", title);
            row.addProperty("widgets", widgets);
            row.add("sides", sides);
            row.addProperty("fe", level.getCapability(Capabilities.Energy.BLOCK, pos, null) != null || anySide(level, pos));
            row.addProperty("fluid", FluidTankCard.handler(level, pos, Direction.UP) != null);
            row.addProperty("items", ItemStorageCard.handler(level, pos, Direction.UP) != null);
            return row;
        } catch (LinkageError | RuntimeException e) {
            CloudLog.LOGGER.warn("[cccloud] Dev catalog skipped {}: {}", id, e.toString());
            return null;
        } finally {
            try {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            } catch (LinkageError | RuntimeException e) {
                CloudLog.LOGGER.warn("[cccloud] Dev catalog could not remove {}: {}", id, e.toString());
            }
        }
    }

    private static boolean anySide(ServerLevel level, BlockPos pos) {
        for (var side : SIDES) if (level.getCapability(Capabilities.Energy.BLOCK, pos, side) != null) return true;
        return false;
    }

    // ---- scripted players --------------------------------------------------------------------------------------------

    /** The fixed profile of a scripted player: the same name is the same player in every run. */
    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("cccloud-e2e:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }

    /** A scripted player on this server (with its FTB team when FTB Teams is there), standing in the command's level. */
    private static ServerPlayer player(ServerLevel level, String name) {
        var profile = profile(name);
        if (ModList.get().isLoaded("ftbchunks")) return DevClaims.player(level, profile);
        return FakePlayerFactory.get(level, profile);
    }

    private static int uuid(CommandContext<CommandSourceStack> c) {
        var name = StringArgumentType.getString(c, "player");
        player(c.getSource().getLevel(), name);
        reply(c.getSource(), "uuid " + profile(name).id());
        return 1;
    }

    private static int party(CommandContext<CommandSourceStack> c) {
        try {
            reply(c.getSource(), "party " + DevClaims.party(player(c.getSource().getLevel(), StringArgumentType.getString(c, "player")), StringArgumentType.getString(c, "name")));
            return 1;
        } catch (Exception e) {
            reply(c.getSource(), "failed: " + e);
            return 0;
        }
    }

    private static int ally(CommandContext<CommandSourceStack> c) {
        var level = c.getSource().getLevel();
        try {
            DevClaims.ally(player(level, StringArgumentType.getString(c, "player")), player(level, StringArgumentType.getString(c, "other")), BoolArgumentType.getBool(c, "on"));
            reply(c.getSource(), "ok");
            return 1;
        } catch (Exception e) {
            reply(c.getSource(), "failed: " + e);
            return 0;
        }
    }

    private static int claim(CommandContext<CommandSourceStack> c, boolean claim) throws CommandSyntaxException {
        var level = c.getSource().getLevel();
        var player = player(level, StringArgumentType.getString(c, "player"));
        var chunk = ChunkPos.containing(BlockPosArgument.getBlockPos(c, "pos"));
        if (claim) {
            reply(c.getSource(), "claim " + chunk + " " + DevClaims.claim(player, level, chunk));
        } else {
            DevClaims.unclaim(player, level, chunk);
            reply(c.getSource(), "unclaimed " + chunk);
        }
        return 1;
    }

    /** Hands a mainframe to a scripted player and applies the upgrades written with {@code data merge}, like a chunk load. */
    private static int own(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var level = c.getSource().getLevel();
        if (!(level.getBlockEntity(BlockPosArgument.getLoadedBlockPos(c, "pos")) instanceof ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity mainframe)) {
            reply(c.getSource(), "failed: no mainframe");
            return 0;
        }
        mainframe.setOwner(player(level, StringArgumentType.getString(c, "player")));
        mainframe.refreshEffects();
        reply(c.getSource(), "ok");
        return 1;
    }

    private static int role(CommandContext<CommandSourceStack> c) {
        var server = c.getSource().getServer();
        var name = StringArgumentType.getString(c, "player");
        player(c.getSource().getLevel(), name);
        var role = Role.parse(StringArgumentType.getString(c, "role"));
        if (role == null) {
            reply(c.getSource(), "failed: unknown role");
            return 0;
        }
        var error = WebAuthData.access(server).setRole(UUID.fromString(StringArgumentType.getString(c, "mainframe")), profile(name).id(), name, role);
        reply(c.getSource(), error == null ? "ok" : "failed: " + error);
        return error == null ? 1 : 0;
    }

    private static int token(CommandContext<CommandSourceStack> c) {
        var server = c.getSource().getServer();
        var name = StringArgumentType.getString(c, "player");
        player(c.getSource().getLevel(), name);
        var mainframe = UUID.fromString(StringArgumentType.getString(c, "mainframe"));
        var auth = WebAuthData.get(server);
        var id = profile(name).id();
        var pending = auth.pair(auth.currentCode(mainframe, id).code(), "e2e-" + name, "127.0.0.1");
        var approved = pending == null ? null : auth.approve(pending.id(), id, name, true);
        reply(c.getSource(), approved == null ? "failed: no token" : "token " + approved.token());
        return approved == null ? 0 : 1;
    }

    private static int analyzer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var player = player(c.getSource().getLevel(), StringArgumentType.getString(c, "player"));
        var pos = BlockPosArgument.getBlockPos(c, "pos").getCenter();
        player.setPos(pos.x, pos.y, pos.z);
        reply(c.getSource(), "json " + AnalyzerService.build(player));
        return 1;
    }

    private static int mapNodes(CommandContext<CommandSourceStack> c) {
        reply(c.getSource(), "json " + MapNodesService.build(player(c.getSource().getLevel(), StringArgumentType.getString(c, "player"))));
        return 1;
    }

    /** The messages an opened tablet gets right away: the same session class the Cloud Tablet uses. */
    /** Tablets opened by {@link #tablet}: the snapshot is written on another thread, the second call reads it. */
    private static final Map<String, InGameSession> TABLETS = new HashMap<>();

    /** Call twice, a moment apart: opens a Cloud Tablet session of the player, then answers what it received and closes it. */
    private static int tablet(CommandContext<CommandSourceStack> c) {
        var player = player(c.getSource().getLevel(), StringArgumentType.getString(c, "player"));
        var mainframe = UUID.fromString(StringArgumentType.getString(c, "mainframe"));
        var access = InGameAccess.resolve(player, mainframe);
        var out = new JsonObject();
        out.addProperty("role", access.role().id);
        var key = mainframe + "/" + player.getUUID();
        var session = TABLETS.remove(key);
        if (session == null) {
            session = new InGameSession(mainframe, InGameClients.device(player, mainframe, "tablet"), access.role(), access.perms());
            WebHub.INSTANCE.addSession(session);
            TABLETS.put(key, session);
            out.addProperty("opened", true);
            reply(c.getSource(), "json " + out);
            return 1;
        }
        WebHub.INSTANCE.flush();
        var messages = new JsonArray();
        var list = session.drainMessages();
        if (list != null) list.forEach(messages::add);
        WebHub.INSTANCE.removeSession(session);
        out.add("messages", messages);
        reply(c.getSource(), "json " + out);
        return 1;
    }

    private static int display(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof CloudDisplayBlockEntity display)) {
            reply(c.getSource(), "failed: no display");
            return 0;
        }
        var origin = display.origin();
        reply(c.getSource(), "json " + (origin == null ? "{}" : origin.content()));
        return 1;
    }

    // ---- filling machines --------------------------------------------------------------------------------------------

    private static int fe(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var level = c.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        int left = IntegerArgumentType.getInteger(c, "amount");
        int total = 0;
        for (var side : allSides()) {
            var handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
            if (handler == null || left <= 0) continue;
            try (var tx = Transaction.openRoot()) {
                int done = handler.insert(left, tx);
                tx.commit();
                total += done;
                left -= done;
            }
        }
        reply(c.getSource(), "inserted " + total);
        return total;
    }

    private static int fluid(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var level = c.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        var fluid = BuiltInRegistries.FLUID.getValue(IdentifierArgument.getId(c, "id"));
        int left = IntegerArgumentType.getInteger(c, "amount");
        int total = 0;
        for (var side : allSides()) {
            var handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
            if (handler == null || left <= 0) continue;
            try (var tx = Transaction.openRoot()) {
                int done = handler.insert(FluidResource.of(fluid), left, tx);
                tx.commit();
                total += done;
                left -= done;
            }
        }
        reply(c.getSource(), "inserted " + total);
        return total;
    }

    private static int item(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var level = c.getSource().getLevel();
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        var item = BuiltInRegistries.ITEM.getValue(IdentifierArgument.getId(c, "id"));
        int left = IntegerArgumentType.getInteger(c, "amount");
        int total = 0;
        for (var side : allSides()) {
            var handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
            if (handler == null || left <= 0) continue;
            try (var tx = Transaction.openRoot()) {
                int done = handler.insert(ItemResource.of(item), left, tx);
                tx.commit();
                total += done;
                left -= done;
            }
        }
        reply(c.getSource(), "inserted " + total);
        return total;
    }

    /** Null first (the block's own inventory view), then every face: machines often take things on some faces only. */
    private static Direction[] allSides() {
        var all = new Direction[SIDES.length + 1];
        System.arraycopy(SIDES, 0, all, 1, SIDES.length);
        return all;
    }
}
