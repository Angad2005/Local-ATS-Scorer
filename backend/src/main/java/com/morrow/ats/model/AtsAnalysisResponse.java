package com.morrow.ats.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AtsAnalysisResponse(
        UUID id,
        String fileName,
        String jobTitle,
        int overallScore,
        String scoreType,
        Map<String, ScoreComponent> breakdown,
        List<Match> matchedRequirements,
        List<Match> partialMatches,
        List<Match> missingRequirements,
        AtsReadability atsReadability,
        KeywordAnalysis keywordAnalysis,
        List<String> recommendations,
        String explanation,
        boolean semanticAnalysisAvailable,
        Instant analyzedAt) {

    public record ScoreComponent(int score, int weight, double contribution, String explanation) {}

    public record Match(
            String requirement,
            String canonicalName,
            String category,
            boolean required,
            String classification,
            String evidence,
            String sourceSection,
            double confidence) {}

    public record AtsReadability(int score, List<String> detected, List<String> warnings) {}

    public record KeywordAnalysis(
            List<String> matched,
            List<String> missing,
            List<String> partial,
            Map<String, Integer> mentionCounts,
            List<String> stuffingWarnings) {}
}