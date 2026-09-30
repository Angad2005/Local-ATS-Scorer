package com.morrow.ats.api;

import com.morrow.ats.model.AtsAnalysisResponse;
import com.morrow.ats.service.AtsMatchingService;
import com.morrow.ats.service.LmStudioClient;
import com.morrow.ats.service.ResumeTextExtractor;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AtsAnalysisController {
    private final AtsMatchingService matchingService;
    private final ResumeTextExtractor textExtractor;
    private final LmStudioClient lmStudioClient;

    public AtsAnalysisController(AtsMatchingService matchingService, ResumeTextExtractor textExtractor, LmStudioClient lmStudioClient) {
        this.matchingService = matchingService;
        this.textExtractor = textExtractor;
        this.lmStudioClient = lmStudioClient;
    }

    @PostMapping(path = "/api/ats/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AtsAnalysisResponse analyze(
            @RequestParam @NotBlank String jobTitle,
            @RequestParam @NotBlank String jobDescription,
            @RequestParam("resume") MultipartFile resume) {
        String resumeText = textExtractor.extract(resume);
        if (resumeText.isBlank()) throw new IllegalArgumentException("No readable text was found in this resume.");
        var analysis = matchingService.analyze(jobTitle, jobDescription, resumeText,
                resume.getOriginalFilename() == null ? "resume" : resume.getOriginalFilename());
        return new AtsAnalysisResponse(analysis.id(), analysis.fileName(), analysis.jobTitle(), analysis.overallScore(), analysis.scoreType(),
            analysis.breakdown(), analysis.matchedRequirements(), analysis.partialMatches(), analysis.missingRequirements(),
            analysis.atsReadability(), analysis.keywordAnalysis(), analysis.recommendations(),
            lmStudioClient.explain(analysis).orElse(null), lmStudioClient.isEnabled(), analysis.analyzedAt());
    }
}