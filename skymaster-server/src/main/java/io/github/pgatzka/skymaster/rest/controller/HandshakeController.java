package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.controller.service.HandshakeControllerService;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/handshake")
@RequiredArgsConstructor
public class HandshakeController {

    private final HandshakeControllerService service;

    @PostMapping(value = "/perform", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> perform(@RequestBody @Valid HandshakeRequest request) {
        service.perform(request);
        return ResponseEntity.noContent().build();
    }

}
