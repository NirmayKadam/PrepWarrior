package com.example.mockinterview.controller;

import com.example.mockinterview.model.Question;
import com.example.mockinterview.repository.QuestionRepository;
import com.example.mockinterview.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionRepository questionRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public QuestionController(QuestionRepository questionRepository,
                              GeminiService geminiService) {
        this.questionRepository = questionRepository;
        this.geminiService = geminiService;
        this.objectMapper = new ObjectMapper();
    }

    // POST /api/questions — generate custom questions bank and save
    @PostMapping
    public ResponseEntity<?> createQuestions(@RequestBody CreateQuestionsRequest request,
                                             java.security.Principal principal) throws Exception {
        String userEmail = (principal != null) ? principal.getName() : "user@example.com";
        String jobPosition = request.getJobPosition();
        String jobDesc = request.getJobDesc();
        String jobExperience = request.getJobExperience();
        String typeQuestion = request.getTypeQuestion();
        String company = request.getCompany();

        // Validate
        if (jobPosition == null || jobPosition.trim().isEmpty() ||
            jobDesc == null || jobDesc.trim().isEmpty() ||
            typeQuestion == null || typeQuestion.trim().isEmpty() ||
            company == null || company.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required fields");
        }

        String experienceStr = jobExperience != null ? jobExperience : "0";

        // Generate questions with Gemini
        String mockQuestionJsonResp = geminiService.generateCompanyQuestions(
                jobPosition, jobDesc, experienceStr, typeQuestion, company
        );

        // Verify AI output is valid JSON
        try {
            objectMapper.readTree(mockQuestionJsonResp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Failed to parse AI response. Please try again."));
        }

        // Save to DB
        String mockId = UUID.randomUUID().toString();
        String createdAt = LocalDate.now().toString();

        Question questionEntity = Question.builder()
                .mockId(mockId)
                .mockQuestionJsonResp(mockQuestionJsonResp)
                .jobPosition(jobPosition)
                .jobDesc(jobDesc)
                .jobExperience(experienceStr)
                .typeQuestion(typeQuestion)
                .company(company)
                .createdBy(userEmail)
                .createdAt(createdAt)
                .build();

        questionRepository.save(questionEntity);

        return ResponseEntity.ok(Map.of("mockId", mockId));
    }

    // GET /api/questions/{id} — fetch custom questions by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getQuestions(@PathVariable("id") String id) {
        return questionRepository.findByMockId(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Questions not found")));
    }

    // GET /api/questions/list — fetch all custom questions for the currently logged-in user
    @GetMapping("/list")
    public ResponseEntity<java.util.List<Question>> listQuestions(java.security.Principal principal) {
        String userEmail = (principal != null) ? principal.getName() : "user@example.com";
        java.util.List<Question> questions = questionRepository.findByCreatedByOrderByIdDesc(userEmail);
        return ResponseEntity.ok(questions);
    }

    @Data
    public static class CreateQuestionsRequest {
        private String jobPosition;
        private String jobDesc;
        private String jobExperience;
        private String typeQuestion;
        private String company;
    }
}
