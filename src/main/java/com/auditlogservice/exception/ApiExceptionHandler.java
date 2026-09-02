package com.auditlogservice.exception;

import java.time.Instant;
import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuditLogNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(AuditLogNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalidArgument(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream().findFirst()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> malformedJson(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request body must contain valid JSON");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not allowed for this endpoint");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(), "status", status.value(),
                "message", message == null ? status.getReasonPhrase() : message));
    }
}
