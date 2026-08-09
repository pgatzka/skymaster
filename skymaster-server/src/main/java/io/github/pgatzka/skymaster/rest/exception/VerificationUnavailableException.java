package io.github.pgatzka.skymaster.rest.exception;

public class VerificationUnavailableException extends RuntimeException {

    public VerificationUnavailableException(String message) {
        super(message);
    }

    public VerificationUnavailableException(Throwable cause) {
        super("Hypixel status verification failed", cause);
    }
}
