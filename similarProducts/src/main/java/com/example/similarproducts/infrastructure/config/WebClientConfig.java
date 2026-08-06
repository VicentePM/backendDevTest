package com.example.similarproducts.infrastructure.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient upstreamWebClient(AppConfig appConfig) {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) appConfig.timeoutMs())
            .responseTimeout(Duration.ofMillis(appConfig.timeoutMs()));

    return WebClient.builder()
        .baseUrl(appConfig.baseUrl())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }
}
