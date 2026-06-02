package com.leonardtrinh.supportsaas.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@ConditionalOnProperty(name = "spring.mail.host")
public class SpringMailEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String appBaseUrl;
    private final String senderEmail;

    public SpringMailEmailService(
            JavaMailSender mailSender,
            @Value("${app.base-url:http://localhost:3000}") String appBaseUrl,
            @Value("${app.mail.sender:noreply@localhost}") String senderEmail) {
        this.mailSender = mailSender;
        this.appBaseUrl = appBaseUrl;
        this.senderEmail = senderEmail;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(toEmail);
        message.setSubject("Reset your password");
        message.setText("Click the link below to reset your password:\n\n"
                + appBaseUrl + "/reset-password?token=" + resetToken
                + "\n\nThis link expires in 1 hour.");
        mailSender.send(message);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(toEmail);
        message.setSubject("Verify your email address");
        message.setText("Click the link below to verify your email address:\n\n"
                + appBaseUrl + "/verify-email?token=" + verificationToken);
        mailSender.send(message);
    }

    @Override
    public void sendInvitationEmail(String toEmail, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(toEmail);
        message.setSubject("You've been invited");
        message.setText("You have been invited to join a workspace. Click the link below to accept:\n\n"
                + appBaseUrl + "/invitation/accept?token=" + rawToken
                + "\n\nThis link expires in 72 hours.");
        mailSender.send(message);
    }

    @Override
    public void sendTrialExpiredEmail(String toEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(toEmail);
        message.setSubject("Your free trial has ended");
        message.setText("Your 14-day Pro trial has ended. Your account has been moved to the Free plan.\n\n"
                + "Upgrade anytime at " + appBaseUrl + "/billing to restore full access.");
        mailSender.send(message);
    }

    @Override
    public void sendPaymentFailedEmail(String toEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(toEmail);
        message.setSubject("Payment failed — action required");
        message.setText("We were unable to process your latest payment. Your subscription is now past due.\n\n"
                + "Please update your payment method at " + appBaseUrl + "/billing"
                + " to avoid losing access.");
        mailSender.send(message);
    }
}
