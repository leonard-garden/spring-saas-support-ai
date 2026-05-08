package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.email.EmailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailSender {

    private final EmailService emailService;

    public EmailSender(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("taskExecutor")
    public void sendPasswordResetAsync(String email, String token) {
        emailService.sendPasswordResetEmail(email, token);
    }

    @Async("taskExecutor")
    public void sendEmailVerificationAsync(String email, String token) {
        emailService.sendEmailVerificationEmail(email, token);
    }
}
