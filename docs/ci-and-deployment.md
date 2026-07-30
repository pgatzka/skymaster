# CI and deployment

How code gets from a pull request to a running container, and what will bite you when changing it.

## CI (`.github/workflows/ci.yml`)

Four jobs, on push to `main`, pull requests, and manual dispatch:

- **Verify** — `./gradlew spotlessCheck`. Formatting only, fast feedback.
- **Build** — `./gradlew build`, then Sonar analysis for each module if `SONAR_TOKEN` is present.
  Uploads both jars as artifacts.
- **Build Image** — downloads the server jar artifact and builds the Docker image. Pushes to GHCR
  only on `main`, tagged `sha-<short>` and `snapshot`.
- **CI Gate** — a single required check that fails if any dependency failed or was cancelled. This is
  the job to protect the branch on; adding a new job means adding it to the gate's `needs`, or its
  failures are invisible.

`deploy-ci` then runs on `main`, calling the deploy workflow with the `snapshot` tag.

Permissions start at `{ }` — nothing — and are escalated per job. Keep that pattern when adding jobs.

The image build does **not** rebuild the jar. It consumes the artifact from the Build job, which is
why the Dockerfile expects `build/libs/*.jar` to already exist.

## Deployment (`.github/workflows/deploy.yml` + `.github/scripts/deploy.sh`)

Runs on a self-hosted runner labelled with the target environment. Callable manually with a chosen
tag and environment, or automatically from CI for `ci`.

`deploy.sh` does more than `docker compose up`:

1. Resolves the image and reads its `org.opencontainers.image.revision` label.
2. Downloads the compose files **at that exact commit**, so configuration and image always match —
   not whatever is currently on `main`.
3. Writes them into a timestamped release directory under a persistent per-environment path, with
   `current` and `previous` symlinks.
4. Starts the stack and polls until the container reports Docker `healthy`.
5. **Rolls back to the previous release if the health gate times out.**
6. Prunes old releases beyond `KEEP_RELEASES`.

Almost all of its inputs come from GitHub environment variables and secrets rather than the
repository, so the same script serves `ci` and `production` with different values.

### The health gate is load-bearing

`docker-compose.ci.yml` health-checks `http://localhost:8080/actuator/health` every 10 seconds. That
single endpoint decides whether a release stands or is rolled back. Anything that makes it
unreachable, slow, or authenticated causes every deploy to fail and revert — with an error that
looks like a deployment problem rather than an application change. Keep it unauthenticated and cheap.

## Supporting workflows

- **`dependency-review.yml`** — reviews dependency changes on pull requests.
- **`pr-decorator.yml`** — rewrites PR titles from the branch name and builds the body from the
  commit list. This is why branches are named for what they do.
- **`dependabot.yml`** — Gradle daily, GitHub Actions weekly. The Actions entry exists specifically
  so pinned action SHAs get moved; pinning without a bot just means stale pins.

## Conventions

- Actions are pinned by commit SHA rather than tag where possible. A tag is a mutable pointer, and
  these workflows hold `packages: write` and run on a self-hosted runner. Making this consistent is
  tracked in #29.
- Java setup and Gradle caching live in the composite action at
  `.github/actions/setup-environment`. Change provisioning there, not per workflow.
