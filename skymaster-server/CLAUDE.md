# skymaster-server

Spring Boot 4 REST API. Receives handshakes from the mod and, in future, collected game data.
Publishes its OpenAPI spec as a Gradle artifact that `:skymaster-mod` generates its client from.

Read `docs/architecture.md` for how the modules fit together, `docs/pitfalls.md` when something fails
unexpectedly. Start at `docs/README.md`.

## Layering

A REST feature is four files, and new endpoints are expected to follow the same split:

- `rest/endpoint/` — the annotated interface: mappings, `@Valid`, media types. No logic.
- `rest/controller/` — implements the interface, delegates to a service, logs the outcome.
- `rest/service/` — the logic. Throws domain exceptions.
- `rest/request/` — request records with Jakarta validation annotations.
- `rest/exception/` — domain exceptions, mapped to statuses in `RestExceptionHandler`.

Keeping mappings on the interface is what lets the controller stay free of annotations and keeps the
generated OpenAPI spec accurate.

## Always / never

- Coverage is gated: `check` depends on `jacocoTestCoverageVerification`, requiring 80% line and 70%
  branch overall plus 50% line on every class. New code needs tests or the build fails.
- Lombok is available and used — `@Slf4j`, `@RequiredArgsConstructor`. Prefer constructor injection
  via `@RequiredArgsConstructor` over field injection.
- Request types are records with validation annotations, tested through `RequestValidationTest<R>`.
  Subclass it rather than hand-rolling validator setup.
- `jar` is disabled; `bootJar` produces the artifact. The Dockerfile expects it prebuilt at
  `build/libs/*.jar` — CI downloads it as an artifact rather than rebuilding.

## Two changes that break things far away

Both are easy to trip while editing security or actuator configuration:

- **`/actuator/health` must stay reachable unauthenticated.** `docker-compose.ci.yml` probes it and
  `.github/scripts/deploy.sh` gates the release on it reaching `healthy`. Lock it down and every
  deploy rolls back.
- **The springdoc paths must stay reachable when enabled.** `springdoc.api-docs.enabled` is `false`
  in `application.yaml` but `customBootRun` turns it on so `generateOpenApiDocs` can write
  `spec.json` — and `:skymaster-mod:compileJava` depends on that file. Lock it down and
  `./gradlew build` fails for the whole project, with an error that points at the mod.

## Testing

- Slice tests, not full context boots: `@WebMvcTest` for controllers, standalone MockMvc for
  `RestExceptionHandler`, plain `@ExtendWith(MockitoExtension.class)` for services.
- Mockito runs as a `-javaagent` with `-Xshare:off`, wired through the `mockitoAgent` configuration.
  A new module needs the same setup to mock finals.
- No test may require network access or a real Hypixel key.

## Reviewing a change here

Go looking for these — none of them announce themselves in a diff:

- **Does a new endpoint follow the four-file split?** Mappings on the interface, no logic in the
  controller, no annotations leaking into the service.
- **Do request records carry `@Valid` and validation annotations, with a `RequestValidationTest<R>`
  subclass covering them?** A record without one passes review and fails in production.
- **Does new code have tests?** The gate is 80% line and 70% branch overall plus 50% line per class.
  A class added without tests can pass locally and still drop the project below the threshold.
- **Does the change touch security or actuator configuration?** If so, check both far-away breakages
  above — `/actuator/health` and the springdoc paths. Neither failure surfaces in this module.
- **Does it change a request or response type?** That is the wire contract; see below.

## Writing up work for this module

Beyond the affected files, an issue for this module needs to say:

- **Whether the wire contract changes.** Any change to a request or response type regenerates the
  mod's client and can break `:skymaster-mod:compileJava`. Say so explicitly and name the mod-side
  work, or the issue looks server-local when it is not.
- **Which endpoints and status codes are involved**, since the four-file split means one behaviour
  change usually spans four files plus a test.
- **Whether it needs new configuration**, and what the default is in `application.yaml`.
