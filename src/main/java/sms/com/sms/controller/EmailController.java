package sms.com.sms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import sms.com.sms.service.EmailService;


@RestController
@RequestMapping("/gas-detectors")
@CrossOrigin("*")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class EmailController {
    @Autowired
private EmailService emailService;

@PostMapping("/sendemail/{email},")
public void registerUser(String email, String name) {
    // After saving user
    emailService.sendEmail(email, "Welcome " + name, "Thanks for registering with us!");
}

    
}
