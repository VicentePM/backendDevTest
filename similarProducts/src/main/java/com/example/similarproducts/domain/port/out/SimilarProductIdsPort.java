package com.example.similarproducts.domain.port.out;

import java.util.List;
import reactor.core.publisher.Mono;

public interface SimilarProductIdsPort {
  /** Returns the list of similar product IDs. Emits {@code ProductNotFoundException} on 404. */
  Mono<List<String>> fetchSimilarIds(String productId);
}
