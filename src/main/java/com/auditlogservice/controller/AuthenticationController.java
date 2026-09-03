package com.auditlogservice.controller;

import com.auditlogservice.config.JwtProperties;
import com.auditlogservice.dto.AuthTokenRequest;
import com.auditlogservice.dto.AuthTokenResponse;
import com.auditlogservice.security.JwtService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "JWT bearer-token authentication")
public class AuthenticationController {
    private final JwtProperties properties;
    private final JwtService jwtService;

    public AuthenticationController(JwtProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @PostMapping("/token")
    @Operation(summary = "Issue a JWT access token", description = "Validates configured credentials and returns a short-lived bearer token.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "JWT issued"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")})
    public ResponseEntity<AuthTokenResponse> token(@Valid @RequestBody AuthTokenRequest request) {
        if (!matches(request.username(), properties.getUsername()) || !matches(request.password(), properties.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new AuthTokenResponse(jwtService.issueToken(request.username()), "Bearer",
                jwtService.tokenValiditySeconds()));
    }

    private boolean matches(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
