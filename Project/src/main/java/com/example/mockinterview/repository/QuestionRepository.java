package com.example.mockinterview.repository;

import com.example.mockinterview.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByMockId(String mockId);
    List<Question> findByCreatedByOrderByIdDesc(String createdBy);
}
