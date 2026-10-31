package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.BuildInfo;
import ai.ezfox.cccloud.join.BuildCheck;
import ai.ezfox.cccloud.join.JoinCheck;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * The join check on the server: a client that stays silent gets the message after 10 seconds, one with another build gets
 * it at once, one with the server's build gets nothing. The players really log in (PlayerLoggedInEvent starts the wait);
 * a dev run has no jar file, so the message comes without a link. NeoForge's own negotiator lets a client without the
 * channel in, which a required channel would not.
 */
public final class JoinTests {
    private JoinTests() {
    }

    /** A mock player like {@link GameTestHelper#makeMockServerPlayerInLevel} that keeps what it is told. */
    private record Client(ServerPlayer player, List<String> chat) {
        boolean told(String text) {
            return chat.stream().anyMatch(line -> line.contains(text));
        }
    }

    private static Client join(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "join-check"), false);
        var chat = new ArrayList<String>();
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void sendSystemMessage(Component message) {
                chat.add(message.getString());
                super.sendSystemMessage(message);
            }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return new Client(player, chat);
    }

    private static void oldClientsNegotiate(GameTestHelper helper) {
        var id = JoinCheck.Hello.TYPE.id();
        helper.assertTrue(NetworkRegistry.getCommonPlayChannels(PacketFlow.SERVERBOUND).contains(id), "registered as an optional channel");
        var optional = List.of(new NegotiableNetworkComponent(id, "1", Optional.of(PacketFlow.SERVERBOUND), true));
        var required = List.of(new NegotiableNetworkComponent(id, "1", Optional.of(PacketFlow.SERVERBOUND), false));
        helper.assertTrue(NetworkComponentNegotiator.negotiate(optional, List.of()).success(), "a client without the channel joins");
        helper.assertTrue(NetworkComponentNegotiator.negotiate(List.of(), optional).success(), "a new client joins a server without the channel");
        helper.assertTrue(NetworkComponentNegotiator.negotiate(optional, optional).success(), "both have it");
        helper.assertTrue(!NetworkComponentNegotiator.negotiate(required, List.of()).success(), "a required channel would refuse an old client");
    }

    public static void joinCheck(GameTestHelper helper) {
        oldClientsNegotiate(helper);
        var silent = join(helper);
        var same = join(helper);
        var other = join(helper);
        var repeated = join(helper);
        String server = BuildInfo.id();
        helper.startSequence()
            .thenExecuteAfter(BuildCheck.HELLO_TICKS - 60, () -> {
                JoinCheck.hello(same.player(), server);
                JoinCheck.hello(other.player(), "1.0.0-beta.1-81ab0536");
                JoinCheck.hello(repeated.player(), "1.0.0-beta.1-81ab0536");
                JoinCheck.hello(repeated.player(), "1.0.0-beta.1-81ab0536");
                helper.assertTrue(silent.chat().isEmpty(), "still within the 10 seconds: " + silent.chat());
                helper.assertTrue(same.chat().isEmpty(), "the server's build: nothing: " + same.chat());
                helper.assertTrue(other.told("1.0.0-beta.1-81ab0536") && other.told(server), "both versions, at once: " + other.chat());
                helper.assertTrue(other.chat().size() == 2 && other.told("server admin"), "no dashboard to link to in this test: " + other.chat());
                helper.assertTrue(repeated.chat().size() == 2, "one message per join: " + repeated.chat());
            })
            .thenExecuteAfter(100, () -> {
                helper.assertTrue(silent.told("an older build") && silent.told(server), "no hello: an older client, told: " + silent.chat());
                helper.assertTrue(silent.chat().size() == 2, "the message and what to do: " + silent.chat());
                helper.assertTrue(same.chat().isEmpty(), "the hello ended its wait: " + same.chat());
                helper.assertTrue(other.chat().size() == 2, "told once: " + other.chat());
                JoinCheck.hello(silent.player(), server);
                helper.assertTrue(silent.chat().size() == 2, "a late hello changes nothing: " + silent.chat());
                var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
                try {
                    helper.assertTrue(dispatcher.execute("cccloud version", same.player().createCommandSourceStack()) == 1, "anyone may ask");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    helper.assertTrue(false, "cccloud version: " + e.getMessage());
                }
                helper.assertTrue(same.told(server), "/cccloud version names the server's build: " + same.chat());
            })
            .thenExecute(() -> {
                var players = helper.getLevel().getServer().getPlayerList();
                for (var client : List.of(silent, same, other, repeated)) players.remove(client.player());
            })
            .thenSucceed();
    }
}
