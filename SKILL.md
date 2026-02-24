---
name: crap4clj
description: Use when the user asks for a CRAP report, cyclomatic complexity analysis, or code quality metrics on a Clojure project
---

# crap4clj — CRAP Metric for Clojure

Computes the **CRAP** (Change Risk Anti-Pattern) score for every `defn` and `defn-` in a Clojure project. CRAP combines cyclomatic complexity with test coverage to identify functions that are both complex and under-tested.

## Setup

Add crap4clj as a git dependency in the project's `deps.edn`:

```clojure
:crap {:extra-deps {io.github.unclebob/crap4clj
                     {:git/url "https://github.com/unclebob/crap4clj"
                      :git/sha "68ecdd86dc644b63b25143012b3994c08953b8d8"}}
       :main-opts ["-m" "crap4clj.core"]}
```

## Usage

### Generate coverage data first

```bash
clj -M:cov
```

This produces Cloverage HTML reports in `target/coverage/`.

### Run CRAP analysis

```bash
# Analyze all source files under src/
clj -M:crap

# Filter to specific modules
clj -M:crap combat movement
```

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

1. Finds all `.clj` and `.cljc` files under `src/`
2. Extracts `defn`/`defn-` functions with line ranges
3. Computes cyclomatic complexity (if/when/cond/case/and/or/loop/catch)
4. Reads Cloverage HTML for per-line form coverage
5. Applies CRAP formula: `CC² × (1 - cov)³ + CC`
6. Sorts by CRAP score descending and prints report
