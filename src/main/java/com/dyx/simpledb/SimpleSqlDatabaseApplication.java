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

    // @Bean
    // public CommandLineRunner commandLineRunner() {
    //     return args -> {
    //         Launcher launcher = new Launcher();
    //         // 这里可能需要处理异常或者在Launcher类中移除main方法抛出的异常
    //         launcher.main(args);
    //     };
    // }
}
