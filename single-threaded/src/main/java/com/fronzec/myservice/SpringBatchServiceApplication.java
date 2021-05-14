package com.fronzec.myservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringBatchServiceApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(SpringBatchServiceApplication.class, args)));

    }
}
