# `docs/arrows/` — Arrow of Intent Tracking

This directory tracks the arrow of intent across the project — the chain from high-level design through to realized code:

```
HLD → LLDs → EARS → Tests → Code
```

## Files in this directory

- **`index.yaml`** — The dependency graph. Load this first to understand what's available, what's blocked, and what needs work.
- **`{segment-name}.md`** — One file per arrow segment. Orientation page with References, Spec Coverage, and Key Findings.

## Starting a session

1. Load `index.yaml`.
2. Query for unblocked segments: `yq '.arrows | to_entries | .[] | select(.value.blockedBy | length == 0) | .key' index.yaml`.
3. Load the relevant `{segment-name}.md`.
4. Follow its References to the LLD, spec file, tests, or code.

## Status enum

| Status | Meaning |
|---|---|
| UNMAPPED | Not yet explored |
| MAPPED | Structure known, specs not verified against code |
| AUDITED | Specs verified — implementation status understood |
| OK | Fully coherent — all specs implemented |
| PARTIAL | Some specs missing or partial |
| BROKEN | Code and docs have diverged significantly |
| STALE | Docs exist but outdated |
| OBSOLETE | Superseded, kept for historical reference |
| MERGED | Combined into another arrow (see `merged_into`) |

Normal progression: `UNMAPPED → MAPPED → AUDITED → OK`. `AUDITED` means "we know the state"; `OK` means "it's fixed."

## Common workflows

### Auditing a segment

1. Read the segment's arrow doc references.
2. For each EARS spec, verify the implementing code with the cited `@spec` annotation.
3. Update arrow doc coverage table and any "Key Findings."
4. Refresh `status`, `audited`, `audited_sha`, `next`, and `drift` in `index.yaml`.

### Mapping a new segment

1. Explore the code and docs for the domain.
2. Create `docs/arrows/{name}.md` from the arrow-doc template.
3. Add an entry to `index.yaml` under `arrows:`.
4. Remove from `unmapped.docs` if listed.

## 2026-06-17 bootstrap audit — known issue

The first audit pass (this commit) found that **spec checkboxes in `docs/specs/` are significantly out of sync with actual implementation**, especially in `category-management` and `event-logging`. Many specs marked `[ ]` (active gap) were confirmed already implemented by direct code inspection. See those two arrow docs' Key Findings for specifics. Until a full reconciliation pass is done, treat `[ ]` markers in those two spec files with skepticism — verify against code before assuming work is needed.
