package com.leonardtrinh.supportsaas.auth;

public interface AuthService {

    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);

    void logout(String refreshToken);

    MeResponse me(JwtClaims claims);
}
