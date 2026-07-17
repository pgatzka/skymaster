package io.github.pgatzka.skymaster.rest.endpoint;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

public interface PingEndpoint {

    @GetMapping
    ResponseEntity<String> ping();

}
