package com.community.water;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableJpaRepositories(considerNestedRepositories = true)
@SpringBootApplication
public class WaterFilterApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaterFilterApplication.class, args);
    }
}
