package sms.com.sms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Repository;

import sms.com.sms.model.GasDetector;
import sms.com.sms.model.Users;

@EnableJpaRepositories
public interface GasDetectorRepository extends JpaRepository<GasDetector, String> {
//List<GasDetector> findByUsers(Users user);  
 // Optional<Users> findByPhonenumber(String phoneNumber);

GasDetector findByMacAddress(String macAddress);// Ensure the field name matches `users`
}