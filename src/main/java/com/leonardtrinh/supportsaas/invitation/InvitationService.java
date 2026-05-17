package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InvitationService {

    InvitationResponse invite(InviteRequest request, JwtClaims caller);

    AuthResponse accept(AcceptInvitationRequest request);

    Page<InvitationResponse> listPending(Pageable pageable, JwtClaims caller);

    InvitationResponse resend(UUID id, JwtClaims caller);

    void revoke(UUID id, JwtClaims caller);
}
