package sms.com.sms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private ResendEmailService resendEmailService; // Inject the new ResendEmailService

    /**
     * Sends a generic email using the Resend API.
     * This method assumes the body is HTML content.
     * @param toEmail The recipient's email address.
     * @param subject The subject of the email.
     * @param htmlBody The HTML content of the email.
     * @param fromEmail The sender's email address. If null, ResendEmailService will use its default.
     * @return true if the email was sent successfully, false otherwise.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlBody, String fromEmail) {
        logger.info("Attempting to send email via Resend to {} from {} with subject: {}", toEmail, fromEmail, subject);
        return resendEmailService.sendEmail(toEmail, subject, htmlBody, fromEmail);
    }


    public boolean sendEmail(String toEmail, String subject, String htmlBody) {
        return sendEmail(toEmail, subject, htmlBody, null); // Call the 4-arg method with null fromEmail
    }


    public boolean sendOtpEmail(String toEmail, String otp) {
        String subject = "Your OTP Code";
        // Assuming OTP emails are simple text, we can wrap it in basic HTML
        String htmlBody = "<p>Your OTP is: <strong>" + otp + "</strong></p>";
        logger.info("Attempting to send OTP email via Resend to {}", toEmail);
        return sendEmail(toEmail, subject, htmlBody, null); // Use the 4-arg method with null fromEmail
    }
}
