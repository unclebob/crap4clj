# Bug Report: clj-mutate reuses stale LCOV and can collide on worker dirs

## Summary

Two issues were observed while running mutation testing in `crap4clj`:

1. `clj-mutate` appears to reuse existing `target/coverage/lcov.info` even when stale, which can incorrectly mark newly-tested lines as uncovered.
2. Parallel/overlapping runs can fail with `FileAlreadyExistsException` in `target/mutation-workers/...`.

Both issues reduce trust in mutation results.

## Environment

- Tool: `clj-mutate`
- GitHub HEAD used: `4d80162590ccef0f0396c765b77ce3556acdcff5`
- Project under test: `/Users/unclebob/projects/clojure/crap4clj`
- Clojure: `1.12.0`
- Speclj runner

## Issue 1: stale LCOV reuse

### Actual behavior

After adding tests and running mutation, uncovered-line counts remained unchanged in several files until `clj -M:cov --lcov` was run manually.

After manual coverage regeneration, uncovered-line counts dropped significantly (for example in `complexity.cljc` from `68` uncovered mutation sites down to `9`).

### Expected behavior

`clj-mutate` should detect stale/missing coverage and refresh coverage automatically before selecting covered lines.

### Why this matters

Mutation targeting depends on accurate coverage. Using stale LCOV causes false "coverage gap" reporting and wastes mutation cycles.

### Reproduction (high level)

1. Start with existing `target/coverage/lcov.info`.
2. Add tests that cover previously uncovered branches.
3. Run:
   - `clj -M:mutate src/.../file.cljc`
4. Observe uncovered-line set does not reflect new tests.
5. Run:
   - `clj -M:cov --lcov`
6. Re-run mutate and observe uncovered-line set changes significantly.

## Issue 2: worker directory collision

### Actual behavior

When mutation runs overlapped, one run failed with:

`FileAlreadyExistsException: target/mutation-workers/worker-0/deps.edn`

### Expected behavior

Tool should either:

- use per-run unique worker roots, or
- cleanly lock and serialize worker-root usage, or
- handle pre-existing worker files safely.

### Reproduction (high level)

1. Run `clj -M:mutate ...` in two sessions close together (or while a previous session has left worker files).
2. Observe occasional collision under `target/mutation-workers`.

## Suggested fixes

1. **Coverage freshness check**:
   - Compare LCOV mtime vs source/spec mtimes (or store fingerprint).
   - Regenerate with `clj -M:cov --lcov` when stale.

2. **Worker-root isolation**:
   - Use unique run id path (`target/mutation-workers/<uuid>/...`), or
   - lock and retry robustly.

3. **Diagnostic clarity**:
   - Emit a message when stale coverage is detected and refreshed.
   - Emit a clear warning if mutation run is using cached/stale coverage.

## Impact

- False uncovered-gap reporting
- Extra manual steps for users
- Flaky runs under concurrent execution

