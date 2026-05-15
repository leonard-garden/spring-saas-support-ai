package com.leonardtrinh.supportsaas.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncEmailSenderImpl implements AsyncEmailSender {

    private static final Logger log = LoggerFactory.getLogger(AsyncEmailSenderImpl.class);

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
