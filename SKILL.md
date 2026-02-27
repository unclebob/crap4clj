---
name: crap4clj
description: Use when the user asks for a CRAP report, cyclomatic complexity analysis, or code quality metrics on a Clojure project
---

# crap4clj — CRAP Metric for Clojure

Computes the **CRAP** (Change Risk Anti-Pattern) score for every `defn` and `defn-` in a Clojure project. CRAP combines cyclomatic complexity with test coverage to identify functions that are both complex and under-tested.

## Setup

Add both a `:cov` alias (Cloverage) and a `:crap` alias to the project's `deps.edn`:

```clojure
:cov  {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
       :main-opts ["-m" "speclj.cloverage" "--" "-p" "src" "-s" "spec"]}
:crap {:extra-deps {io.github.unclebob/crap4clj
                     {:git/url "https://github.com/unclebob/crap4clj"
                      :git/sha "<current-sha>"}}
       :main-opts ["-m" "crap4clj.core"]}
```

The example above uses `speclj.cloverage` as the runner. For `clojure.test` projects, use `cloverage.coverage` instead:

```clojure
:cov  {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
       :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "test"]}
```

Adjust the `-p` (source path) and `-s` (test path) flags in `:cov` to match your project layout.

## Usage

```bash
# Analyze all source files under src/
clj -M:crap

# Filter to specific modules
clj -M:crap combat movement
```

crap4clj automatically deletes stale coverage reports, runs `clj -M:cov`, and then analyzes the results.

### Output

A table sorted by CRAP score (worst first):

```
CRAP Report
===========
Function                       Namespace                            CC   Cov%     CRAP
-------------------------------------------------------------------------------------
complex-fn                     my.namespace                         12   45.0%    130.2
simple-fn                      my.namespace                          1  100.0%      1.0
```

## Interpreting Scores

| CRAP Score | Meaning |
|-----------|---------|
| 1-5       | Clean — low complexity, well tested |
| 5-30      | Moderate — consider refactoring or adding tests |
| 30+       | Crappy — high complexity with poor coverage |

## How It Works

1. Deletes old coverage reports and runs Cloverage (`clj -M:cov`)
2. Finds all `.clj` and `.cljc` files under `src/`
3. Extracts `defn`/`defn-` functions with line ranges
4. Computes cyclomatic complexity (if/when/cond/condp/case/cond->/cond->>/some->/some->>/and/or/loop/catch)
5. Reads Cloverage HTML for per-line form coverage
6. Applies CRAP formula: `CC² × (1 - cov)³ + CC`
7. Sorts by CRAP score descending and prints report
