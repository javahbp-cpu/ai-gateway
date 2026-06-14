package io.github.portfolio.aigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AiGatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void protectsOpenAiCompatibleEndpoints() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .bodyValue("{\"model\":\"smart-chat\",\"messages\":[]}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("x-request-id")
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_api_key")
                .jsonPath("$.error.message").isEqualTo("API Key 无效");
    }

    @Test
    void exposesHealthWithoutApiKey() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void listsLogicalModelsForAuthenticatedClients() {
        webTestClient.get()
                .uri("/v1/models")
                .header("Authorization", "Bearer gateway-dev-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("smart-chat")
                .jsonPath("$.data[0].owned_by").isEqualTo("ai-gateway");
    }

    @Test
    void returnsChineseMessageForMalformedJson() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer gateway-dev-key")
                .header("Content-Type", "application/json")
                .bodyValue("{invalid-json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("invalid_request")
                .jsonPath("$.error.message").isEqualTo("请求参数格式错误");
    }

    @Test
    void returnsChineseMessageWhenModelIsMissing() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer gateway-dev-key")
                .header("Content-Type", "application/json")
                .bodyValue("{\"messages\":[]}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("model_required")
                .jsonPath("$.error.message").isEqualTo("请求字段 'model' 不能为空");
    }

    @Test
    void returnsChineseMessageWhenModelRouteDoesNotExist() {
        webTestClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer gateway-dev-key")
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"unknown-model\",\"messages\":[]}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("model_not_found")
                .jsonPath("$.error.message").isEqualTo("未找到模型对应的供应商路由：unknown-model");
    }
}
