package com.stockpulse.service;

import com.stockpulse.model.PortfolioMetrics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ForecastService {

    public void generateForecast(PortfolioMetrics metrics) {
        List<Double> y = metrics.getCumulativeReturns();
        List<String> dates = metrics.getDates();
        if (y.size() < 10) {
            return;
        }

        int N = y.size();
        double[] series = new double[N];
        for (int i = 0; i < N; i++) {
            series[i] = y.get(i);
        }

        // 1. Optimize alpha and beta using grid search to minimize in-sample SSE
        double bestAlpha = 0.2;
        double bestBeta = 0.1;
        double minSse = Double.MAX_VALUE;

        for (double alpha = 0.05; alpha <= 0.6; alpha += 0.05) {
            for (double beta = 0.05; beta <= 0.4; beta += 0.05) {
                double sse = computeSse(series, alpha, beta);
                if (sse < minSse) {
                    minSse = sse;
                    bestAlpha = alpha;
                    bestBeta = beta;
                }
            }
        }

        // 2. Fit Holt's model with optimized parameters
        double[] level = new double[N];
        double[] trend = new double[N];

        // Initialize level and trend with linear regression on the first 5 points
        SimpleRegression initRegression = new SimpleRegression();
        for (int i = 0; i < Math.min(5, N); i++) {
            initRegression.addData(i, series[i]);
        }
        level[0] = initRegression.getIntercept();
        trend[0] = initRegression.getSlope();

        for (int t = 1; t < N; t++) {
            level[t] = bestAlpha * series[t] + (1.0 - bestAlpha) * (level[t - 1] + trend[t - 1]);
            trend[t] = bestBeta * (level[t] - level[t - 1]) + (1.0 - bestBeta) * trend[t - 1];
        }

        // 3. Forecast next 30 business days
        int forecastSteps = 30;
        List<Double> forecastReturns = new ArrayList<>();
        List<String> forecastDates = new ArrayList<>();

        LocalDate lastDate = LocalDate.parse(dates.get(dates.size() - 1));
        LocalDate currDate = lastDate;

        double lastLevel = level[N - 1];
        double lastTrend = trend[N - 1];

        for (int h = 1; h <= forecastSteps; h++) {
            double pred = lastLevel + h * lastTrend;
            forecastReturns.add(pred);

            // Increment date to next business day
            currDate = getNextBusinessDay(currDate);
            forecastDates.add(currDate.toString());
        }

        double lastActual = series[N - 1];
        double pred1d = (forecastReturns.get(0) / lastActual) - 1.0;
        double pred15d = (forecastReturns.get(14) / lastActual) - 1.0;
        double pred30d = (forecastReturns.get(29) / lastActual) - 1.0;

        metrics.setForecastDates(forecastDates);
        metrics.setForecastReturns(forecastReturns);
        metrics.setForecast1d(pred1d);
        metrics.setForecast15d(pred15d);
        metrics.setForecast30d(pred30d);
    }

    private double computeSse(double[] series, double alpha, double beta) {
        int N = series.length;
        double[] level = new double[N];
        double[] trend = new double[N];

        SimpleRegression initRegression = new SimpleRegression();
        for (int i = 0; i < Math.min(5, N); i++) {
            initRegression.addData(i, series[i]);
        }
        level[0] = initRegression.getIntercept();
        trend[0] = initRegression.getSlope();

        double sse = 0.0;
        for (int t = 1; t < N; t++) {
            double pred = level[t - 1] + trend[t - 1];
            double error = series[t] - pred;
            sse += error * error;

            level[t] = alpha * series[t] + (1.0 - alpha) * (level[t - 1] + trend[t - 1]);
            trend[t] = beta * (level[t] - level[t - 1]) + (1.0 - beta) * trend[t - 1];
        }
        return sse;
    }

    private LocalDate getNextBusinessDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }
}
