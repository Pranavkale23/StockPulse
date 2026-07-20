# StockPulse Platform

A comprehensive stock analysis and portfolio management platform built with Spring Boot, Apache Kafka, Redis, and Spring AI. The application provides real-time portfolio tracking, technical pattern scanning, price forecasting, and AI-driven market insights.

## Features

- **Real-Time Market Data**: Integrates with Yahoo Finance for live stock prices and historical data.
- **Portfolio Management**: Manage your stock portfolios, calculate metrics, and monitor asset performance.
- **Technical Pattern Scanning**: Automatically identifies technical patterns in stock charts for better trading decisions.
- **Stock Price Forecasting**: Utilizes Apache Commons Math for predicting future stock trends based on historical data.
- **AI-Powered Insights**: Integrates Spring AI with OpenAI to provide intelligent, conversational analysis of the stock market.
- **Event-Driven Architecture**: Leverages Apache Kafka for asynchronous communication and scalable data processing.
- **High-Performance Caching**: Uses Redis to cache frequent queries, reducing latency and external API calls.
- **Real-Time Updates**: Pushes live market updates to the client via WebSockets.

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.5
- **Messaging**: Apache Kafka & Zookeeper
- **Caching**: Redis
- **AI Integration**: Spring AI (OpenAI)
- **Mathematics/Statistics**: Apache Commons Math3

## Project Structure

```text
stockpulse/
├── stockpulse-java/
│   ├── backend/                 # Spring Boot application (Controllers, Services, Models)
│   │   ├── src/main/java/com/stockpulse/
│   │   │   ├── controller/      # REST APIs (Portfolio, Chat)
│   │   │   ├── service/         # Business Logic (YahooFinance, AI, Forecast, Kafka)
│   │   │   └── ...
│   │   └── pom.xml              # Maven dependencies
│   └── docker-compose.yml       # Infrastructure setup (Kafka, Zookeeper, Redis)
├── RELIANCE.csv                 # Sample dataset
└── temp_cols.txt                # Temporary column definitions
```

## Getting Started

### Prerequisites

- **Java 17** or higher
- **Maven**
- **Docker & Docker Compose** (for running Kafka and Redis)
- **OpenAI API Key** (for Spring AI functionality)

### 1. Start Infrastructure

The project relies on Kafka, Zookeeper, and Redis. Start them using the provided `docker-compose.yml` file:

```bash
cd stockpulse-java
docker-compose up -d
```

### 2. Configure Environment

Ensure you have your OpenAI API key set in your environment variables or application properties (`application.properties` or `application.yml` inside the backend directory):

```properties
spring.ai.openai.api-key=YOUR_OPENAI_API_KEY
```

### 3. Run the Backend Application

Navigate to the `backend` directory, build the project, and run it:

```bash
cd stockpulse-java/backend
mvn clean install
mvn spring-boot:run
```

The application will start on `http://localhost:8080` (default port).

## Key Services Overview

- **`YahooFinanceService`**: Fetches real-time and historical stock data.
- **`SpringAiService` & `ChatController`**: Handles AI interactions for market insights.
- **`PatternScannerService`**: Detects chart patterns (e.g., Head and Shoulders, Double Top).
- **`ForecastService`**: Runs statistical models for price prediction.
- **`KafkaProducerService` & `KafkaConsumerService`**: Manages event streaming topics.
- **`MetricsService`**: Computes portfolio metrics like volatility, beta, and expected returns.
