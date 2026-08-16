package io.github.pgatzka.skymaster.e2e;

import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class HandshakeTest {

    private static final UUID VALID_UUID = UUID.randomUUID();

    private static final String VALID_USERNAME = "InternalError_";

    private static final String VALID_VERSION = "1.0.0-SNAPSHOT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void handshakeReturnsNoContent() throws Exception {
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, VALID_USERNAME, VALID_VERSION);
        mockMvc.perform(post("/rest/handshake/perform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

}
