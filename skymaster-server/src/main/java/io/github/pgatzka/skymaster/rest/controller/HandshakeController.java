package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.endpoint.HandshakeEndpoint;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import io.github.pgatzka.skymaster.rest.service.HandshakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("handshake")
@RequiredArgsConstructor
public class HandshakeController implements HandshakeEndpoint {

    private final HandshakeService handshakeService;

    @Override
    public ResponseEntity<Void> handshake(HandshakeRequest request) {
        handshakeService.handshake(request);
        log.info("Completed handshake with {} ({})", request.getUsername(), request.getUuid());
        return ResponseEntity.ok().build();
    }

}
