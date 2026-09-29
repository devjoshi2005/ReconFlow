package com.reconflow.repository;

import com.reconflow.entity.ExceptionQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExceptionQueueRepository extends JpaRepository<ExceptionQueue, UUID> {
}
