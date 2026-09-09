package com.bracit.tendersense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TenderSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenderSenseApplication.class, args);
    }

}
