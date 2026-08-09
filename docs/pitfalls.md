# Pitfalls

Start with the symptom. Find what you saw, then read the cause.

Add to this file when something costs you more than a few minutes to work out, or when the same
mistake happens twice.

## Build

### `:skymaster-mod:compileJava` fails with missing `generated.openapi` classes

**Cause:** the server's spec generation failed, so the mod had nothing to generate a client from. The
mod's error is a symptom; the real failure is upstream.

**Fix:** run `./gradlew :skymaster-server:generateOpenApiDocs` and read *that* failure. Common
reasons: the app fails to start, or the springdoc endpoints are unreachable. See
[build-and-codegen.md](build-and-codegen.md).

### `./gradlew build` starts a Spring application

**Cause:** not a bug. Spec generation boots the app to scrape `/v3/api-docs`, and the mod's codegen
depends on the result.

### `spotlessCheck` suddenly takes minutes

**Cause:** the Spotless target was changed to derive from source sets. The mod's `main` source set
includes the generated directory, so the check now depends on codegen, which boots the server.

**Fix:** keep `target("src/**/*.java")` literal in the `spotless` block of each module's
`build.gradle.kts`.

### A build setting applies to one module but not the other

**Cause:** there is no convention plugin. The toolchain, Spotless, JaCoCo, Sonar and the Mockito agent
are declared separately in `skymaster-server/build.gradle.kts` and `skymaster-mod/build.gradle.kts`,
so a change made in one file silently leaves the other behind.

**Fix:** apply shared build changes to both files. See
[build-and-codegen.md](build-and-codegen.md).

### Push rejected by the formatting hook

**Cause:** `spotlessCheck` failed. The hook already ran `spotlessApply` for you.

**Fix:** commit the resulting changes and push again.

### Mockito fails to mock, or warns about a missing agent

**Cause:** the module lacks the `mockitoAgent` configuration and the `-javaagent` / `-Xshare:off`
test JVM arguments.

**Fix:** copy the wiring from either module's `build.gradle.kts`.

### A Gradle task fails only in a fresh clone

**Cause:** the `.githooks` path is not configured, or Java 25 is not the active JDK.

**Fix:** `./gradlew installGitHooks`, and check `java -version`.

---

## Runtime and deployment

### Every deploy rolls back, application logs look fine

**Cause:** the container never reported Docker `healthy`, so `deploy.sh` reverted to the previous
release. Almost always `/actuator/health` became unreachable, slow, or authenticated.

**Fix:** keep that endpoint unauthenticated and cheap. See
[ci-and-deployment.md](ci-and-deployment.md).

### The whole build breaks after a security or actuator change

**Cause:** the springdoc paths are no longer reachable, so `generateOpenApiDocs` cannot produce
`spec.json`, and the mod compile depends on it. The error appears in the mod, far from the change.

**Fix:** permit the springdoc paths in the filter chain.

### Handshakes stop working for everyone after a server release

**Cause:** the version check requires exact equality with the server's build version, and the mod
disables handshakes permanently on `426`.

**Fix:** tracked in #39.

---

## Mod

### Works in `runClient`, `NoClassDefFoundError` on a real install

**Cause:** the dependency is on `implementation` instead of `shipped`. It resolves in the dev runtime
but is not bundled into the jar, so only real users see the failure.

**Fix:** move it to the `shipped` configuration, which feeds Loom's `include`.

### Everyone's config option reset to its default

**Cause:** a MoulConfig field was renamed. Persistence keys on the field name, so the old value no
longer matches and the default is used.

**Fix:** avoid renaming persisted fields; if you must, say so in the release notes.

### A config option does not persist, or does not appear in the UI

**Cause:** missing `@Expose` (persistence) or missing `@ConfigOption` plus an editor annotation (UI).
Both are required.

### The mod refuses to load against the version it was built for

**Cause:** `fabric.mod.json`'s `depends` block duplicates versions from `gradle/libs.versions.toml`
and the two drifted.

**Fix:** update both together.

### The game stutters or freezes

**Cause:** blocking network calls on the client tick thread. `DataCollectionModule.onEndTick`
currently does this; an upstream timeout freezes rendering for its full duration.

**Fix:** do not add blocking work there. Moving the existing calls off the tick thread is part of
issue 33.

### Code in `src/main` cannot see Minecraft client classes

**Cause:** Loom's `splitEnvironmentSourceSets()` puts client classes only on the `src/client`
classpath. Code misplaced in `main` also escapes the mod coverage report. The repository does not
configure Sonar to analyze only the client source set.

**Fix:** put it in `src/client/java`.
