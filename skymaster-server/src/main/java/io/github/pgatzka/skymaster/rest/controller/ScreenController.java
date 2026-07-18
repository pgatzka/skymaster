package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.endpoint.ScreenEndpoint;
import io.github.pgatzka.skymaster.rest.request.ScreenDataRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("screen")
public class ScreenController implements ScreenEndpoint {

    @Override
    public ResponseEntity<Void> post(ScreenDataRequest request) {
        log.info(request.toString());
        return ResponseEntity.ok().build();
    }
}
