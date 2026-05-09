package com.leonardtrinh.supportsaas.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String password
) {}
