# Build and codegen

How the Gradle build is wired, and why the pieces are arranged the way they are.

## Structure

- **`buildSrc/src/main/kotlin/java-module.gradle.kts`** — the convention plugin both modules apply.
  Owns the Java 25 toolchain, Spotless, JaCoCo and Sonar. Change shared build behaviour here rather
  than in a module.
- **`gradle/libs.versions.toml`** — the version catalog. Every dependency and plugin version lives
  here; Dependabot updates it. Inline coordinates in a module build file bypass that entirely.
- **`settings.gradle.kts`** — adds the FabricMC repository, needed to resolve Loom.

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
  `application.yaml` and re-enabled by `customBootRun` arguments purely for this task. Anything that
  blocks those paths — a security filter chain, for instance — breaks the whole build.
- **A random port is chosen per build** (`Random.nextInt(8080, 9090)`) so concurrent builds do not
  collide on 8080.

The generated sources land in a `generated` package on purpose: both the JaCoCo exclusions and
Sonar's analysis filter on `**/generated/**`.

## Formatting

Spotless with `palantirJavaFormat`, targeting `src/**/*.java` **literally** rather than deriving from
source sets. This is deliberate and load-bearing: the mod's `main` source set includes the generated
directory, so a source-set-derived target would make `spotlessCheck` depend on codegen — which boots
the Spring app. That turned a formatting check into a multi-minute job once already.

A `pre-push` hook runs `spotlessCheck` and, on failure, runs `spotlessApply` and rejects the push so
you commit the result. Install it once per clone with `./gradlew installGitHooks`, which sets
`core.hooksPath` to the tracked `.githooks` directory.

## Coverage

`jacocoTestReport` runs for both modules, excluding generated code. Only the **server** gates on it:
`check` depends on `jacocoTestCoverageVerification`, requiring 80% line and 70% branch overall plus
50% line per class. The mod has no gate — a known and accepted gap. Much of the mod is Minecraft glue
that cannot be unit tested, and a threshold picked without measuring first would either be
meaningless or get disabled the first time it blocked someone. See the out-of-scope note in #38.

## Testing setup

Both modules run Mockito as a `-javaagent` with `-Xshare:off`, wired through a per-module
`mockitoAgent` configuration. This is required for mocking final classes on modern JDKs; a new module
needs the same wiring or mocks fail at runtime with an agent warning.

## Deliberate oddities

- **`org.gradle.configuration-cache=false`** — Loom `1.17-SNAPSHOT` is unverified with it. The Sonar
  plugin is compatible; Loom is the blocker.
- **`jar` is disabled in the server** — `bootJar` produces the artifact.
- **The mod's `shipped` configuration** — dependencies that must be bundled into the mod jar are fed
  into Loom's `include`. Plain `implementation` compiles and runs in `runClient` but does not bundle,
  so the mod fails only on a real user's machine.
