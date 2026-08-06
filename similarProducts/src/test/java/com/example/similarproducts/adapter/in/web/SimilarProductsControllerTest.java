package com.example.similarproducts.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

@WebMvcTest({SimilarProductsController.class, GlobalExceptionHandler.class})
class SimilarProductsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private GetSimilarProductsUseCase useCase;

  // ── Task 4.1 / 4.2: 200 with JSON list ──────────────────────────────────

  @Test
  void getSimilarProducts_returns200WithList_whenProductsExist() throws Exception {
    var query = new SimilarProductsQuery("1");
    var detail2 = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
    var detail3 = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);

    when(useCase.handle(query)).thenReturn(Mono.just(List.of(detail2, detail3)));

    mockMvc
        .perform(get("/product/1/similar").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("2"))
        .andExpect(jsonPath("$[0].name").value("Dress"))
        .andExpect(jsonPath("$[0].price").value(19.99))
        .andExpect(jsonPath("$[0].availability").value(true))
        .andExpect(jsonPath("$[1].id").value("3"));
  }

  @Test
  void getSimilarProducts_returns200WithEmptyList_whenNoSimilarProducts() throws Exception {
    var query = new SimilarProductsQuery("99");
    when(useCase.handle(query)).thenReturn(Mono.just(List.of()));

    mockMvc
        .perform(get("/product/99/similar").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // ── Task 4.3 / 4.4: 404 when ProductNotFoundException ───────────────────

  @Test
  void getSimilarProducts_returns404_whenProductNotFound() throws Exception {
    var query = new SimilarProductsQuery("999");
    when(useCase.handle(query))
        .thenReturn(Mono.error(new ProductNotFoundException("999")));

    mockMvc
        .perform(get("/product/999/similar").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }
}
