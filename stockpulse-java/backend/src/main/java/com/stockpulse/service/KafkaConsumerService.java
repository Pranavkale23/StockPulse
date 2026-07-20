package com.stockpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.controller.LivePriceWebSocketHandler;
import com.stockpulse.model.ScanResult;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KafkaConsumerService {

    private final LivePriceWebSocketHandler webSocketHandler;
    private final PatternScannerService patternScannerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaConsumerService(LivePriceWebSocketHandler webSocketHandler, PatternScannerService patternScannerService) {
        this.webSocketHandler = webSocketHandler;
        this.patternScannerService = patternScannerService;
    }

    @KafkaListener(topics = "stock-prices", groupId = "stock-analysis-group")
    public void consumePrice(String message) {
        // Broadcast the live tick payload straight to connected WebSocket clients
        webSocketHandler.broadcastMessage("TICK", message);
    }

    @KafkaListener(topics = "pattern-scans", groupId = "stock-analysis-group")
    public void consumeScanRequest(String message) {
        if ("START_SCAN".equals(message)) {
            try {
                webSocketHandler.broadcastMessage("SCAN_STATUS", "Scanning started...");
                List<ScanResult> results = patternScannerService.scanMarkets();
                String resultsJson = objectMapper.writeValueAsString(results);
                webSocketHandler.broadcastMessage("SCAN_COMPLETE", resultsJson);
            } catch (Exception e) {
                e.printStackTrace();
                webSocketHandler.broadcastMessage("SCAN_ERROR", "Scanning failed: " + e.getMessage());
            }
        }
    }
}
