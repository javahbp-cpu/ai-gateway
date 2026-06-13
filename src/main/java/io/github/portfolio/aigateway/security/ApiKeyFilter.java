package io.github.portfolio.aigateway.security;

import io.github.portfolio.aigateway.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class ApiKeyFilter implements WebFilter, Ordered {

    public static final String API_KEY_ATTRIBUTE = "gateway.api-key";

    private final GatewayProperties properties;

    public ApiKeyFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Actuator 等运维端点不使用客户端 API Key，便于探针和 Prometheus 访问。
        if (!exchange.getRequest().getPath().value().startsWith("/v1/")) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String apiKey = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : exchange.getRequest().getHeaders().getFirst("x-api-key");

        if (apiKey == null || !matchesAny(apiKey, properties.getApiKeys())) {
            byte[] body = "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"Invalid API key\",\"type\":\"authentication_error\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }

        exchange.getAttributes().put(API_KEY_ATTRIBUTE, apiKey);
        return chain.filter(exchange);
    }

    private boolean matchesAny(String actual, List<String> configuredKeys) {
        // 使用恒定时间比较，降低通过响应耗时推测 API Key 内容的风险。
        return configuredKeys.stream().anyMatch(expected -> MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public int getOrder() {
        // 必须先完成鉴权，后续限流器才能取得经过验证的 API Key。
        return -200;
    }
}
