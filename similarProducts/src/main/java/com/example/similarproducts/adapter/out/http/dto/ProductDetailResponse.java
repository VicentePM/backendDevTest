package com.example.similarproducts.adapter.out.http.dto;

import java.math.BigDecimal;

public record ProductDetailResponse(String id, String name, BigDecimal price, Boolean availability) {}
