package com.morrow.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.morrow.ats.model.AtsAnalysisResponse;
import org.junit.jupiter.api.Test;

class AtsMatchingServiceTest {
    private final AtsMatchingService service = new AtsMatchingService();

    @Test
    void normalizesGenerativeAiLlmAndRagIntoEvidenceBackedMatches() {
        AtsAnalysisResponse result = service.analyze(
                "AI Engineer",
                "Required: Generative AI and Large Language Models. Retrieval-Augmented Generation is preferred.",
                "Projects\nBuilt a Generative AI assistant with LLM and RAG retrieval.",
                "resume.txt");

        assertThat(result.matchedRequirements()).extracting(AtsAnalysisResponse.Match::canonicalName)
                .contains("generative_ai", "large_language_models", "retrieval_augmented_generation");
        assertThat(result.breakdown().get("requiredSkills").score()).isEqualTo(100);
        assertThat(result.missingRequirements()).isEmpty();
    }

    @Test
    void reportsPartialAndMissingRequirementsWithoutInventingEvidence() {
        AtsAnalysisResponse result = service.analyze(
                "Marketing Engineer",
                "Required: Python. Digital marketing is a plus.",
                "Summary\nFamiliar with Python and interested in marketing.",
                "resume.txt");

        assertThat(result.partialMatches()).extracting(AtsAnalysisResponse.Match::canonicalName).contains("python");
        assertThat(result.missingRequirements()).extracting(AtsAnalysisResponse.Match::canonicalName).contains("digital_marketing");
    }

    @Test
    void marksRequiredAndPreferredRequirementsCorrectly() {
        AtsAnalysisResponse result = service.analyze(
                "AI Engineer",
                "Required: Python. Digital marketing is a plus.",
                "Experience\nBuilt Python services.",
                "resume.txt");

        assertThat(result.matchedRequirements()).singleElement()
                .extracting(AtsAnalysisResponse.Match::required).isEqualTo(true);
        assertThat(result.missingRequirements()).singleElement()
                .extracting(AtsAnalysisResponse.Match::required).isEqualTo(false);
        assertThat(result.breakdown().get("requiredSkills").score()).isEqualTo(100);
        assertThat(result.breakdown().get("preferredSkills").score()).isZero();
    }
}