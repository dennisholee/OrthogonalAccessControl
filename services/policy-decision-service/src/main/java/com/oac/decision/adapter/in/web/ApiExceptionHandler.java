package com.oac.decision.adapter.in.web;

import com.oac.decision.model.GovernanceConflictException;
import com.oac.decision.model.ErrorItem;
import com.oac.decision.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<ErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorItem("VALIDATION_ERROR", fieldError.getField() + " " + fieldError.getDefaultMessage(), false))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", errors));
    }

    @ExceptionHandler(GovernanceConflictException.class)
    public ResponseEntity<ErrorResponse> handleGovernanceConflict(GovernanceConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        exception.decisionCode(),
                        List.of(new ErrorItem(exception.decisionCode(), exception.getMessage(), false))
                ));
    }
}
