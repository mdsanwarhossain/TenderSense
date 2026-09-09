package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.schedule")
public class ScheduleProperties {

    /** Disabled by the demo profile so nothing fires mid-presentation. */
    private boolean enabled = true;

    private String zone = "Asia/Dhaka";
}
