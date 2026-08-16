
package io.github.pgatzka.skymaster.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class VersionMismatchException extends ResponseStatusException {

    public VersionMismatchException(String clientVersion, String serverVersion) {
        super(HttpStatus.UPGRADE_REQUIRED,
                "Client has version " + clientVersion + ", but server requires version " + serverVersion);
    }

}
