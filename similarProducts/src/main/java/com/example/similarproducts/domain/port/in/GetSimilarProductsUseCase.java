package com.example.similarproducts.domain.port.in;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import java.util.List;
import reactor.core.publisher.Mono;

public interface GetSimilarProductsUseCase {
  Mono<List<ProductDetail>> handle(SimilarProductsQuery query);
}
