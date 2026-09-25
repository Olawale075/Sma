package sms.com.sms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sms.com.sms.dto.DetectorDTO;
import sms.com.sms.dto.RegisterRequest;
import sms.com.sms.dto.UserDTO;
import sms.com.sms.dto.UserGasDetectorDTO;
import sms.com.sms.enums.UserRole;
import sms.com.sms.model.GasDetector;
import sms.com.sms.model.Users;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.repository.UsersRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UsersRepository repository;
    private final GasDetectorRepository gasDetectorRepository;

    private final PasswordEncoder passwordEncoder;
    private final OTPService otpService;
    private final EmailVerificationService emailVerificationService;

    private final SmsService smsService;
    private final MailService emailService;


    private final Map<String, Users> tempUserStorage = new HashMap<>();

    @Transactional(readOnly = true)
    public Optional<UserGasDetectorDTO> getUserAndGasDetector(String phoneNumber, String macAddress) {

        if (phoneNumber == null || macAddress == null) {
            return Optional.empty();
        }

        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            return Optional.empty();
        }

        Optional<Users> optionalUser = repository.findByPhonenumber(normalizedPhone);
        GasDetector detector = gasDetectorRepository.findByMacAddress(macAddress);

        if (optionalUser.isEmpty() || detector == null) {
            return Optional.empty();
        }

        Users user = optionalUser.get();

        // Safer ownership check (see note below)
        boolean ownsDetector = user.getGasDetectors() != null &&
                user.getGasDetectors().stream()
                        .anyMatch(d -> Objects.equals(d.getMacAddress(), detector.getMacAddress()));

        if (!ownsDetector) {
            return Optional.empty();
        }

        // Build detector DTO
        DetectorDTO detectorDTO = new DetectorDTO();
        detectorDTO. setMacAddress(detector.getMacAddress());
//        detectorDTO.setHumidity(detector.getHumidity());
//        detectorDTO.setTemperature(detector.getTemperature());


        // set other DetectorDTO fields here

        // Build user DTO
        UserDTO userDTO = UserDTO.builder()
                .phoneNumbers(user.getPhonenumber())
                .name(user.getName())
                .email(user.getEmail())
                .crop(user.getCrop())
                .farmName(user.getFarmName())
                .farmLocation(user.getFarmLocation())
                .farmSize(user.getFarmSize())
                // .notificationPreference(user.getNotificationPreference())
                .build();

        // Build combined DTO
        UserGasDetectorDTO result = new UserGasDetectorDTO();
        result.setUser(userDTO);          // only if this setter exists
        result.setGasDetector(detectorDTO);

        return Optional.of(result);
    }

    public boolean isPhonenumberRegistered(String phoneNumber)
    {
        return repository.existsById(phoneNumber);
    }
    public boolean isEmailRegistered(String email)
    {
        return repository.findByEmail(email).isPresent();
    }

    public void saveTempUser(Users user) {
        tempUserStorage.put(user.getPhonenumber(), user);
    }

    public Users findTempUser(String phoneNumber) {
        return tempUserStorage.get(phoneNumber);
    }

    public String sendOtp(String phoneNumber) {

        if (phoneNumber == null) {
            throw new IllegalArgumentException("Phone number is required");
        }

        if (!phoneNumber.startsWith("234")) {
            phoneNumber = "234" + phoneNumber.replaceFirst("^0", "");
        }

        if (isPhonenumberRegistered(phoneNumber)) {
            throw new IllegalArgumentException("Phone number already used");
        }

        String otp = otpService.generateOtp(phoneNumber);

        String message = "Dear User, your verification PIN is " + otp +
                ". Valid for 5 minutes. One-time use only.";

        return smsService.sendSms(phoneNumber, message);
    }
    @Transactional
    public ResponseEntity<String> verifyOtpAndCreateUser(RegisterRequest request) {

        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Full name is required");
        }

        try {
            String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
            if (phoneNumber == null) {
                return ResponseEntity.badRequest().body("Invalid phone number format");
            }

            if (isPhonenumberRegistered(phoneNumber)) {
                return ResponseEntity.badRequest().body("Phone number already used");
            }
            if (isEmailRegistered(request.getEmail())) {
                return ResponseEntity.badRequest().body("Email already used");
            }
            // Uncomment this when ready
            // if (!otpService.verifyOtp(phoneNumber, request.getOtp())) {
            //     return ResponseEntity.badRequest().body("Invalid OTP");
            // }

            Users user = Users.builder()
                    .phonenumber(phoneNumber)
                    .name(request.getFullName()) // This should not be null!
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(UserRole.ROLE_USER)
                    .farmName(request.getFarmName())
                    .crop(request.getCrop())
                    .farmSize(request.getFarmSize())
                    .farmLocation(request.getFarmLocation())
                    .isVerified(false)
                    .emailVerified(request.getEmail() != null ? false : true)
                    .build();

            repository.save(user);
            return ResponseEntity.ok("User registered successfully");

        } catch (DataIntegrityViolationException e) {
            // Handle specific database constraints
            return ResponseEntity.badRequest().body("Data integrity violation: " + e.getMessage());
        } catch (Exception e) {
            // Log the error and rollback
          //  log.error("Registration failed for phone: {}", request.getPhonenumber(), e);
            // Re-throw to trigger rollback
            throw new RuntimeException("Registration failed", e);
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        String cleaned = phoneNumber.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("234") && cleaned.length() == 13) return cleaned;
        if (cleaned.startsWith("0") && cleaned.length() == 11) {
            return "234" + cleaned.substring(1);
        }
        if (cleaned.length() == 10) return "234" + cleaned;
        return null;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        Users user = repository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String role = user.getRole().name().startsWith("ROLE_")
                ? user.getRole().name()
                : "ROLE_" + user.getRole().name();

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getPhonenumber())
                .password(user.getPassword())
                .authorities(new SimpleGrantedAuthority(role))
                .build();
    }


    public ResponseEntity<?> forgotPassword(String email) {

        Optional<Users> user = repository.findByEmail(email);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().body("Email not found");
        }

        Users existingUser = user.get();
        String token = UUID.randomUUID().toString();
        existingUser.setResetToken(token);
        repository.save(existingUser);

        String resetLink = "http://localhost:8080/reset-password?token=" + token;
        emailService.sendResetPasswordEmail(existingUser.getEmail(), resetLink);

        return ResponseEntity.ok("Password reset link sent");
    }

    public ResponseEntity<?> resetPassword(String token, String newPassword) {

        Optional<Users> user = repository.findByResetToken(token);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid token");
        }

        Users existingUser = user.get();
        existingUser.setPassword(passwordEncoder.encode(newPassword));
        existingUser.setResetToken(null);
        repository.save(existingUser);

        return ResponseEntity.ok("Password successfully reset");
    }


    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return repository.findAll(pageable).map(user -> UserDTO.builder()
                        .phoneNumbers(user.getPhonenumber())
                        .name(user.getName())
                        .email(user.getEmail())
                        .crop(user.getCrop())
                        .farmName(user.getFarmName())
                        .farmLocation(user.getFarmLocation())
                        .farmSize(user.getFarmSize())
                       .notificationPreference(user.getNotificationPreference())
                        .build());
    }

    public Optional<UserDTO> getUserByPhone(String phonenumber) {

        if (!phonenumber.startsWith("234")) {
            phonenumber = "234" + phonenumber.replaceFirst("^0", "");
        }

       Optional<Users> user =  repository.findByPhonenumber(phonenumber);
        UserDTO userDTO = UserDTO.builder()
                .phoneNumbers(user.get().getPhonenumber())
                .name(user.get().getName())
                .email(user.get().getEmail())
                .crop(user.get().getCrop())
                .farmName(user.get().getFarmName())
                .farmLocation(user.get().getFarmLocation())
                .farmSize(user.get().getFarmSize())
               // .notificationPreference(user.get().getNotificationPreference())
                .build();
        return Optional.ofNullable(userDTO);
    }

    public Optional<UserDTO> updateUser(String phoneNumber, UserDTO dto) {

        if (!phoneNumber.startsWith("234")) {
            phoneNumber = "234" + phoneNumber.replaceFirst("^0", "");
        }

        return repository.findById(phoneNumber).map(existing -> {

            existing.setName(dto.getName());
            existing.setEmail(dto.getEmail());
            existing.setCrop(dto.getCrop());
            existing.setFarmName(dto.getFarmName());
            existing.setFarmLocation(dto.getFarmLocation());
            existing.setFarmSize(dto.getFarmSize());

            if (dto.getNotificationPreference() != null) {
                existing.setNotificationPreference(dto.getNotificationPreference());
            }
repository.save(existing);
            return UserDTO.builder()
                    .phoneNumbers(existing.getPhonenumber())
                    .name(existing.getName())
                    .email(existing.getEmail())
                    .crop(existing.getCrop())
                    .farmName(existing.getFarmName())
                    .farmLocation(existing.getFarmLocation())
                    .farmSize(existing.getFarmSize())
                   .notificationPreference(existing.getNotificationPreference())
                    .build();
        });
    }

    public String deleteUser(String phoneNumber) {

        if (repository.existsById(phoneNumber)) {
            repository.deleteById(phoneNumber);
            return "Deleted successfully";
        }
        return "User not found";
    }
    
}
