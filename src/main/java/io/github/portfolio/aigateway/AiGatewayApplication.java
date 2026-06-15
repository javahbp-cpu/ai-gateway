package io.github.portfolio.aigateway;

import io.github.portfolio.aigateway.config.GatewayProperties;
import io.github.portfolio.aigateway.config.LocalEnvFileLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class AiGatewayApplication {

    public static void main(String[] args) {
        LocalEnvFileLoader.load();
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
