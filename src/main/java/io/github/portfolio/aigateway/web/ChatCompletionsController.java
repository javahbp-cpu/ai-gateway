package io.github.portfolio.aigateway.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.portfolio.aigateway.service.ChatGatewayService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class ChatCompletionsController {

    private final ChatGatewayService gatewayService;

    public ChatCompletionsController(ChatGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/chat/completions")
    Mono<ResponseEntity<Flux<DataBuffer>>> chatCompletions(@RequestBody JsonNode request) {
        return gatewayService.chatCompletions(request)
                .map(upstream -> ResponseEntity.status(upstream.status())
                        .headers(upstream.headers())
                        .body(upstream.body()));
    }
}
