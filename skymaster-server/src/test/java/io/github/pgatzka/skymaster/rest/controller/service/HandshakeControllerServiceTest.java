package io.github.pgatzka.skymaster.rest.controller.service;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandshakeControllerServiceTest {

    private static final UUID VALID_UUID = UUID.randomUUID();

    private static final String VALID_USERNAME = "InternalError_";

    @Mock
    BuildProperties buildProperties;

    @InjectMocks
    HandshakeControllerService service;

    @Test
    void handshakeDoesNotThrowWhenVersionsMatch() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, VALID_USERNAME, "1.0.0-SNAPSHOT");
        assertThatNoException().isThrownBy(() -> service.perform(request));
    }

    @Test
    void handshakeThrowsWhenVersionsMismatch() {
        when(buildProperties.getVersion()).thenReturn("1.0.2-SNAPSHOT");
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, VALID_USERNAME, "1.0.0-SNAPSHOT");
        assertThatThrownBy(() -> service.perform(request)).isInstanceOf(VersionMismatchException.class);
    }

}