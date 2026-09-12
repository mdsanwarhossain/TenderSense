package com.bracit.tendersense.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({EgpProperties.class, LlmProperties.class, ProcessingProperties.class, WorldBankProperties.class, ScheduleProperties.class,
        UngmProperties.class, IsdbProperties.class, BracProperties.class})
public class AppConfig {

    @Bean
    RestClient restClient() {
        return RestClient.builder().build();
    }
}
