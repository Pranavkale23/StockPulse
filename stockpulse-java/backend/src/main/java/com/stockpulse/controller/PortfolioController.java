package com.stockpulse.controller;

import com.stockpulse.model.Holding;
import com.stockpulse.model.PortfolioMetrics;
import com.stockpulse.model.PortfolioRequest;
import com.stockpulse.service.KafkaProducerService;
import com.stockpulse.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PortfolioController {

    private final MetricsService metricsService;
    private final KafkaProducerService kafkaProducerService;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> simulationTask;
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public PortfolioController(MetricsService metricsService, KafkaProducerService kafkaProducerService) {
        this.metricsService = metricsService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostMapping("/portfolio/calculate")
    public ResponseEntity<PortfolioMetrics> calculatePortfolio(@RequestBody PortfolioRequest request) {
        LocalDate start = LocalDate.parse(request.getStartDate());
        LocalDate end = LocalDate.parse(request.getEndDate());
        
        PortfolioMetrics metrics = metricsService.computePortfolioMetrics(
                request.getHoldings(),
                start,
                end,
                request.getBenchmarkTicker(),
                request.getRiskFreeRate()
        );
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/scanner/scan")
    public ResponseEntity<Map<String, String>> triggerScan() {
        kafkaProducerService.sendScanRequest();
        Map<String, String> response = new HashMap<>();
        response.put("status", "scanning_requested");
        response.put("message", "Scan task has been queued to Kafka.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/live/simulate")
    public ResponseEntity<Map<String, String>> toggleSimulation(
            @RequestParam boolean enable,
            @RequestBody List<Holding> holdings) {
        
        Map<String, String> response = new HashMap<>();

        if (enable) {
            if (simulationTask != null && !simulationTask.isDone()) {
                response.put("status", "already_running");
                return ResponseEntity.ok(response);
            }

            // Initialize base price placeholders
            for (Holding h : holdings) {
                currentPrices.put(h.getTicker(), 100.0 + random.nextDouble() * 200.0);
            }

            simulationTask = scheduler.scheduleAtFixedRate(() -> {
                for (String ticker : currentPrices.keySet()) {
                    double currentPrice = currentPrices.get(ticker);
                    // fluctuate price slightly (-1.5% to +1.5%)
                    double pct = (random.nextDouble() - 0.5) * 0.03;
                    double newPrice = currentPrice * (1.0 + pct);
                    currentPrices.put(ticker, newPrice);

                    // Send live tick to Kafka
                    kafkaProducerService.sendLivePrice(ticker, newPrice);
                }
            }, 0, 3, TimeUnit.SECONDS);

            response.put("status", "started");
            response.put("message", "Live price simulation started. Emitting to Kafka stock-prices topic every 3 seconds.");
        } else {
            if (simulationTask != null) {
                simulationTask.cancel(true);
                simulationTask = null;
            }
            response.put("status", "stopped");
            response.put("message", "Live price simulation stopped.");
        }

        return ResponseEntity.ok(response);
    }
}
