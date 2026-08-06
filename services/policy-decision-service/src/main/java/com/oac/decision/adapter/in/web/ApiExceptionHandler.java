package com.oac.decision.adapter.in.web;

import com.oac.decision.model.ErrorItem;
import com.oac.decision.model.ErrorResponse;
import com.oac.decision.model.PolicyDomainException;
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

    @ExceptionHandler(PolicyDomainException.class)
    public ResponseEntity<ErrorResponse> handlePolicyDomain(PolicyDomainException exception) {
        HttpStatus status = HttpStatus.resolve(exception.httpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        exception.decisionCode(),
                        List.of(new ErrorItem(exception.decisionCode(), exception.getMessage(), false))
                ));
    }
}
