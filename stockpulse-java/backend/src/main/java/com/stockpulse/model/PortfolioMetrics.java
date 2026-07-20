package com.stockpulse.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioMetrics {
    private double portfolioAnnualReturn;
    private double portfolioAnnualVolatility;
    private double portfolioSharpeRatio;
    private double portfolioVaR95;

    private List<AssetMetrics> assetMetricsList;

    // Charts data
    private List<String> dates;
    private List<Double> cumulativeReturns;

    // Forecast data
    private List<String> forecastDates;
    private List<Double> forecastReturns;
    private double forecast1d;
    private double forecast15d;
    private double forecast30d;
}
