package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.BuildInfo;
import ai.ezfox.cccloud.join.JoinCheck;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** Names this client's build to the server right after joining (the join check, {@link JoinCheck}). */
final class ClientHello {
    private ClientHello() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(ClientHello::onLogin);
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        var connection = Minecraft.getInstance().getConnection();
        // a server from before the check has no such channel (sending would throw)
        if (connection != null && connection.hasChannel(JoinCheck.Hello.TYPE)) ClientPacketDistributor.sendToServer(new JoinCheck.Hello(BuildInfo.id()));
    }
}
