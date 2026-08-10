package io.github.pgatzka.skymaster.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class VersionMismatchException extends ResponseStatusException {

    public VersionMismatchException(String modVersion, String serverVersion) {
        super(HttpStatus.UPGRADE_REQUIRED, "required version: " + serverVersion + ", actual version: " + modVersion);
    }
}
