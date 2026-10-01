package com.reconflow.repository;

import com.reconflow.entity.ExceptionQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface ExceptionQueueRepository extends JpaRepository<ExceptionQueue, UUID> {
    
    long countByStatus(String status);

    @Query("SELECT e.exceptionType as type, COUNT(e) as count FROM ExceptionQueue e GROUP BY e.exceptionType")
    List<Map<String, Object>> countByExceptionType();

    @Query("SELECT COUNT(e) FROM ExceptionQueue e WHERE e.status = 'OPEN'")
    long countOpenExceptions();
}
