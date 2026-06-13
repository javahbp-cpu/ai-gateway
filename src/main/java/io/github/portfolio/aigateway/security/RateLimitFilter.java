package io.github.portfolio.aigateway.security;

import io.github.portfolio.aigateway.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RateLimitFilter implements WebFilter, Ordered {

    private final GatewayProperties properties;
    private final Clock clock;
    // 第一阶段采用单机固定窗口；多实例部署时应替换为 Redis 等共享限流存储。
    private final Map<String, MinuteWindow> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(GatewayProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RateLimitFilter(GatewayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getAttribute(ApiKeyFilter.API_KEY_ATTRIBUTE);
        if (apiKey == null) {
            return chain.filter(exchange);
        }

        long currentMinute = clock.instant().getEpochSecond() / 60;
        // 分钟变化时原子替换窗口，同一分钟内则复用已有计数器。
        MinuteWindow window = windows.compute(apiKey, (key, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new MinuteWindow(currentMinute);
            }
            return existing;
        });
        int used = window.count.incrementAndGet();
        int remaining = Math.max(0, properties.getRequestsPerMinute() - used);
        exchange.getResponse().getHeaders().set("x-ratelimit-limit-requests",
                String.valueOf(properties.getRequestsPerMinute()));
        exchange.getResponse().getHeaders().set("x-ratelimit-remaining-requests", String.valueOf(remaining));

        if (used <= properties.getRequestsPerMinute()) {
            return chain.filter(exchange);
        }

        byte[] body = "{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"Request rate limit exceeded\",\"type\":\"rate_limit_error\"}}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在 ApiKeyFilter 之后执行，确保只对合法客户端计数。
        return -190;
    }

    private static final class MinuteWindow {

        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private MinuteWindow(long minute) {
            this.minute = minute;
        }
    }
}
