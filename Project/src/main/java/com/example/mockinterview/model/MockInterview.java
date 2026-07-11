package com.example.mockinterview.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mock_interview", indexes = {
    @Index(name = "idx_mock_interview_mock_id", columnList = "mock_id"),
    @Index(name = "idx_mock_interview_created_by", columnList = "created_by")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mock_id", nullable = false)
    private String mockId;

    @Column(name = "json_mock_resp", columnDefinition = "TEXT", nullable = false)
    private String jsonMockResp;

    @Column(name = "job_position", nullable = false)
    private String jobPosition;

    @Column(name = "job_desc", nullable = false)
    private String jobDesc;

    @Column(name = "job_experience", nullable = false)
    private String jobExperience;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at")
    private String createdAt;
}
