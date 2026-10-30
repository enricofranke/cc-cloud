package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Opening the in-game screens, their custom payloads and live updates.
 * <p>
 * Client → server: {@link MainframeActionPayload}, {@link UplinkActionPayload}. Every packet is checked on the server
 * thread: the player must have the matching menu open (same container id, still in range), and the rights are checked
 * live ({@link GuiActions}). Server → client: {@link ViewPayload} with the view JSON, sent when it changes (checked
 * every 10 ticks) and right after an action (with a notice).
 */
public final class GuiNetwork {
    private static final int UPDATE_INTERVAL = 10;

    public record ViewPayload(int containerId, String view) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "gui_view"));
        public static final StreamCodec<ByteBuf, ViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ViewPayload::containerId, ByteBufCodecs.stringUtf8(CloudMenu.MAX_VIEW), ViewPayload::view, ViewPayload::new);

        @Override
        public Type<ViewPayload> type() {
            return TYPE;
        }
    }

    public record MainframeActionPayload(int containerId, String action, String arg) implements CustomPacketPayload {
        public static final Type<MainframeActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "mainframe_action"));
        public static final StreamCodec<ByteBuf, MainframeActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MainframeActionPayload::containerId, ByteBufCodecs.stringUtf8(32), MainframeActionPayload::action,
            ByteBufCodecs.stringUtf8(GuiActions.MAX_ARG), MainframeActionPayload::arg, MainframeActionPayload::new);

        @Override
        public Type<MainframeActionPayload> type() {
            return TYPE;
        }
    }

    public record UplinkActionPayload(int containerId, String action, String arg) implements CustomPacketPayload {
        public UplinkActionPayload(int containerId, String action) {
            this(containerId, action, "");
        }

        public static final Type<UplinkActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "uplink_action"));
        public static final StreamCodec<ByteBuf, UplinkActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UplinkActionPayload::containerId, ByteBufCodecs.stringUtf8(32), UplinkActionPayload::action,
            ByteBufCodecs.stringUtf8(GuiActions.MAX_ARG), UplinkActionPayload::arg, UplinkActionPayload::new);

        @Override
        public Type<UplinkActionPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client entry point; handles {@link ViewPayload} on the client. */
    public static volatile @Nullable Consumer<ViewPayload> clientViewHandler;

    private GuiNetwork() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(GuiNetwork::register);
        NeoForge.EVENT_BUS.addListener(GuiNetwork::onServerTick);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, (payload, context) -> {
            var handler = clientViewHandler;
            if (handler != null) handler.accept(payload);
        });
        registrar.playToServer(MainframeActionPayload.TYPE, MainframeActionPayload.CODEC, GuiNetwork::onMainframeAction);
        registrar.playToServer(UplinkActionPayload.TYPE, UplinkActionPayload.CODEC, GuiNetwork::onUplinkAction);
    }

    // ---- opening ---------------------------------------------------------------------------------------------------

    public static void openMainframe(ServerPlayer player, CloudMainframeBlockEntity mainframe) {
        var resolution = ai.ezfox.cccloud.access.Access.resolve(player, mainframe.getMainframeId());
        if (resolution.source() == ai.ezfox.cccloud.access.AccessResolver.Source.SERVER_ADMIN && player.level().getServer() != null) {
            ai.ezfox.cccloud.access.Access.auditAdmin(player.level().getServer(), mainframe.getMainframeId(), player.getGameProfile().name(), "Screen opened by admin");
        }
        long[] netV = {-1};
        String view = GuiViews.mainframe(mainframe, player, null, -1, netV);
        var pos = mainframe.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MainframeMenu(id, inv, pos, view, mainframe.upgrades(),
                who -> who instanceof ServerPlayer sp && mainframe.has(sp, ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE)),
                Component.translatable("block.cccloud.cloud_mainframe")),
            buf -> writeOpen(buf, pos, view));
        if (player.containerMenu instanceof MainframeMenu menu) menu.sentNetV = netV[0];
        sendLargeView(player, view);
    }

    public static void openUplink(ServerPlayer player, CloudUplinkBlockEntity uplink) {
        String view = GuiViews.uplink(uplink, player, null);
        var pos = uplink.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new UplinkMenu(id, pos, view), uplink.getBlockState().getBlock().getName()),
            buf -> writeOpen(buf, pos, view));
        sendLargeView(player, view);
    }

    public static void openBridge(ServerPlayer player, ai.ezfox.cccloud.bridge.BridgeHost bridge) {
        String view = GuiViews.bridge(bridge, player, null);
        var be = bridge.asBlockEntity();
        var pos = be.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new UplinkMenu(id, pos, view), be.getBlockState().getBlock().getName()),
            buf -> writeOpen(buf, pos, view));
        sendLargeView(player, view);
    }

    /** The relay screen (read-only: what it does, its networks, power). */
    public static void openRelay(ServerPlayer player, ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay) {
        String view = GuiViews.relay(relay, player, null);
        var pos = relay.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new UplinkMenu(id, pos, view), relay.getBlockState().getBlock().getName()),
            buf -> writeOpen(buf, pos, view));
        sendLargeView(player, view);
    }

    public static void writeOpen(RegistryFriendlyByteBuf buf, net.minecraft.core.BlockPos pos, String view) {
        buf.writeBlockPos(pos);
        buf.writeUtf(view.length() <= CloudMenu.OPEN_VIEW ? view : "{}", CloudMenu.MAX_VIEW);
    }

    /** After {@code openMenu}: a view too big for the open packet follows as a view update. */
    public static void sendLargeView(ServerPlayer player, String view) {
        if (view.length() <= CloudMenu.OPEN_VIEW || !(player.containerMenu instanceof CloudMenu menu)) return;
        if (view.length() > CloudMenu.MAX_VIEW) {
            CCCloud.LOGGER.warn("[cccloud] Screen view for {} is too big ({} chars); not sent", player.getGameProfile().name(), view.length());
            return;
        }
        menu.lastSent = view;
        PacketDistributor.sendToPlayer(player, new ViewPayload(menu.containerId, view));
    }

    /** True if this player currently has the screen of this mainframe open. */
    public static boolean hasScreenOpen(ServerPlayer player, UUID mainframeId) {
        return player.containerMenu instanceof MainframeMenu menu
            && player.level().getBlockEntity(menu.pos()) instanceof CloudMainframeBlockEntity m && m.getMainframeId().equals(mainframeId);
    }

    // ---- rights ----------------------------------------------------------------------------------------------------

    /** Link/unlink devices of a mainframe ({@code network.link}), also when the mainframe is not loaded. */
    public static boolean canManageMainframe(ServerPlayer player, UUID mainframeId) {
        return ai.ezfox.cccloud.access.Access.has(player, mainframeId, ai.ezfox.cccloud.access.Perm.NETWORK_LINK);
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    private static void onMainframeAction(MainframeActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ai.ezfox.cccloud.ingame.PacketLimiter.SCREEN_ACTIONS.allow(player.getUUID())) return;
        CloudMainframeBlockEntity mainframe = null;
        boolean open = false;
        if (player.containerMenu instanceof MainframeMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)
            && player.level().getBlockEntity(menu.pos()) instanceof CloudMainframeBlockEntity be) {
            mainframe = be;
            open = true;
        }
        java.util.Set<ai.ezfox.cccloud.access.Perm> perms = mainframe == null ? java.util.Set.of()
            : ai.ezfox.cccloud.access.Access.resolve(player, mainframe.getMainframeId()).perms();
        var ops = mainframe == null ? null : new Ops(player, mainframe);
        String error = GuiActions.mainframe(payload.action(), payload.arg(), open, perms, ops == null ? DENY_ALL : ops);
        if (error != null) {
            // without an open screen it is a stray or crafted packet: no log line per packet
            if (open) CCCloud.LOGGER.info("[cccloud] Rejected screen action {} of {}: {}", payload.action(), player.getGameProfile().name(), error);
            else CCCloud.LOGGER.debug("[cccloud] Rejected screen action {} of {}: {}", payload.action(), player.getGameProfile().name(), error);
        }
        if (open) push(player, error != null ? error : ops.notice);
    }

    private static void onUplinkAction(UplinkActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ai.ezfox.cccloud.ingame.PacketLimiter.SCREEN_ACTIONS.allow(player.getUUID())) return;
        ai.ezfox.cccloud.network.DashboardSection section = null;
        Runnable unlink = null;
        CloudUplinkBlockEntity redstoneTarget = null;
        boolean open = false;
        if (player.containerMenu instanceof UplinkMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            var be = player.level().getBlockEntity(menu.pos());
            if (be instanceof CloudUplinkBlockEntity uplink) {
                section = uplink;
                redstoneTarget = uplink;
                unlink = () -> uplink.unlink(player.getGameProfile().name());
                open = true;
            } else if (be instanceof ai.ezfox.cccloud.bridge.BridgeHost bridge) {
                section = bridge.bridgeSection();
                unlink = () -> bridge.bridgeSection().unlink(player.getGameProfile().name());
                open = true;
            }
        }
        var link = section == null ? null : section.getLink();
        java.util.Set<ai.ezfox.cccloud.access.Perm> perms = link == null ? java.util.Set.of() : ai.ezfox.cccloud.access.Access.resolve(player, link.id()).perms();
        var doUnlink = unlink;
        var target = redstoneTarget;
        var card = section;
        String[] notice = {null};
        String error = GuiActions.uplink(payload.action(), payload.arg(), open, perms, new GuiActions.UplinkOps() {
            @Override
            public @Nullable String unlink() {
                doUnlink.run();
                return null;
            }

            @Override
            public @Nullable String rename(String title) {
                if (link == null || player.level().getServer() == null) return "not_linked";
                String error = renameCard(player.level().getServer(), link.id(), card.sectionId().toString(), title);
                if (error != null) return error;
                CCCloud.LOGGER.info("[cccloud] {} renamed the card {} to '{}' (screen)", player.getGameProfile().name(), card.sectionId(), title);
                notice[0] = title.isEmpty() ? "title_reset" : "renamed";
                return null;
            }

            @Override
            public @Nullable String dashboardLink() {
                if (link == null) return "not_linked";
                var urls = CloudWebServer.dashboardUrls();
                if (!CloudWebServer.isRunning() || urls.isEmpty()) return "web_off";
                String url = ai.ezfox.cccloud.web.PairingText.cardUrl(urls.getFirst(), link.id().toString(), card.sectionId().toString());
                if (url == null) return "web_off";
                String custom = GuiViews.customTitle(WebHub.INSTANCE.layout(link.id()), card.sectionId().toString());
                String title = custom != null ? custom : card.getTitle().isEmpty() ? "-" : card.getTitle();
                player.sendSystemMessage(Component.translatable("cccloud.gui.card.link_chat", title)
                    .append(ai.ezfox.cccloud.web.PairingText.openLink(url, Component.translatable("cccloud.gui.card.link_open"))));
                notice[0] = "link_sent";
                return null;
            }

            @Override
            public boolean linked() {
                return link != null;
            }

            @Override
            public @Nullable String clearTarget() {
                if (target == null || !target.isProbe()) return "not_supported";
                target.clearRemoteTarget(player.getGameProfile().name());
                notice[0] = "target_cleared";
                return null;
            }

            @Override
            public @Nullable String redstone(ai.ezfox.cccloud.redstone.RedstoneMode mode, String channel) {
                if (target == null) return "not_supported";
                target.setRedstone(mode, channel);
                CCCloud.LOGGER.info("[cccloud] {} set the uplink at {} to wireless redstone {} on '{}'", player.getGameProfile().name(), target.getBlockPos(),
                    mode.id(), channel);
                return null;
            }
        });
        if (error != null) {
            if (open) CCCloud.LOGGER.info("[cccloud] Rejected screen action {} of {}: {}", payload.action(), player.getGameProfile().name(), error);
            else CCCloud.LOGGER.debug("[cccloud] Rejected screen action {} of {}: {}", payload.action(), player.getGameProfile().name(), error);
        }
        if (open) push(player, error != null ? error : notice[0] != null ? notice[0] : "redstone".equals(payload.action()) ? "redstone_saved" : "unlinked");
    }

    /**
     * Sets (or with an empty title: removes) a card's custom title in the mainframe's dashboard layout and sends the
     * layout to every open dashboard. A mainframe without a layout gets an empty v2 document (cards keep their
     * automatic places). Returns null or an error code.
     */
    public static @Nullable String renameCard(MinecraftServer server, UUID mainframeId, String cardKey, String title) {
        if (!WebHub.INSTANCE.tryLayoutSave(mainframeId)) return "rate_limited";
        var current = WebHub.INSTANCE.layout(mainframeId);
        var layout = current != null ? current : emptyLayout();
        if (!(layout.get("cards") instanceof com.google.gson.JsonObject cards)) return "invalid_layout";
        var card = cards.get(cardKey) instanceof com.google.gson.JsonObject c ? c : new com.google.gson.JsonObject();
        if (title.isEmpty()) card.remove("title");
        else card.addProperty("title", title);
        if (card.isEmpty()) cards.remove(cardKey);
        else cards.add(cardKey, card);
        var result = ai.ezfox.cccloud.layout.LayoutValidator.validate(layout);
        if (result.layout() == null) return "invalid_layout";
        ai.ezfox.cccloud.layout.LayoutData.get(server).set(mainframeId, result.layout());
        return null;
    }

    private static com.google.gson.JsonObject emptyLayout() {
        var layout = new com.google.gson.JsonObject();
        layout.addProperty("v", 2);
        layout.add("pages", new com.google.gson.JsonArray());
        layout.add("cards", new com.google.gson.JsonObject());
        layout.add("widgets", new com.google.gson.JsonObject());
        return layout;
    }

    private static final GuiActions.MainframeOps DENY_ALL = new GuiActions.MainframeOps() {
        // Never reached: GuiActions rejects before calling ops when no screen is open. Kept total for safety.
        @Override public @Nullable String toggleDashboard() { return "no_screen"; }
        @Override public @Nullable String newCode() { return "no_screen"; }
        @Override public @Nullable String approve(String id) { return "no_screen"; }
        @Override public @Nullable String deny(String id) { return "no_screen"; }
        @Override public @Nullable String revoke(String id) { return "no_screen"; }
        @Override public @Nullable String revokeAll() { return "no_screen"; }
        @Override public @Nullable String trust(String name) { return "no_screen"; }
        @Override public @Nullable String untrust(UUID player) { return "no_screen"; }
        @Override public @Nullable String setRole(UUID player, ai.ezfox.cccloud.access.Role role) { return "no_screen"; }
        @Override public @Nullable String linkAccount() { return "no_screen"; }
        @Override public @Nullable String setPin(String pin) { return "no_screen"; }
        @Override public @Nullable String clearPin() { return "no_screen"; }
    };

    /** GameTests: the screen operations of a player on a mainframe (the same object a screen action uses). */
    public static GuiActions.MainframeOps opsForTest(ServerPlayer player, CloudMainframeBlockEntity mainframe) {
        return new Ops(player, mainframe);
    }

    private static final java.util.concurrent.atomic.AtomicInteger LOOKUPS = new java.util.concurrent.atomic.AtomicInteger();
    private static final int MAX_LOOKUPS = 4;

    /** The real mainframe operations (server thread, rights already checked). */
    private static final class Ops implements GuiActions.MainframeOps {
        private final ServerPlayer player;
        private final CloudMainframeBlockEntity mainframe;
        private final MinecraftServer server;
        private final AuthStore auth;
        @Nullable String notice;

        Ops(ServerPlayer player, CloudMainframeBlockEntity mainframe) {
            this.player = player;
            this.mainframe = mainframe;
            this.server = player.level().getServer();
            this.auth = WebAuthData.get(server);
        }

        private String who() {
            return player.getGameProfile().name();
        }

        @Override
        public @Nullable String toggleDashboard() {
            mainframe.setEnabled(!mainframe.isEnabled());
            notice = mainframe.isEnabled() ? "dashboard_on" : "dashboard_off";
            return null;
        }

        @Override
        public @Nullable String newCode() {
            auth.dropCode(mainframe.getMainframeId());
            auth.currentCode(mainframe.getMainframeId(), player.getUUID());
            CCCloud.LOGGER.info("[cccloud] {} generated a new pairing code for {}", who(), mainframe.getDisplayName());
            notice = "new_code";
            return null;
        }

        private @Nullable String decide(String id, boolean allow) {
            var pending = auth.pending(id);
            if (pending == null || pending.state() != AuthStore.PendingState.PENDING || !mainframe.getMainframeId().equals(pending.mainframeId())) { // account-code requests have no mainframe
                return "unknown_request";
            }
            if (allow) {
                var approved = auth.approve(id, player.getUUID(), who(), true);
                if (approved == null) return "unknown_request";
                CCCloud.LOGGER.info("[cccloud] {} approved device '{}' from {} (screen)", who(), approved.deviceName(), pending.ip());
                notice = "approved";
            } else {
                auth.deny(id);
                CCCloud.LOGGER.info("[cccloud] {} denied device '{}' from {} (screen)", who(), pending.deviceName(), pending.ip());
                notice = "denied";
            }
            return null;
        }

        @Override
        public @Nullable String approve(String id) {
            return decide(id, true);
        }

        @Override
        public @Nullable String deny(String id) {
            return decide(id, false);
        }

        @Override
        public @Nullable String revoke(String deviceId) {
            var device = auth.revokeById(mainframe.getMainframeId(), deviceId);
            if (device == null) return "unknown_device";
            WebHub.INSTANCE.closeSessions(mainframe.getMainframeId(), device.tokenHash());
            CCCloud.LOGGER.info("[cccloud] {} revoked device '{}' of {} (screen)", who(), device.name(), mainframe.getDisplayName());
            notice = "revoked";
            return null;
        }

        @Override
        public @Nullable String revokeAll() {
            mainframe.revoke(player.level(), "all", who());
            notice = "revoked";
            return null;
        }

        @Override
        public @Nullable String trust(String playerName) {
            var online = server.getPlayerList().getPlayerByName(playerName);
            if (online != null) return applyTrust(new NameAndId(online.getUUID(), online.getGameProfile().name()));
            // Unknown players may need a (blocking) profile lookup: do it off-thread, apply on the server thread.
            // At most one lookup per player and second, and a few at a time server-wide (L13).
            if (!ai.ezfox.cccloud.ingame.PacketLimiter.IN_GAME.allow(player.getUUID(), "profile_lookup", System.currentTimeMillis())) return "rate_limited";
            if (LOOKUPS.incrementAndGet() > MAX_LOOKUPS) {
                LOOKUPS.decrementAndGet();
                return "rate_limited";
            }
            var id = mainframe.getMainframeId();
            CompletableFuture.supplyAsync(() -> server.services().nameToIdCache().get(playerName))
                .whenComplete((r, e) -> LOOKUPS.decrementAndGet())
                .thenAccept(result -> server.execute(() -> {
                var current = player.containerMenu instanceof MainframeMenu menu
                    && player.level().getBlockEntity(menu.pos()) instanceof CloudMainframeBlockEntity m && m.getMainframeId().equals(id) ? m : null;
                if (current == null || !ai.ezfox.cccloud.access.Access.has(player, id, ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE)) return;
                String error = result.isEmpty() ? "unknown_player" : new Ops(player, current).applyTrust(result.get());
                push(player, error != null ? error : "trusted");
            }));
            notice = "looking_up";
            return null;
        }

        /** Adds a player as operator, never above the caller's own role (the role can be changed in the list). */
        @Nullable String applyTrust(NameAndId profile) {
            if (profile.id().equals(mainframe.getOwner()) || mainframe.getGrants().containsKey(profile.id())) return "already_trusted";
            var caller = ai.ezfox.cccloud.access.Access.caller(player, mainframe.getMainframeId());
            var role = ai.ezfox.cccloud.access.AccessRules.trustRole(caller, ai.ezfox.cccloud.access.Role.OPERATOR);
            if (ai.ezfox.cccloud.access.AccessRules.changeGrant(caller, ai.ezfox.cccloud.access.Role.NONE, false, profile.id().equals(player.getUUID()), role,
                java.util.Set.of()) != null) return "no_permission";
            if (!mainframe.setRole(profile, role)) return "already_trusted";
            CCCloud.LOGGER.info("[cccloud] {} added {} as {} on {} (screen)", who(), profile.name(), role.id, mainframe.getDisplayName());
            WebAuthData.access(server).audit(mainframe.getMainframeId(), who() + " added " + profile.name() + " as " + role.id);
            notice = "trusted";
            return null;
        }

        @Override
        public @Nullable String untrust(UUID target) {
            var grant = mainframe.getGrants().get(target);
            if (grant != null && ai.ezfox.cccloud.access.AccessRules.changeGrant(ai.ezfox.cccloud.access.Access.caller(player, mainframe.getMainframeId()),
                grant.role(), false, target.equals(player.getUUID()), ai.ezfox.cccloud.access.Role.NONE, java.util.Set.of()) != null) return "no_permission";
            if (!mainframe.untrust(target)) return "not_trusted";
            CCCloud.LOGGER.info("[cccloud] {} removed the role of {} on {} (screen)", who(), grant != null ? grant.name() : target, mainframe.getDisplayName());
            WebAuthData.access(server).audit(mainframe.getMainframeId(), who() + " removed " + (grant != null ? grant.name() : target.toString()));
            notice = "untrusted";
            return null;
        }

        @Override
        public @Nullable String setRole(UUID target, ai.ezfox.cccloud.access.Role role) {
            var grant = mainframe.getGrants().get(target);
            if (grant == null) return "not_trusted"; // the screen only edits existing grants; add players by name
            if (ai.ezfox.cccloud.access.AccessRules.changeGrant(ai.ezfox.cccloud.access.Access.caller(player, mainframe.getMainframeId()), grant.role(), false,
                target.equals(player.getUUID()), role, java.util.Set.of()) != null) return "no_permission";
            if (!mainframe.setRole(new NameAndId(target, grant.name()), role)) return null;
            CCCloud.LOGGER.info("[cccloud] {} set the role of {} on {} to {} (screen)", who(), grant.name(), mainframe.getDisplayName(), role.id);
            WebAuthData.access(server).audit(mainframe.getMainframeId(), who() + " set " + grant.name() + " to " + role.id);
            notice = "role_changed";
            return null;
        }

        @Override
        public @Nullable String linkAccount() {
            if (!ai.ezfox.cccloud.access.Access.has(player, ai.ezfox.cccloud.access.ServerPermissions.Node.USE)) return "not_allowed";
            auth.accountCode(player.getUUID());
            notice = "account_code";
            return null;
        }

        @Override
        public @Nullable String setPin(String pin) {
            var id = mainframe.getMainframeId();
            String name = mainframe.getDisplayName();
            // PBKDF2 is slow on purpose: hash off-thread.
            CompletableFuture.supplyAsync(() -> auth.hashPin(pin)).thenAccept(hash -> server.execute(() -> {
                auth.setPin(id, hash);
                CCCloud.LOGGER.info("[cccloud] {} set the dashboard PIN of {} (screen)", who(), name);
                if (player.containerMenu instanceof MainframeMenu) push(player, "pin_set");
            }));
            notice = "pin_saving";
            return null;
        }

        @Override
        public @Nullable String clearPin() {
            auth.setPin(mainframe.getMainframeId(), null);
            CCCloud.LOGGER.info("[cccloud] {} cleared the dashboard PIN of {} (screen)", who(), mainframe.getDisplayName());
            notice = "pin_cleared";
            return null;
        }
    }

    // ---- live updates ----------------------------------------------------------------------------------------------

    /** Sends the current view to a player whose screen is open (optionally with a notice). */
    public static void push(ServerPlayer player, @Nullable String notice) {
        if (!(player.containerMenu instanceof CloudMenu menu)) return;
        String view = view(player, menu, notice);
        if (view == null || view.length() > CloudMenu.MAX_VIEW) return;
        menu.lastSent = view;
        PacketDistributor.sendToPlayer(player, new ViewPayload(menu.containerId, view));
    }

    private static @Nullable String view(ServerPlayer player, CloudMenu menu, @Nullable String notice) {
        var be = player.level().getBlockEntity(menu.pos());
        if (menu instanceof MainframeMenu && be instanceof CloudMainframeBlockEntity mainframe) {
            // The network goes along only when it changed since the screen got it (it is big on large bases).
            long[] netV = {-1};
            String view = GuiViews.mainframe(mainframe, player, notice, menu.sentNetV, netV);
            menu.sentNetV = netV[0];
            return view;
        }
        if (menu instanceof UplinkMenu && be instanceof CloudUplinkBlockEntity uplink) return GuiViews.uplink(uplink, player, notice);
        if (menu instanceof UplinkMenu && be instanceof ai.ezfox.cccloud.bridge.BridgeHost bridge) return GuiViews.bridge(bridge, player, notice);
        if (menu instanceof UplinkMenu && be instanceof ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay) return GuiViews.relay(relay, player, notice);
        return null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % UPDATE_INTERVAL != 0) return;
        long cost = ai.ezfox.cccloud.network.TickCost.start();
        try {
            updateOpenScreens(server);
        } finally {
            ai.ezfox.cccloud.network.TickCost.end(ai.ezfox.cccloud.network.TickCost.SCREENS, cost);
        }
    }

    private static void updateOpenScreens(net.minecraft.server.MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof CloudMenu menu)) continue;
            try {
                String view = view(player, menu, null);
                if (view == null || view.equals(menu.lastSent) || view.length() > CloudMenu.MAX_VIEW) continue;
                menu.lastSent = view;
                PacketDistributor.sendToPlayer(player, new ViewPayload(menu.containerId, view));
            } catch (RuntimeException e) {
                CCCloud.LOGGER.error("[cccloud] Could not update the screen of {}", player.getGameProfile().name(), e);
            }
        }
    }
}
