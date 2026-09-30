package com.morrow.ats.service;

import com.morrow.ats.model.AtsAnalysisResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AtsMatchingService {
    private static final Pattern YEARS = Pattern.compile("(?i)\\b(\\d{1,2})\\+?\\s*(?:years?|yrs?)\\b");
    private static final Pattern PREFERRED = Pattern.compile("(?i)preferred|nice[- ]to[- ]have|bonus|a plus|plus|optional");
    private static final Map<String, List<String>> ALIASES = aliases();

    @Value("${app.scoring.required-skills-weight:40}") private int requiredWeight;
    @Value("${app.scoring.preferred-skills-weight:15}") private int preferredWeight;
    @Value("${app.scoring.experience-weight:15}") private int experienceWeight;
    @Value("${app.scoring.project-weight:10}") private int projectWeight;
    @Value("${app.scoring.education-weight:5}") private int educationWeight;
    @Value("${app.scoring.semantic-weight:10}") private int semanticWeight;
    @Value("${app.scoring.readability-weight:5}") private int readabilityWeight;

    public AtsAnalysisResponse analyze(String jobTitle, String jobDescription, String resumeText, String fileName) {
        Map<String, Boolean> requirements = extractRequirements(jobDescription);
        Map<String, Evidence> evidence = extractEvidence(resumeText);
        List<AtsAnalysisResponse.Match> matched = new ArrayList<>();
        List<AtsAnalysisResponse.Match> partial = new ArrayList<>();
        List<AtsAnalysisResponse.Match> missing = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : requirements.entrySet()) {
            String canonical = entry.getKey();
            Evidence found = evidence.get(canonical);
            String classification = found == null ? "NO_MATCH" : found.strength;
            AtsAnalysisResponse.Match match = new AtsAnalysisResponse.Match(
                    displayName(canonical), canonical, category(canonical), !entry.getValue(), classification,
                    found == null ? null : found.text, found == null ? null : found.section,
                    found == null ? 0 : found.confidence);
            if ("STRONG_MATCH".equals(classification)) matched.add(match);
            else if ("PARTIAL_MATCH".equals(classification)) partial.add(match);
            else missing.add(match);
        }

        int requiredCount = (int) requirements.entrySet().stream().filter(entry -> !entry.getValue()).count();
        int preferredCount = requirements.size() - requiredCount;
        long requiredMatches = matched.stream().filter(AtsAnalysisResponse.Match::required).count();
        long preferredMatches = matched.stream().filter(match -> !match.required()).count();
        long requiredPartial = partial.stream().filter(AtsAnalysisResponse.Match::required).count();
        long preferredPartial = partial.stream().filter(match -> !match.required()).count();
        int requiredScore = percentage(requiredMatches * 100 + requiredPartial * 50, requiredCount);
        int preferredScore = percentage(preferredMatches * 100 + preferredPartial * 50, preferredCount);
        int experienceScore = experienceScore(jobDescription, resumeText);
        int projectScore = projectScore(requirements, evidence);
        int readabilityScore = readability(resumeText).score();
        int semanticScore = requirements.isEmpty() ? 0 : Math.min(100, (int) Math.round((matched.size() * 100.0 + partial.size() * 50.0) / requirements.size()));
        int totalWeight = requiredWeight + preferredWeight + experienceWeight + projectWeight + educationWeight + semanticWeight + readabilityWeight;
        Map<String, AtsAnalysisResponse.ScoreComponent> breakdown = new LinkedHashMap<>();
        breakdown.put("requiredSkills", component(requiredScore, requiredWeight, totalWeight, "Required requirements with partial matches counted at half strength."));
        breakdown.put("preferredSkills", component(preferredScore, preferredWeight, totalWeight, "Preferred requirements are reported separately."));
        breakdown.put("experience", component(experienceScore, experienceWeight, totalWeight, "Explicit years and demonstrated experience evidence."));
        breakdown.put("projectRelevance", component(projectScore, projectWeight, totalWeight, "Relevant project evidence is counted without treating projects as employment."));
        breakdown.put("education", component(educationScore(resumeText), educationWeight, totalWeight, "Education evidence detected in the resume."));
        breakdown.put("semanticRelevance", component(semanticScore, semanticWeight, totalWeight, "Deterministic normalized relevance; optional embeddings can enrich this later."));
        breakdown.put("atsReadability", component(readabilityScore, readabilityWeight, totalWeight, "Extractability and recognizable resume structure."));
        int overall = (int) Math.round(breakdown.values().stream().mapToDouble(AtsAnalysisResponse.ScoreComponent::contribution).sum());
        AtsAnalysisResponse.KeywordAnalysis keywords = keywords(requirements, evidence, resumeText);
        List<String> recommendations = recommendations(missing, partial);
        return new AtsAnalysisResponse(UUID.randomUUID(), fileName, jobTitle.trim(), Math.min(100, overall),
                "RESUME_JOB_MATCH", breakdown, matched, partial, missing, readability(resumeText), keywords,
                recommendations, null, false, java.time.Instant.now());
    }

    private AtsAnalysisResponse.ScoreComponent component(int score, int weight, int totalWeight, String explanation) {
        return new AtsAnalysisResponse.ScoreComponent(score, weight, score * weight / (double) totalWeight, explanation);
    }

    private static Map<String, Boolean> extractRequirements(String text) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                Matcher matcher = boundary(alias).matcher(text);
                if (matcher.find()) {
                    int start = matcher.start();
                    while (start > 0 && ".!?;\\n".indexOf(text.charAt(start - 1)) < 0) start--;
                    int end = matcher.end();
                    while (end < text.length() && ".!?;\\n".indexOf(text.charAt(end)) < 0) end++;
                    boolean preferred = PREFERRED.matcher(text.substring(start, end)).find();
                    result.merge(entry.getKey(), preferred, (current, next) -> current && next);
                    break;
                }
            }
        }
        return result;
    }

    private static Map<String, Evidence> extractEvidence(String text) {
        Map<String, Evidence> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                Matcher matcher = boundary(alias).matcher(text);
                if (matcher.find()) {
                    int start = Math.max(0, matcher.start() - 90);
                    int end = Math.min(text.length(), matcher.end() + 110);
                    String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
                    String section = section(text, matcher.start());
                    String lower = snippet.toLowerCase(Locale.ROOT);
                    String strength = lower.matches(".*\\b(learn|learning|interested|familiar|aspir).*") ? "PARTIAL_MATCH" : "STRONG_MATCH";
                    result.put(entry.getKey(), new Evidence(snippet, section, strength, strength.equals("STRONG_MATCH") ? .94 : .55));
                    break;
                }
            }
        }
        return result;
    }

    private static AtsAnalysisResponse.KeywordAnalysis keywords(Map<String, Boolean> requirements, Map<String, Evidence> evidence, String resume) {
        List<String> matched = new ArrayList<>(), missing = new ArrayList<>(), partial = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> stuffing = new ArrayList<>();
        for (String canonical : requirements.keySet()) {
            int count = 0;
            for (String alias : ALIASES.get(canonical)) count += occurrences(resume, alias);
            counts.put(displayName(canonical), count);
            Evidence item = evidence.get(canonical);
            if (item == null) missing.add(displayName(canonical));
            else if ("PARTIAL_MATCH".equals(item.strength)) partial.add(displayName(canonical));
            else matched.add(displayName(canonical));
            if (count >= 8) stuffing.add(displayName(canonical) + " appears " + count + " times; only evidence-backed mentions count.");
        }
        return new AtsAnalysisResponse.KeywordAnalysis(matched, missing, partial, counts, stuffing);
    }

    private static List<String> recommendations(List<AtsAnalysisResponse.Match> missing, List<AtsAnalysisResponse.Match> partial) {
        List<String> result = new ArrayList<>();
        missing.stream().limit(4).forEach(match -> result.add("If truthful, add evidence for " + match.requirement() + " through relevant work, projects, coursework, or measurable accomplishments."));
        partial.stream().limit(3).forEach(match -> result.add("Clarify the demonstrated level of " + match.requirement() + " with a concrete outcome or responsibility."));
        return result;
    }

    private static AtsAnalysisResponse.AtsReadability readability(String text) {
        List<String> detected = new ArrayList<>(), warnings = new ArrayList<>();
        if (!text.isBlank()) detected.add("Extractable text"); else warnings.add("No extractable text");
        for (String heading : List.of("experience", "education", "skills", "projects")) if (text.toLowerCase(Locale.ROOT).contains(heading)) detected.add(capitalize(heading));
        if (text.matches("(?s).*\\b\\S+@\\S+\\.\\S+\\b.*")) detected.add("Contact information"); else warnings.add("Contact information not detected");
        if (!text.matches("(?s).*\\b(19|20)\\d{2}\\b.*")) warnings.add("Dates may be difficult to parse");
        int score = Math.max(0, Math.min(100, detected.size() * 16 - warnings.size() * 8 + 20));
        return new AtsAnalysisResponse.AtsReadability(score, detected, warnings);
    }

    private static int experienceScore(String job, String resume) {
        Matcher required = YEARS.matcher(job), actual = YEARS.matcher(resume);
        if (!required.find()) return 100;
        if (!actual.find()) return 0;
        return Math.min(100, (int) Math.round(Integer.parseInt(actual.group(1)) * 100.0 / Integer.parseInt(required.group(1))));
    }

    private static int projectScore(Map<String, Boolean> requirements, Map<String, Evidence> evidence) {
        long projectMatches = evidence.values().stream().filter(item -> "PROJECTS".equals(item.section)).count();
        return requirements.isEmpty() ? 0 : Math.min(100, (int) Math.round(projectMatches * 100.0 / requirements.size()));
    }

    private static int educationScore(String resume) { return resume.toLowerCase(Locale.ROOT).matches("(?s).*\\b(bachelor|master|phd|degree|university|college)\\b.*") ? 100 : 0; }
    private static int percentage(long numerator, int denominator) { return denominator == 0 ? 0 : (int) Math.round(numerator / (double) denominator); }
    private static Pattern boundary(String value) { return Pattern.compile("(?i)(?<![\\p{Alnum}])" + Pattern.quote(value) + "(?![\\p{Alnum}])"); }
    private static int occurrences(String text, String value) { Matcher matcher = boundary(value).matcher(text); int count = 0; while (matcher.find()) count++; return count; }
    private static String section(String text, int position) { String prefix = text.substring(0, position).toLowerCase(Locale.ROOT); if (prefix.lastIndexOf("project") > prefix.lastIndexOf("experience")) return "PROJECTS"; if (prefix.lastIndexOf("education") > prefix.lastIndexOf("experience")) return "EDUCATION"; return "EXPERIENCE"; }
    private static String category(String canonical) { return canonical.contains("_") ? "REQUIRED_SKILL" : "TECHNOLOGY"; }
    private static String displayName(String canonical) { return canonical.replace('_', ' '); }
    private static String capitalize(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private record Evidence(String text, String section, String strength, double confidence) {}

    private static Map<String, List<String>> aliases() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("python", List.of("python", "python 3", "python programming"));
        map.put("java", List.of("java"));
        map.put("spring_boot", List.of("spring boot", "springboot"));
        map.put("postgresql", List.of("postgresql", "postgres"));
        map.put("javascript", List.of("javascript", "js", "ecmascript"));
        map.put("typescript", List.of("typescript", "ts"));
        map.put("machine_learning", List.of("machine learning", "ml"));
        map.put("deep_learning", List.of("deep learning", "dl"));
        map.put("generative_ai", List.of("generative ai", "generative artificial intelligence", "genai"));
        map.put("large_language_models", List.of("large language models", "large language model", "llms", "llm"));
        map.put("retrieval_augmented_generation", List.of("retrieval augmented generation", "retrieval-augmented generation", "rag"));
        map.put("docker", List.of("docker"));
        map.put("kubernetes", List.of("kubernetes", "k8s"));
        map.put("aws", List.of("aws", "amazon web services"));
        map.put("sql", List.of("sql"));
        map.put("digital_marketing", List.of("digital marketing", "digital engagement", "marketing automation"));
        return Map.copyOf(map);
    }
}