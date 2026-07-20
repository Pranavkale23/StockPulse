package com.stockpulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic stockPricesTopic() {
        return TopicBuilder.name("stock-prices")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic patternScansTopic() {
        return TopicBuilder.name("pattern-scans")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
