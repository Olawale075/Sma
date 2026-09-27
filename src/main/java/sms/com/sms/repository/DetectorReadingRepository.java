package sms.com.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sms.com.sms.model.DetectorReading;

public interface DetectorReadingRepository extends JpaRepository<DetectorReading, Long> {
}
