package com.example.helpdesk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
class ApiErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApiError(ApiException error) {
        return ResponseEntity.status(error.status()).body(body(error));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors()
                .forEach(field -> details.put(field.getField(), field.getDefaultMessage()));
        return new ResponseEntity<>(body(ApiException.validation(details)), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(new ErrorBody("INVALID_REQUEST", "Expected a valid JSON object", Map.of()),
                headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(new ErrorBody("UNSUPPORTED_MEDIA_TYPE", "Use application/json", Map.of()),
                headers, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception error) {
        LOG.error("Unexpected API error", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("INTERNAL_ERROR", "An unexpected error occurred", Map.of()));
    }

    private static ErrorBody body(ApiException error) {
        return new ErrorBody(error.code(), error.getMessage(), error.details());
    }

    record ErrorBody(String code, String message, Map<String, String> details) {
    }
}
