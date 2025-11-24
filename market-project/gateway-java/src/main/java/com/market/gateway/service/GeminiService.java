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
public class GeminiService { // Numele ramane la fel ca sa nu modificam Controller-ul

    private final PriceRepository priceRepository;

    @Value("${groq.api.key}") // Citim cheia noua de Groq
    private String apiKey;

    // URL-ul Groq (folosim modelul Llama 3)
    private final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final String FEAR_GREED_API = "https://api.alternative.me/fng/?limit=1";

    public GeminiService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public String getMarketPrediction() {
        try {
            // 1. Luam pretul
            List<Price> history = priceRepository.findTop50ByOrderByTimestampDesc();
            if (history.isEmpty()) return "Waiting for price data...";
            double currentPrice = history.get(0).getPrice();

            // 2. Luam sentimentul
            String fearAndGreedData = fetchFearAndGreed();

            // 3. Cream Prompt-ul
            String promptText = "You are a crypto expert. Bitcoin is $" + currentPrice +
                    ". Market Sentiment: " + fearAndGreedData + ". " +
                    "Give a short investment advice (Buy/Sell/Hold) and a brief reason. Max 2 sentences.";

            // 4. Intrebam Groq (Llama 3)
            return callGroq(promptText);

        } catch (Exception e) {
            return "Analysis Error: " + e.getMessage();
        }
    }

    private String callGroq(String text) throws Exception {
        // Formatul JSON pentru Groq (compatibil OpenAI)
        String jsonBody = "{"
                + "\"model\": \"llama-3.3-70b-versatile\","
                + "\"messages\": [{\"role\": \"user\", \"content\": \"" + text + "\"}],"
                + "\"temperature\": 0.7"
                + "}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey) // Autentificare Bearer
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Parsare manuala a raspunsului (cautam "content": "...")
        if (body.contains("\"content\":")) {
            int start = body.indexOf("\"content\":") + 11; // sarim peste "content": "
            int end = body.indexOf("\"", start); // cautam ghilimeaua de final

            // Daca textul contine escape characters, poate fi mai complicat,
            // dar pentru demo simplu merge asa:
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