package io.github.pgatzka.skymaster.rest.service;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandshakeService {

    private final BuildProperties buildProperties;

    private final SkyBlockVerificationService skyBlockVerificationService;

    public void handshake(HandshakeRequest request) {
        // Version first: a mismatched client is rejected without spending a Hypixel call.
        if (!request.version().equals(buildProperties.getVersion())) {
            throw new VersionMismatchException(request.version(), buildProperties.getVersion());
        }
        skyBlockVerificationService.verifyInSkyBlock(request.uuid());
    }
}
