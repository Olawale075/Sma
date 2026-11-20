package sms.com.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sms.com.sms.model.DetectorReading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DetectorReadingRepository extends JpaRepository<DetectorReading, Long> {
    List<DetectorReading> findByDetectorMacAddressOrderByReceivedAtDesc(String macAddress);
    Optional<DetectorReading> findTopByDetectorMacAddressOrderByReceivedAtDesc(String macAddress);

    @Query("""
            SELECT reading
            FROM DetectorReading reading
            LEFT JOIN FETCH reading.detector detector
            WHERE (:macAddress = '' OR LOWER(detector.macAddress) = LOWER(:macAddress))
              AND (:filterFrom = false OR reading.receivedAt >= :from)
              AND (:filterTo = false OR reading.receivedAt <= :to)
            ORDER BY reading.receivedAt DESC
            """)
    List<DetectorReading> findReadingsForReport(
            @Param("macAddress") String macAddress,
            @Param("filterFrom") boolean filterFrom,
            @Param("from") Instant from,
            @Param("filterTo") boolean filterTo,
            @Param("to") Instant to);
}
