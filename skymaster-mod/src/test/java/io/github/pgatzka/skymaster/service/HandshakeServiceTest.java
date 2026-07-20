package io.github.pgatzka.skymaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.skymaster.API;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.client.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.HandshakeRequest;

class HandshakeServiceTest {

    private static final int UPGRADE_REQUIRED = 426;
    private static final int INTERNAL_ERROR = 500;
    private static final String MOD_VERSION = "1.2.3";
    private static final long INTERVAL_SECONDS = 60L;

    private MockedStatic<API> apiStatic;
    private API api;

    private User user;
    private UUID profileId;
    private MutableClock clock;

    private HandshakeService service;

    @BeforeEach
    void setUp() {
        // API is obtained via the static API.getInstance() inside the service,
        // so that single static call is intercepted here.
        apiStatic = mockStatic(API.class);
        api = mock(API.class);
        apiStatic.when(API::getInstance).thenReturn(api);

        user = mock(User.class);
        profileId = UUID.randomUUID();
        when(user.getName()).thenReturn("Steve");
        when(user.getProfileId()).thenReturn(profileId);

        clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));

        LongSupplier interval = () -> INTERVAL_SECONDS;
        service = new HandshakeService(interval, MOD_VERSION, clock);
    }

    @AfterEach
    void tearDown() {
        if (apiStatic != null) {
            apiStatic.close();
        }
    }

    @Test
    void returnsTrueAndSendsPopulatedRequest_onSuccessfulHandshake() throws Exception {
        boolean connected = service.isConnected(user);

        assertTrue(connected);

        ArgumentCaptor<HandshakeRequest> captor = ArgumentCaptor.forClass(HandshakeRequest.class);
        verify(api).handshake(captor.capture());
        HandshakeRequest sent = captor.getValue();
        assertEquals("Steve", sent.getUsername());
        assertEquals(profileId.toString(), sent.getUuid());
        assertEquals(MOD_VERSION, sent.getVersion());
    }

    @Test
    void returnsCachedResult_withoutCallingApi_whenIntervalNotElapsed() throws Exception {
        assertTrue(service.isConnected(user));

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS - 1)); // still inside the interval

        assertTrue(service.isConnected(user));
        verify(api, times(1)).handshake(any());
    }

    @Test
    void performsAnotherHandshake_onceIntervalHasElapsed() throws Exception {
        assertTrue(service.isConnected(user));

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1)); // interval elapsed

        assertTrue(service.isConnected(user));
        verify(api, times(2)).handshake(any());
    }

    @Test
    void disablesFutureHandshakes_onVersionMismatch() throws Exception {
        doThrow(apiException(UPGRADE_REQUIRED)).when(api).handshake(any());

        assertFalse(service.isConnected(user)); // 426 -> disabled

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1)); // interval would otherwise allow a retry

        assertFalse(service.isConnected(user)); // 'disabled' short-circuits before the interval check
        verify(api, times(1)).handshake(any());
    }

    @Test
    void keepsRetrying_onNonUpgradeApiException() throws Exception {
        doThrow(apiException(INTERNAL_ERROR)).when(api).handshake(any());

        assertFalse(service.isConnected(user));

        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));

        assertFalse(service.isConnected(user));
        verify(api, times(2)).handshake(any()); // not disabled -> retried
    }

    @Test
    void reportsDisconnected_afterSuccessThenFailure() throws Exception {
        assertTrue(service.isConnected(user));

        doThrow(apiException(INTERNAL_ERROR)).when(api).handshake(any());
        clock.advance(Duration.ofSeconds(INTERVAL_SECONDS + 1));

        assertFalse(service.isConnected(user));
        verify(api, times(2)).handshake(any());
    }

    // --- helpers ---

    private static ApiException apiException(int code) {
        ApiException exception = mock(ApiException.class);
        when(exception.getCode()).thenReturn(code);
        when(exception.getResponseBody()).thenReturn("body");
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
