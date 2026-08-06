package com.example.similarproducts.adapter.in.web;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimilarProductsController {

  private final GetSimilarProductsUseCase useCase;

  public SimilarProductsController(GetSimilarProductsUseCase useCase) {
    this.useCase = useCase;
  }

  @GetMapping("/product/{productId}/similar")
  public ResponseEntity<List<ProductDetail>> getSimilarProducts(
      @PathVariable String productId) {
    List<ProductDetail> result =
        useCase.handle(new SimilarProductsQuery(productId)).block();
    return ResponseEntity.ok(result);
  }
}
