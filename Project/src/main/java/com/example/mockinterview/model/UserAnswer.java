package com.example.mockinterview.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_answer", indexes = {
    @Index(name = "idx_user_answer_mock_id_ref", columnList = "mock_id_ref"),
    @Index(name = "idx_user_answer_user_email", columnList = "user_email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mock_id_ref", nullable = false)
    private String mockIdRef;

    @Column(name = "question", nullable = false)
    private String question;

    @Column(name = "correct_ans", columnDefinition = "TEXT")
    private String correctAns;

    @Column(name = "user_ans", columnDefinition = "TEXT")
    private String userAns;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "rating")
    private String rating;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "created_at")
    private String createdAt;
}
