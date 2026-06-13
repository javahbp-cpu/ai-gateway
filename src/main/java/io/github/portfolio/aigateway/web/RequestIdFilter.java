package io.github.portfolio.aigateway.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestIdFilter implements WebFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "x-request-id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        // Reactor 会跨线程执行任务，因此同时写入 Reactor Context，并在日志阶段维护 MDC。
        return chain.filter(exchange)
                .contextWrite(context -> context.put(REQUEST_ID_HEADER, requestId))
                .doFirst(() -> MDC.put(REQUEST_ID_HEADER, requestId))
                .doFinally(signal -> MDC.remove(REQUEST_ID_HEADER));
    }

    @Override
    public int getOrder() {
        // 最先生成请求 ID，让鉴权失败、限流等早期响应也具备追踪标识。
        return -300;
    }
}
