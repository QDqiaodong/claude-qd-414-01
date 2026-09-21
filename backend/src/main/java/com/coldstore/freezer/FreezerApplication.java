package com.coldstore.freezer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class FreezerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FreezerApplication.class, args);
    }
}
