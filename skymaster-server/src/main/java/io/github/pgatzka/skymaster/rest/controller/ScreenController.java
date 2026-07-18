package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.endpoint.ScreenEndpoint;
import io.github.pgatzka.skymaster.rest.request.ScreenDataRequest;
import io.github.pgatzka.skymaster.rest.service.ScreenControllerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("screen")
@RequiredArgsConstructor
public class ScreenController implements ScreenEndpoint {

    private final ScreenControllerService service;

    @Override
    public ResponseEntity<Void> pushScreenData(ScreenDataRequest request) {
        return service.pushScreenData(request);
    }

}
