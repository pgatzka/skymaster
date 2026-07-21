# SkyMaster

## Development

### Git Hooks

This project uses Git hooks to automatically check code formatting before every push. To set them up, run the following
Gradle task once after cloning the repository:

```bash
./gradlew installGitHooks
```

This installs a pre-push hook that runs the formatting check automatically. If your code doesn't meet the formatting
standards, the push will be blocked until the issues are resolved.

> **Note:** You only need to run this once per clone. On Windows, use `./gradlew.bat installGitHooks` instead of
`./gradlew installGitHooks`.