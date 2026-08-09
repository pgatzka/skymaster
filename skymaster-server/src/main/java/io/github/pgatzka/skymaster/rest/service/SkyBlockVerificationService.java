package io.github.pgatzka.skymaster.rest.service;

import io.github.pgatzka.skymaster.generated.hypixel.api.PlayerDataApi;
import io.github.pgatzka.skymaster.generated.hypixel.model.GetPlayerStatus200Response;
import io.github.pgatzka.skymaster.generated.hypixel.model.GetPlayerStatus200ResponseSession;
import io.github.pgatzka.skymaster.rest.exception.PlayerNotInSkyBlockException;
import io.github.pgatzka.skymaster.rest.exception.PlayerNotOnlineException;
import io.github.pgatzka.skymaster.rest.exception.VerificationUnavailableException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks the official Hypixel API whether a UUID is online and in SkyBlock right now, and fails
 * closed: any upstream failure rejects, because a verification step that silently passes when the
 * verifier is unreachable can be bypassed by attacking the verifier.
 *
 * <p>Every verification spends one Hypixel call against the API key's rate limit (roughly 300
 * requests per 5 minutes), which caps concurrent players at about one handshake per player per
 * minute. Accepted for now: the handshake is planned to become a login returning a token, removing
 * the per-minute call entirely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkyBlockVerificationService {

    static final String SKYBLOCK_GAME_TYPE = "SKYBLOCK";

    private final PlayerDataApi playerDataApi;

    public void verifyInSkyBlock(UUID uuid) {
        GetPlayerStatus200Response response;
        try {
            response = playerDataApi.getPlayerStatus(uuid.toString());
        } catch (RuntimeException exception) {
            throw new VerificationUnavailableException(exception);
        }
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            throw new VerificationUnavailableException("Hypixel returned an unsuccessful status response");
        }
        GetPlayerStatus200ResponseSession session = response.getSession();
        if (session == null || !Boolean.TRUE.equals(session.getOnline())) {
            throw new PlayerNotOnlineException(uuid);
        }
        if (!SKYBLOCK_GAME_TYPE.equals(session.getGameType())) {
            throw new PlayerNotInSkyBlockException(uuid, session.getGameType());
        }
    }
}
