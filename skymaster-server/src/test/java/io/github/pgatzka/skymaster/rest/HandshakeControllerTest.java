package io.github.pgatzka.skymaster.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.pgatzka.skymaster.rest.controller.HandshakeController;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import io.github.pgatzka.skymaster.rest.service.HandshakeControllerService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(HandshakeController.class)
class HandshakeControllerTest {

    private static final UUID VALID_UUID = UUID.fromString("d8cb5ee8-6607-4e26-8b2a-0f9e7c201a4e");

    private static final String VALID_USERNAME = "InternalError_";

    private static final String VALID_VERSION = "1.0.0-SNAPSHOT";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HandshakeControllerService service;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void completesHandshakeAndReturns200() throws Exception {
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, VALID_USERNAME, VALID_VERSION);
        mockMvc.perform(post("/rest/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        ArgumentCaptor<HandshakeRequest> captor = ArgumentCaptor.forClass(HandshakeRequest.class);
        verify(service).handshake(captor.capture());
        assertThat(captor.getValue().uuid()).isEqualTo(VALID_UUID);
        assertThat(captor.getValue().username()).isEqualTo(VALID_USERNAME);
        assertThat(captor.getValue().version()).isEqualTo(VALID_VERSION);
    }

    @Test
    void returns400AndSkipsServiceWhenBodyIsInvalid() throws Exception {
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, VALID_USERNAME, VALID_VERSION);
        String json = objectMapper.writeValueAsString(request);
        json = json.replace(VALID_UUID.toString(), "invalid-uuid");
        mockMvc.perform(post("/rest/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
