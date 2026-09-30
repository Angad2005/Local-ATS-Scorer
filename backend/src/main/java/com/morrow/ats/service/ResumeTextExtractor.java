package com.morrow.ats.service;

import java.io.IOException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeTextExtractor {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Tika TIKA = new Tika();

    public String extract(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The selected resume is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Resume files must be 10 MB or smaller.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(filename.endsWith(".pdf") || filename.endsWith(".docx") || filename.endsWith(".doc") || filename.endsWith(".txt"))) {
            throw new IllegalArgumentException("Use a PDF, DOCX, DOC, or TXT resume.");
        }
        try {
            return TIKA.parseToString(file.getInputStream()).trim();
        } catch (IOException | TikaException exception) {
            throw new IllegalArgumentException("This resume could not be read. Try exporting it as PDF or DOCX.", exception);
        }
    }
}