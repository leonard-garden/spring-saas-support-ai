package com.leonardtrinh.supportsaas.email;

public interface AsyncEmailSender {
    void sendPasswordResetAsync(String email, String token);
    void sendEmailVerificationAsync(String email, String token);
}
