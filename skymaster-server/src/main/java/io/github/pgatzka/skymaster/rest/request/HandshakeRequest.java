package io.github.pgatzka.skymaster.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HandshakeRequest(
        @NotBlank @Pattern(regexp = "^\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12}$")
        String uuid,

        @NotBlank String username,

        @NotBlank @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?")
        String version) {}
