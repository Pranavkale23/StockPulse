package com.stockpulse.service;

import com.stockpulse.model.ScanResult;
import com.stockpulse.model.StockQuote;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatternScannerService {

    private final YahooFinanceService yahooFinanceService;

    private static final List<String> SCAN_LIST = Arrays.asList(
            // US Stocks
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "JPM", "V", "JNJ",
            "WMT", "PG", "MA", "HD", "CVX", "LLY", "UBER", "NFLX", "AMD", "INTC",
            // Indian Stocks
            "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "ICICIBANK.NS", "INFY.NS",
            "ITC.NS", "SBIN.NS", "BHARTIARTL.NS", "BAJFINANCE.NS", "LARSEN.NS",
            "KOTAKBANK.NS", "HCLTECH.NS", "ASIANPAINT.NS", "TITAN.NS", "TATAMOTORS.NS",
            "MARUTI.NS", "SUNPHARMA.NS", "TATASTEEL.NS", "ULTRACEMCO.NS", "WIPRO.NS", "M&M.NS"
    );

    private static final Map<String, String> TICKER_NAMES = Map.ofEntries(
            Map.entry("AAPL", "Apple Inc."),
            Map.entry("MSFT", "Microsoft Corporation"),
            Map.entry("GOOGL", "Alphabet Inc."),
            Map.entry("AMZN", "Amazon.com, Inc."),
            Map.entry("META", "Meta Platforms, Inc."),
            Map.entry("TSLA", "Tesla, Inc."),
            Map.entry("NVDA", "NVIDIA Corporation"),
            Map.entry("JPM", "JPMorgan Chase & Co."),
            Map.entry("V", "Visa Inc."),
            Map.entry("JNJ", "Johnson & Johnson"),
            Map.entry("WMT", "Walmart Inc."),
            Map.entry("PG", "Procter & Gamble Co."),
            Map.entry("MA", "Mastercard Incorporated"),
            Map.entry("HD", "Home Depot, Inc."),
            Map.entry("CVX", "Chevron Corporation"),
            Map.entry("LLY", "Eli Lilly and Company"),
            Map.entry("UBER", "Uber Technologies, Inc."),
            Map.entry("NFLX", "Netflix, Inc."),
            Map.entry("AMD", "Advanced Micro Devices, Inc."),
            Map.entry("INTC", "Intel Corporation"),
            Map.entry("RELIANCE.NS", "Reliance Industries Limited"),
            Map.entry("TCS.NS", "Tata Consultancy Services Limited"),
            Map.entry("HDFCBANK.NS", "HDFC Bank Limited"),
            Map.entry("ICICIBANK.NS", "ICICI Bank Limited"),
            Map.entry("INFY.NS", "Infosys Limited"),
            Map.entry("ITC.NS", "ITC Limited"),
            Map.entry("SBIN.NS", "State Bank of India"),
            Map.entry("BHARTIARTL.NS", "Bharti Airtel Limited"),
            Map.entry("BAJFINANCE.NS", "Bajaj Finance Limited"),
            Map.entry("LARSEN.NS", "Larsen & Toubro Limited"),
            Map.entry("KOTAKBANK.NS", "Kotak Mahindra Bank Limited"),
            Map.entry("HCLTECH.NS", "HCL Technologies Limited"),
            Map.entry("ASIANPAINT.NS", "Asian Paints Limited"),
            Map.entry("TITAN.NS", "Titan Company Limited"),
            Map.entry("TATAMOTORS.NS", "Tata Motors Limited"),
            Map.entry("MARUTI.NS", "Maruti Suzuki India Limited"),
            Map.entry("SUNPHARMA.NS", "Sun Pharmaceutical Industries Limited"),
            Map.entry("TATASTEEL.NS", "Tata Steel Limited"),
            Map.entry("ULTRACEMCO.NS", "UltraTech Cement Limited"),
            Map.entry("WIPRO.NS", "Wipro Limited"),
            Map.entry("M&M.NS", "Mahindra & Mahindra Limited")
    );

    public PatternScannerService(YahooFinanceService yahooFinanceService) {
        this.yahooFinanceService = yahooFinanceService;
    }

    public List<ScanResult> scanMarkets() {
        List<ScanResult> results = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusYears(1);

        for (String ticker : SCAN_LIST) {
            List<StockQuote> quotes = yahooFinanceService.fetchHistory(ticker, start, end);
            if (quotes.size() < 30) {
                continue;
            }

            Optional<ScanResult> pattern = detectDoubleBottom(ticker, quotes);
            pattern.ifPresent(results::add);
        }

        return results;
    }

    private Optional<ScanResult> detectDoubleBottom(String ticker, List<StockQuote> quotes) {
        int N = quotes.size();
        List<Double> prices = quotes.stream().map(StockQuote::getClose).collect(Collectors.toList());

        // 1. Smooth prices (rolling average, window size 5)
        int smoothWindow = 5;
        List<Double> smooth = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            double sum = 0.0;
            int count = 0;
            for (int k = Math.max(0, i - smoothWindow + 1); k <= i; k++) {
                sum += prices.get(k);
                count++;
            }
            smooth.add(sum / count);
        }

        // 2. Find local minima (valleys) and maxima (peaks) with order 10
        int order = 10;
        List<Integer> localMins = new ArrayList<>();
        List<Integer> localMaxs = new ArrayList<>();

        for (int i = order; i < N - order; i++) {
            double val = smooth.get(i);
            boolean isMin = true;
            boolean isMax = true;

            for (int j = 1; j <= order; j++) {
                if (smooth.get(i - j) <= val || smooth.get(i + j) <= val) {
                    isMin = false;
                }
                if (smooth.get(i - j) >= val || smooth.get(i + j) >= val) {
                    isMax = false;
                }
            }

            if (isMin) {
                localMins.add(i);
            }
            if (isMax) {
                localMaxs.add(i);
            }
        }

        // 3. Verify pattern constraints (Double Bottom)
        double thresholdPct = 0.03; // Python code sets threshold_pct=0.03 inside scan

        if (localMins.size() >= 2) {
            int min2Idx = localMins.get(localMins.size() - 1);
            int min1Idx = localMins.get(localMins.size() - 2);

            // There must be a peak between the two valleys
            List<Integer> maxsBetween = new ArrayList<>();
            for (int m : localMaxs) {
                if (m > min1Idx && m < min2Idx) {
                    maxsBetween.add(m);
                }
            }

            if (!maxsBetween.isEmpty()) {
                int peakIdx = maxsBetween.get(maxsBetween.size() - 1);

                double min1Price = prices.get(min1Idx);
                double min2Price = prices.get(min2Idx);
                double peakPrice = prices.get(peakIdx);

                double avgMin = (min1Price + min2Price) / 2.0;
                double diff = Math.abs(min1Price - min2Price) / avgMin;

                // Check W pattern: Bottoms are close, and the peak is noticeably higher
                if (diff <= thresholdPct && peakPrice > avgMin * 1.02) {
                    String company = TICKER_NAMES.getOrDefault(ticker, ticker);

                    // Compile historical candle data for plotting
                    List<String> datesList = new ArrayList<>();
                    List<Double> opens = new ArrayList<>();
                    List<Double> highs = new ArrayList<>();
                    List<Double> lows = new ArrayList<>();
                    List<Double> closes = new ArrayList<>();
                    List<Long> volumes = new ArrayList<>();

                    for (StockQuote q : quotes) {
                        datesList.add(q.getDate().toString());
                        opens.add(q.getOpen());
                        highs.add(q.getHigh());
                        lows.add(q.getLow());
                        closes.add(q.getClose());
                        volumes.add(q.getVolume());
                    }

                    ScanResult result = new ScanResult(
                            company,
                            ticker,
                            min1Price,
                            peakPrice,
                            min2Price,
                            quotes.get(min1Idx).getDate().toString(),
                            quotes.get(peakIdx).getDate().toString(),
                            quotes.get(min2Idx).getDate().toString(),
                            datesList,
                            opens,
                            highs,
                            lows,
                            closes,
                            volumes
                    );

                    return Optional.of(result);
                }
            }
        }

        return Optional.empty();
    }
}
