package io.github.pgatzka.skymaster.rest;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception exception) {
        log.error("Caught unhandled exception", exception);
        return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                .build();
    }

    private ResponseEntity<ProblemDetail> handleWithMessage(HttpStatus status, Exception exception) {
        return ResponseEntity.status(status).body(problem(status, exception.getMessage()));
    }

    @ExceptionHandler(VersionMismatchException.class)
    public ResponseEntity<ProblemDetail> handleVersionMismatchException(VersionMismatchException exception) {
        return handleWithMessage(HttpStatus.UPGRADE_REQUIRED, exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return handleWithMessage(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return handleWithMessage(HttpStatus.BAD_REQUEST, exception);
    }
}
