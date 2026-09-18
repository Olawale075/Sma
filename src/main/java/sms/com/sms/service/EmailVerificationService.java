package sms.com.sms.service;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import sms.com.sms.repository.UsersRepository;

@Service
public class EmailVerificationService {

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

     @Autowired
    private MailService emailService;
 @Autowired
 private UsersRepository repository;
    public ResponseEntity<?> sendOtpToEmail(String email) {
        // Check if email is already in use
        if (repository.findByEmail(email) != null) {
            return ResponseEntity.badRequest().body("Email has been used");
        }

        // Generate OTP
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Store OTP temporarily
        otpStore.put(email, new OtpEntry(otp, LocalDateTime.now().plusMinutes(5)));

        // Send OTP via email
        String message = "Your email verification code is: " + otp;
        emailService.sendVerificationEmail(email, message);

        return ResponseEntity.ok("Verification code sent");
    }

    public boolean verifyOtp(String email, String inputOtp) {
        OtpEntry entry = otpStore.get(email);
        if (entry != null && entry.getCode().equals(inputOtp) && LocalDateTime.now().isBefore(entry.getExpiry())) {
            otpStore.remove(email); // Remove after successful verification
            return true;
        }
        return false;
    }

    // Helper class to hold OTP and expiration
    private static class OtpEntry {
        private final String code;
        private final LocalDateTime expiry;

        public OtpEntry(String code, LocalDateTime expiry) {
            this.code = code;
            this.expiry = expiry;
        }

        public String getCode() {
            return code;
        }

        public LocalDateTime getExpiry() {
            return expiry;
        }
    }
}
