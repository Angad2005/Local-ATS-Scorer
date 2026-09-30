package com.morrow.ats.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvaluationResponse(
        UUID id,
        String candidateName,
        String fileName,
        String jobTitle,
        int score,
        String fitLabel,
        List<String> matchedSkills,
        List<String> missingSkills,
        int skillScore,
        int experienceScore,
        Integer semanticScore,
        Integer yearsExperience,
        String summary,
        String explanation,
        Instant createdAt) {
    public static EvaluationResponse from(EvaluationEntity entity) {
        return new EvaluationResponse(
                entity.getId(), entity.getCandidateName(), entity.getFileName(), entity.getJobTitle(),
                entity.getScore(), entity.getFitLabel(), entity.getMatchedSkills(), entity.getMissingSkills(),
                entity.getSkillScore(), entity.getExperienceScore(), entity.getSemanticScore(), entity.getYearsExperience(),
                entity.getSummary(), entity.getExplanation(), entity.getCreatedAt());
    }
}