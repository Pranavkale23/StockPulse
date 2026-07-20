// ==========================================================================
// Quantum Portfolio App JS Controller
// ==========================================================================

let activeSocket = null;
let currentMetrics = null;
let scanResults = [];

// Sample Portfolio Holdings definition
const SAMPLE_HOLDINGS = [
    { ticker: "AAPL", shares: 10 },
    { ticker: "MSFT", shares: 8 },
    { ticker: "NVDA", shares: 6 },
    { ticker: "GOOGL", shares: 5 },
    { ticker: "AMZN", shares: 4 }
];

// On startup
window.addEventListener('DOMContentLoaded', () => {
    // Set standard dates
    const today = new Date();
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(today.getFullYear() - 1);
    
    document.getElementById('endDate').value = today.toISOString().split('T')[0];
    document.getElementById('startDate').value = oneYearAgo.toISOString().split('T')[0];
    
    // Run initial calculate on sample portfolio
    calculatePortfolio();
});

function toggleInputFields() {
    const val = document.getElementById("inputMethod").value;
    const manualContainer = document.getElementById("manualInputContainer");
    if (val === "manual") {
        manualContainer.classList.remove("hidden");
    } else {
        manualContainer.classList.add("hidden");
    }
}

function switchTab(evt, tabId) {
    const tabcontents = document.getElementsByClassName("tab-content");
    for (let i = 0; i < tabcontents.length; i++) {
        tabcontents[i].classList.remove("active");
    }

    const tablinks = document.getElementsByClassName("tab-btn");
    for (let i = 0; i < tablinks.length; i++) {
        tablinks[i].classList.remove("active");
    }

    document.getElementById(tabId).classList.add("active");
    evt.currentTarget.classList.add("active");
}

function getActiveHoldings() {
    const inputType = document.getElementById("inputMethod").value;
    if (inputType === "sample") {
        return SAMPLE_HOLDINGS;
    } else {
        const text = document.getElementById("holdingsInput").value.trim();
        if (!text) {
            alert("Please enter holdings in 'TICKER, SHARES' format, one per line.");
            return null;
        }
        
        const lines = text.split("\n");
        const list = [];
        for (let line of lines) {
            const parts = line.split(",");
            if (parts.length >= 2) {
                const ticker = parts[0].trim().toUpperCase();
                const shares = parseFloat(parts[1].trim());
                if (ticker && !isNaN(shares)) {
                    list.push({ ticker, shares });
                }
            }
        }
        
        if (list.length === 0) {
            alert("Could not parse any valid holdings. Please enter lines like: AAPL, 10");
            return null;
        }
        return list;
    }
}

// REST Api Call to compute portfolio
function calculatePortfolio() {
    const holdings = getActiveHoldings();
    if (!holdings) return;

    const requestData = {
        holdings: holdings,
        startDate: document.getElementById("startDate").value,
        endDate: document.getElementById("endDate").value,
        benchmarkTicker: document.getElementById("benchmarkTicker").value.trim().toUpperCase(),
        riskFreeRate: parseFloat(document.getElementById("riskFreeRate").value)
    };

    fetch('/api/portfolio/calculate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestData)
    })
    .then(res => {
        if (!res.ok) throw new Error("Backend calculate call failed.");
        return res.json();
    })
    .then(data => {
        currentMetrics = data;
        updateDashboard(data);
    })
    .catch(err => {
        alert("Error loading portfolio stats: " + err.message);
    });
}

function updateDashboard(data) {
    // 1. Update KPI metrics cards
    document.getElementById("mAnnualReturn").innerText = (data.portfolioAnnualReturn * 100).toFixed(2) + "%";
    document.getElementById("mAnnualVol").innerText = (data.portfolioAnnualVolatility * 100).toFixed(2) + "%";
    document.getElementById("mSharpe").innerText = data.portfolioSharpeRatio.toFixed(4);
    document.getElementById("mVaR").innerText = (data.portfolioVaR95 * 100).toFixed(2) + "%";

    // Forecast expected values
    document.getElementById("f1d").innerText = (data.forecast1d * 100).toFixed(2) + "%";
    document.getElementById("f15d").innerText = (data.forecast15d * 100).toFixed(2) + "%";
    document.getElementById("f30d").innerText = (data.forecast30d * 100).toFixed(2) + "%";

    // 2. Populate Asset Table
    const tbody = document.querySelector("#assetTable tbody");
    tbody.innerHTML = "";
    data.assetMetricsList.forEach(asset => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="font-bold text-white">${asset.ticker}</td>
            <td>${(asset.weight * 100).toFixed(2)}%</td>
            <td class="${asset.annualReturn >= 0 ? 'text-emerald-400' : 'text-rose-500'}">${(asset.annualReturn * 100).toFixed(2)}%</td>
            <td>${(asset.annualVolatility * 100).toFixed(2)}%</td>
            <td>${asset.sharpeRatio.toFixed(3)}</td>
            <td>${asset.beta.toFixed(3)}</td>
        `;
        tbody.appendChild(tr);
    });

    // 3. Render Cumulative Returns Chart
    const returnTrace = {
        x: data.dates,
        y: data.cumulativeReturns,
        mode: 'lines',
        name: 'Portfolio',
        line: { color: '#3b82f6', width: 2.5 }
    };
    
    const returnsLayout = getDarkPlotlyLayout("Portfolio Cumulative Return", "Date", "Growth Multiplier");
    Plotly.newPlot('returnsChart', [returnTrace], returnsLayout, { responsive: true });

    // 4. Render Allocation Donut
    const pieLabels = data.assetMetricsList.map(a => a.ticker);
    const pieValues = data.assetMetricsList.map(a => a.weight);
    
    const allocationTrace = {
        labels: pieLabels,
        values: pieValues,
        type: 'pie',
        hole: 0.45,
        marker: {
            colors: ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#06b6d4']
        },
        textinfo: 'label+percent'
    };
    
    const pieLayout = {
        paper_bgcolor: 'rgba(0,0,0,0)',
        plot_bgcolor: 'rgba(0,0,0,0)',
        font: { color: '#94a3b8', family: 'Plus Jakarta Sans' },
        showlegend: true,
        legend: { font: { color: '#f8fafc' } },
        margin: { l: 20, r: 20, t: 40, b: 20 }
    };
    Plotly.newPlot('allocationChart', [allocationTrace], pieLayout, { responsive: true });

    // 5. Render Forecast Chart
    const histTrace = {
        x: data.dates,
        y: data.cumulativeReturns,
        mode: 'lines',
        name: 'Historical',
        line: { color: '#3b82f6', width: 2 }
    };
    
    const forecastTrace = {
        x: data.forecastDates,
        y: data.forecastReturns,
        mode: 'lines',
        name: 'HW Forecast (30D)',
        line: { color: '#f59e0b', width: 2, dash: 'dash' }
    };
    
    const forecastLayout = getDarkPlotlyLayout("Cumulative Forecast Trajectory", "Date", "Growth Base");
    Plotly.newPlot('forecastChart', [histTrace, forecastTrace], forecastLayout, { responsive: true });
}

function getDarkPlotlyLayout(title, xtitle, ytitle) {
    return {
        title: { text: title, font: { color: '#f8fafc', size: 16 } },
        paper_bgcolor: 'rgba(0,0,0,0)',
        plot_bgcolor: 'rgba(0,0,0,0)',
        xaxis: {
            title: xtitle,
            gridcolor: 'rgba(255, 255, 255, 0.05)',
            tickfont: { color: '#94a3b8' },
            titlefont: { color: '#94a3b8' }
        },
        yaxis: {
            title: ytitle,
            gridcolor: 'rgba(255, 255, 255, 0.05)',
            tickfont: { color: '#94a3b8' },
            titlefont: { color: '#94a3b8' }
        },
        margin: { l: 50, r: 20, t: 50, b: 50 },
        showlegend: true,
        legend: { font: { color: '#f8fafc' } }
    };
}

// WebSocket connection for live-pricing & scan status
function setupWebSocket() {
    if (activeSocket && activeSocket.readyState === WebSocket.OPEN) {
        return;
    }

    const loc = window.location;
    const wsUrl = (loc.protocol === 'https:' ? 'wss://' : 'ws://') + loc.host + '/ws/live-prices';
    
    activeSocket = new WebSocket(wsUrl);

    activeSocket.onopen = () => {
        console.log("WebSocket connection established with Spring Boot Backend.");
    };

    activeSocket.onmessage = (event) => {
        try {
            const wrapper = JSON.parse(event.data);
            const type = wrapper.type;
            const payload = JSON.parse(wrapper.payload);

            if (type === "TICK") {
                updateLivePricePill(payload.ticker, payload.price);
            } else if (type === "SCAN_STATUS") {
                document.getElementById("scanStatus").innerText = payload;
            } else if (type === "SCAN_COMPLETE") {
                document.getElementById("scanStatus").innerText = "Scan completed. Matches found!";
                scanResults = payload;
                populateScannerTable(payload);
            } else if (type === "SCAN_ERROR") {
                document.getElementById("scanStatus").innerText = "Scan error: " + payload;
            }
        } catch (e) {
            console.error("Error parsing WebSocket packet: ", e);
        }
    };

    activeSocket.onclose = () => {
        console.log("WebSocket connection closed.");
    };
}

// Kafka Live Simulator Toggle
function toggleSimulation(enable) {
    const holdings = getActiveHoldings();
    if (!holdings) return;

    fetch(`/api/live/simulate?enable=${enable}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(holdings)
    })
    .then(res => res.json())
    .then(data => {
        if (enable) {
            document.getElementById("liveBar").classList.remove("hidden");
            document.getElementById("liveStatus").classList.remove("hidden");
            document.getElementById("liveStatus").classList.add("flex");
            document.getElementById("btnSimulate").classList.add("hidden");
            document.getElementById("btnStopSimulate").classList.remove("hidden");
            
            // Build placeholder pills
            const liveBar = document.getElementById("liveBar");
            liveBar.innerHTML = "";
            holdings.forEach(h => {
                const pill = document.createElement("div");
                pill.id = `live-${h.ticker}`;
                pill.className = "live-pill";
                pill.innerHTML = `
                    <span class="font-bold text-white">${h.ticker}</span>
                    <span id="price-${h.ticker}" class="text-slate-300">Calibrating...</span>
                `;
                liveBar.appendChild(pill);
            });

            // Start socket
            setupWebSocket();
        } else {
            document.getElementById("liveBar").classList.add("hidden");
            document.getElementById("liveStatus").classList.add("hidden");
            document.getElementById("liveStatus").classList.remove("flex");
            document.getElementById("btnSimulate").classList.remove("hidden");
            document.getElementById("btnStopSimulate").classList.add("hidden");
        }
    })
    .catch(err => {
        alert("Error toggling Kafka live simulator: " + err.message);
    });
}

function updateLivePricePill(ticker, price) {
    const pill = document.getElementById(`live-${ticker}`);
    const priceSpan = document.getElementById(`price-${ticker}`);
    if (!pill || !priceSpan) return;

    const oldPriceText = priceSpan.innerText;
    priceSpan.innerText = "$" + price.toFixed(2);

    if (oldPriceText !== "Calibrating...") {
        const oldPrice = parseFloat(oldPriceText.replace("$", ""));
        if (price > oldPrice) {
            pill.className = "live-pill up";
        } else if (price < oldPrice) {
            pill.className = "live-pill down";
        }
    }
}

// Queue scanning task into Kafka
function startScanning() {
    document.getElementById("scanStatus").innerText = "Queuing scan task to Kafka broker...";
    document.getElementById("btnScan").disabled = true;
    
    // Ensure websocket is active to receive findings
    setupWebSocket();

    fetch('/api/scanner/scan', { method: 'POST' })
    .then(res => res.json())
    .then(data => {
        document.getElementById("scanStatus").innerText = "Task queued. Running scanner asynchronously...";
    })
    .catch(err => {
        document.getElementById("scanStatus").innerText = "Trigger scan failed: " + err.message;
        document.getElementById("btnScan").disabled = false;
    });
}

function populateScannerTable(results) {
    document.getElementById("btnScan").disabled = false;
    const table = document.getElementById("scannerTable");
    const tbody = table.querySelector("tbody");
    tbody.innerHTML = "";

    if (results.length === 0) {
        document.getElementById("scanStatus").innerText = "Scan completed. No Double Bottom (W) patterns detected.";
        table.classList.add("hidden");
        return;
    }

    table.classList.remove("hidden");
    results.forEach((row, idx) => {
        const tr = document.createElement("tr");
        tr.className = "cursor-pointer";
        tr.onclick = () => renderScanChart(row.ticker);
        tr.innerHTML = `
            <td class="text-slate-300 font-semibold">${row.company}</td>
            <td class="font-bold text-sky-400">${row.ticker}</td>
            <td>${row.bottom1Price.toFixed(2)}</td>
            <td>${row.peakPrice.toFixed(2)}</td>
            <td>${row.bottom2Price.toFixed(2)}</td>
        `;
        tbody.appendChild(tr);
    });

    // Auto-select first scanner row
    renderScanChart(results[0].ticker);
}

function renderScanChart(ticker) {
    const row = scanResults.find(r => r.ticker === ticker);
    if (!row) return;

    document.getElementById("scanChartContainer").classList.remove("hidden");
    document.getElementById("scanChartTitle").innerText = `${row.company} (${row.ticker}) - Double Bottom Structure`;

    const currencySymbol = row.ticker.endsWith(".NS") ? "₹" : "$";

    // 1. Candlestick trace
    const candleTrace = {
        x: row.chartDates,
        open: row.openPrices,
        high: row.highPrices,
        low: row.lowPrices,
        close: row.closePrices,
        type: 'candlestick',
        name: `${row.ticker} OHLC`,
        increasing: { line: { color: '#10b981' } },
        decreasing: { line: { color: '#ef4444' } }
    };

    // 2. Yellow 'W' Overlay connector
    const wDates = [row.bottom1Date, row.peakDate, row.bottom2Date];
    const wPrices = [row.bottom1Price, row.peakPrice, row.bottom2Price];

    const overlayTrace = {
        x: wDates,
        y: wPrices,
        mode: 'lines+markers',
        name: 'W Pattern Structure',
        line: { color: '#f59e0b', width: 3, dash: 'dash' },
        marker: { color: '#f59e0b', size: 10, symbol: 'circle' }
    };

    // Candlestick layout overrides
    const layout = {
        paper_bgcolor: 'rgba(0,0,0,0)',
        plot_bgcolor: 'rgba(0,0,0,0)',
        xaxis: {
            gridcolor: 'rgba(255, 255, 255, 0.05)',
            tickfont: { color: '#94a3b8' },
            rangeslider: { visible: false }
        },
        yaxis: {
            title: `Price (${currencySymbol})`,
            gridcolor: 'rgba(255, 255, 255, 0.05)',
            tickfont: { color: '#94a3b8' },
            titlefont: { color: '#94a3b8' }
        },
        margin: { l: 50, r: 20, t: 10, b: 30 },
        showlegend: true,
        legend: { font: { color: '#f8fafc' }, y: 0.99, x: 0.01 }
    };

    Plotly.newPlot('scanCandlestickChart', [candleTrace, overlayTrace], layout, { responsive: true });
}

// Spring AI Advisor Chat Client
function handleChatEnter(event) {
    if (event.key === 'Enter') {
        sendChatMessage();
    }
}

function sendChatMessage() {
    const input = document.getElementById("chatInput");
    const messageText = input.value.trim();
    if (!messageText) return;

    // Append user message
    appendChatMessage("user", messageText);
    input.value = "";

    // Set stats context
    const reqBody = {
        message: messageText,
        annReturn: currentMetrics ? currentMetrics.portfolioAnnualReturn : 0.0,
        annVol: currentMetrics ? currentMetrics.portfolioAnnualVolatility : 0.0,
        sharpe: currentMetrics ? currentMetrics.portfolioSharpeRatio : 0.0,
        var95: currentMetrics ? currentMetrics.portfolioVaR95 : 0.0
    };

    // Call REST endpoint
    fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(reqBody)
    })
    .then(res => res.json())
    .then(data => {
        appendChatMessage("ai", data.response);
    })
    .catch(err => {
        appendChatMessage("ai", "Sorry, I had trouble contacting the AI model service. " + err.message);
    });
}

function appendChatMessage(sender, text) {
    const chatHistory = document.getElementById("chatHistory");
    const div = document.createElement("div");
    div.className = `chat-msg ${sender}`;
    
    // Quick format for markdown list strings
    let htmlText = text
        .replace(/\n/g, '<br>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/`(.*?)`/g, '<code class="bg-slate-900 border border-slate-800 px-1 py-0.5 rounded text-rose-300 font-mono">$1</code>');
        
    div.innerHTML = htmlText;
    chatHistory.appendChild(div);
    chatHistory.scrollTop = chatHistory.scrollHeight;
}
