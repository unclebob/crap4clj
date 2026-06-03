# crap4clj

**CRAP** (Change Risk Anti-Pattern) metric for Clojure projects.

Combines cyclomatic complexity with test coverage to identify functions that are both complex and under-tested — the riskiest code to change.

## Quick Start

Use either a Babashka `bb.edn` task or a normal Clojure `deps.edn` alias.
Babashka is recommended for day-to-day use because it starts much faster and avoids JVM startup overhead in the `crap4clj` launcher.
The `clj` launcher remains fully supported and is useful as a compatibility fallback when debugging runtime-specific behavior.

For Babashka, add a `crap` task to your project's `bb.edn`:

```clojure
{:paths ["src"]
 :tasks {crap {:doc "Run crap4clj"
               :requires ([crap4clj.core :as core])
               :task (apply core/-main *command-line-args*)}}}
```

For Clojure CLI, add to your project's `deps.edn`:

```clojure
:cov  {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
       :main-opts ["-m" "speclj.cloverage" "--" "-p" "src" "-s" "spec"]}
:crap {:extra-deps {io.github.unclebob/crap4clj
                     {:git/url "https://github.com/unclebob/crap4clj"
                      :git/sha "<current-sha>"}}
       :main-opts ["-m" "crap4clj.core"]}
```

Both launchers accept the same module filters:

```bash
clj -M:crap    # deletes old coverage, runs Cloverage, analyzes
bb crap        # same, using the Babashka task
```

crap4clj automatically deletes stale coverage reports, runs `clj -M:cov --lcov`
(falling back to `clj -M:cov` if needed), and then analyzes the results. Your
project must have a `:cov` alias configured with Cloverage.

The example above uses `speclj.cloverage` as the runner. For `clojure.test` projects, use `cloverage.coverage` instead:

```clojure
:cov  {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
       :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "test"]}
```

## Output

```
CRAP Report
===========
Function                       Namespace                            CC   Cov%     CRAP
-------------------------------------------------------------------------------------
complex-fn                     my.namespace                         12   45.0%    130.2
simple-fn                      my.namespace                          1  100.0%      1.0
```

## Filtering

Pass module name fragments as arguments to filter:

```bash
clj -M:crap combat movement    # only files matching "combat" or "movement"
bb crap combat movement        # same, using the Babashka task
```

## Recommended Workflow

Run CRAP analysis before refactoring, mutation testing, or work on a risky module:

```bash
bb crap
clj -M:spec
```

Use `bb crap` for local feedback. Use `clj -M:crap` when you want to compare behavior against the normal Clojure launcher.

Start with the worst reported functions. A high score means the function is both complex and under-covered, so reducing either complexity or adding focused tests will lower risk.

For focused work, pass one or more source path fragments:

```bash
bb crap src/myapp/orders src/myapp/billing
```

Recommended loop:
1. Run `bb crap` or `bb crap path/fragment`.
2. Pick the highest scoring function in the module you are changing.
3. Add characterization specs until coverage is clear enough to change the code.
4. Refactor complex branches or split large functions.
5. Rerun CRAP and specs before moving to the next risky function.

## Coverage Mapping Notes

crap4clj uses coverage in this order:

1. per-source-file HTML (`target/coverage/...<source>.html`)
2. `target/coverage/lcov.info` (file-accurate line coverage)
3. namespace HTML fallback (`.../<namespace>.clj.html` or `.cljc.html`)

For split-file namespace patterns (multiple files loaded into one namespace via
`in-ns` + `load`), LCOV is the reliable option for per-function scoring because
it preserves physical source file paths.

If only namespace fallback HTML is available, crap4clj uses `defn` name matching
and does not reuse mismatched line ranges. Unmatched functions are reported as
`N/A` (indeterminate) and a warning is printed to stderr.

Namespace fallback lookup checks:

1. per-file path (for example `target/coverage/foo/bar.clj.html`)
2. namespace `.clj` path (for example `target/coverage/foo/bar.clj.html`)
3. namespace `.cljc` path (for example `target/coverage/foo/bar.cljc.html`)

To enable LCOV in your `:cov` alias, include Cloverage's `--lcov` output option
so `target/coverage/lcov.info` is generated.

## CRAP Formula

```
CRAP(fn) = CC² × (1 - coverage)³ + CC
```

- **CC** = cyclomatic complexity (decision points + 1)
- **coverage** = fraction of forms covered by tests (from Cloverage)

| Score | Risk |
|-------|------|
| 1-5   | Low — clean code |
| 5-30  | Moderate — refactor or add tests |
| 30+   | High — complex and under-tested |

## What It Counts

Decision points that increase cyclomatic complexity:
- `if`, `if-not`, `if-let`, `if-some`
- `when`, `when-not`, `when-let`, `when-some`, `when-first`
- `and`, `or`
- `loop`, `catch`
- Each clause in `cond`, `condp`, `case`, `cond->`, `cond->>`
- Each step in `some->`, `some->>`

## Claude Code Skill

crap4clj includes a `SKILL.md` for use as a [Claude Code skill](https://docs.anthropic.com/en/docs/claude-code/skills). Add it to your project's `.claude/settings.json`:

```json
{
  "skills": [
    "https://github.com/unclebob/crap4clj/blob/master/SKILL.md"
  ]
}
```

Then ask Claude Code for a "CRAP report" and it will know how to set up and run the tool.

## Development

```bash
clj -M:spec    # run tests
clj -M:crap    # run on own source
bb crap        # run on own source via Babashka task
```

## License

Copyright (c) Robert C. Martin. All rights reserved.
