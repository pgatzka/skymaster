package io.github.pgatzka.skymaster.api.pojo;

import java.util.UUID;

public record HandshakeIdentity(String username, UUID uuid) {}
