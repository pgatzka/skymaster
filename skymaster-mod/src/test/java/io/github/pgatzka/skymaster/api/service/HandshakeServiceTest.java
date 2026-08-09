package io.github.pgatzka.skymaster.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.skymaster.api.API;
import io.github.pgatzka.skymaster.api.pojo.HandshakeIdentity;
import io.github.pgatzka.skymaster.generated.openapi.ApiException;
import io.github.pgatzka.skymaster.generated.openapi.model.HandshakeRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandshakeServiceTest {

    private static final int HTTP_UPGRADE_REQUIRED = 426;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final int HTTP_INTERNAL_ERROR = 500;
    private static final String MOD_VERSION = "1.2.3";
    private static final long INTERVAL_SECONDS = 60L;

    private API api;

    private HandshakeIdentity identity;
    private UUID profileId;
    private MutableClock clock;
    private List<String> chatMessages;

    private HandshakeService service;

    @BeforeEach
    void setUp() {
        // API is obtained via the static API.getInstance() inside the service,
        // so that single static call is intercepted here.
        api = mock(API.class);

        profileId = UUID.randomUUID();
        identity = new HandshakeIdentity("Steve", profileId);

        clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        chatMessages = new ArrayList<>();

        LongSupplier interval = () -> INTERVAL_SECONDS;
        service = new HandshakeService(
                request -> api.handshake(request), chatMessages::add, interval, MOD_VERSION, clock);
    }

    @Test
    void returnsTrueAndSendsPopulatedRequest_onSuccessfulHandshake() throws Exception {
        boolean connected = service.isConnected(identity);

        assertTrue(connected);

        ArgumentCaptor<HandshakeRequest> captor = ArgumentCaptor.forClass(HandshakeRequest.class);
        verify(api).handshake(captor.capture());
        HandshakeRequest sent = captor.getValue();
        assertEquals("Steve", sent.getUsername());
        assertEquals(profileId, sent.getUuid());
        assertEquals(MOD_VERSION, sent.getVersion());
    }

    @Test
    void returnsCachedResult_withoutCallingApi_whenIntervalNotElapsed() throws Exception {
        assertTrue(service.isConnected(identity));

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS - 1)); // still inside the interval

        assertTrue(service.isConnected(identity));
        verify(api, times(1)).handshake(any());
    }

    @Test
    void performsAnotherHandshake_onceIntervalHasElapsed() throws Exception {
        assertTrue(service.isConnected(identity));

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1)); // interval elapsed

        assertTrue(service.isConnected(identity));
        verify(api, times(2)).handshake(any());
    }

    @Test
    void disablesFutureHandshakes_onVersionMismatch() throws Exception {
        doThrow(apiException(HTTP_UPGRADE_REQUIRED)).when(api).handshake(any());

        assertFalse(service.isConnected(identity)); // 426 -> disabled

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1)); // interval would otherwise allow a retry

        assertFalse(service.isConnected(identity)); // 'disabled' short-circuits before the interval check
        verify(api, times(1)).handshake(any());
    }

    @Test
    void keepsRetrying_onNonUpgradeApiException() throws Exception {
        doThrow(apiException(HTTP_INTERNAL_ERROR)).when(api).handshake(any());

        assertFalse(service.isConnected(identity));

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));

        assertFalse(service.isConnected(identity));
        verify(api, times(2)).handshake(any()); // not disabled -> retried
    }

    @Test
    void reportsDisconnected_afterSuccessThenFailure() throws Exception {
        assertTrue(service.isConnected(identity));

        doThrow(apiException(HTTP_INTERNAL_ERROR)).when(api).handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));

        assertFalse(service.isConnected(identity));
        verify(api, times(2)).handshake(any());
    }

    @Test
    void sendsChatMessageOncePerNotOnlineEpisode() throws Exception {
        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());

        assertFalse(service.isConnected(identity));
        assertEquals(1, chatMessages.size());

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));

        assertFalse(service.isConnected(identity)); // still the same episode
        assertEquals(1, chatMessages.size());
    }

    @Test
    void sendsChatMessageAgainForNewNotOnlineEpisode() throws Exception {
        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());
        assertFalse(service.isConnected(identity));

        reset(api); // a successful handshake ends the episode
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));
        assertTrue(service.isConnected(identity));

        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));
        assertFalse(service.isConnected(identity));

        assertEquals(2, chatMessages.size());
    }

    @Test
    void keepsNotOnlineEpisodeAcrossFailuresWithoutACode() throws Exception {
        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());
        assertFalse(service.isConnected(identity));

        doThrow(apiException(HTTP_INTERNAL_ERROR, null)).when(api).handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));
        assertFalse(service.isConnected(identity));

        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));
        assertFalse(service.isConnected(identity));

        assertEquals(1, chatMessages.size());
    }

    @Test
    void endsNotOnlineEpisodeOnDifferentRejectionCode() throws Exception {
        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());
        assertFalse(service.isConnected(identity));

        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-in-skyblock\"}"))
                .when(api)
                .handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));
        assertFalse(service.isConnected(identity));

        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-online\"}"))
                .when(api)
                .handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));
        assertFalse(service.isConnected(identity));

        assertEquals(2, chatMessages.size());
    }

    @Test
    void sendsNoChatMessageForNotInSkyBlock() throws Exception {
        doThrow(apiException(HTTP_FORBIDDEN, "{\"code\":\"not-in-skyblock\"}"))
                .when(api)
                .handshake(any());

        assertFalse(service.isConnected(identity));
        assertTrue(chatMessages.isEmpty());
    }

    @Test
    void sendsNoChatMessageForVerificationUnavailable() throws Exception {
        doThrow(apiException(HTTP_SERVICE_UNAVAILABLE, "{\"code\":\"verification-unavailable\"}"))
                .when(api)
                .handshake(any());

        assertFalse(service.isConnected(identity));
        assertTrue(chatMessages.isEmpty());
    }

    @Test
    void sendsNoChatMessageForUnparsableResponseBody() throws Exception {
        doThrow(apiException(HTTP_FORBIDDEN, "not json")).when(api).handshake(any());

        assertFalse(service.isConnected(identity));
        assertTrue(chatMessages.isEmpty());
    }

    // --- helpers ---

    private static ApiException apiException(int code) {
        return apiException(code, "body");
    }

    private static ApiException apiException(int code, String responseBody) {
        ApiException exception = mock(ApiException.class);
        when(exception.getCode()).thenReturn(code);
        when(exception.getResponseBody()).thenReturn(responseBody);
        when(exception.getMessage()).thenReturn("boom");
        return exception;
    }

    /** A Clock whose current instant can be advanced by the test. */
    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }
    }
}
