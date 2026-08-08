# CI and deployment

How code gets from a pull request to a running container, and what will bite you when changing it.

## CI (`.github/workflows/ci.yml`)

Four jobs, on push to `main`, pull requests, and manual dispatch:

- **Verify.** Runs `./gradlew spotlessCheck` for fast formatting feedback.
- **Build.** Runs `./gradlew build`, then Sonar analysis for each module if `SONAR_TOKEN` is present.
  Uploads both jars as artifacts.
- **Build Image.** Downloads the server jar artifact and builds the Docker image. Pushes to GHCR
  only on `main`, tagged `sha-<short>` and `snapshot`.
- **CI Gate.** A single required check that fails if any dependency failed or was cancelled. This is
  the job to protect the branch on; adding a new job means adding it to the gate's `needs`, or its
  failures are invisible. The one exception is the Claude review (`claude-review.yml`): it is
  advisory by design and deliberately outside the gate, because putting a model's judgement in
  `needs` would turn advice into a merge blocker.

`deploy-ci` then runs on `main`, calling the deploy workflow with the `snapshot` tag.

Permissions start at `{ }` with no access and are escalated per job. Keep that pattern when adding
jobs.

The image build does **not** rebuild the jar. It consumes the artifact from the Build job, which is
why the Dockerfile expects `build/libs/*.jar` to already exist.

## Deployment (`.github/workflows/deploy.yml` + `.github/scripts/deploy.sh`)

Runs on a runner hosted by the project and labelled with the target environment. Callable manually with a chosen
tag and environment, or automatically from CI for `ci`.

`deploy.sh` does more than `docker compose up`:

1. Resolves the image and reads its `org.opencontainers.image.revision` label.
2. Downloads the compose files **at that exact commit**, so configuration and image always match
   instead of using whatever is currently on `main`.
3. Writes them into a timestamped release directory under a persistent path for each environment, with
   `current` and `previous` symlinks.
4. Starts the stack and polls until the container reports Docker `healthy`.
5. **Rolls back to the previous release if the health gate times out.**
6. Prunes old releases beyond `KEEP_RELEASES`.

Almost all of its inputs come from GitHub environment variables and secrets rather than the
repository, so the same script serves `ci` and `production` with different values.

### The health gate controls rollback

`.github/deployment/docker-compose.ci.yml` checks
`http://localhost:8080/actuator/health` every 10 seconds. That
single endpoint decides whether a release stands or is rolled back. Anything that makes it
unreachable, slow, or authenticated causes every deploy to fail and revert, with an error that
looks like a deployment problem rather than an application change. Keep it unauthenticated and cheap.

## Supporting workflows

- **`dependency-review.yml`.** Reviews dependency changes on pull requests.
- **`claude-review.yml`.** Posts an advisory Claude review on pull requests against `main`, judged
  against the `docs/` knowledge base rather than generic style advice. A review is always posted,
  in the fixed format specified in the workflow prompt: the linked issue's acceptance criteria as
  a checklist, blocking requested changes, non-blocking suggestions, and a verdict — findings
  only, no praise. Skips drafts and bot-authored
  pull requests — a daily Dependabot bump does not need a conventions review. Deliberately absent
  from `ci-gate`'s `needs`; see the CI section for why. On fork pull requests the job no-ops:
  `pull_request` does not expose `CLAUDE_CODE_OAUTH_TOKEN` to forks, and that is the accepted trade
  over `pull_request_target`, which would run fork code with the repository's secrets in scope.
- **`claude.yml`.** Answers `@claude` mentions in issue comments, review comments and reviews, and
  issues that mention `@claude` when opened or assigned. The job runs only when the author's
  association is `OWNER`, `MEMBER` or `COLLABORATOR`, so a drive-by mention cannot spend the
  project's budget. It comments only — `contents` stays read-only, so it never pushes.
- **`pr-decorator.yml`.** Rewrites PR titles from the branch name and builds the body from the
  commit list. This is why branches are named for what they do.
- **`dependabot.yml`.** Gradle daily, GitHub Actions weekly. The Actions entry exists specifically
  so pinned action SHAs get moved; pinning without a bot just means stale pins.

## Conventions

- Actions are pinned by commit SHA rather than tag where possible. A tag is a mutable pointer, and
  these workflows hold `packages: write` and use a runner hosted by the project. Making this consistent is
  tracked in #29.
- Java setup and Gradle caching live in the composite action at
  `.github/actions/setup-environment`. Change provisioning there, not per workflow.
