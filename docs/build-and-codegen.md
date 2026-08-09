# Build and codegen

How the Gradle build is wired, and why the pieces are arranged the way they are.

## Structure

- **`skymaster-server/build.gradle.kts` and `skymaster-mod/build.gradle.kts`.** Each module configures
  itself in full: the Java 25 toolchain, Spotless, JaCoCo, Sonar and the Mockito agent are declared in
  both files. There is no convention plugin and no `buildSrc`. Shared build behaviour is duplicated on
  purpose — two modules did not justify a plugin — so **a change to shared behaviour has to be made in
  both files**, and a new module has to copy the same blocks.
- **`build.gradle.kts` (root).** This holds only what is genuinely global: the `mavenCentral()`
  repository for all projects, and the `installGitHooks` and `printVersion` tasks. It declares the
  Spotless plugin with `apply false` so both modules resolve the same version from the catalog.
- **`gradle/libs.versions.toml`.** This is the version catalog. Every dependency and plugin version lives
  here; Dependabot updates it. Inline coordinates in a module build file bypass that entirely.
- **`settings.gradle.kts`.** This adds the FabricMC repository, needed to resolve Loom.

## The OpenAPI chain

This is the least obvious part of the build, and the source of the most confusing failures.

```
:skymaster-server:generateOpenApiDocs   boots the Spring app, scrapes /v3/api-docs, writes spec.json
        │  (published as the `openApiSpec` Gradle artifact)
        ▼
:skymaster-mod:openApiGenerate          generates a Java client from that spec
        ▼
:skymaster-mod:compileJava              compiles it as part of the mod's main source set
```

Consequences worth internalising:

- **Building the mod builds and boots the server.** `./gradlew build` starts a Spring application.
  That is expected, not a bug.
- **A server change can break the mod's compile.** If spec generation fails, the failure surfaces as
  missing classes in the mod.
- **Spec generation needs the springdoc endpoints reachable.** They are disabled in
  `application.yaml` and enabled again by `customBootRun` arguments purely for this task. Anything that
  blocks those paths, such as a security filter chain, breaks the whole build.
- **A random port is chosen per build** (`Random.nextInt(8080, 9090)`) so concurrent builds do not
  collide on 8080.

The generated sources land in a `generated` package on purpose. JaCoCo excludes
`**/generated/**`. The repository does not configure an equivalent Sonar source exclusion, so do
not assume generated code is excluded from Sonar analysis.

## Formatting

Spotless with `palantirJavaFormat`, configured in each module's `build.gradle.kts` and targeting
`src/**/*.java` **literally** rather than deriving from source sets. This is deliberate and required. The mod's `main` source set includes the generated
directory, so a source set derived target would make `spotlessCheck` depend on code generation,
which boots the Spring application. That previously turned a formatting check into a long build.

A `pre-push` hook runs `spotlessCheck` and, on failure, runs `spotlessApply` and rejects the push so
you commit the result. Install it once per clone with `./gradlew installGitHooks`, which sets
`core.hooksPath` to the tracked `.githooks` directory.

## Coverage

`jacocoTestReport` runs for both modules, excluding generated code. Only the **server** gates on it:
`check` depends on `jacocoTestCoverageVerification`, requiring 80% line and 70% branch overall plus
50% line per class. The mod has no gate. This is a known and accepted gap. Much of the mod is Minecraft glue
that cannot be unit tested, and a threshold picked without measuring first would either be
meaningless or get disabled the first time it blocked someone. See the scope note in issue 38.

## Testing setup

Both modules run Mockito as a `-javaagent` with `-Xshare:off`, wired through a separate module
`mockitoAgent` configuration. This is required for mocking final classes on modern JDKs; a new module
needs the same wiring or mocks fail at runtime with an agent warning.

## Deliberate oddities

- **`org.gradle.configuration-cache=false`** uses an explicit setting because Loom
  `1.17-SNAPSHOT` is unverified with the configuration cache. The Sonar
  plugin is compatible; Loom is the blocker.
- **`jar` is disabled in the server.** `bootJar` produces the artifact.
- **The mod's `shipped` configuration** contains dependencies that must be bundled into the mod jar. They are fed
  into Loom's `include`. Plain `implementation` compiles and runs in `runClient` but does not bundle,
  so the mod fails only on a real user's machine.
