package io.github.portfolio.aigateway.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfiguration {

    @Bean
    WebClient.Builder gatewayWebClientBuilder(GatewayProperties properties) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(properties.getUpstreamTimeout());
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client));
    }
}
