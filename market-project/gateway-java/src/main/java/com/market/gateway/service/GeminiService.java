package com.market.gateway.service;
import com.market.gateway.model.Price;
import com.market.gateway.repository.PriceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class GeminiService {

    private final PriceRepository priceRepository;

    @Value("${groq.api.key}")
    private String apiKey;
    private final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final String FEAR_GREED_API = "https://api.alternative.me/fng/?limit=1";

    public GeminiService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public String getMarketPrediction() {
        try {
            List<Price> history = priceRepository.findTop50ByOrderByTimestampDesc();
            if (history.isEmpty()) return "Waiting for price data...";
            double currentPrice = history.get(0).getPrice();

            String fearAndGreedData = fetchFearAndGreed();
            String promptText = "You are a crypto expert. Bitcoin is $" + currentPrice +
                    ". Market Sentiment: " + fearAndGreedData + ". " +
                    "Give a short investment advice (Buy/Sell/Hold) and a brief reason. Max 2 sentences.";

            return callGroq(promptText);

        } catch (Exception e) {
            return "Analysis Error: " + e.getMessage();
        }
    }

    private String callGroq(String text) throws Exception {
        String jsonBody = "{"
                + "\"model\": \"llama-3.3-70b-versatile\","
                + "\"messages\": [{\"role\": \"user\", \"content\": \"" + text + "\"}],"
                + "\"temperature\": 0.7"
                + "}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        if (body.contains("\"content\":")) {
            int start = body.indexOf("\"content\":") + 11;
            int end = body.indexOf("\"", start);
            String result = body.substring(start, end);
            return result.replace("\\n", " ").replace("\\\"", "\"");
        }

        return "Groq Error: " + body;
    }

    private String fetchFearAndGreed() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(FEAR_GREED_API)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if(body.contains("\"value\":")) {
                int valIndex = body.indexOf("\"value\":") + 9;
                String value = body.substring(valIndex, body.indexOf("\"", valIndex));
                int classIndex = body.indexOf("\"value_classification\":") + 24;
                String classification = body.substring(classIndex, body.indexOf("\"", classIndex));
                return value + " (" + classification + ")";
            }
            return "Neutral";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}