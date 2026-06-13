package io.github.portfolio.aigateway.web;

import io.github.portfolio.aigateway.config.GatewayProperties;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ModelsController {

    private final GatewayProperties properties;
    private final long createdAt = Instant.now().getEpochSecond();

    public ModelsController(GatewayProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/models")
    Map<String, Object> models() {
        List<Map<String, Object>> models = properties.getRoutes().keySet().stream()
                .sorted(Comparator.naturalOrder())
                .map(model -> Map.<String, Object>of(
                        "id", model,
                        "object", "model",
                        "created", createdAt,
                        "owned_by", "ai-gateway"))
                .toList();
        return Map.of("object", "list", "data", models);
    }
}
