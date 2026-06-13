package io.github.portfolio.aigateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.portfolio.aigateway.config.GatewayProperties;
import io.github.portfolio.aigateway.routing.ModelRouter;
import io.github.portfolio.aigateway.upstream.UpstreamClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ChatGatewayServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer primary;
    private MockWebServer fallback;

    @BeforeEach
    void setUp() throws Exception {
        primary = new MockWebServer();
        fallback = new MockWebServer();
        primary.start();
        fallback.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        primary.shutdown();
        fallback.shutdown();
    }

    @Test
    void fallsBackAndRewritesModel() throws Exception {
        primary.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
        fallback.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"chatcmpl-test\",\"model\":\"fallback-model\"}"));

        GatewayProperties properties = properties();
        ChatGatewayService service = new ChatGatewayService(
                new ModelRouter(properties),
                new UpstreamClient(properties, WebClient.builder(), new SimpleMeterRegistry()));

        StepVerifier.create(service.chatCompletions(objectMapper.readTree(
                        "{\"model\":\"smart-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .flatMap(response -> DataBufferUtils.join(response.body()))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes);
                }))
                .assertNext(body -> assertThat(body).contains("chatcmpl-test"))
                .verifyComplete();

        assertThat(primary.takeRequest().getBody().readUtf8()).contains("\"model\":\"primary-model\"");
        assertThat(fallback.takeRequest().getBody().readUtf8()).contains("\"model\":\"fallback-model\"");
    }

    @Test
    void forwardsClientErrorsWithoutCallingFallback() throws Exception {
        primary.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"invalid request\"}}"));

        ChatGatewayService service = service(properties());

        StepVerifier.create(service.chatCompletions(objectMapper.readTree(
                        "{\"model\":\"smart-chat\",\"messages\":[]}")))
                .assertNext(response -> assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();

        assertThat(primary.getRequestCount()).isEqualTo(1);
        assertThat(fallback.getRequestCount()).isZero();
    }

    @Test
    void passesThroughServerSentEventStream() throws Exception {
        primary.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"id\":\"one\"}\n\ndata: [DONE]\n\n"));

        ChatGatewayService service = service(properties());

        StepVerifier.create(service.chatCompletions(objectMapper.readTree(
                        "{\"model\":\"smart-chat\",\"stream\":true,\"messages\":[]}"))
                .flatMap(response -> DataBufferUtils.join(response.body()))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes);
                }))
                .assertNext(body -> assertThat(body).contains("data: {\"id\":\"one\"}", "data: [DONE]"))
                .verifyComplete();
    }

    private ChatGatewayService service(GatewayProperties properties) {
        return new ChatGatewayService(
                new ModelRouter(properties),
                new UpstreamClient(properties, WebClient.builder(), new SimpleMeterRegistry()));
    }

    private GatewayProperties properties() {
        GatewayProperties properties = new GatewayProperties();
        Map<String, GatewayProperties.Provider> providers = new LinkedHashMap<>();
        providers.put("primary", provider(primary, "primary-model"));
        providers.put("fallback", provider(fallback, "fallback-model"));
        properties.setProviders(providers);
        properties.setRoutes(Map.of("smart-chat", List.of("primary", "fallback")));
        return properties;
    }

    private GatewayProperties.Provider provider(MockWebServer server, String model) {
        GatewayProperties.Provider provider = new GatewayProperties.Provider();
        provider.setBaseUrl(server.url("/v1").toString());
        provider.setApiKey("test-key");
        provider.setModelMapping(Map.of("smart-chat", model));
        return provider;
    }
}
