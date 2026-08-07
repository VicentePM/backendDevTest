package com.example.similarproducts.domain.service;

import static org.mockito.Mockito.when;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import com.example.similarproducts.domain.port.out.ProductDetailPort;
import com.example.similarproducts.domain.port.out.SimilarProductIdsPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SimilarProductsServiceTest {

  @Mock private SimilarProductIdsPort similarProductIdsPort;
  @Mock private ProductDetailPort productDetailPort;

  private SimilarProductsService service;

  @BeforeEach
  void setUp() {
    service = new SimilarProductsService(
        similarProductIdsPort, productDetailPort, Duration.ofSeconds(10));
  }

  // ── Task 2.1 / 2.2: happy path ──────────────────────────────────────────

  @Test
  void handle_returnsSimilarProducts_whenAllDetailCallsSucceed() {
    var query = new SimilarProductsQuery("1");
    var detail2 = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
    var detail3 = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);

    when(similarProductIdsPort.fetchSimilarIds("1")).thenReturn(Mono.just(List.of("2", "3")));
    when(productDetailPort.fetchDetail("2")).thenReturn(Mono.just(detail2));
    when(productDetailPort.fetchDetail("3")).thenReturn(Mono.just(detail3));

    StepVerifier.create(service.handle(query))
        .expectNextMatches(
            list -> list.size() == 2 && list.get(0).id().equals("2") && list.get(1).id().equals("3"))
        .verifyComplete();
  }

  @Test
  void handle_returnsEmptyList_whenNoSimilarIds() {
    var query = new SimilarProductsQuery("99");
    when(similarProductIdsPort.fetchSimilarIds("99")).thenReturn(Mono.just(List.of()));

    StepVerifier.create(service.handle(query))
        .expectNextMatches(List::isEmpty)
        .verifyComplete();
  }

  // ── Task 2.3 / 2.4: ProductNotFoundException when similarids 404 ────────

  @Test
  void handle_throwsProductNotFoundException_whenSimilarIdsMissing() {
    var query = new SimilarProductsQuery("999");
    when(similarProductIdsPort.fetchSimilarIds("999"))
        .thenReturn(Mono.error(new ProductNotFoundException("999")));

    StepVerifier.create(service.handle(query))
        .expectError(ProductNotFoundException.class)
        .verify();
  }

  // ── Task 2.5 / 2.6: skip on timeout / failure ───────────────────────────

  @Test
  void handle_skipsProduct_whenDetailCallFails() {
    var query = new SimilarProductsQuery("2");
    var detail3 = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);

    when(similarProductIdsPort.fetchSimilarIds("2")).thenReturn(Mono.just(List.of("3", "4")));
    when(productDetailPort.fetchDetail("3")).thenReturn(Mono.just(detail3));
    when(productDetailPort.fetchDetail("4")).thenReturn(Mono.error(new RuntimeException("timeout")));

    StepVerifier.create(service.handle(query))
        .expectNextMatches(list -> list.size() == 1 && list.get(0).id().equals("3"))
        .verifyComplete();
  }

  @Test
  void handle_skipsAll_whenAllDetailCallsFail() {
    var query = new SimilarProductsQuery("5");
    when(similarProductIdsPort.fetchSimilarIds("5")).thenReturn(Mono.just(List.of("10", "11")));
    when(productDetailPort.fetchDetail("10")).thenReturn(Mono.error(new RuntimeException("500")));
    when(productDetailPort.fetchDetail("11")).thenReturn(Mono.error(new RuntimeException("404")));

    StepVerifier.create(service.handle(query))
        .expectNextMatches(List::isEmpty)
        .verifyComplete();
  }

  @Test
  void handle_returnsEmptyList_whenFetchSimilarIdsFailsWithNonProductNotFoundError() {
    var query = new SimilarProductsQuery("1");
    when(similarProductIdsPort.fetchSimilarIds("1"))
        .thenReturn(Mono.error(new RuntimeException("CB open")));

    StepVerifier.create(service.handle(query))
        .expectNextMatches(List::isEmpty)
        .verifyComplete();
  }
}
