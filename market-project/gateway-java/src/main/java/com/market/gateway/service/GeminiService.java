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
import java.util.stream.Collectors;

@Service
public class GeminiService {

    private final PriceRepository priceRepository;
    @Value("${gemini.api.key}")
    private String apiKey;
    private final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    public GeminiService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public String getMarketPrediction() {
        List<Price> history = priceRepository.findTop50ByOrderByTimestampDesc();
        if (history.isEmpty()) {
            return "Nu am destule date pentru o analiza.";
        }
        String priceString = history.stream()
                .map(p -> p.getPrice().toString())
                .collect(Collectors.joining(", "));
        String promptText = "You are a financial crypto expert. Here is the recent Bitcoin price history (newest first): ["
                + priceString + "]. "
                + "Analyze the volatility and the trend. Tell me if it's crashing, pumping, or stable. "
                + "Give a short prediction for the next 5 minutes. "
                + "Keep the response under 3 sentences. Be professional.";
        String jsonBody = "{ \"contents\": [{ \"parts\": [{ \"text\": \"" + promptText + "\" }] }] }";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            if (responseBody.contains("\"text\": \"")) {
                int startIndex = responseBody.indexOf("\"text\": \"") + 9;
                int endIndex = responseBody.indexOf("\"", startIndex);
                String analysis = responseBody.substring(startIndex, endIndex);
                return analysis.replace("\\n", " ");
            }
            return "Analiza indisponibila momentan (Raw: " + response.statusCode() + ")";

        } catch (Exception e) {
            return "Eroare interna AI: " + e.getMessage();
        }
    }
}