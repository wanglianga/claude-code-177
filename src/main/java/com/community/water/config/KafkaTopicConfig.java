package com.community.water.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
public class KafkaTopicConfig {

    public static final String TELEMETRY_TOPIC = "telemetry.v1";
    public static final String DISPENSE_TOPIC = "dispense.v1";
    public static final String DEVICE_EVENT_TOPIC = "device-events.v1";

    @Bean
    public NewTopic telemetryTopic() {
        return new NewTopic(TELEMETRY_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic dispenseTopic() {
        return new NewTopic(DISPENSE_TOPIC, 1, (short) 1);
    }

    @Bean
    public NewTopic deviceEventTopic() {
        return new NewTopic(DEVICE_EVENT_TOPIC, 1, (short) 1);
    }
}
