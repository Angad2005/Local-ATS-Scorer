package com.morrow.ats.api;

import com.morrow.ats.model.EvaluationResponse;
import com.morrow.ats.service.EvaluationService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ScoreController {
    private final EvaluationService evaluationService;

    public ScoreController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/api/scores")
    public List<EvaluationResponse> list() {
        return evaluationService.list();
    }

    @PostMapping(path = "/api/scores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationResponse evaluate(
            @RequestParam @NotBlank String jobTitle,
            @RequestParam @NotBlank String jobDescription,
            @RequestParam("resume") MultipartFile resume) {
        if (jobTitle.isBlank() || jobDescription.isBlank()) {
            throw new IllegalArgumentException("A role title and job description are required.");
        }
        return evaluationService.evaluate(jobTitle, jobDescription, resume);
    }
}