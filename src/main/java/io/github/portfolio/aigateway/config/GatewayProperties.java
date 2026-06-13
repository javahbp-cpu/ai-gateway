package io.github.portfolio.aigateway.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private List<String> apiKeys = List.of("gateway-dev-key");
    private int requestsPerMinute = 60;
    private Duration upstreamTimeout = Duration.ofSeconds(60);
    // 使用 LinkedHashMap 保留配置顺序，便于按声明顺序执行供应商回退。
    private Map<String, Provider> providers = new LinkedHashMap<>();
    // 路由中的模型是面向客户端的逻辑模型，不直接暴露供应商的真实模型名。
    private Map<String, List<String>> routes = new LinkedHashMap<>();

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public Duration getUpstreamTimeout() {
        return upstreamTimeout;
    }

    public void setUpstreamTimeout(Duration upstreamTimeout) {
        this.upstreamTimeout = upstreamTimeout;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    public Map<String, List<String>> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, List<String>> routes) {
        this.routes = routes;
    }

    public static class Provider {

        private String baseUrl;
        private String apiKey;
        // 将稳定的逻辑模型映射到不同供应商各自的真实模型。
        private Map<String, String> modelMapping = new LinkedHashMap<>();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Map<String, String> getModelMapping() {
            return modelMapping;
        }

        public void setModelMapping(Map<String, String> modelMapping) {
            this.modelMapping = modelMapping;
        }
    }
}
