package com.example.similarproducts.adapter.out.http;

import com.example.similarproducts.adapter.out.http.dto.ProductDetailResponse;
import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import com.example.similarproducts.domain.port.out.ProductDetailPort;
import com.example.similarproducts.domain.port.out.SimilarProductIdsPort;
import com.example.similarproducts.infrastructure.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class ProductApiAdapter implements SimilarProductIdsPort, ProductDetailPort {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WebClient webClient;
  private final long timeoutMs;
  private final CacheManager cacheManager;

  /** Spring-managed constructor — uses AppConfig for timeout. */
  @Autowired
  public ProductApiAdapter(WebClient upstreamWebClient, AppConfig appConfig, CacheManager cacheManager) {
    this.webClient = upstreamWebClient;
    this.timeoutMs = appConfig.timeoutMs();
    this.cacheManager = cacheManager;
  }

  /** Test constructor — allows injecting timeout directly without Spring context. */
  ProductApiAdapter(WebClient webClient, long timeoutMs) {
    this.webClient = webClient;
    this.timeoutMs = timeoutMs;
    this.cacheManager = new NoOpCacheManager();
  }

  @Override
  @CircuitBreaker(name = "similarIds", fallbackMethod = "fetchSimilarIdsFallback")
  public Mono<List<String>> fetchSimilarIds(String productId) {
    return webClient
        .get()
        .uri("/product/{id}/similarids", productId)
        .retrieve()
        .onStatus(
            status -> status == HttpStatus.NOT_FOUND,
            response -> Mono.error(new ProductNotFoundException(productId)))
        .bodyToMono(String.class)
        .map(
            body -> {
              try {
                return MAPPER.<List<String>>readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
              } catch (Exception e) {
                throw new RuntimeException("Failed to parse similar ids", e);
              }
            })
        .timeout(Duration.ofMillis(timeoutMs));
  }

  @SuppressWarnings("unused")
  private Mono<List<String>> fetchSimilarIdsFallback(String productId, Throwable ex) {
    if (ex instanceof ProductNotFoundException) {
      return Mono.error(ex);
    }
    return Mono.just(List.of());
  }

  @Override
  public Mono<ProductDetail> fetchDetail(String productId) {
    // Manual cache lookup — @Cacheable does not unwrap Mono<T> with CaffeineCacheManager.
    Cache cache = cacheManager.getCache("productDetails");
    if (cache != null) {
      Cache.ValueWrapper cached = cache.get(productId);
      if (cached != null) {
        @SuppressWarnings("unchecked")
        Mono<ProductDetail> hit = (Mono<ProductDetail>) cached.get();
        return hit;
      }
    }

    // .cache() memoizes the emitted value so multiple subscribers share one HTTP call.
    // doOnError evicts the entry so transient errors (timeout, 5xx) are never cached permanently.
    Mono<ProductDetail> upstream = webClient
        .get()
        .uri("/product/{id}", productId)
        .retrieve()
        .onStatus(
            status -> status.isError(),
            response ->
                Mono.error(
                    new WebClientResponseException(
                        response.statusCode().value(),
                        "Upstream error for product " + productId,
                        null,
                        null,
                        null)))
        .bodyToMono(ProductDetailResponse.class)
        .map(dto -> new ProductDetail(dto.id(), dto.name(), dto.price(), dto.availability()))
        .timeout(Duration.ofMillis(timeoutMs))
        .doOnError(ex -> { if (cache != null) cache.evict(productId); })
        .cache();

    if (cache != null) {
      cache.put(productId, upstream);
    }
    return upstream;
  }
}
