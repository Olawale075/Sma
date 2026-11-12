package sms.com.sms.service;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import sms.com.sms.repository.UsersRepository;

@Service
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
  @Autowired
    private final ResendEmailService resendEmailService;
    @Autowired
    private EmailService emailService;
 @Autowired
 private UsersRepository repository;

    public EmailVerificationService(ResendEmailService resendEmailService) {
        this.resendEmailService = resendEmailService;
    }

    public ResponseEntity<?> sendOtpToEmail(String email) {
        // Check if email is already in use
        if (repository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body("Email has been used   ok  ");
        }

        // Generate OTP
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));

        if (!sendOtpEmail(email, otp)) {
            return ResponseEntity.internalServerError().body("Failed to send email verification code");
        }
        otpStore.put(email, new OtpEntry(otp, LocalDateTime.now().plusMinutes(5)));

        return ResponseEntity.ok("Verification code sent");
    }
    public boolean sendOtpEmail(String toEmail, String otp) {
        String subject = "Your OTP Code";
        String htmlBody = "<p>Your OTP is: <strong>" + otp + "</strong></p>";
        return sendEmail(toEmail, subject, htmlBody, null); // Use the 4-arg method with null fromEmail
    }
    public boolean verifyOtp(String email, String inputOtp) {
        OtpEntry entry = otpStore.get(email);
        if (entry != null && entry.getCode().equals(inputOtp) && LocalDateTime.now().isBefore(entry.getExpiry())) {
            return otpStore.remove(email, entry);
        }
        return false;
    }
    public boolean sendEmail(String toEmail, String subject, String htmlBody, String fromEmail) {
     //   logger.info("Attempting to send email via Resend to {} from {} with subject: {}", toEmail, fromEmail, subject);
        return resendEmailService.sendEmail(toEmail, subject, htmlBody, fromEmail);
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
