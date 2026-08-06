package com.example.similarproducts.domain.port.out;

import com.example.similarproducts.domain.model.ProductDetail;
import reactor.core.publisher.Mono;

public interface ProductDetailPort {
  /** Fetches a single product detail. Caller handles errors (skip on failure). */
  Mono<ProductDetail> fetchDetail(String productId);
}
