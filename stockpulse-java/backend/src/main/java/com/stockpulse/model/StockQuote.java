package com.stockpulse.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockQuote implements Serializable {
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
