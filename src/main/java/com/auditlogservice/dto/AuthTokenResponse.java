package com.auditlogservice.dto;

public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn) {}
