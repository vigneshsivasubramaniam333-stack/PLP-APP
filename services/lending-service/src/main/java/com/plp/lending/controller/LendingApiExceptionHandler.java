package com.plp.lending.controller;

import com.plp.lending.exception.LendingBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.plp.lending.controller")
public class LendingApiExceptionHandler {

    @ExceptionHandler(LendingBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleLendingBusiness(LendingBusinessException ex) {
        return errorBody(ex.getStatusCode().value(), ex.getReason());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String msg = ex.getReason();
        String detail = msg != null ? msg : HttpStatus.resolve(code) != null ? HttpStatus.resolve(code).getReasonPhrase() : "Error";
        return errorBody(code, detail);
    }

    private static ResponseEntity<Map<String, Object>> errorBody(int httpStatus, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", message != null ? message : "Request failed");
        return ResponseEntity.status(httpStatus).body(body);
    }
}
