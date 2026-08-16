package io.github.pgatzka.skymaster.rest.request;

import io.github.pgatzka.skymaster.test.RequestValidationTest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.params.provider.Arguments;

import java.util.UUID;
import java.util.stream.Stream;

class HandshakeRequestTest extends RequestValidationTest<HandshakeRequest> {

    private static final UUID VALID_UUID = UUID.randomUUID();

    private static final String VALID_USERNAME = "InternalError_";

    private static final String VALID_VERSION = "1.0.0-SNAPSHOT";

    @Override
    protected HandshakeRequest valid() {
        return new HandshakeRequest(VALID_UUID, VALID_USERNAME, VALID_VERSION);
    }

    private static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.arguments(new HandshakeRequest(null, VALID_USERNAME, VALID_VERSION), null, "uuid", NotNull.class),
                Arguments.arguments(new HandshakeRequest(VALID_UUID, null, VALID_VERSION), null, "username", NotBlank.class),
                Arguments.arguments(new HandshakeRequest(VALID_UUID, "", VALID_VERSION), "", "username", NotBlank.class),
                Arguments.arguments(new HandshakeRequest(VALID_UUID, VALID_USERNAME, null), null, "version", NotBlank.class),
                Arguments.arguments(new HandshakeRequest(VALID_UUID, VALID_USERNAME, ""), "", "version", NotBlank.class),
                Arguments.arguments(new HandshakeRequest(VALID_UUID, VALID_USERNAME, "invalid-version"), "invalid-version", "version", Pattern.class)
        );
    }

}