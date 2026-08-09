package io.github.pgatzka.skymaster.rest;

import io.github.pgatzka.skymaster.rest.exception.PlayerNotInSkyBlockException;
import io.github.pgatzka.skymaster.rest.exception.PlayerNotOnlineException;
import io.github.pgatzka.skymaster.rest.exception.VerificationUnavailableException;
import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Extends {@link ResponseEntityExceptionHandler} so Spring's own exceptions are mapped to their
 * proper statuses (a wrong HTTP method returns 405, an unknown path 404) instead of falling
 * through to the 500 catch-all.
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE_PROPERTY = "code";

    static final String CODE_NOT_ONLINE = "not-online";

    static final String CODE_NOT_IN_SKYBLOCK = "not-in-skyblock";

    static final String CODE_VERIFICATION_UNAVAILABLE = "verification-unavailable";

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    private static ResponseEntity<ProblemDetail> problemWithCode(HttpStatus status, Exception exception, String code) {
        ProblemDetail problemDetail = problem(status, exception.getMessage());
        problemDetail.setProperty(CODE_PROPERTY, code);
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandledException(Exception exception) {
        log.error("Caught unhandled exception", exception);
        return ResponseEntity.of(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                .build();
    }

    @ExceptionHandler(VersionMismatchException.class)
    public ResponseEntity<ProblemDetail> handleVersionMismatchException(VersionMismatchException exception) {
        return ResponseEntity.status(HttpStatus.UPGRADE_REQUIRED)
                .body(problem(HttpStatus.UPGRADE_REQUIRED, exception.getMessage()));
    }

    @ExceptionHandler(PlayerNotOnlineException.class)
    public ResponseEntity<ProblemDetail> handlePlayerNotOnlineException(PlayerNotOnlineException exception) {
        return problemWithCode(HttpStatus.FORBIDDEN, exception, CODE_NOT_ONLINE);
    }

    @ExceptionHandler(PlayerNotInSkyBlockException.class)
    public ResponseEntity<ProblemDetail> handlePlayerNotInSkyBlockException(PlayerNotInSkyBlockException exception) {
        return problemWithCode(HttpStatus.FORBIDDEN, exception, CODE_NOT_IN_SKYBLOCK);
    }

    @ExceptionHandler(VerificationUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleVerificationUnavailableException(
            VerificationUnavailableException exception) {
        log.warn("Hypixel verification unavailable", exception);
        return problemWithCode(HttpStatus.SERVICE_UNAVAILABLE, exception, CODE_VERIFICATION_UNAVAILABLE);
    }

    /**
     * Replaces the default handling with a field-level summary. The exception message itself must
     * never end up in the response: it contains the whole binding result, including the target
     * class name.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(status).headers(headers).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
