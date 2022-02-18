package com.fronzec.myservice;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class SpringBatchServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpringBatchServiceApplication.class, args);
  }
}
