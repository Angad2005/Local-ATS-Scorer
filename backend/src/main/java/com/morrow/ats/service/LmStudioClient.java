package com.morrow.ats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.morrow.ats.model.AtsAnalysisResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LmStudioClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(LmStudioClient.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${app.lm-studio.base-url:}")
    private String baseUrl;

    @Value("${app.lm-studio.model:local-model}")
    private String model;

    @Value("${app.lm-studio.embedding-model:}")
    private String embeddingModel;

    @Value("${app.lm-studio.api-key:}")
    private String apiKey;

    public LmStudioClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public Optional<String> explain(String jobTitle, String jobDescription, String resumeText, ScoringService.MatchScore score) {
        if (!isEnabled()) return Optional.empty();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content",
                    "Explain resume evidence for a recruiter in 2-3 concise sentences. Mention demonstrated strengths and any material gaps. Do not infer protected traits or change the numeric score.");
            String prompt = "Role: " + jobTitle + "\nJob requirements: " + abbreviate(jobDescription, 7000)
                    + "\nResume text: " + abbreviate(resumeText, 12000)
                    + "\nDeterministic score: " + score.score() + "/100. Matched skills: " + score.matchedSkills()
                    + ". Not detected: " + score.missingSkills() + ". Experience fit: " + score.experienceScore() + "%.\n"
                    + "Use only evidence in the provided text.";
            messages.addObject().put("role", "user").put("content", prompt);

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (apiKey != null && !apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("LM Studio returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            String explanation = content.asText("").trim();
            return explanation.isBlank() ? Optional.empty() : Optional.of(abbreviate(explanation, 1200));
        } catch (Exception exception) {
            LOGGER.warn("LM Studio explanation unavailable: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> explain(AtsAnalysisResponse analysis) {
        if (!isEnabled()) return Optional.empty();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content",
                    "Explain this resume-to-job analysis in 2-3 concise sentences. Use only the structured matches, evidence, gaps, and score breakdown provided. State whether each gap is required or preferred exactly as indicated by its required field; never call a preferred requirement required. Do not change the numeric score, invent skills, or make hiring recommendations.");
            messages.addObject().put("role", "user").put("content", "Structured ATS analysis:\n" + objectMapper.writeValueAsString(analysis));
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (apiKey != null && !apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
            String explanation = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("").trim();
            return explanation.isBlank() ? Optional.empty() : Optional.of(abbreviate(explanation, 1200));
        } catch (Exception exception) {
            LOGGER.warn("LM Studio structured explanation unavailable: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Integer> semanticSimilarity(String jobDescription, String resumeText) {
        if (!isEnabled() || embeddingModel == null || embeddingModel.isBlank()) return Optional.empty();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", embeddingModel);
            ArrayNode input = payload.putArray("input");
            input.add(abbreviate(jobDescription, 7000));
            input.add(abbreviate(resumeText, 12000));

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (apiKey != null && !apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("LM Studio embeddings returned HTTP {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() < 2) return Optional.empty();
            JsonNode jobEmbedding = data.get(0).path("embedding");
            JsonNode resumeEmbedding = data.get(1).path("embedding");
            return SemanticSimilarity.cosinePercent(jobEmbedding, resumeEmbedding);
        } catch (Exception exception) {
            LOGGER.warn("LM Studio embeddings unavailable: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}