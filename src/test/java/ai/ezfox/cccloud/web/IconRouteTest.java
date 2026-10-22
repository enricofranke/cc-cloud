package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

/** /api/icon: public but strictly validated, rate limited and never a path into the file system. */
class IconRouteTest {
    @Test
    void iconRouteIsAnExplicitPublicRoute() {
        var route = Router.match(HttpMethod.GET, "/api/icon/minecraft/iron_ingot.png");
        assertNotNull(route);
        assertEquals(Router.Id.ICON, route.id());
        assertEquals(Router.Access.PUBLIC, route.access());
        assertNull(Router.match(HttpMethod.GET, "/api/icon/minecraft/iron_ingot.jpg"));
        assertNull(Router.match(HttpMethod.GET, "/api/icon/Minecraft/iron_ingot.png"), "uppercase never matches");
        assertNull(Router.match(HttpMethod.GET, "/api/icon/minecraft/iron%2fingot.png"));
        assertNull(Router.match(HttpMethod.POST, "/api/icon/minecraft/iron_ingot.png"));
    }

    @Test
    void traversalAndUnknownItemsAre404() {
        var instance = RouteAuthTest.instance(true);
        for (var path : new String[]{"/api/icon/minecraft/../../level.png", "/api/icon/../minecraft/x.png", "/api/icon/minecraft/./x.png",
            "/api/icon/minecraft/a//b.png", "/api/icon/nosuchmod/thing.png", "/api/icon/minecraft/not_an_item_at_all.png"}) {
            var response = RouteAuthTest.request(instance, HttpMethod.GET, path, null);
            assertEquals(404, response.status().code(), path);
            assertEquals("nosniff", response.headers().get("X-Content-Type-Options"), path);
        }
    }

    @Test
    void rateLimitedPerIp() {
        var instance = RouteAuthTest.instance(true);
        int limited = 0;
        for (int i = 0; i < HttpHandler.ICON_RATE + 20; i++) {
            if (RouteAuthTest.request(instance, HttpMethod.GET, "/api/icon/minecraft/not_an_item_at_all.png", null).status().code() == 429) limited++;
        }
        assertTrue(limited >= 15, "requests over 200/min get 429, got " + limited);
    }

    @Test
    void lanOnlyRuleStillApplies() {
        var response = RouteAuthTest.request(RouteAuthTest.instance(false), HttpMethod.GET, "/api/icon/minecraft/iron_ingot.png", null);
        assertEquals(403, response.status().code());
    }
}
