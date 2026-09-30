package com.morrow.ats.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

final class SemanticSimilarity {
    private SemanticSimilarity() {}

    static Optional<Integer> cosinePercent(JsonNode first, JsonNode second) {
        if (!first.isArray() || !second.isArray() || first.isEmpty() || first.size() != second.size()) {
            return Optional.empty();
        }

        double dotProduct = 0;
        double firstMagnitude = 0;
        double secondMagnitude = 0;
        for (int index = 0; index < first.size(); index++) {
            JsonNode firstComponent = first.get(index);
            JsonNode secondComponent = second.get(index);
            if (!firstComponent.isNumber() || !secondComponent.isNumber()) return Optional.empty();
            double firstValue = firstComponent.asDouble();
            double secondValue = secondComponent.asDouble();
            dotProduct += firstValue * secondValue;
            firstMagnitude += firstValue * firstValue;
            secondMagnitude += secondValue * secondValue;
        }
        if (firstMagnitude == 0 || secondMagnitude == 0) return Optional.empty();
        double cosine = dotProduct / (Math.sqrt(firstMagnitude) * Math.sqrt(secondMagnitude));
        return Optional.of((int) Math.round(Math.max(0, Math.min(1, cosine)) * 100));
    }
}