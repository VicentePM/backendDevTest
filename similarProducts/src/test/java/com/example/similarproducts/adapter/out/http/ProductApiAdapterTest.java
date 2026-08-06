package com.example.similarproducts.adapter.out.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ProductApiAdapterTest {

  private static WireMockServer wireMock;
  private ProductApiAdapter adapter;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @BeforeEach
  void setUp() {
    wireMock.resetAll();
    WebClient client = WebClient.builder().baseUrl("http://localhost:" + wireMock.port()).build();
    adapter = new ProductApiAdapter(client, 3000L);
  }

  // ── Task 3.1 / 3.2: fetchSimilarIds ─────────────────────────────────────

  @Test
  void fetchSimilarIds_returnsIds_whenUpstreamReturns200() {
    wireMock.stubFor(
        get(urlEqualTo("/product/1/similarids"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("[\"2\",\"3\",\"4\"]")));

    StepVerifier.create(adapter.fetchSimilarIds("1"))
        .assertNext(ids -> {
          assertThat(ids).containsExactly("2", "3", "4");
        })
        .verifyComplete();
  }

  @Test
  void fetchSimilarIds_throwsProductNotFoundException_when404() {
    wireMock.stubFor(
        get(urlEqualTo("/product/999/similarids"))
            .willReturn(aResponse().withStatus(404)));

    StepVerifier.create(adapter.fetchSimilarIds("999"))
        .expectError(ProductNotFoundException.class)
        .verify();
  }

  // ── Task 3.3 / 3.4: fetchDetail ─────────────────────────────────────────

  @Test
  void fetchDetail_returnsProductDetail_whenUpstreamReturns200() {
    wireMock.stubFor(
        get(urlEqualTo("/product/2"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));

    StepVerifier.create(adapter.fetchDetail("2"))
        .assertNext(detail -> {
          assertThat(detail.id()).isEqualTo("2");
          assertThat(detail.name()).isEqualTo("Dress");
          assertThat(detail.availability()).isTrue();
        })
        .verifyComplete();
  }

  @Test
  void fetchDetail_propagatesError_when404() {
    wireMock.stubFor(
        get(urlEqualTo("/product/404product"))
            .willReturn(aResponse().withStatus(404)));

    StepVerifier.create(adapter.fetchDetail("404product"))
        .expectError()
        .verify();
  }

  @Test
  void fetchDetail_propagatesError_when500() {
    wireMock.stubFor(
        get(urlEqualTo("/product/errProduct"))
            .willReturn(aResponse().withStatus(500)));

    StepVerifier.create(adapter.fetchDetail("errProduct"))
        .expectError()
        .verify();
  }

  @Test
  void fetchSimilarIds_returnsEmptyList_whenUpstreamReturnsEmptyArray() {
    wireMock.stubFor(
        get(urlEqualTo("/product/emptyProduct/similarids"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("[]")));

    StepVerifier.create(adapter.fetchSimilarIds("emptyProduct"))
        .assertNext(ids -> assertThat(ids).isEmpty())
        .verifyComplete();
  }
}
