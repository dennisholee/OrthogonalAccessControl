package com.oac.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.oac.example", "com.oac.enforcement"})
public class NonIntrusiveEntitlementApplication {

    public static void main(String[] args) {
        SpringApplication.run(NonIntrusiveEntitlementApplication.class, args);
    }
}