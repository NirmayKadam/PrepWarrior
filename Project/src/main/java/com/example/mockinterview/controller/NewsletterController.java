package com.example.mockinterview.controller;

import com.example.mockinterview.model.Newsletter;
import com.example.mockinterview.repository.NewsletterRepository;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/newsletter")
@CrossOrigin(origins = "*")
public class NewsletterController {

    private final NewsletterRepository newsletterRepository;

    // Basic email validation regex matching Next.js check
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$");

    public NewsletterController(NewsletterRepository newsletterRepository) {
        this.newsletterRepository = newsletterRepository;
    }

    // POST /api/newsletter — save a contact form submission
    @PostMapping
    public ResponseEntity<?> saveSubmission(@RequestBody NewsletterRequest request) {
        try {
            String name = request.getName();
            String email = request.getEmail();
            String message = request.getMessage();

            if (name == null || name.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                message == null || message.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "All fields are required"));
            }

            if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid email address"));
            }

            String cleanName = name.trim();
            if (cleanName.length() > 100) cleanName = cleanName.substring(0, 100);

            String cleanEmail = email.trim();
            if (cleanEmail.length() > 200) cleanEmail = cleanEmail.substring(0, 200);

            String cleanMessage = message.trim();
            if (cleanMessage.length() > 2000) cleanMessage = cleanMessage.substring(0, 2000);

            String createdAt = LocalDate.now().toString();

            Newsletter newsletter = Newsletter.builder()
                    .newName(cleanName)
                    .newEmail(cleanEmail)
                    .newMessage(cleanMessage)
                    .createdAt(createdAt)
                    .build();

            newsletterRepository.save(newsletter);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send message. Please try again."));
        }
    }

    @Data
    public static class NewsletterRequest {
        private String name;
        private String email;
        private String message;
    }
}
