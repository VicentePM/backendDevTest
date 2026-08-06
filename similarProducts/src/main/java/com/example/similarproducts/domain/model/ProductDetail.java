package com.example.similarproducts.domain.model;

import java.math.BigDecimal;

public record ProductDetail(String id, String name, BigDecimal price, Boolean availability) {}
