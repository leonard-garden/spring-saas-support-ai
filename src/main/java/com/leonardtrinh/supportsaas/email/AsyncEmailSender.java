package com.leonardtrinh.supportsaas.email;

public interface AsyncEmailSender {

    void sendInvitationEmail(String email, String rawToken);
}
