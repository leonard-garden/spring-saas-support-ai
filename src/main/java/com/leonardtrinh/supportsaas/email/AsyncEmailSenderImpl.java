package com.leonardtrinh.supportsaas.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncEmailSenderImpl implements AsyncEmailSender {

    private final EmailService emailService;

    public AsyncEmailSenderImpl(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    @Async("taskExecutor")
    public void sendInvitationEmail(String email, String rawToken) {
        emailService.sendInvitationEmail(email, rawToken);
    }

    @Override
    @Async("taskExecutor")
    public void sendPasswordResetAsync(String email, String token) {
        emailService.sendPasswordResetEmail(email, token);
    }

    @Override
    @Async("taskExecutor")
    public void sendEmailVerificationAsync(String email, String token) {
        emailService.sendEmailVerificationEmail(email, token);
    }
}
