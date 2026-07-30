---
description: Create a well-researched GitHub issue with correct labels and linked relations
---

You are creating a GitHub issue for this repository. The user's request:

$ARGUMENTS

If the request above is empty, ask the user what the issue should be about
and stop.

General rule for this entire command: whenever you need a decision or
confirmation from the user, use the AskUserQuestion tool. Never bury a
question in prose and never proceed on silence, partial answers, or your
own judgment of what the user "probably" wants.

The read-heavy work — reading the code, searching the issue landscape,
writing the draft — belongs to the `issue-author` agent and runs in its own
context. Everything the user has to decide stays here. Do not do the
research yourself, and do not let the agent create anything.

Follow this process strictly, in order:

## 1. Research and draft

Determine the repository first: `gh repo view --json nameWithOwner -q .nameWithOwner`

Then delegate to the `issue-author` agent via the Task tool, passing the
user's request verbatim plus the repository name. Ask it for:

- the affected code, by path
- related, duplicate, blocking and blocked issues, each with a reason
- suggested labels from the real label list
- a full draft body
- its numbered assumptions

Wait for it. Do not start reviewing the code in parallel — the point of the
split is that this context stays small enough for the interactive steps.

## 2. Handle duplicates

If the agent reports a duplicate, link it to the user and ask via
AskUserQuestion how to proceed — options: "Comment on the existing issue" /
"Reopen it" (if closed) / "Create a new issue anyway" / "Cancel".

Stop here on anything other than "Create a new issue anyway".

## 3. Confirm the assumptions

Take the agent's assumption list. Output your interpretation of the request
(1-2 sentences) and every assumption as numbered bullets. Do not classify
them as safe or unsafe — that is the user's call, not yours.

Then IMMEDIATELY use the AskUserQuestion tool to get explicit input:

- Assumptions with predictable alternatives: one question each, the
  alternatives as options, the agent's assumption marked "(assumed)".
- Remaining assumptions: bundle into a question per group, options
  "All correct as listed" / "I want to correct something".
- The tool allows max 4 questions per call — use multiple consecutive
  rounds if needed rather than dropping assumptions.

If the user corrects anything, apply it to the draft, then re-confirm the
updated points via the tool again. Send the correction back to
`issue-author` only if it invalidates the research rather than just a
wording choice.

Do NOT proceed to labels until every assumption has been explicitly
confirmed through the tool. Anything the user explicitly defers goes into
the issue under "Open questions".

## 4. Labels and metadata

- Use the labels the agent suggested; they come from `gh label list` and
  are real. Verify with `gh label list --json name,description` if the
  draft changed enough to affect the choice.
- Pick the best type label (bug/feature/refactor/...) plus fitting area
  labels.
- If no existing label fits well: propose 1-2 new, reusable labels (name,
  color, short description) — not one-off labels — and ask via
  AskUserQuestion whether to create them (options: "Create and apply" /
  "Proceed without labels" / "Let me pick differently"). Only run
  `gh label create <name> --color <hex> --description "..."` after approval.
- If the repo actively uses milestones (`gh api repos/<nameWithOwner>/milestones`),
  suggest one via the same tool; otherwise ignore milestones.

## 5. Confirm and create

- Show the user the full draft (title, labels, body). Above the draft,
  repeat the confirmed key assumptions in 2-3 bullets.
- Then ask via AskUserQuestion: "Create this issue?" — options:
  "Yes, create it" / "I want changes" / "Cancel".
- On "I want changes": revise, show the full updated draft, and ask again.
  Repeat until approved or cancelled.
- After approval: write the body to a temp file, then
  `gh issue create --title "..." --label "..." --body-file <path>`
  (repeat --label per label).
- If a "Blocked by" relation exists, ask via AskUserQuestion whether to also
  comment on the blocking issue with a back-reference — only do it if
  approved.
- Output the created issue URL.
- If the issue came from a draft in `issues/`, delete that file now that it
  is filed.
