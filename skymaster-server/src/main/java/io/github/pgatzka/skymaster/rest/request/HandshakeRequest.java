package io.github.pgatzka.skymaster.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record HandshakeRequest(
        @NotNull UUID uuid,
        @NotBlank String username,
        @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?") String version
) {
}
