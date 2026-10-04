package org.rowny.api;

import jakarta.validation.ConstraintViolationException;
import org.rowny.api.generated.model.ErrorResponse;
import org.rowny.domain.ScoreboardException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ScoreboardException.class)
    ResponseEntity<ErrorResponse> handleScoreboard(ScoreboardException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case MATCH_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TEAM_IN_USE, MATCH_FINISHED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), exception.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, exception.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode status, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail() : "Invalid request";
        return new ResponseEntity<>(new ErrorResponse(status.value(), message), headers, status);
    }
}
