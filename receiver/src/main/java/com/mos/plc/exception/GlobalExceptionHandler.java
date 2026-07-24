package com.mos.plc.exception;

import com.mos.plc.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NotFoundException exc) {
        return error(HttpStatus.NOT_FOUND, 404, exc.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(ConflictException exc) {
        return error(HttpStatus.CONFLICT, 409, exc.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IOException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception exc) {
        return error(HttpStatus.BAD_REQUEST, 400, exc.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> serverError(Exception exc) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, 500, exc.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(code, message));
    }
}
