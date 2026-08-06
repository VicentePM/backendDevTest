package com.example.similarproducts.domain.service;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import com.example.similarproducts.domain.port.out.ProductDetailPort;
import com.example.similarproducts.domain.port.out.SimilarProductIdsPort;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimilarProductsService implements GetSimilarProductsUseCase {

  private final SimilarProductIdsPort similarProductIdsPort;
  private final ProductDetailPort productDetailPort;

  public SimilarProductsService(
      SimilarProductIdsPort similarProductIdsPort, ProductDetailPort productDetailPort) {
    this.similarProductIdsPort = similarProductIdsPort;
    this.productDetailPort = productDetailPort;
  }

  @Override
  public Mono<List<ProductDetail>> handle(SimilarProductsQuery query) {
    return similarProductIdsPort
        .fetchSimilarIds(query.productId())
        .flatMapMany(Flux::fromIterable)
        .flatMapSequential(
            id ->
                productDetailPort
                    .fetchDetail(id)
                    .onErrorResume(ex -> Mono.empty()))
        .collectList();
  }
}
