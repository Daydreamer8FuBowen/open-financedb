package com.fbw.finance.openfinancedb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenFinancedbApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenFinancedbApplication.class, args);
    }

}
