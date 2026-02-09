package com.moltrank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoltrankApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoltrankApplication.class, args);
    }
}
