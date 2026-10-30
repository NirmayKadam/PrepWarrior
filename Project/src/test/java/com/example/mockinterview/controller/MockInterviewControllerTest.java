package com.example.mockinterview.controller;

import com.example.mockinterview.model.MockInterview;
import com.example.mockinterview.model.UserAnswer;
import com.example.mockinterview.repository.MockInterviewRepository;
import com.example.mockinterview.repository.UserAnswerRepository;
import com.example.mockinterview.service.GeminiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Plain unit tests: no Spring context, DB, Redis or Gemini key needed
class MockInterviewControllerTest {

    private static final String OWNER = "alice@example.com";
    private static final Principal ALICE = () -> OWNER;
    private static final Principal BOB = () -> "bob@example.com";

    private MockInterviewRepository interviewRepo;
    private UserAnswerRepository answerRepo;
    private GeminiService gemini;
    private MockInterviewController controller;

    @BeforeEach
    void setUp() {
        interviewRepo = mock(MockInterviewRepository.class);
        answerRepo = mock(UserAnswerRepository.class);
        gemini = mock(GeminiService.class);
        controller = new MockInterviewController(interviewRepo, answerRepo, gemini);
    }

    private static MockInterviewController.CreateInterviewRequest createRequest(String experience) {
        MockInterviewController.CreateInterviewRequest req = new MockInterviewController.CreateInterviewRequest();
        req.setJobPosition("Backend Developer");
        req.setJobDesc("Java, Spring Boot");
        req.setJobExperience(experience);
        return req;
    }

    private static MockInterviewController.RecordAnswerRequest answerRequest(String userAns) {
        MockInterviewController.RecordAnswerRequest req = new MockInterviewController.RecordAnswerRequest();
        req.setQuestion("What is DI?");
        req.setUserAns(userAns);
        return req;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void createInterview_rejectsNonNumericExperience() {
        assertThatThrownBy(() -> controller.createInterview(createRequest("two"), ALICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a number");
        verifyNoInteractions(gemini);
    }

    @Test
    void createInterview_rejectsExperienceOutOfRange() {
        assertThatThrownBy(() -> controller.createInterview(createRequest("51"), ALICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 50");
    }

    @Test
    void createInterview_returns502WhenAiOutputIsNotJson() throws Exception {
        when(gemini.generateQuestions(anyString(), anyString(), anyString())).thenReturn("Sure! Here are your questions:");

        ResponseEntity<?> response = controller.createInterview(createRequest("2"), ALICE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(interviewRepo, never()).save(any());
    }

    @Test
    void createInterview_savesAndReturns201ForValidAiOutput() throws Exception {
        when(gemini.generateQuestions(anyString(), anyString(), anyString()))
                .thenReturn("[{\"Question\":\"Q?\",\"Answer\":\"A.\"}]");

        ResponseEntity<?> response = controller.createInterview(createRequest("2"), ALICE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(response)).containsKey("mockId");
        verify(interviewRepo).save(argThat(saved -> OWNER.equals(saved.getCreatedBy())));
    }

    @Test
    void getInterview_returns404ForSomeoneElsesInterview() {
        when(interviewRepo.findByMockIdAndCreatedBy("abc", "bob@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getInterview("abc", BOB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getFeedback_returns404ForSomeoneElsesInterview() {
        when(interviewRepo.findByMockIdAndCreatedBy("abc", "bob@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getFeedback("abc", BOB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(answerRepo);
    }

    @Test
    void recordAnswer_doesNotCallGeminiForSomeoneElsesInterview() throws Exception {
        when(interviewRepo.findByMockIdAndCreatedBy("abc", "bob@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.recordAnswer("abc", answerRequest("Spring creates beans"), BOB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(gemini);
    }

    @Test
    void recordAnswer_rejectsOverlongAnswer() {
        assertThatThrownBy(() -> controller.recordAnswer("abc", answerRequest("x".repeat(5001)), ALICE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gemini);
    }

    @Test
    void recordAnswer_savesRatingAndFeedbackForOwner() throws Exception {
        when(interviewRepo.findByMockIdAndCreatedBy("abc", OWNER)).thenReturn(Optional.of(new MockInterview()));
        when(gemini.evaluateAnswer(anyString(), anyString())).thenReturn("{\"rating\": 8, \"feedback\": \"Good\"}");

        ResponseEntity<?> response = controller.recordAnswer("abc", answerRequest("Spring creates beans"), ALICE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response)).containsEntry("rating", "8").containsEntry("feedback", "Good");
        verify(answerRepo).save(any(UserAnswer.class));
    }

    @Test
    void getFeedback_returnsAnswersForOwner() {
        when(interviewRepo.findByMockIdAndCreatedBy("abc", OWNER)).thenReturn(Optional.of(new MockInterview()));
        when(answerRepo.findByMockIdRefOrderByIdAsc("abc")).thenReturn(List.of(new UserAnswer()));

        ResponseEntity<?> response = controller.getFeedback("abc", ALICE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).hasSize(1);
    }
}
