package io.github.pgatzka.skymaster.rest.exception;

import java.util.UUID;

public class PlayerNotInSkyBlockException extends RuntimeException {

    public PlayerNotInSkyBlockException(UUID uuid, String gameType) {
        super("Hypixel reports " + uuid + " as online but not in SkyBlock (gameType: " + gameType + ")");
    }
}
