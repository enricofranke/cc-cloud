package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.publicapi.ApiKeys;
import ai.ezfox.cccloud.publicapi.ApiScope;
import ai.ezfox.cccloud.publicapi.ApiSettings;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Config reload of {@code [api]} on a running server: the switch applies to the next request, and the rate limits of
 * buckets already in use change at once without handing out a fresh budget.
 */
class ApiReloadTest {
    final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    final WebInstance instance = new WebInstance(new TestBridge(), auth, new AccessStore(System::currentTimeMillis, () -> {
    }), null, List.of(), true, "http://localhost:8787", 8787);
    final ApiKeys keys = new ApiKeys(System::currentTimeMillis, () -> {
    });
    final String secret;

    ApiReloadTest() {
        instance.apiKeys = keys;
        secret = keys.create(UUID.randomUUID(), "Steve", UUID.fromString(ApiHttpTest.MF), "bot", EnumSet.allOf(ApiScope.class), 0, List.of()).secret();
    }

    static ApiSettings api(boolean enabled, int perKey, int perIp) {
        return new ApiSettings(enabled, 5, perKey, perIp, 2048, List.of(), true, 2);
    }

    int me() {
        var channel = new EmbeddedChannel(new HttpHandler(instance));
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/v1/me");
        req.headers().set(HttpHeaderNames.HOST, "localhost:8787");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + secret);
        channel.writeInbound(req);
        channel.runPendingTasks();
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        return response.status().code();
    }

    @Test
    void switchingTheApiOnAndOffAppliesToTheNextRequest() {
        instance.applyApi(api(true, 60, 120));
        assertEquals(200, me());
        instance.applyApi(api(false, 60, 120));
        assertEquals(404, me(), "the routes are gone at once, as after a fresh start with apiEnabled=false");
        instance.applyApi(api(true, 60, 120));
        assertEquals(200, me());
    }

    @Test
    void aLoweredKeyLimitHoldsForKeysInUse() {
        instance.applyApi(api(true, 60, 6000));
        for (int i = 0; i < 3; i++) assertEquals(200, me());
        instance.applyApi(api(true, 2, 6000)); // 57 tokens left, capped at the new 2
        assertEquals(200, me());
        assertEquals(200, me());
        assertEquals(429, me(), "the new limit, not the one the bucket was made with");
        instance.applyApi(api(true, 6000, 6000));
        assertEquals(429, me(), "raising the limit again does not refill a used-up key");
    }

    @Test
    void aLoweredIpLimitHoldsForIpsInUse() {
        instance.applyApi(api(true, 6000, 120));
        assertEquals(200, me());
        instance.applyApi(api(true, 6000, 1));
        assertEquals(200, me());
        assertEquals(429, me());
    }
}
