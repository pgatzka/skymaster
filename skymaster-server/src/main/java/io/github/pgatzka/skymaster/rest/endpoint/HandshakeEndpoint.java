package io.github.pgatzka.skymaster.rest.endpoint;

import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface HandshakeEndpoint {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> handshake(@RequestBody @Valid HandshakeRequest request);
}
