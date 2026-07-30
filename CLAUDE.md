# SkyMaster

Two-module Gradle build: a Fabric Minecraft client mod that reports Hypixel SkyBlock data to a
Spring Boot server.

- `skymaster-server` — Spring Boot 4 REST API. Publishes its OpenAPI spec as a Gradle artifact.
- `skymaster-mod` — Fabric mod for Minecraft 26.1.2. Consumes that spec to generate its HTTP client.

Each module has its own `CLAUDE.md` with the traps specific to it. Read that one before working in a
module — most of what bites is module-local.

## Where knowledge lives

| Need | Go to |
| --- | --- |
| Why something is built this way | `docs/README.md` — index of the knowledge base |
| Something failed and the error makes no sense | `docs/pitfalls.md` — symptom first |
| What we already decided to build | GitHub issues — `gh issue list` |
| Module-specific rules | `skymaster-server/CLAUDE.md`, `skymaster-mod/CLAUDE.md` |

Check the open issues before proposing a design. Several decisions are already settled and carry
full rationale — the Hypixel verification contract (#31), the token login and why the Minecraft
session id must never be sent (#33), rate limiting (#34) and the version policy (#39). #32 tracks
the auth chain and its ordering. Do not relitigate these.

`issues/` is a staging area for drafts awaiting the `/create-issue` flow. Create it when writing a
draft — it is not carried in the repository while empty. A file there means a draft not yet filed.

## Commands

```bash
./gradlew build                    # compile + test + coverage gate, both modules
./gradlew spotlessCheck            # formatting gate (pre-push hook runs this)
./gradlew spotlessApply            # fix formatting
./gradlew :skymaster-server:test   # server tests only
./gradlew :skymaster-mod:test      # mod tests only
./gradlew :skymaster-server:bootRun  # run the server (port 8080)
./gradlew :skymaster-mod:runClient   # launch Minecraft with the mod (DevAuth handles login)
./gradlew installGitHooks          # once per clone — points core.hooksPath at .githooks
```

Java 25 toolchain, and Java 25 must be on `PATH` — the Gradle wrapper and the format check both run
on it.

## Always / never

- Add dependencies to `gradle/libs.versions.toml` and reference them as `libs.*`. No inline
  coordinates in `build.gradle.kts`.
- Format with `palantirJavaFormat` via Spotless — run `./gradlew spotlessApply`, don't hand-format.
- Spotless targets `src/**/*.java` literally rather than deriving from source sets. Keep it that way:
  a source-set target makes `spotlessCheck` depend on codegen, which boots the Spring app and turns a
  formatting check into a multi-minute job.
- Never edit anything under a `generated/` package or `build/`. It is regenerated on every
  `compileJava`.
- `org.gradle.configuration-cache` is off deliberately — Loom `1.17-SNAPSHOT` is unverified with it.
- Don't commit or push. Write files and hand back.

## OpenAPI contract

`:skymaster-server:generateOpenApiDocs` boots the app and writes `spec.json`; `:skymaster-mod`
consumes it through the `openApiSpec` configuration and generates its client from it. Server request
and response types are the source of truth for the wire contract — change those, not the mod side.

This coupling means server changes can break the mod's compile step. See `docs/build-and-codegen.md`.
