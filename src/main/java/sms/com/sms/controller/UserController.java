package sms.com.sms.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import sms.com.sms.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import sms.com.sms.config.JwtUtil;
import sms.com.sms.enums.UserRole;
//  import sms.com.sms.dto.UserDTO;
import sms.com.sms.model.Users;
import sms.com.sms.service.EmailVerificationService;
import sms.com.sms.service.OTPService;
import sms.com.sms.service.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin("*")
@RequestMapping("/user")
public class UserController {

    private final UserServiceImpl service;
    private final OTPService otpService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
private  final EmailVerificationService emailVerificationService;
    public UserController(
            UserServiceImpl service,
            OTPService otpService,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,EmailVerificationService emailVerificationService) {
        this.service = service;
        this.otpService = otpService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.emailVerificationService= emailVerificationService;

    }

    private final AtomicInteger validatedUsersCount = new AtomicInteger(0);
    private static final int MAX_VALIDATED_USERS = 20;



    @Operation(summary = "Get all the details of the Users")
    @GetMapping("/admin/")
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createDateTime,DESC") String[] sort) {

        Sort sortOrder = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        return ResponseEntity.ok(service.getAllUsers(pageable));
    }

    @Operation(summary = "Sending OTP vai mail to the New user ")
    @PostMapping("/sendOtpToEmail/{email}")
    public ResponseEntity<?> sendOtpToEmail(@PathVariable String email) {
       
        try {
    return emailVerificationService.sendOtpToEmail(email);
} catch (Exception e) {
  System.out.println("Error sending email: " + e.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send email"+ e.getMessage());
}


    }

    @PostMapping("forgotPassword/{email}")
    public ResponseEntity<?> forgotPassword(@PathVariable String email) {

        
        try {
return service.forgotPassword(email);
} catch (Exception e) {
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send email");
}

    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {

     
        try {
     return service.resetPassword(token, newPassword);
} catch (Exception e) {
  
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send email"+e);
}

    }

    @Operation(summary = "Get all the details of the Users")
    @GetMapping("/admin/details")

    public ResponseEntity<Users> getUsers() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof Users) {
            return ResponseEntity.ok((Users) principal);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @Operation(summary = "Log in with PhoneNumber and PassWord")
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        String requestNumber= request.getPhoneNumber();
        try {

             if (!request.getPhoneNumber().startsWith("234")) {
  requestNumber = "234" + request.getPhoneNumber().replaceFirst("^0", "");
             }
            System.out.println("🔹 Attempting login for: " + requestNumber );

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requestNumber , request.getPassword()));

            System.out.println("Authentication successful!");

            UserDetails userDetails = service.loadUserByUsername(requestNumber);
            System.out.println(" Loaded User: " + userDetails.getUsername());

            // Get role from authorities (like "ROLE_ADMIN")
            String roleStr = userDetails.getAuthorities().iterator().next().getAuthority();
            System.out.println(" Extracted Role: " + roleStr);

            UserRole role = UserRole.valueOf(roleStr); //

            String token = jwtUtil.generateToken(userDetails.getUsername(), role);
                    return ResponseEntity.ok(new AuthResponse(token));
         
        } catch (BadCredentialsException e) {
            System.out.println("Invalid Credentials");
            return ResponseEntity.status(401).body("Invalid credentials: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(403).body("Forbidden: " + e.getMessage());
        }
    }

    @Operation(summary = "Checking the Token")
    @GetMapping("/test")
    public ResponseEntity<String> testToken(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            System.out.println("🔹 Received Token: " + token);
            return ResponseEntity.ok("Token received successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(403).body("Forbidden" + e.getMessage());
        }

    }

  

    @Operation(summary = "CreateUser")
    @PostMapping("CreateUser")
    public ResponseEntity<String> createUser(@RequestBody RegisterRequest user) {

        return service.verifyOtpAndCreateUser(user);
    }

@GetMapping("/user-gas-details")
public ResponseEntity<?> getUserGasDetails(@RequestParam String phoneNumber, @RequestParam String macAddress) {
    
    Optional<UserGasDetectorDTO> result = service.getUserAndGasDetector(phoneNumber, macAddress);
    return result
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
    @Operation(summary = "Get the User with the Phone Number")
    @GetMapping("/{phoneNumber}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String phoneNumber) {
        Optional<UserDTO> user = service.getUserByPhone(phoneNumber);
        return user.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update the user using the PhoneNumber ")

    @PutMapping("/user/{phoneNumber}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable String phoneNumber,
                                              @RequestBody UserDTO dto) {
        Optional<UserDTO> updated = service.updateUser(phoneNumber, dto);
        return updated.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a user by phone number.
     */
    @Operation(summary = "Delete a user by phone number")
    @DeleteMapping("/admin/delete/{phoneNumber}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String deleteUser(@PathVariable String phoneNumber) {

        return service.deleteUser(phoneNumber);
    }

    @Operation(summary = "Sending OTP to the New user ")
    @PostMapping("sendOtp/{to}")
    public String sendOtp(@PathVariable String to) {
        return service.sendOtp(to);
    }

    // @GetMapping("/{phone}")
    // public Users getUserWithDetectors(@PathVariable String phone) {
    // Users user = usersRepository.findById(phone)
    // .orElseThrow(() -> new RuntimeException("User not found"));
    // return user;
    // }
}
