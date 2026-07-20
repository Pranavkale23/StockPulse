package com.stockpulse.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {
    private String company;
    private String ticker;
    
    // Pattern Points
    private double bottom1Price;
    private double peakPrice;
    private double bottom2Price;
    private String bottom1Date;
    private String peakDate;
    private String bottom2Date;

    // Historical Chart Data for drawing Candlestick & Volume
    private List<String> chartDates;
    private List<Double> openPrices;
    private List<Double> highPrices;
    private List<Double> lowPrices;
    private List<Double> closePrices;
    private List<Long> volumes;
}
