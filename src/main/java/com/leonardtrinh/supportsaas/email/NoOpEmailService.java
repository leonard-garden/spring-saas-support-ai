package com.leonardtrinh.supportsaas.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@ConditionalOnMissingBean(SpringMailEmailService.class)
public class NoOpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.warn("Mail not configured. Password reset email NOT sent to {}", toEmail);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationToken) {
        log.warn("Mail not configured. Verification email NOT sent to {}", toEmail);
    }
}
