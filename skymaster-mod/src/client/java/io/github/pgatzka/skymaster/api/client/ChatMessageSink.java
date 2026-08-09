package io.github.pgatzka.skymaster.api.client;

/**
 * Sink for messages shown to the player in chat. Mirrors the {@link HandshakeClient} pattern so
 * services stay free of Minecraft types.
 */
@FunctionalInterface
public interface ChatMessageSink {

    void sendChatMessage(String message);
}
