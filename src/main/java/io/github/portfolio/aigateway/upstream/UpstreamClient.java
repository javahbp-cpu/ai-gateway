package io.github.portfolio.aigateway.upstream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.portfolio.aigateway.config.GatewayProperties;
import io.github.portfolio.aigateway.error.GatewayException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UpstreamClient {

    private final GatewayProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final MeterRegistry meterRegistry;

    public UpstreamClient(
            GatewayProperties properties,
            WebClient.Builder gatewayWebClientBuilder,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.webClientBuilder = gatewayWebClientBuilder;
        this.meterRegistry = meterRegistry;
    }

    public Mono<UpstreamResponse> chatCompletions(String providerName, JsonNode originalRequest) {
        GatewayProperties.Provider provider = properties.getProviders().get(providerName);
        if (provider == null) {
            return Mono.error(new GatewayException(HttpStatus.BAD_GATEWAY, "provider_not_found",
                    "未配置模型供应商：" + providerName));
        }
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            return Mono.error(new GatewayException(HttpStatus.BAD_GATEWAY, "provider_key_missing",
                    "模型供应商缺少 API Key：" + providerName));
        }

        JsonNode upstreamRequest = rewriteModel(originalRequest, provider);
        WebClient client = webClientBuilder.build();
        return client.post()
                .uri(chatCompletionsUrl(provider.getBaseUrl()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .bodyValue(upstreamRequest)
                .retrieve()
                // 参数错误等 4xx 应原样返回。盲目回退会增加调用成本，也会掩盖真实问题。
                .onStatus(status -> status.is4xxClientError(), response -> Mono.empty())
                // 使用 Flux<DataBuffer> 透传响应，避免在网关内存中聚合完整的 SSE 流。
                .toEntityFlux(DataBuffer.class)
                .map(this::toUpstreamResponse)
                .doOnSuccess(ignored -> meterRegistry.counter("gateway.upstream.requests",
                        "provider", providerName, "outcome", "success").increment())
                .doOnError(error -> meterRegistry.counter("gateway.upstream.requests",
                        "provider", providerName, "outcome", "error").increment());
    }

    private JsonNode rewriteModel(JsonNode original, GatewayProperties.Provider provider) {
        // 深拷贝后再改写，保证原请求可安全地用于后续供应商回退。
        ObjectNode copy = original.deepCopy();
        String requestedModel = original.path("model").asText();
        copy.put("model", provider.getModelMapping().getOrDefault(requestedModel, requestedModel));
        return copy;
    }

    private String chatCompletionsUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }

    private UpstreamResponse toUpstreamResponse(ResponseEntity<Flux<DataBuffer>> entity) {
        HttpHeaders forwarded = new HttpHeaders();
        // 当前只转发内容类型，避免把供应商内部或连接级响应头泄露给客户端。
        List<String> contentTypes = entity.getHeaders().get(HttpHeaders.CONTENT_TYPE);
        if (contentTypes != null) {
            forwarded.put(HttpHeaders.CONTENT_TYPE, contentTypes);
        }
        return new UpstreamResponse(entity.getStatusCode(), forwarded, entity.getBody());
    }
}
