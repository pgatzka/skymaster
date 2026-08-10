package io.github.pgatzka.skymaster.rest.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

@ExtendWith(MockitoExtension.class)
class HandshakeControllerServiceTest {

    @Mock
    BuildProperties buildProperties;

    @InjectMocks
    HandshakeControllerService service;

    @Test
    void handshakeDoesNotThrowWhenVersionsMatch() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request = new HandshakeRequest(
                UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e"), "InternalError_", "1.0.0-SNAPSHOT");

        assertThatNoException().isThrownBy(() -> service.handshake(request));
    }

    @Test
    void handshakeThrowsWhenVersionsDiffer() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request = new HandshakeRequest(
                UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e"), "InternalError_", "1.0.1-SNAPSHOT");

        assertThatThrownBy(() -> service.handshake(request)).isInstanceOf(VersionMismatchException.class);
    }
}
