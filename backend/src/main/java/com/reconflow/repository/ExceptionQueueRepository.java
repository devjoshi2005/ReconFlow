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

    @Query("SELECT COUNT(e) FROM ExceptionQueue e WHERE e.status IN ('OPEN', 'INVESTIGATING')")
    long countOpenExceptions();

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - created_at))/86400) FROM exception_queue WHERE status = 'RESOLVED'", nativeQuery = true)
    Double avgResolutionTimeDays();

    @Query(value = "SELECT g.gateway FROM exception_queue e JOIN gateway_settlements g ON e.match_id = g.id GROUP BY g.gateway ORDER BY COUNT(e.id) DESC LIMIT 1", nativeQuery = true)
    String topExceptionGateway();

    @Query(value = "SELECT TO_CHAR(created_at, 'Dy') as name, SUM(CASE WHEN status='OPEN' THEN 1 ELSE 0 END) as \"OPEN\", SUM(CASE WHEN status='INVESTIGATING' THEN 1 ELSE 0 END) as \"INVESTIGATING\", SUM(CASE WHEN status='RESOLVED' THEN 1 ELSE 0 END) as \"RESOLVED\" FROM exception_queue WHERE created_at >= :since GROUP BY TO_CHAR(created_at, 'Dy'), CAST(created_at AS DATE) ORDER BY CAST(created_at AS DATE) ASC", nativeQuery = true)
    List<Map<String, Object>> getExceptionTimeSeries(@org.springframework.data.repository.query.Param("since") java.time.ZonedDateTime since);

    List<ExceptionQueue> findTop5ByOrderByCreatedAtDesc();
    
    List<ExceptionQueue> findAllByOrderByCreatedAtDesc();
    
    List<ExceptionQueue> findByAssignedToOrderByCreatedAtDesc(UUID assignedTo);
}
