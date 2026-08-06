package com.example.similarproducts;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsIntegrationTest {

  private static WireMockServer wireMock;

  @LocalServerPort private int port;

  @Autowired private WebTestClient webTestClient;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(3001));
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @DynamicPropertySource
  static void configureUpstream(DynamicPropertyRegistry registry) {
    registry.add("upstream.base-url", () -> "http://localhost:3001");
  }

  @BeforeEach
  void resetStubs() {
    wireMock.resetAll();
  }

  // ── Scenario 1: productId=1, all return 200 ──────────────────────────────

  @Test
  void scenario1_productId1_allSimilarProductsReturned() {
    wireMock.stubFor(
        get(urlEqualTo("/product/1/similarids"))
            .willReturn(okJson("[\"2\",\"3\",\"4\"]")));

    wireMock.stubFor(
        get(urlEqualTo("/product/2"))
            .willReturn(okJson("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}")));
    wireMock.stubFor(
        get(urlEqualTo("/product/3"))
            .willReturn(okJson("{\"id\":\"3\",\"name\":\"Blazer\",\"price\":29.99,\"availability\":false}")));
    wireMock.stubFor(
        get(urlEqualTo("/product/4"))
            .willReturn(okJson("{\"id\":\"4\",\"name\":\"Boots\",\"price\":39.99,\"availability\":true}")));

    webTestClient
        .get()
        .uri("/product/1/similar")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(3)
        .jsonPath("$[0].id").isEqualTo("2")
        .jsonPath("$[1].id").isEqualTo("3")
        .jsonPath("$[2].id").isEqualTo("4");
  }

  // ── Scenario 2: productId=2, one product times out (5s > 3s timeout) ─────

  @Test
  void scenario2_productId2_slowProductSkipped() {
    wireMock.stubFor(
        get(urlEqualTo("/product/2/similarids"))
            .willReturn(okJson("[\"20\",\"21\"]")));

    wireMock.stubFor(
        get(urlEqualTo("/product/20"))
            .willReturn(okJson("{\"id\":\"20\",\"name\":\"Fast\",\"price\":10.00,\"availability\":true}")));
    wireMock.stubFor(
        get(urlEqualTo("/product/21"))
            .willReturn(
                aResponse()
                    .withFixedDelay(5000)
                    .withStatus(200)
                    .withBody("{\"id\":\"21\",\"name\":\"Slow\",\"price\":20.00,\"availability\":true}")
                    .withHeader("Content-Type", "application/json")));

    webTestClient
        .get()
        .uri("/product/2/similar")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo("20");
  }

  // ── Scenario 3: productId=4, one similar product returns 404 → skip ──────

  @Test
  void scenario3_productId4_partialNotFoundSkipped() {
    wireMock.stubFor(
        get(urlEqualTo("/product/4/similarids"))
            .willReturn(okJson("[\"40\",\"41\"]")));

    wireMock.stubFor(
        get(urlEqualTo("/product/40"))
            .willReturn(okJson("{\"id\":\"40\",\"name\":\"Hat\",\"price\":15.00,\"availability\":true}")));
    wireMock.stubFor(
        get(urlEqualTo("/product/41"))
            .willReturn(aResponse().withStatus(404)));

    webTestClient
        .get()
        .uri("/product/4/similar")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo("40");
  }

  // ── Scenario 4: productId=5, one similar product returns 500 → skip ──────

  @Test
  void scenario4_productId5_partialServerErrorSkipped() {
    wireMock.stubFor(
        get(urlEqualTo("/product/5/similarids"))
            .willReturn(okJson("[\"50\",\"51\"]")));

    wireMock.stubFor(
        get(urlEqualTo("/product/50"))
            .willReturn(okJson("{\"id\":\"50\",\"name\":\"Scarf\",\"price\":9.99,\"availability\":false}")));
    wireMock.stubFor(
        get(urlEqualTo("/product/51"))
            .willReturn(aResponse().withStatus(500)));

    webTestClient
        .get()
        .uri("/product/5/similar")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo("50");
  }

  // ── Scenario 5: productId=999, similarids 404 → our endpoint returns 404 ─

  @Test
  void scenario5_productId999_notFoundPropagated() {
    wireMock.stubFor(
        get(urlEqualTo("/product/999/similarids"))
            .willReturn(aResponse().withStatus(404)));

    webTestClient
        .get()
        .uri("/product/999/similar")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
  }
}
