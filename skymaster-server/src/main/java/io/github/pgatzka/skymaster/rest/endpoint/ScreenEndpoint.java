package io.github.pgatzka.skymaster.rest.endpoint;

import io.github.pgatzka.skymaster.rest.request.ScreenDataRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface ScreenEndpoint {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> post(@RequestBody ScreenDataRequest request);
}
