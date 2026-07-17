package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.endpoint.PingEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ping")
public class PingController implements PingEndpoint {

    @Override
    public ResponseEntity<String> ping() {
        log.info("Ping received");
        return ResponseEntity.ok("pong");
    }

}
