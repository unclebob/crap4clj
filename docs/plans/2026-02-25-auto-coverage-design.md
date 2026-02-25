# Auto-Run Coverage in crap4clj

## Problem

crap4clj reads pre-existing Cloverage HTML reports with no staleness detection. Users must manually run `clj -M:cov` first. Stale reports produce misleading CRAP scores.

## Solution

crap4clj deletes old coverage reports and runs Cloverage itself before analysis. One command: `clj -M:crap`.

## Design

### New functions in `crap4clj.core`

**`delete-coverage-dir`** — Recursively deletes `target/coverage/` if it exists. No-op if absent.

**`run-coverage`** — Uses `ProcessBuilder` with `.inheritIO` to run `clj -M:cov`. User sees Cloverage output in real-time. If exit code is non-zero, prints error and calls `(System/exit 1)`.

### Modified function

**`-main`** — Calls `delete-coverage-dir`, then `run-coverage`, then proceeds with existing analysis pipeline.

### Data flow

```
-main
  ├─ delete-coverage-dir   (rm -rf target/coverage/)
  ├─ run-coverage          (clj -M:cov, abort on failure)
  ├─ find-source-files
  ├─ filter-sources
  ├─ analyze-file (for each)
  └─ format-report + println
```

### Error handling

- Missing `target/coverage/` — `delete-coverage-dir` is a no-op.
- `clj -M:cov` exits non-zero — print `"Coverage failed (exit N)"` and `System/exit 1`.
- No `--skip-cov` flag. Always runs coverage.

### Cloverage dependency

crap4clj depends on Cloverage being available via a `:cov` alias. The SKILL.md must instruct users to add both `:cov` and `:crap` aliases to their project.

### Documentation updates

- **README.md** — Simplify usage to single `clj -M:crap` command. Remove separate `clj -M:cov` step.
- **SKILL.md** — Add `:cov` alias setup instructions with Cloverage dependency. Simplify usage to single command.

### Testing

- `delete-coverage-dir` — Create temp dir, call function, verify deleted.
- `run-coverage` — Integration test: verify exit-code abort logic.
- `-main` integration — Existing tests cover the analysis pipeline; manual verification for end-to-end.
