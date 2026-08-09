package io.github.pgatzka.skymaster.rest.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.pgatzka.skymaster.generated.hypixel.api.PlayerDataApi;
import io.github.pgatzka.skymaster.generated.hypixel.model.GetPlayerStatus200Response;
import io.github.pgatzka.skymaster.generated.hypixel.model.GetPlayerStatus200ResponseSession;
import io.github.pgatzka.skymaster.rest.exception.PlayerNotInSkyBlockException;
import io.github.pgatzka.skymaster.rest.exception.PlayerNotOnlineException;
import io.github.pgatzka.skymaster.rest.exception.VerificationUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class SkyBlockVerificationServiceTest {

    private static final UUID UUID_VALUE = UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e");

    @Mock
    PlayerDataApi playerDataApi;

    @InjectMocks
    SkyBlockVerificationService skyBlockVerificationService;

    private static GetPlayerStatus200Response status(Boolean success, GetPlayerStatus200ResponseSession session) {
        return new GetPlayerStatus200Response().success(success).session(session);
    }

    private static GetPlayerStatus200ResponseSession session(Boolean online, String gameType) {
        return new GetPlayerStatus200ResponseSession().online(online).gameType(gameType);
    }

    @Test
    void passesWhenPlayerIsOnlineInSkyBlock() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(status(true, session(true, "SKYBLOCK")));

        assertThatNoException().isThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE));
    }

    @Test
    void throwsNotOnlineWhenSessionIsOffline() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(status(true, session(false, null)));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(PlayerNotOnlineException.class);
    }

    @Test
    void throwsNotOnlineWhenSessionIsMissing() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(status(true, null));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(PlayerNotOnlineException.class);
    }

    @Test
    void throwsNotInSkyBlockWhenOnlineInAnotherGame() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(status(true, session(true, "BEDWARS")));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(PlayerNotInSkyBlockException.class);
    }

    @Test
    void throwsNotInSkyBlockWhenGameTypeIsMissing() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(status(true, session(true, null)));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(PlayerNotInSkyBlockException.class);
    }

    @Test
    void failsClosedWhenHypixelRespondsWithAnError() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString()))
                .thenThrow(new RestClientResponseException("Invalid API key", 403, "Forbidden", null, null, null));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(VerificationUnavailableException.class);
    }

    @Test
    void failsClosedWhenHypixelIsUnreachable() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString()))
                .thenThrow(new RestClientException("connect timed out"));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(VerificationUnavailableException.class);
    }

    @Test
    void failsClosedWhenHypixelReportsFailure() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(status(false, null));

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(VerificationUnavailableException.class);
    }

    @Test
    void failsClosedWhenHypixelReturnsNoBody() {
        when(playerDataApi.getPlayerStatus(UUID_VALUE.toString())).thenReturn(null);

        assertThatThrownBy(() -> skyBlockVerificationService.verifyInSkyBlock(UUID_VALUE))
                .isInstanceOf(VerificationUnavailableException.class);
    }
}
