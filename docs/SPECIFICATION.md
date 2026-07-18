# SkyMaster Repository Specification Audit

## Purpose

This document records the observable SkyMaster repository contract and the complete review baseline for pull request 8. It separates confirmed behavior from proposed behavior so a reviewer can understand what exists, what changes, what must remain compatible, and how to verify the result.

This audit uses `main` at commit `f9025f962f6f0de27ccaefba4b916950431788a5` as the approved baseline. It uses pull request 8 at commit `ad00f00f6310d54f9f9c04b439fc5ed5131a33ff` as the proposed change.

## Audit Scope

The audit covered the following areas.

1. Every tracked source file on `main`.

2. Every file changed by pull request 8.

3. The Gradle module graph and generated OpenAPI client flow.

4. Java formatting and static analysis configuration.

5. GitHub workflows for continuous integration, dependency review, code scanning, releases, and pull request decoration.

6. Local branches, remote branches, tags, and commit history.

7. The handshake request, response, validation, failure, and retry behavior.

8. Existing screen collection and ping behavior that pull request 8 changes or removes.

9. Available automated verification and missing test coverage.

## Repository Baseline

The default branch is `main`. At the time of this audit, the remote contains two branches.

1. `main` points to `f9025f962f6f0de27ccaefba4b916950431788a5`.

2. `pgatzka/handshake-setup` points to `ad00f00f6310d54f9f9c04b439fc5ed5131a33ff`.

There are no repository tags. There are no tracked project documentation files. There are no test source files. GitHub reports pull request 8 as open, mergeable, and targeted at `main`.

## Project Architecture

SkyMaster is a Java 25 Gradle project with three modules.

### Server Module

The `skymaster-server` module is a Spring Boot service. It owns HTTP controllers, request validation, service behavior, error responses, and OpenAPI specification generation.

The server is also the source of truth for the generated API contract. During the build, Spring starts on a temporary port and Springdoc writes `spec.json` into the server build directory.

### API Module

The `skymaster-api` module consumes the server OpenAPI specification. OpenAPI Generator creates a Java client from that specification. The handwritten `API` class wraps generated controllers so the mod does not call generated code throughout its own source tree.

### Mod Module

The `skymaster-mod` module is a Fabric client mod for Minecraft. It depends on the API module for compilation and includes that API inside the production mod jar so the generated client is available at run time.

The module uses split Fabric source sets. Shared initialization lives in the main source set. Minecraft client behavior lives in the client source set.

## Build Contract

The project uses Gradle Kotlin scripts and the Gradle wrapper. Java compilation targets Java 25.

The build order is significant.

1. The server compiles and generates its OpenAPI specification.

2. The API module validates that specification and generates Java client sources.

3. The mod compiles against the generated client and packages it into the mod jar.

4. The normal Gradle `build` task runs checks, including Spotless and JaCoCo report generation.

5. GitHub continuous integration builds the server jar and mod jar.

6. GitHub continuous integration builds the server Docker image from the produced server jar.

The project version comes from `gradle.properties`. The audited version is `1.0.0-SNAPSHOT`. A published GitHub release overrides that value with the release tag version.

## Code Style Contract

Spotless applies Palantir Java Format to tracked Java source files. Generated OpenAPI sources are intentionally excluded from the direct Spotless target.

Server classes use Spring annotations and Lombok where constructor injection or logging is needed. Endpoint interfaces own HTTP mapping annotations. Controller classes implement those interfaces. Service classes own behavior that is not limited to HTTP translation.

The repository currently contains explanatory comments in Gradle and GitHub workflow files. Those comments document build ordering, cache behavior, artifact handling, and release decisions. New code should preserve this preference for explicit explanations when behavior is not obvious.

## Branch And Workflow Contract

The observed integration branch is `main`. Feature work is proposed through a named branch that targets `main`.

Pull request 8 uses `pgatzka/handshake-setup` as its feature branch. The pull request decorator sets a pull request title to the feature branch name and generates its description from commit summaries. On this branch, the decorator runs when the pull request opens and whenever new commits synchronize.

The continuous integration workflow runs for pull requests targeting `main` and for pushes to `main`. It builds both deliverables, builds the Docker image, and reports one aggregate gate.

The dependency review workflow runs for pull requests targeting `main`. CodeQL runs on pushes to `main`, on a weekly schedule, and when started manually.

The release workflow runs when a GitHub release is published or when started manually. It builds the server, publishes the server image, and publishes the mod package. Deployment jobs and Modrinth publication are placeholders and are disabled.

## Approved Main Behavior

The approved `main` branch exposes two server contracts.

1. The ping contract returns a pong response so the mod can verify that the server is reachable.

2. The screen contract receives collected SkyBlock container data and stores a formatted JSON file under the server storage directory.

The approved client performs these actions.

1. It pings the server during client initialization.

2. It avoids registering collection behavior when the server cannot be reached.

3. It detects whether the player is in SkyBlock by reading the sidebar objective.

4. It detects newly opened container screens.

5. It converts container slots, item names, counts, and lore into an API request.

6. It queues requests and sends them at a controlled rate.

## Pull Request 8 Proposed Behavior

Pull request 8 replaces the ping and screen contracts with a handshake contract. It also introduces a client module interface and a data collection module.

The proposed client performs this flow.

1. Fabric initializes `SkyMasterClientMod`.

2. The mod initializes registered modules in numeric order.

3. `DataCollectionModule` registers an end of tick callback.

4. The first callback creates a handshake request.

5. The request contains the Minecraft username, profile UUID, and mod version.

6. The generated API client sends the request to `localhost` on port `8080`.

7. A successful response marks the handshake successful.

8. Any exception marks the handshake unsuccessful.

The proposed server performs this flow.

1. Spring validates the request fields.

2. `HandshakeService` compares the mod version with the server build version.

3. An exact match returns status 200.

4. A mismatch throws `VersionMismatchException` and returns status 426.

5. The general exception handler returns status 500 for other handled exceptions.

## Handshake Data Contract

The request contains three required string fields.

1. `uuid` must be non empty and must match the configured UUID pattern.

2. `username` must be non empty.

3. `version` must be non empty and must match the configured numeric version pattern with an optional snapshot suffix.

The server currently performs only version compatibility validation. It does not create a session, return a token, store the player identity, or authorize later data requests.

## Review Findings

### Finding 1. Existing Collection Behavior Is Removed

Pull request 8 deletes the ping controller, screen controller, screen request model, screen storage service, and matching API methods. It also removes all screen parsing and queue behavior from the client.

The replacement `DataCollectionModule` ends after the handshake with a collection todo. Merging the pull request as written would remove working screen collection rather than place it behind the new handshake.

This is a blocking behavior regression unless the removal is an explicitly approved product decision.

### Finding 2. A Temporary Failure Becomes Permanent

The client stores handshake state in `handshakeSuccessful`. The first exception sets this value to false. Every later tick returns immediately without another handshake attempt.

A server that starts late, a brief network interruption, or one temporary server error therefore disables collection until Minecraft restarts. The client needs a controlled retry policy. The policy should prevent requests every tick while still allowing recovery.

This is a blocking reliability issue.

### Finding 3. Request Validation Can Return The Wrong Status

The server advice handles every `Exception` and returns status 500. Spring request validation failures are exceptions. Without a specific validation handler, malformed client input can be reported as a server failure instead of a client error.

The server needs a specific validation response, normally status 400, before the general handler is used.

This is a blocking API contract issue.

### Finding 4. The Pre Push Hook Is Not Active

Pull request 8 adds `.githooks/pre-push`, but the Git object mode is `100644`. Git hooks must be executable on systems that honor executable mode.

The repository also contains no setup that configures `core.hooksPath` to use `.githooks`. The hook therefore does not run in a normal clone.

The hook must be executable, and setup instructions or automation must enable the hook path.

### Finding 5. The Handshake Has No Tests

The repository has no test source files. Pull request 8 adds validation, version comparison, exception translation, and state transitions without automated coverage.

At minimum, tests should cover a valid request, malformed fields, matching versions, mismatched versions, expected response statuses, a temporary connection failure, a later successful retry, and preservation of collection behavior after success.

### Finding 6. The Server Address Is Fixed

The client constructs the API with `localhost` and port `8080`. This is acceptable for a local prototype, but it prevents use with a remote or differently configured server.

The address should be documented as a prototype limitation or moved into client configuration before remote use is expected.

## Required Resolution Before Approval

Pull request 8 should satisfy these conditions before approval.

1. Preserve the approved ping and screen collection behavior, or provide explicit approval that these contracts may be removed.

2. Run collection only after a successful handshake.

3. Add a delayed and bounded retry path for temporary handshake failures.

4. Return a clear client error for malformed handshake requests.

5. Keep status 426 for version mismatches.

6. Add automated tests for the server contract and client handshake state.

7. Make the pre push hook executable and explain how a clone enables it.

8. Document whether `localhost` and port `8080` are temporary defaults or permanent requirements.

## Automated Verification Evidence

GitHub Actions evaluated pull request head `ad00f00f6310d54f9f9c04b439fc5ed5131a33ff`.

The continuous integration run `29656963640` completed successfully. Its build job, image job, and aggregate gate succeeded. The run produced the server jar, produced the mod jar, completed analysis, and built the Docker image.

The dependency review run `29656963677` completed successfully. The pull request decorator run `29656963673` also completed successfully.

`git diff --check` reported no whitespace errors for the pull request diff.

An independent local build was attempted from an isolated archive of the exact pull request head. The sandbox blocked Gradle from writing lock files under the user Gradle cache. Unsandboxed execution was not permitted because pull request build scripts are untrusted code. The clean GitHub runner is therefore the confirmed build result for this audit.

## Manual Verification Procedure

Use this procedure after the blocking findings are corrected.

### Prerequisites

1. Install a Java 25 development kit.

2. Clone the repository.

3. Check out the corrected feature branch.

4. Confirm that Docker is available if the image check will be repeated.

5. Use matching server and mod versions for the success case.

### Build Verification

1. Run `./gradlew clean build` from the repository root.

2. Confirm that the command exits successfully.

3. Confirm that the server jar exists under `skymaster-server/build/libs`.

4. Confirm that the mod jar exists under `skymaster-mod/build/libs`.

5. Run `./gradlew spotlessCheck`.

6. Confirm that Spotless reports no violations.

### Successful Handshake Verification

1. Start the server with `./gradlew :skymaster-server:bootRun`.

2. Start the mod client with `./gradlew :skymaster-mod:runClient`.

3. Join a playable Minecraft session.

4. Confirm that the client sends one handshake for the active player.

5. Confirm that the server returns status 200.

6. Confirm that collection begins only after the successful response.

7. Open a supported SkyBlock container.

8. Confirm that the screen data reaches the server and is stored as expected.

### Version Mismatch Verification

1. Start the server and client with different project versions.

2. Confirm that the server returns status 426.

3. Confirm that the response explains the required and actual versions.

4. Confirm that collection does not begin.

### Validation Verification

1. Send requests with a missing UUID, blank username, malformed UUID, and malformed version.

2. Confirm that each request returns status 400 instead of status 500.

3. Confirm that the server remains healthy after each rejected request.

### Recovery Verification

1. Start the Minecraft client while the server is unavailable.

2. Confirm that the first handshake fails without crashing the client.

3. Start the server without restarting Minecraft.

4. Wait for the documented retry delay.

5. Confirm that a later handshake succeeds.

6. Confirm that collection begins after recovery.

### Hook Verification

1. Confirm that `git ls-files -s .githooks/pre-push` reports executable mode `100755`.

2. Configure the clone with `git config core.hooksPath .githooks` if setup automation does not do so.

3. Introduce a temporary formatting violation in a safe test branch.

4. Attempt to push the branch.

5. Confirm that the hook blocks the push and applies formatting.

6. Restore the temporary test change before continuing.

## Approval Standard

A green build is necessary but is not sufficient. Approval requires preserved behavior, correct response statuses, recovery from temporary failures, executable developer tooling, and automated coverage of the handshake contract.
