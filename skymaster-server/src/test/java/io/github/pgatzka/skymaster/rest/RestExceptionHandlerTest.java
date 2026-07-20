package io.github.pgatzka.skymaster.rest;

import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/throwUnhandled")
        void throwUnhandled() {
            throw new RuntimeException();
        }

        @GetMapping("/throwVersionMismatch")
        void throwVersionMismatch() {
            throw new VersionMismatchException("9.9.9", "1.0.0-SNAPSHOT");
        }

    }

    private MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void handlesVersionMismatchException() throws Exception {
        mockMvc.perform(get("/throwVersionMismatch"))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.detail").value("required version: 1.0.0-SNAPSHOT, actual version: 9.9.9"));
    }

    @Test
    void handlesUnhandled() throws Exception {
        mockMvc.perform(get("/throwUnhandled"))
                .andExpect(status().isInternalServerError());
    }

}