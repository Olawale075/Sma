package sms.com.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sms.com.sms.model.DetectorReading;

import java.util.List;
import java.util.Optional;

public interface DetectorReadingRepository extends JpaRepository<DetectorReading, Long> {
    List<DetectorReading> findByDetectorMacAddressOrderByReceivedAtDesc(String macAddress);
    Optional<DetectorReading> findTopByDetectorMacAddressOrderByReceivedAtDesc(String macAddress);
}
