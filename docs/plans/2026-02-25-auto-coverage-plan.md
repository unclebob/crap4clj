# Auto-Run Coverage Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** crap4clj deletes stale coverage reports and runs Cloverage before analysis, so users only need `clj -M:crap`.

**Architecture:** Add `delete-coverage-dir` and `run-coverage` to `crap4clj.core`. Both are called from `-main` before the existing analysis pipeline. `run-coverage` uses `ProcessBuilder` with `.inheritIO` for real-time output.

**Tech Stack:** Clojure 1.12, Java ProcessBuilder, speclj for tests.

---

### Task 1: `delete-coverage-dir` — failing test

**Files:**
- Modify: `spec/crap4clj/core_spec.clj`

**Step 1: Write the failing test**

Add this context to `spec/crap4clj/core_spec.clj` inside the `(describe "crap4clj core" ...)` block, after the existing contexts:

```clojure
(context "delete-coverage-dir"
  (it "deletes an existing coverage directory"
    (let [dir (java.io.File. "target/test-coverage-dir/sub")]
      (.mkdirs dir)
      (spit (java.io.File. dir "file.html") "data")
      (delete-coverage-dir "target/test-coverage-dir")
      (should-not (.exists (java.io.File. "target/test-coverage-dir")))))

  (it "is a no-op when directory does not exist"
    (delete-coverage-dir "target/nonexistent-coverage-dir")
    (should-not (.exists (java.io.File. "target/nonexistent-coverage-dir")))))
```

**Step 2: Run test to verify it fails**

Run: `clj -M:spec`
Expected: FAIL — `delete-coverage-dir` is not defined.

---

### Task 2: `delete-coverage-dir` — implementation

**Files:**
- Modify: `src/crap4clj/core.cljc`

**Step 3: Write minimal implementation**

Add this function to `src/crap4clj/core.cljc` after the `ns` declaration, before `filter-sources`:

```clojure
(defn delete-coverage-dir [dir-path]
  (let [dir (io/file dir-path)]
    (when (.exists dir)
      (run! io/delete-file (reverse (file-seq dir))))))
```

**Step 4: Run tests to verify they pass**

Run: `clj -M:spec`
Expected: All tests pass, including the two new `delete-coverage-dir` tests.

**Step 5: Commit**

```bash
git add src/crap4clj/core.cljc spec/crap4clj/core_spec.clj
git commit -m "Add delete-coverage-dir with tests"
```

---

### Task 3: `run-coverage` — failing test

**Files:**
- Modify: `spec/crap4clj/core_spec.clj`

**Step 6: Write the failing test**

Add this context to `spec/crap4clj/core_spec.clj` inside the `(describe "crap4clj core" ...)` block:

```clojure
(context "run-coverage"
  (it "returns 0 for a successful command"
    (should= 0 (run-coverage "true")))

  (it "returns non-zero for a failing command"
    (should-not= 0 (run-coverage "false"))))
```

Note: `run-coverage` takes a command string so it's testable. In `-main` we'll pass `"clj -M:cov"`.

**Step 7: Run test to verify it fails**

Run: `clj -M:spec`
Expected: FAIL — `run-coverage` is not defined.

---

### Task 4: `run-coverage` — implementation

**Files:**
- Modify: `src/crap4clj/core.cljc`

**Step 8: Write minimal implementation**

Add this function to `src/crap4clj/core.cljc` after `delete-coverage-dir`:

```clojure
(defn run-coverage [command]
  (let [parts (.split command " ")
        pb (ProcessBuilder. (java.util.Arrays/asList parts))]
    (.inheritIO pb)
    (.waitFor (.start pb))))
```

**Step 9: Run tests to verify they pass**

Run: `clj -M:spec`
Expected: All tests pass.

**Step 10: Commit**

```bash
git add src/crap4clj/core.cljc spec/crap4clj/core_spec.clj
git commit -m "Add run-coverage with tests"
```

---

### Task 5: Wire into `-main`

**Files:**
- Modify: `src/crap4clj/core.cljc`

**Step 11: Modify `-main`**

Replace the current `-main` with:

```clojure
(defn -main [& args]
  (delete-coverage-dir "target/coverage")
  (let [exit (run-coverage "clj -M:cov")]
    (when-not (zero? exit)
      (println (str "Coverage failed (exit " exit ")"))
      (System/exit 1)))
  (let [sources (find-source-files)
        filtered (filter-sources sources (vec args))
        all-entries (mapcat analyze-file filtered)
        sorted (crap/sort-by-crap all-entries)]
    (println (crap/format-report sorted))))
```

**Step 12: Run tests**

Run: `clj -M:spec`
Expected: All tests pass. (The existing `-main` integration test doesn't call `-main` directly, so no change needed.)

**Step 13: Commit**

```bash
git add src/crap4clj/core.cljc
git commit -m "Wire delete-coverage-dir and run-coverage into -main"
```

---

### Task 6: Update README.md

**Files:**
- Modify: `README.md`

**Step 14: Simplify usage instructions**

In the Quick Start section, replace the two-step `clj -M:cov` / `clj -M:crap` with just:

```bash
clj -M:crap    # deletes old coverage, runs Cloverage, analyzes
```

Add a note that the project needs a `:cov` alias with Cloverage:

```clojure
:cov {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
      :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "spec"]}
```

**Step 15: Commit**

```bash
git add README.md
git commit -m "Update README for auto-coverage"
```

---

### Task 7: Update SKILL.md

**Files:**
- Modify: `SKILL.md`

**Step 16: Add Cloverage setup instructions**

In the Setup section, add the `:cov` alias that consuming projects must have:

```clojure
:cov {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
      :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "spec"]}
```

Simplify the Usage section — remove the separate `clj -M:cov` step. Just `clj -M:crap`.

Update the "How It Works" section to mention that step 1 is now "Deletes old coverage and runs Cloverage".

**Step 17: Commit**

```bash
git add SKILL.md
git commit -m "Update SKILL.md with Cloverage dependency instructions"
```

---

### Task 8: End-to-end verification

**Step 18: Run full test suite**

Run: `clj -M:spec`
Expected: All tests pass.

**Step 19: Manual end-to-end test**

Run: `clj -M:crap`
Expected: Deletes `target/coverage/`, runs Cloverage, prints CRAP report.

**Step 20: Check coverage**

Run: `clj -M:cov` then check coverage numbers are in the high 90s.
