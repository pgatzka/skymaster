package io.github.pgatzka.skymaster.rest.service;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HandshakeControllerService {

    private final BuildProperties buildProperties;

    public void handshake(HandshakeRequest request) {
        if (!request.version().equals(buildProperties.getVersion())) {
            throw new VersionMismatchException(request.version(), buildProperties.getVersion());
        }
    }
}
