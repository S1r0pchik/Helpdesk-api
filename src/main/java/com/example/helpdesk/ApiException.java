package com.example.helpdesk;

import org.springframework.http.HttpStatus;

import java.util.Map;

class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> details;

    ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    ApiException(HttpStatus status, String code, String message, Map<String, String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    static ApiException validation(Map<String, String> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    Map<String, String> details() {
        return details;
    }
}
