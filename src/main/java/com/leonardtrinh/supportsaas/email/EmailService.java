package com.leonardtrinh.supportsaas.email;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetToken);
    void sendEmailVerificationEmail(String toEmail, String verificationToken);
    void sendInvitationEmail(String toEmail, String rawToken);
}
