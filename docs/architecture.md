# Architecture

This document explains the two modules, how they communicate, and where new code belongs.

## The two modules

**`skymaster-mod`** is a Fabric client mod for Minecraft 26.1.2. It runs inside the player's game,
observes SkyBlock, and reports to the server. It is a client mod in practice. `src/main` holds only
a small common entrypoint, and the client behavior lives in `src/client`, split by Loom's
`splitEnvironmentSourceSets()`.

**`skymaster-server`** is a Spring Boot 4 REST API. It authenticates mods, and will receive,
validate and process collected data. It is deployed as a single container.

They are coupled by an OpenAPI contract that flows in one direction: the server defines it, the mod
generates code from it. See [build-and-codegen.md](build-and-codegen.md).

## Request flow today

The only implemented endpoint is the handshake:

1. `DataCollectionModule.onEndTick` fires every client tick and asks `HandshakeService.isConnected`.
2. That service limits itself to one call per configured interval (default 60s), caching the
   last result in between.
3. It calls the generated client, which POSTs `{uuid, username, version}` to `/handshake`.
4. The server compares the version to its own build version, returning `426` on mismatch.
5. On `426` the mod disables handshakes permanently for that session; on other failures it retries
   at the next interval.

This is planned to change. See [auth-and-identity.md](auth-and-identity.md) for the rationale and
proposed design.

## Where code goes

**Server.** A REST feature is divided into small files.

```
rest/endpoint/    annotated interface: mappings, @Valid, media types
rest/controller/  implements it, delegates to a service
rest/service/     logic; throws domain exceptions
rest/request/     records with Jakarta validation
rest/exception/   domain exceptions, mapped in RestExceptionHandler
```

Method mappings, validation, and media types live on the endpoint interface. The controller keeps
the class level route and Spring stereotype annotations, implements the interface, and delegates to
the service. This split keeps the generated specification accurate without hiding the controller
route.

**Mod.** Features are `IModule` implementations registered by ordinal in the `SkyMasterClientMod`
constructor. Services take their collaborators as functional interfaces rather than reaching for
`Minecraft` directly, which is what keeps them testable outside the game. `HandshakeService`
with its `HandshakeClient` and injected `Clock` is the reference example: a static `create()` wires
the real thing, and a package private constructor lets tests supply fakes.

## Design constraints worth knowing

- **The mod cannot be trusted.** It runs on the player's machine and can be modified. Anything the
  server accepts from it needs verification; see [auth-and-identity.md](auth-and-identity.md).
- **The server is a single container.** No clustering, no shared cache, no session affinity to work
  around. Memory resident state is viable today, but that assumption is worth stating whenever you rely
  on it.
- **The game loop is not a thread pool.** Anything the mod does on the client tick thread blocks
  rendering. This is currently violated. Moving the calls off the tick thread is part of issue 33.
