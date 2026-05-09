package com.leonardtrinh.supportsaas.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @Schema(description = "New role to assign — ADMIN or AGENT (cannot assign OWNER)")
    @NotNull Role role
) {}
