package io.github.portfolio.aigateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.portfolio.aigateway.error.GatewayException;
import io.github.portfolio.aigateway.routing.ModelRouter;
import io.github.portfolio.aigateway.upstream.UpstreamClient;
import io.github.portfolio.aigateway.upstream.UpstreamResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ChatGatewayService {

    private final ModelRouter modelRouter;
    private final UpstreamClient upstreamClient;

    public ChatGatewayService(ModelRouter modelRouter, UpstreamClient upstreamClient) {
        this.modelRouter = modelRouter;
        this.upstreamClient = upstreamClient;
    }

    public Mono<UpstreamResponse> chatCompletions(JsonNode request) {
        String model = request.path("model").asText();
        if (model.isBlank()) {
            return Mono.error(new GatewayException(HttpStatus.BAD_REQUEST, "model_required",
                    "Request field 'model' is required"));
        }
        return attempt(modelRouter.providersFor(model), 0, request, null);
    }

    private Mono<UpstreamResponse> attempt(
            List<String> providers,
            int index,
            JsonNode request,
            Throwable lastError) {
        if (index >= providers.size()) {
            String detail = lastError == null ? "No providers available" : lastError.getMessage();
            return Mono.error(new GatewayException(HttpStatus.BAD_GATEWAY, "all_providers_failed",
                    "All configured providers failed: " + detail));
        }
        // 仅当上游调用以异常结束时尝试下一供应商；上游 4xx 会作为正常响应直接返回客户端。
        return upstreamClient.chatCompletions(providers.get(index), request)
                .onErrorResume(error -> attempt(providers, index + 1, request, error));
    }
}
