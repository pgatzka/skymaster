package io.github.pgatzka.skymaster.rest.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.pgatzka.skymaster.rest.exception.PlayerNotOnlineException;
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
class HandshakeServiceTest {

    private static final UUID UUID_VALUE = UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e");

    @Mock
    BuildProperties buildProperties;

    @Mock
    SkyBlockVerificationService skyBlockVerificationService;

    @InjectMocks
    HandshakeService handshakeService;

    @Test
    void handshakeVerifiesSkyBlockPresenceWhenVersionsMatch() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request = new HandshakeRequest(UUID_VALUE, "InternalError_", "1.0.0-SNAPSHOT");

        assertThatNoException().isThrownBy(() -> handshakeService.handshake(request));
        verify(skyBlockVerificationService).verifyInSkyBlock(UUID_VALUE);
    }

    @Test
    void handshakeThrowsWithoutCallingHypixelWhenVersionsDiffer() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        HandshakeRequest request = new HandshakeRequest(UUID_VALUE, "InternalError_", "1.0.1-SNAPSHOT");

        assertThatThrownBy(() -> handshakeService.handshake(request)).isInstanceOf(VersionMismatchException.class);
        verifyNoInteractions(skyBlockVerificationService);
    }

    @Test
    void handshakePropagatesVerificationFailure() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        doThrow(new PlayerNotOnlineException(UUID_VALUE))
                .when(skyBlockVerificationService)
                .verifyInSkyBlock(UUID_VALUE);
        HandshakeRequest request = new HandshakeRequest(UUID_VALUE, "InternalError_", "1.0.0-SNAPSHOT");

        assertThatThrownBy(() -> handshakeService.handshake(request)).isInstanceOf(PlayerNotOnlineException.class);
    }
}
