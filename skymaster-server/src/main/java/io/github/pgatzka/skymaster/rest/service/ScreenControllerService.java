package io.github.pgatzka.skymaster.rest.service;

import io.github.pgatzka.skymaster.rest.endpoint.ScreenEndpoint;
import io.github.pgatzka.skymaster.rest.request.ScreenDataRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenControllerService implements ScreenEndpoint {

    private final ObjectMapper objectMapper;

    @Override
    public ResponseEntity<Void> pushScreenData(ScreenDataRequest request) {
        String filename = request.title().trim().toLowerCase().replace(" ", "-") + ".json";
        File storageDir = new File("storage");
        storageDir.mkdir();
        File file = new File(storageDir, filename);
        log.info("Writing screen data to: {}", file.getAbsolutePath());
        objectMapper.writeValue(file, request);
        log.info("Successfully written screen data to {}", file.getAbsolutePath());
        return ResponseEntity.ok().build();
    }

}
