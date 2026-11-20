package sms.com.sms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sms.com.sms.model.DetectionHistory;

import java.time.Instant;
import java.util.List;

public interface DetectionHistoryRepository extends JpaRepository<DetectionHistory, Long> {

    @Query("""
            SELECT history
            FROM DetectionHistory history
            WHERE history.userPhone = :userPhone
              AND (:filterFrom = false OR history.createdAt >= :from)
              AND (:filterTo = false OR history.createdAt <= :to)
              AND (:focus = '' OR LOWER(history.focus) = LOWER(:focus))
              AND (:className = '' OR LOWER(history.className) = LOWER(:className))
              AND (:severity = '' OR UPPER(history.severity) = UPPER(:severity))
            """)
    Page<DetectionHistory> findForReport(
            @Param("userPhone") String userPhone,
            @Param("filterFrom") boolean filterFrom,
            @Param("from") Instant from,
            @Param("filterTo") boolean filterTo,
            @Param("to") Instant to,
            @Param("focus") String focus,
            @Param("className") String className,
            @Param("severity") String severity,
            Pageable pageable);

    @Query("""
            SELECT history.className AS label, COUNT(history) AS count
            FROM DetectionHistory history
            WHERE history.userPhone = :userPhone
              AND (:filterFrom = false OR history.createdAt >= :from)
              AND (:filterTo = false OR history.createdAt <= :to)
              AND (:focus = '' OR LOWER(history.focus) = LOWER(:focus))
              AND (:className = '' OR LOWER(history.className) = LOWER(:className))
              AND (:severity = '' OR UPPER(history.severity) = UPPER(:severity))
            GROUP BY history.className
            ORDER BY COUNT(history) DESC
            """)
    List<ReportCount> countByClassForReport(
            @Param("userPhone") String userPhone,
            @Param("filterFrom") boolean filterFrom,
            @Param("from") Instant from,
            @Param("filterTo") boolean filterTo,
            @Param("to") Instant to,
            @Param("focus") String focus,
            @Param("className") String className,
            @Param("severity") String severity);

    @Query("""
            SELECT history.severity AS label, COUNT(history) AS count
            FROM DetectionHistory history
            WHERE history.userPhone = :userPhone
              AND (:filterFrom = false OR history.createdAt >= :from)
              AND (:filterTo = false OR history.createdAt <= :to)
              AND (:focus = '' OR LOWER(history.focus) = LOWER(:focus))
              AND (:className = '' OR LOWER(history.className) = LOWER(:className))
              AND (:severity = '' OR UPPER(history.severity) = UPPER(:severity))
            GROUP BY history.severity
            ORDER BY COUNT(history) DESC
            """)
    List<ReportCount> countBySeverityForReport(
            @Param("userPhone") String userPhone,
            @Param("filterFrom") boolean filterFrom,
            @Param("from") Instant from,
            @Param("filterTo") boolean filterTo,
            @Param("to") Instant to,
            @Param("focus") String focus,
            @Param("className") String className,
            @Param("severity") String severity);

    interface ReportCount {
        String getLabel();
        long getCount();
    }
}
