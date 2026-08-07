package com.example.similarproducts.infrastructure.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient upstreamWebClient(AppConfig appConfig) {
    ConnectionProvider provider =
        ConnectionProvider.builder("upstream")
            .maxConnections(500)
            .pendingAcquireMaxCount(1000)
            .pendingAcquireTimeout(Duration.ofMillis(4000))
            .build();

    HttpClient httpClient =
        HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
            // No .responseTimeout() — Reactor operator timeouts handle cancellation

    return WebClient.builder()
        .baseUrl(appConfig.baseUrl())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }

  @Bean
  public Duration serviceResponseTimeout(AppConfig appConfig) {
    return Duration.ofMillis(appConfig.responseTimeoutMs());
  }
}
