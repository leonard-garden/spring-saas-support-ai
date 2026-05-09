package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.JwtClaims;

public interface InvitationService {

    InvitationResponse invite(InviteRequest request, JwtClaims caller);

    AuthResponse accept(AcceptInvitationRequest request);
}
