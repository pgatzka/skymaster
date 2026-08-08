# SkyMaster

A Fabric client mod for Minecraft that reports Hypixel SkyBlock data to a Spring Boot server. The
build has two modules: `skymaster-server` (the REST API, which publishes its OpenAPI spec) and
`skymaster-mod` (the mod, which generates its HTTP client from that spec).

## Documentation

[`docs/README.md`](docs/README.md) is the documentation index. It links the architecture, build and
code generation, CI and deployment, authentication, identity, and troubleshooting guides.

Open GitHub issues carry the rationale for settled future work. Check them before proposing a
design.

Maintainers can mention `@claude` on any issue or pull request to get an answer from the
repository's Claude workflow, and pull requests receive an automatic advisory review; both are
described in [`docs/ci-and-deployment.md`](docs/ci-and-deployment.md).

## Development

### Requirements

- **Java 25** must be installed and active on your `PATH`. The formatting check and the Gradle wrapper both run on Java 25, so an older or newer JDK may cause the `installGitHooks` task or the push check to fail. Verify your version with:

```bash
java -version
```

### Git Hooks

This project uses Git hooks to automatically check code formatting before every push. To set them up, run the following Gradle task once after cloning the repository:

```bash
./gradlew installGitHooks
```

This installs a `pre-push` hook that runs the formatting check automatically. If your code does not meet the formatting standards, the push is blocked until the issues are resolved.

> **Note:** You only need to run this once per clone.

**Windows (PowerShell):**

```powershell
.\gradlew.bat installGitHooks
```

**Windows (Command Prompt):**

```cmd
gradlew.bat installGitHooks
```

#### How the hook is wired up

The `installGitHooks` task works by setting the `core.hooksPath` Git configuration value. `core.hooksPath` tells Git which directory to look in for hook scripts, overriding the default location of `.git/hooks`. Pointing it at a tracked directory lets the whole team share the same `pre-push` hook through version control instead of copying scripts into each local clone by hand.

#### Verifying the setup

To confirm the configuration was applied, run:

```bash
git config --get core.hooksPath
```

The expected value is:

```
.githooks
```

(This should match the directory the `installGitHooks` task writes to. Adjust the expected value here if your build uses a different path.) If the command prints nothing, `core.hooksPath` is not set and the hook will not run.

#### Common failures

- **The push is not blocked and no formatting check runs.** `core.hooksPath` is probably unset or pointing at the wrong directory. Rerun `./gradlew installGitHooks` and verify with the command above.
- **The `installGitHooks` task is not found.** Run the command from the repository root, and make sure you are using the Gradle wrapper (`./gradlew`) rather than a system Gradle install.
- **The formatting check itself errors out.** This is usually a Java version mismatch. Confirm Java 25 is active with `java -version`.
- **A global hooks path overrides the project one.** If you previously set `core.hooksPath` globally, that value can take precedence. Check with `git config --global --get core.hooksPath` and unset it if needed.

#### Restoring or removing the setting

To remove the project hooks path entirely (for example, to fall back to Git's default `.git/hooks` behavior):

```bash
git config --unset core.hooksPath
```

If you had a previous value you want to restore, set it explicitly instead:

```bash
git config core.hooksPath <your-previous-value>
```

All three `git config` commands above are identical across PowerShell and Command Prompt on Windows, so no shell specific variant is needed.
