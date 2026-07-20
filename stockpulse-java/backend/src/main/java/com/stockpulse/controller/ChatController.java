package com.stockpulse.controller;

import com.stockpulse.service.SpringAiService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final SpringAiService springAiService;

    public ChatController(SpringAiService springAiService) {
        this.springAiService = springAiService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequest request) {
        String systemPrompt = String.format(
                "You are an expert quantitative analyst and portfolio risk advisor. " +
                "Evaluate this portfolio: Annual Return: %.2f%%, Volatility: %.2f%%, Sharpe: %.2f, VaR: %.2f%%. " +
                "The user asks: %s",
                request.getAnnReturn() * 100.0,
                request.getAnnVol() * 100.0,
                request.getSharpe(),
                request.getVar95() * 100.0,
                request.getMessage()
        );

        String responseText = springAiService.generatePortfolioInsights(
                systemPrompt,
                request.getAnnReturn(),
                request.getAnnVol(),
                request.getSharpe(),
                request.getVar95()
        );

        Map<String, String> response = new HashMap<>();
        response.put("response", responseText);
        return ResponseEntity.ok(response);
    }

    @Data
    public static class ChatRequest {
        private String message;
        private double annReturn;
        private double annVol;
        private double sharpe;
        private double var95;
    }
}
