package sms.com.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import sms.com.sms.model.CropDeceaseDetector;

@EnableJpaRepositories
public interface GasDetectorRepository extends JpaRepository<CropDeceaseDetector, String> {
//List<GasDetector> findByUsers(Users user);  
 // Optional<Users> findByPhonenumber(String phoneNumber);

CropDeceaseDetector findByMacAddress(String macAddress);// Ensure the field name matches `users`
}