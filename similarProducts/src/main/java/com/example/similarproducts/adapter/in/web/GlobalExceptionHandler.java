package com.example.similarproducts.adapter.in.web;

import com.example.similarproducts.domain.model.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleProductNotFound(ProductNotFoundException ex) {
    // 404 with empty body — matches k6 expectation
  }
}
