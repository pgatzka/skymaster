package io.github.pgatzka.skymaster;

import net.fabricmc.api.ClientModInitializer;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Configuration;
import org.openapitools.client.api.PingControllerApi;

import static io.github.pgatzka.skymaster.SkyMasterMod.log;

public class SkyMasterClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setHost("localhost");
        PingControllerApi pingControllerApi = new PingControllerApi(apiClient);
        try {
            log.info(pingControllerApi.ping().getMessage());
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

}
