package com.morrow.ats.repository;

import com.morrow.ats.model.EvaluationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<EvaluationEntity, UUID> {
    List<EvaluationEntity> findAllByOrderByCreatedAtDesc();
}