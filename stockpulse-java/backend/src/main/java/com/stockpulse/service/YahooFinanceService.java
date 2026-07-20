package com.stockpulse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.model.StockQuote;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class YahooFinanceService {

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Cacheable(value = "stock-history", key = "#ticker + '-' + #start + '-' + #end")
    public List<StockQuote> fetchHistory(String ticker, LocalDate start, LocalDate end) {
        try {
            long p1 = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            long p2 = end.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toEpochSecond();
            
            String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d",
                    ticker, p1, p2);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Collections.emptyList();
            }

            return parseYahooResponse(response.body());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<StockQuote> parseYahooResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode result = root.path("chart").path("result").get(0);
        if (result == null || result.isNull()) {
            return Collections.emptyList();
        }

        JsonNode timestamps = result.path("timestamp");
        JsonNode indicators = result.path("indicators").path("quote").get(0);
        
        if (timestamps == null || timestamps.isNull() || indicators == null || indicators.isNull()) {
            return Collections.emptyList();
        }

        JsonNode openNode = indicators.path("open");
        JsonNode highNode = indicators.path("high");
        JsonNode lowNode = indicators.path("low");
        JsonNode closeNode = indicators.path("close");
        JsonNode volumeNode = indicators.path("volume");

        List<StockQuote> quotes = new ArrayList<>();
        
        double lastOpen = 0.0;
        double lastHigh = 0.0;
        double lastLow = 0.0;
        double lastClose = 0.0;
        long lastVolume = 0;

        for (int i = 0; i < timestamps.size(); i++) {
            long epochSec = timestamps.get(i).asLong();
            LocalDate date = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate();

            JsonNode oVal = openNode.get(i);
            JsonNode hVal = highNode.get(i);
            JsonNode lVal = lowNode.get(i);
            JsonNode cVal = closeNode.get(i);
            JsonNode vVal = volumeNode.get(i);

            double open = (oVal == null || oVal.isNull()) ? lastOpen : oVal.asDouble();
            double high = (hVal == null || hVal.isNull()) ? lastHigh : hVal.asDouble();
            double low = (lVal == null || lVal.isNull()) ? lastLow : lVal.asDouble();
            double close = (cVal == null || cVal.isNull()) ? lastClose : cVal.asDouble();
            long volume = (vVal == null || vVal.isNull()) ? lastVolume : vVal.asLong();

            // Skip records that remain empty if first trades haven't filled
            if (open == 0.0 && lastOpen == 0.0) {
                continue;
            }

            quotes.add(new StockQuote(date, open, high, low, close, volume));

            lastOpen = open;
            lastHigh = high;
            lastLow = low;
            lastClose = close;
            lastVolume = volume;
        }

        return quotes;
    }
}
