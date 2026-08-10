package io.github.pgatzka.skymaster.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record HandshakeRequest(
        @NonNull @NotNull UUID uuid,
        @NonNull @NotBlank String username,
        @NonNull @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?") String version
) {
}
