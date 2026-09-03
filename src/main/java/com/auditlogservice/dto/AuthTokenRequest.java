package com.auditlogservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthTokenRequest(@NotBlank String username, @NotBlank String password) {}
