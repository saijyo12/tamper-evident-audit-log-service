package com.auditlogservice.config;

import com.auditlogservice.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            @Value("${app.security.enabled:true}") boolean securityEnabled) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!securityEnabled) {
            return http
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                    .build();
        }

        return http
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bearer token is required or invalid")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**",
                                "/actuator/health/**", "/actuator/info", "/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
