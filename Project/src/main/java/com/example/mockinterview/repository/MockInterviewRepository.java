package com.example.mockinterview.repository;

import com.example.mockinterview.model.MockInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockInterviewRepository extends JpaRepository<MockInterview, Long> {
    Optional<MockInterview> findByMockId(String mockId);
    List<MockInterview> findByCreatedByOrderByIdDesc(String createdBy);
}
