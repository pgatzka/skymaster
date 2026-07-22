package io.github.pgatzka.skymaster.api.client;

import io.github.pgatzka.skymaster.generated.openapi.ApiException;
import io.github.pgatzka.skymaster.generated.openapi.model.HandshakeRequest;

@FunctionalInterface
public interface HandshakeClient {

    void handshake(HandshakeRequest request) throws ApiException;
}
