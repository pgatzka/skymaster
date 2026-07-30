---
name: capture-pitfall
description: Record a lesson learned so it is not rediscovered next session. Use when something went wrong, took too long to diagnose, or when the user corrected the same project fact twice. Routes the lesson to the right layer — enforcement, a CLAUDE.md rule, the docs knowledge base, or an issue — instead of piling everything into CLAUDE.md.
---

# Capture a pitfall

Something cost time or went wrong. Write it down where it will actually be found, in the form that
prevents a repeat.

## 1. Establish what actually happened

If it is not already clear from the conversation, ask. A vague entry is worse than none — it takes up
context every session and helps nobody. You need:

- **The symptom** — the error message or observed behaviour, in the words someone would search for.
- **The cause** — what was actually wrong, not what it looked like.
- **The fix or the rule** — what to do instead.

Do not record something already covered. Check the existing files first and update the entry that is
already there rather than adding a near-duplicate.

## 2. Route it

Work down this list and stop at the first match. Prefer the earliest option that genuinely fits —
enforcement beats a rule, and a module rule beats a project-wide one.

| If it is… | Put it in |
| --- | --- |
| Something that should be **impossible**, not merely discouraged | `.claude/settings.json` — a hook or permission rule. Use the `update-config` skill. |
| A hard rule that applies **only to one module** | `skymaster-server/CLAUDE.md` or `skymaster-mod/CLAUDE.md`, under *Always / never* |
| A hard rule that applies to **everything** | `CLAUDE.md` — *Always / never*. High bar: this file loads every session. |
| A **failure you had to diagnose** | `docs/pitfalls.md`, symptom-first, in the right section |
| **Background understanding** — why the system is like this | the matching topic file in `docs/`, or a new one |
| A **decision about future work** | a draft in `issues/<title>.md`, filed later via `/create-issue`; or a comment on the existing issue if one covers it |

Two things do **not** belong anywhere here: anything already obvious from the code or `README.md`,
and anything specific to one conversation.

Ambiguous cases are common — a symptom often implies a rule too. Recording the symptom in
`docs/pitfalls.md` *and* a one-line rule in the relevant `CLAUDE.md` is fine when both genuinely earn
their place. Duplicating the same paragraph in two files is not; link instead.

## 3. Write it in the house style

**`docs/pitfalls.md`** — the heading is what the reader saw, not what was wrong:

```markdown
### Every deploy rolls back, application logs look fine

**Cause:** the container never reported Docker `healthy`, so `deploy.sh` reverted.

**Fix:** keep `/actuator/health` unauthenticated and cheap.
```

**A `CLAUDE.md` rule** — imperative, one or two lines, and it must carry its reason. A rule without a
rationale gets "cleaned up" by the next person who does not know why it exists:

```markdown
- Spotless targets `src/**/*.java` literally rather than deriving from source sets. Keep it that way:
  a source-set target makes `spotlessCheck` depend on codegen, which boots the Spring app.
```

**A `docs/` topic file** — prose explaining the reasoning. If you create a new file, add it to the
map in `docs/README.md`.

## 4. Confirm

Say which file you wrote to and why that layer, in one line. If you chose enforcement, say what is
now blocked.

Do not commit. Write the files and hand back.
