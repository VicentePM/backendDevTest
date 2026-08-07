package com.example.similarproducts.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upstream")
public record AppConfig(String baseUrl, long timeoutMs, long responseTimeoutMs) {}
