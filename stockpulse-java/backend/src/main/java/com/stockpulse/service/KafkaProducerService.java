package com.stockpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendLivePrice(String ticker, double price) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("ticker", ticker);
            payload.put("price", price);
            payload.put("timestamp", System.currentTimeMillis());

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("stock-prices", ticker, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendScanRequest() {
        try {
            kafkaTemplate.send("pattern-scans", "trigger", "START_SCAN");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
