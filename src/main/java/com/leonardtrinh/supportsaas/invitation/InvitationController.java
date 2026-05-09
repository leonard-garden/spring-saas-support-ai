package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Invitations", description = "Invite members and accept invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/v1/members/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Invite a new member to the tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Invitation sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Member already exists")
    })
    public ApiResponse<InvitationResponse> invite(@Valid @RequestBody InviteRequest request) {
        JwtClaims caller = (JwtClaims) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        InvitationResponse response = invitationService.invite(request, caller);
        return ApiResponse.ok(response);
    }

    @PostMapping("/api/v1/invitations/accept")
    @Operation(summary = "Accept a member invitation and set password")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation accepted, JWT tokens returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired invitation token")
    })
    public ApiResponse<AuthResponse> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        AuthResponse response = invitationService.accept(request);
        return ApiResponse.ok(response);
    }
}
