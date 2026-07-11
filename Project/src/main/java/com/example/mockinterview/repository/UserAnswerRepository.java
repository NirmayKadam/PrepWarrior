package com.example.mockinterview.repository;

import com.example.mockinterview.model.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {
    List<UserAnswer> findByMockIdRefOrderByIdAsc(String mockIdRef);
}
