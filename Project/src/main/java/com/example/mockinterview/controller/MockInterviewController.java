package com.example.mockinterview.controller;

import com.example.mockinterview.model.MockInterview;
import com.example.mockinterview.model.UserAnswer;
import com.example.mockinterview.repository.MockInterviewRepository;
import com.example.mockinterview.repository.UserAnswerRepository;
import com.example.mockinterview.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
public class MockInterviewController {

    private final MockInterviewRepository mockInterviewRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public MockInterviewController(MockInterviewRepository mockInterviewRepository,
                                   UserAnswerRepository userAnswerRepository,
                                   GeminiService geminiService) {
        this.mockInterviewRepository = mockInterviewRepository;
        this.userAnswerRepository = userAnswerRepository;
        this.geminiService = geminiService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping
    public ResponseEntity<?> createInterview(@RequestBody CreateInterviewRequest request,
                                             java.security.Principal principal) throws Exception {
        String userEmail = (principal != null) ? principal.getName() : "user@example.com";
        String jobPosition = request.getJobPosition();
        String jobDesc = request.getJobDesc();
        String jobExperience = request.getJobExperience();

        // Validate
        if (jobPosition == null || jobPosition.trim().isEmpty() ||
            jobDesc == null || jobDesc.trim().isEmpty() ||
            jobExperience == null || jobExperience.trim().isEmpty()) {
            throw new IllegalArgumentException("All fields are required");
        }

        int expNum;
        try {
            expNum = Integer.parseInt(jobExperience.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Years of experience must be a number");
        }

        if (expNum < 0 || expNum > 50) {
            throw new IllegalArgumentException("Years of experience must be between 0 and 50");
        }

        // Sanitize
        String cleanPosition = sanitize(jobPosition);
        String cleanDesc = sanitize(jobDesc);
        String cleanExperience = String.valueOf(expNum);

        // Generate questions with Gemini
        String jsonMockResp = geminiService.generateQuestions(cleanPosition, cleanDesc, cleanExperience);

        // Verify AI output is valid JSON
        try {
            objectMapper.readTree(jsonMockResp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Failed to parse AI response. Please try again."));
        }

        // Save to DB
        String mockId = UUID.randomUUID().toString();
        String createdAt = LocalDate.now().toString();

        MockInterview mockInterview = MockInterview.builder()
                .mockId(mockId)
                .jsonMockResp(jsonMockResp)
                .jobPosition(cleanPosition)
                .jobDesc(cleanDesc)
                .jobExperience(cleanExperience)
                .createdBy(userEmail)
                .createdAt(createdAt)
                .build();

        mockInterviewRepository.save(mockInterview);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mockId", mockId));
    }

    // GET /api/interviews/{id} — fetch a single interview by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getInterview(@PathVariable("id") String id) {
        return mockInterviewRepository.findByMockId(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Interview not found")));
    }

    // GET /api/interviews/list — fetch all interviews for the currently logged-in user
    @GetMapping("/list")
    public ResponseEntity<List<MockInterview>> listInterviews(java.security.Principal principal) {
        String userEmail = (principal != null) ? principal.getName() : "user@example.com";
        List<MockInterview> interviews = mockInterviewRepository.findByCreatedByOrderByIdDesc(userEmail);
        return ResponseEntity.ok(interviews);
    }

    // POST /api/interviews/{id}/answer — evaluate answer with Gemini and save to DB
    @PostMapping("/{id}/answer")
    public ResponseEntity<?> recordAnswer(@PathVariable("id") String id,
                                          @RequestBody RecordAnswerRequest request,
                                          java.security.Principal principal) throws Exception {
        String userEmail = (principal != null) ? principal.getName() : "user@example.com";
        String question = request.getQuestion();
        String correctAns = request.getCorrectAns();
        String userAns = request.getUserAns();

        if (question == null || question.trim().isEmpty() ||
            userAns == null || userAns.trim().isEmpty()) {
            throw new IllegalArgumentException("question and userAns are required");
        }

        // Build Gemini feedback
        String feedbackJsonRaw = geminiService.evaluateAnswer(question, userAns);

        Map<?, ?> feedbackMap;
        try {
            feedbackMap = objectMapper.readValue(feedbackJsonRaw, Map.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Failed to parse AI feedback. Please try again."));
        }

        String feedback = feedbackMap.get("feedback") != null ? feedbackMap.get("feedback").toString() : "";
        String rating = feedbackMap.get("rating") != null ? feedbackMap.get("rating").toString() : "0";
        String createdAt = LocalDate.now().toString();

        UserAnswer userAnswer = UserAnswer.builder()
                .mockIdRef(id)
                .question(question)
                .correctAns(correctAns != null ? correctAns : "")
                .userAns(userAns)
                .feedback(feedback)
                .rating(rating)
                .userEmail(userEmail)
                .createdAt(createdAt)
                .build();

        userAnswerRepository.save(userAnswer);

        return ResponseEntity.ok(Map.of("feedback", feedback, "rating", rating));
    }

    // GET /api/interviews/{id}/feedback — fetch all answers+feedback for an interview
    @GetMapping("/{id}/feedback")
    public ResponseEntity<List<UserAnswer>> getFeedback(@PathVariable("id") String id) {
        List<UserAnswer> answers = userAnswerRepository.findByMockIdRefOrderByIdAsc(id);
        return ResponseEntity.ok(answers);
    }

    private String sanitize(String str) {
        if (str == null) return "";
        String sanitized = str.replaceAll("[<>{}]", "").trim();
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500);
        }
        return sanitized;
    }

    @Data
    public static class CreateInterviewRequest {
        private String jobPosition;
        private String jobDesc;
        private String jobExperience;
    }

    @Data
    public static class RecordAnswerRequest {
        private String question;
        private String correctAns;
        private String userAns;
    }
}
