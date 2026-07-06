package com.oac.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {"com.oac.sample", "com.oac.enforcement"})
@EnableMongoRepositories
public class SampleOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleOrderServiceApplication.class, args);
    }
}