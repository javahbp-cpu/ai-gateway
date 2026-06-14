package io.github.portfolio.aigateway.routing;

import io.github.portfolio.aigateway.config.GatewayProperties;
import io.github.portfolio.aigateway.error.GatewayException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ModelRouter {

    private final GatewayProperties properties;

    public ModelRouter(GatewayProperties properties) {
        this.properties = properties;
    }

    public List<String> providersFor(String model) {
        // 列表顺序就是故障回退顺序，首个供应商作为主供应商。
        List<String> providers = properties.getRoutes().get(model);
        if (providers == null || providers.isEmpty()) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "model_not_found",
                    "未找到模型对应的供应商路由：" + model);
        }
        return providers;
    }
}
