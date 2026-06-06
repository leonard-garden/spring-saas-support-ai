package com.leonardtrinh.supportsaas.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnMissingBean(SpringMailEmailService.class)
public class NoOpEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.warn("Mail not configured. Password reset email NOT sent to {}", toEmail);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationToken) {
        log.warn("Mail not configured. Verification email NOT sent to {}", toEmail);
    }

    @Override
    public void sendInvitationEmail(String toEmail, String rawToken) {
        log.warn("Mail not configured. Invitation email NOT sent to {}", toEmail);
    }

    @Override
    public void sendTrialExpiredEmail(String toEmail) {
        log.warn("Mail not configured. Trial expired email NOT sent to {}", toEmail);
    }

    @Override
    public void sendPaymentFailedEmail(String toEmail) {
        log.warn("Mail not configured. Payment failed email NOT sent to {}", toEmail);
    }
}
