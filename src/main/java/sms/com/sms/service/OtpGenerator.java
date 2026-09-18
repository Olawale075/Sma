package sms.com.sms.service;

    import java.security.SecureRandom;
    import java.util.HashMap;
    import java.util.Map;

    
    public class OtpGenerator {
    
        // Constants for OTP expiration time (60 seconds)
        private static final int OTP_EXPIRATION_TIME = 3600; // in 1hour
        private static final String DIGITS = "0123456789";
    
        // In-memory storage for OTPs and their expiration times per user
        private static final Map<String, OtpRecord> otpStore = new HashMap<>();
    
        // Generates OTP for the given user
        public static String generateOtp(String userId) {
            // Generate a random OTP
            SecureRandom random = new SecureRandom();
            StringBuilder otp = new StringBuilder();
            for (int i = 0; i < 6; i++) { // 6-digit OTP
                otp.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
            }
    
            // Store the OTP with the expiration time
            long expirationTime = System.currentTimeMillis() + (OTP_EXPIRATION_TIME * 1000); // current time + 60 seconds
            otpStore.put(userId, new OtpRecord(otp.toString(), expirationTime));
    
            return otp.toString();
        }
    
        // Validate OTP for the given user
        public static boolean validateOtp(String userId, String otp) {
            OtpRecord otpRecord = otpStore.get(userId);

    
            if (otpRecord == null) {
                return false; // OTP does not exist
            }
    
            // Check if OTP has expired
            if (System.currentTimeMillis() > otpRecord.getExpirationTime()) {
                otpStore.remove(userId); // Remove expired OTP
                return false; // OTP has expired
            }
    
            // Check if the OTP is correct
            if (otp.equals(otpRecord.getOtp())) {
                otpStore.remove(userId); // OTP is valid, remove it from the store
                return true;
            }
    
            return false; // OTP is incorrect
        }
    
        // OTP record class to hold OTP and its expiration time
        private static class OtpRecord {
            private final String otp;
            private final long expirationTime;
    
            public OtpRecord(String otp, long expirationTime) {
                this.otp = otp;
                this.expirationTime = expirationTime;
            }
    
            public String getOtp() {
                return otp;
            }
    
            public long getExpirationTime() {
                return expirationTime;
            }
        }

        public static String generateOtps(String userId) {
            SecureRandom random = new SecureRandom();
            StringBuilder otp = new StringBuilder();
            for (int i = 0; i < 6; i++) { // 6-digit OTP
                otp.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
            }
    
            // Store the OTP with the expiration time
            long expirationTime = System.currentTimeMillis() + (OTP_EXPIRATION_TIME * 1000); // current time + 60 seconds
            otpStore.put(userId, new OtpRecord(otp.toString(), expirationTime));
    
            return otp.toString();
        }
    }
    
       

