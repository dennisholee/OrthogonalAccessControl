package com.oac.decision.bdd;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import io.cucumber.spring.CucumberContextConfiguration;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mongodb")
public class CucumberSpringConfiguration {

    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Value("${local.server.port}")
    private int instancePort;

    private static int port;

    static {
        mongoDBContainer.start();
    }

    @PostConstruct
    void init() {
        port = instancePort;
    }

    public static int getPort() {
        return port;
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }
}