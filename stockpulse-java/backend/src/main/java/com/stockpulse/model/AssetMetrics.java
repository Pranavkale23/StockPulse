package com.stockpulse.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetMetrics {
    private String ticker;
    private double weight;
    private double annualReturn;
    private double annualVolatility;
    private double sharpeRatio;
    private double beta;
}
