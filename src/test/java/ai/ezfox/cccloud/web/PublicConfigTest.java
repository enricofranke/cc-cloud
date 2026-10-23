package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Public config (store URL, features, version), the store URL rules and the CSP connect-src. */
class PublicConfigTest {
    @AfterEach
    void reset() {
        PublicConfig.setSource(() -> new PublicConfig.Settings("dev", false, "", false, false, false, false, false));
    }

    static String csp() {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        HttpHandler.addSecurityHeaders(response, "192.168.1.5:8787", false);
        return response.headers().get("Content-Security-Policy");
    }

    @Test
    void storeUrlRules() {
        assertEquals("https://store.example.org/store/", PublicConfig.validStoreUrl("https://store.example.org/store/"));
        for (String bad : new String[]{"http://store.example.org/", "https://store.example.org/store", "https://u:p@x.org/", "https://x.org/?a=1/",
            "javascript:alert(1)/", "", "https:///"}) {
            assertNull(PublicConfig.validStoreUrl(bad), bad);
        }
    }

    @Test
    void enabledStoreGoesIntoConfigAndCsp() {
        PublicConfig.setSource(() -> new PublicConfig.Settings("0.9.0", true, "https://store.example.org:8443/store/", true, false, true, true, true));
        var json = PublicConfig.json();
        assertEquals("0.9.0", json.get("version").getAsString());
        assertTrue(json.getAsJsonObject("store").get("enabled").getAsBoolean());
        assertEquals("https://store.example.org:8443/store/", json.getAsJsonObject("store").get("url").getAsString());
        assertTrue(json.getAsJsonObject("features").get("terminal").getAsBoolean());
        assertFalse(json.getAsJsonObject("features").get("voxels").getAsBoolean());
        assertTrue(csp().contains("connect-src 'self' ws://192.168.1.5:8787 wss://192.168.1.5:8787 https://store.example.org:8443;"), csp());
    }

    @Test
    void disabledStoreStaysOutOfTheCsp() {
        PublicConfig.setSource(() -> new PublicConfig.Settings("0.9.0", false, "https://store.example.org/store/", false, false, false, false, false));
        assertFalse(PublicConfig.json().getAsJsonObject("store").get("enabled").getAsBoolean());
        assertTrue(PublicConfig.json().getAsJsonObject("store").get("url").isJsonNull());
        assertFalse(csp().contains("store.example.org"));
        assertNull(Router.match(io.netty.handler.codec.http.HttpMethod.POST, "/api/config"));
        assertEquals(Router.Access.PUBLIC, Router.match(io.netty.handler.codec.http.HttpMethod.GET, "/api/config").access());
    }
}
