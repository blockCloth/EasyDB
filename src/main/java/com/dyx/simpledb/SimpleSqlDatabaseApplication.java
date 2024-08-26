package com.dyx.simpledb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SimpleSqlDatabaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleSqlDatabaseApplication.class, args);
    }
}
