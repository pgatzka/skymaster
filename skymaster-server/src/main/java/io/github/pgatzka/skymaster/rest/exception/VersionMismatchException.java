package io.github.pgatzka.skymaster.rest.exception;

public class VersionMismatchException extends RuntimeException {

    public VersionMismatchException(String modVersion, String serverVersion) {
        super("required version: " + serverVersion + ", actual version: " + modVersion);
    }

}
