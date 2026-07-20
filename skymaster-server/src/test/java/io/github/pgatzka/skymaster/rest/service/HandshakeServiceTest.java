package io.github.pgatzka.skymaster.rest.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

@ExtendWith(MockitoExtension.class)
class HandshakeServiceTest {

    @Mock
    BuildProperties buildProperties;

    @InjectMocks
    HandshakeService handshakeService;

    @Test
    void handshakeDoesNotThrowWhenVersionsMatch() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request =
                new HandshakeRequest("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e", "InternalError_", "1.0.0-SNAPSHOT");

        assertThatNoException().isThrownBy(() -> handshakeService.handshake(request));
    }

    @Test
    void handshakeThrowsWhenVersionsDiffer() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request =
                new HandshakeRequest("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e", "InternalError_", "1.0.1-SNAPSHOT");

        assertThatThrownBy(() -> handshakeService.handshake(request)).isInstanceOf(VersionMismatchException.class);
    }
}
