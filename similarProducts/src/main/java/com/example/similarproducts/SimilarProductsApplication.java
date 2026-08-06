package com.example.similarproducts;

import com.example.similarproducts.infrastructure.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class SimilarProductsApplication {

  public static void main(String[] args) {
    SpringApplication.run(SimilarProductsApplication.class, args);
  }
}
