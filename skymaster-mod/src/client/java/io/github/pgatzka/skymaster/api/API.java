package io.github.pgatzka.skymaster.api;

import io.github.pgatzka.skymaster.generated.openapi.ApiClient;
import io.github.pgatzka.skymaster.generated.openapi.ApiException;
import io.github.pgatzka.skymaster.generated.openapi.api.HandshakeControllerApi;
import io.github.pgatzka.skymaster.generated.openapi.model.HandshakeRequest;

public class API {

    private static API instance;

    private final HandshakeControllerApi handshakeControllerApi;

    public static void initialize(String host, int port) {
        instance = new API(host, port);
    }

    private API(String host, int port) {
        ApiClient apiClient = new ApiClient();
        apiClient.setHost(host);
        apiClient.setPort(port);

        this.handshakeControllerApi = new HandshakeControllerApi(apiClient);
    }

    public void handshake(HandshakeRequest request) throws ApiException {
        handshakeControllerApi.handshake(request);
    }

    public static API getInstance() {
        if (instance == null) {
            throw new IllegalStateException("API instance has not been initialized");
        }
        return instance;
    }
}
