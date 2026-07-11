package com.example.mockinterview.controller;

import com.example.mockinterview.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/transcribe")
public class TranscribeController {

    private final GeminiService geminiService;

    public TranscribeController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // POST /api/transcribe — transcribe audio file via Gemini
    @PostMapping
    public ResponseEntity<?> transcribeAudio(@RequestParam("audio") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No audio file provided");
        }

        // Limit size to 15MB to prevent memory issues
        if (file.getSize() > 15 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds limit of 15MB");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("Invalid file type. Only audio files are allowed.");
        }

        byte[] bytes = file.getBytes();
        String transcription = geminiService.transcribe(bytes, contentType);

        return ResponseEntity.ok(Map.of("transcription", transcription));
    }
}
