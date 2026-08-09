package io.github.pgatzka.skymaster.rest.exception;

import java.util.UUID;

public class PlayerNotOnlineException extends RuntimeException {

    public PlayerNotOnlineException(UUID uuid) {
        super("Hypixel reports " + uuid + " as offline");
    }
}
