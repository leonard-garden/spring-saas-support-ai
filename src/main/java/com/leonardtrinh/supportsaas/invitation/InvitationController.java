package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitations", description = "Invite members and accept invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept a member invitation and set password")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation accepted, JWT tokens returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired invitation token")
    })
    public ApiResponse<AuthResponse> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        AuthResponse response = invitationService.accept(request);
        return ApiResponse.ok(response);
    }

    @GetMapping
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "List pending invitations for the caller's tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pending invitations returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public ApiResponse<PagedResponse<InvitationResponse>> listPending(
            @PageableDefault(size = 10) Pageable pageable) {
        JwtClaims caller = caller();
        return ApiResponse.ok(PagedResponse.from(invitationService.listPending(pageable, caller)));
    }

    @PostMapping("/invite")
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
        JwtClaims caller = caller();
        InvitationResponse response = invitationService.invite(request, caller);
        return ApiResponse.ok(response);
    }

    @PostMapping("/{id}/resend")
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Resend a pending invitation email and reset expiry to 72 hours")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation resent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found or already accepted")
    })
    public ApiResponse<InvitationResponse> resend(@PathVariable UUID id) {
        return ApiResponse.ok(invitationService.resend(id, caller()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "Bearer")
    @Operation(summary = "Revoke a pending invitation")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Invitation revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found or already accepted")
    })
    public void revoke(@PathVariable UUID id) {
        invitationService.revoke(id, caller());
    }

    private JwtClaims caller() {
        return (JwtClaims) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
