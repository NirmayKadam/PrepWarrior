package com.example.mockinterview.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question", indexes = {
    @Index(name = "idx_question_mock_id", columnList = "mock_id"),
    @Index(name = "idx_question_created_by", columnList = "created_by")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mock_question_json_resp", columnDefinition = "TEXT", nullable = false)
    private String mockQuestionJsonResp;

    @Column(name = "job_position", nullable = false)
    private String jobPosition;

    @Column(name = "job_desc", nullable = false)
    private String jobDesc;

    @Column(name = "job_experience", nullable = false)
    private String jobExperience;

    @Column(name = "type_question", nullable = false)
    private String typeQuestion;

    @Column(name = "company", nullable = false)
    private String company;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "mock_id", nullable = false)
    private String mockId;
}
