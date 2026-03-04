# crap4clj

**CRAP** (Change Risk Anti-Pattern) metric for Clojure projects.

Combines cyclomatic complexity with test coverage to identify functions that are both complex and under-tested — the riskiest code to change.

## Quick Start

Add to your project's `deps.edn`:

```clojure
:cov  {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
       :main-opts ["-m" "speclj.cloverage" "--" "-p" "src" "-s" "spec"]}
:crap {:extra-deps {io.github.unclebob/crap4clj
                     {:git/url "https://github.com/unclebob/crap4clj"
                      :git/sha "<current-sha>"}}
       :main-opts ["-m" "crap4clj.core"]}
```

Run:

```bash
clj -M:crap    # deletes old coverage, runs Cloverage, analyzes
```

crap4clj automatically deletes stale coverage reports, runs `clj -M:cov`, and then analyzes the results. Your project must have a `:cov` alias configured with Cloverage.

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
```

## Coverage Mapping Notes

By default, crap4clj looks for Cloverage HTML using the source file path.

For split-file namespace patterns (multiple files loaded into one namespace via
`in-ns` + `load`), crap4clj also falls back to namespace-based coverage pages
when a per-file page is missing. It checks:

1. per-file path (for example `target/coverage/foo/bar.clj.html`)
2. namespace `.clj` path (for example `target/coverage/foo/bar.clj.html`)
3. namespace `.cljc` path (for example `target/coverage/foo/bar.cljc.html`)

This prevents false `0.0%` function coverage in valid split-file namespace
setups.

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
```

## License

Copyright (c) Robert C. Martin. All rights reserved.
