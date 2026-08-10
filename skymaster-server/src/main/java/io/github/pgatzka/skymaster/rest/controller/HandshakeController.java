package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import io.github.pgatzka.skymaster.rest.service.HandshakeControllerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rest/handshake")
public class HandshakeController {

    private final HandshakeControllerService service;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public @NonNull ResponseEntity<Void> doHandshake(@RequestBody @Valid @NonNull HandshakeRequest request) {
        service.handshake(request);
        return ResponseEntity.ok().build();
    }


}
