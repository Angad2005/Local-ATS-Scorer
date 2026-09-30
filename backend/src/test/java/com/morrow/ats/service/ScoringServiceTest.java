package com.morrow.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {
    private final ScoringService scoringService = new ScoringService();

    @Test
    void scoresRequiredSkillsExperienceAndPreferredSkillsSeparately() {
        ScoringService.MatchScore result = scoringService.score(
                "Required: Java, Spring Boot, PostgreSQL. 5+ years experience. Kubernetes is a plus.",
                "Jordan Rivera\n7 years of experience building Java and Spring Boot services.");

        assertThat(result.matchedSkills()).contains("Java", "Spring Boot");
        assertThat(result.missingSkills()).contains("PostgreSQL", "Kubernetes");
        assertThat(result.requiredSkillCount()).isEqualTo(3);
        assertThat(result.preferredSkillCount()).isEqualTo(1);
        assertThat(result.yearsExperience()).isEqualTo(7);
        assertThat(result.experienceScore()).isEqualTo(100);
    }

    @Test
    void doesNotInventExperienceWhenResumeHasNoYearCount() {
        ScoringService.MatchScore result = scoringService.score(
                "Need 4 years of Java experience.", "Built Java services and APIs.");

        assertThat(result.yearsExperience()).isNull();
        assertThat(result.experienceScore()).isZero();
    }

    @Test
    void doesNotAwardFreePointsForCriteriaMissingFromTheJobDescription() {
        ScoringService.MatchScore skillOnly = scoringService.score(
                "Java and Spring Boot are required.", "Java engineer.");
        ScoringService.MatchScore experienceOnly = scoringService.score(
                "At least 5 years of experience.", "2 years of experience.");

        assertThat(skillOnly.score()).isEqualTo(skillOnly.skillScore());
        assertThat(experienceOnly.score()).isEqualTo(experienceOnly.experienceScore());
        assertThat(skillOnly.score()).isEqualTo(50);
        assertThat(experienceOnly.score()).isEqualTo(40);
    }

    @Test
    void usesSemanticSimilarityAloneWhenNoDeterministicCriteriaAreDetected() {
        ScoringService.MatchScore result = scoringService.score(
                "Seeking a collaborative problem solver.", "Creative team member.");

        assertThat(result.score()).isZero();
        assertThat(result.withSemanticScore(62).score()).isEqualTo(62);
    }

    @Test
    void appliesSemanticSimilarityOnlyWhenAnEmbeddingScoreIsAvailable() {
        ScoringService.MatchScore result = scoringService.score(
                "Java and 5 years of experience.", "Java developer with 5 years of experience.");

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.semanticScore()).isNull();
        assertThat(result.withSemanticScore(0).score()).isEqualTo(85);
        assertThat(result.withSemanticScore(0).semanticScore()).isZero();
    }

    @Test
    void convertsEmbeddingVectorsToBoundedCosinePercentages() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode aligned = mapper.createArrayNode().add(1).add(0);
        ArrayNode opposed = mapper.createArrayNode().add(-1).add(0);
        ArrayNode zero = mapper.createArrayNode().add(0).add(0);

        assertThat(SemanticSimilarity.cosinePercent(aligned, aligned)).contains(100);
        assertThat(SemanticSimilarity.cosinePercent(aligned, opposed)).contains(0);
        assertThat(SemanticSimilarity.cosinePercent(aligned, zero)).isEmpty();
    }
}