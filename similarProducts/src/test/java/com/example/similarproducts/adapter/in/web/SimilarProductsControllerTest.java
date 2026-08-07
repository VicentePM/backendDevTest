package com.example.similarproducts.adapter.in.web;

import static org.mockito.Mockito.when;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest({SimilarProductsController.class, GlobalExceptionHandler.class})
class SimilarProductsControllerTest {

  @Autowired private WebTestClient webTestClient;

  @MockBean private GetSimilarProductsUseCase useCase;

  // ── Task 4.1 / 4.2: 200 with JSON list ──────────────────────────────────

  @Test
  void getSimilarProducts_returns200WithList_whenProductsExist() {
    var query = new SimilarProductsQuery("1");
    var detail2 = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
    var detail3 = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);

    when(useCase.handle(query)).thenReturn(Mono.just(List.of(detail2, detail3)));

    webTestClient
        .get()
        .uri("/product/1/similar")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0].id").isEqualTo("2")
        .jsonPath("$[0].name").isEqualTo("Dress")
        .jsonPath("$[0].price").isEqualTo(19.99)
        .jsonPath("$[0].availability").isEqualTo(true)
        .jsonPath("$[1].id").isEqualTo("3");
  }

  @Test
  void getSimilarProducts_returns200WithEmptyList_whenNoSimilarProducts() {
    var query = new SimilarProductsQuery("99");
    when(useCase.handle(query)).thenReturn(Mono.just(List.of()));

    webTestClient
        .get()
        .uri("/product/99/similar")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0);
  }

  // ── Task 4.3 / 4.4: 404 when ProductNotFoundException ───────────────────

  @Test
  void getSimilarProducts_returns404_whenProductNotFound() {
    var query = new SimilarProductsQuery("999");
    when(useCase.handle(query))
        .thenReturn(Mono.error(new ProductNotFoundException("999")));

    webTestClient
        .get()
        .uri("/product/999/similar")
        .exchange()
        .expectStatus().isNotFound();
  }
}
