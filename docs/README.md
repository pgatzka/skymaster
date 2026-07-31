# SkyMaster knowledge base

This documentation explains why the project is structured as it is and gives maintainers enough
context to make changes without rediscovering the same behavior.

## Map

| Document | Read it when |
| --- | --- |
| [architecture.md](architecture.md) | You need the shape of the system: modules, request flow, where a new feature goes |
| [build-and-codegen.md](build-and-codegen.md) | You are touching Gradle, the OpenAPI chain, formatting, coverage or Sonar |
| [ci-and-deployment.md](ci-and-deployment.md) | You are changing workflows, the Docker image, or anything that reaches production |
| [auth-and-identity.md](auth-and-identity.md) | You are working on the handshake, login, tokens, or trusting client data |
| [pitfalls.md](pitfalls.md) | Something failed and the error does not explain itself |

## Related

- GitHub issues contain settled designs and their rationale. Check them before proposing a new one.
- The root [`README.md`](../README.md) contains setup instructions and the development entry point.

## Conventions

Use these conventions to keep the documentation navigable.

- **One topic per file.** If a document starts covering two things, split it and update the map.
- **Lead each section with what it is about**, so a reader skimming headings can tell whether to
  keep going.
- **Pitfalls are symptom first.** The reader has an error message, not a diagnosis. The heading
  should be what they saw.
- **Explain the reasoning, not just the conclusion.** A rule without its rationale gets "cleaned up"
  by the next person who does not know why it exists.
- **Link instead of duplicating.** Keep one home per fact and reference it from elsewhere.
