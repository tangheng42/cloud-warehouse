package com.yuncang.gateway;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.discovery.enabled=false", "spring.main.web-application-type=reactive"})
class GatewayRoutesTest {
    @Autowired RouteDefinitionLocator routes;
    @Test
    void onlyPublicPathsAreRoutedAndInternalCredentialIsRemoved() {
        var definitions = routes.getRouteDefinitions().collectList().block(Duration.ofSeconds(5));
        assertNotNull(definitions);
        assertEquals(1, definitions.size());
        var route = definitions.get(0);
        assertEquals("lb://cloud-warehouse", route.getUri().toString());
        var paths = route.getPredicates().get(0).getArgs().values();
        assertTrue(paths.contains("/api/v1/**"));
        assertTrue(paths.contains("/api/v1.1/**"));
        assertTrue(paths.stream().noneMatch(p -> p.contains("internal")));
        assertTrue(route.getFilters().stream().anyMatch(f -> f.getName().equals("RemoveRequestHeader")
                && f.getArgs().containsValue("X-Internal-Token")));
    }
}
