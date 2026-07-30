# skymaster-mod

Fabric client mod for Minecraft 26.1.2. Collects Hypixel SkyBlock data and reports it to
`:skymaster-server`, using an HTTP client generated from that server's OpenAPI spec.

Read `docs/architecture.md` for how the modules fit together, `docs/pitfalls.md` when something fails
unexpectedly. Start at `docs/README.md`.

## Source sets

Loom's `splitEnvironmentSourceSets()` splits this module in two, and it matters where code goes:

- `src/main/java` — common/server-side entrypoint (`SkyMasterMod`). Almost nothing lives here.
  Minecraft **client** classes are not on its classpath.
- `src/client/java` — the real mod. Anything touching `Minecraft`, screens, config or rendering.

Coverage and Sonar analysis are both pointed at the `client` source set, so code placed in `main` by
mistake silently escapes them.

- `src/main/java/.../generated/openapi/` is build output. Never edit it; it is regenerated on every
  `compileJava` from the server's spec.

## Structure

- Features are `IModule` implementations, registered by ordinal in the `SkyMasterClientMod`
  constructor. The ordinal is initialisation order — add a module there, not by scanning.
- Config is a MoulConfig `Config` subclass reached through `SkyMasterClientMod.getConfig()`.
- Services keep Minecraft types out and take collaborators as functional interfaces — see
  `HandshakeClient` and the `HandshakeService.create()` / package-private constructor pair. That is
  what makes them unit-testable without booting Minecraft, so follow it for new services.

## Always / never

- Runtime dependencies that must ship inside the jar go in the `shipped` configuration. It is fed
  into Loom's `include`, so plain `implementation` compiles but does **not** bundle — the mod then
  fails at runtime on a user's machine while working fine in `runClient`.
- Config fields need `@Expose` to be persisted, plus `@ConfigOption` and an editor annotation to
  appear in the UI. Renaming a field silently resets every existing user to the default, because
  MoulConfig keys the JSON by field name.
- `fabric.mod.json` duplicates versions that also live in `gradle/libs.versions.toml` — its
  `depends` block pins loader, Minecraft, Java, Fabric API and Kotlin. Bump one and you must bump
  the other, or the mod refuses to load against the very versions the build compiled it for.
- Mockito runs as a `-javaagent` with `-Xshare:off` via the `mockitoAgent` configuration.

## Known problems

- **Network calls run on the client tick thread.** `DataCollectionModule.onEndTick` drives blocking
  HTTP from the render thread, so an upstream timeout freezes the game. Moving this off-thread is
  part of #33; don't add more blocking work there.
- **No coverage gate.** `jacocoTestCoverageVerification` is configured only in the server module, so
  mod coverage can drift without CI noticing. Accepted rather than tracked — see #38 for why.

## Running it

`./gradlew :skymaster-mod:runClient` launches Minecraft with the mod. DevAuth is on `localRuntime`
and handles login, so no manual account setup is needed.

## Reviewing a change here

Every one of these compiles and passes CI while being wrong:

- **Did new code land in `src/client`?** Anything touching Minecraft belongs there. Code placed in
  `src/main` by mistake escapes coverage and Sonar silently.
- **Did a new runtime dependency go into `shipped` rather than `implementation`?** `implementation`
  works in `runClient` and fails on a user's machine.
- **Do new config fields have `@Expose`, and was an existing field renamed?** A rename resets every
  user to the default without any error.
- **Was a version bumped in only one of `fabric.mod.json` and `gradle/libs.versions.toml`?** The mod
  then refuses to load against the versions it was compiled for.
- **Was blocking work added to the tick thread?** See the known problem above — an upstream timeout
  freezes the game.
- **Was anything under `generated/openapi/` edited?** It is overwritten on the next `compileJava`.

## Writing up work for this module

Beyond the affected files, an issue for this module needs to say:

- **Whether it touches the generated client.** If the change needs a new endpoint or field, the work
  starts in `:skymaster-server` — the mod side cannot be implemented first.
- **Whether it moves the Minecraft version**, which means both version homes and a `runClient` check,
  not just a build file edit.
- **Whether it changes what ships inside the jar**, so the `shipped` configuration is part of the
  scope rather than discovered at runtime.
- **Which source set the work lands in**, since `src/main` and `src/client` are not interchangeable.
