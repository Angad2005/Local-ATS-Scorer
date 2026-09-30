package com.morrow.ats.service;

import com.morrow.ats.model.EvaluationEntity;
import com.morrow.ats.model.EvaluationResponse;
import com.morrow.ats.repository.EvaluationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvaluationService {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b\\S+@\\S+\\.\\S+\\b");
    private final EvaluationRepository repository;
    private final ResumeTextExtractor textExtractor;
    private final ScoringService scoringService;
    private final LmStudioClient lmStudioClient;

    public EvaluationService(EvaluationRepository repository, ResumeTextExtractor textExtractor,
            ScoringService scoringService, LmStudioClient lmStudioClient) {
        this.repository = repository;
        this.textExtractor = textExtractor;
        this.scoringService = scoringService;
        this.lmStudioClient = lmStudioClient;
    }

    @Transactional
    public EvaluationResponse evaluate(String jobTitle, String jobDescription, MultipartFile file) {
        String resumeText = textExtractor.extract(file);
        if (resumeText.isBlank()) {
            throw new IllegalArgumentException("No readable text was found in this resume.");
        }
        ScoringService.MatchScore match = scoringService.score(jobDescription, resumeText);
        match = lmStudioClient.semanticSimilarity(jobDescription, resumeText)
            .map(match::withSemanticScore)
            .orElse(match);
        String candidateName = candidateName(resumeText, file.getOriginalFilename());
        String fitLabel = match.score() >= 75 ? "Strong match" : match.score() >= 50 ? "Potential match" : "Limited evidence";
        String summary = summary(match);
        String explanation = lmStudioClient.explain(jobTitle, jobDescription, resumeText, match).orElse(null);
        EvaluationEntity entity = new EvaluationEntity(
                UUID.randomUUID(), candidateName, safeFilename(file.getOriginalFilename()), jobTitle.trim(),
                match.score(), fitLabel, match.matchedSkills(), match.missingSkills(), match.skillScore(),
                match.experienceScore(), match.semanticScore(), match.yearsExperience(), summary, explanation, Instant.now());
        return EvaluationResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<EvaluationResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(EvaluationResponse::from).toList();
    }

    private static String summary(ScoringService.MatchScore match) {
        if (match.requiredSkillCount() == 0 && match.preferredSkillCount() == 0) {
            return "No catalogued skills were detected in the job description. Add specific requirements for a more informative comparison.";
        }
        String skills = match.matchedSkills().isEmpty()
                ? "No listed skills were detected"
                : "Detected " + String.join(", ", match.matchedSkills());
        String gaps = match.missingSkills().isEmpty()
                ? "; no listed skill gaps detected"
                : "; not detected: " + String.join(", ", match.missingSkills());
        String experience = match.yearsExperience() == null
                ? ". Years of experience were not detected"
                : ". Detected " + match.yearsExperience() + " years of experience";
        return skills + gaps + experience + ".";
    }

    private static String candidateName(String resumeText, String filename) {
        for (String line : resumeText.split("\\R")) {
            String candidate = line.trim().replaceAll("\\s+", " ");
            if (candidate.isBlank() || candidate.length() > 70 || EMAIL.matcher(candidate).find()
                    || candidate.matches(".*\\d{3,}.*") || candidate.toLowerCase(Locale.ROOT).matches("resume|curriculum vitae|cv")) {
                continue;
            }
            if (candidate.split(" ").length >= 2) return candidate;
        }
        String name = safeFilename(filename).replaceFirst("(?i)\\.(pdf|docx?|txt)$", "").replace('_', ' ').replace('-', ' ').trim();
        return name.isBlank() ? "Candidate" : name;
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "resume";
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}