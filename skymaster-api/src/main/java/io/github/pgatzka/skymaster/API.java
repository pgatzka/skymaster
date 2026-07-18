package io.github.pgatzka.skymaster;

import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.PingControllerApi;
import org.openapitools.client.api.ScreenControllerApi;
import org.openapitools.client.model.PingResponse;
import org.openapitools.client.model.ScreenDataRequest;

public class API {

    private final ScreenControllerApi screenApi;

    private final PingControllerApi pingApi;

    public API(String host) {
        ApiClient apiClient = new ApiClient();
        apiClient.setHost(host);

        this.screenApi = new ScreenControllerApi(apiClient);
        this.pingApi = new PingControllerApi(apiClient);
    }

    public void postScreenData(ScreenDataRequest request) throws ApiException {
        screenApi.post(request);
    }

    public PingResponse getPing() throws ApiException {
        return pingApi.ping();
    }
}
