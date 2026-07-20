package com.stockpulse.model;

import lombok.Data;
import java.util.List;

@Data
public class PortfolioRequest {
    private List<Holding> holdings;
    private String startDate;
    private String endDate;
    private String benchmarkTicker;
    private double riskFreeRate;
}
