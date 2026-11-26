package sms.com.sms.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String toEmail, String message) {

        
        String subject = "Verify Your Email Address";
        String content = "<p>Thank you for registering.</p>"
                       + "<p>Please copy your one time password and do not send it not anyone :</p>"
                       + "<p> "  + message+ "\" Verify Email</p>";

        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendResetPasswordEmail(String toEmail, String resetLink) {
        String subject = "Reset Your Password";
        String content = "<p>You requested to reset your password.</p>"
                       + "<p>Click the link below to reset it:</p>"
                       + "<p><a href=\"" + resetLink + "\">Reset Password</a></p>";

        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String content,
                                        String filename, byte[] attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            helper.addAttachment(filename, new ByteArrayResource(attachment), "text/csv");

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send email with attachment", e);
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // Enable HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
