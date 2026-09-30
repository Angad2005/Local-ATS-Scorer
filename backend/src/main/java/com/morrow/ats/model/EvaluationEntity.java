package com.morrow.ats.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evaluations")
public class EvaluationEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 180)
    private String candidateName;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 180)
    private String jobTitle;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 40)
    private String fitLabel;

    @Convert(converter = SkillListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> matchedSkills;

    @Convert(converter = SkillListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> missingSkills;

    @Column(nullable = false)
    private int skillScore;

    @Column(nullable = false)
    private int experienceScore;

    private Integer semanticScore;

    private Integer yearsExperience;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private Instant createdAt;

    protected EvaluationEntity() {}

    public EvaluationEntity(
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
        this.id = id;
        this.candidateName = candidateName;
        this.fileName = fileName;
        this.jobTitle = jobTitle;
        this.score = score;
        this.fitLabel = fitLabel;
        this.matchedSkills = matchedSkills;
        this.missingSkills = missingSkills;
        this.skillScore = skillScore;
        this.experienceScore = experienceScore;
        this.semanticScore = semanticScore;
        this.yearsExperience = yearsExperience;
        this.summary = summary;
        this.explanation = explanation;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getCandidateName() { return candidateName; }
    public String getFileName() { return fileName; }
    public String getJobTitle() { return jobTitle; }
    public int getScore() { return score; }
    public String getFitLabel() { return fitLabel; }
    public List<String> getMatchedSkills() { return matchedSkills; }
    public List<String> getMissingSkills() { return missingSkills; }
    public int getSkillScore() { return skillScore; }
    public int getExperienceScore() { return experienceScore; }
    public Integer getSemanticScore() { return semanticScore; }
    public Integer getYearsExperience() { return yearsExperience; }
    public String getSummary() { return summary; }
    public String getExplanation() { return explanation; }
    public Instant getCreatedAt() { return createdAt; }
}