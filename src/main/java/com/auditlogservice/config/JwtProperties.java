package com.auditlogservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class JwtProperties {
    private String jwtSecret;
    private String username;
    private String password;
    private long tokenValiditySeconds;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public long getTokenValiditySeconds() { return tokenValiditySeconds; }
    public void setTokenValiditySeconds(long tokenValiditySeconds) { this.tokenValiditySeconds = tokenValiditySeconds; }
}
