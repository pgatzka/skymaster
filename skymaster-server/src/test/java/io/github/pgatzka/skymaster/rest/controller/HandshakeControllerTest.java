package io.github.pgatzka.skymaster.rest.controller;

import io.github.pgatzka.skymaster.rest.controller.service.HandshakeControllerService;
import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WebMvcTest(HandshakeController.class)
public class HandshakeControllerTest {

    private static final UUID VALID_UUID = UUID.randomUUID();

    private static final String VALID_USERNAME = "InternalError_";

    private static final String VALID_VERSION = "1.0.0-SNAPSHOT";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HandshakeControllerService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void test() throws Exception {
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, VALID_USERNAME, VALID_VERSION);
        mockMvc.perform(post("/rest/handshake/perform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<HandshakeRequest> captor = ArgumentCaptor.forClass(HandshakeRequest.class);
        verify(service).perform(captor.capture());
        assertThat(captor.getValue().uuid()).isEqualTo(VALID_UUID);
        assertThat(captor.getValue().username()).isEqualTo(VALID_USERNAME);
        assertThat(captor.getValue().version()).isEqualTo(VALID_VERSION);
    }

    @Test
    void returns400AndSkipsServiceWhenBodyIsInvalid() throws Exception {
        HandshakeRequest request = new HandshakeRequest(VALID_UUID, "", VALID_VERSION);
        mockMvc.perform(post("/rest/handshake/perform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

}
