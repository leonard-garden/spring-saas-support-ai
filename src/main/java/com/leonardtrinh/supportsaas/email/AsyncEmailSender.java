package com.leonardtrinh.supportsaas.email;

public interface AsyncEmailSender {

    void sendInvitationEmail(String email, String rawToken);

    void sendPasswordResetAsync(String email, String token);

    void sendEmailVerificationAsync(String email, String token);
}
