package com.example.similarproducts.adapter.in.web;

import com.example.similarproducts.adapter.in.web.generated.SimilarProductsApi;
import com.example.similarproducts.adapter.in.web.generated.model.ProductDetail;
import com.example.similarproducts.domain.model.SimilarProductsQuery;
import com.example.similarproducts.domain.port.in.GetSimilarProductsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class SimilarProductsController implements SimilarProductsApi {

  private final GetSimilarProductsUseCase useCase;

  public SimilarProductsController(GetSimilarProductsUseCase useCase) {
    this.useCase = useCase;
  }

  @Override
  public Mono<ResponseEntity<Flux<ProductDetail>>> getProductSimilar(
      String productId, ServerWebExchange exchange) {
    Flux<ProductDetail> result =
        useCase
            .handle(new SimilarProductsQuery(productId))
            .flatMapMany(Flux::fromIterable)
            .map(
                d ->
                    new ProductDetail(d.id(), d.name(), d.price(), d.availability()));
    return Mono.just(ResponseEntity.ok(result));
  }
}
