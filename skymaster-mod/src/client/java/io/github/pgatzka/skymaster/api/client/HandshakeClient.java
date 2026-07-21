package io.github.pgatzka.skymaster.api.client;

import org.openapitools.client.ApiException;
import org.openapitools.client.model.HandshakeRequest;

@FunctionalInterface
public interface HandshakeClient {

    void handshake(HandshakeRequest request) throws ApiException;
}
