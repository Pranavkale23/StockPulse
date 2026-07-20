package com.stockpulse.service;

import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpringAiService {

    private final ChatClient chatClient;

    public SpringAiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generatePortfolioInsights(String prompt, double annReturn, double annVol, double sharpe, double var95) {
        try {
            // Attempt to call OpenAI LLM via Spring AI
            return chatClient.call(prompt);
        } catch (Exception e) {
            // Friendly fallback if OpenAI API key is missing or invalid
            return generateLocalFallbackInsights(annReturn, annVol, sharpe, var95);
        }
    }

    private String generateLocalFallbackInsights(double annReturn, double annVol, double sharpe, double var95) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🤖 Local Portfolio Analytics Engine (AI Key Fallback)\n\n");
        sb.append("Here is an automated risk evaluation of your current portfolio holdings:\n\n");

        // 1. Performance evaluation
        sb.append(String.format("*   **Annualized Return**: `%.2f%%`. ", annReturn * 100.0));
        if (annReturn > 0.15) {
            sb.append("This is an excellent growth trajectory, outperforming historical S&P 500 averages.\n");
        } else if (annReturn > 0.05) {
            sb.append("This is a moderate return. Consider adding higher beta growth stocks to increase yield.\n");
        } else {
            sb.append("The portfolio returns are underperforming. Re-evaluating asset weights is recommended.\n");
        }

        // 2. Risk evaluation
        sb.append(String.format("*   **Volatility (Risk)**: `%.2f%%`. ", annVol * 100.0));
        if (annVol > 0.25) {
            sb.append("The portfolio is highly volatile. Consider diversifying into defensive assets like consumer staples or utilities to lower volatility.\n");
        } else {
            sb.append("The volatility is well-managed, showing stable pricing characteristics.\n");
        }

        // 3. Sharpe evaluation
        sb.append(String.format("*   **Sharpe Ratio**: `%.2f`. ", sharpe));
        if (sharpe >= 1.0) {
            sb.append("A Sharpe ratio above 1.0 is considered **Good**. The portfolio yields strong returns relative to the risk taken.\n");
        } else if (sharpe >= 0.0) {
            sb.append("The risk-adjusted returns are sub-optimal (below 1.0). You are taking significant risk for marginal return.\n");
        } else {
            sb.append("The Sharpe ratio is negative, indicating a risk-free asset would outperform this portfolio.\n");
        }

        // 4. VaR evaluation
        sb.append(String.format("*   **Value at Risk (95%%, Daily)**: `%.2f%%`. ", var95 * 100.0));
        sb.append(String.format("There is a 5%% probability that the portfolio loses more than **%.2f%%** of its total value in a single trading day.", Math.abs(var95 * 100.0)));

        return sb.toString();
    }
}
