package io.github.pgatzka.skymaster;

import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.HandshakeControllerApi;
import org.openapitools.client.model.HandshakeRequest;

public class API {

    private final HandshakeControllerApi handshakeControllerApi;

    public API(String host, int port) {
        ApiClient apiClient = new ApiClient();
        apiClient.setHost(host);
        apiClient.setPort(port);

        this.handshakeControllerApi = new HandshakeControllerApi(apiClient);
    }

    public void handshake(HandshakeRequest request) throws ApiException {
        handshakeControllerApi.handshake(request);
    }


}
