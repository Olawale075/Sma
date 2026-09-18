package sms.com.sms.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OTPService {

    private static final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private static final int EXPIRE_MINUTES = 5;

    public String generateOtp(String phoneNumber) {
        String otp = String.valueOf((int)(Math.random() * 9000) + 1000); // 4-digit
        otpCache.put(phoneNumber, new OtpEntry(otp, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
        return otp;
    }

    public boolean verifyOtp(String phoneNumber, String inputOtp) {
        OtpEntry entry = otpCache.get(phoneNumber);
        if (entry == null || LocalDateTime.now().isAfter(entry.expiry)) return false;
        return entry.otp.equals(inputOtp);
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
