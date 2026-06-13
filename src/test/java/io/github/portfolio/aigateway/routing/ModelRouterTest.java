package io.github.portfolio.aigateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.portfolio.aigateway.config.GatewayProperties;
import io.github.portfolio.aigateway.error.GatewayException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelRouterTest {

    @Test
    void returnsProvidersInConfiguredFallbackOrder() {
        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(Map.of("smart-chat", List.of("primary", "fallback")));

        assertThat(new ModelRouter(properties).providersFor("smart-chat"))
                .containsExactly("primary", "fallback");
    }

    @Test
    void rejectsUnknownModel() {
        ModelRouter router = new ModelRouter(new GatewayProperties());

        assertThatThrownBy(() -> router.providersFor("missing"))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("No provider route");
    }
}
