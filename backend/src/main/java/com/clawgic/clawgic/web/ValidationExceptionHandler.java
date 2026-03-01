package com.clawgic.clawgic.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handle(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage())
        );

        String detail = fieldErrors.isEmpty()
                ? "Validation failed"
                : "Validation failed: " + String.join("; ", fieldErrors.values());

        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse(detail, fieldErrors));
    }

    public record ValidationErrorResponse(
            String detail,
            Map<String, String> fieldErrors
    ) {
    }
}
