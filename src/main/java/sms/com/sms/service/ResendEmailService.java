package sms.com.sms.service;

import com.resend.Resend;

import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResendEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);

    private final Resend resend;
    private final String defaultFromEmail;

    public ResendEmailService(@Value("${resend.api.key:}") String resendApiKey,
                              @Value("${resend.email.from:olawale@medhandy.net}") String defaultFromEmail) {
        if (resendApiKey == null || resendApiKey.isEmpty() || resendApiKey.contains("${")) {
            logger.error("CRITICAL ERROR: Resend API Key is not set! Check your Environment Variables for RESEND_API_KEY.");
        }
        this.resend = new Resend(resendApiKey);
        this.defaultFromEmail = defaultFromEmail;
    }

    /**
     * Sends an email using the Resend API with a specified 'from' address.
     *
     * @param toEmail The recipient's email address.
     * @param subject The subject of the email.
     * @param htmlBody The HTML content of the email.
     * @param fromEmail The sender's email address. If null or empty, a default will be used.
     * @return true if the email was sent successfully, false otherwise.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlBody, String fromEmail) {
        try {
            String sender = (fromEmail != null && !fromEmail.isEmpty()) ? fromEmail : defaultFromEmail;

            CreateEmailOptions sendEmailRequest = CreateEmailOptions.builder()
                    .from(sender)
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlBody)
                    .build();

            CreateEmailResponse response = this.resend.emails().send(sendEmailRequest);

            if (response != null && response.getId() != null) {
                logger.info("Email sent successfully via Resend. Email ID: {}", response.getId());
                return true;
            } else {
                logger.error("Resend API failed for {}. Check if the sender {} is verified.", toEmail, sender);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error sending email via Resend to {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends an email using the Resend API with the default 'from' address.
     * This method delegates to the 4-argument sendEmail method, passing null for 'fromEmail'.
     *
     * @param toEmail The recipient's email address.
     * @param subject The subject of the email.
     * @param htmlBody The HTML content of the email.
     * @return true if the email was sent successfully, false otherwise.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlBody ) {
        return sendEmail(toEmail, subject, htmlBody, null); // Corrected: Calls the 4-argument method with null for fromEmail
    }
}
