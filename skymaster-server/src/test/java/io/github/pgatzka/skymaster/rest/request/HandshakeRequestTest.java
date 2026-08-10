package io.github.pgatzka.skymaster.rest.request;

import io.github.pgatzka.skymaster.test.RequestValidationTest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

class HandshakeRequestTest extends RequestValidationTest<HandshakeRequest> {

    private static final UUID VALID_UUID = UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e");

    private static final String VALID_USERNAME = "InternalError_";

    private static final String VALID_VERSION = "1.0.0-SNAPSHOT";

    @Override
    protected HandshakeRequest valid() {
        return new HandshakeRequest(VALID_UUID, VALID_USERNAME, VALID_VERSION);
    }

    private static HandshakeRequest withUuid(UUID uuid) {
        return new HandshakeRequest(uuid, VALID_USERNAME, VALID_VERSION);
    }

    private static HandshakeRequest withUsername(String username) {
        return new HandshakeRequest(VALID_UUID, username, VALID_VERSION);
    }

    private static HandshakeRequest withVersion(String version) {
        return new HandshakeRequest(VALID_UUID, VALID_USERNAME, version);
    }

    static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.arguments(withUuid(null), "null", "uuid", NotNull.class),
                Arguments.arguments(withUsername(""), "empty", "username", NotBlank.class),
                Arguments.arguments(withUsername(null), "null", "username", NotBlank.class),
                Arguments.arguments(withUsername(" "), "whitespace-only", "username", NotBlank.class),
                Arguments.arguments(withVersion(""), "empty", "version", NotBlank.class),
                Arguments.arguments(withVersion(null), "null", "version", NotBlank.class),
                Arguments.arguments(withVersion(" "), "whitespace-only", "version", NotBlank.class),
                Arguments.arguments(withVersion("hello-world"), "invalid version", "version", Pattern.class));
    }
}
