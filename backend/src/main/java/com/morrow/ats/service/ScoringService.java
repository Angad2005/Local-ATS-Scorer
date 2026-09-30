package com.morrow.ats.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {
    private static final Pattern YEARS = Pattern.compile("(?i)\\b(\\d{1,2})\\+?\\s*(?:years?|yrs?)\\b");
    private static final Pattern PREFERRED_CONTEXT = Pattern.compile("(?i)preferred|nice[- ]to[- ]have|bonus|a plus|plus");
    private static final Map<String, List<String>> SKILL_ALIASES = skillAliases();

    public MatchScore score(String jobDescription, String resumeText) {
        Map<String, Boolean> jobSkills = findSkills(jobDescription);
        Set<String> resumeSkills = findSkills(resumeText).keySet();
        List<String> required = new ArrayList<>();
        List<String> preferred = new ArrayList<>();
        for (Map.Entry<String, Boolean> skill : jobSkills.entrySet()) {
            (skill.getValue() ? preferred : required).add(skill.getKey());
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : required) {
            (resumeSkills.contains(skill) ? matched : missing).add(skill);
        }
        for (String skill : preferred) {
            if (resumeSkills.contains(skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int requiredScore = percentage(matched.stream().filter(required::contains).count(), required.size());
        int preferredScore = percentage(preferred.stream().filter(resumeSkills::contains).count(), preferred.size());
        boolean hasRequiredSkills = !required.isEmpty();
        boolean hasPreferredSkills = !preferred.isEmpty();
        boolean hasSkillCriteria = hasRequiredSkills || hasPreferredSkills;
        int skillScore = hasRequiredSkills && hasPreferredSkills
            ? (int) Math.round(requiredScore * 0.85 + preferredScore * 0.15)
            : hasRequiredSkills ? requiredScore : hasPreferredSkills ? preferredScore : 0;

        OptionalInt requiredYears = firstYears(jobDescription);
        OptionalInt resumeYears = firstYears(resumeText);
        boolean hasExperienceCriteria = requiredYears.isPresent() && requiredYears.getAsInt() > 0;
        int experienceScore = !hasExperienceCriteria
                ? 100
                : resumeYears.isEmpty() ? 0
                : Math.min(100, (int) Math.round(resumeYears.getAsInt() * 100.0 / requiredYears.getAsInt()));
        int totalScore = hasSkillCriteria && hasExperienceCriteria
            ? (int) Math.round(skillScore * 0.8 + experienceScore * 0.2)
            : hasSkillCriteria ? skillScore : hasExperienceCriteria ? experienceScore : 0;
        return new MatchScore(totalScore, skillScore, experienceScore, null,
                resumeYears.isPresent() ? resumeYears.getAsInt() : null,
            List.copyOf(matched), List.copyOf(missing), required.size(), preferred.size(),
            hasSkillCriteria || hasExperienceCriteria);
    }

    private static int percentage(long numerator, int denominator) {
        return denominator == 0 ? 100 : (int) Math.round(numerator * 100.0 / denominator);
    }

    private static OptionalInt firstYears(String text) {
        Matcher matcher = YEARS.matcher(text);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    private static Map<String, Boolean> findSkills(String text) {
        Map<String, Boolean> found = new LinkedHashMap<>();
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> skill : SKILL_ALIASES.entrySet()) {
            for (String alias : skill.getValue()) {
                Matcher matcher = Pattern.compile("(?i)(?<![\\p{Alnum}])" + Pattern.quote(alias) + "(?![\\p{Alnum}])").matcher(text);
                if (matcher.find()) {
                    int start = Math.max(0, matcher.start() - 65);
                    int end = Math.min(text.length(), matcher.end() + 40);
                    boolean preferred = PREFERRED_CONTEXT.matcher(lowerText.substring(start, end)).find();
                    found.merge(skill.getKey(), preferred, (current, next) -> current && next);
                    break;
                }
            }
        }
        return found;
    }

    private static Map<String, List<String>> skillAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("Java", List.of("java"));
        aliases.put("Spring Boot", List.of("spring boot", "springboot"));
        aliases.put("PostgreSQL", List.of("postgresql", "postgres"));
        aliases.put("SQL", List.of("sql"));
        aliases.put("REST APIs", List.of("rest api", "restful api", "rest apis", "restful apis"));
        aliases.put("Docker", List.of("docker"));
        aliases.put("AWS", List.of("aws", "amazon web services"));
        aliases.put("Kubernetes", List.of("kubernetes", "k8s"));
        aliases.put("Python", List.of("python"));
        aliases.put("Microservices", List.of("microservices", "microservice"));
        aliases.put("Kafka", List.of("kafka"));
        aliases.put("Redis", List.of("redis"));
        aliases.put("React", List.of("react"));
        aliases.put("TypeScript", List.of("typescript"));
        aliases.put("Machine Learning", List.of("machine learning", "ml"));
        aliases.put("Git", List.of("git"));
        aliases.put("CI/CD", List.of("ci/cd", "continuous integration", "continuous delivery"));
        return Map.copyOf(aliases);
    }

    public record MatchScore(
            int score,
            int skillScore,
            int experienceScore,
            Integer semanticScore,
            Integer yearsExperience,
            List<String> matchedSkills,
            List<String> missingSkills,
            int requiredSkillCount,
                int preferredSkillCount,
                boolean hasDeterministicCriteria) {
        public MatchScore withSemanticScore(int semanticScore) {
                int combinedScore = hasDeterministicCriteria
                    ? (int) Math.round(score * 0.85 + semanticScore * 0.15)
                    : semanticScore;
            return new MatchScore(combinedScore, skillScore, experienceScore, semanticScore,
                    yearsExperience, matchedSkills, missingSkills, requiredSkillCount, preferredSkillCount,
                    hasDeterministicCriteria);
        }
    }
}