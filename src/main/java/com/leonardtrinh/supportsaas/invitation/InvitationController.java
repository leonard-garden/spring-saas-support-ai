package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/v1/members/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<InvitationResponse> invite(@Valid @RequestBody InviteRequest request) {
        JwtClaims caller = (JwtClaims) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        InvitationResponse response = invitationService.invite(request, caller);
        return ApiResponse.ok(response);
    }

    @PostMapping("/api/v1/invitations/accept")
    public ApiResponse<AuthResponse> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        AuthResponse response = invitationService.accept(request);
        return ApiResponse.ok(response);
    }
}
