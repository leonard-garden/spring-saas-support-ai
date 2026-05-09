package com.leonardtrinh.supportsaas.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoOpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.debug("NoOp: sendPasswordResetEmail to={}", toEmail);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationToken) {
        log.debug("NoOp: sendEmailVerificationEmail to={}", toEmail);
    }
}
