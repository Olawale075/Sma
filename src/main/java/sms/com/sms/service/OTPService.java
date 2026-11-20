package sms.com.sms.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OTPService {

    private static final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private static final int EXPIRE_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateOtp(String phoneNumber) {
        String otp = String.valueOf(RANDOM.nextInt(9000) + 1000);
        otpCache.put(phoneNumber, new OtpEntry(otp, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
        return otp;
    }

    public boolean verifyOtp(String phoneNumber, String inputOtp) {
        OtpEntry entry = otpCache.get(phoneNumber);
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiry)) {
            otpCache.remove(phoneNumber, entry);
            return false;
        }
        return inputOtp != null && entry.otp.equals(inputOtp) && otpCache.remove(phoneNumber, entry);
    }

    private static class OtpEntry {
        String otp;
        LocalDateTime expiry;
        OtpEntry(String otp, LocalDateTime expiry) {
            this.otp = otp;
            this.expiry = expiry;
        }
    }
    
}
