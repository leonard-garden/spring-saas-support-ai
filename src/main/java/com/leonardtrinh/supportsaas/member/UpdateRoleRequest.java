package com.leonardtrinh.supportsaas.member;

import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull Role role
) {}
