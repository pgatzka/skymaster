package io.github.pgatzka.skymaster.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.pgatzka.skymaster.rest.exception.PlayerNotInSkyBlockException;
import io.github.pgatzka.skymaster.rest.exception.PlayerNotOnlineException;
import io.github.pgatzka.skymaster.rest.exception.VerificationUnavailableException;
import io.github.pgatzka.skymaster.rest.exception.VersionMismatchException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RestExceptionHandlerTest {

    private static final UUID UUID_VALUE = UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e");

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

        @GetMapping("/throwPlayerNotOnline")
        void throwPlayerNotOnline() {
            throw new PlayerNotOnlineException(UUID_VALUE);
        }

        @GetMapping("/throwPlayerNotInSkyBlock")
        void throwPlayerNotInSkyBlock() {
            throw new PlayerNotInSkyBlockException(UUID_VALUE, "BEDWARS");
        }

        @GetMapping("/throwVerificationUnavailable")
        void throwVerificationUnavailable() {
            throw new VerificationUnavailableException("upstream down");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void handlesVersionMismatchException() throws Exception {
        mockMvc.perform(get("/throwVersionMismatch"))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.detail").value("required version: 1.0.0-SNAPSHOT, actual version: 9.9.9"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void handlesPlayerNotOnlineException() throws Exception {
        mockMvc.perform(get("/throwPlayerNotOnline"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestExceptionHandler.CODE_NOT_ONLINE));
    }

    @Test
    void handlesPlayerNotInSkyBlockException() throws Exception {
        mockMvc.perform(get("/throwPlayerNotInSkyBlock"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(RestExceptionHandler.CODE_NOT_IN_SKYBLOCK));
    }

    @Test
    void handlesVerificationUnavailableException() throws Exception {
        mockMvc.perform(get("/throwVerificationUnavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(RestExceptionHandler.CODE_VERIFICATION_UNAVAILABLE));
    }

    @Test
    void handlesUnhandled() throws Exception {
        mockMvc.perform(get("/throwUnhandled")).andExpect(status().isInternalServerError());
    }

    @Test
    void handlesWrongHttpMethodAsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/throwUnhandled")).andExpect(status().isMethodNotAllowed());
    }
}
