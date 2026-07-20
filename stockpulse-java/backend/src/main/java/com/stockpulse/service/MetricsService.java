package com.stockpulse.service;

import com.stockpulse.model.AssetMetrics;
import com.stockpulse.model.Holding;
import com.stockpulse.model.PortfolioMetrics;
import com.stockpulse.model.StockQuote;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetricsService {

    private final YahooFinanceService yahooFinanceService;
    private final ForecastService forecastService;

    public MetricsService(YahooFinanceService yahooFinanceService, ForecastService forecastService) {
        this.yahooFinanceService = yahooFinanceService;
        this.forecastService = forecastService;
    }

    public PortfolioMetrics computePortfolioMetrics(List<Holding> holdings, LocalDate start, LocalDate end, String benchmarkTicker, double rf) {
        List<String> tickers = holdings.stream().map(Holding::getTicker).collect(Collectors.toList());
        List<String> allTickers = new ArrayList<>(tickers);
        if (!allTickers.contains(benchmarkTicker)) {
            allTickers.add(benchmarkTicker);
        }

        // 1. Download price data for all tickers
        Map<String, List<StockQuote>> quotesMap = new HashMap<>();
        SortedSet<LocalDate> allDates = new TreeSet<>();

        for (String ticker : allTickers) {
            List<StockQuote> quotes = yahooFinanceService.fetchHistory(ticker, start, end);
            quotesMap.put(ticker, quotes);
            for (StockQuote q : quotes) {
                allDates.add(q.getDate());
            }
        }

        if (allDates.isEmpty() || quotesMap.get(benchmarkTicker).isEmpty()) {
            throw new IllegalArgumentException("No price data retrieved for selected date range and assets.");
        }

        // Convert sorted dates to a indexed list
        List<LocalDate> dateList = new ArrayList<>(allDates);
        int N = dateList.size();

        // 2. Forward and backward fill prices to align dates across all assets
        Map<String, double[]> alignedPrices = new HashMap<>();
        for (String ticker : allTickers) {
            double[] prices = new double[N];
            List<StockQuote> quotes = quotesMap.get(ticker);
            Map<LocalDate, Double> priceMap = quotes.stream()
                    .collect(Collectors.toMap(StockQuote::getDate, StockQuote::getClose, (v1, v2) -> v1));

            double lastValidPrice = 0.0;
            // Find first valid price for backward fill
            for (LocalDate date : dateList) {
                if (priceMap.containsKey(date)) {
                    lastValidPrice = priceMap.get(date);
                    break;
                }
            }

            for (int i = 0; i < N; i++) {
                LocalDate date = dateList.get(i);
                if (priceMap.containsKey(date)) {
                    lastValidPrice = priceMap.get(date);
                }
                prices[i] = lastValidPrice;
            }
            alignedPrices.put(ticker, prices);
        }

        // 3. Compute Daily Returns
        // Returns arrays will have size N - 1
        int M = N - 1;
        if (M <= 0) {
            throw new IllegalArgumentException("Insufficient data points for return calculation.");
        }

        Map<String, double[]> dailyReturns = new HashMap<>();
        for (String ticker : allTickers) {
            double[] prices = alignedPrices.get(ticker);
            double[] rets = new double[M];
            for (int i = 0; i < M; i++) {
                double prev = prices[i];
                double curr = prices[i + 1];
                rets[i] = (prev == 0) ? 0 : (curr - prev) / prev;
            }
            dailyReturns.put(ticker, rets);
        }

        // 4. Calculate Portfolio Weights
        double[] latestPrices = new double[tickers.size()];
        double[] marketValues = new double[tickers.size()];
        double totalMarketValue = 0.0;

        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);
            double[] prices = alignedPrices.get(ticker);
            latestPrices[i] = prices[N - 1];
            
            double shares = 0.0;
            for (Holding h : holdings) {
                if (h.getTicker().equals(ticker)) {
                    shares = h.getShares();
                    break;
                }
            }
            marketValues[i] = latestPrices[i] * shares;
            totalMarketValue += marketValues[i];
        }

        double[] weights = new double[tickers.size()];
        if (totalMarketValue > 0) {
            for (int i = 0; i < tickers.size(); i++) {
                weights[i] = marketValues[i] / totalMarketValue;
            }
        } else {
            // fallback equal weights
            for (int i = 0; i < tickers.size(); i++) {
                weights[i] = 1.0 / tickers.size();
            }
        }

        // 5. Compute Asset-Level Metrics
        double[] benchmarkRets = dailyReturns.get(benchmarkTicker);
        List<AssetMetrics> assetMetricsList = new ArrayList<>();

        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);
            double[] rets = dailyReturns.get(ticker);

            DescriptiveStatistics stats = new DescriptiveStatistics(rets);
            double meanRet = stats.getMean();
            double stdRet = stats.getStandardDeviation();

            double annReturn = meanRet * 252;
            double annVol = stdRet * Math.sqrt(252);
            double sharpe = (annVol == 0) ? 0.0 : (annReturn - rf) / annVol;

            // Beta vs Benchmark
            double beta = 0.0;
            if (benchmarkRets.length == rets.length && rets.length > 1) {
                Covariance cov = new Covariance();
                double covariance = cov.covariance(rets, benchmarkRets);
                DescriptiveStatistics benchStats = new DescriptiveStatistics(benchmarkRets);
                double benchVar = benchStats.getVariance();
                beta = (benchVar == 0) ? 0.0 : covariance / benchVar;
            }

            assetMetricsList.add(new AssetMetrics(ticker, weights[i], annReturn, annVol, sharpe, beta));
        }

        // 6. Compute Portfolio Daily Returns
        double[] portDailyReturns = new double[M];
        for (int j = 0; j < M; j++) {
            double sum = 0.0;
            for (int i = 0; i < tickers.size(); i++) {
                sum += weights[i] * dailyReturns.get(tickers.get(i))[j];
            }
            portDailyReturns[j] = sum;
        }

        // 7. Portfolio Summary Metrics
        DescriptiveStatistics portStats = new DescriptiveStatistics(portDailyReturns);
        double portMeanRet = portStats.getMean();
        double portStdRet = portStats.getStandardDeviation();

        double portAnnReturn = portMeanRet * 252;
        double portAnnVol = portStdRet * Math.sqrt(252);
        double portSharpe = (portAnnVol == 0) ? 0.0 : (portAnnReturn - rf) / portAnnVol;
        
        // Value at Risk (VaR 95%) - 5th percentile
        double portVaR95 = portStats.getPercentile(5.0);

        // 8. Cumulative returns series
        List<String> dates = new ArrayList<>();
        List<Double> cumulativeReturns = new ArrayList<>();
        
        // Add starting point
        dates.add(dateList.get(0).toString());
        cumulativeReturns.add(1.0);

        double cum = 1.0;
        for (int i = 0; i < M; i++) {
            cum *= (1 + portDailyReturns[i]);
            dates.add(dateList.get(i + 1).toString());
            cumulativeReturns.add(cum);
        }

        // 9. Generate Forecasting
        PortfolioMetrics metrics = new PortfolioMetrics();
        metrics.setPortfolioAnnualReturn(portAnnReturn);
        metrics.setPortfolioAnnualVolatility(portAnnVol);
        metrics.setPortfolioSharpeRatio(portSharpe);
        metrics.setPortfolioVaR95(portVaR95);
        metrics.setAssetMetricsList(assetMetricsList);
        metrics.setDates(dates);
        metrics.setCumulativeReturns(cumulativeReturns);

        // Run forecast service on cumulative returns series
        forecastService.generateForecast(metrics);

        return metrics;
    }
}
