package com.example.similarproducts.domain.service;

import com.example.similarproducts.domain.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import com.example.similarproducts.domain.port.out.ProductDetailPort;
import com.example.similarproducts.domain.port.out.SimilarProductIdsPort;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SimilarProductsService implements GetSimilarProductsUseCase {

  private static final Logger log = LoggerFactory.getLogger(SimilarProductsService.class);

  private final SimilarProductIdsPort similarProductIdsPort;
  private final ProductDetailPort productDetailPort;
  private final Duration responseTimeout;

  public SimilarProductsService(
      SimilarProductIdsPort similarProductIdsPort,
      ProductDetailPort productDetailPort,
      Duration responseTimeout) {
    this.similarProductIdsPort = similarProductIdsPort;
    this.productDetailPort = productDetailPort;
    this.responseTimeout = responseTimeout;
  }

  @Override
  public Mono<List<ProductDetail>> handle(SimilarProductsQuery query) {
    return similarProductIdsPort
        .fetchSimilarIds(query.productId())
        .onErrorResume(
            ex -> !(ex instanceof ProductNotFoundException),
            ex -> {
              log.warn(
                  "fetchSimilarIds failed for productId={}, returning empty. Cause: {}",
                  query.productId(),
                  ex.getMessage());
              return Mono.just(List.of());
            })
        .flatMapMany(Flux::fromIterable)
        .flatMapSequential(
            id ->
                productDetailPort
                    .fetchDetail(id)
                    .onErrorResume(ex -> Mono.empty()))
        .collectList()
        .timeout(responseTimeout, Mono.just(List.of()));
  }
}
