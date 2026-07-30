# SkyMaster knowledge base

Background knowledge that does not belong in `CLAUDE.md` — the *why* behind the rules, and enough
context to make good decisions without re-deriving the system every time.

`CLAUDE.md` files carry rules and are loaded automatically. These documents carry understanding and
are read on demand. If something here becomes a rule you must never break, it belongs in a
`CLAUDE.md` instead — or better, in enforcement.

## Map

| Document | Read it when |
| --- | --- |
| [architecture.md](architecture.md) | You need the shape of the system: modules, request flow, where a new feature goes |
| [build-and-codegen.md](build-and-codegen.md) | You are touching Gradle, the OpenAPI chain, formatting, coverage or Sonar |
| [ci-and-deployment.md](ci-and-deployment.md) | You are changing workflows, the Docker image, or anything that reaches production |
| [auth-and-identity.md](auth-and-identity.md) | You are working on the handshake, login, tokens, or trusting client data |
| [pitfalls.md](pitfalls.md) | Something failed and the error does not explain itself |

## Related

- GitHub issues — settled designs with their rationale. Check before proposing a new one.
- `issues/` — staging area for drafts awaiting filing. Created on demand, not carried when empty.
- `skymaster-server/CLAUDE.md`, `skymaster-mod/CLAUDE.md` — module-local rules.

## Conventions

So this stays navigable rather than becoming a wall of bullets:

- **One topic per file.** If a document starts covering two things, split it and update the map.
- **Lead each section with what it is about**, so a reader skimming headings can tell whether to
  keep going.
- **Pitfalls are symptom-first.** The reader has an error message, not a diagnosis — the heading
  should be what they saw.
- **Explain the reasoning, not just the conclusion.** A rule without its rationale gets "cleaned up"
  by the next person who does not know why it exists.
- **Link, don't duplicate.** One home per fact, referenced from elsewhere.
